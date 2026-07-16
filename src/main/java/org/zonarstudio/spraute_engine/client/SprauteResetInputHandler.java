package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.SprauteResetPresentationPacket;

/**
 * Ctrl+Shift+U — сброс всех Spraute GUI/оверлеев и возврат камеры игроку.
 * Работает даже когда открыт скриптовый экран (диалог, квесты и т.д.).
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class SprauteResetInputHandler {

    private SprauteResetInputHandler() {}

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (event.getKey() != GLFW.GLFW_KEY_U) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long window = mc.getWindow().getWindow();
        if (!isCtrlDown(window) || !isShiftDown(window)) return;

        SprauteClientReset.applyLocal();
        ModNetwork.CHANNEL.sendToServer(new SprauteResetPresentationPacket());
    }

    private static boolean isCtrlDown(long window) {
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean isShiftDown(long window) {
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}
