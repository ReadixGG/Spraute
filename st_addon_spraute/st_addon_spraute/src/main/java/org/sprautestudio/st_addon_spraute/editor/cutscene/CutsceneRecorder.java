package org.sprautestudio.st_addon_spraute.editor.cutscene;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.editor.ClientEvents;

/**
 * Recording a cutscene in the game: the editor is closed, the author flies around and presses the
 * frame key (K) wherever the camera should be; the editor key brings the editor back.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class CutsceneRecorder {
    private CutsceneRecorder() {}

    public static final KeyMapping FRAME = new KeyMapping("key.st_addon_spraute.cutscene_frame", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.st_addon_spraute");

    private static CutsceneTab tab;

    static void start(CutsceneTab t) {
        tab = t;
    }

    public static boolean recording() {
        return tab != null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (tab == null) {
            if (CutsceneWorldEditor.active()) return;
            while (FRAME.consumeClick()) {
                // not recording: ignore
            }
            return;
        }
        // back in the editor: recording is over
        if (mc.screen instanceof org.sprautestudio.st_addon_spraute.editor.SprauteScreen || mc.player == null) {
            tab = null;
            return;
        }
        while (FRAME.consumeClick()) {
            if (mc.screen != null) continue;
            tab.addFromPlayer();
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.6f);
        }
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
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
