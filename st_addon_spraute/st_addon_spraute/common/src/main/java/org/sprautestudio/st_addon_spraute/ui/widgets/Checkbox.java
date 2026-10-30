package org.sprautestudio.st_addon_spraute.ui.widgets;

import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Toggle switch with a label. */
public class Checkbox extends Widget implements UI.CursorProvider {
    private final String text;
    private final BooleanSupplier value;
    private final Consumer<Boolean> onChange;
    private float anim;

    public Checkbox(String text, BooleanSupplier value, Consumer<Boolean> onChange) {
        this.text = text;
        this.value = value;
        this.onChange = onChange;
        this.prefH = 16;
    }

    @Override
    public int cursor(int mx, int my) {
        return UI.CURSOR_HAND;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        boolean on = value.getAsBoolean();
        anim += (on ? 1 : -1) * dt * 10;
        anim = Math.max(0, Math.min(1, anim));
        int sw = 22, sh = 12;
        int sy = y + (h - sh) / 2;
        c.round(x, sy, sw, sh, 6, Theme.lerp(Theme.BORDER_STRONG, Theme.ACCENT, anim));
        int knob = (int) (x + 2 + anim * (sw - sh));
        c.round(knob, sy + 2, sh - 4, sh - 4, 4, 0xFFFFFFFF);
        c.text(text, x + sw + 6, y + (h - 8) / 2, Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, hoverAnim));
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        if (button != 0) return false;
        onChange.accept(!value.getAsBoolean());
        return true;
    }

    /** Small pill showing a state, e.g. "активен". */
    public static void badge(Canvas c, String text, int x, int y, int color) {
        int w = c.width(text) + 8;
        c.round(x, y, w, 11, 4, Theme.alpha(color, 0.22f));
        c.text(text, x + 4, y + 2, color);
    }

    public static int badgeWidth(Canvas c, String text) {
        return c.width(text) + 8;
    }

    public static void iconBadge(Canvas c, Icon icon, int x, int y, int color) {
        c.round(x, y, 13, 13, 3, Theme.alpha(color, 0.22f));
        c.icon(icon, x + 2, y + 2, color);
    }
}
