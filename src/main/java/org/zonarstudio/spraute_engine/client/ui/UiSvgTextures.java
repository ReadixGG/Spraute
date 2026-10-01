package org.zonarstudio.spraute_engine.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.zonarstudio.spraute_engine.client.editor.icons.LucideSvgRasterizer;
import org.zonarstudio.spraute_engine.resource.WorkspaceContent;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Workspace {@code .svg} → cached {@link DynamicTexture} for GUI widgets. */
public final class UiSvgTextures {
    private static final int MAX_CACHE = 48;
    private static final int MIN_RASTER = 32;
    private static final int MAX_RASTER = 512;
    private static final Map<Key, Slot> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static int nextId;

    public record Handle(ResourceLocation location, int width, int height) {}

    private record Key(String path, long mtime, int w, int h,
                       String color, String stroke, int strokeBits, boolean recolor) {}

    private record Slot(ResourceLocation location, DynamicTexture texture, int width, int height) {}

    private UiSvgTextures() {}

    public static Handle get(String source, int widgetW, int widgetH,
                             String color, String stroke, float strokeWidth, boolean recolor) {
        String path = normalize(source);
        if (path.isEmpty() || !path.toLowerCase(Locale.ROOT).endsWith(".svg")) return null;
        Path file = WorkspaceContent.resolve(path);
        if (file == null || !Files.isRegularFile(file)) return null;
        int[] raster = rasterSize(widgetW, widgetH);
        long mtime = 0L;
        try {
            mtime = Files.getLastModifiedTime(file).toMillis();
        } catch (Exception ignored) {
        }
        String col = color == null ? "" : color.trim();
        String strk = stroke == null ? "" : stroke.trim();
        Key key = new Key(path, mtime, raster[0], raster[1], col, strk,
                Float.floatToIntBits(strokeWidth), recolor);
        Slot slot = CACHE.get(key);
        if (slot != null) return new Handle(slot.location, slot.width, slot.height);
        Slot created = load(file, raster[0], raster[1], col, strk, strokeWidth, recolor);
        if (created == null) return null;
        CACHE.put(key, created);
        evict();
        return new Handle(created.location, created.width, created.height);
    }

    public static void clear() {
        for (Slot slot : CACHE.values()) {
            release(slot);
        }
        CACHE.clear();
    }

    private static Slot load(Path file, int rw, int rh,
                             String color, String stroke, float strokeWidth, boolean recolor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        try {
            String svg = Files.readString(file, StandardCharsets.UTF_8);
            Color current = LucideSvgRasterizer.parseMcColor(color);
            Color strokeColor = LucideSvgRasterizer.parseMcColor(stroke);
            Float width = strokeWidth > 0.001f ? strokeWidth : null;
            BufferedImage img = LucideSvgRasterizer.rasterize(svg, rw, rh,
                    LucideSvgRasterizer.Paint.gui(current, strokeColor, width, recolor));
            if (img == null) return null;
            NativeImage nativeImage = toNative(img);
            DynamicTexture texture = new DynamicTexture(nativeImage);
            texture.setFilter(true, false);
            int id = ++nextId;
            ResourceLocation loc = mc.getTextureManager().register("spraute_svg_" + Integer.toHexString(id), texture);
            return new Slot(loc, texture, img.getWidth(), img.getHeight());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void evict() {
        Iterator<Map.Entry<Key, Slot>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_CACHE && it.hasNext()) {
            Slot old = it.next().getValue();
            it.remove();
            release(old);
        }
    }

    private static void release(Slot slot) {
        if (slot == null || slot.location == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.getTextureManager().release(slot.location);
        } catch (Exception ignored) {
        }
    }

    private static int[] rasterSize(int w, int h) {
        int ww = Math.max(1, w);
        int hh = Math.max(1, h);
        int max = Math.max(ww, hh);
        int raster = Math.max(MIN_RASTER, Math.min(MAX_RASTER, max * 2));
        if (ww >= hh) {
            int rh = Math.max(8, Math.round(raster * (hh / (float) ww)));
            return new int[]{raster, rh};
        }
        int rw = Math.max(8, Math.round(raster * (ww / (float) hh)));
        return new int[]{rw, raster};
    }

    private static String normalize(String source) {
        if (source == null) return "";
        String path = source.replace('\\', '/').trim();
        while (path.startsWith("./")) path = path.substring(2);
        if (path.startsWith("/")) path = path.substring(1);
        return path;
    }

    private static NativeImage toNative(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        NativeImage ni = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                ni.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return ni;
    }
}
