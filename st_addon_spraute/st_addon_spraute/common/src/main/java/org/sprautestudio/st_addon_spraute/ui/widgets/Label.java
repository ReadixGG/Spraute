package org.sprautestudio.st_addon_spraute.ui.widgets;

import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.Widget;

import java.util.function.Supplier;

public class Label extends Widget {
    public enum Align { LEFT, CENTER, RIGHT }

    private Supplier<String> text;
    public int color = Theme.TEXT;
    public Align align = Align.LEFT;
    public Icon icon;
    public int iconColor = Theme.TEXT_DIM;
    public boolean wrap;
    public int scale = 1;

    public Label(String text) {
        this(() -> text);
    }

    public Label(Supplier<String> text) {
        this.text = text;
        this.prefH = 12;
    }

    public Label color(int c) {
        this.color = c;
        return this;
    }

    public Label align(Align a) {
        this.align = a;
        return this;
    }

    public Label icon(Icon icon, int color) {
        this.icon = icon;
        this.iconColor = color;
        return this;
    }

    public Label wrap() {
        this.wrap = true;
        return this;
    }

    public Label big() {
        this.scale = 2;
        this.prefH = 22;
        return this;
    }

    public void setText(String s) {
        this.text = () -> s;
    }

    public String text() {
        String s = text.get();
        return s == null ? "" : s;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        String s = text();
        if (wrap) {
            c.wrapped(s, x, y + 1, w, color, 2);
            return;
        }
        int ix = x;
        int textX;
        int avail = w - (icon != null ? 13 : 0);
        if (scale != 1) {
            int tw = c.width(s) * scale;
            int tx = align == Align.CENTER ? x + (w - tw) / 2 : align == Align.RIGHT ? x + w - tw : x;
            c.pushTransform(tx, y + (h - 9 * scale) / 2f, scale);
            c.text(s, 0, 0, color);
            c.popTransform();
            return;
        }
        String shown = c.ellipsize(s, avail);
        int tw = c.width(shown) + (icon != null ? 13 : 0);
        if (align == Align.CENTER) ix = x + (w - tw) / 2;
        else if (align == Align.RIGHT) ix = x + w - tw;
        int ty = y + (h - 8) / 2;
        if (icon != null) {
            c.icon(icon, ix, ty, iconColor);
            textX = ix + 13;
        } else {
            textX = ix;
        }
        c.text(shown, textX, ty, color);
    }
}
