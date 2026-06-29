package org.zonarstudio.spraute_engine.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.network.CameraPacket;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class CameraClientEvents {
    private CameraClientEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!CameraHandler.isLockMovement()) return;
        event.getInput().forwardImpulse = 0f;
        event.getInput().leftImpulse = 0f;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!CameraHandler.isActive()) return;

        byte mode = CameraHandler.getHideGui();
        if (mode == CameraPacket.HIDE_NONE) return;

        NamedGuiOverlay overlay = event.getOverlay();
        if (mode == CameraPacket.HIDE_ALL) {
            event.setCanceled(true);
            return;
        }

        if (mode == CameraPacket.HIDE_MINECRAFT && isVanillaOverlay(overlay)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderHand(RenderHandEvent event) {
        if (CameraHandler.shouldHideVanillaHud()) {
            event.setCanceled(true);
        }
    }

    private static boolean isVanillaOverlay(NamedGuiOverlay overlay) {
        return "minecraft".equals(overlay.id().getNamespace());
    }
}
