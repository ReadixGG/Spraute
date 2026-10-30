package org.sprautestudio.st_addon_spraute;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.sprautestudio.st_addon_spraute.engine.EngineServer;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime;

@Mod(St_addon_spraute.MODID)
public class St_addon_spraute {
    public static final String MODID = "st_addon_spraute";
    public static final Logger LOGGER = LogUtils.getLogger();

    public St_addon_spraute() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        org.sprautestudio.st_addon_spraute.runtime.orb.Orbs.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::onLevelSave);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
    }

    private void onPlayerLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            EngineServer.sync(sp);
            org.sprautestudio.st_addon_spraute.net.SprauteNetwork.sendTo(sp, new org.sprautestudio.st_addon_spraute.net.Packets.CoinsSync(org.sprautestudio.st_addon_spraute.core.system.Coins.get()));
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(SprauteNetwork::register);
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        SprauteRuntime.installErrorHook();
        org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.scan();
        EngineServer.load(event.getServer());
        SprauteRuntime.get().loadWorld();
        SprauteRuntime.get().openEnvironment(event.getServer());
        SprauteRuntime.env().ifPresent(SprauteRuntime.get()::registerInEnv);
    }

    private void onServerStarted(ServerStartedEvent event) {
        org.sprautestudio.st_addon_spraute.core.system.Coins.load(event.getServer());
    }

    private void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        SprauteRuntime.env().ifPresent(org.sprautestudio.st_addon_spraute.core.script.ScriptEnvironment::save);
    }

    /** The world is saved: so is what runs in it. */
    private void onLevelSave(net.minecraftforge.event.level.LevelEvent.Save event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel l && l.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            SprauteRuntime.env().ifPresent(org.sprautestudio.st_addon_spraute.core.script.ScriptEnvironment::save);
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        SprauteRuntime.get().clear();
        SprauteRuntime.get().closeEnvironment();
        org.sprautestudio.st_addon_spraute.core.script.Behaviours.unload();
        EngineServer.unload();
        org.sprautestudio.st_addon_spraute.shop.Shops.unload();
        org.sprautestudio.st_addon_spraute.quest.Quests.unload();
        org.sprautestudio.st_addon_spraute.runtime.Globals.unload();
        org.sprautestudio.st_addon_spraute.path.Walkers.unload();
        org.sprautestudio.st_addon_spraute.runtime.JsEngine.unload();
        org.sprautestudio.st_addon_spraute.zone.Zones.unload();
        org.sprautestudio.st_addon_spraute.fight.Fights.unload();
        org.sprautestudio.st_addon_spraute.fx.Casts.unload();
        org.sprautestudio.st_addon_spraute.runtime.Mechanics.unload();
        org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.clearServer();
        org.sprautestudio.st_addon_spraute.runtime.GameEvents.reset();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        org.sprautestudio.st_addon_spraute.core.script.ScriptCommand.register(event.getDispatcher());
        // the world's operator decides whether stories' JavaScript may use Java (files, other mods' classes…)
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("sprautejs").requires(s -> s.hasPermission(4))
                .then(net.minecraft.commands.Commands.literal("unsafe")
                        .then(net.minecraft.commands.Commands.literal("on").executes(ctx -> {
                            org.sprautestudio.st_addon_spraute.runtime.JsEngine.setUnsafe(true);
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                    "JavaScript сюжетов этого мира может использовать классы Java. Включайте только для сюжетов, которым доверяете."), true);
                            return 1;
                        }))
                        .then(net.minecraft.commands.Commands.literal("off").executes(ctx -> {
                            org.sprautestudio.st_addon_spraute.runtime.JsEngine.setUnsafe(false);
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("JavaScript сюжетов снова в песочнице (без Java)."), true);
                            return 1;
                        }))
                        .executes(ctx -> {
                            boolean on = org.sprautestudio.st_addon_spraute.runtime.JsEngine.unsafe();
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(on
                                    ? "Java в JavaScript сюжетов: разрешена (/sprautejs unsafe off — выключить)"
                                    : "Java в JavaScript сюжетов: запрещена (/sprautejs unsafe on — разрешить)"), false);
                            return 1;
                        })));
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) SprauteRuntime.env().ifPresent(org.sprautestudio.st_addon_spraute.core.script.ScriptEnvironment::tick);
    }
}
