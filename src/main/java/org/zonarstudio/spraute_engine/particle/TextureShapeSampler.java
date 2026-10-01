package org.zonarstudio.spraute_engine.particle;

import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.resource.WorkspaceContent;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Samples a binary mask from rendered text or a workspace image into a deterministic
 * set of normalized 2D points in [-1, 1] (centered, aspect-correct), for use as a
 * particle-emission "shape" ({@code shape.type == "text" | "image"}).
 *
 * <p>Pure AWT ({@link Font}/{@link Graphics2D}) and {@link ImageIO} — no Minecraft client
 * ResourceManager dependency, so sampling is authoritative and identical whether it runs
 * on a dedicated server or the client, exactly like {@link org.zonarstudio.spraute_engine.resource.SprautePaths}.
 */
public final class TextureShapeSampler {
    private TextureShapeSampler() {}

    private static final Map<String, double[][]> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final int MAX_POINTS = 8192;
    private static final double[] ORIGIN = {0, 0};

    /** Sample points for literal text. Cached by (text, resolution, threshold). */
    public static double[][] sampleText(String text, int resolution, float threshold) {
        if (text == null || text.isBlank()) return new double[0][];
        int res = clampResolution(resolution);
        float t = clampThreshold(threshold);
        String key = "text|" + text + "|" + res + "|" + t;
        double[][] cached = CACHE.get(key);
        if (cached != null) return cached;
        double[][] points;
        try {
            points = renderText(text, res, t);
        } catch (Exception e) {
            points = new double[0][];
        }
        cache(key, points);
        return points;
    }

    /** Sample points from a workspace/particle-texture alpha mask. Cached by (file, mtime, resolution, threshold). */
    public static double[][] sampleImage(String texture, int resolution, float threshold) {
        if (texture == null || texture.isBlank()) return new double[0][];
        int res = clampResolution(resolution);
        float t = clampThreshold(threshold);
        Path file = resolveImageFile(texture);
        if (file == null) return new double[0][];
        String key = cacheKeyForFile(file, res, t);
        double[][] cached = CACHE.get(key);
        if (cached != null) return cached;
        double[][] points;
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            points = img == null ? new double[0][] : sampleMask(img, res, t);
        } catch (IOException e) {
            points = new double[0][];
        }
        cache(key, points);
        return points;
    }

    /** Deterministic pick: the same index always yields the same point for a given sample set. */
    public static double[] pointAt(double[][] points, int index) {
        if (points == null || points.length == 0) return ORIGIN;
        return points[Math.floorMod(index, points.length)];
    }

    private static String cacheKeyForFile(Path file, int res, float threshold) {
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            return "image|" + file.toAbsolutePath() + "|" + mtime + "|" + res + "|" + threshold;
        } catch (IOException e) {
            return "image|" + file.toAbsolutePath() + "|" + res + "|" + threshold;
        }
    }

    /**
     * Resolves a raw {@code shape.image} value (bare name, {@code textures/...},
     * {@code textures/particle/...}, or a namespaced resource path) to a real file,
     * mirroring {@code ExternalAssetPack.resolveParticleTextureFile}: workspace disk
     * first, then jar-bundled default assets via {@link WorkspaceContent}.
     */
    private static Path resolveImageFile(String rawTexture) {
        String path = rawTexture.replace('\\', '/').trim();
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        String assetPrefix = "assets/" + Spraute_engine.MODID + "/";
        if (path.startsWith(assetPrefix)) path = path.substring(assetPrefix.length());

        Path resolved = WorkspaceContent.resolve(path);
        if (resolved != null) return resolved;
        if (!path.startsWith("textures/")) {
            resolved = WorkspaceContent.resolve("textures/" + path);
            if (resolved != null) return resolved;
            resolved = WorkspaceContent.resolve("textures/particle/" + path);
            if (resolved != null) return resolved;
        }
        return null;
    }

    private static double[][] renderText(String text, int resolution, float threshold) {
        int fontSize = 128;
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontRenderContext frc = pg.getFontRenderContext();
        Rectangle2D bounds = font.getStringBounds(text, frc);
        LineMetrics lm = font.getLineMetrics(text, frc);
        pg.dispose();

        int padding = Math.max(2, fontSize / 8);
        int w = Math.max(1, (int) Math.ceil(bounds.getWidth())) + padding * 2;
        int h = Math.max(1, (int) Math.ceil(lm.getAscent() + lm.getDescent())) + padding * 2;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);
        float drawX = padding - (float) bounds.getX();
        float drawY = padding + lm.getAscent();
        g.drawString(text, drawX, drawY);
        g.dispose();

        return sampleMask(img, resolution, threshold);
    }

    /**
     * Scans {@code img} on an aspect-preserving grid whose longer axis has
     * {@code resolution} cells, keeping one deterministic (row-major) point per cell
     * whose alpha clears {@code threshold}. Points are centered and normalized so the
     * longer axis spans exactly [-1, 1].
     */
    private static double[][] sampleMask(BufferedImage img, int resolution, float threshold) {
        int iw = img.getWidth();
        int ih = img.getHeight();
        if (iw <= 0 || ih <= 0) return new double[0][];
        int alphaThreshold = Math.max(1, Math.min(255, Math.round(threshold * 255f)));

        int gw, gh;
        if (iw >= ih) {
            gw = resolution;
            gh = Math.max(1, Math.round(resolution * (ih / (float) iw)));
        } else {
            gh = resolution;
            gw = Math.max(1, Math.round(resolution * (iw / (float) ih)));
        }
        double half = Math.max(gw, gh) / 2.0;

        List<double[]> hits = new ArrayList<>();
        for (int gy = 0; gy < gh && hits.size() < MAX_POINTS; gy++) {
            int py = Math.min(ih - 1, (int) ((gy + 0.5) * ih / gh));
            for (int gx = 0; gx < gw && hits.size() < MAX_POINTS; gx++) {
                int px = Math.min(iw - 1, (int) ((gx + 0.5) * iw / gw));
                int argb = img.getRGB(px, py);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha < alphaThreshold) continue;
                double nx = ((gx + 0.5) - gw / 2.0) / half;
                double ny = -(((gy + 0.5) - gh / 2.0) / half);
                hits.add(new double[]{nx, ny});
            }
        }
        return hits.toArray(new double[0][]);
    }

    private static int clampResolution(int res) {
        if (res <= 0) return 48;
        return Math.max(4, Math.min(256, res));
    }

    private static float clampThreshold(float threshold) {
        if (Float.isNaN(threshold)) return 0.5f;
        return Math.max(0.01f, Math.min(1f, threshold));
    }

    private static void cache(String key, double[][] points) {
        if (CACHE.size() > MAX_CACHE_ENTRIES) CACHE.clear();
        CACHE.put(key, points);
    }

    /** Editor / debug: drop cached masks (e.g. after an image file on disk changed). */
    public static void clearCache() {
        CACHE.clear();
    }
}
