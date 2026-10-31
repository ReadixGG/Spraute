package org.sprautestudio.spraute_engine.runtime;

import org.sprautestudio.spraute_engine.core.script.ScriptInstance;
import org.sprautestudio.spraute_engine.core.script.ScriptPerformer;
import org.sprautestudio.spraute_engine.core.script.ScriptEnvironment;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.lang.Ast;
import org.sprautestudio.spraute_engine.lang.Diagnostic;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.net.SprauteNetwork;
import org.sprautestudio.spraute_engine.platform.Platform;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Server-side registry of deployed Spraute scripts and the world's running stories. */
public final class SprauteRuntime {
    private static final SprauteRuntime INSTANCE = new SprauteRuntime();

    public static SprauteRuntime get() {
        return INSTANCE;
    }

    public static final class Deployed {
        public final int id;
        public final String title;
        public final String project;
        public final String source;
        public final Ast.Program program;
        public final List<String> labels;

        Deployed(int id, String title, String project, String source, Ast.Program program, List<String> labels) {
            this.id = id;
            this.title = title;
            this.project = project;
            this.source = source;
            this.program = program;
            this.labels = labels;
        }
    }

    public record DeployResult(boolean ok, int id, List<String> messages) {}

    private final Map<Integer, Deployed> deployed = new TreeMap<>();
    /** Characters are global; reuse them across script instances with the same definition. */
    final Map<String, org.sprautestudio.spraute_engine.core.script.Character<?>> characterCache = new java.util.HashMap<>();
    private Constructor<SprauteScriptInstance> constructor;

    private SprauteRuntime() {}

    public Deployed deployed(int id) {
        return deployed.get(org.sprautestudio.spraute_engine.core.script.ScriptEnvironment.baseId(id));
    }

    public Map<Integer, Deployed> all() {
        return Collections.unmodifiableMap(deployed);
    }

    // ------------------------------------------------------------------ players

    public static MinecraftServer server() {
        return Platform.get().server();
    }

    public static List<ServerPlayer> players() {
        MinecraftServer s = server();
        return s == null ? List.of() : s.getPlayerList().getPlayers();
    }

    private static final ThreadLocal<ScriptInstance> CURRENT = new ThreadLocal<>();
    /** The player an "as" block is about, in the step running now. */
    private static final ThreadLocal<java.util.UUID> AS = new ThreadLocal<>();

    /** The story whose step runs now on this thread: player() and the audience are its. */
    public static ScriptInstance enter(ScriptInstance s) {
        ScriptInstance before = CURRENT.get();
        if (s == null) CURRENT.remove();
        else CURRENT.set(s);
        return before;
    }

    public static java.util.UUID getAs() {
        return AS.get();
    }

    public static void setAs(java.util.UUID player) {
        if (player == null) AS.remove();
        else AS.set(player);
    }

    private static ServerPlayer online(java.util.UUID id) {
        MinecraftServer s = server();
        return s == null || id == null ? null : s.getPlayerList().getPlayer(id);
    }

    /** The player a story is about: the one in its "as" block, else its protagonist; outside a story, the first on the server. */
    public static ServerPlayer player() {
        ServerPlayer as = online(AS.get());
        if (as != null) return as;
        ScriptInstance s = CURRENT.get();
        if (s != null) return s.protagonist();
        List<ServerPlayer> list = players();
        return list.isEmpty() ? null : list.get(0);
    }

    /** The protagonist of a story by its id, for what runs outside its steps (fights, casts). */
    public static ServerPlayer playerOf(int scriptId) {
        ScriptInstance s = env().map(e -> e.scriptInstances.get(scriptId)).orElse(null);
        if (s != null) return s.protagonist();
        List<ServerPlayer> list = players();
        return list.isEmpty() ? null : list.get(0);
    }

    private static List<ServerPlayer> only(ServerPlayer p) {
        return p == null ? List.of() : List.of(p);
    }

    /** Who sees and hears the story running now: one player in an "as" block or a solo story, else everybody. */
    public static List<ServerPlayer> audience() {
        ServerPlayer as = online(AS.get());
        if (as != null) return List.of(as);
        ScriptInstance s = CURRENT.get();
        return s != null && s.solo() ? only(s.protagonist()) : players();
    }

