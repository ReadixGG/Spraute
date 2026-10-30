package org.sprautestudio.st_addon_spraute.fx.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.runtime.Images;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Particle sprites drawn in code (no files): soft glow, spark streak, smoke puff, ring, star, lens
 * flare, beam, hard circle and square. White with the shape in the alpha, tinted by the particle.
 */
public final class FxTextures {
    private FxTextures() {
    }

    public static final List<String> BUILT_IN = List.of("glow", "spark", "smoke", "ring", "star", "flare", "beam", "circle", "square");
    private static final Map<String, ResourceLocation> MADE = new HashMap<>();
    private static final int N = 64;

    public static ResourceLocation get(String name, String project) {
        if (name != null && name.startsWith("image:")) return Images.texture(project == null ? "" : project, name.substring(6));
        String key = name == null || !BUILT_IN.contains(name) ? "glow" : name;
        ResourceLocation rl = MADE.get(key);
        if (rl != null) return rl;
        NativeImage img = new NativeImage(key.equals("smoke") ? 128 : N, key.equals("smoke") ? 128 : N, false);
        draw(key, img);
        rl = new ResourceLocation(St_addon_spraute.MODID, "fx/" + key);
        DynamicTexture tex = new DynamicTexture(img);
        tex.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(rl, tex);
        MADE.put(key, rl);
        return rl;
    }

    private static void put(NativeImage img, int x, int y, float a) {
        int al = Math.max(0, Math.min(255, Math.round(a * 255)));
        img.setPixelRGBA(x, y, (al << 24) | 0xFFFFFF);
    }

    private static void draw(String key, NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        Random rnd = new Random(key.hashCode());
        // value noise for smoke
        float[][] grid = new float[9][9];
        for (float[] row : grid) for (int i = 0; i < row.length; i++) row[i] = rnd.nextFloat();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float u = (x + 0.5f) / w * 2 - 1, v = (y + 0.5f) / h * 2 - 1;
                float r = (float) Math.sqrt(u * u + v * v);
                float a;
                switch (key) {
                    case "spark" -> {
                        // a streak along U: bright thin core, soft edges, tapering ends
                        float across = Math.abs(v), along = Math.abs(u);
                        a = (float) (Math.exp(-across * across * 60) * (1 - Math.pow(along, 3)));
                        a += (float) (Math.exp(-across * across * 8) * 0.35 * (1 - along));
                    }
                    case "smoke" -> {
                        float n = noise(grid, (u + 1) * 4, (v + 1) * 4) * 0.6f + noise(grid, (u + 1) * 8 % 8, (v + 1) * 8 % 8) * 0.4f;
                        float edge = (float) Math.max(0, 1 - Math.pow(r, 1.6));
                        a = Math.max(0, edge * (0.55f + 0.7f * n) - 0.12f);
                        a = Math.min(1, a * 1.2f);
                    }
                    case "ring" -> {
                        float d = (r - 0.78f) / 0.09f;
                        a = (float) Math.exp(-d * d) + (float) Math.exp(-Math.pow((r - 0.78f) / 0.25f, 2)) * 0.25f;
                        if (r > 1) a = 0;
                    }
                    case "star" -> {
                        float cross = (float) (Math.exp(-Math.abs(u) * 14) * (1 - Math.abs(v)) + Math.exp(-Math.abs(v) * 14) * (1 - Math.abs(u)));
                        a = Math.min(1, cross * 1.3f + (float) Math.exp(-r * r * 18));
                    }
                    case "flare" -> {
                        float core = (float) Math.exp(-r * r * 30);
                        float halo = (float) Math.exp(-r * r * 3.2) * 0.55f;
                        float streak = (float) (Math.exp(-Math.abs(v) * 40) * Math.max(0, 1 - Math.abs(u)) * 0.6);
                        a = Math.min(1, core + halo + streak);
                    }
                    case "beam" -> {
                        float across = Math.abs(u);
                        a = (float) (Math.exp(-across * across * 10) * 0.75 + Math.exp(-across * across * 70) * 0.5);
                        a = Math.min(1, a);
                    }
                    case "circle" -> a = Math.max(0, Math.min(1, (1 - r) * w / 4f));
                    case "square" -> a = Math.max(Math.abs(u), Math.abs(v)) < 0.92f ? 1 : 0;
                    default -> a = (float) Math.exp(-r * r * 4.5) * (r < 1 ? 1 : 0);
                }
                put(img, x, y, Math.max(0, Math.min(1, a)));
            }
        }
    }

    private static float noise(float[][] g, float x, float y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float xf = x - xi, yf = y - yi;
        xi = Math.floorMod(xi, 8);
        yi = Math.floorMod(yi, 8);
        float sx = xf * xf * (3 - 2 * xf), sy = yf * yf * (3 - 2 * yf);
        float a = g[yi][xi], b = g[yi][xi + 1], c = g[yi + 1][xi], d = g[yi + 1][xi + 1];
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sy;
    }
}
