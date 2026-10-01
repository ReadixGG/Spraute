package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.zonarstudio.spraute_engine.scene.PlayerSkinLayers;

/**
 * Steve (wide, 4 px arms) vs Alex (slim, 3 px arms) UV remap for 64×64 skins
 * and integer multiples (128×128, …).
 */
@OnlyIn(Dist.CLIENT)
public final class PlayerSkinLayout {
    private PlayerSkinLayout() {}

    /**
     * Clears second-layer UV (hat / jacket / sleeves / pants) so those pixels
     * do not render. Cape is a separate texture and is ignored here.
     */
    public static void clearOuter(NativeImage image, int hiddenMask) {
        if (image == null || hiddenMask == 0) return;
        int s = unit(image);
        if (s <= 0) return;
        int mask = hiddenMask & PlayerSkinLayers.CLOTHING;
        if ((mask & PlayerSkinLayers.HAT) != 0) clear(image, 32 * s, 0, 32 * s, 16 * s);
        if ((mask & PlayerSkinLayers.JACKET) != 0) clear(image, 16 * s, 32 * s, 24 * s, 16 * s);
        if ((mask & PlayerSkinLayers.RIGHT_SLEEVE) != 0) clear(image, 40 * s, 32 * s, 16 * s, 16 * s);
        if ((mask & PlayerSkinLayers.RIGHT_PANTS) != 0) clear(image, 0, 32 * s, 16 * s, 16 * s);
        if ((mask & PlayerSkinLayers.LEFT_PANTS) != 0) clear(image, 0, 48 * s, 16 * s, 16 * s);
        if ((mask & PlayerSkinLayers.LEFT_SLEEVE) != 0) clear(image, 48 * s, 48 * s, 16 * s, 16 * s);
    }

    public static boolean isSlim(NativeImage image) {
        if (image == null) return false;
        int s = unit(image);
        if (s <= 0) return false;
        // Extra 2 px of the wide back-face sit unused on slim arms.
        return isClear(image, 54 * s, 20 * s, 2 * s, 12 * s)
                && isClear(image, 46 * s, 52 * s, 2 * s, 12 * s);
    }

    /**
     * Rewrites arm UV of {@code image} to {@code destSlim}. Returns the same
     * instance when already matching, otherwise a new image (caller closes the old).
     */
    public static NativeImage matchModel(NativeImage image, boolean destSlim) {
        if (image == null) return null;
        if (isSlim(image) == destSlim) return image;
        NativeImage out = new NativeImage(image.getWidth(), image.getHeight(), true);
        copyAll(image, out);
        remapArms(image, out, destSlim);
        return out;
    }

    private static void remapArms(NativeImage src, NativeImage dest, boolean destSlim) {
        int s = unit(src);
        if (s <= 0) return;
        remapArm(src, dest, 40 * s, 16 * s, s, destSlim);
        remapArm(src, dest, 40 * s, 32 * s, s, destSlim);
        remapArm(src, dest, 32 * s, 48 * s, s, destSlim);
        remapArm(src, dest, 48 * s, 48 * s, s, destSlim);
    }