    /** The same for a story by its id, for what runs outside its steps. */
    public static List<ServerPlayer> audience(int scriptId) {
        ScriptInstance s = env().map(e -> e.scriptInstances.get(scriptId)).orElse(null);
        return s != null && s.solo() ? only(s.protagonist()) : players();
    }

    public static final String SOLO_TAG = "spraute_solo:";

    /** A solo story's entity is seen by its player alone. */
    public static void markOwned(net.minecraft.world.entity.Entity e, ScriptInstance s) {
        if (s != null && s.solo() && s.owner != null) e.addTag(SOLO_TAG + s.owner);
    }

    public static void markOwned(net.minecraft.world.entity.Entity e) {
        markOwned(e, CURRENT.get());
    }

    /** Whose alone this entity is; null — everybody's. */
    public static String ownerOf(net.minecraft.world.entity.Entity e) {
        for (String t : e.getTags()) if (t.startsWith(SOLO_TAG)) return t.substring(SOLO_TAG.length());
        return null;
    }

    public static boolean sees(net.minecraft.world.entity.Entity e, ServerPlayer p) {
        String owner = ownerOf(e);
        return owner == null || owner.equals(p.getUUID().toString());
    }

    public static boolean canEdit(ServerPlayer player) {
        MinecraftServer s = player.getServer();
        return player.hasPermissions(2) || (s != null && s.isSingleplayerOwner(player.getGameProfile()));
    }

    private ScriptEnvironment environment;

    public static Optional<ScriptEnvironment> env() {
        return Optional.ofNullable(INSTANCE.environment);
    }

    /** The world opened: its stories. */
    public void openEnvironment(MinecraftServer server) {
        if (!Platform.get().isClient()) org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.scan();
        environment = new ScriptEnvironment(server);
        environment.load();
    }

    public void closeEnvironment() {
        environment = null;
    }

    // ------------------------------------------------------------------ deploy

    public DeployResult deploy(String project, String source) {
        Analyzer.Result analysis = Analyzer.analyze(source);
        List<String> messages = new ArrayList<>();
        for (Diagnostic d : analysis.diagnostics) {
            if (d.isError()) messages.add("Строка " + (d.line + 1) + ": " + d.message);
        }
        Ast.Header header = analysis.program().header;
        if (header == null) messages.add("Нет заголовка script <id> \"Название\"");
        if (!messages.isEmpty()) return new DeployResult(false, header != null ? header.id : -1, messages);

        int id = header.id;
        Optional<ScriptEnvironment> env = env();
        if (env.isPresent()) {
            Constructor<? extends ScriptInstance> existing = env.get().constructors.get(id);
            if (existing != null && existing.getDeclaringClass() != SprauteScriptInstance.class) {
                messages.add("Id " + id + " уже занят (" + existing.getDeclaringClass().getSimpleName() + "). Выберите другой id.");
                return new DeployResult(false, id, messages);
            }
        }
        Deployed old = deployed.get(id);
        if (old != null && !old.project.equals(project)) {
            messages.add("Id " + id + " уже используется скриптом «" + old.title + "» из проекта " + old.project);
            return new DeployResult(false, id, messages);
        }

        List<String> labels = new ArrayList<>(analysis.labels.keySet());
        deployed.put(id, new Deployed(id, header.title, project, source, analysis.program(), labels));
        save(id);
        env.ifPresent(this::registerInEnv);
        messages.add("Сюжет #" + id + " «" + header.title + "» загружен в игру");
        int warnings = analysis.warningCount();
        if (warnings > 0) messages.add("Предупреждений: " + warnings);
        return new DeployResult(true, id, messages);
    }

    public boolean undeploy(int id) {
        Deployed d = deployed.remove(id);
        if (d == null) return false;
        env().ifPresent(env -> {
            if (env.scriptInstances.containsKey(id)) env.stop(id);
            Constructor<? extends ScriptInstance> c = env.constructors.get(id);
            if (c != null && c.getDeclaringClass() == SprauteScriptInstance.class) env.constructors.remove(id);
        });
        Path dir = storageDir();
        if (dir != null) {
            try {
                Files.deleteIfExists(dir.resolve(id + ".sst"));
                Files.deleteIfExists(dir.resolve(id + ".project"));
            } catch (IOException e) {
                SprauteEngine.LOGGER.warn("Failed to delete deployed script {}", id, e);
            }
        }
        registerCategories();
        return true;
    }

