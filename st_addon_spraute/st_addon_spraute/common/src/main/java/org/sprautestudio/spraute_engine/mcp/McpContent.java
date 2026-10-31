package org.sprautestudio.spraute_engine.mcp;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.sprautestudio.spraute_engine.editor.ClientState;
import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.project.Workspace;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static org.sprautestudio.spraute_engine.mcp.McpTools.*;

/**
 * Making things for a story through MCP: quests, zones, shops, fighting styles, effects, cutscenes,
 * interfaces, entity types and scripts — with the format of every file, changes by merging JSON, and
 * lists of what can be used (models, animations, items, sounds…).
 */
final class McpContent {
    private McpContent() {
    }

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** A kind of thing the assistant can make. */
    record Kind(String name, String title, Project.Kind file, Class<?> type, String notes, Function<String, String> example,
                Function<String, Object> parse) {
    }

    static final Map<String, Kind> KINDS = new LinkedHashMap<>();

    private static void kind(String name, String title, Project.Kind file, Class<?> type, String notes, Function<String, String> example,
                             Function<String, Object> parse) {
        KINDS.put(name, new Kind(name, title, file, type, notes, example, parse));
    }

    static {
        kind("quest", "Квест", Project.Kind.QUEST, org.sprautestudio.spraute_engine.quest.QuestDef.class,
                "В сюжете: quest start \"имя\", quest progress/done/fail, queststate(\"имя\"), questprogress(\"имя\", \"задача\"). "
                        + "Имя квеста — имя файла. Задачи (steps) по id отмечаются из сюжета.",
                n -> org.sprautestudio.spraute_engine.quest.QuestDef.example(n).toJson(),
                org.sprautestudio.spraute_engine.quest.QuestDef::fromJson);
        kind("zone", "Зона", Project.Kind.ZONE, org.sprautestudio.spraute_engine.zone.ZoneDef.class,
                "В сюжете: on zoneenter \"имя\" / zoneleave, wait zone \"имя\", inzone(player, \"имя\"), zone wall/enable. "
                        + "Координаты — блоки мира; more — дополнительные куски зоны. create с at — зона вокруг точки.",
                n -> org.sprautestudio.spraute_engine.zone.ZoneDef.example(n, Vec3.ZERO).toJson(),
                org.sprautestudio.spraute_engine.zone.ZoneDef::fromJson);
        kind("shop", "Магазин", Project.Kind.SHOP, org.sprautestudio.spraute_engine.shop.ShopDef.class,
                "В сюжете: shop open \"имя\". Товары — предметы minecraft:…, цены в валюте магазина.",
                n -> org.sprautestudio.spraute_engine.shop.ShopDef.example(n).toJson(),
                org.sprautestudio.spraute_engine.shop.ShopDef::fromJson);
        kind("fight", "Стиль боя", Project.Kind.FIGHT, org.sprautestudio.spraute_engine.fight.FightDef.class,
                "В сюжете: fight кто style \"имя\" target player, wait defeated кто, fighthp(кто). preset: fists, sword, brute, weak.",
                n -> org.sprautestudio.spraute_engine.fight.FightDef.example(n).toJson(),
                org.sprautestudio.spraute_engine.fight.FightDef::fromJson);
        kind("effect", "Эффект (VFX)", Project.Kind.EFFECT, org.sprautestudio.spraute_engine.fx.FxDef.class,
                "В сюжете: fx play \"имя\" at точка, cast \"имя\" from кто … (снаряд). preset — любой из assets kind=fx.",
                n -> new org.sprautestudio.spraute_engine.fx.FxDef().toJson(),
                org.sprautestudio.spraute_engine.fx.FxDef::fromJson);
        kind("cutscene", "Катсцена", Project.Kind.CUTSCENE, org.sprautestudio.spraute_engine.cutscene.CutsceneDoc.class,
                "В сюжете: cutscene play \"имя\". Ключевые кадры камеры и персонажей по времени (в тиках).",
                n -> org.sprautestudio.spraute_engine.cutscene.CutsceneDoc.example(n).toJson(),
                org.sprautestudio.spraute_engine.cutscene.CutsceneDoc::fromJson);
        kind("gui", "Интерфейс", Project.Kind.GUI, org.sprautestudio.spraute_engine.gui.GuiDoc.class,
                "В сюжете: gui open \"имя\", on guiclick \"имя\" [id элемента]. Элементы с координатами в пикселях экрана 1x.",
                n -> org.sprautestudio.spraute_engine.gui.GuiDoc.example(n).toJson(),
                org.sprautestudio.spraute_engine.gui.GuiDoc::fromJson);
        kind("entity", "Тип сущности (персонаж со своей моделью)", null, org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.Def.class,
                "Файл entities/<имя>.sent. В сюжете: character x = \"spraute:<имя>\" name \"…\" [health 100] [hitbox \"mortal\"] [speed 1.5]. "
                        + "model — модель из assets kind=models (или встроенная), анимации idle/walk/run подхватываются по именам.",
                n -> new org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.Def().toJson(),
                org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.Def::fromJson);
        kind("path", "Путь (по нему ходят персонажи)", Project.Kind.PATH, org.sprautestudio.spraute_engine.path.PathDef.class,
                "В сюжете: walk кто \"имя\" [speed 1.4] [loop|pingpong] [nowait], stopwalk кто, on pathend \"имя\". Точки — x y z блоков мира, "
                        + "wait — постоять (сек), anim — анимация стоя, yaw — куда смотреть. create с at — путь рядом с точкой.",
                n -> org.sprautestudio.spraute_engine.path.PathDef.example(Vec3.ZERO).toJson(),
                org.sprautestudio.spraute_engine.path.PathDef::fromJson);
        kind("script", "Сюжет (текст .sst)", Project.Kind.SCRIPT, null,
                "Свободный номер подставляется сам. Персонажи объявляются в сюжете: character имя = \"тип\" name \"Имя\" color \"#hex\".",
                n -> "", null);
        kind("blocks", "Сюжет в визуальных блоках (.svs)", Project.Kind.BLOCKS, null, "Лучше писать текстом (script) — блоки редактирует человек.",
                n -> "", null);
    }

