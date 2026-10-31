package org.sprautestudio.spraute_engine.platform.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.platform.Hooks;
import org.sprautestudio.spraute_engine.platform.Platform;

/** The mod as Fabric sees it: installs the port, starts the engine, then connects Fabric's events to it. */
public final class FabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.install(new FabricPlatform());
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) FabricClientPlatform.install();
        SprauteEngine.init();
        FabricEvents.install();
        Hooks.COMMON_SETUP.fire(new Hooks.CommonSetup());
    }
}
