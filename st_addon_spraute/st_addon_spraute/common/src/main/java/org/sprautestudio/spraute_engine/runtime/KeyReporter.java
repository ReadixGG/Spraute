package org.sprautestudio.spraute_engine.runtime;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.net.SprauteNetwork;
import org.sprautestudio.spraute_engine.platform.ClientHooks;

/**
 * Tells the server which keys the player presses in the game (not in menus), so stories can answer
 * "keypress": "jump", "sneak", "sprint" for those actions (whatever key they are on), else the key's
 * own name ("r", "f", "g", "1"…).
 */
public final class KeyReporter {
    public static void registerHooks() {
        ClientHooks.KEY.add(KeyReporter::onKey);
        ClientHooks.MOUSE_BUTTON.add(KeyReporter::onMouse);
        ClientHooks.LEFT_CLICK_EMPTY.add(KeyReporter::onLeftAir);
    }

    private KeyReporter() {
    }

    public static void onKey(ClientHooks.Key e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.getConnection() == null) return;
        // the key that moves speech on is F8 to the stories, whichever it is
        if (org.sprautestudio.spraute_engine.core.client.SpeechClient.ADVANCE.matches(e.getKey(), e.getScanCode())) SprauteNetwork.sendToServer(new Packets.KeyPress("f8"));
        String name;
        if (mc.options.keyJump.matches(e.getKey(), e.getScanCode())) name = "jump";
        else if (mc.options.keyShift.matches(e.getKey(), e.getScanCode())) name = "sneak";
        else if (mc.options.keySprint.matches(e.getKey(), e.getScanCode())) name = "sprint";
        else {
            String full = InputConstants.getKey(e.getKey(), e.getScanCode()).getName();
            name = full.startsWith("key.keyboard.") ? full.substring(13) : full;
        }
        if (!name.equals("f8")) SprauteNetwork.sendToServer(new Packets.KeyPress(name));
    }

    /** Mouse buttons, for "keypress": mouse.left, mouse.right, mouse.middle. */
    public static void onMouse(ClientHooks.MouseButton e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getAction() != GLFW.GLFW_PRESS || mc.player == null || mc.screen != null || mc.getConnection() == null) return;
        String name = switch (e.getButton()) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "mouse.left";
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "mouse.right";
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "mouse.middle";
            default -> "mouse." + e.getButton();
        };
        SprauteNetwork.sendToServer(new Packets.KeyPress(name));
    }

    /** A left click at nothing is known to the client only. */
    public static void onLeftAir(ClientHooks.LeftClickEmpty e) {
        SprauteNetwork.sendToServer(new Packets.KeyPress("air.left"));
    }
}
