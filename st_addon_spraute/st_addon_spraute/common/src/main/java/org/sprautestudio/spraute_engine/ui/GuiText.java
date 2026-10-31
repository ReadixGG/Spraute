package org.sprautestudio.spraute_engine.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

/** Text at a fraction of a pixel, for scaled text: the game's own GuiGraphics draws it at whole ones only. */
public final class GuiText {
    private GuiText() {
    }

    private static final int FULL_BRIGHT = 15728880;

    public static void draw(GuiGraphics g, Font font, String text, float x, float y, int color, boolean shadow) {
        if (text == null) return;
        font.drawInBatch(text, x, y, color, shadow, g.pose().last().pose(), g.bufferSource(), Font.DisplayMode.NORMAL, 0, FULL_BRIGHT, font.isBidirectional());
        g.flush();
    }

    public static void draw(GuiGraphics g, Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
        font.drawInBatch(text, x, y, color, shadow, g.pose().last().pose(), g.bufferSource(), Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
        g.flush();
    }
}
