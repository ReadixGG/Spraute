package org.zonarstudio.spraute_engine.script;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.scene.SceneActiveState;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Manages loading, compiling, caching, and running .spr script files.
 * Scripts are loaded from the spraute_engine/scripts/ directory.
 */
public class ScriptManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SCRIPT_EXTENSION = ".spr";
    private static final String DEFAULT_SCRIPTS_PREFIX = "/default_scripts/";
    private static final List<String> BUNDLED_DEFAULT_SCRIPTS = List.of(
            "notification.spr",
            "spraute_chat.spr",
            "economy.spr",
            "spraute_quests.spr",
            "dialog_classic.spr",
            "dialog_cinematic.spr",
            "story_dialog_test.spr",
            "test_camera.spr",
            "test_hitbox.spr",
            "billboard_test.spr",
            "test_combat.spr"
    );
    /** Engine libraries: always refresh from the jar so updates reach existing workspaces. */
    private static final Set<String> ENGINE_LIBRARY_SCRIPTS = Set.of(
            "notification.spr",
            "spraute_chat.spr",
            "economy.spr",
            "spraute_quests.spr",
            "dialog_classic.spr",
            "dialog_cinematic.spr"
    );

    private static ScriptManager INSTANCE;

    private final Path scriptsDir;
    private final Map<String, CompiledScript> compiledScripts = new HashMap<>();
    /** Scripts that failed to compile: name -> error message (with line if available) */
    private final Map<String, String> failedScripts = new HashMap<>();
    private final ScriptCompiler compiler = new ScriptCompiler();
    private final ScriptExecutor executor = new ScriptExecutor();
    private final Set<UUID> debugPlayers = new HashSet<>();

    public void toggleDebug(net.minecraft.world.entity.player.Player player) {
        if (debugPlayers.contains(player.getUUID())) {
            debugPlayers.remove(player.getUUID());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Spraute] §fОтладка событий отключена."));
        } else {
            debugPlayers.add(player.getUUID());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[Spraute] §fОтладка событий включена."));
        }
    }

    public ScriptManager(Path gameDir) {
        this.scriptsDir = gameDir.resolve("spraute_engine").resolve("scripts");
    }

    private final Map<UUID, Map<String, Object>> playerSessionData = new java.util.concurrent.ConcurrentHashMap<>();

    public Map<String, Object> getPlayerSessionData(UUID uuid) {
        return playerSessionData.computeIfAbsent(uuid, k -> new java.util.concurrent.ConcurrentHashMap<>());
    }

    public static ScriptManager getInstance() {
        return INSTANCE;
    }

    public static void init(Path gameDir) {
        if (INSTANCE != null) {
            INSTANCE.stopAll();
            INSTANCE.clearGlobalVariables();
        }
        INSTANCE = new ScriptManager(gameDir);
        INSTANCE.ensureDirectoryExists();
        INSTANCE.copyDefaultScripts();
        INSTANCE.loadAll();
    }

    /**
     * Load and compile all .spr scripts from the scripts directory.
     */
    public void loadAll() {
        compiledScripts.clear();
        failedScripts.clear();
        org.zonarstudio.spraute_engine.script.ScriptTriggerRegistry.reset();
        int count = 0;
        List<Path> scriptFiles = new ArrayList<>(
                org.zonarstudio.spraute_engine.resource.WorkspaceContent.allSprFiles());
        for (Path file : scriptFiles) {
            if (org.zonarstudio.spraute_engine.registry.SyncedRegistrySnapshot.isSyncedRegistryFile(file)) {
                continue;
            }
            if (loadScriptFile(file)) count++;
        }

        LOGGER.info("Loaded {} script(s) ({} with errors)", count, failedScripts.size());
        if (!failedScripts.isEmpty()) {
            ScriptConsole.warn("compile",
                    "Загружено скриптов: " + count + ", с ошибками компиляции: " + failedScripts.size());
        } else {
            ScriptConsole.info("compile", "Загружено скриптов: " + count);
        }

        applyCompiledTriggers();
        org.zonarstudio.spraute_engine.script.ScriptTriggerRegistry.get()
                .migrateFromConfig(org.zonarstudio.spraute_engine.config.ScriptTriggersConfig.get());
    }

    private void applyCompiledTriggers() {
        var registry = org.zonarstudio.spraute_engine.script.ScriptTriggerRegistry.get();
        for (CompiledScript cs : compiledScripts.values()) {
            if (cs.isAutorunSelf()) registry.addAutorun(cs.getName());
            for (String target : cs.getAutorunTargets()) registry.addAutorun(target);
        }
    }

    /**
     * Legacy compatibility hook. Autorun declarations still parse and compile,
     * but standalone scripts no longer start with the server; startup ownership
     * now belongs to scene documents.
     */
    public void runAutorunScripts(net.minecraft.server.MinecraftServer server) {
        // Intentionally no-op so old `autorun` code remains source-compatible.
    }

    /**
     * Recompile all scripts from disk without stopping running instances.
     * Saved files are picked up on the next {@link #run}/{@link #runInstance}.
     */
    public void refreshFromDisk() {
        LOGGER.info("Refreshing scripts from disk...");
        org.zonarstudio.spraute_engine.registry.CustomProjectileRegistry.invalidateAndReparse();
        org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.invalidateAll();
        org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.reload();
        loadAll();
    }

    /** @deprecated Prefer {@link #refreshFromDisk()}; kept for debug overlay compatibility. */
    public void reload() {
        refreshFromDisk();
    }

    /**
     * Recompile one script from disk (by logical name, with or without {@code plugins/} prefix).
     */
    public void refreshScript(String name) {
        if (name == null || name.isBlank()) return;
        Path file = resolveScriptFile(name.trim());
        if (file != null) refreshScriptFile(file);
    }

    /** Recompile a {@code .spr} file under the scripts directory after editor save. */
    public void refreshScriptFile(Path file) {
        if (file == null) return;
        try {
            Path abs = file.toAbsolutePath().normalize();
            Path root = scriptsDir.toAbsolutePath().normalize();
            if (!abs.startsWith(root) || !Files.isRegularFile(abs)
                    || !abs.getFileName().toString().endsWith(SCRIPT_EXTENSION)) {
                return;
            }
            String relative = root.relativize(abs).toString().replace('\\', '/');
            String fullName = relative.substring(0, relative.length() - SCRIPT_EXTENSION.length());
            String primaryName = fullName;
            if (fullName.startsWith("plugins/")) {
                int slash = fullName.lastIndexOf('/');
                if (slash >= 0 && slash < fullName.length() - 1) {
                    primaryName = fullName.substring(slash + 1);
                }
            }
            compiledScripts.remove(primaryName);
            compiledScripts.remove(fullName);
            failedScripts.remove(primaryName);
            failedScripts.remove(fullName);
            loadScriptFile(abs);
            org.zonarstudio.spraute_engine.registry.CustomProjectileRegistry.invalidateAndReparse();
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh script {}: {}", file, e.getMessage());
        }
    }

    private Path resolveScriptFile(String name) {
        Path overlay = org.zonarstudio.spraute_engine.resource.WorkspaceContent.resolve("scripts/" + name + SCRIPT_EXTENSION);
        if (overlay != null) return overlay;
        String want = name.contains("/")
                ? name.substring(name.lastIndexOf('/') + 1) + SCRIPT_EXTENSION
                : name + SCRIPT_EXTENSION;
        for (Path p : org.zonarstudio.spraute_engine.resource.WorkspaceContent.allSprFiles()) {
            if (p.getFileName().toString().equals(want)) return p;
        }
        return null;
    }

    /**
     * Run a script by name.
     *
     * @param name   script name (without .spr extension)
     * @param source the command source to execute in context of
     * @return true if the script was found and executed
     */
    public boolean run(String name, CommandSourceStack source) {
        return run(name, source, null);
    }

    /**
     * Run a script with initial variables (e.g. from run_script — передаются автоматически).
     *
     * @param name   script name (without .spr extension)
     * @param source the command source to execute in context of
     * @param initialVariables variables to inject (player, npc ids, etc.)
     * @return true if the script was found and executed
     */
    public boolean run(String name, CommandSourceStack source, Map<String, Object> initialVariables) {
        refreshScript(name);

        String compileError = failedScripts.get(name);
        if (compileError != null) {
            ScriptConsole.compileError(name, compileError);
            if (source != null) {
                source.sendFailure(net.minecraft.network.chat.Component.literal(
                        SprauteMessages.chatCompileError(name, compileError, SprauteMessages.isEnglish(source))));
            }
            return false;
        }

        CompiledScript script = compiledScripts.get(name);
        if (script == null) {
            LOGGER.warn("Script not found: '{}'", name);
            ScriptConsole.error("run", "Скрипт не найден: '" + name + "'");
            if (source != null) {
                source.sendFailure(net.minecraft.network.chat.Component.literal(
                        SprauteMessages.chatScriptNotFound(name, SprauteMessages.isEnglish(source))));
            }
            return false;
        }

        try {
            Map<String, Object> vars = new HashMap<>();
            if (initialVariables != null) vars.putAll(initialVariables);
            seedInheritedSceneContext(vars);
            executor.start(script, source, vars);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error executing script '{}': {}", name, e.getMessage());
            ScriptConsole.error(name, "Не удалось запустить: " + e.getMessage());
            if (source != null) {
                source.sendFailure(net.minecraft.network.chat.Component.literal(
                        SprauteMessages.chatRunFailed(e.getMessage(), SprauteMessages.isEnglish(source))));
            }
            return false;
        }
    }

    public void tick() {
        executor.tick();

        if (!debugPlayers.isEmpty()) {
            List<org.zonarstudio.spraute_engine.network.SyncDebugStatePacket.ScriptDebugData> debugData = executor.getDebugData();
            List<String> allScripts = new ArrayList<>(getScriptNames());
            allScripts.sort(String::compareTo);
            org.zonarstudio.spraute_engine.network.SyncDebugStatePacket packet = new org.zonarstudio.spraute_engine.network.SyncDebugStatePacket(debugData, allScripts);
            
            // net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() can get players, but we can do it safer
            // Just broadcast to the players who have it active.
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (UUID uuid : debugPlayers) {
                    net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), packet);
                    }
                }
            }
        }
    }

    /** Stop all running scripts. */
    public void stopAll() {
        executor.stopAll();
    }

    /** Stop a specific script by name. Returns true if found and stopped. */
    public boolean stopScript(String name) {
        return executor.stopScript(name);
    }

    /** Runs a separate script instance, identified independently from its source file. */
    public boolean runInstance(String name, String instanceId, CommandSourceStack source,
                               Map<String, Object> initialVariables) {
        refreshScript(name);

        String compileError = failedScripts.get(name);
        if (compileError != null) {
            // Always mirror to the editor console before any attempt to start.
            ScriptConsole.compileError(name, compileError);
            if (source != null) {
                source.sendFailure(net.minecraft.network.chat.Component.literal(
                        SprauteMessages.chatCompileError(name, compileError, SprauteMessages.isEnglish(source))));
            }
            LOGGER.warn("Script '{}' has compile error (instance '{}'): {}", name, instanceId, compileError);
            return false;
        }
        CompiledScript script = compiledScripts.get(name);
        if (script == null) {
            ScriptConsole.error("run", "Скрипт не найден: '" + name + "'");
            if (source != null) {
                source.sendFailure(net.minecraft.network.chat.Component.literal(
                        SprauteMessages.chatScriptNotFound(name, SprauteMessages.isEnglish(source))));
            }
            LOGGER.warn("Script not found for instance '{}': '{}'", instanceId, name);
            return false;
        }
        return executor.startInstance(script, source, initialVariables, instanceId);
    }

    public boolean isInstanceRunning(String instanceId) {
        return executor.isInstanceRunning(instanceId);
    }

    public boolean stopInstance(String instanceId) {
        return executor.stopInstance(instanceId);
    }

    public void clearGlobalVariables() {
        executor.clearGlobalVariables();
    }

    public boolean removeGlobalVariable(String name) {
        return executor.removeGlobalVariable(name);
    }

    public java.util.Set<String> getGlobalVariableNames() {
        return executor.getGlobalVariableNames();
    }

    /** Get names of currently running scripts. */
    public Set<String> getRunningScriptNames() {
        return executor.getRunningScriptNames();
    }

    public void onInteract(net.minecraft.world.entity.Entity target, net.minecraft.world.entity.Entity interactor) {
        executor.onInteract(target, interactor);
    }

    public void onKeybind(String key, net.minecraft.world.entity.player.Player player) {
        executor.onKeybind(key, player);
    }

    public void onPlayerJoin(net.minecraft.server.level.ServerPlayer player) {
        executor.onPlayerJoin(player);
    }

    public void onDeath(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.entity.Entity killer) {
        executor.onDeath(entity, killer);
    }

    /** Клик по кнопке UI или закрытие экрана (ESC). */
    public void onUiAction(net.minecraft.server.level.ServerPlayer player, String widgetId, boolean closed) {
        executor.onUiAction(player, widgetId, closed);
    }

    public void onUiAction(net.minecraft.server.level.ServerPlayer player, byte action, String widgetId, int mouseButton) {
        executor.onUiAction(player, action, widgetId, mouseButton);
    }

    /** Unblock await uiClick / uiClose / uiInput for this player (e.g. emergency GUI reset hotkey). */
    public void cancelUiWaitsForPlayer(net.minecraft.server.level.ServerPlayer player) {
        executor.cancelUiWaitsForPlayer(player);
    }

    /** Stop player-owned scripts and release session-only data on logout. */
    public void onPlayerLoggedOut(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        executor.onPlayerLoggedOut(player);
        UUID playerId = player.getUUID();
        playerSessionData.remove(playerId);
        debugPlayers.remove(playerId);
    }

    /** Legacy UUID-only cleanup entry point. */
    public void onPlayerLoggedOut(UUID playerId) {
        if (playerId == null) return;
        executor.onPlayerLoggedOut(playerId);
        playerSessionData.remove(playerId);
        debugPlayers.remove(playerId);
    }

    public void onUiOverlapAction(net.minecraft.server.level.ServerPlayer player, String id1, String id2, boolean overlapping) {
        executor.onUiOverlapAction(player, id1, id2, overlapping);
    }

    public void onSceneHitboxTouch(net.minecraft.server.level.ServerPlayer player, String sceneId,
                                   String nodeId, String nodeName, String triggerId, boolean entering) {
        executor.onSceneHitboxTouch(player, sceneId, nodeId, nodeName, triggerId, entering);
    }

    public boolean onClickBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, boolean isLeft) {
        return executor.onClickBlock(player, pos, block, isLeft);
    }

    public boolean onBreakBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
        return executor.onBreakBlock(player, pos, block);
    }

    public boolean onPlaceBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
        return executor.onPlaceBlock(player, pos, block);
    }

    public boolean onOpenChest(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, net.minecraft.world.level.Level level) {
        return executor.onOpenChest(player, pos, block, level);
    }

    public boolean onOpenDoor(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, net.minecraft.world.level.Level level) {
        return executor.onOpenDoor(player, pos, block, level);
    }

    public boolean onUseBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, net.minecraft.world.level.Level level) {
        return executor.onUseBlock(player, pos, block, level);
    }

    public void onChat(net.minecraft.server.level.ServerPlayer player, String message) {
        executor.onChat(player, message);
    }

    public boolean onPlayerAction(net.minecraft.world.entity.player.Player player, String actionType, Object target) {
        return executor.onPlayerAction(player, actionType, target);
    }

    public boolean onPlayerAttack(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target) {
        return executor.onPlayerAttack(player, target);
    }

    public boolean onPlayerSprint(net.minecraft.world.entity.player.Player player) {
        return executor.onPlayerSprint(player);
    }

    public void onPlayerDimensionChange(net.minecraft.server.level.ServerPlayer player, String fromDimension, String toDimension) {
        executor.onPlayerDimensionChange(player, fromDimension, toDimension);
    }

    public void onOrbPickup(net.minecraft.server.level.ServerPlayer player, String texture, int amount) {
        executor.onOrbPickup(player, texture, amount);
    }

    public void onProjectileHit(String projectileId, String hitType, double x, double y, double z,
                                net.minecraft.world.entity.Entity target, net.minecraft.world.entity.Entity owner) {
        executor.onProjectileHit(projectileId, hitType, x, y, z, target, owner);
    }

    public void onTradeBuy(net.minecraft.server.level.ServerPlayer player, String itemId, int price) {
        onCustomEvent("tradeBuy", java.util.List.of(player, itemId, price));
    }

    public void onTradeSell(net.minecraft.server.level.ServerPlayer player, String itemId, int price) {
        onCustomEvent("tradeSell", java.util.List.of(player, itemId, price));
    }

    /** Fan-out a script-registered custom event to every running script. */
    public void onCustomEvent(String eventName, java.util.List<Object> payload) {
        executor.onCustomEvent(eventName, payload);
    }

    public void onRelGift(net.minecraft.server.level.ServerPlayer player, String npcId, String itemId, int repDelta) {
        executor.onRelGift(player, npcId, itemId, repDelta);
    }

    public void onRelRepChange(net.minecraft.server.level.ServerPlayer player, String npcId, int oldRep, int newRep) {
        executor.onRelRepChange(player, npcId, oldRep, newRep);
    }

    public void onRelTalk(net.minecraft.server.level.ServerPlayer player, String npcId) {
        executor.onRelTalk(player, npcId);
    }

    public void onRelButton(net.minecraft.server.level.ServerPlayer player, String npcId, String btnId) {
        executor.onRelButton(player, npcId, btnId);
    }

    /**
     * Get all loaded script names (including those with compile errors) for tab completion.
     */
    public Set<String> getScriptNames() {
        Set<String> all = new java.util.HashSet<>(compiledScripts.keySet());
        all.addAll(failedScripts.keySet());
        return Collections.unmodifiableSet(all);
    }

    /** Check if a script failed to compile. */
    public boolean hasCompileError(String name) {
        return failedScripts.containsKey(name);
    }

    private final Map<String, Object> consoleLocals = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Compile and run ephemeral Spraute source (editor console).
     * Locals persist across console runs (same map); {@code global}/{@code world} are shared with all scripts.
     */
    public boolean eval(String source, CommandSourceStack commandSource) {
        if (source == null || source.isBlank()) {
            ScriptConsole.warn("console", "Пустой код");
            return false;
        }
        String trimmed = source.strip();
        String preview = trimmed.length() > 120 ? trimmed.substring(0, 117) + "..." : trimmed;
        preview = preview.replace('\n', ' ').replace('\r', ' ');
        ScriptConsole.info("console", ">> " + preview);
        try {
            CompiledScript compiled = compileSource("__console__", trimmed);
            String instanceId = "console:" + System.nanoTime();
            seedConsoleSceneContext(consoleLocals);
            boolean ok = executor.startInstance(compiled, commandSource, consoleLocals, instanceId, true);
            if (!ok) {
                ScriptConsole.error("console", "Не удалось запустить (конфликт instance?)");
            }
            return ok;
        } catch (Exception e) {
            String errorMsg = formatCompileError(e);
            ScriptConsole.compileError("console", errorMsg);
            if (commandSource != null) {
                commandSource.sendFailure(net.minecraft.network.chat.Component.literal(
                        SprauteMessages.chatCompileError("console", errorMsg,
                                SprauteMessages.isEnglish(commandSource))));
            }
            return false;
        }
    }

    public Path getScriptsDir() {
        return scriptsDir;
    }

    public CompiledScript getCompiledScript(String name) {
        return compiledScripts.get(name);
    }

    // --- Internal methods ---

    /** When one scene is live, console {@code create npc/...} attaches under its root. */
    private static void seedConsoleSceneContext(Map<String, Object> locals) {
        if (locals == null) return;
        if (locals.containsKey("_scene")) return;
        SceneActiveState state = SceneRuntime.getSingletonActiveState();
        if (state == null) return;
        locals.put("_scene", state.sceneView());
        // Root itself only accepts PLAYERS/GLOBAL_WORLD/LOCAL_WORLD since the worlds split —
        // console `create` with no scene-node context attaches into the shared Global World.
        locals.putIfAbsent("_node", state.nodeView(state.document.ensureGlobalWorldNode()));
    }

    /**
     * New scripts started via {@code startScript} / {@code run} inherit {@code _scene}
     * from the caller when parent left parent empty in visual blocks.
     */
    public static void seedInheritedSceneContext(Map<String, Object> locals) {
        if (locals == null || locals.containsKey("_scene")) return;
        Map<String, Object> caller = ScriptExecutor.currentVariables();
        if (caller != null && caller.containsKey("_scene")) {
            locals.put("_scene", caller.get("_scene"));
            Object node = caller.get("_node");
            if (node != null) locals.putIfAbsent("_node", node);
            Object scriptNode = caller.get("_scriptNode");
            if (scriptNode != null) locals.putIfAbsent("_scriptNode", scriptNode);
            return;
        }
        seedConsoleSceneContext(locals);
    }

    private CompiledScript compileSource(String name, String source) {
        ScriptLexer lexer = new ScriptLexer(source);
        List<ScriptToken> tokens = lexer.tokenize();

        ScriptParser parser = new ScriptParser(tokens);
        List<ScriptNode> nodes = parser.parse();

        return compiler.compile(name, nodes);
    }

    private String getScriptName(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.substring(0, fileName.length() - SCRIPT_EXTENSION.length());
    }

    private String scriptRelative(Path file) {
        try {
            Path abs = file.toAbsolutePath().normalize();
            Path root = scriptsDir.toAbsolutePath().normalize();
            if (abs.startsWith(root)) {
                return root.relativize(abs).toString().replace('\\', '/');
            }
        } catch (RuntimeException ignored) {
        }
        String packRel = org.zonarstudio.spraute_engine.resource.StoryPackRegistry.relativize(file);
        if (packRel != null) {
            if (packRel.startsWith("scripts/")) packRel = packRel.substring("scripts/".length());
            return packRel;
        }
        return file.getFileName().toString();
    }

    /**
     * Load one .spr file. Scripts under {@code plugins/...} are registered by short name (filename only)
     * so {@code import("npc_relations")} works from any script.
     */
    private boolean loadScriptFile(Path file) {
        String relative = scriptRelative(file);
        String fullName = relative.endsWith(SCRIPT_EXTENSION)
                ? relative.substring(0, relative.length() - SCRIPT_EXTENSION.length())
                : relative;
        String primaryName = fullName;
        if (fullName.startsWith("plugins/")) {
            int slash = fullName.lastIndexOf('/');
            if (slash >= 0 && slash < fullName.length() - 1) {
                primaryName = fullName.substring(slash + 1);
            }
        }
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            CompiledScript compiled = compileSource(primaryName, source);
            compiledScripts.put(primaryName, compiled);
            if (!primaryName.equals(fullName) && !compiledScripts.containsKey(fullName)) {
                compiledScripts.put(fullName, compiled);
            }
            LOGGER.info("Compiled script: {} ({} instructions)", primaryName, compiled.getInstructions().size());
            return true;
        } catch (Exception e) {
            String errorMsg = formatCompileError(e);
            failedScripts.put(primaryName, errorMsg);
            LOGGER.error("Failed to compile script '{}': {}", file.getFileName(), e.getMessage());
            ScriptConsole.compileError(primaryName, errorMsg);
            return false;
        }
    }

    private String formatCompileError(Throwable e) {
        if (e instanceof ScriptException se && se.getScriptLine() >= 0) {
            String detail = se.getMessage() != null
                    ? se.getMessage().replaceFirst("^Line \\d+: ", "")
                    : e.getClass().getSimpleName();
            return "Line " + se.getScriptLine() + ": " + detail;
        }
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getSimpleName();
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(scriptsDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create scripts directory: {}", e.getMessage());
        }
    }

    private void copyDefaultScripts() {
        try {
            int installed = 0;
            int updated = 0;
            for (String fileName : BUNDLED_DEFAULT_SCRIPTS) {
                boolean overwrite = ENGINE_LIBRARY_SCRIPTS.contains(fileName);
                int result = copyBundledScript(fileName, overwrite);
                if (result == 1) installed++;
                else if (result == 2) updated++;
            }
            if (copyExampleScriptIfMissing()) {
                installed++;
            }
            if (installed > 0 || updated > 0) {
                LOGGER.info("Default scripts: {} installed, {} updated in {}", installed, updated, scriptsDir);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to copy default scripts: {}", e.getMessage());
        }
    }

    /** @return 0 unchanged, 1 installed, 2 updated */
    private int copyBundledScript(String fileName, boolean overwrite) throws IOException {
        Path dest = scriptsDir.resolve(fileName);
        boolean existed = Files.exists(dest);
        if (existed && !overwrite) {
            return 0;
        }
        String resourcePath = DEFAULT_SCRIPTS_PREFIX + fileName;
        try (InputStream in = ScriptManager.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.warn("Bundled default script not found in mod jar: {}", resourcePath);
                return 0;
            }
            byte[] bundled = in.readAllBytes();
            if (existed && Arrays.equals(bundled, Files.readAllBytes(dest))) {
                return 0;
            }
            Files.createDirectories(dest.getParent());
            Files.write(dest, bundled);
            if (existed) {
                LOGGER.info("Updated engine script: {}", fileName);
                return 2;
            }
            LOGGER.info("Installed default script: {}", fileName);
            return 1;
        }
    }

    private boolean copyExampleScriptIfMissing() throws IOException {
        Path dest = scriptsDir.resolve("example.spr");
        if (Files.exists(dest)) {
            return false;
        }
        String exampleContent = """
                # Пример скрипта Spraute Engine
                # Запусти: startScript("example")  или  /spraute run <scene> с SCRIPT-нодой

                chat("§6[Spraute Engine] §fДвижок запущен!")
                chat("§7Это пример сюжетного скрипта.")
                chat("§aДобро пожаловать в мир историй!")
                """;
        Files.writeString(dest, exampleContent, StandardCharsets.UTF_8);
        LOGGER.info("Installed default script: example.spr");
        return true;
    }
}
