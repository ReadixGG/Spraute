package org.zonarstudio.spraute_engine.client.editor.texture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Pixel-art document: stacked layers in NativeImage RGBA (ABGR word).
 * Flat PNG is what the game reads; {@code *.png.layers.json} keeps layers.
 */
public final class TextureDocument {
    public static final class Layer {
        public String name;
        public boolean visible = true;
        public float opacity = 1f;
        public int[] pixels;

        public Layer copy() {
            Layer l = new Layer();
            l.name = name;
            l.visible = visible;
            l.opacity = opacity;
            l.pixels = pixels == null ? null : pixels.clone();
            return l;
        }
    }

    public int width;
    public int height;
    public int active;
    public final List<Layer> layers = new ArrayList<>();

    public static Path sidecar(Path png) {
        return png.resolveSibling(png.getFileName().toString() + ".layers.json");
    }

    public static TextureDocument blank(int w, int h) {
        TextureDocument d = new TextureDocument();
        d.width = Math.max(1, w);
        d.height = Math.max(1, h);
        Layer layer = new Layer();
        layer.name = "Слой 1";
        layer.pixels = new int[d.width * d.height];
        d.layers.add(layer);
        return d;
    }

    public static void writeBlank(Path png, int size) throws IOException {
        blank(size, size).save(png);
    }

    public static TextureDocument load(Path png) throws IOException {
        Path side = sidecar(png);
        if (Files.isRegularFile(side)) {
            try {
                return loadSidecar(side);
            } catch (Exception ignored) {
                // fall through to flat PNG
            }
        }
        return loadFlat(png);
    }

