package org.sprautestudio.st_addon_spraute.editor.assets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns an ordinary Minecraft skin (64×64, or old 64×32) into a character texture: the skin stays where it is, and its
 * unused top-left 8×8 gets the parts the model animates. Where they go is the model's own layout: eye whites (6,4) 2×2,
 * pupils (2,4) and (5,4) 1×2, lids (0,3) (6,3) (0,6) (6,6) 2×1, brows (6,2) and (0,2) 2×1. They are taken from the face,
 * and on the face itself the eyes and brows are painted over with skin, since the game draws them.
 */
public final class FaceSkin {
    private FaceSkin() {
    }

    /** A picture that can be turned into a character texture. */
    public static boolean isSkin(BufferedImage img) {
        return img.getWidth() == 64 && (img.getHeight() == 64 || img.getHeight() == 32);
    }

    /** How the brows come out: thick 3×1 each, thin 2×1 each, arrows (2 up, 1 down-right), or none. */
    public enum Brows {
        THICK("толстые 3×1"), THIN("тонкие 2×1"), ARROW("стрелками"), NONE("без бровей");
        public final String title;

        Brows(String title) {
            this.title = title;
        }
    }

    /**
     * How the eyes sit on the skin and what to make. eyeH: 0 — find, 1 (2×1 eyes) or 2 (2×2); offset: rows
     * between the eyes' bottom and the face's bottom, -1 — find; still: 0 — both eyes move, 1 — the left
     * one stays painted on the face, 2 — the right one.
     */
    public static final class Options {
        public int eyeH = 0, offset = -1, still = 0;
        public Brows brows = Brows.THICK;
        public boolean eraseFace = true;

        public Options copy() {
            Options o = new Options();
            o.eyeH = eyeH;
            o.offset = offset;
            o.still = still;
            o.brows = brows;
            o.eraseFace = eraseFace;
            return o;
        }
    }

    public static BufferedImage convert(BufferedImage src) {
        return convert(src, new Options());
    }

    /** Where the eyes are: {top row, height} on the skin. */
    public static int[] findEyes(BufferedImage src, Options o) {
        int skin = skinColor(src);
        int h = o.eyeH, off = o.offset;
        if (h <= 0 || off < 0) {
            // eyes are where the whites are (light, greyish pixels in the outer columns); without whites —
            // the lowest run of "not skin" in the eyes' columns
            int bottom = -1, top = -1;
            for (int y = 15; y >= 9; y--) {
                boolean white = whitish(src.getRGB(9, y), skin) || whitish(src.getRGB(14, y), skin);
                if (white && bottom < 0) bottom = y;
                if (white) top = y;
                else if (bottom >= 0) break;
            }
            if (bottom < 0) {
                for (int y = 15; y >= 10; y--) {
                    boolean eye = false;
                    for (int x : new int[]{9, 10, 13, 14}) {
                        int c = src.getRGB(x, y);
                        if ((c >>> 24) > 0 && far(c, skin)) eye = true;
                    }
                    if (eye && bottom < 0) bottom = y;
                    if (eye) top = y;
                    else if (bottom >= 0) break;
                }
            }
            if (bottom < 0) {
                bottom = 13;
                top = 12;
            }
            if (off < 0) off = 15 - bottom;
            if (h <= 0) h = Math.min(2, bottom - top + 1);
        }
        h = Math.max(1, Math.min(2, h));
        int bottomRow = 15 - Math.max(0, Math.min(5, off));
        return new int[]{bottomRow - h + 1, h};
    }

    private static void put(BufferedImage out, int x, int y, int w, int h, int color) {
        for (int dy = 0; dy < h; dy++) for (int dx = 0; dx < w; dx++) out.setRGB(x + dx, y + dy, color);
    }

