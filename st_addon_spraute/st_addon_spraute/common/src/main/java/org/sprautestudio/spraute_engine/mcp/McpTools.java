package org.sprautestudio.spraute_engine.mcp;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.sprautestudio.spraute_engine.editor.ClientState;
import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.project.Workspace;
import org.sprautestudio.spraute_engine.lang.*;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.platform.Platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What an assistant can do through MCP: projects and their files, checking and running stories, the
 * log, game commands, the player and the world around, the language reference, engine settings and a
 * screenshot of the game.
 */
public final class McpTools {
    private McpTools() {
    }

    public static final String INSTRUCTIONS = """
            Spraute is a story engine (a Minecraft 1.20.1 Forge mod). You work with the user's projects:
            stories in the Spraute language (.sst), visual blocks (.svs), and JSON files for interfaces (.sgui), cutscenes (.scut),
            effects (.sfx), shops (.sshop), quests (.squest), zones (.szone) and fighting styles (.sfight).
            To make quests, zones, shops, fighting styles, effects, cutscenes, interfaces or entity types: `formats` (fields + example), then `create`
            (with a JSON `patch`), and `patch_json` to change them later; `list_things`, `assets`, `registry`, `here` help to find names and places.
            Workflow: `reference` to learn commands/functions/events → write or edit with `write_file` → `check_script` until there are
            no errors → `run_script` → read `logs` and `screenshot` / `player` / `nearby` to see what happened → fix and repeat.
            Story files start with `script <id> "Title" [autostart]`. A `say` without `nowait` waits for the player's F8, so put
            `every`/`on` handlers above lines like that. Texts for the player are in Russian. After changing zones, shops, quests or
            other JSON files call `sync_project` so the world uses them at once.
            To test while the player is away: `game` action=background (the game keeps running unfocused) or action=unpause, then
            `press_key` to play the story like a player (H / F8 for speech, 1–9 for choices) and `screenshot` / `logs` to watch it.""";

    record Tool(String name, String description, JsonObject schema, Function<JsonObject, Object> run) {
    }

    private static final Map<String, Tool> TOOLS = new LinkedHashMap<>();

    // ================================================================== schema helpers

    static JsonObject schema(String... props) {
        // props: name, type, description, required(+)/optional(?) — groups of four
        JsonObject s = new JsonObject();
        s.addProperty("type", "object");
        JsonObject p = new JsonObject();
        JsonArray req = new JsonArray();
        for (int i = 0; i + 3 < props.length; i += 4) {
            JsonObject one = new JsonObject();
            one.addProperty("type", props[i + 1]);
            one.addProperty("description", props[i + 2]);
            p.add(props[i], one);
            if (props[i + 3].equals("+")) req.add(props[i]);
        }
        s.add("properties", p);
        s.add("required", req);
        return s;
    }

    static void tool(String name, String description, JsonObject schema, Function<JsonObject, Object> run) {
        TOOLS.put(name, new Tool(name, description, schema, run));
    }

    static String str(JsonObject a, String k, String def) {
        return a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsString() : def;
    }

    static double num(JsonObject a, String k, double def) {
        return a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsDouble() : def;
    }

    static boolean bool(JsonObject a, String k, boolean def) {
        return a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsBoolean() : def;
    }

    // ================================================================== threads

