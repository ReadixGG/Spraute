package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.client.cameraroute.CameraRoutePlayback;

/** Immediate client-side cleanup while the server reset packet is in flight. */
public final class SprauteClientReset {

    private SprauteClientReset() {}

    public static void applyLocal() {
        Minecraft mc = Minecraft.getInstance();
        SprauteScriptScreen.closeIfActive();
        SprauteScriptScreen.closeOverlayIfActive();
        CameraHandler.resetCamera();
        CameraRoutePlayback.stop();
        if (LoadScreenOverlay.active) {
            LoadScreenOverlay.triggerFadeOut();
        }
        if (mc.screen instanceof org.zonarstudio.spraute_engine.client.SprauteContainerScreen) {
            mc.setScreen(null);
        }
    }
}