    /** Puts every deployed script into the constructor table (idempotent). */
    public void registerInEnv(ScriptEnvironment env) {
        if (constructor == null) {
            try {
                constructor = SprauteScriptInstance.class.getConstructor(ScriptEnvironment.class, int.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }
        for (Deployed d : deployed.values()) {
            Constructor<? extends ScriptInstance> existing = env.constructors.get(d.id);
            if (existing == null) {
                env.constructors.put(d.id, constructor);
            } else if (existing.getDeclaringClass() != SprauteScriptInstance.class) {
                report(d.id, "Id " + d.id + " занят — скрипт не зарегистрирован");
            }
        }
        registerCategories();
    }

    /** True if some deployed script is missing from the environment. */
    public boolean needsRegistration(ScriptEnvironment env) {
        for (Integer id : deployed.keySet()) if (!env.constructors.containsKey((int) id)) return true;
        return false;
    }

    private void registerCategories() {
        if (environment == null) return;
        Map<String, List<Integer>> byProject = new LinkedHashMap<>();
        for (Deployed d : deployed.values()) byProject.computeIfAbsent(categoryName(d.project), k -> new ArrayList<>()).add(d.id);
        environment.categories.clear();
        byProject.forEach((k, v) -> environment.categories.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
    }

    public static String categoryName(String project) {
        String s = project.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return "spraute_" + (s.isEmpty() ? "project" : s);
    }

    // ------------------------------------------------------------- persistence

    private Path storageDir() {
        MinecraftServer s = server();
        if (s == null) return null;
        return s.getWorldPath(LevelResource.ROOT).resolve("spraute");
    }

    private void save(int id) {
        Path dir = storageDir();
        Deployed d = deployed.get(id);
        if (dir == null || d == null) return;
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(id + ".sst"), d.source, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(id + ".project"), d.project, StandardCharsets.UTF_8);
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Failed to save deployed script {}", id, e);
        }
    }

    /** Loads scripts deployed into this world earlier. */
    public void loadWorld() {
        deployed.clear();
        Path dir = storageDir();
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".sst"))::iterator) {
                String source = Files.readString(f, StandardCharsets.UTF_8);
                Path projectFile = Path.of(f.toString().replace(".sst", ".project"));
                String project = Files.exists(projectFile) ? Files.readString(projectFile, StandardCharsets.UTF_8).trim() : "world";
                Analyzer.Result a = Analyzer.analyze(source);
                if (a.hasErrors() || a.program().header == null) {
                    SprauteEngine.LOGGER.warn("Skipping broken Spraute script {}", f);
                    continue;
                }
                Ast.Header h = a.program().header;
                deployed.put(h.id, new Deployed(h.id, h.title, project, source, a.program(), new ArrayList<>(a.labels.keySet())));
            }
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Failed to load Spraute scripts", e);
        }
        loadGuis(dir);
        SprauteEngine.LOGGER.info("Loaded {} Spraute scripts", deployed.size());
    }

    public void clear() {
        deployed.clear();
    }

    // ----------------------------------------------------------------- logging

    // ------------------------------------------------------------- autostart

    /** Players who entered the world and when (server tick) their autostart stories start. */
    private final Map<java.util.UUID, Integer> autostartAt = new java.util.HashMap<>();

    /** A player entered: autostart stories begin a moment later, when the client has loaded. */
    public void playerJoined(ServerPlayer player) {
        MinecraftServer s = server();
        if (s != null) autostartAt.put(player.getUUID(), s.getTickCount() + 40);
        org.sprautestudio.spraute_engine.quest.Quests.sync(player, false);
        org.sprautestudio.spraute_engine.zone.Zones.syncTo(player);
        org.sprautestudio.spraute_engine.content.ProjectContent.sendIndex(player);
    }

    public void tickAutostart() {
        MinecraftServer s = server();
        if (s == null || autostartAt.isEmpty()) return;
        for (java.util.Iterator<Map.Entry<java.util.UUID, Integer>> it = autostartAt.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<java.util.UUID, Integer> e = it.next();
            if (s.getTickCount() < e.getValue()) continue;
            it.remove();
            ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
            if (p != null) autostart(p);
        }
    }

    private void autostart(ServerPlayer player) {
        Optional<ScriptEnvironment> envOpt = env();
        if (envOpt.isEmpty()) return;
        java.util.Set<String> done = autostartDone();
        boolean changed = false;
        for (Deployed d : deployed.values()) {
            Ast.Header h = d.program.header;
            if (h == null || !h.autostart) continue;
            String mark = h.solo ? d.id + ":" + player.getUUID() : String.valueOf(d.id);
            if (envOpt.get().instanceFor(d.id, player) != null) continue; // already running
            if (h.once && done.contains(mark)) continue;
            MinecraftServer s = server();
            s.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "script start " + d.id);
            log(d.id, "Автозапуск: «" + d.title + "»");
            if (done.add(mark)) changed = true;
        }
        if (changed) saveAutostartDone(done);
    }

    private java.util.Set<String> autostartDone() {
        java.util.Set<String> out = new java.util.TreeSet<>();
        Path dir = storageDir();
        if (dir == null) return out;
        try {
            Path f = dir.resolve("autostarted.txt");
            if (Files.exists(f)) for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) if (!line.isBlank()) out.add(line.trim());
        } catch (IOException ignored) {
        }
        return out;
    }

    private void saveAutostartDone(java.util.Set<String> done) {
        Path dir = storageDir();
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("autostarted.txt"), String.join("\n", done) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public void log(int scriptId, String text) {
        broadcast(Packets.Log.INFO, "#" + scriptId, text);
    }

    /** Exceptions of running stories: logged, and the cause shown in the editor console. */
    public static void installErrorHook() {
        ScriptEnvironment.onError = (id, error) -> {
            if (!(error instanceof SprauteRuntimeException)) SprauteEngine.LOGGER.error("Story #{} failed", id, error);
            Throwable root = error;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            StackTraceElement where = null;
            for (StackTraceElement e : root.getStackTrace()) {
                if (e.getClassName().startsWith("org.sprautestudio")) {
                    where = e;
                    break;
                }
            }
            if (root instanceof SprauteRuntimeException) {
                get().report(id, "Сюжет остановлен из-за ошибки: " + root.getMessage());
                return;
            }
            get().report(id, "Сюжет остановлен из-за ошибки: " + root.getClass().getSimpleName()
                    + (root.getMessage() != null ? ": " + root.getMessage() : "")
                    + (where != null ? " (" + where.getClassName().substring(where.getClassName().lastIndexOf('.') + 1) + "." + where.getMethodName() + ")" : "")
                    + ". Подробности в latest.log");
        };
    }

    // ------------------------------------------------------------------- GUIs

    /** project -> GUI name -> JSON, sent along with every deploy. */
    private final Map<String, Map<String, String>> guis = new LinkedHashMap<>();

    public void putGuis(String project, Map<String, String> map) {
        if (map == null) return;
        guis.put(project, new LinkedHashMap<>(map));
        // the engine's HUD is one of these interfaces: changed, it reaches the players now
        // the engine's windows are some of these interfaces: changed, they reach the players now
        var cfg = org.sprautestudio.spraute_engine.engine.EngineServer.config();
        var server = Platform.get().server();
        if (server != null && project.equals(cfg.project)) {
            org.sprautestudio.spraute_engine.engine.EngineConfig c = null;
            for (var en : cfg.layouts.entrySet()) {
                String json = en.getValue() == null || en.getValue().isEmpty() ? null : map.get(en.getValue());
                if (json == null || json.equals(cfg.layoutJson.get(en.getKey()))) continue;
                if (c == null) c = cfg.copy();
                c.layoutJson.put(en.getKey(), json);
            }
            if (c != null) org.sprautestudio.spraute_engine.engine.EngineServer.set(server, c);
        }
        Path dir = storageDir();
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(categoryName(project) + ".guis.json"), new com.google.gson.Gson().toJson(map), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(categoryName(project) + ".guis.project"), project, StandardCharsets.UTF_8);
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Failed to save GUIs of {}", project, e);
        }
    }

    /** Every deployed project's interfaces, cutscenes, shops, zones…: project -> name -> JSON. */
    public Map<String, Map<String, String>> allGuis() {
        return java.util.Collections.unmodifiableMap(guis);
    }

    public String gui(String project, String name) {
        Map<String, String> m = guis.get(project);
        return m == null ? null : m.get(name);
    }

    @SuppressWarnings("unchecked")
    private void loadGuis(Path dir) {
        guis.clear();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".guis.json"))::iterator) {
                Path pf = Path.of(f.toString().replace(".guis.json", ".guis.project"));
                String project = Files.exists(pf) ? Files.readString(pf, StandardCharsets.UTF_8).trim() : "world";
                Map<String, String> m = new com.google.gson.Gson().fromJson(Files.readString(f, StandardCharsets.UTF_8), LinkedHashMap.class);
                if (m != null) guis.put(project, m);
            }
        } catch (Exception e) {
            SprauteEngine.LOGGER.warn("Failed to load Spraute GUIs", e);
        }
    }

    /** A button press or closing of a story GUI, from the client. */
    public void onGuiEvent(ServerPlayer player, Packets.GuiEvent msg) {
        env().ifPresent(env -> {
            ScriptInstance si = env.scriptInstances.get(msg.scriptId());
            if (!(si instanceof SprauteScriptInstance s)) return;
            // an interface opened for one player answers to that player alone
            if (msg.gui().equals(s.openGui) && s.guiPlayer != null && !s.guiPlayer.equals(player.getUUID())) return;
            if (msg.inputs() != null) s.guiInputs.putAll(msg.inputs());
            if (msg.kind().equals("click")) {
                s.lastButton = msg.element();
                s.buttonSeq++;
            } else if (msg.kind().startsWith("slot_put:")) {
                String[] p = msg.kind().split(":");
                try {
                    GuiSlots.put(s, player, msg.element(), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                } catch (RuntimeException ignored) {
                }
            } else if (msg.kind().startsWith("slot_take:")) {
                try {
                    GuiSlots.take(s, player, msg.element(), Integer.parseInt(msg.kind().substring(10)));
                } catch (RuntimeException ignored) {
                }
            } else if (msg.kind().equals("close")) {
                if (msg.gui().equals(s.openGui)) s.openGui = null;
                s.guiCloseSeq++;
            }
        });
    }

    public void report(int scriptId, String text) {
        Deployed d = deployed(scriptId);
        String who = d != null ? "«" + d.title + "» #" + scriptId + (d.project.isEmpty() ? "" : " (" + d.project + ")") : "#" + scriptId;
        SprauteEngine.LOGGER.warn("[Spraute {}] {}", who, text);
        broadcast(Packets.Log.ERROR, who, text);
    }

    public void broadcast(int level, String source, String text) {
        for (ServerPlayer p : players()) {
            if (canEdit(p)) SprauteNetwork.sendTo(p, new Packets.Log(level, source, text));
        }
    }

    // ------------------------------------------------------------------ status

    public Packets.Status status() {
        List<Packets.ScriptEntry> entries = new ArrayList<>();
        Optional<ScriptEnvironment> envOpt = env();
        List<String> categories = envOpt.<List<String>>map(e -> new ArrayList<>(e.categories.keySet())).orElseGet(ArrayList::new);
        if (envOpt.isPresent()) {
            ScriptEnvironment env = envOpt.get();
            List<Integer> ids = new ArrayList<>(env.constructors.keySet());
            Collections.sort(ids);
            for (int id : ids) {
                Packets.ScriptEntry e = new Packets.ScriptEntry();
                e.id = id;
                Constructor<? extends ScriptInstance> c = env.constructors.get(id);
                Deployed d = deployed.get(id);
                if (d != null && c != null && c.getDeclaringClass() == SprauteScriptInstance.class) {
                    e.spraute = true;
                    e.title = d.title;
                    e.project = d.project;
                    e.labels.addAll(d.labels);
                } else if (c != null) {
                    e.title = c.getDeclaringClass().getSimpleName();
                    e.project = "";
                }
                ScriptInstance inst = env.anyInstance(id);
                if (inst != null) {
                    e.active = true;
                    e.paused = inst.isPaused();
                    ScriptPerformer main = inst.getMainPerformer();
                    if (main != null) {
                        e.stage = String.valueOf(main.getStage());
                        e.actionIndex = main.getActionIndex();
                        e.actionCount = main.getActionsAmount();
                        e.doing = main.describe();
                        if (!e.spraute) {
                            for (String l : main.labelPointsNames) if (!l.startsWith("__sp_")) e.labels.add(l);
                        }
                    }
                }
                entries.add(e);
            }
        }
        return new Packets.Status(envOpt.isPresent(), entries, categories);
    }
}
