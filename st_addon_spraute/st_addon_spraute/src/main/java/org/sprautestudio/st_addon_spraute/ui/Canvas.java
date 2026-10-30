package org.sprautestudio.st_addon_spraute.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Drawing surface of the Spraute UI. Works in "virtual" pixels: the UI can use its own
 * scale independent from Minecraft's GUI scale; clipping is converted accordingly.
 */
public final class Canvas {
    public final GuiGraphics g;
    public final Font font;
    /** Factor from virtual pixels to Minecraft GUI pixels. */
    private final float baseScale;
    /** Current transform on top of the base scale (used by zoomable views). */
    private float tx, ty, zoom = 1f;
    private final Deque<float[]> transforms = new ArrayDeque<>();
    private final Deque<int[]> clips = new ArrayDeque<>();

    public Canvas(GuiGraphics g, Font font, float baseScale) {
        this.g = g;
        this.font = font;
        this.baseScale = baseScale;
    }

    // ------------------------------------------------------------------ shapes

    public void fill(int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        g.fill(x, y, x + w, y + h, color);
    }

    public void fillF(float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(w, h, 1);
        g.fill(0, 0, 1, 1, color);
        g.pose().popPose();
    }

    public void outline(int x, int y, int w, int h, int color) {
        fill(x, y, w, 1, color);
        fill(x, y + h - 1, w, 1, color);
        fill(x, y + 1, 1, h - 2, color);
        fill(x + w - 1, y + 1, 1, h - 2, color);
    }

    public void gradient(int x, int y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        g.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    /** Filled rectangle with rounded corners (radius in pixels, 0..6 looks best). */
    public void round(int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            fill(x, y, w, h, color);
            return;
        }
        fill(x, y + r, w, h - 2 * r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            fill(x + inset, y + i, w - 2 * inset, 1, color);
            fill(x + inset, y + h - 1 - i, w - 2 * inset, 1, color);
        }
    }

    public void roundBorder(int x, int y, int w, int h, int r, int fill, int border) {
        round(x, y, w, h, r, border);
        round(x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    public void shadow(int x, int y, int w, int h, int r, int size) {
        for (int i = size; i > 0; i--) {
            int a = (int) (40.0 * (1 - (double) i / (size + 1)) * (1.0 / size) * 2.2);
            round(x - i, y - i + 2, w + 2 * i, h + 2 * i, r + i, a << 24);
        }
    }

    public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        g.pose().pushPose();
        g.pose().translate(x1, y1, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        g.pose().scale(len, thickness, 1);
        g.pose().translate(0, -0.5f, 0);
        g.fill(0, 0, 1, 1, color);
        g.pose().popPose();
    }

    public void bezier(float x1, float y1, float x2, float y2, float thickness, int color) {
        float d = Math.max(30, Math.abs(x2 - x1) * 0.5f);
        float cx1 = x1 + d, cx2 = x2 - d;
        int steps = Math.max(12, (int) (Math.abs(x2 - x1) + Math.abs(y2 - y1)) / 6);
        float px = x1, py = y1;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps, u = 1 - t;
            float nx = u * u * u * x1 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x2;
            float ny = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
            line(px, py, nx, ny, thickness, color);
            px = nx;
            py = ny;
        }
    }

    /** Zig-zag underline used for code diagnostics. */
    public void squiggle(int x, int y, int w, int color) {
        for (int i = 0; i < w; i += 2) {
            int off = ((i / 2) % 2 == 0) ? 0 : 1;
            fill(x + i, y + off, Math.min(2, w - i), 1, color);
        }
    }

    public void icon(Icon icon, int x, int y, int color) {
        icon.draw(this, x, y, color);
    }

    // -------------------------------------------------------------------- text

    public int text(String s, int x, int y, int color) {
        return g.drawString(font, s, x, y, color, false);
    }

    public int text(Component s, int x, int y, int color) {
        return g.drawString(font, s, x, y, color, false);
    }

    public int textShadow(String s, int x, int y, int color) {
        return g.drawString(font, s, x, y, color, true);
    }

    public void textCentered(String s, int cx, int y, int color) {
        text(s, cx - font.width(s) / 2, y, color);
    }

    public void textEllipsized(String s, int x, int y, int maxWidth, int color) {
        text(ellipsize(s, maxWidth), x, y, color);
    }

    public String ellipsize(String s, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(s) <= maxWidth) return s;
        String dots = "…";
        int dw = font.width(dots);
        return font.plainSubstrByWidth(s, Math.max(0, maxWidth - dw)) + dots;
    }

    public int width(String s) {
        return font.width(s);
    }

    public int lineHeight() {
        return font.lineHeight;
    }

    /** Draws word-wrapped text and returns the height used. */
    public int wrapped(String s, int x, int y, int width, int color, int lineSpacing) {
        int yy = y;
        for (String paragraph : s.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                yy += font.lineHeight + lineSpacing;
                continue;
            }
            for (FormattedCharSequence line : font.split(Component.literal(paragraph), Math.max(10, width))) {
                g.drawString(font, line, x, yy, color, false);
                yy += font.lineHeight + lineSpacing;
            }
        }
        return yy - y;
    }

    public int wrappedHeight(String s, int width, int lineSpacing) {
        int h = 0;
        for (String paragraph : s.split("\n", -1)) {
            int lines = paragraph.isEmpty() ? 1 : font.split(Component.literal(paragraph), Math.max(10, width)).size();
            h += lines * (font.lineHeight + lineSpacing);
        }
        return h;
    }

    // ------------------------------------------------------------- transforms

    public void pushTransform(float translateX, float translateY, float scale) {
        transforms.push(new float[]{tx, ty, zoom});
        g.pose().pushPose();
        g.pose().translate(translateX, translateY, 0);
        g.pose().scale(scale, scale, 1);
        tx = tx + translateX * zoom;
        ty = ty + translateY * zoom;
        zoom *= scale;
    }

    public void popTransform() {
        float[] t = transforms.pop();
        tx = t[0];
        ty = t[1];
        zoom = t[2];
        g.pose().popPose();
    }

    /** Raises following draws above previously drawn content (for popups). */
    public void pushLayer(float z) {
        g.pose().pushPose();
        g.pose().translate(0, 0, z);
    }

    public void popLayer() {
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- clipping

    public void pushClip(int x, int y, int w, int h) {
        float s = baseScale;
        int x0 = (int) Math.floor((tx + x * zoom) * s);
        int y0 = (int) Math.floor((ty + y * zoom) * s);
        int x1 = (int) Math.ceil((tx + (x + w) * zoom) * s);
        int y1 = (int) Math.ceil((ty + (y + h) * zoom) * s);
        if (x1 < x0) x1 = x0;
        if (y1 < y0) y1 = y0;
        clips.push(new int[]{x0, y0, x1, y1});
        g.enableScissor(x0, y0, x1, y1);
    }

    public void popClip() {
        if (clips.isEmpty()) return;
        clips.pop();
        g.disableScissor();
    }

    public void flush() {
        g.flush();
    }
}
