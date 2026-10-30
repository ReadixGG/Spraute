package org.sprautestudio.st_addon_spraute.editor.texture;

import java.util.ArrayDeque;
import java.util.function.IntBinaryOperator;

/** Colours and pixel-art drawing: lines, shapes, fill, hue-shifted shading ramps and dithered gradients. */
public final class PixelOps {
    private PixelOps() {
    }

    // ================================================================== colour

    public static int a(int c) {
        return c >>> 24;
    }

    /** src over dst with an extra opacity. */
    public static int over(int dst, int src, float opacity) {
        int sa = Math.round((src >>> 24) * opacity);
        if (sa == 0) return dst;
        int da = dst >>> 24;
        if (sa == 255 || da == 0) return (sa << 24) | (src & 0xFFFFFF);
        float s = sa / 255f, d = da / 255f * (1 - s), oa = s + d;
        int r = Math.round((((src >> 16) & 255) * s + ((dst >> 16) & 255) * d) / oa);
        int g = Math.round((((src >> 8) & 255) * s + ((dst >> 8) & 255) * d) / oa);
        int b = Math.round(((src & 255) * s + (dst & 255) * d) / oa);
        return (Math.round(oa * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    public static float[] hsv(int c) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h = (h * 60 + 360) % 360;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    public static int fromHsv(float h, float s, float v, int alpha) {
        h = ((h % 360) + 360) % 360;
        s = clamp(s);
        v = clamp(v);
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return (alpha << 24) | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static float towards(float h, float target, float by) {
        float d = ((target - h + 540) % 360) - 180;
        return h + Math.signum(d) * Math.min(Math.abs(d), by);
    }

    /**
     * A colour "steps" shades lighter (+) or darker (−), the pixel-art way: shadows go cooler (towards
     * blue-violet) and more saturated, lights go warmer (towards yellow) and a little paler.
     * hueShift — degrees per step (0 — plain lighter/darker).
     */
    public static int shade(int c, int steps, float hueShift) {
        if (steps == 0 || (c >>> 24) == 0) return c;
        float[] hsv = hsv(c);
        float h = hsv[0], s = hsv[1], v = hsv[2];
        boolean grey = s < 0.06f;
        for (int i = 0; i < Math.abs(steps); i++) {
            if (steps < 0) {
                if (!grey) h = towards(h, 250, hueShift);
                s = clamp(s + (grey ? 0.02f : 0.07f));
                v = clamp(v - 0.13f);
            } else {
                if (!grey) h = towards(h, 55, hueShift);
                s = clamp(s - (grey ? 0 : 0.06f));
                v = clamp(v + 0.12f);
            }
        }
        return fromHsv(h, grey ? Math.min(s, 0.12f) : s, v, c >>> 24);
    }

    /** n shades of a colour from dark to light, the colour itself in the middle. */
    public static int[] ramp(int c, int n, float hueShift) {
        int[] out = new int[n];
        int mid = n / 2;
        for (int i = 0; i < n; i++) out[i] = shade(c, i - mid, hueShift);
        return out;
    }

    /** n colours from a to b (hue taken the short way). */
    public static int[] blend(int a, int b, int n) {
        int[] out = new int[n];
        float[] ha = hsv(a), hb = hsv(b);
        boolean greyA = ha[1] < 0.05f, greyB = hb[1] < 0.05f;
        if (greyA) ha[0] = hb[0];
        if (greyB) hb[0] = ha[0];
        float dh = ((hb[0] - ha[0] + 540) % 360) - 180;
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0 : i / (float) (n - 1);
            int alpha = Math.round((a >>> 24) + ((b >>> 24) - (a >>> 24)) * t);
            out[i] = fromHsv(ha[0] + dh * t, ha[1] + (hb[1] - ha[1]) * t, ha[2] + (hb[2] - ha[2]) * t, alpha);
        }
        return out;
    }

    // ================================================================== dithering

    private static final int[] BAYER4 = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
    private static final int[] BAYER2 = {0, 2, 3, 1};

    /** Ordered-dither threshold 0..1 for a pixel; pattern 0 — none, 1 — checker 2×2, 2 — Bayer 4×4. */
    public static float threshold(int x, int y, int pattern) {
        return switch (pattern) {
            case 1 -> (BAYER2[(y & 1) * 2 + (x & 1)] + 0.5f) / 4f;
            case 2 -> (BAYER4[(y & 3) * 4 + (x & 3)] + 0.5f) / 16f;
            default -> 0.5f;
        };
    }

    /**
     * Which colour of a ramp a gradient gives this pixel: t 0..1 along the gradient, the band edges
     * softened by ordered dithering over "spread" (0 — hard bands, 1 — the whole band dithered).
     */
    public static int gradientIndex(float t, int n, int x, int y, int pattern, float spread) {
        float p = clamp(t) * (n - 1);
        int base = (int) Math.floor(p);
        if (base >= n - 1) return n - 1;
        float frac = p - base;
        if (pattern == 0 || spread <= 0) return frac >= 0.5f ? base + 1 : base;
        float s = Math.max(0.01f, spread);
        float f = clamp((frac - 0.5f) / s + 0.5f);
        return f > threshold(x, y, pattern) ? base + 1 : base;
    }

    // ================================================================== shapes

    public interface Plot {
        void at(int x, int y);
    }

    public static void line(int x0, int y0, int x1, int y1, Plot p) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            p.at(x0, y0);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    public static void rect(int x0, int y0, int x1, int y1, boolean filled, Plot p) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1);
        for (int y = ay; y <= by; y++)
            for (int x = ax; x <= bx; x++)
                if (filled || x == ax || x == bx || y == ay || y == by) p.at(x, y);
    }

    /** An ellipse in the box (x0,y0)-(x1,y1), pixel-clean (no doubled corners). */
    public static void ellipse(int x0, int y0, int x1, int y1, boolean filled, Plot p) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1);
        double cx = (ax + bx) / 2.0, cy = (ay + by) / 2.0, rx = (bx - ax) / 2.0 + 0.5, ry = (by - ay) / 2.0 + 0.5;
        for (int y = ay; y <= by; y++) {
            for (int x = ax; x <= bx; x++) {
                if (!insideE(x, y, cx, cy, rx, ry)) continue;
                if (filled) p.at(x, y);
                else if (!insideE(x - 1, y, cx, cy, rx, ry) || !insideE(x + 1, y, cx, cy, rx, ry)
                        || !insideE(x, y - 1, cx, cy, rx, ry) || !insideE(x, y + 1, cx, cy, rx, ry)) p.at(x, y);
            }
        }
    }

    private static boolean insideE(int x, int y, double cx, double cy, double rx, double ry) {
        double dx = (x - cx) / rx, dy = (y - cy) / ry;
        return dx * dx + dy * dy <= 1.0;
    }

    /** The pixels of the same colour joined to (x, y) (or all of that colour when !contiguous): a mask. */
    public static boolean[] region(int[] px, int w, int h, int x, int y, boolean contiguous, int tolerance) {
        boolean[] m = new boolean[w * h];
        int target = px[y * w + x];
        if (!contiguous) {
            for (int i = 0; i < px.length; i++) m[i] = close(px[i], target, tolerance);
            return m;
        }
        ArrayDeque<Integer> todo = new ArrayDeque<>();
        todo.add(y * w + x);
        m[y * w + x] = true;
        while (!todo.isEmpty()) {
            int i = todo.poll(), cx = i % w, cy = i / w;
            int[][] n = {{cx - 1, cy}, {cx + 1, cy}, {cx, cy - 1}, {cx, cy + 1}};
            for (int[] q : n) {
                if (q[0] < 0 || q[1] < 0 || q[0] >= w || q[1] >= h) continue;
                int j = q[1] * w + q[0];
                if (!m[j] && close(px[j], target, tolerance)) {
                    m[j] = true;
                    todo.add(j);
                }
            }
        }
        return m;
    }

    static boolean close(int a, int b, int tol) {
        if ((a >>> 24) == 0 && (b >>> 24) == 0) return true;
        if (tol <= 0) return a == b;
        int d = Math.abs((a >>> 24) - (b >>> 24)) + Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))
                + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) + Math.abs((a & 255) - (b & 255));
        return d <= tol * 4;
    }

    /** Applies f(old colour, index) to every pixel of a mask. */
    public static void apply(int[] px, boolean[] mask, IntBinaryOperator f) {
        for (int i = 0; i < px.length; i++) if (mask == null || mask[i]) px[i] = f.applyAsInt(px[i], i);
    }
}
