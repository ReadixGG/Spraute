package org.zonarstudio.spraute_engine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.zonarstudio.spraute_engine.script.ScriptManager;
import org.zonarstudio.spraute_engine.script.ScriptWorldData;

import java.util.Collections;

/**
 * Registers all /spraute commands.
 * 
 * Commands:
 *   /spraute run <script_name>  — run a compiled script
 *   /spraute reload             — reload all scripts from disk
 *   /spraute list               — list all loaded scripts
 *   /spraute var clear global [имя] — сброс глобальных переменных скриптов
 *   /spraute var clear world [имя] — сброс переменных мира (текущее измерение)
 *   /spraute var list global|world — список имён
 */
public class SprauteCommands {

    private static final SuggestionProvider<CommandSourceStack> SCRIPT_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    ScriptManager.getInstance().getScriptNames(), builder
            );

    private static final SuggestionProvider<CommandSourceStack> RUNNING_SCRIPT_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    ScriptManager.getInstance() != null ? ScriptManager.getInstance().getRunningScriptNames() : Collections.emptySet(), builder
            );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spraute")
                        .requires(source -> source.hasPermission(2)) // OP level 2+

                        // /spraute run <script>
                        .then(Commands.literal("run")
                                .then(Commands.argument("script", StringArgumentType.word())
                                        .suggests(SCRIPT_SUGGESTIONS)
                                        .executes(SprauteCommands::executeRun)))

                        // /spraute reload
                        .then(Commands.literal("reload")
                                .executes(SprauteCommands::executeReload))

                        // /spraute list
                        .then(Commands.literal("list")
                                .executes(SprauteCommands::executeList))

                        // /spraute path — показать путь к папке скриптов
                        .then(Commands.literal("path")
                                .executes(SprauteCommands::executePath))

                        // /spraute stop <script> — остановить указанный скрипт
                        .then(Commands.literal("stop")
                                .then(Commands.argument("script", StringArgumentType.word())
                                        .suggests(RUNNING_SCRIPT_SUGGESTIONS)
                                        .executes(SprauteCommands::executeStop)))

                        // /spraute debug — включить/выключить отладку
                        .then(Commands.literal("debug")
                                .executes(SprauteCommands::executeDebug))

                        .then(Commands.literal("sounds")
                                .then(Commands.literal("stop")
                                        .executes(SprauteCommands::executeSoundsStop))
                                .then(Commands.literal("play")
                                        .then(Commands.argument("sound", StringArgumentType.word())
                                                .executes(SprauteCommands::executeSoundsPlay))))

                        .then(Commands.literal("var")
                                .then(Commands.literal("clear")
                                        .then(Commands.literal("global")
                                                .executes(SprauteCommands::executeVarClearGlobalAll)
                                                .then(Commands.argument("name", StringArgumentType.word())
                                                        .executes(SprauteCommands::executeVarClearGlobalOne)))
                                        .then(Commands.literal("world")
                                                .executes(SprauteCommands::executeVarClearWorldAll)
                                                .then(Commands.argument("name", StringArgumentType.word())
                                                        .executes(SprauteCommands::executeVarClearWorldOne))))
                                .then(Commands.literal("list")
                                        .then(Commands.literal("global")
                                                .executes(SprauteCommands::executeVarListGlobal))
                                        .then(Commands.literal("world")
                                                .executes(SprauteCommands::executeVarListWorld))))
        );
    }

    private static int executeRun(CommandContext<CommandSourceStack> context) {
        String scriptName = StringArgumentType.getString(context, "script");
        CommandSourceStack source = context.getSource();

        ScriptManager manager = ScriptManager.getInstance();
        if (manager == null) {
            source.sendFailure(Component.literal("§a[Spraute]§r Script manager not initialized!"));
            return 0;
        }

        if (manager.run(scriptName, source)) {
            return 1;
        } else {
            // Не показывать "Script not found", если уже показали ошибку компиляции
            if (!manager.hasCompileError(scriptName)) {
                source.sendFailure(Component.literal("§a[Spraute]§r Script not found: '" + scriptName + "'"));
            }
            return 0;
        }
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ScriptManager manager = ScriptManager.getInstance();
        if (manager == null) {
            source.sendFailure(Component.literal("§a[Spraute]§r Script manager not initialized!"));
            return 0;
        }

        manager.reload();
        int count = manager.getScriptNames().size();
        source.sendSuccess(
                Component.literal("§a[Spraute]§r §fReloaded §e" + count + "§f script(s)"),
                true
        );
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ScriptManager manager = ScriptManager.getInstance();
        if (manager == null) {
            source.sendFailure(Component.literal("§a[Spraute]§r Script manager not initialized!"));
            return 0;
        }

        var names = manager.getScriptNames();
        if (names.isEmpty()) {
            source.sendSuccess(Component.literal("§7[Spraute]§r No scripts loaded."), false);
        } else {
            source.sendSuccess(
                    Component.literal("§6[Spraute]§r §fСкрипты (§e" + names.size() + "§f):"),
                    false
            );
            for (String name : names.stream().sorted().toList()) {
                String suffix = manager.hasCompileError(name) ? " §c(ошибка компиляции)" : "";
                source.sendSuccess(Component.literal("  §7- §f" + name + suffix), false);
            }
        }
        return 1;
    }

    private static int executePath(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ScriptManager manager = ScriptManager.getInstance();
        if (manager == null) {
            source.sendFailure(Component.literal("§a[Spraute]§r Script manager not initialized!"));
            return 0;
        }

        var path = manager.getScriptsDir().toAbsolutePath();
        source.sendSuccess(
                Component.literal("§6[Spraute]§r §fСкрипты загружаются из: §e" + path),
                false
        );
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> context) {
        String scriptName = StringArgumentType.getString(context, "script");
        CommandSourceStack source = context.getSource();

        ScriptManager manager = ScriptManager.getInstance();
        if (manager == null) {
            source.sendFailure(Component.literal("§a[Spraute]§r Script manager not initialized!"));
            return 0;
        }

        if (manager.stopScript(scriptName)) {
            source.sendSuccess(Component.literal("§a[Spraute]§r §fСкрипт §e" + scriptName + "§f остановлен."), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("§a[Spraute]§r §fСкрипт §e" + scriptName + "§f не запущен."));
            return 0;
        }
    }

    private static int executeDebug(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
            ScriptManager manager = ScriptManager.getInstance();
            if (manager != null) {
                manager.toggleDebug(player);
                return 1;
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("§cThis command can only be run by a player."));
        }
        return 0;
    }

    private static int executeSoundsStop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, null));
            source.sendSuccess(Component.literal("§a[Spraute]§r §fЗвуки остановлены."), false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("§cThis command can only be run by a player."));
        }
        return 0;
    }

    private static int executeSoundsPlay(CommandContext<CommandSourceStack> context) {
        String soundId = StringArgumentType.getString(context, "sound");
        CommandSourceStack source = context.getSource();
        try {
            net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
            net.minecraft.resources.ResourceLocation rl = soundId.contains(":") ? new net.minecraft.resources.ResourceLocation(soundId) : new net.minecraft.resources.ResourceLocation("minecraft", soundId);
            net.minecraft.sounds.SoundEvent event = new net.minecraft.sounds.SoundEvent(rl);
            player.playNotifySound(event, net.minecraft.sounds.SoundSource.MASTER, 1.0f, 1.0f);
            source.sendSuccess(Component.literal("§a[Spraute]§r §fИграет звук: §e" + soundId), false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("§cThis command can only be run by a player."));
        }
        return 0;
    }

    private static ServerLevel requireServerLevel(CommandSourceStack source) {
        return source.getLevel();
    }

    private static int executeVarClearGlobalAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ScriptManager manager = ScriptManager.getInstance();
        if (manager == null) {
            source.sendFailure(Component.literal("§a[Spraute]§r Script manager not initialized!"));
            return 0;
        }
        manager.clearGlobalVariables();
        source.sendSuccess(Component.literal("§6[Spraute]§r §fГлобальные переменные очищены."), true);
        return 1;
    }

    private static int executeVarClearGlobalOne(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        ScriptManager manager = ScriptManager.getInstance();
        if (manager == null) {
            source.sendFailure(Component.literal("§a[Spraute]§r Script manager not initialized!"));
            return 0;
        }
        if (manager.removeGlobalVariable(name)) {
            source.sendSuccess(Component.literal("§6[Spraute]§r §fГлобальная переменная §e" + name + "§f удалена."), true);
            return 1;
        }
        source.sendFailure(Component.literal("§c[Spraute]§r §fГлобальной переменной §e" + name + "§f нет."));
        return 0;
    }

    private static int executeVarClearWorldAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = requireServerLevel(source);
        if (level == null) {
            source.sendFailure(Component.literal("§c[Spraute]§r Команда доступна только на сервере с миром."));
            return 0;
        }
        ScriptWorldData.get(level).clearAll();
        source.sendSuccess(
                Component.literal("§6[Spraute]§r §fПеременные мира очищены (измерение: §e" + level.dimension().location() + "§f)."),
                true
        );
        return 1;
    }

    private static int executeVarClearWorldOne(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        ServerLevel level = requireServerLevel(source);
        if (level == null) {
            source.sendFailure(Component.literal("§c[Spraute]§r Команда доступна только на сервере с миром."));
            return 0;
        }
        ScriptWorldData data = ScriptWorldData.get(level);
        if (data.has(name)) {
            data.remove(name);
            source.sendSuccess(
                    Component.literal("§6[Spraute]§r §fПеременная мира §e" + name + "§f удалена (§7" + level.dimension().location() + "§f)."),
                    true
            );
            return 1;
        }
        source.sendFailure(Component.literal("§c[Spraute]§r §fПеременной §e" + name + "§f в этом измерении нет."));
        return 0;
    }

    private static int executeVarListGlobal(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ScriptManager manager = ScriptManager.getInstance();
        if (manager == null) {
            source.sendFailure(Component.literal("§a[Spraute]§r Script manager not initialized!"));
            return 0;
        }
        var keys = manager.getGlobalVariableNames().stream().sorted().toList();
        if (keys.isEmpty()) {
            source.sendSuccess(Component.literal("§7[Spraute]§r Глобальных переменных нет."), false);
        } else {
            source.sendSuccess(Component.literal("§6[Spraute]§r §fГлобальные (§e" + keys.size() + "§f):"), false);
            for (String k : keys) {
                source.sendSuccess(Component.literal("  §7- §f" + k), false);
            }
        }
        return 1;
    }

    private static int executeVarListWorld(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = requireServerLevel(source);
        if (level == null) {
            source.sendFailure(Component.literal("§c[Spraute]§r Команда доступна только на сервере с миром."));
            return 0;
        }
        var keys = ScriptWorldData.get(level).allKeys().stream().sorted().toList();
        if (keys.isEmpty()) {
            source.sendSuccess(
                    Component.literal("§7[Spraute]§r В этом измерении переменных нет (§7" + level.dimension().location() + "§r)."),
                    false
            );
        } else {
            source.sendSuccess(
                    Component.literal("§6[Spraute]§r §fМир §7" + level.dimension().location() + "§f (§e" + keys.size() + "§f):"),
                    false
            );
            for (String k : keys) {
                source.sendSuccess(Component.literal("  §7- §f" + k), false);
            }
        }
        return 1;
    }
}
