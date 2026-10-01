package org.zonarstudio.spraute_engine.client.editor.scene;

import org.zonarstudio.spraute_engine.script.function.FunctionRegistry;
import org.zonarstudio.spraute_engine.script.function.ScriptFunction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.resource.SprauteDefaultNpcAssets;

/** Spraute (.spr) autocomplete entries and inline documentation for the code editor. */
public final class ScriptEditorHints {
    public record Entry(String key, String insert, String signature, String doc) {}

    /** Text to insert and caret position after insertion. */
    public record InsertResult(String text, int cursor) {}

    private static final Map<String, Entry> BY_KEY = new LinkedHashMap<>();
    private static final List<Entry> ALL = new ArrayList<>();

    static {
        registerBuiltins();
        registerFromRegistry();
    }

    private ScriptEditorHints() {}

    public static Entry lookup(String word) {
        if (word == null || word.isBlank()) return null;
        return BY_KEY.get(word.toLowerCase(Locale.ROOT));
    }

    /** Best hint for the identifier at {@code cursor}, or contextual create-block property. */
    public static Entry hintAt(String text, int cursor) {
        if (text == null) return null;
        cursor = Math.max(0, Math.min(cursor, text.length()));
        int[] bounds = wordBounds(text, cursor);
        if (bounds[0] < bounds[1]) {
            String word = text.substring(bounds[0], bounds[1]);
            Entry direct = lookup(word);
            if (direct != null) return direct;
            for (Entry e : dynamicEntries(text)) {
                if (e.key().equalsIgnoreCase(word)) return e;
            }
        }
        String line = linePrefixAt(text, cursor);
        if (!line.isEmpty()) {
            Entry lineHit = BY_KEY.get(line);
            if (lineHit != null) return lineHit;
            for (Entry e : ALL) {
                if (e.key().equalsIgnoreCase(line)) return e;
            }
        }
        String ctx = createContextType(text, cursor);
        if (ctx != null && bounds[0] < bounds[1]) {
            String prop = text.substring(bounds[0], bounds[1]).toLowerCase(Locale.ROOT);
            Entry ctxEntry = BY_KEY.get(ctx + "." + prop);
            if (ctxEntry != null) return ctxEntry;
        }
        if (ctx != null) {
            Entry block = BY_KEY.get("create." + ctx);
            if (block != null) return block;
        }
        return null;
    }

