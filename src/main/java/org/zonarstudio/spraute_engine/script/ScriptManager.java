package org.zonarstudio.spraute_engine.script;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;

import java.io.IOException;
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
        int count = 0;

        try {
            if (!Files.exists(scriptsDir)) {
                LOGGER.warn("Scripts directory not found: {}", scriptsDir);
                return;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(scriptsDir, "*" + SCRIPT_EXTENSION)) {
                for (Path file : stream) {
                    String name = getScriptName(file);
                    try {
                        String source = Files.readString(file, StandardCharsets.UTF_8);
                        CompiledScript compiled = compileSource(name, source);
                        compiledScripts.put(name, compiled);
                        count++;
                        LOGGER.info("Compiled script: {} ({} instructions)", name, compiled.getInstructions().size());
                    } catch (Exception e) {
                        String errorMsg = formatCompileError(e);
                        failedScripts.put(name, errorMsg);
                        LOGGER.error("Failed to compile script '{}': {}", file.getFileName(), e.getMessage());
                    }
                }
            }

            LOGGER.info("Loaded {} script(s) from {} ({} with errors)", count, scriptsDir, failedScripts.size());
        } catch (IOException e) {
            LOGGER.error("Failed to scan scripts directory: {}", e.getMessage());
        }
    }

    /**
     * Reload all scripts (clear cache and re-load).
     */
    public void reload() {
        LOGGER.info("Reloading scripts...");
        loadAll();
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
        String compileError = failedScripts.get(name);
        if (compileError != null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("§c[Spraute] §fОшибка компиляции '" + name + "': §e" + compileError));
            return false;
        }

        CompiledScript script = compiledScripts.get(name);
        if (script == null) {
            LOGGER.warn("Script not found: '{}'", name);
            return false;
        }

        try {
            executor.start(script, source, initialVariables);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error executing script '{}': {}", name, e.getMessage());
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

    public void onDeath(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.entity.Entity killer) {
        executor.onDeath(entity, killer);
    }

    /** Клик по кнопке UI или закрытие экрана (ESC). */
    public void onUiAction(net.minecraft.server.level.ServerPlayer player, String widgetId, boolean closed) {
        executor.onUiAction(player, widgetId, closed);
    }

    public void onClickBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, boolean isLeft) {
        executor.onClickBlock(player, pos, block, isLeft);
    }

    public void onBreakBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
        executor.onBreakBlock(player, pos, block);
    }

    public void onPlaceBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
        executor.onPlaceBlock(player, pos, block);
    }

    public void onChat(net.minecraft.server.level.ServerPlayer player, String message) {
        executor.onChat(player, message);
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

    public Path getScriptsDir() {
        return scriptsDir;
    }

    public CompiledScript getCompiledScript(String name) {
        return compiledScripts.get(name);
    }

    // --- Internal methods ---

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

    private String formatCompileError(Throwable e) {
        if (e instanceof ScriptException se && se.getScriptLine() >= 0) {
            return "строка " + se.getScriptLine() + ": " + se.getMessage().replaceFirst("^Line \\d+: ", "");
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
            if (Files.list(scriptsDir).findAny().isEmpty()) {
                // Copy the example script
                String exampleContent = """
                        # Пример скрипта Spraute Engine
                        # Запусти: /spraute run example
                        
                        chat("§6[Spraute Engine] §fДвижок запущен!")
                        chat("§7Это пример сюжетного скрипта.")
                        chat("§aДобро пожаловать в мир историй!")
                        """;
                Files.writeString(
                        scriptsDir.resolve("example.spr"),
                        exampleContent,
                        StandardCharsets.UTF_8
                );
                
                String chatContent = """
                        # Библиотека кастомного чата
                        # Импортируйте через: include "spraute_chat"
                        
                        world chat_history = list()
                        
                        fun calc_msg_height(text) {
                            var len = str_len(text)
                            var lines = len // 40 + 1
                            var h = lines * 10 + 20
                            if (h < 40) {
                                h = 40
                            }
                            return h
                        }
                        
                        fun npc_chat(player, npc_id, text, color) {
                            var headTexture = npc_id.head
                            if (headTexture == null) {
                                headTexture = "minecraft:textures/heads/head.png"
                            }
                            var npcName = npc_id.name
                            if (npcName == null) {
                                npcName = "Неизвестный"
                            }
                            
                            # Добавляем в историю (макс 50 сообщений)
                            chat_history.add(dict("name", npcName, "text", text, "color", color, "head", headTexture))
                            if (chat_history.size() > 50) {
                                chat_history.remove(0)
                            }
                            
                            var msg_h = calc_msg_height(text)
                            var target_y = -msg_h - 40
                            
                            create ui chat_hud {
                                w = 240
                                h = msg_h
                                x = "50%"
                                y = "100%"
                                
                                clip {
                                    id = "chat_container"
                                    x = -120
                                    y = target_y
                                    w = 240
                                    h = msg_h
                                    alpha = 0.0  # Начальная прозрачность
                                    
                                    rect {
                                        x = 0
                                        y = 0
                                        w = 240
                                        h = msg_h
                                        color = "#AA000000"
                                    }
                                    
                                    image {
                                        x = 5
                                        y = (msg_h - 30) // 2
                                        w = 30
                                        h = 30
                                        texture = headTexture
                                    }
                                    
                                    text {
                                        x = 40
                                        y = 5
                                        text = npcName
                                        color = color
                                    }
                                    
                                    text {
                                        x = 40
                                        y = 18
                                        text = text
                                        color = "#FFFFFF"
                                        wrap = 190
                                        max_lines = 10
                                    }
                                }
                            }
                            
                            overlay_open(player, chat_hud)
                            play_sound(player, "minecraft:entity.experience_orb.pickup", 0.5, 1.0)
                            
                            # Анимация плавного появления
                            ui_animate(player, "chat_container", "alpha", 1.0, 0.3)
                            
                            async {
                                await time(5.0)
                                ui_animate(player, "chat_container", "alpha", 0.0, 0.3)
                                await time(0.3)
                                overlay_close(player)
                            }
                        }
                        
                        on keybind("key.keyboard.b") {
                            var total_h = 0
                            for (i in range(chat_history.size())) {
                                val msg = chat_history.get(i)
                                total_h = total_h + calc_msg_height(msg.text) + 5
                            }
                            
                            create ui history_ui {
                                w = 300
                                h = 200
                                bg = "#DD000000"
                                can_close = true
                                
                                text {
                                    x = 10
                                    y = 10
                                    text = "История сообщений"
                                    color = "#FFAA00"
                                }
                                
                                scroll {
                                    x = 10
                                    y = 25
                                    w = 280
                                    h = 165
                                    content_h = total_h
                                    
                                    var current_y = 0
                                    for (i in range(chat_history.size())) {
                                        val msg = chat_history.get(i)
                                        var m_h = calc_msg_height(msg.text)
                                        
                                        rect {
                                            x = 0
                                            y = current_y
                                            w = 270
                                            h = m_h
                                            color = "#55000000"
                                        }
                                        image {
                                            x = 5
                                            y = current_y + (m_h - 30) // 2
                                            w = 30
                                            h = 30
                                            texture = msg.head
                                        }
                                        text {
                                            x = 40
                                            y = current_y + 5
                                            text = msg.name
                                            color = msg.color
                                        }
                                        text {
                                            x = 40
                                            y = current_y + 18
                                            text = msg.text
                                            color = "#FFFFFF"
                                            wrap = 220
                                        }
                                        current_y = current_y + m_h + 5
                                    }
                                }
                            }
                            
                            ui_open(player, history_ui)
                        }
                        """;
                Files.writeString(
                        scriptsDir.resolve("spraute_chat.spr"),
                        chatContent,
                        StandardCharsets.UTF_8
                );
                
                LOGGER.info("Created default example scripts");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to copy default scripts: {}", e.getMessage());
        }
    }
}
