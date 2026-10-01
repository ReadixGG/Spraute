package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.client.cameraroute.CameraRoutePlayback;
import org.zonarstudio.spraute_engine.client.editor.scene.ScenePreviewManager;

/** Immediate client-side cleanup while the server reset packet is in flight. */
public final class SprauteClientReset {

    private SprauteClientReset() {}

    public static void applyLocal() {
        Minecraft mc = Minecraft.getInstance();
        BillboardClientState.suppressRendering();
        SprauteScriptScreen.closeIfActive();
        SprauteScriptScreen.closeOverlayIfActive();
        org.zonarstudio.spraute_engine.client.ui.UiSvgTextures.clear();
        CameraHandler.resetCamera();
        PlayerControlHandler.clear();
        ClientPlayerExtrasHolder.clear();
        CameraEffectsClient.clear();
        HudHideClient.clear();
        CameraRoutePlayback.stop();
        ScenePreviewManager.clearInstances();
        org.zonarstudio.spraute_engine.entity.client.SprauteNpcRenderer.clearInstances();
        org.zonarstudio.spraute_engine.entity.client.SprauteModelRenderer.clearInstances();
        org.zonarstudio.spraute_engine.entity.client.GeoRenderStats.reset();
        org.zonarstudio.spraute_engine.entity.client.SpGeoGpuRenderer.clear();
        LoadScreenOverlay.cancel();
        if (mc.screen instanceof org.zonarstudio.spraute_engine.client.SprauteContainerScreen) {
            mc.setScreen(null);
        }
    }
}
