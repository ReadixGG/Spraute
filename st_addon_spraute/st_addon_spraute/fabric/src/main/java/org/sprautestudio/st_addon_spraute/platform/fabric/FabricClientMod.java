package org.sprautestudio.st_addon_spraute.platform.fabric;

import net.fabricmc.api.ClientModInitializer;

public final class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientEvents.install();
    }
}
