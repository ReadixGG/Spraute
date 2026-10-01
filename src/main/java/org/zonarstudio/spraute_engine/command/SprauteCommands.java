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
import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteStorage;
import org.zonarstudio.spraute_engine.script.CameraRouteScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptManager;
import org.zonarstudio.spraute_engine.script.ScriptWorldData;
import org.zonarstudio.spraute_engine.scene.SceneRepository;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;

import java.util.Collections;
import java.util.List;

/**
 * Registers all /spraute commands.
 * 
 * Commands:
 *   /spraute engine             — open in-game editor (OP 2+, player only)
 *   /spraute run <scene_name>   — load a scene, spawn its objects and start its enabled scripts
 *   /spraute stop [scene_name]  — unload scene(s): remove objects and stop scene scripts
 *   /spraute reload             — re-read .spr from the server workspace
 *   /spraute list               — list loaded scenes and scripts
 *   /spraute var clear global [имя] — сброс глобальных переменных скриптов
 *   /spraute var clear world [имя] — сброс переменных сейва (общие для всех измерений)
 *   /spraute var list global|world — список имён
 */
public class SprauteCommands {

    private static final SuggestionProvider<CommandSourceStack> SCENE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    SceneRepository.listPlayableSceneIds(org.zonarstudio.spraute_engine.resource.SprautePaths.gameDir()), builder
            );

    private static final SuggestionProvider<CommandSourceStack> LOADED_SCENE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(SceneRuntime.listLoaded(), builder);

    private static final SuggestionProvider<CommandSourceStack> ROUTE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    CameraRouteStorage.listRoutes(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spraute")
                        .requires(source -> source.hasPermission(2)) // OP level 2+

                        // /spraute engine — enable editor mode; N opens the UI (player only)
                        .then(Commands.literal("engine")
                                .executes(SprauteCommands::executeEngine))

                        // /spraute run <scene>
                        .then(Commands.literal("run")
                                .then(Commands.argument("scene", StringArgumentType.string())
                                        .suggests(SCENE_SUGGESTIONS)
                                        .executes(SprauteCommands::executeRun)))

                        // /spraute list
                        .then(Commands.literal("list")
                                .executes(SprauteCommands::executeList))

                        // /spraute path — показать путь к папке скриптов
                        .then(Commands.literal("path")
                                .executes(SprauteCommands::executePath))

                        .then(Commands.literal("reload")
                                .executes(SprauteCommands::executeReload))

                        // /spraute stop [scene] — unload scene (all objects + scene scripts)
                        .then(Commands.literal("stop")
                                .executes(SprauteCommands::executeStopAll)
                                .then(Commands.argument("scene", StringArgumentType.string())
                                        .suggests(LOADED_SCENE_SUGGESTIONS)
                                        .executes(SprauteCommands::executeStop)))

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
                        .then(Commands.literal("load_screen")
                                .then(Commands.literal("on").executes(SprauteCommands::executeLoadScreenOn))
                                .then(Commands.literal("off").executes(SprauteCommands::executeLoadScreenOff)))
                        .then(Commands.literal("route")
                                .then(Commands.literal("preview")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .suggests(ROUTE_SUGGESTIONS)
                                                .executes(SprauteCommands::executeRoutePreview)))
                                .then(Commands.literal("list")
                                        .executes(SprauteCommands::executeRouteList)))
        );
    }

    private static int executeEngine(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("§c[Spraute]§r Редактор доступен только игроку."));
            return 0;
        }
        org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new org.zonarstudio.spraute_engine.network.OpenEditorPacket());
        return 1;
    }

    private static int executeLoadScreenOn(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        ScriptWorldData data = ScriptWorldData.get(level);
        data.put("_sys_load_screen_off", false);
        //? if >=1.20.1 {
        context.getSource().sendSuccess(() -> Component.literal("§a[Spraute]§r Заставка при входе §eвключена§r для этого мира."), true);
        //?} else {
        /*context.getSource().sendSuccess(Component.literal("§a[Spraute]§r Заставка при входе §eвключена§r для этого мира."), true);
        *///?}
        return 1;
    }

    private static int executeLoadScreenOff(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        ScriptWorldData data = ScriptWorldData.get(level);
        data.put("_sys_load_screen_off", true);
        //? if >=1.20.1 {
        context.getSource().sendSuccess(() -> Component.literal("§a[Spraute]§r Заставка при входе §eвыключена§r для этого мира."), true);
        //?} else {
        /*context.getSource().sendSuccess(Component.literal("§a[Spraute]§r Заставка при входе §eвыключена§r для этого мира."), true);
        *///?}
        return 1;
    }

    private static int executeRoutePreview(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("§c[Spraute]§r Просмотр маршрута доступен только игроку."));
            return 0;
        }
        if (context.getSource().getServer() == null) return 0;
        String name = StringArgumentType.getString(context, "name");
        try {
            float duration = CameraRouteScriptUtil.playRoute(player, name);
            String msg = "§a[Spraute]§r Просмотр маршрута §e" + name + "§r (~" + String.format("%.1f", duration) + " сек)";
            //? if >=1.20.1 {
            context.getSource().sendSuccess(() -> Component.literal(msg), true);
            //?} else {
            /*context.getSource().sendSuccess(Component.literal(msg), true);
            *///?}
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c[Spraute]§r " + e.getMessage()));
            return 0;
        }
    }

    private static int executeRouteList(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getServer() == null) return 0;
        var routes = CameraRouteStorage.listRoutes();
        if (routes.isEmpty()) {
            //? if >=1.20.1 {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§7[Spraute]§r Маршруты не найдены в spraute_engine/routes/"), false);
            //?} else {
            /*context.getSource().sendSuccess(Component.literal(
                    "§7[Spraute]§r Маршруты не найдены в spraute_engine/routes/"), false);
            *///?}
            return 1;
        }
        String joined = String.join(", ", routes);
        //? if >=1.20.1 {
        context.getSource().sendSuccess(() -> Component.literal("§a[Spraute]§r Маршруты: §f" + joined), false);
        //?} else {
        /*context.getSource().sendSuccess(Component.literal("§a[Spraute]§r Маршруты: §f" + joined), false);
        *///?}
        return 1;
    }

    private static int executeRun(CommandContext<CommandSourceStack> context) {
        String sceneId = StringArgumentType.getString(context, "scene").trim();
        CommandSourceStack source = context.getSource();

        if (sceneId.isEmpty()) {
            source.sendFailure(Component.literal("§c[Spraute]§r Не указано имя сцены."));
            return 0;
        }

        if (!SceneRuntime.load(sceneId, source)) return 0;

        //? if >=1.20.1 {
        source.sendSuccess(() -> Component.literal(
                "§a[Spraute]§r Сцена §e" + sceneId + "§r запущена."), true);
        //?} else {
        /*source.sendSuccess(Component.literal(
                "§a[Spraute]§r Сцена §e" + sceneId + "§r запущена."), true);
        *///?}
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        var loadedScenes = SceneRuntime.listLoaded();
        if (loadedScenes.isEmpty()) {
            //? if >=1.20.1 {
            source.sendSuccess(() -> Component.literal("§7[Spraute]§r Нет активных сцен."), false);
            //?} else {
            /*source.sendSuccess(Component.literal("§7[Spraute]§r Нет активных сцен."), false);
            *///?}
        } else {
            //? if >=1.20.1 {
            source.sendSuccess(
                    () -> Component.literal("§6[Spraute]§r §fАктивные сцены (§e" + loadedScenes.size() + "§f):"),
                    false
            );
            //?} else {
            /*source.sendSuccess(
                    Component.literal("§6[Spraute]§r §fАктивные сцены (§e" + loadedScenes.size() + "§f):"),
                    false
            );
            *///?}
            for (String name : loadedScenes.stream().sorted().toList()) {
                //? if >=1.20.1 {
                source.sendSuccess(() -> Component.literal("  §7- §f" + name), false);
                //?} else {
                /*source.sendSuccess(Component.literal("  §7- §f" + name), false);
                *///?}
            }
        }

        ScriptManager manager = ScriptManager.getInstance();
        if (manager != null) {
            var names = manager.getScriptNames();
            //? if >=1.20.1 {
            source.sendSuccess(
                    () -> Component.literal("§6[Spraute]§r §fСкрипты на диске (§e" + names.size() + "§f)"),
                    false
            );
            //?} else {
            /*source.sendSuccess(
                    Component.literal("§6[Spraute]§r §fСкрипты на диске (§e" + names.size() + "§f)"),
                    false
            );
            *///?}
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
        var workspace = org.zonarstudio.spraute_engine.resource.SprautePaths.workspace().toAbsolutePath();
        var scenes = org.zonarstudio.spraute_engine.resource.SprautePaths.scenesDir().toAbsolutePath();
        //? if >=1.20.1 {
        source.sendSuccess(
                () -> Component.literal("§6[Spraute]§r §fWorkspace сервера: §e" + workspace
                        + "\n§6[Spraute]§r §fСкрипты: §e" + path
                        + "\n§6[Spraute]§r §fСцены: §e" + scenes),
                false
        );
        //?} else {
        /*source.sendSuccess(
                Component.literal("§6[Spraute]§r §fWorkspace сервера: §e" + workspace
                        + "\n§6[Spraute]§r §fСкрипты: §e" + path
                        + "\n§6[Spraute]§r §fСцены: §e" + scenes),
                false
        );
        *///?}
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ScriptManager manager = ScriptManager.getInstance();
        if (manager == null) {
            source.sendFailure(Component.literal("§c[Spraute]§r Script manager not initialized!"));
            return 0;
        }
        manager.refreshFromDisk();
        org.zonarstudio.spraute_engine.registry.CustomProjectileRegistry.invalidateAndReparse();
        org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.invalidateAndReparse();
        org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.reloadFromScripts();
        org.zonarstudio.spraute_engine.resource.AssetSyncServer.invalidate();
        //? if >=1.20.1 {
        source.sendSuccess(() -> Component.literal(
                "§a[Spraute]§r Скрипты перечитаны с диска сервера."), true);
        //?} else {
        /*source.sendSuccess(Component.literal(
                "§a[Spraute]§r Скрипты перечитаны с диска сервера."), true);
        *///?}
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> context) {
        String sceneId = StringArgumentType.getString(context, "scene").trim();
        CommandSourceStack source = context.getSource();

        if (sceneId.isEmpty()) {
            source.sendFailure(Component.literal("§c[Spraute]§r Не указано имя сцены."));
            return 0;
        }

        if (SceneRuntime.unload(sceneId, source)) {
            //? if >=1.20.1 {
            source.sendSuccess(() -> Component.literal(
                    "§a[Spraute]§r Сцена §e" + sceneId + "§r остановлена (объекты удалены, скрипты остановлены)."), true);
            //?} else {
            /*source.sendSuccess(Component.literal(
                    "§a[Spraute]§r Сцена §e" + sceneId + "§r остановлена (объекты удалены, скрипты остановлены)."), true);
            *///?}
            return 1;
        }
        source.sendFailure(Component.literal("§c[Spraute]§r Сцена §e" + sceneId + "§r не была запущена."));
        return 0;
    }

    private static int executeStopAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var loaded = SceneRuntime.listLoaded();
        if (loaded.isEmpty()) {
            source.sendFailure(Component.literal("§c[Spraute]§r Нет активных сцен."));
            return 0;
        }
        int n = 0;
        for (String sceneId : List.copyOf(loaded)) {
            if (SceneRuntime.unload(sceneId, source)) n++;
        }
        int stopped = n;
        //? if >=1.20.1 {
        source.sendSuccess(() -> Component.literal(
                "§a[Spraute]§r Остановлено сцен: §e" + stopped), true);
        //?} else {
        /*source.sendSuccess(Component.literal(
                "§a[Spraute]§r Остановлено сцен: §e" + stopped), true);
        *///?}
        return stopped;
    }

    private static int executeSoundsStop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, null));
            //? if >=1.20.1 {
            source.sendSuccess(() -> Component.literal("§a[Spraute]§r §fЗвуки остановлены."), false);
            //?} else {
            /*source.sendSuccess(Component.literal("§a[Spraute]§r §fЗвуки остановлены."), false);
            *///?}
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
            //? if >=1.20.1 {
            net.minecraft.sounds.SoundEvent event = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(rl);
            //?} else {
            /*net.minecraft.sounds.SoundEvent event = new net.minecraft.sounds.SoundEvent(rl);
            *///?}
            player.playNotifySound(event, net.minecraft.sounds.SoundSource.MASTER, 1.0f, 1.0f);
            //? if >=1.20.1 {
            source.sendSuccess(() -> Component.literal("§a[Spraute]§r §fИграет звук: §e" + soundId), false);
            //?} else {
            /*source.sendSuccess(Component.literal("§a[Spraute]§r §fИграет звук: §e" + soundId), false);
            *///?}
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
        //? if >=1.20.1 {
        source.sendSuccess(() -> Component.literal("§6[Spraute]§r §fГлобальные переменные очищены."), true);
        //?} else {
        /*source.sendSuccess(Component.literal("§6[Spraute]§r §fГлобальные переменные очищены."), true);
        *///?}
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
            //? if >=1.20.1 {
            source.sendSuccess(() -> Component.literal("§6[Spraute]§r §fГлобальная переменная §e" + name + "§f удалена."), true);
            //?} else {
            /*source.sendSuccess(Component.literal("§6[Spraute]§r §fГлобальная переменная §e" + name + "§f удалена."), true);
            *///?}
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
        //? if >=1.20.1 {
        source.sendSuccess(
                () -> Component.literal("§6[Spraute]§r §fПеременные сейва очищены (общие для всех измерений)."),
                true
        );
        //?} else {
        /*source.sendSuccess(
                Component.literal("§6[Spraute]§r §fПеременные мира очищены (измерение: §e" + level.dimension().location() + "§f)."),
                true
        );
        *///?}
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
            //? if >=1.20.1 {
            source.sendSuccess(
                    () -> Component.literal("§6[Spraute]§r §fПеременная сейва §e" + name + "§f удалена."),
                    true
            );
            //?} else {
            /*source.sendSuccess(
                    Component.literal("§6[Spraute]§r §fПеременная мира §e" + name + "§f удалена (§7" + level.dimension().location() + "§f)."),
                    true
            );
            *///?}
            return 1;
        }
        source.sendFailure(Component.literal("§c[Spraute]§r §fПеременной сейва §e" + name + "§f нет."));
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
            //? if >=1.20.1 {
            source.sendSuccess(() -> Component.literal("§7[Spraute]§r Глобальных переменных нет."), false);
            //?} else {
            /*source.sendSuccess(Component.literal("§7[Spraute]§r Глобальных переменных нет."), false);
            *///?}
        } else {
            //? if >=1.20.1 {
            source.sendSuccess(() -> Component.literal("§6[Spraute]§r §fГлобальные (§e" + keys.size() + "§f):"), false);
            //?} else {
            /*source.sendSuccess(Component.literal("§6[Spraute]§r §fГлобальные (§e" + keys.size() + "§f):"), false);
            *///?}
            for (String k : keys) {
                //? if >=1.20.1 {
                source.sendSuccess(() -> Component.literal("  §7- §f" + k), false);
                //?} else {
                /*source.sendSuccess(Component.literal("  §7- §f" + k), false);
                *///?}
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
            //? if >=1.20.1 {
            source.sendSuccess(
                    () -> Component.literal("§7[Spraute]§r Переменных сейва нет."),
                    false
            );
            //?} else {
            /*source.sendSuccess(
                    Component.literal("§7[Spraute]§r В этом измерении переменных нет (§7" + level.dimension().location() + "§r)."),
                    false
            );
            *///?}
        } else {
            //? if >=1.20.1 {
            source.sendSuccess(
                    () -> Component.literal("§6[Spraute]§r §fПеременные сейва (§e" + keys.size() + "§f):"),
                    false
            );
            //?} else {
            /*source.sendSuccess(
                    Component.literal("§6[Spraute]§r §fМир §7" + level.dimension().location() + "§f (§e" + keys.size() + "§f):"),
                    false
            );
            *///?}
            for (String k : keys) {
                //? if >=1.20.1 {
                source.sendSuccess(() -> Component.literal("  §7- §f" + k), false);
                //?} else {
                /*source.sendSuccess(Component.literal("  §7- §f" + k), false);
                *///?}
            }
        }
        return 1;
    }
}
