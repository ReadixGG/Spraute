package org.zonarstudio.spraute_engine.client.cameraroute;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class CameraRouteInputHandler {
    private CameraRouteInputHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (CameraRouteKeybinds.OPEN_MENU == null || !CameraRouteKeybinds.OPEN_MENU.consumeClick()) return;
        if (isHoldingCamera(mc)) {
            mc.setScreen(new CameraRouteScreen());
        }
    }

    private static boolean isHoldingCamera(Minecraft mc) {
        return mc.player.getMainHandItem().getItem() instanceof org.zonarstudio.spraute_engine.item.CameraItem
                || mc.player.getOffhandItem().getItem() instanceof org.zonarstudio.spraute_engine.item.CameraItem;
    }
}
