package org.sprautestudio.spraute_engine.editor.assets;

import java.awt.image.BufferedImage;

/** The Super Rig keeps Minecraft's body UVs and places its animated face pieces on a 128×128 atlas. */
public final class SuperRigSkin {
    private SuperRigSkin() {
    }

    public static BufferedImage convert(BufferedImage src, FaceSkin.Options options) {
        BufferedImage out = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++) out.setRGB(x, y, src.getRGB(x, y));
        if (src.getHeight() == 32) {
            copy(out, 0, 16, 16, 16, 16, 48);
            copy(out, 40, 16, 16, 16, 32, 48);
        }

        int[] eyes = FaceSkin.findEyes(src, options);
        int eyeY = eyes[0], eyeH = eyes[1], bottom = eyeY + eyeH - 1;
        int skin = FaceSkin.skinColor(src);
        boolean existing = src.getWidth() == 128;
        if (!existing || !hasPixels(src, 116, 61, 12, 3)) {
            int white = opaque(src.getRGB(options.still == 1 ? 14 : 9, bottom));
            int shade = shade(white, .84f);
            for (int x = 116; x < 128; x++) {
                out.setRGB(x, 61, white);
                out.setRGB(x, 62, white);
                out.setRGB(x, 63, shade);
            }
        }
        if (!existing || !hasPixels(src, 120, 0, 8, 3)) {
            int left = opaque(src.getRGB(options.still == 1 ? 13 : 10, bottom));
            int right = opaque(src.getRGB(options.still == 2 ? 10 : 13, bottom));
            for (int x = 120; x < 128; x++) {
                int color = x < 124 ? right : left;
                out.setRGB(x, 1, color);
                out.setRGB(x, 2, shade(color, .8f));
            }
            for (int x : new int[]{121, 122}) out.setRGB(x, 0, right);
            for (int x : new int[]{125, 126}) out.setRGB(x, 0, left);
        }
        if (!existing || !hasPixels(src, 0, 0, 3, 6)) {
            clear(out, 0, 0, 3, 6);
            int brow = browColor(src, eyeY - 1, skin);
            switch (options.brows) {
                case THICK -> {
                    fill(out, 0, 0, 3, 1, brow);
                    fill(out, 0, 3, 3, 1, brow);
                }
                case THIN -> {
                    fill(out, 1, 0, 2, 1, brow);
                    fill(out, 0, 3, 2, 1, brow);
                }
                case ARROW -> {
                    fill(out, 0, 0, 2, 1, brow);
                    out.setRGB(2, 1, brow);
                    fill(out, 1, 3, 2, 1, brow);
                    out.setRGB(0, 4, brow);
                }
                case NONE -> { }
            }
        }
        if (!existing || !hasPixels(src, 4, 4, 4, 4)) {
            clear(out, 4, 4, 4, 4);
            int lip = shade(skin, .72f);
            int inner = 0xFF000000 | Math.max(20, ((skin >> 16) & 255) / 2) << 16
                    | Math.max(8, ((skin >> 8) & 255) / 5) << 8 | Math.max(8, (skin & 255) / 5);
            out.setRGB(4, 5, 0xFFF0ECE4);
            fill(out, 4, 6, 4, 2, inner);
            fill(out, 5, 7, 2, 1, lip);
        }
        if (options.eraseFace) {
            FaceSkin.Options faceOptions = options.copy();
            faceOptions.target = FaceSkin.Target.SPRAUTE;
            BufferedImage face = FaceSkin.convert(src, faceOptions);
            copyFace(face, out);
        }
        return out;
    }

    private static void copyFace(BufferedImage src, BufferedImage out) {
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) out.setRGB(x, y, src.getRGB(x, y));
    }

    private static void copy(BufferedImage img, int sx, int sy, int w, int h, int dx, int dy) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(dx + x, dy + y, img.getRGB(sx + x, sy + y));
    }

    private static boolean hasPixels(BufferedImage img, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++)
            if ((img.getRGB(xx, yy) >>> 24) != 0) return true;
        return false;
    }

    private static void clear(BufferedImage img, int x, int y, int w, int h) {
        fill(img, x, y, w, h, 0);
    }

    private static void fill(BufferedImage img, int x, int y, int w, int h, int color) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) img.setRGB(xx, yy, color);
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    private static int shade(int color, float factor) {
        return 0xFF000000 | Math.round(((color >> 16) & 255) * factor) << 16
                | Math.round(((color >> 8) & 255) * factor) << 8 | Math.round((color & 255) * factor);
    }

    private static int browColor(BufferedImage src, int y, int skin) {
        for (int x : new int[]{9, 10, 13, 14}) {
            int color = src.getRGB(x, Math.max(8, y));
            if ((color >>> 24) != 0 && colorDistance(color, skin) > 40 * 40) return opaque(color);
        }
        return shade(skin, .4f);
    }

    private static int colorDistance(int a, int b) {
        int r = ((a >> 16) & 255) - ((b >> 16) & 255);
        int g = ((a >> 8) & 255) - ((b >> 8) & 255);
        int blue = (a & 255) - (b & 255);
        return r * r + g * g + blue * blue;
    }
}