    public static List<Entry> complete(String prefix, String text, int cursor) {
        String word = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        String line = linePrefixAt(text, cursor);
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) {
            if (matchesCompletion(e, word, line)) out.add(e);
        }
        for (Entry e : dynamicEntries(text)) {
            if (matchesCompletion(e, word, line)) out.add(e);
        }
        String ctx = createContextType(text, cursor);
        if (ctx != null) {
            String ctxPrefix = ctx + ".";
            for (Entry e : ALL) {
                if (e.key().startsWith(ctxPrefix) && e.key().substring(ctxPrefix.length()).startsWith(word)) {
                    out.add(e);
                }
            }
        }
        out.sort((a, b) -> {
            int pa = completionPriority(a, word, line);
            int pb = completionPriority(b, word, line);
            if (pa != pb) return Integer.compare(pa, pb);
            boolean sa = a.insert().contains("\n");
            boolean sb = b.insert().contains("\n");
            if (sa != sb) return sa ? -1 : 1;
            return a.key().compareToIgnoreCase(b.key());
        });
        Map<String, Entry> unique = new LinkedHashMap<>();
        for (Entry e : out) unique.putIfAbsent(e.key().toLowerCase(Locale.ROOT), e);
        return new ArrayList<>(unique.values());
    }

    private static boolean matchesCompletion(Entry e, String word, String line) {
        String key = e.key().toLowerCase(Locale.ROOT);
        if (key.startsWith(word) || key.startsWith(line) || key.startsWith(line + " ")) return true;
        if (!line.isEmpty() && line.contains(" ") && key.startsWith(line)) return true;
        // "on" / "await" line: also match event name as the second token
        if (line.startsWith("on ") || line.equals("on")) {
            String rest = line.equals("on") ? word : line.substring(3).trim();
            if (key.startsWith("on ") && (rest.isEmpty() || key.substring(3).startsWith(rest))) return true;
        }
        if (line.startsWith("await ") || line.equals("await")) {
            String rest = line.equals("await") ? word : line.substring(6).trim();
            if (key.startsWith("await ") && (rest.isEmpty() || key.substring(6).startsWith(rest))) return true;
        }
        return false;
    }

    private static int completionPriority(Entry e, String word, String line) {
        String key = e.key().toLowerCase(Locale.ROOT);
        if (key.equals(line) || key.equals(word)) return 0;
        if (line.startsWith(key) || key.startsWith(line)) return 1;
        if (key.startsWith(word)) return 2;
        return 3;
    }

    public static String linePrefixAt(String text, int cursor) {
        if (text == null || text.isEmpty()) return "";
        int c = Math.max(0, Math.min(cursor, text.length()));
        int lineStart = text.lastIndexOf('\n', c - 1) + 1;
        return text.substring(lineStart, c).trim().toLowerCase(Locale.ROOT);
    }

    public static int[] wordBounds(String text, int cursor) {
        int start = Math.max(0, Math.min(cursor, text.length()));
        int end = start;
        while (start > 0 && isIdentPart(text.charAt(start - 1))) start--;
        while (end < text.length() && isIdentPart(text.charAt(end))) end++;
        return new int[]{start, end};
    }

    public static InsertResult resolveInsert(Entry entry) {
        if (entry == null) return new InsertResult("", 0);
        String ins = entry.insert();
        int bar = ins.indexOf('|');
        if (bar >= 0) {
            return new InsertResult(ins.substring(0, bar) + ins.substring(bar + 1), bar);
        }
        if (ins.contains("\n") || ins.contains("{")) {
            int body = ins.indexOf('{');
            if (body >= 0) {
                int nl = ins.indexOf('\n', body);
                int pos = nl >= 0 ? nl + 1 : ins.length();
                return new InsertResult(ins, pos);
            }
            return new InsertResult(ins, ins.length());
        }
        if (ins.contains("(") && !ins.endsWith("(") && !ins.endsWith("()")) {
            return new InsertResult(ins, ins.length());
        }
        if (entry.signature() != null && entry.signature().contains("(") && !ins.contains("(")) {
            String withParen = ins + "(|)";
            int marker = withParen.indexOf('|');
            return new InsertResult(withParen.substring(0, marker) + withParen.substring(marker + 1), marker);
        }
        return new InsertResult(ins, ins.length());
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Returns create-block type when cursor is inside `{ ... }`, e.g. {@code npc}. */
    private static String createContextType(String text, int cursor) {
        int depth = 0;
        int i = Math.min(cursor, text.length()) - 1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') {
                if (depth == 0) {
                    int j = i - 1;
                    while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
                    while (j >= 0 && isIdentPart(text.charAt(j))) j--;
                    while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
                    int typeEnd = j + 1;
                    while (j >= 0 && isIdentPart(text.charAt(j))) j--;
                    if (typeEnd <= j + 1) return null;
                    String type = text.substring(j + 1, typeEnd);
                    while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
                    if (j >= 5 && text.regionMatches(true, j - 5, "create", 0, 6)) {
                        return normalizeCreateType(type);
                    }
                    return null;
                }
                depth--;
            }
            i--;
        }
        return null;
    }

    private static String normalizeCreateType(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "npc", "model", "hitbox", "empty", "ui", "block", "item", "craft", "drop",
                    "world", "command", "tab" -> raw.toLowerCase(Locale.ROOT);
            case "particleemitter", "particle_emitter" -> "particleemitter";
            case "particle" -> "particle";
            case "audiosource", "audio_source", "audio" -> "audiosource";
            case "npc_prefab", "npcprefab" -> "npc_prefab";
            case "skin_overlay", "skinoverlay" -> "skin_overlay";
            default -> null;
        };
    }

    private static void registerBuiltins() {
        structure("val", "val name = |", "val name = …", EditorI18n.t("Локальная переменная"));
        structure("global", "global val name = |", "global val name = …", EditorI18n.t("Глобальная переменная скрипта"));
        structure("world", "world val name = |", "world val name = …", EditorI18n.t("Переменная сейва"));

        structure("fun", "fun name() {\n    |\n}", "fun name() { … }", EditorI18n.t("Объявление функции"));
        structure("return", "return |", "return …", EditorI18n.t("Возврат из fun"));

        structure("if", "if condition {\n    |\n}", "if condition { … }", EditorI18n.t("Условие"));
        structure("elif", "elif condition {\n    |\n}", "elif condition { … }", EditorI18n.t("Иначе если"));
        structure("else", "else {\n    |\n}", "else { … }", EditorI18n.t("Ветка else"));
        structure("while", "while condition {\n    |\n}", "while condition { … }", EditorI18n.t("Цикл while"));
        structure("for", "for (i in range(10)) {\n    |\n}", "for (i in range(n)) { … }", EditorI18n.t("Цикл for по range()"));
        structure("for in list", "for (x in |list) {\n    \n}", "for (x in list) { … }", EditorI18n.t("Цикл по элементам списка"));

        structure("await", "await time(|1)", "await time(seconds)", EditorI18n.t("Пауза / ожидание события (Tab — другие await)"));
        structure("async", "async {\n    |\n}", "async { … }", EditorI18n.t("Асинхронный блок"));
        structure("import", "import(\"|\")", "import(\"script\")", EditorI18n.t("Подключить и запустить скрипт"));

        structure("dialogClassic", EditorI18n.t("dialogClassic(|player, npc, \"Вопрос?\", list(\"Да\", \"Нет\"))"),
                "dialogClassic(player, npc, question, answers)",
                EditorI18n.t("Классическое меню выбора (кому показать = player)"));
        structure("dialogCinematic", EditorI18n.t("dialogCinematic(|player, npc, \"Вопрос?\", list(\"Да\", \"Нет\"))"),
                "dialogCinematic(player, npc, question, answers)",
                EditorI18n.t("Кинематографическое меню выбора + камера (кому показать = player)"));

        structure("on", "on interact(target) -> handler {\n    |\n}",
                "on event(…) -> id { … }", EditorI18n.t("Обработчик события (Tab — другие on)"));

        registerAllOnEvents();
        registerAllAwaitTriggers();

        structure("every", "every 1 -> tick {\n    |\n}",
                "every(seconds) -> id { … }", EditorI18n.t("Периодический таймер"));
        structure("stop", "stop(|handler)", "stop(handlerId)", EditorI18n.t("Остановить on/every-обработчик"));

        structure("create", "create npc id {\n    name = \"NPC\"\n    model = \"" + SprauteDefaultNpcAssets.MODEL + "\"\n    texture = \"" + SprauteDefaultNpcAssets.TEXTURE + "\"\n    animation = \"" + SprauteDefaultNpcAssets.ANIMATION + "\"\n    pos = 0, 64, 0\n}",
                "create … id { … }", EditorI18n.t("Создание объекта (Tab ещё раз — другие типы)"));
        structure("create npc", "create npc id {\n    name = \"NPC\"\n    model = \"" + SprauteDefaultNpcAssets.MODEL + "\"\n    texture = \"" + SprauteDefaultNpcAssets.TEXTURE + "\"\n    animation = \"" + SprauteDefaultNpcAssets.ANIMATION + "\"\n    pos = 0, 64, 0\n}",
                "create npc id { … }", EditorI18n.t("Создаёт или обновляет NPC"));
        structure("create model", "create model id {\n    model = \"geo/model.geo.json\"\n    texture = \"textures/entity/texture.png\"\n    pos = 0, 64, 0\n}",
                "create model id { … }", EditorI18n.t("Geo-модель без AI"));
        structure("create hitbox", "create hitbox id {\n    parent = _node\n    x = 0\n    y = 64\n    z = 0\n    width = 1\n    height = 2\n    depth = 1\n    solid = true\n    trigger = false\n}",
                "create hitbox id { … }", EditorI18n.t("Хитбокс сцены"));
        structure("create empty", "create empty id {\n    parent = _node\n    x = 0\n    y = 64\n    z = 0\n}",
                "create empty id { … }", EditorI18n.t("Пустая нода-якорь"));
        structure("create skin_overlay", "create skin_overlay hat {\n    parent = player\n    texture = \"textures/skins/hat.png\"\n}",
                "create skin_overlay id { … }", EditorI18n.t("Наложение скина на игрока"));
        structure("create particleEmitter", "create particleEmitter id {\n    parent = _node\n    type = \"minecraft:flame\"\n    shape = \"point\"\n    count = 8\n    speed = 0.05\n    loop = true\n    interval = 20\n}",
                "create particleEmitter id { … }", EditorI18n.t("Эмиттер частиц в сцене"));
        structure("create light", "create light id {\n    parent = _node\n    level = 15\n    enabled = true\n    x = 0\n    y = 64\n    z = 0\n}",
                "create light id { … }", EditorI18n.t("Свет сцены (minecraft:light 0–15)"));
        structure("create postProcess", "create postProcess look {\n    parent = player.camera\n    bloom = 0.35\n    vignette = 0.4\n}",
                "create postProcess id { … }", EditorI18n.t("Пост-обработка камеры игрока (bloom, vignette, цветокор)"));
        block("player.id",
                "player.id",
                EditorI18n.t("Id PLAYER-ноды этого игрока, как у любой ноды"),
                "_scene.child(player.id)\nplayer.camera.id\nplayer.audio.id\nplayer.camera.post.id");
        block("_node.id",
                "_node.id",
                EditorI18n.t("Id ноды сцены. У NPC после create npc — тот же id: guard.id"),
                "chat(_node.id)\nguard.id\nguard.node\n_node.parent.id");
        block("player.camera.post",
                "player.camera.post.bloom = 0.4",
                EditorI18n.t("Пост-эффекты CAMERA-ноды (создаётся при первом обращении)"),
                "player.camera.post.vignette = 0.5\nplayer.camera.post.enabled = true");
        block("player.camera.followPlayer",
                "player.camera.followPlayer = false",
                EditorI18n.t("false — камера в мировых координатах"),
                "player.camera.x = 100\nplayer.camera.yaw = 90");
        block("player.camera.lockX",
                "player.camera.lockX = true",
                EditorI18n.t("true — не следить по X, камера держит worldX"),
                "player.camera.lockX = true\nplayer.camera.worldX = 100");
        block("player.camera.lockY",
                "player.camera.lockY = true",
                EditorI18n.t("true — не следить по Y, камера держит worldY"),
                "player.camera.lockY = true\nplayer.camera.worldY = 70");
        block("player.camera.lockZ",
                "player.camera.lockZ = true",
                EditorI18n.t("true — не следить по Z, камера держит worldZ"),
                "player.camera.lockZ = true\nplayer.camera.worldZ = 0");
        block("player.camera.worldX",
                "player.camera.worldX = 100",
                EditorI18n.t("Мировая X, пока lockX"),
                "player.camera.lockX = true\nplayer.camera.worldX = 100");
        block("player.camera.holdAt",
                "player.camera.holdAt(100, 70, 0)",
                EditorI18n.t("Следовать за игроком, но держать камеру в этих мировых XYZ"),
                "player.camera.lockAxis(true, false, true)");
        block("player.camera.moveRelative",
                "player.camera.moveRelative = true",
                EditorI18n.t("true — WASD от взгляда камеры, не от тела"),
                "player.camera.lockYaw = true\nplayer.camera.moveRelative = true");
        block("player.camera.separateTurn",
                "player.camera.separateTurn = true",
                EditorI18n.t("true — мышь крутит камеру, игрок смотрит куда идёт"),
                "player.camera.distance = 4\nplayer.camera.separateTurn = true\nplayer.camera.turnSmooth = 0.4");
        block("player.camera.turnSmooth",
                "player.camera.turnSmooth = 0.4",
                EditorI18n.t("0 — мгновенный поворот тела, 1 — плавный"),
                "player.camera.separateTurn = true\nplayer.camera.turnSmooth = 0.4");
        block("player.camera.screenRaycast",
                "player.camera.screenRaycast = true",
                EditorI18n.t("true — рейкаст в центр экрана, не по телу игрока"),
                "player.camera.separateTurn = true\nplayer.camera.screenRaycast = true");
        block("player.camera.lookAtInteract",
                "player.camera.lookAtInteract = true",
                EditorI18n.t("При рубке, атаке и использовании тело смотрит на цель"),
                "player.camera.separateTurn = true\nplayer.camera.lookAtInteract = true");
        block("player.camera.disableEffects",
                "player.camera.disableEffects = true",
                EditorI18n.t("Выключить ванильный bobbing, тряску от урона и тошноту портала"),
                "player.camera.disableEffects = true\nplayer.camera.walkBob = true");
        block("player.camera.walkBob",
                "player.camera.walkBob = true",
                EditorI18n.t("Ход головы при ходьбе и приземлении (камера, не постпроцесс)"),
                "player.camera.walkBob = true\nplayer.camera.walkBobAmount = 1");
        block("player.camera.hideGui",
                "player.camera.hideGui = \"minecraft\"",
                EditorI18n.t("none / minecraft / all. all прячет ваниль и UI Spraute; затемнение экрана не входит — его снимает fadeOut или Ctrl+Shift+U"),
                "player.camera.hideGui = \"all\"\nplayer.camera.hideGui = \"none\"");
        block("player.camera.shake",
                "player.camera.shake(0.5, 0.4)",
                EditorI18n.t("Тряска камеры: сила, секунды, [частота]"),
                "player.camera.shake(0.6, 0.5, 18)\nshakeCamera(player, 0.4, 0.45)");
        block("player.camera.punch",
                "player.camera.punch(2, -4)",
                EditorI18n.t("Короткий удар камеры в градусах (yaw, pitch, [roll])"),
                "player.camera.punch(3, -5)\npunchCamera(player, 2, -4, 1)");
        block("player.camera.stopShake",
                "player.camera.stopShake()",
                EditorI18n.t("Снять тряску и удар камеры"),
                "player.camera.stopShake()\nstopCameraShake(player)");
        block("player.camera.lookAt",
                "player.camera.lookAt(x, y, z [, track, time])",
                EditorI18n.t("Направить камеру на точку или сущность. time=0 или без time — мгновенно"),
                "player.camera.lookAt(npc, true, 0.8)\nplayer.camera.lookAt(npc, true, 0)\nplayer.camera.stopLook()");
        block("player.camera.moveTo",
                "player.camera.moveTo(x, y, z, yaw, pitch, time, lock, hideGui, lockBlocks, afterEnd, …)",
                EditorI18n.t("Катсцена: подлёт. time=0 — резко. afterEnd: stay | return | hold"),
                "player.camera.moveTo(10, 80, 10, 90, 0, 0.8, true, \"minecraft\", true, \"return\", 0.8)");
        block("player.camera.followRoute",
                "player.camera.followRoute(route, time, lookAlong, lock, hideGui, lockBlocks, afterEnd, …)",
                EditorI18n.t("Пролёт по ROUTE. Интерп: interp, \"linear\" | \"smooth\" | \"smooth_camera\""),
                "player.camera.followRoute(path, 4, true, true, \"minecraft\", true, \"return\", 0.8, \"interp\", \"smooth\")");
        block("player.camera.returnToPlayer",
                "player.camera.returnToPlayer(0.5)\nplayer.camera.reset()",
                EditorI18n.t("Вернуть вид игроку: плавно или мгновенно"),
                "player.camera.returnToPlayer(0.8)\nplayer.camera.reset()");
        structure("declare section", EditorI18n.t("declare section Экономика\n"),
                EditorI18n.t("declare section Имя"), EditorI18n.t("Раздел в Hierarchy для custom-нод"));
        structure("declare inspector", EditorI18n.t("declare inspector {\n    section = \"Экономика\"\n    field canTrade bool { default = true }\n    field title text { default = \"Торговля\" }\n    field color color { }\n    field icon texture { }\n    list items {\n        field item item { }\n        field price number { default = 10 }\n    }\n}"),
                "declare inspector { … }", EditorI18n.t("Секции инспектора у родителя: повесь SCRIPT ребёнком на NPC/модель/…"));
        structure("declare node type", EditorI18n.t("declare node type economy {\n    section = \"Экономика\"\n    label = \"Торговля\"\n    field canTrade bool { default = true }\n    field title text { default = \"Торговля\" }\n    list items {\n        field item item { }\n        field buyPrice number { default = 10 }\n        field sellPrice number { default = 5 }\n        field canBuy bool { default = true }\n        field canSell bool { default = true }\n    }\n}"),
                "declare node type id { … }", EditorI18n.t("Схема ноды; economy — лавка на NPC через ecoOpenTrade"));
        structure("ecoOpenTrade", "ecoOpenTrade(player, items, title, npcId)\n",
                "ecoOpenTrade(player, items, …)", EditorI18n.t("UI трейда из economy.spr (монеты savedData)"));
        structure("create particle", "create particle id {\n    texture = \"textures/particle/spark.png\"\n}",
                "create particle id { … }", EditorI18n.t("Кастомный партикл spraute_engine:id → нода RES_PARTICLE в иерархии"));
        structure("create projectile", "create projectile fireball {\n    texture = \"textures/projectile/fire.png\"\n    renderStyle = \"cross\"\n    width = 0.5\n    height = 0.5\n}",
                "create projectile id { … }", EditorI18n.t("Тип снаряда → RES_PROJECTILE; shootProjectile(id, …)"));
        structure("create ui", EditorI18n.t("create ui name {\n    text title {\n        id = \"title\"\n        text = \"Заголовок\"\n        x = 10\n        y = 10\n    }\n    button ok {\n        id = \"ok\"\n        text = \"OK\"\n        x = 10\n        y = 40\n    }\n}"),
                "create ui name { … }", EditorI18n.t("Шаблон GUI"));
        structure("create block", "create block id {\n    texture = \"textures/block/block.png\"\n    hardness = 1.5\n}",
                "create block id { … }", EditorI18n.t("Кастомный блок"));
        structure("create item", EditorI18n.t("create item id {\n    texture = \"textures/item/item.png\"\n    name = \"Предмет\"\n}"),
                "create item id { … }", EditorI18n.t("Кастомный предмет"));

        block("create.npc",
                "create npc id { … }",
                EditorI18n.t("Создаёт или обновляет NPC. id — переменная с сущностью."),
                EditorI18n.t("Свойства: name, hp, speed, walkTurnSpeed, idleTurnSpeed, pathArriveRadius, pathStepHeight, pathMaxJump, flyArriveRadius, flyMaxRange, flyDiagonal, pos, rotate, model, texture, animation, idleAnim, walkAnim, ")
                        + "parent, scene, dimension, collision, collideBlocks, hitbox, drop_item, drop_chance, translucent, opacity, lod, renderDistance, forceVisible, loadChunks");
        ctxProp("npc", "name", EditorI18n.t("Отображаемое имя"));
        ctxProp("npc", "hp", EditorI18n.t("Здоровье и max HP"));
        ctxProp("npc", "speed", EditorI18n.t("Скорость движения"));
        ctxProp("npc", "pathArriveRadius", EditorI18n.t("Точность прибытия по земле (блоки, 0.1 = десятые)"));
        ctxProp("npc", "pathStepHeight", EditorI18n.t("Высота шага без прыжка (ступеньки, плиты)"));
        ctxProp("npc", "pathMaxJump", EditorI18n.t("Макс. высота прыжка при поиске пути"));
        ctxProp("npc", "pathMaxDrop", EditorI18n.t("Макс. спуск при поиске пути"));
        ctxProp("npc", "pathMaxRange", EditorI18n.t("Дальность поиска пути"));
        ctxProp("npc", "flyArriveRadius", EditorI18n.t("Точность прибытия в полёте (блоки, 0.1 = десятые)"));
        ctxProp("npc", "flyMaxRange", EditorI18n.t("Дальность поиска пути в воздухе"));
        ctxProp("npc", "pos", EditorI18n.t("x, y, z — позиция"));
        ctxProp("npc", "rotate", "yaw, pitch");
        ctxProp("npc", "model", "geo/….geo.json");
        ctxProp("npc", "texture", "textures/…");
        ctxProp("npc", "translucent", EditorI18n.t("true — альфа PNG смешивается; false — вырезание (cutout, стабильнее с шейдерами)"));
        ctxProp("npc", "opacity", EditorI18n.t("0 — невидимо, 1 — непрозрачно. Ниже 1 включает blend."));
        ctxProp("npc", "lod", EditorI18n.t("true/false — экранный LOD модели"));
        ctxProp("npc", "renderDistance", EditorI18n.t("Дальность от активной камеры; 0 — общая настройка"));
        ctxProp("npc", "forceVisible", EditorI18n.t("Приоритет катсцены: без LOD-скрытия и лимита дальности"));
        ctxProp("npc", "loadChunks", EditorI18n.t("Прогружать чанки вокруг NPC, чтобы он ходил без игрока рядом"));
        ctxProp("npc", "animation", "animations/….animation.json");
        ctxProp("npc", "collision", EditorI18n.t("Столкновения с сущностями"));
        ctxProp("npc", "collideBlocks", EditorI18n.t("Коллизия с блоками мира (false = проходит сквозь)"));
        ctxProp("npc", "parent", EditorI18n.t("Родительская нода сцены (id или _node)"));
        ctxProp("npc", "scene", EditorI18n.t("id сцены, если нет _scene"));

        block("create.model",
                "create model id { … }",
                EditorI18n.t("Geo-модель без AI."),
                EditorI18n.t("Свойства: name, model, texture, animation, idleAnim, pos, rotate, parent, scene, translucent, opacity, lod, renderDistance, forceVisible"));
        ctxProp("model", "model", "geo/….geo.json");
        ctxProp("model", "texture", "textures/…");
        ctxProp("model", "translucent", EditorI18n.t("true — альфа PNG смешивается; false — вырезание (cutout, стабильнее с шейдерами)"));
        ctxProp("model", "opacity", EditorI18n.t("0 — невидимо, 1 — непрозрачно. Ниже 1 включает blend."));
        ctxProp("model", "lod", EditorI18n.t("true/false — экранный LOD модели"));
        ctxProp("model", "renderDistance", EditorI18n.t("Дальность от активной камеры; 0 — общая настройка"));
        ctxProp("model", "forceVisible", EditorI18n.t("Приоритет катсцены: без LOD-скрытия и лимита дальности"));
        ctxProp("model", "pos", "x, y, z");

        block("create.hitbox",
                "create hitbox id { … }",
                EditorI18n.t("Хитбокс сцены (нужна активная сцена)."),
                EditorI18n.t("Свойства: parent, x, y, z, width, height, depth, solid, trigger"));
        ctxProp("hitbox", "width", EditorI18n.t("Ширина (блоки)"));
        ctxProp("hitbox", "height", EditorI18n.t("Высота"));
        ctxProp("hitbox", "depth", EditorI18n.t("Глубина"));
        ctxProp("hitbox", "solid", EditorI18n.t("true/false — физическая коллизия"));
        ctxProp("hitbox", "trigger", EditorI18n.t("true/false — событие пересечения"));
        ctxProp("hitbox", "triggerId", EditorI18n.t("id для on/await hitboxTouch (иначе id ноды)"));

        block("create.empty",
                "create empty id { … }",
                EditorI18n.t("Пустая нода-якорь в иерархии сцены."),
                EditorI18n.t("Свойства: parent, x, y, z, name"));

        block("create.particleemitter",
                "create particleEmitter id { … }",
                EditorI18n.t("Эмиттер частиц в сцене (нужна активная сцена)."),
                EditorI18n.t("Свойства: type, shape, count, speed, spread, loop, interval, parent, x, y, z"));
        ctxProp("particleemitter", "type", EditorI18n.t("ID частицы: minecraft:flame или spraute_engine:my_particle"));
        ctxProp("particleemitter", "shape", "point | sphere | line | circle | cone");
        ctxProp("particleemitter", "count", EditorI18n.t("Количество за burst"));
        ctxProp("particleemitter", "speed", EditorI18n.t("Скорость"));
        ctxProp("particleemitter", "spread", EditorI18n.t("spreadX, spreadY, spreadZ — разброс"));
        ctxProp("particleemitter", "loop", EditorI18n.t("true — повторять; false — только node.emit()"));
        ctxProp("particleemitter", "interval", EditorI18n.t("Тиков между burst (при loop)"));
        ctxProp("particleemitter", "radius", EditorI18n.t("Радиус (sphere/circle)"));
        ctxProp("particleemitter", "height", EditorI18n.t("Высота (circle/cylinder)"));
        ctxProp("particleemitter", "dir", EditorI18n.t("dirX, dirY, dirZ — направление (cone)"));
        ctxProp("particleemitter", "parent", EditorI18n.t("Родитель в иерархии"));

        block("create.light",
                "create light id { … }",
                EditorI18n.t("Источник света в сцене (нужна активная сцена). Ставит невидимый minecraft:light."),
                EditorI18n.t("Свойства: level (0–15), enabled, parent, x, y, z. Цветной свет пока не поддерживается."));
        ctxProp("light", "level", EditorI18n.t("Уровень блока 0–15 (белый)"));
        ctxProp("light", "enabled", EditorI18n.t("true/false — выключить без удаления ноды"));
        ctxProp("light", "parent", EditorI18n.t("Родитель в иерархии"));

        block("create.audiosource",
                "create audioSource id { … }",
                EditorI18n.t("Позиционный звук с зоной затухания (сфера/куб)."),
                EditorI18n.t("Свойства: sound, shape, fullRadius, maxDistance, volume, pitch, loop, replayOnEnter, enabled, parent, x, y, z"));
        ctxProp("audiosource", "sound", EditorI18n.t("ID звука, напр. minecraft:ambient.cave"));
        ctxProp("audiosource", "loop", EditorI18n.t("true — зациклить"));
        ctxProp("audiosource", "replayOnEnter", EditorI18n.t("Воспроизводить заново при входе в зону слышимости"));
        ctxProp("audiosource", "fullRadius", EditorI18n.t("Радиус полной громкости"));
        ctxProp("audiosource", "maxDistance", EditorI18n.t("Дальность слышимости"));

        block("create.postprocess",
                "create postProcess id { … }",
                EditorI18n.t("Post-process volume: bloom, vignette, color grading. Нода = код, как NPC."),
                EditorI18n.t("Свойства: global, priority, weight, blendDistance, bloom, vignette, chromatic, grain, sharpen, exposure, contrast, saturation, parent, x, y, z"));
        ctxProp("postprocess", "global", EditorI18n.t("true — на всю камеру; false — box (scale ноды)"));
        ctxProp("postprocess", "priority", EditorI18n.t("Меньший блендится первым, больший перекрывает"));
        ctxProp("postprocess", "bloom", EditorI18n.t("Интенсивность bloom (включает override)"));
        ctxProp("postprocess", "vignette", EditorI18n.t("Интенсивность виньетки"));
        ctxProp("postprocess", "exposure", EditorI18n.t("Экспозиция color grading"));

        block("declare.nodetype",
                "declare node type id { … }",
                EditorI18n.t("Схема кастомной ноды. После сохранения .spr тип появляется в Hierarchy."),
                EditorI18n.t("Поля: field name kind { label, default }, list name { field… }. kind: text, number, bool, color, item, block, dropdown, button, curve. economy → ecoOpenTrade."));
        block("declare.inspector",
                "declare inspector { … }",
                EditorI18n.t("Секции инспектора у родителя: повесь SCRIPT ребёнком на NPC/модель/…"),
                EditorI18n.t("Поля как у declare node type. Данные на родителе: _node.field / _node.list"));
        block("ecoOpenTrade",
                "ecoOpenTrade(player, items, title, npcId)",
                EditorI18n.t("UI трейда из economy.spr (монеты savedData coins)."),
                EditorI18n.t("items — список dict(id, name, buy, sell, canBuy, canSell)."));

        block("create.particle",
                "create particle id { … }",
                EditorI18n.t("Регистрирует кастомный партикл spraute_engine:id и создаёт RES_PARTICLE в иерархии."),
                EditorI18n.t("Свойства: texture — путь к текстуре"));
        ctxProp("particle", "texture", "textures/particle/….png");

        block("create.ui",
                "create ui name { … }",
                EditorI18n.t("Шаблон GUI (виджеты: text, button, image, svg, rect, …)."),
                EditorI18n.t("См. metods.md — create ui"));
        structure("svg", "svg(\"icon\") {\n    source = \"textures/gui/icon.svg\"\n    size = [32, 32]\n    color = \"#FFFFFFFF\"\n}",
                "svg(id [, source]) { color, stroke, strokeWidth, recolor }",
                EditorI18n.t("Виджет SVG в create ui. Цвет подменяет currentColor, source — путь в проекте."));

        fn("particleSpawn", "particleSpawn(type, x, y, z, count, dx, dy, dz, speed)",
                EditorI18n.t("Разовый burst частиц в точке. type: minecraft:flame или spraute_engine:id"));
        fn("particleEmit", "particleEmit(node) | particleEmit(\"id\")",
                EditorI18n.t("Разовая вспышка PARTICLE-ноды. Синонимы: node.emit(), node.burst()"));
        fn("particleLine", "particleLine(type, x1,y1,z1, x2,y2,z2, count, dx,dy,dz, speed)",
                EditorI18n.t("Частицы вдоль отрезка"));
        fn("particleCircle", "particleCircle(type, x,y,z, radius, count, …)",
                EditorI18n.t("Кольцо частиц в горизонтальной плоскости"));
        fn("particleSpiral", "particleSpiral(type, x,y,z, radius, height, turns, count, …)",
                EditorI18n.t("Спираль частиц"));
        fn("particleStartBone", "particleStartBone(npc, bone, type, count, dx,dy,dz, speed)",
                EditorI18n.t("Частицы из кости NPC (до particleStopBone)"));
        fn("particleStopBone", "particleStopBone(npc, taskId)", EditorI18n.t("Остановить bone-частицы"));

        fn("sceneLoad", "sceneLoad(sceneId)", EditorI18n.t("Загрузить сцену /spraute run"));
        fn("sceneUnload", "sceneUnload(sceneId)", EditorI18n.t("Выгрузить сцену"));
        fn("sceneGetNode", "sceneGetNode(pathOrId) | sceneGetNode(sceneId, pathOrId)",
                EditorI18n.t("Нода текущей сцены (или указанной): id / путь"));
        fn("sceneFindNode", "sceneFindNode(name) | sceneFindNode(sceneId, name)",
                EditorI18n.t("Нода по имени в текущей сцене"));
        fn("sceneFindNodeById", "sceneFindNodeById(id) | sceneFindNodeById(sceneId, id)",
                EditorI18n.t("Нода по точному id в текущей сцене"));
        fn("sceneFindByTag", "sceneFindByTag(tag) | sceneFindByTag(sceneId, tag)",
                EditorI18n.t("Первая нода с тегом в текущей сцене"));
        fn("sceneNodesWithTag", "sceneNodesWithTag(tag) | sceneNodesWithTag(sceneId, tag)",
                EditorI18n.t("Все ноды с тегом в текущей сцене"));
        fn("sceneHasNode", "sceneHasNode(pathOrId) | sceneHasNode(sceneId, pathOrId)",
                EditorI18n.t("Есть ли нода в текущей сцене"));
        fn("sceneNodeForEntity", "sceneNodeForEntity(entity)",
                EditorI18n.t("Нода сцены по NPC/модели; то же, что entity.node"));
        fn("routeCreate", "routeCreate(id [, closed])",
                EditorI18n.t("Создать путь; методы: addPoint/removePoint/setPoint/point/pointCount/clearPoints"));
        fn("routeGet", "routeGet(id)",
                EditorI18n.t("Получить ROUTE-ноду текущей сцены"));
        fn("prefabSpawn", "prefabSpawn(sceneId, prefab, x, y, z)", EditorI18n.t("Заспавнить префаб в сцене"));
        fn("prefabSpawnIn", "prefabSpawnIn(sceneId, prefab, parent, x, y, z)", EditorI18n.t("Префаб под родителем"));
        fn("spawnNpcPrefab", "spawnNpcPrefab(prefabId, instanceId, x, y, z [, overrides])",
                EditorI18n.t("Спавн NPC по шаблону create npc_prefab"));

        fn("registerEvent", "registerEvent(\"myEvent\")",
                EditorI18n.t("Зарегистрировать своё on/await событие"));
        fn("fireEvent", "fireEvent(\"myEvent\", player, …)",
                EditorI18n.t("Вызвать своё событие (on/await myEvent)"));
        fn("isEventRegistered", "isEventRegistered(\"myEvent\")",
                EditorI18n.t("Событие зарегистрировано через registerEvent?"));

        fn("fadeIn", "fadeIn([player], [timeSec], [text], [subtitle])",
                EditorI18n.t("Затемнить экран поверх GUI. hide_gui=true — спрятать HUD, а не накрыть. Блок: time, timeOut, visibleTime, text, subtitle, fadeout, hide_gui"));
        fn("fadeOut", "fadeOut([player], [timeSec], [text], [subtitle])",
                EditorI18n.t("Осветлить экран за time сек; можно сменить текст. Не зависит от hideGui"));
        fn("uiOpen", "uiOpen(player, templateOrCanvas)", EditorI18n.t("Открыть UI или канвас сцены"));
        fn("hideHud", "hideHud(player, part [, hidden])",
                EditorI18n.t("Скрыть часть классического HUD: health, hunger, hotbar, xp, …"));
        fn("showHud", "showHud(player [, part])", EditorI18n.t("Показать часть HUD или весь, если part не указан"));
        fn("isHudHidden", "isHudHidden(player, part)", EditorI18n.t("Скрыта ли часть классического HUD"));
        fn("uiClose", "uiClose(player [, id])", EditorI18n.t("Закрыть UI"));
        fn("uiUpdate", "uiUpdate(player, widgetId, field, value)",
                EditorI18n.t("Обновить поле виджета (text, color, source, stroke, strokeWidth, recolor, fillAmount, fill, src, …)"));
        fn("uiVideoPlay", "uiVideoPlay(player, widgetId)", EditorI18n.t("Запустить или продолжить видео UI"));
        fn("uiVideoPause", "uiVideoPause(player, widgetId)", EditorI18n.t("Поставить видео UI на паузу"));
        fn("uiVideoStop", "uiVideoStop(player, widgetId)", EditorI18n.t("Остановить видео UI и сбросить в начало"));
        fn("uiVideoRestart", "uiVideoRestart(player, widgetId)", EditorI18n.t("Воспроизвести видео UI заново с начала"));
        fn("uiVideoEnable", "uiVideoEnable(player, widgetId, enabled)", EditorI18n.t("Включить или отключить видеоплеер UI"));
        fn("uiVideoTime", "uiVideoTime(player, widgetId)", EditorI18n.t("Текущая секунда видео UI"));
        fn("uiVideoDuration", "uiVideoDuration(player, widgetId)", EditorI18n.t("Длительность видео UI в секундах"));
        fn("uiVideoPlaying", "uiVideoPlaying(player, widgetId)", EditorI18n.t("Воспроизводится ли видео UI"));
        fn("uiVideoEnded", "uiVideoEnded(player, widgetId)", EditorI18n.t("Закончилось ли видео UI"));
        fn("uiVideoResume", "uiVideoResume(player, widgetId)", EditorI18n.t("Запустить или продолжить видео UI"));
        fn("giveItem", "giveItem(player, \"id{nbt}\" [, count, name, lore, nbt])", EditorI18n.t("Выдать предмет. NBT — как в /give внутри id, либо 6-м аргументом"));
        fn("chat", "chat(message)", EditorI18n.t("Сообщение источнику скрипта"));
        fn("say", "say(who, message)", EditorI18n.t("Сообщение всем игрокам"));
        fn("getChatNameFormat", "getChatNameFormat()",
                EditorI18n.t("Формат имени из настроек ($Name — имя говорящего)"));
        fn("getChatNameColor", "getChatNameColor()",
                EditorI18n.t("Цвет имени из настроек (#RRGGBB)"));
        fn("formatChatName", "formatChatName(name)",
                EditorI18n.t("Подставить имя в формат из настроек"));
        fn("execute", "execute(command)", EditorI18n.t("Выполнить команду"));
        fn("getPlayer", "getPlayer(name)", EditorI18n.t("Игрок по нику"));
        fn("getPlayerStats", "getPlayerStats(player)", EditorI18n.t("Словарь характеристик: hp, hunger, selectedSlot, …"));
        fn("setPlayerHealthEnabled", "setPlayerHealthEnabled(player, enabled)",
                EditorI18n.t("Выкл: нет урона и регена, сердца скрыты. /kill работает"));
        fn("setPlayerHungerEnabled", "setPlayerHungerEnabled(player, enabled)",
                EditorI18n.t("Выкл: голод не падает, полоска скрыта"));
        fn("setPlayerXpVisible", "setPlayerXpVisible(player, visible)",
                EditorI18n.t("Выкл: скрыть полоску и цифру опыта. XP копится как обычно"));
        fn("setPlayerVitalsEnabled", "setPlayerVitalsEnabled(player, enabled)",
                EditorI18n.t("HP, голод и полоска опыта сразу"));
        fn("isPlayerHealthEnabled", "isPlayerHealthEnabled(player)", EditorI18n.t("Работает ли механика здоровья"));
        fn("isPlayerHungerEnabled", "isPlayerHungerEnabled(player)", EditorI18n.t("Работает ли механика голода"));
        fn("isPlayerXpVisible", "isPlayerXpVisible(player)", EditorI18n.t("Видна ли полоска опыта"));
        fn("playerLanguage", "playerLanguage([player])", EditorI18n.t("Язык клиента игрока (en_us, ru_ru, …)"));
        fn("playerLang", "playerLang([player])", EditorI18n.t("Короткий код языка (en, ru, …)"));
        fn("isEnglish", "isEnglish([player])", EditorI18n.t("true, если язык игрока английский"));
        fn("t", "t(ru, en [, player])", EditorI18n.t("RU/EN строка по языку игрока"));
        fn("translate", "translate(player, {en, ru}) | translate({…})", EditorI18n.t("Выбрать текст из словаря по языку"));
        fn("tr", "tr(key [, player] [, …args|dict])", EditorI18n.t("Перевод из spraute_engine/lang (плейсхолдеры {0} / {name})"));
        fn("hasTr", "hasTr(key [, player])", EditorI18n.t("Есть ли ключ перевода"));
        fn("languages", "languages()", EditorI18n.t("Список загруженных кодов языка"));
        fn("format", "format(text, …args|dict)", EditorI18n.t("Подставить {0} / {name} / %s в строку"));
        fn("reloadLang", "reloadLang()", EditorI18n.t("Перечитать lang/*.json сейчас"));
        fn("random", "random([min, max])", EditorI18n.t("Случайное число"));
        fn("lerp", "lerp(a, b, t [, easing]) | lerp(target, prop, end, time [, easing])",
                EditorI18n.t("Интерполяция или tween: время + плавность"));
        fn("lerpAngle", "lerpAngle(a, b, t [, easing])", EditorI18n.t("Угол (кратчайший путь), опц. easing"));
        fn("lerpColor", "lerpColor(from, to, t [, easing])", EditorI18n.t("Цвет #hex, опц. easing"));
        fn("ease", "ease(t, mode)", EditorI18n.t("Кривая: linear, easeIn, easeOut, easeInOut, bounceOut, elasticOut"));
        fn("curveEval", "curveEval(curve, t)", EditorI18n.t("Сэмпл кривой кастом-ноды (field x curve)"));
        method("eval", "curve.eval(t)", EditorI18n.t("Сэмпл кривой 0…1"));
        fn("lerpTo", "lerpTo(target, prop, end, time [, easing])", EditorI18n.t("То же что timed lerp(...); await time(...)"));
        fn("lerpStop", "lerpStop(target [, prop])", EditorI18n.t("Остановить lerp tween"));
        fn("dict", "dict(key1, val1, …)", EditorI18n.t("Словарь"));
        fn("list", "list()", EditorI18n.t("Пустой список"));

        method("moveTo", "npc.moveTo(x, y, z [, speed])", EditorI18n.t("Путь к точке; await — ждать прибытия"));
        method("flyTo", "npc.flyTo(x, y, z [, speed])", EditorI18n.t("Полёт к точке"));
        method("followRoute", "npc.followRoute(route [, speed])", EditorI18n.t("Патсфайндинг по точкам пути (обходит блоки), не вдоль кривой"));
        method("alwaysFollowRoute", "npc.alwaysFollowRoute(route [, speed])", EditorI18n.t("Идти по пути по кругу: с последней точки к первой"));
        method("routeIndex", "npc.routeIndex", EditorI18n.t("Индекс текущей точки пути (-1 если не идёт)"));
        method("routePointCount", "npc.routePointCount", EditorI18n.t("Сколько точек в активном пути"));
        method("followingRoute", "npc.followingRoute", EditorI18n.t("Идёт ли сейчас по пути"));
        method("flyRoute", "npc.flyRoute(route [, speed])", EditorI18n.t("Лететь по пути один раз"));
        method("alwaysFlyRoute", "npc.alwaysFlyRoute(route [, speed])", EditorI18n.t("Лететь по пути по кругу"));
        method("pickupMax", "npc.pickupMax(\"minecraft:iron_ingot\", 5 [, nbt])",
                EditorI18n.t("Не подбирать этот предмет сверх N штук"));
        method("clearPickupMax", "npc.clearPickupMax([item [, nbt]])",
                EditorI18n.t("Снять лимит подбора (все или один предмет)"));
        method("pickupAny", "npc.pickupAny()", EditorI18n.t("Снять фильтр pickupOnlyFrom"));
        method("camera", "player.camera", EditorI18n.t("Камера игрока: смещение, дистанция, FOV"));
        method("addOverlay", "player.addOverlay(id, texture)", EditorI18n.t("Добавить наложение скина на игрока"));
        method("overlay", "player.overlay(id)", EditorI18n.t("Наложение скина игрока по id"));
        method("resetSkin", "player.resetSkin()", EditorI18n.t("Сбросить скин игрока"));
        method("hideSkinLayer", "player.hideSkinLayer(\"hat\")", EditorI18n.t("Скрыть второй слой оригинального/установленного скина: hat, jacket, sleeves, torso (куртка+рукава), pants, cape, all. Наложения не трогает"));
        method("showSkinLayer", "player.showSkinLayer(\"jacket\")", EditorI18n.t("Показать второй слой оригинального скина"));
        method("resetSkinLayers", "player.resetSkinLayers()", EditorI18n.t("Вернуть все вторые слои оригинального скина"));
        ctxProp("player", "id", EditorI18n.t("Id PLAYER-ноды этого игрока, как у любой ноды"));
        ctxProp("camera", "id", EditorI18n.t("Id CAMERA-ноды"));
        ctxProp("audio", "id", EditorI18n.t("Id аудио-шины игрока"));
        ctxProp("postProcess", "id", EditorI18n.t("Id пост-эффекта камеры"));
        method("id", "npc.id / _node.id", EditorI18n.t("Id ноды сцены или ключ create npc"));
        method("node", "npc.node", EditorI18n.t("Нода сцены этой сущности (null, если спавн вне сцены)"));
        method("parent", "_node.parent", EditorI18n.t("Родительская нода"));
        method("children", "_node.children", EditorI18n.t("Дочерние ноды"));
        ctxProp("player", "hideHat", EditorI18n.t("true — скрыть шапку оригинального или установленного скина"));
        ctxProp("player", "hideJacket", EditorI18n.t("true — скрыть куртку оригинального или установленного скина"));
        ctxProp("player", "hideLeftSleeve", EditorI18n.t("true — скрыть левый рукав оригинального скина"));
        ctxProp("player", "hideRightSleeve", EditorI18n.t("true — скрыть правый рукав оригинального скина"));
        ctxProp("player", "hideLeftPants", EditorI18n.t("true — скрыть левую штанину оригинального скина"));
        ctxProp("player", "hideRightPants", EditorI18n.t("true — скрыть правую штанину оригинального скина"));
        ctxProp("player", "hideCape", EditorI18n.t("true — скрыть плащ"));
        ctxProp("player", "skin", EditorI18n.t("Скин игрока (путь, URL или пусто = ваниль)"));
        ctxProp("skin_overlay", "parent", EditorI18n.t("PLAYER-нода или игрок"));
        ctxProp("skin_overlay", "texture", EditorI18n.t("Полная текстура скина поверх текущего"));
        ctxProp("skin_overlay", "active", EditorI18n.t("true — слой виден, включая руку от 1 лица"));
        ctxProp("skin_overlay", "hideHat", EditorI18n.t("true — скрыть шапку (2 слой) этого наложения"));
        ctxProp("skin_overlay", "hideJacket", EditorI18n.t("true — скрыть куртку (2 слой) этого наложения"));
        ctxProp("skin_overlay", "hideLeftSleeve", EditorI18n.t("true — скрыть левый рукав этого наложения"));
        ctxProp("skin_overlay", "hideRightSleeve", EditorI18n.t("true — скрыть правый рукав этого наложения"));
        ctxProp("skin_overlay", "hideLeftPants", EditorI18n.t("true — скрыть левую штанину этого наложения"));
        ctxProp("skin_overlay", "hideRightPants", EditorI18n.t("true — скрыть правую штанину этого наложения"));
        ctxProp("skin_overlay", "hideSleeves", EditorI18n.t("true — скрыть оба рукава этого наложения"));
        ctxProp("skin_overlay", "hidePants", EditorI18n.t("true — скрыть обе штанины этого наложения"));
        method("hideSkinLayer", "overlay.hideSkinLayer(\"hat\")", EditorI18n.t("Скрыть второй слой наложения: hat, jacket, sleeves, torso, pants, all"));
        method("showSkinLayer", "overlay.showSkinLayer(\"jacket\")", EditorI18n.t("Показать второй слой наложения"));
        method("resetSkinLayers", "overlay.resetSkinLayers()", EditorI18n.t("Вернуть все вторые слои наложения"));
        method("moveTo", "cam.moveTo(x, y, z [, yaw, pitch, time])", EditorI18n.t("Камера едет к точке; time=0 — резко, иначе flyTime"));
        method("lookAt", "cam.lookAt(target [, track [, time]])", EditorI18n.t("Камера смотрит на цель; time=0 — резко"));
        method("followRoute", "cam.followRoute(route [, duration [, lookAlong [, …, \"interp\", type]]])", EditorI18n.t("Камера летит по ROUTE; interp: linear / smooth / smooth_camera"));
        method("returnToPlayer", "cam.returnToPlayer([time])", EditorI18n.t("Вернуть камеру в rest-позу игрока"));
        method("setRest", "cam.setRest(x, y, z [, yaw, pitch, distance, fov])", EditorI18n.t("Задать rest-позу камеры игрока"));
        method("setOrbit", "cam.setOrbit(orbitYaw, orbitPitch [, distance, pivotX, pivotY, pivotZ])", EditorI18n.t("Орбита вокруг точки: камера объезжает игрока и смотрит на него"));
        method("setPivot", "cam.setPivot(x, y, z)", EditorI18n.t("Точка вращения орбиты: смещение от глаз (X вправо, Y вверх, Z назад)"));
        method("lockRotation", "cam.lockRotation([yaw, pitch])", EditorI18n.t("Заморозить оси камеры: ось держит мировой угол вместо взгляда игрока"));
        method("lockAxis", "cam.lockAxis([x, y, z])", EditorI18n.t("Не следить по осям: true — держать мировую координату"));
        method("holdAt", "cam.holdAt(x, y, z)", EditorI18n.t("Следовать за игроком, но оставить камеру в этих мировых XYZ"));
        ctxProp("camera", "lockYaw", EditorI18n.t("true — поворот камеры не следует за игроком"));
        ctxProp("camera", "lockPitch", EditorI18n.t("true — наклон камеры не следует за игроком"));
        ctxProp("camera", "lockX", EditorI18n.t("true — не следить по X, камера держит worldX"));
        ctxProp("camera", "lockY", EditorI18n.t("true — не следить по Y, камера держит worldY"));
        ctxProp("camera", "lockZ", EditorI18n.t("true — не следить по Z, камера держит worldZ"));
        ctxProp("camera", "followX", EditorI18n.t("false — то же, что lockX = true"));
        ctxProp("camera", "followY", EditorI18n.t("false — то же, что lockY = true"));
        ctxProp("camera", "followZ", EditorI18n.t("false — то же, что lockZ = true"));
        ctxProp("camera", "worldX", EditorI18n.t("Мировая X, пока lockX"));
        ctxProp("camera", "worldY", EditorI18n.t("Мировая Y, пока lockY"));
        ctxProp("camera", "worldZ", EditorI18n.t("Мировая Z, пока lockZ"));
        ctxProp("camera", "orbit", EditorI18n.t("true — камера висит на точке и смотрит на неё"));
        ctxProp("camera", "orbitYaw", EditorI18n.t("Угол объезда вокруг точки, градусы"));
        ctxProp("camera", "orbitPitch", EditorI18n.t("Подъём орбиты над точкой, градусы"));
        ctxProp("camera", "collide", EditorI18n.t("true — камера не проходит сквозь блоки"));
        ctxProp("camera", "clearOcclusion", EditorI18n.t("true — скрывать блоки между камерой и целью"));
        ctxProp("camera", "occlusionAlpha", EditorI18n.t("0 = невидимы, 0.35 = полупрозрачные кубы"));
        ctxProp("camera", "occlusionRadius", EditorI18n.t("Толщина коридора скрытия в блоках, 0 = только прямая"));
        ctxProp("camera", "showHand", EditorI18n.t("true — рука и предмет остаются на экране даже при смещении камеры"));
        ctxProp("camera", "hideGui", EditorI18n.t("none / minecraft / all. Затемнение экрана отдельно — fadeOut / Ctrl+Shift+U"));
        ctxProp("camera", "moveRelative", EditorI18n.t("true — WASD от взгляда камеры, не от тела"));
        ctxProp("camera", "separateTurn", EditorI18n.t("true — мышь крутит камеру, тело смотрит куда идёт"));
        ctxProp("camera", "turnSmooth", EditorI18n.t("0 — мгновенный поворот тела, 1 — плавный"));
        ctxProp("camera", "screenRaycast", EditorI18n.t("true — рейкаст в центр экрана, не по телу игрока"));
        ctxProp("camera", "lookAtInteract", EditorI18n.t("true — при рубке, атаке и использовании тело смотрит на цель"));
        ctxProp("camera", "disableEffects", EditorI18n.t("true — выключить ванильный bobbing, удар и тошноту"));
        ctxProp("camera", "walkBob", EditorI18n.t("true — ход головы при ходьбе (камера, не постпроцесс)"));
        ctxProp("camera", "walkBobAmount", EditorI18n.t("Сила хода головы, 0…2"));
        method("shake", "cam.shake(intensity [, duration [, frequency]])", EditorI18n.t("Тряска камеры как Unreal CameraShake"));
        method("punch", "cam.punch(yaw, pitch [, roll])", EditorI18n.t("Короткий удар камеры в градусах"));
        method("stopShake", "cam.stopShake()", EditorI18n.t("Снять тряску и удар камеры"));
        method("playOnce", "npc.playOnce(anim [, additive])", EditorI18n.t("Анимация один раз (MODEL: replace по умолчанию)"));
        method("playLoop", "model.playLoop(anim [, additive])", EditorI18n.t("Цикл анимации на NPC/MODEL"));
        method("playFreeze", "npc.playFreeze(anim [, additive])", EditorI18n.t("Анимация до последнего кадра и стоп"));
        method("stop", "npc.stop(anim)", EditorI18n.t("Остановить overlay-анимацию"));
        method("emit", "particleNode.emit()", EditorI18n.t("Разовая вспышка PARTICLE-ноды. Синоним: particleEmit(node)"));
        method("burst", "particleNode.burst()", EditorI18n.t("Синоним emit() для PARTICLE-ноды"));
        method("play", "audioNode.play()", EditorI18n.t("Начать воспроизведение AUDIO_SOURCE с начала (синонимы: startPlay, restart)"));
        method("startPlay", "audioNode.startPlay()", EditorI18n.t("Синоним play() для AUDIO_SOURCE"));
        method("teleport", "entity.teleport(x, y, z)", EditorI18n.t("Телепорт"));
        method("remove", "node.remove()", EditorI18n.t("Удалить ноду сцены и детей"));
        method("reparent", "node.reparent(parentNode)", EditorI18n.t("Сменить родителя"));
        method("hp", "player.hp", EditorI18n.t("Здоровье игрока"));
        method("maxHp", "player.maxHp", EditorI18n.t("Максимальное здоровье"));
        method("hunger", "player.hunger", EditorI18n.t("Голод 0–20"));
        method("saturation", "player.saturation", EditorI18n.t("Насыщение"));
        method("selectedSlot", "player.selectedSlot", EditorI18n.t("Выбранный слот хотбара 0–8"));
        method("air", "player.air", EditorI18n.t("Запас воздуха"));
        method("armor", "player.armor", EditorI18n.t("Очки брони"));
        method("xpLevel", "player.xpLevel", EditorI18n.t("Уровень опыта"));
        method("x", "player.x", EditorI18n.t("Мировая координата X. Запись телепортирует"));
        method("y", "player.y", EditorI18n.t("Мировая координата Y. Запись телепортирует"));
        method("z", "player.z", EditorI18n.t("Мировая координата Z. Запись телепортирует"));
        method("yaw", "player.yaw", EditorI18n.t("Поворот взгляда, градусы"));
        method("pitch", "player.pitch", EditorI18n.t("Наклон взгляда, градусы"));
        method("lookX", "player.lookX", EditorI18n.t("Направление взгляда по X (−1…1)"));
        method("lookY", "player.lookY", EditorI18n.t("Направление взгляда по Y (−1…1)"));
        method("lookZ", "player.lookZ", EditorI18n.t("Направление взгляда по Z (−1…1)"));
        method("jumpStrength", "player.jumpStrength", EditorI18n.t("Сила прыжка (0.42 = ваниль)"));
        method("digSpeed", "player.digSpeed", EditorI18n.t("Множитель скорости копания (1 = ваниль)"));
        method("gamemode", "player.gamemode", EditorI18n.t("Режим игры: survival, creative, …"));
        method("blockReach", "player.blockReach", EditorI18n.t("Дальность ломания и постановки блоков"));
        method("language", "player.language", EditorI18n.t("Язык клиента: en_us, ru_ru, …"));
        method("lang", "player.lang", EditorI18n.t("Короткий код языка: en, ru, …"));
        method("isEnglish", "player.isEnglish", EditorI18n.t("true, если язык игрока английский"));
    }

    private static void registerFromRegistry() {
        for (String name : FunctionRegistry.allNames()) {
            if (BY_KEY.containsKey(name.toLowerCase(Locale.ROOT))) continue;
            add(registryEntry(name, FunctionRegistry.get(name)));
        }
    }

    /** Registry + user `fun` from buffer/project — refreshed on each completion. */
    private static List<Entry> dynamicEntries(String text) {
        List<Entry> out = new ArrayList<>();
        for (String name : FunctionRegistry.allNames()) {
            String key = name.toLowerCase(Locale.ROOT);
            if (BY_KEY.containsKey(key)) continue;
            out.add(registryEntry(name, FunctionRegistry.get(name)));
        }
        out.addAll(parseUserFunctions(text));
        out.addAll(projectUserFunctions());
        return out;
    }

    private static Entry registryEntry(String name, ScriptFunction fn) {
        String sig = name + "(…)";
        String doc = EditorI18n.t("Встроенная функция Spraute");
        if (fn != null) {
            int n = fn.getArgCount();
            Class<?>[] types = fn.getArgTypes();
            if (n < 0) {
                sig = name + "(…)";
                doc = EditorI18n.t("Переменное число аргументов");
            } else if (n == 0) {
                sig = name + "()";
            } else {
                StringBuilder sb = new StringBuilder(name).append('(');
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(argLabel(types, i));
                }
                sb.append(')');
                sig = sb.toString();
            }
        }
        return new Entry(name, name, sig, doc);
    }

    private static String argLabel(Class<?>[] types, int i) {
        if (types == null || i >= types.length || types[i] == null) return "arg" + (i + 1);
        Class<?> t = types[i];
        if (t == String.class) return "str";
        if (t == Double.class || t == Float.class || t == double.class || t == float.class) return "num";
        if (t == Integer.class || t == int.class || t == Long.class || t == long.class) return "int";
        if (t == Boolean.class || t == boolean.class) return "bool";
        String simple = t.getSimpleName();
        if (simple.isEmpty()) return "arg" + (i + 1);
        return Character.toLowerCase(simple.charAt(0)) + (simple.length() > 1 ? simple.substring(1) : "");
    }

    private static final java.util.regex.Pattern FUN_PATTERN = java.util.regex.Pattern.compile(
            "(?m)^\\s*fun\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)");

    private static List<Entry> parseUserFunctions(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<Entry> out = new ArrayList<>();
        java.util.regex.Matcher m = FUN_PATTERN.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            String params = m.group(2) == null ? "" : m.group(2).trim();
            String sig = name + "(" + params + ")";
            out.add(new Entry(name, name + "(|)", sig, EditorI18n.t("Пользовательская fun из скрипта")));
        }
        return out;
    }

    private static volatile long projectScanAtMs;
    private static List<Entry> projectFuns = List.of();

    private static List<Entry> projectUserFunctions() {
        long now = System.currentTimeMillis();
        if (now - projectScanAtMs < 2500L) return projectFuns;
        projectScanAtMs = now;
        try {
            Path root = SceneEditorSession.gameDir().resolve("spraute_engine").resolve("scripts");
            if (!Files.isDirectory(root)) {
                projectFuns = List.of();
                return projectFuns;
            }
            Map<String, Entry> found = new LinkedHashMap<>();
            try (var walk = Files.walk(root, 6)) {
                walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".spr"))
                        .limit(200)
                        .forEach(p -> {
                            try {
                                String src = Files.readString(p);
                                for (Entry e : parseUserFunctions(src)) {
                                    found.putIfAbsent(e.key().toLowerCase(Locale.ROOT),
                                            new Entry(e.key(), e.insert(), e.signature(),
                                                    EditorI18n.t("fun из ") + root.relativize(p).toString().replace('\\', '/')));
                                }
                            } catch (Exception ignored) {}
                        });
            }
            projectFuns = new ArrayList<>(found.values());
        } catch (Exception e) {
            projectFuns = List.of();
        }
        return projectFuns;
    }

    /** Полный список on-событий (ScriptExecutor / metods.md). */
    private static void registerAllOnEvents() {
        on("interact", "on interact(target) -> handler {\n    |\n}",
                "on interact(target) -> id { … }", EditorI18n.t("Клик ПКМ по NPC/сущности. _eventPlayer"));
        on("keybind", "on keybind(\"f\") -> handler {\n    |\n}",
                "on keybind(\"key\") -> id { … }", EditorI18n.t("Нажатие клавиши. _eventPlayer"));
        on("join", "on join() -> handler {\n    |\n}",
                "on join([player]) -> id { … }", EditorI18n.t("Игрок зашёл в мир. _eventPlayer"));
        on("assetsAllow", "on assetsAllow() -> handler {\n    |\n}",
                "on assetsAllow([player]) -> id { … }",
                EditorI18n.t("Игрок разрешил загрузку ресурсов (или файлы уже были). _eventPlayer"));
        on("assetsReady", "on assetsReady() -> handler {\n    |\n}",
                "on assetsReady([player]) -> id { … }",
                EditorI18n.t("Клиент докачал и применил ресурсы. _eventPlayer, _event1=файлов, _event2=разрешил"));
        on("assetsDeny", "on assetsDeny() -> handler {\n    |\n}",
                "on assetsDeny([player]) -> id { … }",
                EditorI18n.t("Игрок отклонил загрузку ресурсов. _eventPlayer"));
        on("firstJoin", "on firstJoin() -> handler {\n    |\n}",
                "on firstJoin([player]) -> id { … }", EditorI18n.t("Первый вход в мир. _eventPlayer"));
        on("leave", "on leave() -> handler {\n    |\n}",
                "on leave([player]) -> id { … }", EditorI18n.t("Игрок вышел. Алиасы: logout, playerLeave"));
        on("death", "on death(target) -> handler {\n    |\n}",
                "on death(filter) -> id { … }", EditorI18n.t("Смерть. _eventEntity, _eventKiller, координаты жертвы/убийцы"));
        on("kill", "on kill(killer, any) -> handler {\n    |\n}",
                "on kill(killer, [victim]) -> id { … }", EditorI18n.t("Убийство. _eventEntity, _eventKiller, координаты жертвы/убийцы"));
        on("pickup", "on pickup(npc, \"minecraft:iron_ingot\", 5) -> handler {\n    |\n}",
                "on pickup(npc, item_id, [nbt|max], [max]) -> id { … }",
                EditorI18n.t("Предмет у NPC. count — сколько подобрали сейчас, _eventItemCount — всего. Опц. лимит"));
        on("uiClick", "on uiClick() -> handler {\n    |\n}",
                "on uiClick([player]) -> id { … }", EditorI18n.t("Клик по кнопке UI. _eventPlayer, _eventWidget"));
        on("uiDragStart", "on uiDragStart() -> handler {\n    |\n}",
                "on uiDragStart([player]) -> id { … }",
                EditorI18n.t("Начало перетаскивания UI. _eventWidget — drag, _eventPayload"));
        on("uiDrop", "on uiDrop() -> handler {\n    |\n}",
                "on uiDrop([player], [widget]) -> id { … }",
                EditorI18n.t("Сброс на drop-зону. _eventWidget/_eventDrop, _eventDrag, _eventPayload, _eventX/_eventY"));
        on("uiDragEnd", "on uiDragEnd() -> handler {\n    |\n}",
                "on uiDragEnd([player]) -> id { … }",
                EditorI18n.t("Конец перетаскивания UI. _eventWidget — drag, _eventDrop (пусто если мимо)"));
        on("uiClose", "on uiClose() -> handler {\n    |\n}",
                "on uiClose([player]) -> id { … }", EditorI18n.t("Закрытие UI. _eventPlayer"));
        on("uiInput", "on uiInput() -> handler {\n    |\n}",
                "on uiInput([player], [widget]) -> id { … }", EditorI18n.t("Ввод в input. _eventInput, _eventWidget"));
        on("uiHover", "on uiHover() -> handler {\n    |\n}",
                "on uiHover([player], [widget]) -> id { … }", EditorI18n.t("Наведение на UI. _eventHover enter/leave"));
        on("uiTouch", "on uiTouch(player, \"a\", \"b\") -> handler {\n    |\n}",
                "on uiTouch(player, id1, id2) -> id { … }", EditorI18n.t("Пересечение двух UI-виджетов"));
        on("uiOverlap", "on uiOverlap(player, \"a\", \"b\") -> handler {\n    |\n}",
                "on uiOverlap(…) -> id { … }", EditorI18n.t("Алиас uiTouch"));
        on("hitboxTouch", "on hitboxTouch(any, \"*\", \"zone\") -> handler {\n    |\n}",
                "on hitboxTouch([player], [scene], [triggerId]) -> id { … }",
                EditorI18n.t("Вход в HITBOX-триггер. _eventPlayer, _eventTriggerId, _eventNode"));
        on("hitboxEnter", "on hitboxEnter(any, \"*\", \"zone\") -> handler {\n    |\n}",
                "on hitboxEnter(…) -> id { … }", EditorI18n.t("Алиас hitboxTouch"));
        on("hitboxExit", "on hitboxExit(any, \"*\", \"zone\") -> handler {\n    |\n}",
                "on hitboxExit([player], [scene], [triggerId]) -> id { … }",
                EditorI18n.t("Выход из HITBOX-триггера. _eventPlayer, _eventTriggerId"));
        on("hitboxLeave", "on hitboxLeave(any, \"*\", \"zone\") -> handler {\n    |\n}",
                "on hitboxLeave(…) -> id { … }", EditorI18n.t("Алиас hitboxExit"));
        on("position", "on position(player, 0, 64, 0, 2) -> handler {\n    |\n}",
                "on position(player, x, y, z, [r]) -> id { … }", EditorI18n.t("Игрок в радиусе точки"));
        on("inventory", "on inventory(player, \"minecraft:diamond\", 1) -> handler {\n    |\n}",
                "on inventory(player, item, [count]) -> id { … }", EditorI18n.t("Предмет в инвентаре"));
        on("hasItem", "on hasItem(player, \"minecraft:diamond\") -> handler {\n    |\n}",
                "on hasItem(…) -> id { … }", EditorI18n.t("Алиас inventory"));
        on("clickBlock", "on clickBlock() -> handler {\n    |\n}",
                "on clickBlock([target]) -> id { … }", EditorI18n.t("ЛКМ/ПКМ по блоку. cancelEvent()"));
        on("useBlock", "on useBlock() -> handler {\n    |\n}",
                "on useBlock([target]) -> id { … }", EditorI18n.t("ПКМ по блоку (дверь, сундук, рычаг…). cancelEvent()"));
        fn("useBlock", "useBlock(x, y, z) / useBlock(actor, x, y, z)",
                EditorI18n.t("Симулировать ПКМ по блоку"));
        on("breakBlock", "on breakBlock() -> handler {\n    |\n}",
                "on breakBlock([target]) -> id { … }", EditorI18n.t("Ломание блока. cancelEvent()"));
        on("placeBlock", "on placeBlock() -> handler {\n    |\n}",
                "on placeBlock([target]) -> id { … }", EditorI18n.t("Установка блока. cancelEvent()"));
        on("openChest", "on openChest() -> handler {\n    |\n}",
                "on openChest([target]) -> id { … }", EditorI18n.t("Алиас useBlock для сундука. cancelEvent()"));
        on("openDoor", "on openDoor() -> handler {\n    |\n}",
                "on openDoor([target]) -> id { … }", EditorI18n.t("Алиас useBlock для двери. cancelEvent()"));
        on("jump", "on jump() -> handler {\n    |\n}",
                "on jump([player]) -> id { … }", EditorI18n.t("Прыжок. cancelEvent()"));
        on("attack", "on attack() -> handler {\n    |\n}",
                "on attack([player]) -> id { … }", EditorI18n.t("Атака сущности. cancelEvent(), _eventTarget"));
        on("sprint", "on sprint() -> handler {\n    |\n}",
                "on sprint([player]) -> id { … }", EditorI18n.t("Старт спринта. cancelEvent()"));
        on("action", "on action(\"use\") -> handler {\n    |\n}",
                "on action([player], type, [target]) -> id { … }", "use/eat/fish/hoe/jump/attack/sprint/sleep/craft/drop");
        on("playerAction", "on playerAction(\"use\") -> handler {\n    |\n}",
                "on playerAction(…) -> id { … }", EditorI18n.t("Алиас action"));
        on("chat", "on chat() -> handler {\n    |\n}",
                "on chat([player], [msg]) -> id { … }", EditorI18n.t("Сообщение в чат. _eventMessage / _eventText"));
        on("orbPickup", "on orbPickup() -> handler {\n    |\n}",
                "on orbPickup([player], [texture]) -> id { … }", EditorI18n.t("Подбор орба spawnOrb"));
        on("projectileHit", "on projectileHit(any) -> handler {\n    |\n}",
                "on projectileHit(id, [block|entity]) -> id { … }", EditorI18n.t("Попадание снаряда"));
        on("dimension", "on dimension(player, \"minecraft:the_nether\") -> handler {\n    |\n}",
                "on dimension(player, dim) -> id { … }", EditorI18n.t("Смена измерения"));
        on("tradeBuy", "on tradeBuy(player) -> handler {\n    |\n}",
                "on tradeBuy(player, [item]) -> id { … }",
                EditorI18n.t("Покупка (economy: registerEvent + fireEvent)"));
        on("tradeSell", "on tradeSell(player) -> handler {\n    |\n}",
                "on tradeSell(player, [item]) -> id { … }",
                EditorI18n.t("Продажа (economy: registerEvent + fireEvent)"));
        on("relGift", "on relGift(player) -> handler {\n    |\n}",
                "on relGift(player, [npc], [item]) -> id { … }", EditorI18n.t("Подарок (Relations)"));
        on("relRepChange", "on relRepChange(player) -> handler {\n    |\n}",
                "on relRepChange(player, [npc]) -> id { … }", EditorI18n.t("Изменение репутации"));
        on("relTalk", "on relTalk(player) -> handler {\n    |\n}",
                "on relTalk(player, [npc]) -> id { … }", EditorI18n.t("Разговор с NPC (Relations)"));
        on("relButton", "on relButton(player) -> handler {\n    |\n}",
                "on relButton(player, [btn], [npc]) -> id { … }", EditorI18n.t("Кнопка Relations"));
    }

    /** Полный список await-триггеров (ScriptCompiler). */
    private static void registerAllAwaitTriggers() {
        awaitFn("time", "await time(|1)", "await time(seconds)", EditorI18n.t("Пауза в секундах"));
        awaitFn("interact", "await interact(|target)", "await interact(entity)", EditorI18n.t("Ждать ПКМ по цели"));
        awaitFn("next", "await next()", "await next", EditorI18n.t("Продолжение по клавише диалога"));
        awaitFn("keybind", "await keybind(\"|f\")", "await keybind(\"key\")", EditorI18n.t("Ждать нажатие клавиши"));
        awaitFn("death", "await death(|target)", "await death(filter)", EditorI18n.t("Ждать смерть"));
        awaitFn("kill", "await kill(|killer, any)", "await kill(killer, [victim])", EditorI18n.t("Ждать убийство"));
        awaitFn("pickup", "await pickup(|npc, 1, \"minecraft:iron_ingot\")",
                "await pickup(npc, amount, item, [nbt])",
                EditorI18n.t("Ждать N новых предметов у NPC и не давать подобрать больше"));
        awaitFn("orbPickup", "await orbPickup(|player, 1)",
                "await orbPickup(player, amount, [texture])", EditorI18n.t("Ждать подбор орбов"));
        awaitFn("projectileHit", "await projectileHit(|\"id\")",
                "await projectileHit(id, [type])", EditorI18n.t("Ждать попадание снаряда"));
        awaitFn("tradeBuy", "await tradeBuy(|player)", "await tradeBuy(player, [item])",
                EditorI18n.t("Ждать покупку (событие economy)"));
        awaitFn("tradeSell", "await tradeSell(|player)", "await tradeSell(player, [item])",
                EditorI18n.t("Ждать продажу (событие economy)"));
        awaitFn("relGift", "await relGift(|player)", "await relGift(player, …)", EditorI18n.t("Ждать подарок"));
        awaitFn("relRepChange", "await relRepChange(|player)", "await relRepChange(player, [npc])", EditorI18n.t("Ждать репутацию"));
        awaitFn("relTalk", "await relTalk(|player)", "await relTalk(player, [npc])", EditorI18n.t("Ждать разговор"));
        awaitFn("relButton", "await relButton(|player)", "await relButton(player, …)", EditorI18n.t("Ждать кнопку Relations"));
        awaitFn("task", "await task(\"|id\")", "await task(taskId)", EditorI18n.t("Ждать завершения async"));
        awaitFn("uiClick", "await uiClick(|player)", "await uiClick(player)", EditorI18n.t("Ждать клик UI / закрытие"));
        awaitFn("uiDrop", "await uiDrop(|player)", "await uiDrop(player)",
                EditorI18n.t("Ждать сброс на drop-зону. _eventDrop, _eventDrag, _eventPayload"));
        awaitFn("uiClose", "await uiClose(|player)", "await uiClose(player)", EditorI18n.t("Ждать закрытие UI"));
        awaitFn("uiInput", "await uiInput(|player)", "await uiInput(player, [widget])", EditorI18n.t("Ждать ввод текста"));
        awaitFn("uiTouch", "await uiTouch(|player, \"a\", \"b\")",
                "await uiTouch(player, id1, id2)", EditorI18n.t("Ждать пересечение UI"));
        awaitFn("uiOverlap", "await uiOverlap(|player, \"a\", \"b\")",
                "await uiOverlap(…)", EditorI18n.t("Алиас uiTouch"));
        awaitFn("hitboxTouch", "await hitboxTouch(|player, \"zone\")",
                "await hitboxTouch(player, [scene|triggerId], [triggerId])",
                EditorI18n.t("Ждать вход в HITBOX-триггер. _eventTriggerId"));
        awaitFn("hitboxEnter", "await hitboxEnter(|player, \"zone\")",
                "await hitboxEnter(…)", EditorI18n.t("Алиас hitboxTouch"));
        awaitFn("hitboxExit", "await hitboxExit(|player, \"zone\")",
                "await hitboxExit(player, [scene|triggerId], [triggerId])",
                EditorI18n.t("Ждать выход из HITBOX-триггера"));
        awaitFn("hitboxLeave", "await hitboxLeave(|player, \"zone\")",
                "await hitboxLeave(…)", EditorI18n.t("Алиас hitboxExit"));
        awaitFn("position", "await position(|player, 0, 64, 0)",
                "await position(player, x, y, z, [r])", EditorI18n.t("Ждать позицию игрока"));
        awaitFn("inventory", "await inventory(|player, \"minecraft:diamond\")",
                "await inventory(player, item, [count])", EditorI18n.t("Ждать предмет в инвентаре"));
        awaitFn("hasItem", "await hasItem(|player, \"minecraft:diamond\")",
                "await hasItem(…)", EditorI18n.t("Алиас inventory"));
        awaitFn("clickBlock", "await clickBlock(|player)", "await clickBlock(player, [target])", EditorI18n.t("Ждать клик по блоку"));
        awaitFn("useBlock", "await useBlock(|player)", "await useBlock(player, [target])", EditorI18n.t("Ждать использование блока"));
        awaitFn("breakBlock", "await breakBlock(|player)", "await breakBlock(player, [target])", EditorI18n.t("Ждать ломание блока"));
        awaitFn("placeBlock", "await placeBlock(|player)", "await placeBlock(player, [target])", EditorI18n.t("Ждать установку блока"));
        awaitFn("openChest", "await openChest(|player)", "await openChest(player, [target])", EditorI18n.t("Алиас: ждать сундук"));
        awaitFn("openDoor", "await openDoor(|player)", "await openDoor(player, [target])", EditorI18n.t("Алиас: ждать дверь"));
        awaitFn("chat", "val msg = await chat(|player, \"hello\")",
                "await chat(player, msg|list(), [ignoreCase], [ignorePunct]) → текст сообщения",
                EditorI18n.t("Ждать сообщение в чат; результат — текст"));
        awaitFn("jump", "await jump(|player)", "await jump(player)", EditorI18n.t("Ждать прыжок"));
        awaitFn("dimension", "await dimension(|player, \"minecraft:the_nether\")",
                "await dimension(player, dim)", EditorI18n.t("Ждать смену измерения"));
        awaitFn("join", "await join()", "await join([player])", EditorI18n.t("Ждать вход игрока"));
        awaitFn("assetsReady", "await assetsReady(|player)",
                "await assetsReady([player])", EditorI18n.t("Ждать пока клиент загрузит ресурсы сервера"));
        awaitFn("assetsAllow", "await assetsAllow(|player)",
                "await assetsAllow([player])", EditorI18n.t("Ждать пока игрок разрешит загрузку ресурсов"));
        fn("assetsAllowed", "assetsAllowed(player)", EditorI18n.t("Игрок разрешил загрузку ресурсов?"));
        fn("assetsReady", "assetsReady(player)", EditorI18n.t("Клиент уже загрузил ресурсы?"));
        fn("assetsDenied", "assetsDenied(player)", EditorI18n.t("Игрок отклонил загрузку ресурсов?"));
        awaitFn("firstJoin", "await firstJoin()", "await firstJoin([player])", EditorI18n.t("Ждать первый вход"));
        awaitFn("leave", "await leave()", "await leave([player])", EditorI18n.t("Ждать выход. Алиасы: logout, playerLeave"));
        awaitFn("cameraRoute", "await cameraRoute(|player, \"route\")",
                "await cameraRoute(player, route, …)", EditorI18n.t("Ждать пролёт камеры"));
        fn("lockPlayerMovement", "lockPlayerMovement(player, locked)",
                EditorI18n.t("Заблокировать/разблокировать ходьбу (WASD), независимо от кинематографической камеры"));
        fn("lockPlayerMoveDir", "lockPlayerMoveDir(player, dir, locked)",
                EditorI18n.t("Запретить/разрешить одну сторону ходьбы: forward/back/left/right или north/south/east/west"));
        fn("lockPlayerMoveDirs", "lockPlayerMoveDirs(player, dirs)",
                EditorI18n.t("Задать набор запрещённых сторон ходьбы (строка, список или 4/8 флагов). Пусто — снять все"));
        fn("isPlayerMoveDirLocked", "isPlayerMoveDirLocked(player, dir)",
                EditorI18n.t("Запрещена ли указанная сторона ходьбы"));
        fn("lockPlayerLook", "lockPlayerLook(player, locked)",
                EditorI18n.t("Заблокировать/разблокировать поворот мышью; при lock фиксирует текущий взгляд"));
        fn("lockPlayerPerspective", "lockPlayerPerspective(player, locked)",
                EditorI18n.t("Заблокировать/разблокировать смену вида камеры (F5)"));
        fn("lockPlayerF5", "lockPlayerF5(player, locked)",
                EditorI18n.t("Алиас lockPlayerPerspective — запретить переключение 1-е / 3-е лицо"));
        fn("setPlayerLook", "setPlayerLook(player, yaw, pitch)",
                EditorI18n.t("Установить куда смотрит игрок (градусы). Если look locked — обновляет фиксацию"));
        fn("setPlayerLookAt", "setPlayerLookAt(player, x, y, z | entity)",
                EditorI18n.t("Повернуть игрока взглядом на точку или сущность"));
        fn("setPlayerLookDir", "setPlayerLookDir(player, dx, dy, dz)",
                EditorI18n.t("Направить взгляд игрока по вектору (lookX/lookY/lookZ)"));
        fn("setPlayerPos", "setPlayerPos(player, x, y, z)",
                EditorI18n.t("Телепорт игрока в текущем мире. То же, что player.x/y/z ="));
        fn("isPlayerMovementLocked", "isPlayerMovementLocked(player)", EditorI18n.t("Заблокирована ли ходьба"));
        fn("isPlayerLookLocked", "isPlayerLookLocked(player)", EditorI18n.t("Заблокирован ли поворот взгляда"));
        fn("isPlayerPerspectiveLocked", "isPlayerPerspectiveLocked(player)",
                EditorI18n.t("Заблокирована ли смена вида (F5)"));
        awaitFn("action", "await action(|player, \"use\")",
                "await action(player, type, [target])", EditorI18n.t("Ждать действие игрока"));
        awaitFn("playerAction", "await playerAction(|player, \"use\")",
                "await playerAction(…)", EditorI18n.t("Алиас action"));
    }

    private static void on(String name, String insert, String signature, String doc) {
        structure("on " + name, insert, signature, doc);
    }

    private static void awaitFn(String name, String insert, String signature, String doc) {
        structure("await " + name, insert, signature, doc);
    }

    private static void add(Entry e) {
        BY_KEY.putIfAbsent(e.key().toLowerCase(Locale.ROOT), e);
        ALL.add(e);
    }

    private static void structure(String key, String insert, String signature, String doc) {
        add(new Entry(key, insert, signature, doc));
    }

    private static void fn(String key, String signature, String doc) {
        add(new Entry(key, key, signature, doc));
    }

    private static void method(String key, String signature, String doc) {
        add(new Entry(key, key, signature, doc));
    }

    private static void block(String key, String signature, String doc, String props) {
        add(new Entry(key, key, signature, doc + "\n" + props));
    }

    private static void ctxProp(String createType, String prop, String doc) {
        String key = createType + "." + prop;
        add(new Entry(key, prop + " = |", createType + "." + prop, doc));
    }
}
