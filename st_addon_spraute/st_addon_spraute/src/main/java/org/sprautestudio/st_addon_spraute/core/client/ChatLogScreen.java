package org.sprautestudio.st_addon_spraute.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sprautestudio.st_addon_spraute.engine.client.EngineClient;
import org.sprautestudio.st_addon_spraute.engine.client.HudPainter;

import java.util.List;

/** The history of what was said in the story. */
public final class ChatLogScreen extends Screen {
    private static final int ROW_GAP = 8;
    private int scroll;

    public ChatLogScreen() {
        super(Component.literal("История речи"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll + (int) (delta * 24));
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
        renderChat(g, r);
        if (lay == null && cfg.chatLog.frameOnTop()) HudPainter.chatFrame(g, cfg, r[0], r[1], r[2], r[3]);
    }

    /** The newest line at the bottom; the wheel scrolls back. */
    private void renderChat(GuiGraphics g, int[] r) {
        List<SpeechClient.Line> lines = SpeechClient.history();
        int pad = 24, x = r[0] + pad, w = r[2] - pad * 2, top = r[1] + pad, bottom = r[1] + r[3] - pad;
        g.enableScissor(x, top, x + w, bottom);
        int y = bottom + scroll;
        for (int i = lines.size() - 1; i >= 0 && y > top; i--) {
            SpeechClient.Line l = lines.get(i);
            int rows = font.split(Component.literal(l.text()), w - 40).size();
            y -= Math.max(32, 12 + rows * 10) + ROW_GAP;
            SpeechClient.drawLine(g, font, l, x, y, w, false);
        }
        g.disableScissor();
    }
}
