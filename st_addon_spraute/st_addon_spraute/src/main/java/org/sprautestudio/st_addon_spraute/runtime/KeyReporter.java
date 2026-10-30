package org.sprautestudio.st_addon_spraute.runtime;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;

/**
 * Tells the server which keys the player presses in the game (not in menus), so stories can answer
 * "keypress": "jump", "sneak", "sprint" for those actions (whatever key they are on), else the key's
 * own name ("r", "f", "g", "1"…).
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class KeyReporter {
    private KeyReporter() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.getConnection() == null) return;
        String name;
        if (mc.options.keyJump.matches(e.getKey(), e.getScanCode())) name = "jump";
        else if (mc.options.keyShift.matches(e.getKey(), e.getScanCode())) name = "sneak";
        else if (mc.options.keySprint.matches(e.getKey(), e.getScanCode())) name = "sprint";
        else {
            String full = InputConstants.getKey(e.getKey(), e.getScanCode()).getName();
            name = full.startsWith("key.keyboard.") ? full.substring(13) : full;
        }
        SprauteNetwork.sendToServer(new Packets.KeyPress(name));
    }
}
