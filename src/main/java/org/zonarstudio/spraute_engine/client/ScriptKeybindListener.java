package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.network.KeybindPressedPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side listener that detects key presses and sends them to the server
 * for scripts waiting on `await keybind(key)`.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public class ScriptKeybindListener {

    public static final Map<String, Integer> KEY_MAP = new HashMap<>();

    static {
        KEY_MAP.put("space", GLFW.GLFW_KEY_SPACE);
        KEY_MAP.put("shift", GLFW.GLFW_KEY_LEFT_SHIFT);
        KEY_MAP.put("ctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        KEY_MAP.put("alt", GLFW.GLFW_KEY_LEFT_ALT);
        KEY_MAP.put("enter", GLFW.GLFW_KEY_ENTER);
        KEY_MAP.put("tab", GLFW.GLFW_KEY_TAB);
        KEY_MAP.put("escape", GLFW.GLFW_KEY_ESCAPE);
        for (char c = 'a'; c <= 'z'; c++) {
            KEY_MAP.put(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'a'));
        }
        for (int i = 0; i <= 9; i++) {
            KEY_MAP.put(String.valueOf(i), GLFW.GLFW_KEY_0 + i);
        }
        for (int i = 1; i <= 12; i++) {
            KEY_MAP.put("f" + i, GLFW.GLFW_KEY_F1 + (i - 1));
        }
    }

    @SubscribeEvent
    public static void onKeyPress(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        for (var entry : KEY_MAP.entrySet()) {
            if (entry.getValue() == event.getKey()) {
                ModNetwork.CHANNEL.sendToServer(new KeybindPressedPacket(entry.getKey()));
                return;
            }
        }
    }
}
