package org.sprautestudio.spraute_engine.platform.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.platform.Hooks;
import org.sprautestudio.spraute_engine.platform.Platform;

/** The mod as Forge sees it: installs the port, starts the engine, then connects Forge's events to it. */
@Mod(SprauteEngine.MODID)
public final class ForgeMod {
    public ForgeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ForgePlatform platform = new ForgePlatform();
        Platform.install(platform);
        if (FMLEnvironment.dist == Dist.CLIENT) ForgeClientPlatform.install();
        SprauteEngine.init();
        platform.registerOn(modBus);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() -> Hooks.COMMON_SETUP.fire(new Hooks.CommonSetup())));
        ForgeEvents.install();
        if (FMLEnvironment.dist == Dist.CLIENT) ForgeClientEvents.install(modBus);
    }
}
