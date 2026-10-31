package org.sprautestudio.spraute_engine.editor.cutscene;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.editor.ClientEvents;
import org.sprautestudio.spraute_engine.platform.ClientHooks;
import org.sprautestudio.spraute_engine.platform.ClientPlatform;
import org.sprautestudio.spraute_engine.platform.TickPhase;

/**
 * Recording a cutscene in the game: the editor is closed, the author flies around and presses the
 * frame key (K) wherever the camera should be; the editor key brings the editor back.
 */
public final class CutsceneRecorder {
    public static void registerHooks() {
        ClientHooks.CLIENT_TICK.add(CutsceneRecorder::onTick);
        ClientHooks.RENDER_GUI.add(CutsceneRecorder::onGui);
    }

    private CutsceneRecorder() {}

    public static final KeyMapping FRAME = ClientPlatform.get().gameKey("key.spraute_engine.cutscene_frame", GLFW.GLFW_KEY_K, "key.categories.spraute_engine");

    private static CutsceneTab tab;

    static void start(CutsceneTab t) {
        tab = t;
    }

    public static boolean recording() {
        return tab != null;
    }

    public static void onTick(ClientHooks.ClientTick e) {
        if (e.phase != TickPhase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (tab == null) {
            if (CutsceneWorldEditor.active()) return;
            while (FRAME.consumeClick()) {
                // not recording: ignore
            }
            return;
        }
        // back in the editor: recording is over
        if (mc.screen instanceof org.sprautestudio.spraute_engine.editor.SprauteScreen || mc.player == null) {
            tab = null;
            return;
        }
        while (FRAME.consumeClick()) {
            if (mc.screen != null) continue;
            tab.addFromPlayer();
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.6f);
        }
    }

    public static void onGui(ClientHooks.RenderGui e) {
        if (tab == null) return;
        Minecraft mc = Minecraft.getInstance();
        var g = e.getGuiGraphics();
        String key = FRAME.getTranslatedKeyMessage().getString();
        String back = ClientEvents.OPEN_EDITOR.getTranslatedKeyMessage().getString();
        String[] lines = {"● Запись катсцены — кадров: " + tab.frames(),
                "[" + key + "] — кадр там, где вы стоите и куда смотрите",
                "[" + back + "] — вернуться в редактор (в режиме наблюдателя можно летать сквозь стены)"};
        int y = 6;
        for (int i = 0; i < lines.length; i++) {
            int w = mc.font.width(lines[i]);
            g.fill(4, y - 2, 10 + w, y + 9, 0x88000000);
            g.drawString(mc.font, lines[i], 7, y, i == 0 ? 0xFFFF5555 : 0xFFFFFFFF, false);
            y += 12;
        }
    }
}
