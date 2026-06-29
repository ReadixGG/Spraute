package org.zonarstudio.spraute_engine.client.cameraroute;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint;
import org.zonarstudio.spraute_engine.Spraute_engine;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class CameraRouteOverlay {
    private CameraRouteOverlay() {}

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!CameraRouteRenderer.isHoldingCamera(mc)) return;

        int x = 8;
        int y = 8;
        int line = 0;
        int color = 0xFFFFFF;
        int accent = 0x55CCFF;

        drawShadowText(event, Component.translatable("spraute_engine.camera_route.hud.title"), x, y + line++ * 10, accent);
        drawShadowText(event, Component.translatable("spraute_engine.camera_route.hud.points",
                CameraRouteManager.getWaypoints().size()), x, y + line++ * 10, color);

        CameraRouteWaypoint last = CameraRouteManager.getLastWaypoint();
        if (last != null) {
            drawShadowText(event, Component.translatable("spraute_engine.camera_route.hud.pos",
                    fmt(last.x()), fmt(last.y()), fmt(last.z())), x, y + line++ * 10, color);
            drawShadowText(event, Component.translatable("spraute_engine.camera_route.hud.rot",
                    fmt(last.yaw()), fmt(last.pitch())), x, y + line++ * 10, color);
        }

        drawShadowText(event, Component.translatable("spraute_engine.camera_route.hud.menu_key",
                CameraRouteKeybinds.OPEN_MENU.getTranslatedKeyMessage()), x, y + line++ * 10 + 4, 0xAAAAAA);
        drawShadowText(event, Component.literal("Shift+ПКМ — убрать точку"), x, y + line++ * 10, 0x888888);
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private static void drawShadowText(RenderGuiOverlayEvent.Post event, Component text, int x, int y, int color) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=1.20.1 {
        event.getGuiGraphics().drawString(mc.font, text, x, y, color, true);
        //?} else {
        /*PoseStack pose = event.getPoseStack();
        mc.font.drawShadow(pose, text, x, y, color);
        *///?}
    }
}
