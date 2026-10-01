package org.zonarstudio.spraute_engine.client.editor.texture;

import org.zonarstudio.spraute_engine.client.editor.texture.TextureDocument.IntRect;
import org.zonarstudio.spraute_engine.client.editor.texture.TextureDocument.PixelClip;

/**
 * Discrete pixel-art shade ramp: OKLab mix + hue-shifted shadows/highlights
 * + ordered dither between steps. Not a smooth RGB blend.
 */
public final class PixelArtGradient {
    public enum Shape { LINEAR, RADIAL }

    public enum Dither {
        NONE,
        BAYER2,
        BAYER4,
        BAYER8,
        HATCH
    }

    private static final int[] BAYER2 = {
            0, 2,
            3, 1
    };
    private static final int[] BAYER4 = {
            0, 8, 2, 10,
            12, 4, 14, 6,
            3, 11, 1, 9,
            15, 7, 13, 5
    };
    private static final int[] BAYER8 = {
            0, 32, 8, 40, 2, 34, 10, 42,
            48, 16, 56, 24, 50, 18, 58, 26,
            12, 44, 4, 36, 14, 46, 6, 38,
            60, 28, 52, 20, 62, 30, 54, 22,
            3, 35, 11, 43, 1, 33, 9, 41,
            51, 19, 59, 27, 49, 17, 57, 25,
            15, 47, 7, 39, 13, 45, 5, 37,
            63, 31, 55, 23, 61, 29, 53, 21
    };

    private PixelArtGradient() {}

