package org.sprautestudio.st_addon_spraute.editor.texture;

import com.google.gson.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * A picture being drawn: its layers (ARGB pixels each), which one is drawn on, and the history for
 * undo. Saved as the flat PNG the game uses, with the layers kept beside it in
 * {@code images/.layers/<name>.json} so they are there next time.
 */
public final class PixelDoc {
    public static final class Layer {
        public String name;
        public int[] px;
        public boolean visible = true, locked;
        /** 0..100 */
        public int opacity = 100;

        public Layer(String name, int w, int h) {
            this.name = name;
            this.px = new int[w * h];
        }

        Layer copy() {
            Layer l = new Layer(name, 0, 0);
            l.px = px.clone();
            l.visible = visible;
            l.locked = locked;
            l.opacity = opacity;
            return l;
        }
    }

    public int w, h;
    public final List<Layer> layers = new ArrayList<>();
    public int active;
    /** Changes since the last save. */
    public int version, savedVersion;

    private record State(int w, int h, List<Layer> layers, int active) {
    }

    private final List<State> undo = new ArrayList<>(), redo = new ArrayList<>();

    public PixelDoc(int w, int h) {
        this.w = w;
        this.h = h;
    }

    public Layer layer() {
        return layers.get(Math.max(0, Math.min(active, layers.size() - 1)));
    }

    public boolean dirty() {
        return version != savedVersion;
    }

    // ------------------------------------------------------------------ history

    /** Call before changing anything: the state now goes to the undo list. */
    public void checkpoint() {
        List<Layer> copy = new ArrayList<>();
        for (Layer l : layers) copy.add(l.copy());
        undo.add(new State(w, h, copy, active));
        while (undo.size() > 80) undo.remove(0);
        redo.clear();
        version++;
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.add(snapshot());
        restore(undo.remove(undo.size() - 1));
        version++;
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.add(snapshot());
        restore(redo.remove(redo.size() - 1));
        version++;
        return true;
    }

    private State snapshot() {
        List<Layer> copy = new ArrayList<>();
        for (Layer l : layers) copy.add(l.copy());
        return new State(w, h, copy, active);
    }

    private void restore(State s) {
        w = s.w;
        h = s.h;
        layers.clear();
        layers.addAll(s.layers);
        active = s.active;
    }

    // ------------------------------------------------------------------ pixels

    public boolean in(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    /** All visible layers together, bottom to top. */
    public int[] flatten() {
        int[] out = new int[w * h];
        for (Layer l : layers) {
            if (!l.visible || l.opacity <= 0) continue;
            for (int i = 0; i < out.length; i++) out[i] = PixelOps.over(out[i], l.px[i], l.opacity / 100f);
        }
        return out;
    }

    public int flatAt(int x, int y) {
        if (!in(x, y)) return 0;
        int c = 0;
        for (Layer l : layers) if (l.visible) c = PixelOps.over(c, l.px[y * w + x], l.opacity / 100f);
        return c;
    }

    // ------------------------------------------------------------------ layers

    public Layer addLayer(String name) {
        Layer l = new Layer(name, w, h);
        layers.add(active + 1, l);
        active++;
        return l;
    }

    public void mergeDown() {
        if (active <= 0) return;
        Layer top = layers.get(active), below = layers.get(active - 1);
        for (int i = 0; i < below.px.length; i++) if (top.visible) below.px[i] = PixelOps.over(below.px[i], top.px[i], top.opacity / 100f);
        layers.remove(active);
        active--;
    }

    /** A new size; pixels stay at the top-left (anchor 0..2 = left/centre/right, same for top). */
    public void resize(int nw, int nh, int ax, int ay) {
        int ox = ax == 0 ? 0 : ax == 1 ? (nw - w) / 2 : nw - w, oy = ay == 0 ? 0 : ay == 1 ? (nh - h) / 2 : nh - h;
        for (Layer l : layers) {
            int[] n = new int[nw * nh];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int tx = x + ox, ty = y + oy;
                    if (tx >= 0 && ty >= 0 && tx < nw && ty < nh) n[ty * nw + tx] = l.px[y * w + x];
                }
            l.px = n;
        }
        w = nw;
        h = nh;
    }

