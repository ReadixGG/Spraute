package org.sprautestudio.spraute_engine.ui.widgets;

import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;

import java.util.function.IntConsumer;

/** Popup colour picker: saturation/value square, hue and alpha bars, palette and hex field. */
public class ColorPicker extends Widget implements UI.OverlayLayout, UI.CursorProvider {
    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFE3E5EE, 0xFF9BA0B5, 0xFF686D85, 0xFF3D4157, 0xFF272A36, 0xFF1C1E26, 0xFF101116, 0xFF000000,
            0xFFF0607A, 0xFFF78C6C, 0xFFF2B84B, 0xFFFFCB6B, 0xFF4CC38A, 0xFF39D0B5, 0xFF5DA9F6, 0xFF8B6CFF, 0xFFE57BD8,
            0x00000000, 0x66000000, 0xCC000000, 0xE01C1E26, 0x88FFFFFF, 0xFFAA0000, 0xFF00AA00, 0xFF0000AA, 0xFFFFAA00};
    private static final int W = 180, SQ = 120, BAR = 10;

    private float hue, sat, val, alpha;
    private final IntConsumer onChange;
    private final TextField hex = new TextField();
    private int ax, ay;
    private int dragging; // 1 square, 2 hue, 3 alpha

    private ColorPicker(int argb, IntConsumer onChange, int ax, int ay) {
        this.onChange = onChange;
        this.ax = ax;
        this.ay = ay;
        setFrom(argb);
        add(hex);
        hex.setText(String.format("#%08X", argb));
        hex.onSubmit = s -> parseHex();
        hex.onChange = s -> parseHex();
        focusable = true;
    }

    /** Opens a picker under (x, y); every change is reported immediately. */
    public static ColorPicker open(int argb, int x, int y, IntConsumer onChange) {
        ColorPicker p = new ColorPicker(argb, onChange, x, y);
        UI ui = UI.get();
        if (ui != null) ui.openOverlay(p, false, true, null);
        return p;
    }

    private void setFrom(int argb) {
        alpha = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        val = max;
        sat = max == 0 ? 0 : (max - min) / max;
        float d = max - min;
        if (d == 0) return;
        float h;
        if (max == r) h = ((g - b) / d) % 6;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        hue = ((h * 60) + 360) % 360;
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    private int value() {
        return (Math.round(alpha * 255) << 24) | (hsv(hue, sat, val) & 0xFFFFFF);
    }

    private void changed(boolean updateHex) {
        int v = value();
        if (updateHex) hex.setText(String.format("#%08X", v));
        onChange.accept(v);
    }

    private void parseHex() {
        String s = hex.text().trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            int v;
            if (s.length() == 6) v = 0xFF000000 | Integer.parseInt(s, 16);
            else if (s.length() == 8) v = (int) Long.parseLong(s, 16);
            else return;
            setFrom(v);
            onChange.accept(v);
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void layoutOverlay(int vw, int vh) {
        int h = 8 + SQ + 6 + BAR + 4 + BAR + 6 + 3 * 14 + 6 + 18 + 8;
        int px = Math.min(ax, vw - W - 4), py = ay + h > vh - 4 ? Math.max(4, ay - h - 24) : ay;
        bounds(px, py, W, h);
        hex.bounds(x + 8, y + h - 26, W - 16, 18);
    }

    @Override
    public void layout() {
        UI ui = UI.get();
        if (ui != null) layoutOverlay(ui.vw, ui.vh);
    }

    private int sqX() { return x + 8; }
    private int sqY() { return y + 8; }
    private int sqW() { return W - 16; }
    private int hueY() { return sqY() + SQ + 6; }
    private int alphaY() { return hueY() + BAR + 4; }
    private int palY() { return alphaY() + BAR + 6; }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.shadow(x, y, w, h, 5, 8);
        c.roundBorder(x, y, w, h, 5, Theme.PANEL, Theme.BORDER_STRONG);
        int sx = sqX(), sy = sqY(), sw = sqW();
        // saturation (left->right) over the hue, then value (top->bottom) darkening
        int steps = 48;
        for (int i = 0; i < steps; i++) {
            int x0 = sx + sw * i / steps, x1 = sx + sw * (i + 1) / steps;
            c.fill(x0, sy, x1 - x0, SQ, hsv(hue, i / (float) (steps - 1), 1));
        }
        c.gradient(sx, sy, sw, SQ, 0x00000000, 0xFF000000);
        int kx = sx + Math.round(sat * sw), ky = sy + Math.round((1 - val) * SQ);
        c.outline(kx - 3, ky - 3, 7, 7, 0xFFFFFFFF);
        c.outline(kx - 2, ky - 2, 5, 5, 0xFF000000);
        // hue
        for (int i = 0; i < steps; i++) {
            int x0 = sx + sw * i / steps, x1 = sx + sw * (i + 1) / steps;
            c.fill(x0, hueY(), x1 - x0, BAR, hsv(360f * i / steps, 1, 1));
        }
        int hx = sx + Math.round(hue / 360f * sw);
        c.fill(hx - 1, hueY() - 1, 3, BAR + 2, 0xFFFFFFFF);
        // alpha over a checkerboard
        for (int i = 0; i < sw; i += 5) c.fill(sx + i, alphaY(), Math.min(5, sw - i), BAR, (i / 5) % 2 == 0 ? 0xFF888888 : 0xFFBBBBBB);
        int rgb = hsv(hue, sat, val) & 0xFFFFFF;
        for (int i = 0; i < steps; i++) {
            int x0 = sx + sw * i / steps, x1 = sx + sw * (i + 1) / steps;
            c.fill(x0, alphaY(), x1 - x0, BAR, ((Math.round(255f * i / (steps - 1))) << 24) | rgb);
        }
        int alx = sx + Math.round(alpha * sw);
        c.fill(alx - 1, alphaY() - 1, 3, BAR + 2, 0xFFFFFFFF);
        // palette
        int cols = 9, cell = (sw - (cols - 1) * 2) / cols;
        for (int i = 0; i < PALETTE.length; i++) {
            int cx = sx + (i % cols) * (cell + 2), cy = palY() + (i / cols) * 14;
            if ((PALETTE[i] >>> 24) < 255) {
                c.fill(cx, cy, cell, 12, 0xFF888888);
                c.fill(cx, cy, cell / 2, 6, 0xFFBBBBBB);
                c.fill(cx + cell / 2, cy + 6, cell - cell / 2, 6, 0xFFBBBBBB);
            }
            c.fill(cx, cy, cell, 12, PALETTE[i]);
            c.outline(cx, cy, cell, 12, 0x33FFFFFF);
        }
    }

    private boolean pick(double mx, double my, int mode) {
        int sx = sqX(), sw = sqW();
        float fx = (float) Math.max(0, Math.min(1, (mx - sx) / sw));
        switch (mode) {
            case 1 -> {
                sat = fx;
                val = (float) Math.max(0, Math.min(1, 1 - (my - sqY()) / SQ));
            }
            case 2 -> hue = Math.min(359.9f, fx * 360);
            case 3 -> alpha = fx;
            default -> {
                return false;
            }
        }
        if (alpha == 0 && mode != 3) alpha = 1;
        changed(true);
        return true;
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        int sx = sqX(), sw = sqW();
        if (mx >= sx && mx < sx + sw) {
            if (my >= sqY() && my < sqY() + SQ) dragging = 1;
            else if (my >= hueY() && my < hueY() + BAR) dragging = 2;
            else if (my >= alphaY() && my < alphaY() + BAR) dragging = 3;
            else dragging = 0;
            if (dragging != 0) return pick(mx, my, dragging);
            int cols = 9, cell = (sw - (cols - 1) * 2) / cols;
            for (int i = 0; i < PALETTE.length; i++) {
                int cx = sx + (i % cols) * (cell + 2), cy = palY() + (i / cols) * 14;
                if (mx >= cx && mx < cx + cell && my >= cy && my < cy + 12) {
                    setFrom(PALETTE[i]);
                    if ((PALETTE[i] >>> 24) == 0) alpha = 0;
                    hex.setText(String.format("#%08X", PALETTE[i]));
                    onChange.accept(PALETTE[i]);
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return dragging != 0 && pick(mx, my, dragging);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = 0;
        return true;
    }

    @Override
    public int cursor(int mx, int my) {
        return my < palY() ? UI.CURSOR_MOVE : UI.CURSOR_HAND;
    }
}