    public static BufferedImage convert(BufferedImage src, Options o) {
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int h = Math.min(64, src.getHeight());
        for (int y = 0; y < h; y++) for (int x = 0; x < 64; x++) out.setRGB(x, y, src.getRGB(x, y));
        if (src.getHeight() == 32) {
            // old skins have one leg and one arm: the left ones are copies
            copy(out, 0, 16, 16, 16, 16, 48);
            copy(out, 40, 16, 16, 16, 32, 48);
        }
        // the face: 8..15 × 8..15; left eye x 9..10 (pupil inside, at 10), right eye x 13..14 (pupil at 13)
        int[] eyes = findEyes(src, o);
        int eyeY = eyes[0], eyeH = eyes[1];
        int lOut = 9, lIn = 10, rIn = 13, rOut = 14;
        // a still eye gives nothing: the moving one gives both
        boolean leftMoves = o.still != 1, rightMoves = o.still != 2;
        int skin = skinColor(src);
        int bottom = eyeY + eyeH - 1;
        put(out, 0, 0, 8, 8, 0);
        // the whites: one patch both eyes share
        int white = opaque(src.getRGB(leftMoves ? lOut : rOut, bottom));
        put(out, 6, 4, 2, 2, white);
        // pupils, each from its own eye
        for (int dy = 0; dy < 2; dy++) {
            int sy = eyeY + Math.min(dy, eyeH - 1);
            out.setRGB(2, 4 + dy, opaque(src.getRGB(rightMoves ? rIn : lIn, sy)));
            out.setRGB(5, 4 + dy, opaque(src.getRGB(leftMoves ? lIn : rIn, sy)));
        }
        // lids are skin: they show when the eyes close
        put(out, 0, 3, 2, 1, skin);
        put(out, 6, 3, 2, 1, skin);
        put(out, 0, 6, 2, 1, skin);
        put(out, 6, 6, 2, 1, skin);
        // brows: the row above the eyes
        int browY = eyeY - 1, browColor = 0;
        for (int i = 0; i < 6; i++) {
            int c = src.getRGB(lOut + i, browY);
            if ((c >>> 24) > 0 && far(c, skin) && browColor == 0) browColor = opaque(c);
        }
        if (browColor == 0) browColor = darker(skin);
        if (o.brows != Brows.NONE) {
            put(out, 6, 2, 2, 1, browColor);
            put(out, 0, 2, 2, 1, browColor);
        }
        // the face without what the game draws: the moving eyes and the brows
        if (o.eraseFace) {
            for (int x = lOut; x <= rOut; x++) {
                if (o.brows != Brows.NONE && browY >= 8 && far(src.getRGB(x, browY), skin)) {
                    boolean overStill = (o.still == 1 && x <= lIn) || (o.still == 2 && x >= rIn);
                    if (!overStill) out.setRGB(x, browY, skin);
                }
                boolean eyeCol = (leftMoves && (x == lOut || x == lIn)) || (rightMoves && (x == rIn || x == rOut));
                if (eyeCol) for (int dy = 0; dy < eyeH; dy++) out.setRGB(x, eyeY + dy, skin);
            }
        }
        return out;
    }

    /** Converts a file into the Spraute format next to it ("<name> face.png"); returns the new file. */
    public static Path convertFile(Path source, Path targetDir, String name) throws IOException {
        return convertFile(source, targetDir, name, new Options());
    }

    public static BufferedImage read(Path source) throws IOException {
        BufferedImage img = ImageIO.read(source.toFile());
        if (img == null) throw new IOException("Не картинка");
        if (!isSkin(img)) throw new IOException("Нужен скин Minecraft 64×64 (или 64×32), а тут " + img.getWidth() + "×" + img.getHeight());
        return img;
    }

    public static Path convertFile(Path source, Path targetDir, String name, Options o) throws IOException {
        BufferedImage img = ImageIO.read(source.toFile());
        if (img == null) throw new IOException("Не картинка");
        if (!isSkin(img)) throw new IOException("Нужен скин Minecraft 64×64 (или 64×32), а тут " + img.getWidth() + "×" + img.getHeight());
        Files.createDirectories(targetDir);
        Path out = targetDir.resolve(name + ".png");
        int n = 2;
        while (Files.exists(out)) out = targetDir.resolve(name + " " + n++ + ".png");
        ImageIO.write(convert(img, o), "png", out.toFile());
        return out;
    }

    // ------------------------------------------------------------------ colours

    /** The commonest colour of the cheeks and chin (the face below the eyes). */
    static int skinColor(BufferedImage src) {
        Map<Integer, Integer> count = new HashMap<>();
        for (int y = 14; y < 16; y++) for (int x = 8; x < 16; x++) {
            int c = src.getRGB(x, y);
            if ((c >>> 24) > 0) count.merge(c | 0xFF000000, 1, Integer::sum);
        }
        for (int x = 8; x < 16; x++) {
            int c = src.getRGB(x, 11);
            if ((c >>> 24) > 0) count.merge(c | 0xFF000000, 1, Integer::sum);
        }
        return count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0xFFBC876F);
    }

    private static boolean whitish(int c, int skin) {
        if ((c >>> 24) == 0 || !far(c, skin)) return false;
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return Math.min(r, Math.min(g, b)) > 140 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 45;
    }

    private static boolean far(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
        return dr * dr + dg * dg + db * db > 40 * 40;
    }

    private static int darker(int c) {
        int r = (int) (((c >> 16) & 255) * 0.35), g = (int) (((c >> 8) & 255) * 0.3), b = (int) ((c & 255) * 0.3);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int opaque(int c) {
        return c | 0xFF000000;
    }

    private static void copy(BufferedImage img, int sx, int sy, int w, int h, int dx, int dy) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(dx + x, dy + y, img.getRGB(sx + x, sy + y));
    }

    /** For trying it by hand: java FaceSkin skin.png out.png */
    public static void main(String[] a) throws IOException {
        Options o = new Options();
        if (a.length > 2) o.eyeH = Integer.parseInt(a[2]);
        if (a.length > 3) o.offset = Integer.parseInt(a[3]);
        if (a.length > 4) o.still = Integer.parseInt(a[4]);
        if (a.length > 5) o.brows = Brows.valueOf(a[5]);
        ImageIO.write(convert(ImageIO.read(Path.of(a[0]).toFile()), o), "png", Path.of(a[1]).toFile());
    }
}