    // ------------------------------------------------------------------ files

    public static Path layersFile(Path png) {
        String n = png.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return png.getParent().resolve(".layers").resolve((dot > 0 ? n.substring(0, dot) : n) + ".json");
    }

    /** The picture with its layers when they were saved with it, else one layer of the picture. */
    public static PixelDoc load(Path file) throws IOException {
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) throw new IOException("Картинка не читается");
        Path lf = layersFile(file);
        if (Files.exists(lf)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(lf, StandardCharsets.UTF_8)).getAsJsonObject();
                long stamp = o.has("stamp") ? o.get("stamp").getAsLong() : -1;
                if (stamp == Files.getLastModifiedTime(file).toMillis() && o.get("w").getAsInt() == img.getWidth() && o.get("h").getAsInt() == img.getHeight()) {
                    PixelDoc d = new PixelDoc(img.getWidth(), img.getHeight());
                    for (JsonElement e : o.getAsJsonArray("layers")) {
                        JsonObject lo = e.getAsJsonObject();
                        Layer l = new Layer(lo.get("name").getAsString(), d.w, d.h);
                        l.visible = !lo.has("visible") || lo.get("visible").getAsBoolean();
                        l.locked = lo.has("locked") && lo.get("locked").getAsBoolean();
                        l.opacity = lo.has("opacity") ? lo.get("opacity").getAsInt() : 100;
                        BufferedImage li = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(lo.get("png").getAsString())));
                        li.getRGB(0, 0, d.w, d.h, l.px, 0, d.w);
                        d.layers.add(l);
                    }
                    d.active = Math.max(0, Math.min(o.has("active") ? o.get("active").getAsInt() : 0, d.layers.size() - 1));
                    if (!d.layers.isEmpty()) return d;
                }
            } catch (Exception ignored) {
                // the layers do not match the picture any more (it was changed elsewhere): start from the picture
            }
        }
        PixelDoc d = new PixelDoc(img.getWidth(), img.getHeight());
        Layer l = new Layer("Фон", d.w, d.h);
        img.getRGB(0, 0, d.w, d.h, l.px, 0, d.w);
        d.layers.add(l);
        return d;
    }

    public void save(Path file) throws IOException {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, flatten(), 0, w);
        Path png = file.getFileName().toString().toLowerCase().endsWith(".png") ? file
                : file.resolveSibling(file.getFileName().toString().replaceAll("\\.[^.]+$", "") + ".png");
        ImageIO.write(out, "png", png.toFile());
        Path lf = layersFile(png);
        if (layers.size() == 1 && layers.get(0).visible && layers.get(0).opacity == 100) {
            Files.deleteIfExists(lf);
        } else {
            JsonObject o = new JsonObject();
            o.addProperty("w", w);
            o.addProperty("h", h);
            o.addProperty("active", active);
            o.addProperty("stamp", Files.getLastModifiedTime(png).toMillis());
            JsonArray arr = new JsonArray();
            for (Layer l : layers) {
                JsonObject lo = new JsonObject();
                lo.addProperty("name", l.name);
                lo.addProperty("visible", l.visible);
                lo.addProperty("locked", l.locked);
                lo.addProperty("opacity", l.opacity);
                BufferedImage li = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                li.setRGB(0, 0, w, h, l.px, 0, w);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                ImageIO.write(li, "png", bo);
                lo.addProperty("png", Base64.getEncoder().encodeToString(bo.toByteArray()));
                arr.add(lo);
            }
            o.add("layers", arr);
            Files.createDirectories(lf.getParent());
            Files.writeString(lf, o.toString(), StandardCharsets.UTF_8);
        }
        savedVersion = version;
    }
}