    public static TextureDocument loadFlat(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png); NativeImage image = NativeImage.read(in)) {
            TextureDocument d = new TextureDocument();
            d.width = image.getWidth();
            d.height = image.getHeight();
            Layer layer = new Layer();
            layer.name = "Слой 1";
            layer.pixels = new int[d.width * d.height];
            for (int y = 0; y < d.height; y++) {
                for (int x = 0; x < d.width; x++) {
                    layer.pixels[y * d.width + x] = image.getPixelRGBA(x, y);
                }
            }
            d.layers.add(layer);
            return d;
        }
    }

    private static TextureDocument loadSidecar(Path side) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(side, StandardCharsets.UTF_8)).getAsJsonObject();
        TextureDocument d = new TextureDocument();
        d.width = Math.max(1, root.get("w").getAsInt());
        d.height = Math.max(1, root.get("h").getAsInt());
        d.active = root.has("active") ? root.get("active").getAsInt() : 0;
        JsonArray arr = root.getAsJsonArray("layers");
        if (arr == null || arr.isEmpty()) throw new IOException("empty layers");
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = arr.get(i).getAsJsonObject();
            Layer layer = new Layer();
            layer.name = o.has("name") ? o.get("name").getAsString() : ("Слой " + (i + 1));
            layer.visible = !o.has("visible") || o.get("visible").getAsBoolean();
            layer.opacity = o.has("opacity") ? o.get("opacity").getAsFloat() : 1f;
            byte[] png = Base64.getDecoder().decode(o.get("png").getAsString());
            try (NativeImage image = NativeImage.read(new ByteArrayInputStream(png))) {
                layer.pixels = new int[d.width * d.height];
                int cw = Math.min(d.width, image.getWidth());
                int ch = Math.min(d.height, image.getHeight());
                for (int y = 0; y < ch; y++) {
                    for (int x = 0; x < cw; x++) {
                        layer.pixels[y * d.width + x] = image.getPixelRGBA(x, y);
                    }
                }
            }
            d.layers.add(layer);
        }
        if (d.active < 0 || d.active >= d.layers.size()) d.active = d.layers.size() - 1;
        return d;
    }

    public void save(Path png) throws IOException {
        Files.createDirectories(png.getParent());
        NativeImage flat = new NativeImage(width, height, true);
        try {
            int[] composed = new int[width * height];
            flatten(composed);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    flat.setPixelRGBA(x, y, composed[y * width + x]);
                }
            }
            flat.writeToFile(png.toFile());
        } finally {
            flat.close();
        }
        JsonObject root = new JsonObject();
        root.addProperty("w", width);
        root.addProperty("h", height);
        root.addProperty("active", active);
        JsonArray arr = new JsonArray();
        for (Layer layer : layers) {
            JsonObject o = new JsonObject();
            o.addProperty("name", layer.name == null ? "Слой" : layer.name);
            o.addProperty("visible", layer.visible);
            o.addProperty("opacity", layer.opacity);
            o.addProperty("png", Base64.getEncoder().encodeToString(encodePng(layer.pixels)));
            arr.add(o);
        }
        root.add("layers", arr);
        Path side = sidecar(png);
        Files.writeString(side, root.toString(), StandardCharsets.UTF_8);
    }

    public TextureDocument snapshot() {
        TextureDocument d = new TextureDocument();
        d.width = width;
        d.height = height;
        d.active = active;
        for (Layer layer : layers) d.layers.add(layer.copy());
        return d;
    }

    public void restore(TextureDocument snap) {
        width = snap.width;
        height = snap.height;
        active = snap.active;
        layers.clear();
        for (Layer layer : snap.layers) layers.add(layer.copy());
        if (active < 0 || active >= layers.size()) active = Math.max(0, layers.size() - 1);
    }

    public Layer activeLayer() {
        if (layers.isEmpty()) return null;
        if (active < 0 || active >= layers.size()) active = layers.size() - 1;
        return layers.get(active);
    }

    public void flatten(int[] out) {
        java.util.Arrays.fill(out, 0);
        for (Layer layer : layers) {
            if (!layer.visible || layer.opacity <= 0.001f || layer.pixels == null) continue;
            int n = Math.min(out.length, layer.pixels.length);
            for (int i = 0; i < n; i++) {
                out[i] = over(out[i], layer.pixels[i], layer.opacity);
            }
        }
    }

    public int compositeAt(int x, int y) {
        if (!inBounds(x, y)) return 0;
        int i = y * width + x;
        int dst = 0;
        for (Layer layer : layers) {
            if (!layer.visible || layer.opacity <= 0.001f || layer.pixels == null) continue;
            dst = over(dst, layer.pixels[i], layer.opacity);
        }
        return dst;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    @FunctionalInterface
    public interface PixelClip {
        boolean test(int x, int y);
    }

    public void stamp(int x, int y, int color, int brush, boolean erase, PixelClip clip) {
        Layer layer = activeLayer();
        if (layer == null || layer.pixels == null) return;
        int r = Math.max(0, (brush - 1) / 2);
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int px = x + dx;
                int py = y + dy;
                if (!inBounds(px, py)) continue;
                if (clip != null && !clip.test(px, py)) continue;
                layer.pixels[py * width + px] = erase ? 0 : color;
            }
        }
    }

    public void rect(int x0, int y0, int x1, int y1, int color, int brush, boolean fill, PixelClip clip) {
        int xa = Math.min(x0, x1);
        int xb = Math.max(x0, x1);
        int ya = Math.min(y0, y1);
        int yb = Math.max(y0, y1);
        if (fill) {
            for (int y = ya; y <= yb; y++) {
                for (int x = xa; x <= xb; x++) {
                    stamp(x, y, color, 1, false, clip);
                }
            }
            return;
        }
        line(xa, ya, xb, ya, color, brush, false, clip);
        line(xb, ya, xb, yb, color, brush, false, clip);
        line(xb, yb, xa, yb, color, brush, false, clip);
        line(xa, yb, xa, ya, color, brush, false, clip);
    }

    public void ellipse(int x0, int y0, int x1, int y1, int color, int brush, boolean fill, PixelClip clip) {
        int xa = Math.min(x0, x1);
        int xb = Math.max(x0, x1);
        int ya = Math.min(y0, y1);
        int yb = Math.max(y0, y1);
        float cx = (xa + xb) * 0.5f;
        float cy = (ya + yb) * 0.5f;
        float rx = Math.max(0.5f, (xb - xa) * 0.5f);
        float ry = Math.max(0.5f, (yb - ya) * 0.5f);
        int yMin = Math.max(0, ya);
        int yMax = Math.min(height - 1, yb);
        int xMin = Math.max(0, xa);
        int xMax = Math.min(width - 1, xb);
        for (int y = yMin; y <= yMax; y++) {
            for (int x = xMin; x <= xMax; x++) {
                if (!insideEllipse(x, y, cx, cy, rx, ry)) continue;
                if (!fill
                        && insideEllipse(x - 1, y, cx, cy, rx, ry)
                        && insideEllipse(x + 1, y, cx, cy, rx, ry)
                        && insideEllipse(x, y - 1, cx, cy, rx, ry)
                        && insideEllipse(x, y + 1, cx, cy, rx, ry)) {
                    continue;
                }
                stamp(x, y, color, fill ? 1 : brush, false, clip);
            }
        }
    }

    private static boolean insideEllipse(int x, int y, float cx, float cy, float rx, float ry) {
        float nx = (x - cx) / rx;
        float ny = (y - cy) / ry;
        return nx * nx + ny * ny <= 1.0001f;
    }

    public void triangle(int x0, int y0, int x1, int y1, int color, int brush, boolean fill, PixelClip clip) {
        int xa = Math.min(x0, x1);
        int xb = Math.max(x0, x1);
        int ya = Math.min(y0, y1);
        int yb = Math.max(y0, y1);
        int tx = (xa + xb) / 2;
        int ty = ya;
        if (fill) {
            fillTriangle(tx, ty, xa, yb, xb, yb, color, clip);
            return;
        }
        line(tx, ty, xa, yb, color, brush, false, clip);
        line(xa, yb, xb, yb, color, brush, false, clip);
        line(xb, yb, tx, ty, color, brush, false, clip);
    }

    private void fillTriangle(int x0, int y0, int x1, int y1, int x2, int y2, int color, PixelClip clip) {
        int minX = Math.max(0, Math.min(x0, Math.min(x1, x2)));
        int maxX = Math.min(width - 1, Math.max(x0, Math.max(x1, x2)));
        int minY = Math.max(0, Math.min(y0, Math.min(y1, y2)));
        int maxY = Math.min(height - 1, Math.max(y0, Math.max(y1, y2)));
        float den = (float) (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
        if (Math.abs(den) < 1e-4f) return;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float a = ((y1 - y2) * (x - x2) + (x2 - x1) * (y - y2)) / den;
                float b = ((y2 - y0) * (x - x2) + (x0 - x2) * (y - y2)) / den;
                float c = 1f - a - b;
                if (a < -0.001f || b < -0.001f || c < -0.001f) continue;
                stamp(x, y, color, 1, false, clip);
            }
        }
    }

    public void line(int x0, int y0, int x1, int y1, int color, int brush, boolean erase, PixelClip clip) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            stamp(x0, y0, color, brush, erase, clip);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    public void floodFill(int x, int y, int color, PixelClip clip) {
        Layer layer = activeLayer();
        if (layer == null || !inBounds(x, y)) return;
        if (clip != null && !clip.test(x, y)) return;
        int target = layer.pixels[y * width + x];
        if (target == color) return;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{x, y});
        boolean[] seen = new boolean[width * height];
        int guard = width * height;
        while (!q.isEmpty() && guard-- > 0) {
            int[] p = q.removeFirst();
            int px = p[0];
            int py = p[1];
            if (!inBounds(px, py)) continue;
            if (clip != null && !clip.test(px, py)) continue;
            int i = py * width + px;
            if (seen[i] || layer.pixels[i] != target) continue;
            seen[i] = true;
            layer.pixels[i] = color;
            q.add(new int[]{px + 1, py});
            q.add(new int[]{px - 1, py});
            q.add(new int[]{px, py + 1});
            q.add(new int[]{px, py - 1});
        }
    }

    /**
     * Magic-wand region: pixels within {@code tolerance} of the seed color.
     * {@code contiguous} walks 8-neighbors; otherwise every matching pixel.
     * {@code sampleAll} reads the visible composite instead of the active layer.
     */
    public boolean[] floodSelect(int x, int y, int tolerance, boolean contiguous, boolean sampleAll) {
        boolean[] out = new boolean[width * height];
        if (!inBounds(x, y)) return out;
        int[] src;
        if (sampleAll) {
            src = new int[width * height];
            flatten(src);
        } else {
            Layer layer = activeLayer();
            if (layer == null || layer.pixels == null) return out;
            src = layer.pixels;
        }
        int target = src[y * width + x];
        int tol = Math.max(0, Math.min(255, tolerance));
        if (!contiguous) {
            int n = Math.min(src.length, out.length);
            for (int i = 0; i < n; i++) {
                if (colorNear(src[i], target, tol)) out[i] = true;
            }
            return out;
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{x, y});
        int guard = width * height;
        while (!q.isEmpty() && guard-- > 0) {
            int[] p = q.removeFirst();
            int px = p[0];
            int py = p[1];
            if (!inBounds(px, py)) continue;
            int i = py * width + px;
            if (out[i] || !colorNear(src[i], target, tol)) continue;
            out[i] = true;
            q.add(new int[]{px + 1, py});
            q.add(new int[]{px - 1, py});
            q.add(new int[]{px, py + 1});
            q.add(new int[]{px, py - 1});
            q.add(new int[]{px + 1, py + 1});
            q.add(new int[]{px - 1, py + 1});
            q.add(new int[]{px + 1, py - 1});
            q.add(new int[]{px - 1, py - 1});
        }
        return out;
    }

    /** Photoshop-style per-channel max delta, including alpha. */
    public static boolean colorNear(int a, int b, int tolerance) {
        if (a == b) return true;
        if (tolerance >= 255) return true;
        int dr = Math.abs((a & 0xFF) - (b & 0xFF));
        int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
        int db = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
        int da = Math.abs(((a >>> 24) & 0xFF) - ((b >>> 24) & 0xFF));
        return Math.max(Math.max(dr, dg), Math.max(db, da)) <= tolerance;
    }

    public int[] copyRect(IntRect rect) {
        Layer layer = activeLayer();
        if (layer == null || rect == null || rect.isEmpty()) return new int[0];
        IntRect r = rect.clipped(width, height);
        int[] out = new int[r.w * r.h];
        for (int y = 0; y < r.h; y++) {
            System.arraycopy(layer.pixels, (r.y + y) * width + r.x, out, y * r.w, r.w);
        }
        return out;
    }

    public void clearRect(IntRect rect) {
        Layer layer = activeLayer();
        if (layer == null || rect == null || rect.isEmpty()) return;
        IntRect r = rect.clipped(width, height);
        for (int y = 0; y < r.h; y++) {
            int row = (r.y + y) * width + r.x;
            java.util.Arrays.fill(layer.pixels, row, row + r.w, 0);
        }
    }

    public void clearWhere(PixelClip clip) {
        Layer layer = activeLayer();
        if (layer == null || layer.pixels == null || clip == null) return;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                if (clip.test(x, y)) layer.pixels[row + x] = 0;
            }
        }
    }

    public void pasteRect(int destX, int destY, int srcW, int srcH, int[] src, PixelClip clip) {
        pasteRect(destX, destY, srcW, srcH, src, clip, true);
    }

    public void pasteRect(int destX, int destY, int srcW, int srcH, int[] src,
                          PixelClip clip, boolean skipTransparent) {
        Layer layer = activeLayer();
        if (layer == null || src == null || srcW <= 0 || srcH <= 0) return;
        for (int y = 0; y < srcH; y++) {
            for (int x = 0; x < srcW; x++) {
                int px = destX + x;
                int py = destY + y;
                if (!inBounds(px, py)) continue;
                if (clip != null && !clip.test(px, py)) continue;
                int c = src[y * srcW + x];
                if (skipTransparent && alpha(c) == 0) continue;
                layer.pixels[py * width + px] = c;
            }
        }
    }

    /** Paste only pixels where {@code srcMask} is true, including transparent ones. */
    public void pasteRectMasked(int destX, int destY, int srcW, int srcH, int[] src, boolean[] srcMask) {
        Layer layer = activeLayer();
        if (layer == null || src == null || srcW <= 0 || srcH <= 0) return;
        for (int y = 0; y < srcH; y++) {
            for (int x = 0; x < srcW; x++) {
                int i = y * srcW + x;
                if (srcMask != null && (i >= srcMask.length || !srcMask[i])) continue;
                int px = destX + x;
                int py = destY + y;
                if (!inBounds(px, py)) continue;
                layer.pixels[py * width + px] = src[i];
            }
        }
    }

    public Layer addLayer(String name) {
        Layer layer = new Layer();
        layer.name = name == null || name.isBlank() ? ("Слой " + (layers.size() + 1)) : name;
        layer.pixels = new int[width * height];
        layers.add(layer);
        active = layers.size() - 1;
        return layer;
    }

    public void duplicateActive() {
        Layer src = activeLayer();
        if (src == null) return;
        Layer copy = src.copy();
        copy.name = src.name + " копия";
        layers.add(active + 1, copy);
        active = active + 1;
    }

    public void removeActive() {
        if (layers.size() <= 1) return;
        layers.remove(active);
        if (active >= layers.size()) active = layers.size() - 1;
    }

    public void moveActive(int dir) {
        int to = active + dir;
        if (to < 0 || to >= layers.size()) return;
        Layer layer = layers.remove(active);
        layers.add(to, layer);
        active = to;
    }

    public boolean canMergeDown() {
        return layers.size() > 1 && active > 0;
    }

    /** Composite the active layer onto the one below it, then drop the active. */
    public void mergeDown() {
        if (!canMergeDown()) return;
        Layer top = layers.get(active);
        Layer bottom = layers.get(active - 1);
        compositeOnto(bottom, top);
        layers.remove(active);
        active = active - 1;
    }

    public boolean canFlatten() {
        return layers.size() > 1;
    }

    /** Bake every visible layer into one. Hidden layers are discarded. */
    public void flattenVisible() {
        if (!canFlatten()) return;
        int[] baked = new int[width * height];
        flatten(baked);
        Layer keep = new Layer();
        keep.name = "Слой 1";
        keep.visible = true;
        keep.opacity = 1f;
        keep.pixels = baked;
        layers.clear();
        layers.add(keep);
        active = 0;
    }

    private static void compositeOnto(Layer dst, Layer src) {
        if (dst == null || src == null || dst.pixels == null || src.pixels == null) return;
        float opacity = src.opacity;
        if (opacity <= 0.001f) return;
        int n = Math.min(dst.pixels.length, src.pixels.length);
        for (int i = 0; i < n; i++) {
            dst.pixels[i] = over(dst.pixels[i], src.pixels[i], opacity);
        }
    }

    public static int packRgba(float r, float g, float b, float a) {
        int ri = clamp255(Math.round(r * 255f));
        int gi = clamp255(Math.round(g * 255f));
        int bi = clamp255(Math.round(b * 255f));
        int ai = clamp255(Math.round(a * 255f));
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;
    }

    public static void unpackRgba(int packed, float[] out) {
        out[0] = (packed & 0xFF) / 255f;
        out[1] = ((packed >> 8) & 0xFF) / 255f;
        out[2] = ((packed >> 16) & 0xFF) / 255f;
        out[3] = ((packed >>> 24) & 0xFF) / 255f;
    }

    public static int alpha(int packed) {
        return (packed >>> 24) & 0xFF;
    }

    public static int imguiCol(int packed) {
        return packed;
    }

    private byte[] encodePng(int[] pixels) throws IOException {
        NativeImage img = new NativeImage(width, height, true);
        try {
            int n = Math.min(pixels.length, width * height);
            for (int i = 0; i < n; i++) {
                img.setPixelRGBA(i % width, i / width, pixels[i]);
            }
            Path tmp = Files.createTempFile("spraute-tex", ".png");
            try {
                img.writeToFile(tmp.toFile());
                return Files.readAllBytes(tmp);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } finally {
            img.close();
        }
    }

    /** src-over dst with layer opacity. Both colors are NativeImage RGBA. */
    private static int over(int dst, int src, float opacity) {
        int sa = Math.round(alpha(src) * Math.max(0f, Math.min(1f, opacity)));
        if (sa <= 0) return dst;
        int da = alpha(dst);
        int sr = src & 0xFF;
        int sg = (src >> 8) & 0xFF;
        int sb = (src >> 16) & 0xFF;
        int dr = dst & 0xFF;
        int dg = (dst >> 8) & 0xFF;
        int db = (dst >> 16) & 0xFF;
        int outA = sa + da * (255 - sa) / 255;
        if (outA <= 0) return 0;
        int outR = (sr * sa + dr * da * (255 - sa) / 255) / outA;
        int outG = (sg * sa + dg * da * (255 - sa) / 255) / outA;
        int outB = (sb * sa + db * da * (255 - sa) / 255) / outA;
        return (outA << 24) | (clamp255(outB) << 16) | (clamp255(outG) << 8) | clamp255(outR);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    public static final class IntRect {
        public int x, y, w, h;

        public IntRect() {}

        public IntRect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public static IntRect fromCorners(int x0, int y0, int x1, int y1) {
            int l = Math.min(x0, x1);
            int t = Math.min(y0, y1);
            return new IntRect(l, t, Math.abs(x1 - x0) + 1, Math.abs(y1 - y0) + 1);
        }

        public boolean isEmpty() {
            return w <= 0 || h <= 0;
        }

        public boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }

        /** Includes the marching-ants border so a grab on the edge still hits. */
        public boolean hits(int px, int py, int pad) {
            int p = Math.max(0, pad);
            return px >= x - p && py >= y - p && px < x + w + p && py < y + h + p;
        }

        public IntRect clipped(int maxW, int maxH) {
            int nx = Math.max(0, x);
            int ny = Math.max(0, y);
            int nr = Math.min(maxW, x + w);
            int nb = Math.min(maxH, y + h);
            return new IntRect(nx, ny, Math.max(0, nr - nx), Math.max(0, nb - ny));
        }

        public IntRect copy() {
            return new IntRect(x, y, w, h);
        }

        public void offset(int dx, int dy) {
            x += dx;
            y += dy;
        }
    }
}