    // ================================================================== describing a format

    /** The fields of a file's class (and of the classes inside it), with types: the "schema". */
    static String fields(Class<?> c) {
        StringBuilder sb = new StringBuilder();
        Set<Class<?>> seen = new HashSet<>();
        describe(c, sb, "", seen, 0);
        return sb.toString();
    }

    private static void describe(Class<?> c, StringBuilder sb, String indent, Set<Class<?>> seen, int depth) {
        if (!seen.add(c) || depth > 4) return;
        List<Class<?>> inner = new ArrayList<>();
        for (Field f : c.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) continue;
            sb.append(indent).append(f.getName()).append(": ").append(typeName(f.getGenericType(), inner)).append('\n');
        }
        for (Class<?> k : inner) {
            if (seen.contains(k)) continue;
            sb.append(indent).append("— ").append(k.getSimpleName()).append(":\n");
            describe(k, sb, indent + "  ", seen, depth + 1);
        }
    }

    private static String typeName(Type t, List<Class<?>> inner) {
        if (t instanceof ParameterizedType p) {
            Class<?> raw = (Class<?>) p.getRawType();
            StringBuilder s = new StringBuilder(List.class.isAssignableFrom(raw) ? "list of " : Map.class.isAssignableFrom(raw) ? "map of " : raw.getSimpleName() + " of ");
            Type[] a = p.getActualTypeArguments();
            for (int i = 0; i < a.length; i++) s.append(i > 0 ? " → " : "").append(typeName(a[i], inner));
            return s.toString();
        }
        if (t instanceof Class<?> c) {
            if (c.isArray()) return "list of " + typeName(c.getComponentType(), inner);
            if (c.isEnum()) return "одно из " + Arrays.toString(c.getEnumConstants());
            if (c.isPrimitive() || c == String.class || Number.class.isAssignableFrom(c) || c == Boolean.class) return c.getSimpleName();
            if (c.getName().startsWith("org.sprautestudio")) {
                if (!inner.contains(c)) inner.add(c);
                return c.getSimpleName();
            }
            return c.getSimpleName();
        }
        return t.getTypeName();
    }

    // ================================================================== JSON merge

    /** Puts patch into base: objects merge key by key, everything else (lists too) replaces; null removes. */
    static JsonElement merge(JsonElement base, JsonElement patch) {
        if (base == null || !base.isJsonObject() || !patch.isJsonObject()) return patch.deepCopy();
        JsonObject out = base.getAsJsonObject().deepCopy();
        for (Map.Entry<String, JsonElement> e : patch.getAsJsonObject().entrySet()) {
            if (e.getValue().isJsonNull()) out.remove(e.getKey());
            else out.add(e.getKey(), merge(out.get(e.getKey()), e.getValue()));
        }
        return out;
    }

    private static JsonElement asJson(JsonObject a, String k) {
        if (!a.has(k) || a.get(k).isJsonNull()) return null;
        JsonElement e = a.get(k);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) return JsonParser.parseString(e.getAsString());
        return e;
    }

    /** Checks a file's JSON by reading it the way the game does; "" when fine. */
    static String validate(Kind k, String json) {
        if (k == null || k.parse() == null) return "";
        try {
            Object o = k.parse().apply(json);
            return o == null ? "Не читается как " + k.title() : "";
        } catch (Exception e) {
            return "Ошибка формата: " + e.getMessage();
        }
    }

    static Kind kindOfFile(String rel) {
        if (rel.endsWith(".sent")) return KINDS.get("entity");
        for (Kind k : KINDS.values()) if (k.file() != null && rel.endsWith(k.file().ext)) return k;
        return null;
    }

    /** After a change: the editor shows it, the world uses it. */
    static String afterChange(String project, Path dir, Path file, Kind k) {
        onClient(() -> {
            org.sprautestudio.spraute_engine.editor.SprauteScreen.fileChangedOutside(file);
            if (k != null && k.name().equals("entity")) org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.scan();
            return null;
        });
        if (k == null || k.file() == Project.Kind.SCRIPT || k.file() == Project.Kind.BLOCKS) return "";
        try {
            Project pr = Project.openReadOnly(dir);
            var src = pr.deploySources();
            return onClient(() -> {
                if (Minecraft.getInstance().getConnection() == null) return " (мир не открыт — применится при загрузке сюжета)";
                ClientState.get().sources(pr.name(), src);
                if (k.name().equals("entity")) return " (тип сущности: новые появления персонажей возьмут его)";
                return " Отправлено в мир.";
            });
        } catch (Exception e) {
            return " (не отправлено в мир: " + e.getMessage() + ")";
        }
    }

    // ================================================================== tools

    static void register() {
        tool("formats", "Формат файлов Spraute: какие виды можно создать (quest, zone, shop, fight, effect, cutscene, gui, entity, script) "
                        + "и для выбранного kind — поля с типами, пример JSON и как использовать в сюжете. Смотрите перед созданием.",
                schema("kind", "string", "Вид (пусто — список видов)", "?"), a -> {
                    String kn = str(a, "kind", "");
                    if (kn.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (Kind k : KINDS.values()) sb.append(k.name()).append(" — ").append(k.title()).append(". ").append(k.notes()).append('\n');
                        sb.append("\nНастройки движка: engine_config. Персонажи в сюжете — строка character; их тип с моделью — kind entity.");
                        return sb.toString();
                    }
                    Kind k = KINDS.get(kn);
                    if (k == null) throw new IllegalArgumentException("Нет вида " + kn + ". Есть: " + String.join(", ", KINDS.keySet()));
                    StringBuilder sb = new StringBuilder(k.title()).append("\n").append(k.notes()).append("\n");
                    if (k.type() != null) sb.append("\nПоля:\n").append(fields(k.type()));
                    String ex = k.example().apply("пример");
                    if (!ex.isEmpty()) {
                        try {
                            ex = PRETTY.toJson(JsonParser.parseString(ex));
                        } catch (Exception ignored) {
                        }
                        sb.append("\nПример (так создаёт create):\n").append(ex);
                    }
                    if (k.name().equals("effect")) sb.append("\n\nШаблоны эффектов: ").append(String.join(", ", org.sprautestudio.spraute_engine.fx.FxPresets.names()));
                    if (k.name().equals("fight")) sb.append("\n\nШаблоны: ").append(org.sprautestudio.spraute_engine.fight.FightDef.PRESET_TITLES);
                    return sb.toString();
                });

        tool("create", "Создаёт вещь в проекте: quest, zone, shop, fight, effect, cutscene, gui, entity, script, blocks — как кнопка «Создать» в редакторе "
                        + "(с примером внутри), затем вливает в неё patch. Зоны, магазины, квесты… сразу уходят в мир. Ответ — путь и итоговый текст.",
                schema("project", "string", "Имя проекта", "+", "kind", "string", "Вид", "+", "name", "string", "Имя (по нему сюжет обращается)", "+",
                        "patch", "object", "JSON, вливаемый поверх примера: объекты сливаются, списки заменяются, null удаляет поле", "?",
                        "preset", "string", "Шаблон: для effect — имя пресета, для fight — fists/sword/brute/weak", "?",
                        "model", "string", "Для entity: модель", "?",
                        "at", "array", "Для zone: центр [x, y, z] (по умолчанию — где игрок)", "?",
                        "folder", "string", "Подпапка внутри папки вида (необязательно)", "?",
                        "code", "string", "Для script: текст сюжета без строки script (номер и заголовок добавятся)", "?"),
                a -> {
                    try {
                        String pname = str(a, "project", "");
                        Path dir = projectDir(pname);
                        Project pr = Project.open(dir);
                        Kind k = KINDS.get(str(a, "kind", ""));
                        if (k == null) throw new IllegalArgumentException("Нет вида " + str(a, "kind", "") + ". Есть: " + String.join(", ", KINDS.keySet()));
                        String name = str(a, "name", "новое");
                        Path file;
                        switch (k.name()) {
                            case "quest" -> file = pr.newQuest(name).path();
                            case "shop" -> file = pr.newShop(name).path();
                            case "cutscene" -> file = pr.newCutscene(name).path();
                            case "gui" -> file = pr.newGui(name).path();
                            case "effect" -> file = pr.newEffect(name, str(a, "preset", "sparkles")).path();
                            case "fight" -> {
                                file = pr.newFight(name).path();
                                if (a.has("preset")) {
                                    var d = org.sprautestudio.spraute_engine.fight.FightDef.preset(str(a, "preset", "fists"));
                                    if (d != null) Files.writeString(file, d.toJson(), StandardCharsets.UTF_8);
                                }
                            }
                            case "zone" -> {
                                Vec3 at;
                                if (a.has("at") && a.get("at").isJsonArray()) {
                                    JsonArray v = a.getAsJsonArray("at");
                                    at = new Vec3(v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble());
                                } else at = onClient(() -> Minecraft.getInstance().player != null ? Minecraft.getInstance().player.position() : Vec3.ZERO);
                                file = pr.newZone(name, at).path();
                            }
                            case "path" -> {
                                Vec3 at;
                                if (a.has("at") && a.get("at").isJsonArray()) {
                                    JsonArray v = a.getAsJsonArray("at");
                                    at = new Vec3(v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble());
                                } else at = onClient(() -> Minecraft.getInstance().player != null ? Minecraft.getInstance().player.position() : Vec3.ZERO);
                                file = pr.newPath(name, at).path();
                            }
                            case "entity" -> file = pr.newEntity(name, a.has("model") ? str(a, "model", null) : null).path();
                            case "script" -> {
                                file = pr.newScript(name).path();
                                if (a.has("code")) {
                                    String head = Files.readString(file, StandardCharsets.UTF_8).lines().findFirst().orElse("script 1 \"" + name + "\"");
                                    Files.writeString(file, head + "\n" + str(a, "code", ""), StandardCharsets.UTF_8);
                                }
                            }
                            case "blocks" -> file = pr.newBlocks(name).path();
                            default -> throw new IllegalArgumentException("Не умею создавать " + k.name());
                        }
                        if (a.has("folder") && !str(a, "folder", "").isBlank()) {
                            Path base = file.getParent();
                            Path target = base.resolve(str(a, "folder", "").replace('\\', '/')).normalize();
                            if (!target.startsWith(base)) throw new IllegalArgumentException("Папка вне проекта");
                            Files.createDirectories(target);
                            file = Files.move(file, target.resolve(file.getFileName()));
                        }
                        JsonElement patch = asJson(a, "patch");
                        String text = Files.readString(file, StandardCharsets.UTF_8);
                        String problem = "";
                        if (patch != null && k.type() != null) {
                            JsonElement merged = merge(JsonParser.parseString(text), patch);
                            text = PRETTY.toJson(merged);
                            problem = validate(k, text);
                            Files.writeString(file, text, StandardCharsets.UTF_8);
                        }
                        String applied = afterChange(pname, dir, file, k);
                        String r = "Создано: " + rel(dir, file) + "." + applied + (problem.isEmpty() ? "" : "\n⚠ " + problem);
                        if (k.name().equals("script")) r += "\n" + diagnostics(text);
                        return r + "\n\n" + text;
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });

        tool("patch_json", "Меняет JSON-файл проекта (квест, зону, магазин, стиль боя, эффект, катсцену, интерфейс, сущность, engine.json) слиянием: "
                        + "объекты сливаются по ключам, списки и значения заменяются, null удаляет ключ. Проверяет формат и отправляет в мир.",
                schema("project", "string", "Имя проекта", "+", "path", "string", "Путь внутри проекта", "+", "patch", "object", "Что влить", "+"), a -> {
                    try {
                        String pname = str(a, "project", "");
                        Path dir = projectDir(pname);
                        Path file = inProject(pname, str(a, "path", ""));
                        String rp = rel(dir, file);
                        JsonElement merged = merge(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)), asJson(a, "patch"));
                        String text = PRETTY.toJson(merged);
                        Kind k = kindOfFile(rp);
                        String problem = validate(k, text);
                        if (!problem.isEmpty()) return "Не записано — " + problem;
                        Files.writeString(file, text, StandardCharsets.UTF_8);
                        if (rp.equals("engine.json") || rp.endsWith("/engine.json")) {
                            String json = org.sprautestudio.spraute_engine.engine.EngineConfig.fromJson(text).toJson();
                            onClient(() -> {
                                if (Minecraft.getInstance().getConnection() != null) ClientState.get().engine(json);
                                return null;
                            });
                            return "Записано и применено.";
                        }
                        return "Записано." + afterChange(pname, dir, file, k);
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });

        tool("list_things", "Всё, что есть в проекте, по видам: сюжеты (с номерами), квесты, зоны, магазины, стили боя, эффекты, катсцены, интерфейсы, типы сущностей.",
                schema("project", "string", "Имя проекта", "+"), a -> {
                    try {
                        Path dir = projectDir(str(a, "project", ""));
                        Project pr = Project.openReadOnly(dir);
                        StringBuilder sb = new StringBuilder();
                        for (Project.Kind pk : Project.Kind.values()) {
                            Path kd = pr.kindDir(pk);
                            List<String> items = new ArrayList<>();
                            if (Files.isDirectory(kd)) try (var s = Files.walk(kd)) {
                                s.filter(p -> p.toString().endsWith(pk.ext)).sorted().forEach(p -> {
                                    String line = rel(dir, p);
                                    if (pk == Project.Kind.SCRIPT) {
                                        try {
                                            line += "  — " + Files.readString(p, StandardCharsets.UTF_8).lines().findFirst().orElse("");
                                        } catch (Exception ignored) {
                                        }
                                    }
                                    items.add(line);
                                });
                            }
                            if (!items.isEmpty()) sb.append(pk.name()).append(":\n  ").append(String.join("\n  ", items)).append('\n');
                        }
                        Path ed = pr.entitiesDir();
                        if (Files.isDirectory(ed)) try (var s = Files.list(ed)) {
                            List<String> es = s.filter(p -> p.toString().endsWith(".sent")).map(p -> rel(dir, p) + "  — character x = \"spraute:"
                                    + p.getFileName().toString().replace(".sent", "") + "\"").sorted().toList();
                            if (!es.isEmpty()) sb.append("ENTITY:\n  ").append(String.join("\n  ", es)).append('\n');
                        }
                        sb.append("Следующий свободный номер сюжета: ").append(pr.nextFreeId());
                        return sb.toString();
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });

        tool("assets", "Что можно использовать: models — модели (и их анимации), entities — типы сущностей spraute:…, classic — персонажи и сущности "
                        + "Spraute, fx — шаблоны эффектов, fights — шаблоны боя, animations — анимации модели (query = модель).",
                schema("kind", "string", "models | entities | fx | fights | animations", "+", "query", "string", "Фильтр / имя модели", "?"), a -> {
                    String kn = str(a, "kind", "models"), q = str(a, "query", "").toLowerCase(Locale.ROOT);
                    List<String> out = new ArrayList<>();
                    switch (kn) {
                        case "models" -> {
                            for (var m : org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.all())
                                if (q.isEmpty() || m.key().toLowerCase(Locale.ROOT).contains(q))
                                    out.add(m.key() + "  анимации: " + String.join(", ", org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.animations(m)));
                            out.add("встроенные: " + String.join(", ", org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.builtinModels()));
                        }
                        case "entities" -> {
                            for (var t : org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.all())
                                if (q.isEmpty() || t.key().toLowerCase(Locale.ROOT).contains(q))
                                    out.add("spraute:" + t.name() + "  (" + t.project() + ")  model " + t.def().model + (t.def().health > 0 ? ", hp " + t.def().health : ""));
                        }
                        case "fx" -> {
                            for (var p : org.sprautestudio.spraute_engine.fx.FxPresets.ALL.values())
                                if (q.isEmpty() || (p.name() + p.title() + p.doc()).toLowerCase(Locale.ROOT).contains(q))
                                    out.add(p.name() + " — " + p.title() + ": " + p.doc());
                        }
                        case "fights" -> org.sprautestudio.spraute_engine.fight.FightDef.PRESET_TITLES.forEach((k2, v) -> out.add(k2 + " — " + v));
                        case "animations" -> {
                            for (var m : org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.all())
                                if (q.isEmpty() || m.key().toLowerCase(Locale.ROOT).contains(q))
                                    out.add(m.key() + ": " + String.join(", ", org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.animations(m)));
                        }
                        default -> throw new IllegalArgumentException("kind: models | entities | fx | fights | animations");
                    }
                    return out.isEmpty() ? "Ничего" : String.join("\n", out);
                });

        tool("registry", "Ищет id в игре: item, block, entity, sound, effect (зелья), particle, enchantment, biome. Для give, shop, sound, summon…",
                schema("kind", "string", "item | block | entity | sound | effect | particle | enchantment", "+", "query", "string", "Часть id (например sword)", "?",
                        "limit", "integer", "Сколько (80)", "?"), a -> {
                    String q = str(a, "query", "").toLowerCase(Locale.ROOT);
                    int lim = (int) num(a, "limit", 80);
                    Registry<?> reg = switch (str(a, "kind", "item")) {
                        case "block" -> BuiltInRegistries.BLOCK;
                        case "entity" -> BuiltInRegistries.ENTITY_TYPE;
                        case "sound" -> BuiltInRegistries.SOUND_EVENT;
                        case "effect" -> BuiltInRegistries.MOB_EFFECT;
                        case "particle" -> BuiltInRegistries.PARTICLE_TYPE;
                        case "enchantment" -> BuiltInRegistries.ENCHANTMENT;
                        default -> BuiltInRegistries.ITEM;
                    };
                    List<String> out = new ArrayList<>();
                    for (ResourceLocation id : reg.keySet()) if (q.isEmpty() || id.toString().contains(q)) out.add(id.toString());
                    Collections.sort(out);
                    int total = out.size();
                    if (out.size() > lim) out = out.subList(0, lim);
                    return total == 0 ? "Ничего" : String.join("\n", out) + (total > lim ? "\n… ещё " + (total - lim) : "");
                });

        tool("convert_skin", "Делает из скина Minecraft (64×64 или 64×32) текстуру Spraute 64×64 или Super Rig 128×128 с глазами, бровями и ртом, "
                        + "которые движок анимирует. Источник — картинка проекта (image) или файл на диске (file). Результат — картинка «<имя> ST»; "
                        + "её можно дать типу сущности (texture \"image:<имя> ST\") или в сюжете: skin кто \"<имя> ST\".",
                schema("project", "string", "Имя проекта", "+", "image", "string", "Картинка проекта (имя без .png)", "?", "file", "string", "Или путь к PNG на диске", "?",
                        "name", "string", "Имя результата (по умолчанию «<имя> ST»)", "?",
                        "format", "string", "Формат результата: spraute (по умолчанию) или super_rig", "?",
                        "eyes", "integer", "Глаза на скине: 2 — 2×2, 1 — 2×1, 0 — найти самому", "?",
                        "offset", "integer", "Отступ глаз от низа лица в пикселях (-1 — найти)", "?",
                        "still", "integer", "Неподвижный глаз, остаётся на лице: 0 нет, 1 левый, 2 правый", "?",
                        "brows", "string", "Брови: THICK (3×1), THIN (2×1), ARROW (стрелками), NONE", "?",
                        "keep_face", "boolean", "Не стирать с лица глаза и брови", "?"), a -> {
                    try {
                        Project pr = Project.openReadOnly(projectDir(str(a, "project", "")));
                        Path src;
                        String base;
                        if (a.has("file")) {
                            src = Path.of(str(a, "file", ""));
                            base = src.getFileName().toString().replaceAll("\\.png$", "");
                        } else {
                            base = str(a, "image", "");
                            src = pr.imagesDir().resolve(base + ".png");
                        }
                        if (!Files.exists(src)) throw new IllegalArgumentException("Нет файла " + src);
                        var o = new org.sprautestudio.spraute_engine.editor.assets.FaceSkin.Options();
                        o.target = switch (str(a, "format", "spraute").toLowerCase(Locale.ROOT)) {
                            case "spraute" -> org.sprautestudio.spraute_engine.editor.assets.FaceSkin.Target.SPRAUTE;
                            case "super_rig", "superrig" -> org.sprautestudio.spraute_engine.editor.assets.FaceSkin.Target.SUPER_RIG;
                            default -> throw new IllegalArgumentException("Формат должен быть spraute или super_rig");
                        };
                        o.eyeH = (int) num(a, "eyes", 0);
                        o.offset = (int) num(a, "offset", -1);
                        o.still = (int) num(a, "still", 0);
                        o.brows = org.sprautestudio.spraute_engine.editor.assets.FaceSkin.Brows.valueOf(str(a, "brows", "THICK").toUpperCase(Locale.ROOT));
                        o.eraseFace = !bool(a, "keep_face", false);
                        var img = org.sprautestudio.spraute_engine.editor.assets.FaceSkin.read(src);
                        if (img.getWidth() == 128 && !a.has("keep_face")) o.eraseFace = false;
                        int[] eyes = org.sprautestudio.spraute_engine.editor.assets.FaceSkin.findEyes(img, o);
                        Path out = org.sprautestudio.spraute_engine.editor.assets.FaceSkin.convertFile(src, pr.imagesDir(),
                                Project.sanitize(str(a, "name", base + (o.target == org.sprautestudio.spraute_engine.editor.assets.FaceSkin.Target.SUPER_RIG ? " Super Rig" : " лицо"))), o);
                        Project.ImageRef ref = new Project.ImageRef(out);
                        onClient(() -> {
                            org.sprautestudio.spraute_engine.editor.assets.ProjectImages.load(pr, ref);
                            org.sprautestudio.spraute_engine.editor.SprauteScreen.fileChangedOutside(out);
                            return null;
                        });
                        return "Готово: картинка «" + ref.name() + "» (" + pr.dir.relativize(out).toString().replace('\\', '/') + "). Глаза: "
                                + (eyes[1] == 2 ? "2×2" : "2×1") + ", строки " + eyes[0] + ".." + (eyes[0] + eyes[1] - 1) + " скина, брови " + o.brows;
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });

        tool("here", "Координаты для зон, локаций, катсцен: где стоит игрок, куда смотрит и блок под прицелом, — готовые строки для сюжета и JSON.",
                schema(), a -> onServer(() -> {
                    ServerPlayer p = player();
                    var h = org.sprautestudio.spraute_engine.runtime.Raycast.look(new org.sprautestudio.spraute_engine.runtime.EntityRef(p), 64);
                    StringBuilder sb = new StringBuilder();
                    sb.append("Игрок: ").append(round(p.getX())).append(", ").append(round(p.getY())).append(", ").append(round(p.getZ()))
                            .append("  yaw ").append(round(p.getYRot())).append(" pitch ").append(round(p.getXRot())).append('\n');
                    sb.append("location здесь = (").append(round(p.getX())).append(", ").append(round(p.getY())).append(", ").append(round(p.getZ())).append(")\n");
                    sb.append("Блок под ногами: ").append(p.blockPosition().below().toShortString()).append('\n');
                    if (h.block() != null) sb.append("Под прицелом блок: ").append(h.block().toShortString()).append(" (грань ").append(h.face()).append(")\n");
                    if (h.entity() != null) sb.append("Под прицелом: ").append(McpTools.describe(h.entity())).append('\n');
                    return sb.toString();
                }));
    }
}