    static <T> T onClient(Supplier<T> s) {
        try {
            return Minecraft.getInstance().submit(s).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    static <T> T onServer(Supplier<T> s) {
        MinecraftServer server = Platform.get().server();
        if (server == null) throw new IllegalStateException("Мир не открыт (нужна одиночная игра или свой сервер)");
        try {
            return server.submit(s).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    static ServerPlayer player() {
        MinecraftServer server = Platform.get().server();
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) throw new IllegalStateException("Игрока нет в мире");
        return server.getPlayerList().getPlayers().get(0);
    }

    // ================================================================== projects

    static Path projectDir(String name) {
        for (Workspace.ProjectInfo p : Workspace.projects()) if (p.name().equalsIgnoreCase(name) || p.dir().getFileName().toString().equalsIgnoreCase(name)) return p.dir();
        throw new IllegalArgumentException("Нет проекта «" + name + "». Есть: " + String.join(", ", Workspace.projects().stream().map(Workspace.ProjectInfo::name).toList()));
    }

    static Path inProject(String project, String rel) {
        Path dir = projectDir(project).toAbsolutePath().normalize();
        Path p = dir.resolve(rel.replace('\\', '/')).toAbsolutePath().normalize();
        if (!p.startsWith(dir)) throw new IllegalArgumentException("Путь вне проекта: " + rel);
        return p;
    }

    static String rel(Path dir, Path p) {
        return dir.relativize(p).toString().replace('\\', '/');
    }

    // ================================================================== results

    static JsonObject text(String s) {
        JsonObject r = new JsonObject();
        JsonArray c = new JsonArray();
        JsonObject t = new JsonObject();
        t.addProperty("type", "text");
        t.addProperty("text", s);
        c.add(t);
        r.add("content", c);
        return r;
    }

    public static JsonArray list() {
        JsonArray out = new JsonArray();
        for (Tool t : TOOLS.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", t.name());
            o.addProperty("description", t.description());
            o.add("inputSchema", t.schema());
            out.add(o);
        }
        return out;
    }

    public static JsonObject call(String name, JsonObject args) {
        Tool t = TOOLS.get(name);
        if (t == null) {
            JsonObject r = text("Нет инструмента " + name);
            r.addProperty("isError", true);
            return r;
        }
        try {
            Object res = t.run().apply(args);
            if (res instanceof JsonObject jo) return jo;
            return text(res instanceof String s ? s : new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(res));
        } catch (Exception e) {
            JsonObject r = text("Ошибка: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            r.addProperty("isError", true);
            return r;
        }
    }

    // ================================================================== the tools

    static {
        tool("status", "Состояние: открыт ли мир, игрок, проекты, запущенные сюжеты, последние ошибки. Начинайте с этого.",
                schema(), a -> {
                    Map<String, Object> o = new LinkedHashMap<>();
                    Minecraft mc = Minecraft.getInstance();
                    o.put("world_open", mc.level != null);
                    o.put("singleplayer_server", Platform.get().server() != null);
                    if (mc.player != null) {
                        o.put("player", mc.player.getGameProfile().getName());
                        o.put("pos", List.of(Math.round(mc.player.getX() * 10) / 10.0, Math.round(mc.player.getY() * 10) / 10.0, Math.round(mc.player.getZ() * 10) / 10.0));
                        o.put("dimension", mc.level.dimension().location().toString());
                    }
                    o.put("ide_project", org.sprautestudio.spraute_engine.editor.SprauteScreen.currentProjectName());
                    List<String> ps = new ArrayList<>();
                    for (Workspace.ProjectInfo p : Workspace.projects()) ps.add(p.name() + " (" + p.files() + " файлов)");
                    o.put("projects", ps);
                    o.put("scripts", scripts());
                    List<String> errs = new ArrayList<>();
                    var log = ClientState.get().log();
                    for (int i = Math.max(0, log.size() - 8); i < log.size(); i++) if (log.get(i).level() == Packets.Log.ERROR)
                        errs.add(log.get(i).source() + ": " + log.get(i).text());
                    o.put("recent_errors", errs);
                    return o;
                });

        tool("list_projects", "Проекты Spraute (папки в workspace).", schema(), a -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Workspace.ProjectInfo p : Workspace.projects()) out.add(Map.of("name", p.name(), "dir", p.dir().toString(), "files", p.files()));
            return out;
        });

        tool("create_project", "Создаёт новый проект.", schema("name", "string", "Имя проекта", "+"), a -> {
            try {
                Project p = Workspace.createProject(str(a, "name", "Новый проект"));
                return "Создан проект " + p.name() + " в " + p.dir;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        });

        tool("list_files", "Все файлы проекта (с подпапками): путь, тип, размер. Типы: script .sst, blocks .svs, gui, cutscene, effect, shop, quest, zone, fight; "
                + "плюс картинки, сущности (entities/*.sent), модели, настройки движка (engine.json).",
                schema("project", "string", "Имя проекта", "+"), a -> {
                    Path dir = projectDir(str(a, "project", ""));
                    List<String> out = new ArrayList<>();
                    try (var s = Files.walk(dir)) {
                        s.filter(Files::isRegularFile).sorted().forEach(p -> {
                            try {
                                out.add(rel(dir, p) + "  (" + Files.size(p) + " б)");
                            } catch (Exception ignored) {
                            }
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                    return String.join("\n", out);
                });

        tool("read_file", "Читает файл проекта (путь относительно папки проекта, например scripts/main.sst или zones/арена.szone).",
                schema("project", "string", "Имя проекта", "+", "path", "string", "Путь внутри проекта", "+"), a -> {
                    try {
                        return Files.readString(inProject(str(a, "project", ""), str(a, "path", "")), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });

        tool("write_file", "Пишет файл проекта целиком (создаёт папки). Открытый в редакторе файл без несохранённых правок обновится. "
                + "Сюжеты кладите в scripts/, блочные — в blocks/, зоны в zones/ и т.д.",
                schema("project", "string", "Имя проекта", "+", "path", "string", "Путь внутри проекта", "+", "content", "string", "Полный текст файла", "+"), a -> {
                    try {
                        Path p = inProject(str(a, "project", ""), str(a, "path", ""));
                        Files.createDirectories(p.getParent());
                        Files.writeString(p, str(a, "content", ""), StandardCharsets.UTF_8);
                        onClient(() -> {
                            org.sprautestudio.spraute_engine.editor.SprauteScreen.fileChangedOutside(p);
                            return null;
                        });
                        String msg = "Записано: " + str(a, "path", "");
                        if (p.toString().endsWith(".sst")) msg += "\n" + diagnostics(str(a, "content", ""));
                        return msg;
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });

        tool("edit_file", "Заменяет в файле проекта точный кусок текста (old) на новый (new). old должен встречаться ровно один раз.",
                schema("project", "string", "Имя проекта", "+", "path", "string", "Путь внутри проекта", "+", "old", "string", "Что заменить (точно)", "+",
                        "new", "string", "На что", "+"), a -> {
                    try {
                        Path p = inProject(str(a, "project", ""), str(a, "path", ""));
                        String s = Files.readString(p, StandardCharsets.UTF_8), old = str(a, "old", "");
                        int i = s.indexOf(old);
                        if (old.isEmpty() || i < 0) throw new IllegalArgumentException("Кусок не найден");
                        if (s.indexOf(old, i + 1) >= 0) throw new IllegalArgumentException("Кусок встречается несколько раз — дайте больше контекста");
                        s = s.substring(0, i) + str(a, "new", "") + s.substring(i + old.length());
                        Files.writeString(p, s, StandardCharsets.UTF_8);
                        onClient(() -> {
                            org.sprautestudio.spraute_engine.editor.SprauteScreen.fileChangedOutside(p);
                            return null;
                        });
                        return "Изменено." + (p.toString().endsWith(".sst") ? "\n" + diagnostics(s) : "");
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });

        tool("delete_file", "Удаляет файл проекта.", schema("project", "string", "Имя проекта", "+", "path", "string", "Путь внутри проекта", "+"), a -> {
            try {
                Path p = inProject(str(a, "project", ""), str(a, "path", ""));
                Files.deleteIfExists(p);
                onClient(() -> {
                    org.sprautestudio.spraute_engine.editor.SprauteScreen.fileChangedOutside(p);
                    return null;
                });
                return "Удалено: " + str(a, "path", "");
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        });

        tool("check_script", "Проверяет текст сюжета на языке Spraute без запуска: ошибки и предупреждения с номерами строк.",
                schema("source", "string", "Текст сюжета (.sst)", "+"), a -> diagnostics(str(a, "source", "")));

        tool("run_script", "Загружает сюжет проекта в мир (и запускает, если start). Вместе с ним уходят интерфейсы, зоны, магазины… проекта. "
                + "Ответ — номер сюжета и сообщения загрузки.",
                schema("project", "string", "Имя проекта", "+", "path", "string", "Путь .sst внутри проекта", "+", "start", "boolean", "Запустить сразу (да)", "?"),
                a -> {
                    try {
                        Path dir = projectDir(str(a, "project", ""));
                        Path p = inProject(str(a, "project", ""), str(a, "path", ""));
                        String src = Files.readString(p, StandardCharsets.UTF_8);
                        Project pr = Project.openReadOnly(dir);
                        CompletableFuture<Packets.DeployResult> done = new CompletableFuture<>();
                        onClient(() -> {
                            var prev = ClientState.get().deployListener;
                            ClientState.get().deployListener = r -> {
                                done.complete(r);
                                ClientState.get().deployListener = prev;
                                if (prev != null) prev.accept(r);
                            };
                            ClientState.get().deploy(pr.name(), src, bool(a, "start", true), pr.deploySources());
                            return null;
                        });
                        Packets.DeployResult r = done.get(10, TimeUnit.SECONDS);
                        return (r.ok() ? "Загружен сюжет #" + r.id() + (bool(a, "start", true) ? " и запущен" : "") : "Не загрузился") + "\n" + String.join("\n", r.messages());
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });

        tool("run_code", "Сразу выполняет кусок кода Spraute в мире (без файла): заворачивает его в временный сюжет #9999 и запускает. "
                + "Удобно, чтобы проверить команду, заспавнить кого-то, показать эффект. Можно объявлять character/location/var в начале.",
                schema("code", "string", "Строки сюжета без заголовка script", "+", "project", "string", "Проект, чьи модели, зоны и интерфейсы нужны (необязательно)", "?"),
                a -> {
                    try {
                        String src = "script 9999 \"ИИ: проба\"\n" + str(a, "code", "");
                        String diag = diagnostics(src);
                        if (!diag.startsWith("Ошибок нет") && diag.contains("ERROR")) return "Не запущено, ошибки (строки считаются с заголовком, +1):\n" + diag;
                        String proj = str(a, "project", "");
                        java.util.Map<String, String> guis = new java.util.HashMap<>();
                        String pname = proj;
                        if (!proj.isEmpty()) {
                            Project pr = Project.openReadOnly(projectDir(proj));
                            guis = pr.deploySources();
                            pname = pr.name();
                        }
                        String fp = pname;
                        java.util.Map<String, String> fg = guis;
                        CompletableFuture<Packets.DeployResult> done = new CompletableFuture<>();
                        onClient(() -> {
                            var prev = ClientState.get().deployListener;
                            ClientState.get().deployListener = r -> {
                                done.complete(r);
                                ClientState.get().deployListener = prev;
                                if (prev != null) prev.accept(r);
                            };
                            ClientState.get().deploy(fp, src, true, fg);
                            return null;
                        });
                        Packets.DeployResult r = done.get(10, TimeUnit.SECONDS);
                        Thread.sleep(600);
                        StringBuilder sb = new StringBuilder(r.ok() ? "Запущено (#9999)." : "Не запустилось.");
                        for (String m : r.messages()) sb.append('\n').append(m);
                        var log = ClientState.get().log();
                        for (int i = Math.max(0, log.size() - 6); i < log.size(); i++) sb.append("\n[log] ").append(log.get(i).source()).append(": ").append(log.get(i).text());
                        return sb.toString();
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });

        tool("open_in_editor", "Открывает проект и файл в редакторе Spraute на экране игрока (чтобы показать ему результат).",
                schema("project", "string", "Имя проекта", "+", "path", "string", "Путь файла внутри проекта (необязательно)", "?"), a -> {
                    Path dir = projectDir(str(a, "project", ""));
                    Path f = a.has("path") ? inProject(str(a, "project", ""), str(a, "path", "")) : null;
                    return onClient(() -> org.sprautestudio.spraute_engine.editor.SprauteScreen.openForAssistant(dir, f));
                });

        tool("close_screen", "Закрывает открытый экран (редактор, меню), чтобы снимок показал игру.", schema(), a -> onClient(() -> {
            Minecraft.getInstance().setScreen(null);
            return "Закрыто";
        }));

        tool("game", "Пауза и фон: action = unpause — закрыть меню паузы (игра снова идёт); background — игра не встаёт на паузу, "
                        + "когда окно не в фокусе (можно тестировать, пока игрок занят другим); foreground — вернуть обычное поведение; "
                        + "state — на паузе ли игра и в фокусе ли окно.",
                schema("action", "string", "unpause | background | foreground | state", "+"), a -> onClient(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    String act = str(a, "action", "state");
                    switch (act) {
                        case "unpause" -> {
                            if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen || mc.screen == null) mc.setScreen(null);
                            else return "Открыт экран " + mc.screen.getClass().getSimpleName() + " — не меню паузы; закройте его close_screen";
                        }
                        case "background", "foreground" -> {
                            mc.options.pauseOnLostFocus = act.equals("foreground");
                            mc.options.save();
                            if (act.equals("background") && mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
                        }
                        case "state" -> {
                        }
                        default -> throw new IllegalArgumentException("action: unpause, background, foreground или state");
                    }
                    return "Пауза: " + (mc.isPaused() ? "да" : "нет") + ", окно в фокусе: " + (mc.isWindowActive() ? "да" : "нет")
                            + ", пауза при потере фокуса: " + (mc.options.pauseOnLostFocus ? "да" : "нет")
                            + ", экран: " + (mc.screen == null ? "игра" : mc.screen.getClass().getSimpleName());
                }));

        tool("press_key", "Нажимает клавишу в игре, как игрок: H, F8, SPACE, 1–9 (варианты choice), ESCAPE… Работает и в фоне. "
                        + "Так можно пройти свой сюжет: листать реплики (wait key H), выбирать ответы. times — сколько раз подряд.",
                schema("key", "string", "Клавиша", "+", "times", "integer", "Сколько раз (1)", "?"), a -> {
                    String key = str(a, "key", "");
                    int code = Specs.glfwKey(key);
                    if (code <= 0) throw new IllegalArgumentException("Неизвестная клавиша: " + key);
                    int times = (int) Math.max(1, Math.min(50, num(a, "times", 1)));
                    for (int i = 0; i < times; i++) {
                        onClient(() -> {
                            Minecraft mc = Minecraft.getInstance();
                            long w = mc.getWindow().getWindow();
                            mc.keyboardHandler.keyPress(w, code, 0, org.lwjgl.glfw.GLFW.GLFW_PRESS, 0);
                            mc.keyboardHandler.keyPress(w, code, 0, org.lwjgl.glfw.GLFW.GLFW_RELEASE, 0);
                            return null;
                        });
                        if (times > 1) {
                            try {
                                Thread.sleep(150);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                    return "Нажато: " + key + (times > 1 ? " ×" + times : "");
                });

        tool("stop_script", "Останавливает сюжет по номеру.", schema("id", "integer", "Номер сюжета (из status / scripts)", "+"),
                a -> command("script stop " + (int) num(a, "id", 0)));

        tool("scripts", "Сюжеты в мире: номер, название, проект, активен ли, на паузе, где сейчас.", schema(), a -> scripts());

        tool("sync_project", "Отправляет в мир файлы проекта (зоны, магазины, квесты, интерфейсы, катсцены, эффекты, стили боя) — после их правки.",
                schema("project", "string", "Имя проекта", "+"), a -> {
                    try {
                        Project pr = Project.openReadOnly(projectDir(str(a, "project", "")));
                        var src = pr.deploySources();
                        onClient(() -> {
                            ClientState.get().sources(pr.name(), src);
                            return null;
                        });
                        return "Отправлено в мир: " + src.size() + " файлов";
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });

        tool("logs", "Последние сообщения Spraute (ошибки сюжетов, log, вывод команд).",
                schema("count", "integer", "Сколько строк (40)", "?", "errors_only", "boolean", "Только ошибки", "?", "clear", "boolean", "Очистить после чтения", "?"), a -> {
                    int n = (int) num(a, "count", 40);
                    boolean errs = bool(a, "errors_only", false);
                    List<String> out = new ArrayList<>();
                    var log = ClientState.get().log();
                    for (var l : log) {
                        if (errs && l.level() != Packets.Log.ERROR) continue;
                        String lv = switch (l.level()) {
                            case Packets.Log.ERROR -> "ERROR";
                            case Packets.Log.WARNING -> "WARN";
                            case Packets.Log.SUCCESS -> "OK";
                            default -> "INFO";
                        };
                        out.add("[" + lv + "] " + l.source() + ": " + l.text());
                    }
                    if (bool(a, "clear", false)) onClient(() -> {
                        ClientState.get().clearLog();
                        return null;
                    });
                    return out.isEmpty() ? "(пусто)" : String.join("\n", out.subList(Math.max(0, out.size() - n), out.size()));
                });

        tool("run_command", "Выполняет команду Minecraft от имени игрока с правами оператора (tp, give, time, summon, effect, script start…). "
                + "Возвращает вывод команды.", schema("command", "string", "Команда, со / или без", "+"), a -> command(str(a, "command", "")));

        tool("reference", "Справочник языка Spraute: команды, функции, события. С query — только подходящие (по имени и описанию).",
                schema("query", "string", "Что искать (например: cast, зона, квест, camera)", "?", "kind", "string", "commands | functions | events | all", "?"), a -> {
                    String q = str(a, "query", "").toLowerCase(Locale.ROOT);
                    String kind = str(a, "kind", "all");
                    StringBuilder sb = new StringBuilder();
                    if (kind.equals("all") || kind.equals("commands")) {
                        for (CommandSpec c : Specs.commands()) {
                            String s = c.signature() + " — " + c.doc;
                            if (q.isEmpty() || s.toLowerCase(Locale.ROOT).contains(q)) sb.append("CMD ").append(s).append('\n');
                        }
                    }
                    if (kind.equals("all") || kind.equals("functions")) {
                        for (Specs.FunctionSpec f : Specs.functions()) {
                            String s = f.signature() + " → " + f.returns() + " — " + f.doc();
                            if (q.isEmpty() || s.toLowerCase(Locale.ROOT).contains(q)) sb.append("FN  ").append(s).append('\n');
                        }
                    }
                    if (kind.equals("all") || kind.equals("events")) {
                        for (EventDefs.Def d : EventDefs.ALL.values()) {
                            String s = "on " + d.name() + (d.filter() != null ? " [" + d.filterLabel() + "]" : "") + " — " + d.doc() + " Поля: " + String.join(", ", d.fields());
                            if (q.isEmpty() || s.toLowerCase(Locale.ROOT).contains(q)) sb.append("EV  ").append(s).append('\n');
                        }
                    }
                    String out = sb.toString();
                    if (out.length() > 60000) out = out.substring(0, 60000) + "\n… (сузьте query)";
                    return out.isEmpty() ? "Ничего не найдено" : out;
                });

        tool("player", "Игрок: позиция, взгляд, здоровье, режим, предмет в руке, на какой блок или существо смотрит.", schema(), a -> onServer(() -> {
            ServerPlayer p = player();
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", p.getGameProfile().getName());
            o.put("pos", List.of(round(p.getX()), round(p.getY()), round(p.getZ())));
            o.put("yaw_pitch", List.of(round(p.getYRot()), round(p.getXRot())));
            o.put("look", List.of(round(p.getViewVector(1).x), round(p.getViewVector(1).y), round(p.getViewVector(1).z)));
            o.put("dimension", p.level().dimension().location().toString());
            o.put("health", round(p.getHealth()) + "/" + round(p.getMaxHealth()));
            o.put("food", p.getFoodData().getFoodLevel());
            o.put("gamemode", p.gameMode.getGameModeForPlayer().getName());
            o.put("hand", String.valueOf(BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem())));
            var h = org.sprautestudio.spraute_engine.runtime.Raycast.look(new org.sprautestudio.spraute_engine.runtime.EntityRef(p), 32);
            o.put("looking_at", h.kind().equals("block") ? "block " + BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(h.block()).getBlock())
                    + " at " + h.block().toShortString() : h.kind().equals("entity") ? "entity " + describe(h.entity()) : "nothing");
            o.put("time_of_day", p.level().getDayTime() % 24000);
            return o;
        }));

        tool("nearby", "Существа рядом с игроком (или точкой): тип, имя, позиция, здоровье, расстояние.",
                schema("radius", "number", "Радиус (16)", "?", "type", "string", "Только этот тип, например minecraft:zombie", "?"), a -> onServer(() -> {
                    ServerPlayer p = player();
                    double r = num(a, "radius", 16);
                    String type = str(a, "type", "");
                    List<String> out = new ArrayList<>();
                    for (Entity e : p.level().getEntities(p, new AABB(p.blockPosition()).inflate(r))) {
                        String t = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
                        if (!type.isEmpty() && !t.equals(type) && !t.endsWith(":" + type)) continue;
                        out.add(describe(e) + "  dist " + round(e.distanceTo(p)));
                    }
                    return out.isEmpty() ? "Никого" : String.join("\n", out);
                }));

        tool("blocks", "Блоки в области вокруг точки (по умолчанию игрока): все не-воздушные блоки куба radius (до 8) или один блок at.",
                schema("x", "number", "X (иначе игрок)", "?", "y", "number", "Y", "?", "z", "number", "Z", "?", "radius", "integer", "Радиус куба 0..8 (0 — один блок)", "?"),
                a -> onServer(() -> {
                    ServerPlayer p = player();
                    int cx = a.has("x") ? (int) Math.floor(num(a, "x", 0)) : p.blockPosition().getX();
                    int cy = a.has("y") ? (int) Math.floor(num(a, "y", 0)) : p.blockPosition().getY();
                    int cz = a.has("z") ? (int) Math.floor(num(a, "z", 0)) : p.blockPosition().getZ();
                    int r = (int) Math.max(0, Math.min(8, num(a, "radius", 0)));
                    Map<String, List<String>> by = new TreeMap<>();
                    for (int x = -r; x <= r; x++)
                        for (int y = -r; y <= r; y++)
                            for (int z = -r; z <= r; z++) {
                                BlockPos bp = new BlockPos(cx + x, cy + y, cz + z);
                                var st = p.level().getBlockState(bp);
                                if (st.isAir() && r > 0) continue;
                                by.computeIfAbsent(st.toString().replace("Block{", "").replace("}", ""), k -> new ArrayList<>()).add(bp.toShortString());
                            }
                    StringBuilder sb = new StringBuilder();
                    by.forEach((k, v) -> sb.append(k).append(" ×").append(v.size()).append(": ").append(String.join(" | ", v.subList(0, Math.min(12, v.size()))))
                            .append(v.size() > 12 ? " …" : "").append('\n'));
                    return sb.length() == 0 ? "Только воздух" : sb.toString();
                }));

        tool("engine_config", "Настройки движка проекта (HUD, чат, меню выбора, магазин, журнал квестов, название зоны, время): прочитать, "
                + "или записать (set — полный JSON) и сразу применить в мире.",
                schema("project", "string", "Имя проекта", "+", "set", "string", "Новый JSON настроек целиком (необязательно)", "?"), a -> {
                    try {
                        Project pr = Project.openReadOnly(projectDir(str(a, "project", "")));
                        Path f = org.sprautestudio.spraute_engine.editor.engine.EngineTab.file(pr);
                        if (!a.has("set")) return Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8) : "(настроек нет — всё по умолчанию)\n"
                                + org.sprautestudio.spraute_engine.engine.EngineConfig.fromJson("{}").toJson();
                        var cfg = org.sprautestudio.spraute_engine.engine.EngineConfig.fromJson(str(a, "set", "{}"));
                        cfg.project = pr.name();
                        String json = cfg.toJson();
                        Files.writeString(f, json, StandardCharsets.UTF_8);
                        onClient(() -> {
                            if (Minecraft.getInstance().getConnection() != null) ClientState.get().engine(json);
                            return null;
                        });
                        return "Записано и применено";
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });

        tool("screenshot", "Снимок экрана игры (PNG), чтобы увидеть результат: катсцену, интерфейс, эффект.",
                schema("width", "integer", "Ширина картинки (до 1280, по умолчанию 960)", "?"), a -> {
                    int want = (int) Math.max(160, Math.min(1280, num(a, "width", 960)));
                    byte[] png = onClient(() -> {
                        Minecraft mc = Minecraft.getInstance();
                        try (NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                            int w = Math.min(want, full.getWidth()), h = Math.max(1, full.getHeight() * w / full.getWidth());
                            try (NativeImage small = new NativeImage(w, h, false)) {
                                full.resizeSubRectTo(0, 0, full.getWidth(), full.getHeight(), small);
                                return small.asByteArray();
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e.getMessage());
                        }
                    });
                    JsonObject r = new JsonObject();
                    JsonArray c = new JsonArray();
                    JsonObject img = new JsonObject();
                    img.addProperty("type", "image");
                    img.addProperty("data", Base64.getEncoder().encodeToString(png));
                    img.addProperty("mimeType", "image/png");
                    c.add(img);
                    r.add("content", c);
                    return r;
                });

        tool("chat", "Пишет сообщение игроку в чат игры (от имени ассистента), например чтобы попросить его что-то сделать.",
                schema("text", "string", "Текст", "+"), a -> onClient(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.displayClientMessage(Component.literal("§d[ИИ]§r " + str(a, "text", "")), false);
                    return "Отправлено";
                }));
        McpContent.register();
    }

    // ================================================================== helpers

    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    static String describe(Entity e) {
        String t = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
        String hp = e instanceof LivingEntity le ? " hp " + round(le.getHealth()) + "/" + round(le.getMaxHealth()) : "";
        return t + " «" + e.getName().getString() + "» at " + round(e.getX()) + " " + round(e.getY()) + " " + round(e.getZ()) + hp;
    }

    static String diagnostics(String src) {
        Analyzer.Result r = Analyzer.analyze(src);
        if (r.diagnostics.isEmpty()) return "Ошибок нет.";
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : r.diagnostics) sb.append(d).append('\n');
        return sb.toString();
    }

    private static Object scripts() {
        onClient(() -> {
            ClientState.get().requestStatus();
            return null;
        });
        try {
            Thread.sleep(300);
        } catch (InterruptedException ignored) {
        }
        List<String> out = new ArrayList<>();
        for (Packets.ScriptEntry e : ClientState.get().status().scripts()) {
            if (!e.spraute && !e.active) continue;
            out.add("#" + e.id + " «" + e.title + "»" + (e.project.isEmpty() ? "" : " (" + e.project + ")") + (e.active ? " активен" : " стоит")
                    + (e.paused ? ", пауза" : "") + (e.stage.isEmpty() ? "" : ", " + e.stage));
        }
        return out.isEmpty() ? "Нет сюжетов Spraute" : String.join("\n", out);
    }

    /** A command as the player (op), with its output. */
    private static String command(String cmd) {
        return onServer(() -> {
            ServerPlayer p = player();
            String line = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            List<String> out = new ArrayList<>();
            net.minecraft.commands.CommandSource capture = new net.minecraft.commands.CommandSource() {
                @Override
                public void sendSystemMessage(Component message) {
                    out.add(message.getString());
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };
            int res = p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withSource(capture).withPermission(4), line);
            return "/" + line + " → " + res + (out.isEmpty() ? "" : "\n" + String.join("\n", out));
        });
    }
}
