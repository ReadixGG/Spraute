package org.sprautestudio.st_addon_spraute.editor.assets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns an ordinary Minecraft skin (64×64, or old 64×32) into a Spraute character texture
 * (128×64): the skin on the left, and in its unused top-left 8×8 the parts Spraute animates —
 * pupils (0,0) 2×2, eye whites (0,2) and (4,2) 2×2, brows (0,5) 6×1, teeth (6,6) and mouth (6,7) 2×1.
 * They are taken from the face: eyes 2×2 two pixels above its bottom, pupils on their inner side,
 * brows right above them; on the face itself the eyes and brows are painted over with skin, since
 * the game draws them.
 */
public final class StSkin {
    private StSkin() {
    }

    public static final int TEETH = 0xFFACA7A0, MOUTH = 0xFF7F484B;

    /** A picture already in Spraute's format (128×64), or one that can be turned into it. */
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

    public static BufferedImage convert(BufferedImage src, Options o) {
        BufferedImage out = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
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
        int pupilX = leftMoves ? lIn : rIn;
        int whiteL = leftMoves ? lOut : rOut, whiteR = rightMoves ? rOut : lOut;
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) out.setRGB(x, y, 0);
        for (int dy = 0; dy < 2; dy++) {
            int sy = eyeY + Math.min(dy, eyeH - 1);
            int pupil = opaque(src.getRGB(pupilX, sy));
            out.setRGB(0, dy, pupil);
            out.setRGB(1, dy, pupil);
            int l = opaque(src.getRGB(whiteL, sy)), r = opaque(src.getRGB(whiteR, sy));
            out.setRGB(0, 2 + dy, l);
            out.setRGB(1, 2 + dy, l);
            out.setRGB(4, 2 + dy, r);
            out.setRGB(5, 2 + dy, r);
        }
        // brows: the row above the eyes
        int skin = skinColor(src);
        int browY = eyeY - 1;
        int[] browSrc = new int[6];
        int browColor = 0;
        for (int i = 0; i < 6; i++) {
            int c = src.getRGB(lOut + i, browY);
            if ((c >>> 24) > 0 && far(c, skin)) {
                browSrc[i] = opaque(c);
                if (browColor == 0) browColor = opaque(c);
            }
        }
        if (browColor == 0) browColor = darker(skin);
        for (int i = 0; i < 6; i++) if (browSrc[i] == 0) browSrc[i] = browColor;
        switch (o.brows) {
            case THICK -> {
                for (int i = 0; i < 6; i++) out.setRGB(i, 5, browSrc[i]);
            }
            case THIN -> {
                for (int i : new int[]{0, 1, 3, 4}) out.setRGB(i, 5, browSrc[i]);
            }
            case ARROW -> {
                for (int i : new int[]{0, 1, 3, 4}) out.setRGB(i, 5, browSrc[i]);
                out.setRGB(2, 6, browSrc[2]);
                out.setRGB(5, 6, browSrc[5]);
            }
            case NONE -> {
            }
        }
        for (int x = 6; x < 8; x++) {
            out.setRGB(x, 6, TEETH);
            out.setRGB(x, 7, MOUTH);
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

    /** Converts a file into the Spraute format next to it ("<name> ST.png"); returns the new file. */
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

    /** For trying it by hand: java StSkin skin.png out.png */
    public static void main(String[] a) throws IOException {
        Options o = new Options();
        if (a.length > 2) o.eyeH = Integer.parseInt(a[2]);
        if (a.length > 3) o.offset = Integer.parseInt(a[3]);
        if (a.length > 4) o.still = Integer.parseInt(a[4]);
        if (a.length > 5) o.brows = Brows.valueOf(a[5]);
        ImageIO.write(convert(ImageIO.read(Path.of(a[0]).toFile()), o), "png", Path.of(a[1]).toFile());
    }
}