    public static int[] ramp(int colorA, int colorB, int steps, float hueShift) {
        int n = Math.max(2, Math.min(16, steps));
        int[] out = new int[n];
        float[] a = new float[4];
        float[] b = new float[4];
        TextureDocument.unpackRgba(colorA, a);
        TextureDocument.unpackRgba(colorB, b);
        float[] labA = srgbToOklab(a[0], a[1], a[2]);
        float[] labB = srgbToOklab(b[0], b[1], b[2]);
        float shift = Math.max(0f, Math.min(1f, hueShift));
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            float l = labA[0] + (labB[0] - labA[0]) * t;
            float ca = labA[1] + (labB[1] - labA[1]) * t;
            float cb = labA[2] + (labB[2] - labA[2]) * t;
            float[] rgb = oklabToSrgb(l, ca, cb);
            applyHueShift(rgb, l, shift);
            float alpha = a[3] + (b[3] - a[3]) * t;
            out[i] = TextureDocument.packRgba(rgb[0], rgb[1], rgb[2], alpha);
        }
        return out;
    }

    public static void paint(int[] pixels, int w, int h,
                             int x0, int y0, int x1, int y1,
                             int colorA, int colorB,
                             int steps, float hueShift,
                             Dither dither, float ditherAmt,
                             Shape shape, IntRect bounds, PixelClip clip) {
        if (pixels == null || w <= 0 || h <= 0) return;
        int[] colors = ramp(colorA, colorB, steps, hueShift);
        int minX = 0, minY = 0, maxX = w, maxY = h;
        if (bounds != null && !bounds.isEmpty()) {
            IntRect r = bounds.clipped(w, h);
            minX = r.x;
            minY = r.y;
            maxX = r.x + r.w;
            maxY = r.y + r.h;
        }
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len2 = dx * dx + dy * dy;
        boolean radial = shape == Shape.RADIAL;
        float radius = (float) Math.sqrt(Math.max(1e-4f, len2));
        Dither mode = dither == null ? Dither.BAYER4 : dither;
        float amt = Math.max(0f, Math.min(1f, ditherAmt));
        for (int y = minY; y < maxY; y++) {
            int row = y * w;
            for (int x = minX; x < maxX; x++) {
                if (clip != null && !clip.test(x, y)) continue;
                float t;
                if (radial) {
                    float ddx = (x + 0.5f) - x0;
                    float ddy = (y + 0.5f) - y0;
                    t = (float) Math.sqrt(ddx * ddx + ddy * ddy) / radius;
                } else if (len2 < 1e-4f) {
                    t = 0f;
                } else {
                    t = (((x + 0.5f) - x0) * dx + ((y + 0.5f) - y0) * dy) / len2;
                }
                t = t < 0f ? 0f : Math.min(1f, t);
                pixels[row + x] = sampleRamp(colors, t, x, y, mode, amt);
            }
        }
    }

    private static int sampleRamp(int[] ramp, float t, int x, int y, Dither dither, float amt) {
        int last = ramp.length - 1;
        if (dither == Dither.NONE || amt <= 0.001f) {
            return ramp[Math.round(t * last)];
        }
        float pos = t * last;
        int i0 = (int) pos;
        if (i0 >= last) return ramp[last];
        float f = pos - i0;
        float th = threshold(dither, x, y);
        float edge = 0.5f + (th - 0.5f) * amt;
        return f >= edge ? ramp[i0 + 1] : ramp[i0];
    }

    private static float threshold(Dither dither, int x, int y) {
        return switch (dither) {
            case BAYER2 -> (BAYER2[(y & 1) * 2 + (x & 1)] + 0.5f) / 4f;
            case BAYER4 -> (BAYER4[(y & 3) * 4 + (x & 3)] + 0.5f) / 16f;
            case BAYER8 -> (BAYER8[(y & 7) * 8 + (x & 7)] + 0.5f) / 64f;
            case HATCH -> {
                int diag = (x + y * 2) & 3;
                yield (diag + 0.5f) / 4f;
            }
            default -> 0.5f;
        };
    }

    /**
     * Shadows rotate toward blue-violet, highlights toward yellow;
     * midtones get extra saturation so the ramp does not go muddy.
     */
    private static void applyHueShift(float[] rgb, float oklabL, float shift) {
        if (shift <= 0.001f) return;
        float[] hsv = rgbToHsv(rgb[0], rgb[1], rgb[2]);
        float lum = oklabL;
        float cool = Math.max(0f, 0.55f - lum);
        float warm = Math.max(0f, lum - 0.55f);
        hsv[0] = lerpHue(hsv[0], 268f, cool * shift * 0.72f);
        hsv[0] = lerpHue(hsv[0], 48f, warm * shift * 0.55f);
        float mid = 1f - Math.abs(lum * 2f - 1f);
        hsv[1] = clamp01(hsv[1] + mid * 0.22f * shift);
        hsv[2] = clamp01(hsv[2] + (warm - cool) * 0.04f * shift);
        float[] out = hsvToRgb(hsv[0], hsv[1], hsv[2]);
        rgb[0] = out[0];
        rgb[1] = out[1];
        rgb[2] = out[2];
    }

    private static float lerpHue(float from, float to, float t) {
        t = clamp01(t);
        float d = ((to - from + 540f) % 360f) - 180f;
        return (from + d * t + 360f) % 360f;
    }

    private static float[] rgbToHsv(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0f;
        if (d > 1e-5f) {
            if (max == r) h = 60f * (((g - b) / d) % 6f);
            else if (max == g) h = 60f * (((b - r) / d) + 2f);
            else h = 60f * (((r - g) / d) + 4f);
            if (h < 0f) h += 360f;
        }
        float s = max <= 1e-5f ? 0f : d / max;
        return new float[]{h, s, max};
    }

    private static float[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        if (h < 60f) {
            r = c; g = x; b = 0f;
        } else if (h < 120f) {
            r = x; g = c; b = 0f;
        } else if (h < 180f) {
            r = 0f; g = c; b = x;
        } else if (h < 240f) {
            r = 0f; g = x; b = c;
        } else if (h < 300f) {
            r = x; g = 0f; b = c;
        } else {
            r = c; g = 0f; b = x;
        }
        return new float[]{clamp01(r + m), clamp01(g + m), clamp01(b + m)};
    }

    private static float[] srgbToOklab(float r, float g, float b) {
        float lr = srgbToLinear(r);
        float lg = srgbToLinear(g);
        float lb = srgbToLinear(b);
        float l = 0.4122214708f * lr + 0.5363325363f * lg + 0.0514459929f * lb;
        float m = 0.2119034982f * lr + 0.6806995451f * lg + 0.1073969566f * lb;
        float s = 0.0883024619f * lr + 0.2817188376f * lg + 0.6299787005f * lb;
        l = cbrt(l);
        m = cbrt(m);
        s = cbrt(s);
        return new float[]{
                0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
                1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
                0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
        };
    }

    private static float[] oklabToSrgb(float L, float a, float b) {
        float l = L + 0.3963377774f * a + 0.2158037573f * b;
        float m = L - 0.1055613458f * a - 0.0638541728f * b;
        float s = L - 0.0894841775f * a - 1.2914855480f * b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        float lr = +4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s;
        float lg = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s;
        float lb = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s;
        return new float[]{linearToSrgb(lr), linearToSrgb(lg), linearToSrgb(lb)};
    }

    private static float srgbToLinear(float c) {
        c = clamp01(c);
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    private static float linearToSrgb(float c) {
        if (c <= 0f) return 0f;
        if (c >= 1f) return 1f;
        return c <= 0.0031308f ? 12.92f * c : 1.055f * (float) Math.pow(c, 1.0 / 2.4) - 0.055f;
    }

    private static float cbrt(float v) {
        return (float) Math.cbrt(Math.max(0f, v));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}