    /**
     * One 16×16 arm box. Wide faces are packed as R/F/L/B of 4+4+4+4;
     * slim as 4+3+4+3 with the extra columns left transparent.
     */
    private static void remapArm(NativeImage src, NativeImage dest,
                                 int ox, int oy, int s, boolean destSlim) {
        clear(dest, ox, oy, 16 * s, 16 * s);
        boolean srcSlim = isSlimArm(src, ox, oy, s);
        if (srcSlim == destSlim) {
            copyRegion(src, dest, ox, oy, 16 * s, 16 * s, ox, oy);
            return;
        }
        if (srcSlim) {
            // slim → wide: expand 3 px fronts/backs, shift left/back by 1 unit
            copyRegion(src, dest, ox, oy + 4 * s, 4 * s, 12 * s, ox, oy + 4 * s);
            copyAndWiden(src, dest, ox + 4 * s, oy + 4 * s, 3 * s, 12 * s, ox + 4 * s, oy + 4 * s, s);
            copyRegion(src, dest, ox + 7 * s, oy + 4 * s, 4 * s, 12 * s, ox + 8 * s, oy + 4 * s);
            copyAndWiden(src, dest, ox + 11 * s, oy + 4 * s, 3 * s, 12 * s, ox + 12 * s, oy + 4 * s, s);
            copyAndWiden(src, dest, ox + 4 * s, oy, 3 * s, 4 * s, ox + 4 * s, oy, s);
            copyAndWiden(src, dest, ox + 7 * s, oy, 3 * s, 4 * s, ox + 8 * s, oy, s);
        } else {
            // wide → slim: drop the last unit of 4 px fronts/backs, pack left
            copyRegion(src, dest, ox, oy + 4 * s, 4 * s, 12 * s, ox, oy + 4 * s);
            copyRegion(src, dest, ox + 4 * s, oy + 4 * s, 3 * s, 12 * s, ox + 4 * s, oy + 4 * s);
            copyRegion(src, dest, ox + 8 * s, oy + 4 * s, 4 * s, 12 * s, ox + 7 * s, oy + 4 * s);
            copyRegion(src, dest, ox + 12 * s, oy + 4 * s, 3 * s, 12 * s, ox + 11 * s, oy + 4 * s);
            copyRegion(src, dest, ox + 4 * s, oy, 3 * s, 4 * s, ox + 4 * s, oy);
            copyRegion(src, dest, ox + 8 * s, oy, 3 * s, 4 * s, ox + 7 * s, oy);
        }
    }

    private static boolean isSlimArm(NativeImage image, int ox, int oy, int s) {
        return isClear(image, ox + 14 * s, oy + 4 * s, 2 * s, 12 * s);
    }

    private static void copyAndWiden(NativeImage src, NativeImage dest,
                                     int sx, int sy, int w, int h, int dx, int dy, int s) {
        copyRegion(src, dest, sx, sy, w, h, dx, dy);
        copyRegion(src, dest, sx + w - s, sy, s, h, dx + w, dy);
    }

    private static int unit(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w < 64 || h < 32 || w % 64 != 0) return 0;
        return w / 64;
    }

    private static boolean isClear(NativeImage image, int x, int y, int w, int h) {
        int maxX = Math.min(x + w, image.getWidth());
        int maxY = Math.min(y + h, image.getHeight());
        if (x >= maxX || y >= maxY) return true;
        for (int py = y; py < maxY; py++) {
            for (int px = x; px < maxX; px++) {
                if (((image.getPixelRGBA(px, py) >> 24) & 0xFF) != 0) return false;
            }
        }
        return true;
    }

    private static void clear(NativeImage image, int x, int y, int w, int h) {
        int maxX = Math.min(x + w, image.getWidth());
        int maxY = Math.min(y + h, image.getHeight());
        for (int py = y; py < maxY; py++) {
            for (int px = x; px < maxX; px++) {
                image.setPixelRGBA(px, py, 0);
            }
        }
    }

    private static void copyAll(NativeImage src, NativeImage dest) {
        copyRegion(src, dest, 0, 0, Math.min(src.getWidth(), dest.getWidth()),
                Math.min(src.getHeight(), dest.getHeight()), 0, 0);
    }

    private static void copyRegion(NativeImage src, NativeImage dest,
                                   int sx, int sy, int w, int h, int dx, int dy) {
        int maxW = Math.min(w, Math.min(src.getWidth() - sx, dest.getWidth() - dx));
        int maxH = Math.min(h, Math.min(src.getHeight() - sy, dest.getHeight() - dy));
        if (maxW <= 0 || maxH <= 0) return;
        for (int y = 0; y < maxH; y++) {
            for (int x = 0; x < maxW; x++) {
                dest.setPixelRGBA(dx + x, dy + y, src.getPixelRGBA(sx + x, sy + y));
            }
        }
    }
}
