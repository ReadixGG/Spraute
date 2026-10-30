package org.sprautestudio.st_addon_spraute.engine.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages;
import org.sprautestudio.st_addon_spraute.engine.EngineConfig;
import org.sprautestudio.st_addon_spraute.runtime.Images;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Draws {@link EngineConfig.Skin}s: stretched or as a 9-slice. */
public final class Skins {
    private Skins() {}

    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();

    public static ResourceLocation texture(String project, EngineConfig.Skin skin) {
        return Images.texture(project, skin.image);
    }

    /** Pixel size of a texture: project pictures are known, resource textures are read once. */
    public static int[] size(ResourceLocation rl) {
        int[] s = ProjectImages.size(rl);
        if (s[0] > 0) return s;
        return SIZES.computeIfAbsent(rl, k -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(k); NativeImage img = NativeImage.read(in)) {
                return new int[]{img.getWidth(), img.getHeight()};
            } catch (Exception e) {
                return new int[]{0, 0};
            }
        });
    }

    public static boolean exists(ResourceLocation rl) {
        return size(rl)[0] > 0;
    }

    /**
     * A part of a sheet laid out like Spraute's (u, v, w, h in a tw × th sheet): with the setting's own
     * picture, the same part of it — proportionally, so a sheet drawn 2× or 4× bigger works too; else the default.
     */
    public static void drawSheet(GuiGraphics g, String project, EngineConfig.Skin skin, net.minecraft.resources.ResourceLocation def,
                                 int x, int y, int w, int h, int u, int v, int tw, int th) {
        if (skin == null || !skin.isSet()) {
            g.blit(def, x, y, u, v, w, h, tw, th);
            return;
        }
        net.minecraft.resources.ResourceLocation rl = texture(project, skin);
        int[] sz = size(rl);
        int rw = sz != null && sz[0] > 0 ? sz[0] : tw, rh = sz != null && sz[1] > 0 ? sz[1] : th;
        float kx = rw / (float) tw, ky = rh / (float) th;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(rl, x, y, w, h, u * kx, v * ky, Math.round(w * kx), Math.round(h * ky), rw, rh);
    }

    public static void draw(GuiGraphics g, String project, EngineConfig.Skin skin, int x, int y, int w, int h) {
        if (skin == null || !skin.isSet()) return;
        draw(g, texture(project, skin), skin, x, y, w, h);
    }

    /** Draws a texture into the rectangle, as a 9-slice when the skin says so. */
    public static void draw(GuiGraphics g, ResourceLocation rl, EngineConfig.Skin skin, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        int[] s = size(rl);
        int tw = s[0], th = s[1];
        if (tw <= 0 || th <= 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (skin == null || !skin.nine) {
            g.blit(rl, x, y, w, h, 0, 0, tw, th, tw, th);
            return;
        }
        int l = clamp(skin.left, 0, tw / 2), r = clamp(skin.right, 0, tw - l - 1);
        int t = clamp(skin.top, 0, th / 2), b = clamp(skin.bottom, 0, th - t - 1);
        float k = skin.scale <= 0 ? 1 : skin.scale;
        // screen sizes of the borders, shrunk when the rectangle is smaller than both borders
        int dl = Math.round(l * k), dr = Math.round(r * k), dt = Math.round(t * k), db = Math.round(b * k);
        if (dl + dr > w) {
            float f = w / (float) (dl + dr);
            dl = (int) (dl * f);
            dr = w - dl;
        }
        if (dt + db > h) {
            float f = h / (float) (dt + db);
            dt = (int) (dt * f);
            db = h - dt;
        }
        int mw = w - dl - dr, mh = h - dt - db;
        int cw = tw - l - r, ch = th - t - b;
        int[] dxs = {x, x + dl, x + w - dr}, dws = {dl, mw, dr};
        int[] dys = {y, y + dt, y + h - db}, dhs = {dt, mh, db};
        int[] us = {0, l, tw - r}, uws = {l, cw, r};
        int[] vs = {0, t, th - b}, vhs = {t, ch, b};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (dws[col] <= 0 || dhs[row] <= 0 || uws[col] <= 0 || vhs[row] <= 0) continue;
                g.blit(rl, dxs[col], dys[row], dws[col], dhs[row], us[col], vs[row], uws[col], vhs[row], tw, th);
            }
        }
    }

    /** Left part of a skin, for bars: the visible width follows {@code fraction}. */
    public static void drawFill(GuiGraphics g, String project, EngineConfig.Skin skin, int x, int y, int w, int h, float fraction) {
        if (skin == null || !skin.isSet() || fraction <= 0) return;
        ResourceLocation rl = texture(project, skin);
        int vis = Math.round(w * Math.min(1, fraction));
        if (skin.nine) {
            draw(g, rl, skin, x, y, vis, h);
            return;
        }
        // stretched: crop the texture instead of squeezing it
        int[] s = size(rl);
        if (s[0] <= 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int uw = Math.max(1, Math.round(s[0] * Math.min(1, fraction)));
        g.blit(rl, x, y, vis, h, 0, 0, uw, s[1], s[0], s[1]);
    }

    /** A plain texture cropped from the left to {@code fraction} of its width. */
    public static void drawFillTexture(GuiGraphics g, ResourceLocation rl, int x, int y, int w, int h, float fraction) {
        int[] s = size(rl);
        if (s[0] <= 0 || fraction <= 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float f = Math.min(1, fraction);
        g.blit(rl, x, y, Math.round(w * f), h, 0, 0, Math.max(1, Math.round(s[0] * f)), s[1], s[0], s[1]);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
