package org.sprautestudio.spraute_engine;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.sprautestudio.spraute_engine.engine.EngineServer;
import org.sprautestudio.spraute_engine.net.SprauteNetwork;
import org.sprautestudio.spraute_engine.platform.Hooks;
import org.sprautestudio.spraute_engine.platform.Platform;
import org.sprautestudio.spraute_engine.platform.Priority;
import org.sprautestudio.spraute_engine.platform.TickPhase;
import org.sprautestudio.spraute_engine.runtime.SprauteRuntime;

/** The engine: everything a loader starts by calling {@link #init()} once its {@code Platform} is installed. */
public final class SprauteEngine {
    public static final String MODID = "spraute_engine";
    public static final Logger LOGGER = LogUtils.getLogger();

    private SprauteEngine() {
    }

    public static void init() {
        org.sprautestudio.spraute_engine.runtime.ItemSpec.install();
        org.sprautestudio.spraute_engine.core.anim.AnimationSystem.setLibrary(org.sprautestudio.spraute_engine.runtime.Anims::resolve);
        org.sprautestudio.spraute_engine.runtime.orb.Orbs.init();
        Hooks.COMMON_SETUP.add(e -> SprauteNetwork.register());
        Hooks.SERVER_ABOUT_TO_START.add(SprauteEngine::onServerAboutToStart);
        Hooks.SERVER_STOPPING.add(SprauteEngine::onServerStopping);
        Hooks.LEVEL_SAVE.add(SprauteEngine::onLevelSave);
        Hooks.SERVER_STOPPED.add(SprauteEngine::onServerStopped);
        Hooks.REGISTER_COMMANDS.add(Priority.LOWEST, SprauteEngine::onRegisterCommands);
        Hooks.SERVER_TICK.add(SprauteEngine::onServerTick);
        Hooks.PLAYER_LOGGED_IN.add(SprauteEngine::onPlayerLogin);
        Hooks.PLAYER_LOGGED_OUT.add(SprauteEngine::onPlayerLogout);
        Modules.common();
        if (Platform.get().isClient()) ClientModules.client();
    }

    private static void onPlayerLogin(Hooks.PlayerLoggedIn event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            EngineServer.sync(sp);
            org.sprautestudio.spraute_engine.core.system.Coins.sync(sp, -2);
            org.sprautestudio.spraute_engine.core.system.PlayerTexture.sendTo(sp);
            SprauteRuntime.env().ifPresent(env -> env.sendTo(sp));
        }
    }

    private static void onPlayerLogout(Hooks.PlayerLoggedOut event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            org.sprautestudio.spraute_engine.core.system.CameraState.left(sp);
        }
    }

    private static void onServerAboutToStart(Hooks.ServerAboutToStart event) {
        SprauteRuntime.installErrorHook();
        org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.scan();
        EngineServer.load(event.getServer());
        SprauteRuntime.get().loadWorld();
        SprauteRuntime.get().openEnvironment(event.getServer());
        SprauteRuntime.env().ifPresent(SprauteRuntime.get()::registerInEnv);
    }

    private static void onServerStopping(Hooks.ServerStopping event) {
        SprauteRuntime.env().ifPresent(org.sprautestudio.spraute_engine.core.script.ScriptEnvironment::save);
    }

    /** The world is saved: so is what runs in it. */
    private static void onLevelSave(Hooks.LevelSave event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel l && l.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            SprauteRuntime.env().ifPresent(org.sprautestudio.spraute_engine.core.script.ScriptEnvironment::save);
        }
    }

    private static void onServerStopped(Hooks.ServerStopped event) {
        SprauteRuntime.get().clear();
        SprauteRuntime.get().closeEnvironment();
        org.sprautestudio.spraute_engine.core.script.Behaviours.unload();
        org.sprautestudio.spraute_engine.core.system.WorldClock.unload();
        org.sprautestudio.spraute_engine.core.system.CameraState.unload();
        org.sprautestudio.spraute_engine.content.ProjectContent.unload();
        EngineServer.unload();
        org.sprautestudio.spraute_engine.shop.Shops.unload();
        org.sprautestudio.spraute_engine.quest.Quests.unload();
        org.sprautestudio.spraute_engine.runtime.Globals.unload();
        org.sprautestudio.spraute_engine.path.Walkers.unload();
        org.sprautestudio.spraute_engine.runtime.JsEngine.unload();
        org.sprautestudio.spraute_engine.zone.Zones.unload();
        org.sprautestudio.spraute_engine.fight.Fights.unload();
        org.sprautestudio.spraute_engine.fx.Casts.unload();
        org.sprautestudio.spraute_engine.runtime.Mechanics.unload();
        org.sprautestudio.spraute_engine.hitbox.Hitboxes.clearServer();
        org.sprautestudio.spraute_engine.runtime.GameEvents.reset();
    }

    private static void onRegisterCommands(Hooks.RegisterCommands event) {
        org.sprautestudio.spraute_engine.core.script.ScriptCommand.register(event.getDispatcher());
        // the world's operator decides whether stories' JavaScript may use Java (files, other mods' classes…)
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("sprautejs").requires(s -> s.hasPermission(4))
                .then(net.minecraft.commands.Commands.literal("unsafe")
                        .then(net.minecraft.commands.Commands.literal("on").executes(ctx -> {
                            org.sprautestudio.spraute_engine.runtime.JsEngine.setUnsafe(true);
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                    "JavaScript сюжетов этого мира может использовать классы Java. Включайте только для сюжетов, которым доверяете."), true);
                            return 1;
                        }))
                        .then(net.minecraft.commands.Commands.literal("off").executes(ctx -> {
                            org.sprautestudio.spraute_engine.runtime.JsEngine.setUnsafe(false);
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("JavaScript сюжетов снова в песочнице (без Java)."), true);
                            return 1;
                        }))
                        .executes(ctx -> {
                            boolean on = org.sprautestudio.spraute_engine.runtime.JsEngine.unsafe();
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(on
                                    ? "Java в JavaScript сюжетов: разрешена (/sprautejs unsafe off — выключить)"
                                    : "Java в JavaScript сюжетов: запрещена (/sprautejs unsafe on — разрешить)"), false);
                            return 1;
                        })));
    }

    private static boolean tickEvents;

    private static void onServerTick(Hooks.ServerTick event) {
        if (event.phase != TickPhase.END) return;
        SprauteRuntime.env().ifPresent(org.sprautestudio.spraute_engine.core.script.ScriptEnvironment::tick);
        org.sprautestudio.spraute_engine.content.ProjectContent.tick(event.getServer());
        // only when some story listens: a tick is a lot of events
        if (event.getServer().getTickCount() % 100 == 0) {
            tickEvents = false;
            for (var d : SprauteRuntime.get().all().values()) if (d.source.contains("servertick") || d.source.contains("playertick")) tickEvents = true;
        }
        if (tickEvents) {
            org.sprautestudio.spraute_engine.runtime.GameEvents.fire("servertick", null, new java.util.HashMap<>());
            for (var p : event.getServer().getPlayerList().getPlayers()) org.sprautestudio.spraute_engine.runtime.GameEvents.fire("playertick", p, new java.util.HashMap<>());
        }
    }
}
