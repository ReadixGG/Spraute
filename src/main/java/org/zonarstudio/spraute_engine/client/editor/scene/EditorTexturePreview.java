package org.zonarstudio.spraute_engine.client.editor.scene;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Workspace PNG/JPG previews for ImGui. Full files are downscaled and capped so
 * opening a texture combo cannot keep every atlas in RAM.
 */
public final class EditorTexturePreview {
    public record Tex(int glId, int width, int height) {}

    private static final int MAX_EDGE = 256;
    private static final int MAX_CACHE = 48;

    private record Slot(Tex tex, DynamicTexture texture) {}

    private static final LinkedHashMap<String, Slot> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Slot> eldest) {
            if (size() <= MAX_CACHE) return false;
            close(eldest.getValue());
            return true;
        }
    };
    private static final Set<String> MISSING = new HashSet<>();

    private EditorTexturePreview() {}

    /** Already-decoded preview, or null. Does not touch the disk. */
    public static Tex cached(String relPath) {
        Slot slot = CACHE.get(normalizeKey(relPath));
        return slot == null ? null : slot.tex;
    }

    /** Texture for a workspace-relative path ("textures/foo.png"); null if it can't be loaded. */
    public static Tex get(String relPath) {
        String key = normalizeKey(relPath);
        if (key == null) return null;
        if (MISSING.contains(key)) return null;
        Slot slot = CACHE.get(key);
        if (slot != null) return slot.tex;
        Path file = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("spraute_engine").resolve(key);
        Slot loaded = load(file);
        if (loaded == null) {
            MISSING.add(key);
            return null;
        }
        CACHE.put(key, loaded);
        return loaded.tex;
    }

    /** Texture for an absolute file path (project explorer). */
    public static Tex getAbsolute(Path file) {
        if (file == null) return null;
        String key = "abs:" + file.toAbsolutePath();
        if (MISSING.contains(key)) return null;
        Slot slot = CACHE.get(key);
        if (slot != null) return slot.tex;
        Slot loaded = load(file);
        if (loaded == null) {
            MISSING.add(key);
            return null;
        }
        CACHE.put(key, loaded);
        return loaded.tex;
    }

    public static void clear() {
        for (Slot slot : CACHE.values()) close(slot);
        CACHE.clear();
        MISSING.clear();
    }

    /** Drop cached GL preview after the file was rewritten (texture editor save). */
    public static void invalidate(Path file) {
        if (file == null) return;
        String absKey = "abs:" + file.toAbsolutePath();
        close(CACHE.remove(absKey));
        MISSING.remove(absKey);
        try {
            Path root = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("spraute_engine").toAbsolutePath().normalize();
            Path abs = file.toAbsolutePath().normalize();
            if (abs.startsWith(root)) {
                String rel = root.relativize(abs).toString().replace('\\', '/');
                close(CACHE.remove(rel));
                MISSING.remove(rel);
            }
        } catch (Exception ignored) {
        }
    }

    private static String normalizeKey(String relPath) {
        if (relPath == null || relPath.isBlank()) return null;
        String key = relPath.trim().replace('\\', '/');
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        if (key.isBlank() || key.indexOf(':') >= 0) return null;
        return key;
    }

    private static Slot load(Path file) {
        if (!Files.isRegularFile(file)) return null;
        NativeImage image = null;
        NativeImage upload = null;
        try (InputStream in = Files.newInputStream(file)) {
            image = NativeImage.read(in);
            int srcW = image.getWidth();
            int srcH = image.getHeight();
            upload = downscale(image, MAX_EDGE);
            if (upload != image) {
                image.close();
            }
            image = null;
            DynamicTexture texture = new DynamicTexture(upload);
            upload = null;
            return new Slot(new Tex(texture.getId(), srcW, srcH), texture);
        } catch (Exception e) {
            return null;
        } finally {
            if (image != null) image.close();
            if (upload != null) upload.close();
        }
    }

    private static NativeImage downscale(NativeImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxEdge && h <= maxEdge) return src;
        float scale = Math.min(maxEdge / (float) w, maxEdge / (float) h);
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        NativeImage dst = new NativeImage(nw, nh, true);
        for (int y = 0; y < nh; y++) {
            int sy = Math.min(h - 1, y * h / nh);
            for (int x = 0; x < nw; x++) {
                int sx = Math.min(w - 1, x * w / nw);
                dst.setPixelRGBA(x, y, src.getPixelRGBA(sx, sy));
            }
        }
        return dst;
    }

    private static void close(Slot slot) {
        if (slot == null || slot.texture == null) return;
        try {
            slot.texture.close();
        } catch (Exception ignored) {
        }
    }

    /** Fit (w,h) into a max box, preserving aspect. Uses source pixel size. */
    public static float[] fit(Tex tex, float maxW, float maxH) {
        if (tex == null || tex.width() <= 0 || tex.height() <= 0) return new float[]{maxW, maxH};
        float k = Math.min(maxW / tex.width(), maxH / tex.height());
        return new float[]{tex.width() * k, tex.height() * k};
    }

    /**
     * Opaque bounding box in texture pixels {@code [u, v, w, h]}, or null if the file
     * cannot be read or is fully transparent.
     */
    public static int[] opaqueBounds(String relPath, int alphaMin) {
        if (relPath == null || relPath.isBlank()) return null;
        String key = relPath.trim().replace('\\', '/');
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        if (key.isBlank() || key.indexOf(':') >= 0) return null;
        Path file = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("spraute_engine").resolve(key);
        if (!Files.isRegularFile(file)) return null;
        try (InputStream in = Files.newInputStream(file); NativeImage image = NativeImage.read(in)) {
            int w = image.getWidth();
            int h = image.getHeight();
            int minX = w, minY = h, maxX = -1, maxY = -1;
            int threshold = Math.max(1, Math.min(255, alphaMin));
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = (image.getPixelRGBA(x, y) >>> 24) & 0xFF;
                    if (a < threshold) continue;
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
            if (maxX < minX) return null;
            return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
        } catch (Exception e) {
            return null;
        }
    }
}
