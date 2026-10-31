package org.sprautestudio.spraute_engine.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sprautestudio.spraute_engine.engine.client.EngineClient;
import org.sprautestudio.spraute_engine.engine.client.HudPainter;

import java.util.List;

/** The record of what was said in the newest running story: the game pauses, the HUD hides, the latest line is in view. */
public final class ChatLogScreen extends Screen {
    private static final int GAP = 4, EDGE = 12, BACK_ALPHA = 0xB3;
    private static final float MAX_WIDTH = 0.47f;
    /** Lines show up one after another when it opens: seconds of fade and of delay between lines. */
    private static final float FADE = 0.25f, CASCADE = 0.03f;

    private final long opened = System.nanoTime();
    private final boolean hudWas;
    /** How far up from the latest line (pixels). */
    private int scroll;

    public ChatLogScreen() {
        super(Component.literal("История речи"));
        hudWas = Minecraft.getInstance().options.hideGui;
        Minecraft.getInstance().options.hideGui = true;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void removed() {
        Minecraft.getInstance().options.hideGui = hudWas;
    }

    private int total(List<SpeechClient.Line> lines, int maxW) {
        int h = 0;
        for (SpeechClient.Line l : lines) h += SpeechClient.plateHeight(font, l, maxW) + GAP;
        return h;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        List<SpeechClient.Line> lines = SpeechClient.history();
        if (lines == null) return true;
        int view = height - EDGE * 2;
        int most = Math.max(0, total(lines, Math.round(width * MAX_WIDTH)) - view);
        scroll = Math.max(0, Math.min(most, scroll + (int) (delta * 24)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        var cfg = EngineClient.config();
        var lay = EngineClient.window("chatlog");
        int[] r = HudPainter.chatRect(width, height);
        if (lay != null) EngineClient.drawWindow(g, lay, mx, my);
        else {
            HudPainter.chatBack(g, cfg, r[0], r[1], r[2], r[3]);
            if (!cfg.chatLog.frameOnTop()) HudPainter.chatFrame(g, cfg, r[0], r[1], r[2], r[3]);
        }
        List<SpeechClient.Line> lines = SpeechClient.history();
        if (lines != null) drawLines(g, lines);
        if (lay == null && cfg.chatLog.frameOnTop()) HudPainter.chatFrame(g, cfg, r[0], r[1], r[2], r[3]);
    }

    /** The newest line at the bottom; they appear from the newest up, with a slight cascade. */
    private void drawLines(GuiGraphics g, List<SpeechClient.Line> lines) {
        int maxW = Math.round(width * MAX_WIDTH), top = EDGE, bottom = height - EDGE;
        float since = (System.nanoTime() - opened) / 1e9f;
        g.enableScissor(0, top, width, bottom);
        int y = bottom + scroll;
        int shown = 0;
        for (int i = lines.size() - 1; i >= 0 && y > top; i--) {
            SpeechClient.Line l = lines.get(i);
            y -= SpeechClient.plateHeight(font, l, maxW) + GAP;
            float alpha = Math.max(0, Math.min(1, (since - shown * CASCADE) / FADE));
            shown++;
            if (y < bottom && alpha > 0) SpeechClient.plate(g, font, l, width / 2, y, maxW, BACK_ALPHA, alpha, false);
        }
        g.disableScissor();
    }
}
