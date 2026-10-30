package org.sprautestudio.st_addon_spraute.fx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A visual effect: layers that emit particles (sparks, smoke, glows, rings, beams) plus screen
 * layers (flash, camera shake) and a sound. Times are seconds, distances blocks; every distance
 * and size is multiplied by the scale the effect is played with.
 */
public class FxDef {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Whole length; layers stop emitting after it. 0 — plays until stopped (fx stop). */
    public float duration = 3;
    /** Sound at the start: id and volume. */
    public String sound = "";
    public float soundVolume = 1, soundPitch = 1;
    public List<Layer> layers = new ArrayList<>();

    /** One emitter (or a screen layer). */
    public static class Layer {
        public String name = "Слой";
        public boolean enabled = true;
        /** particles, flash (screen flash), shake (camera shake). */
        public String type = "particles";

        // ---------------------------------------------------------------- timing
        /** Seconds after the start before the layer begins. */
        public float delay;
        /** Seconds the layer emits; 0 — as long as the effect. */
        public float time;

        // ---------------------------------------------------------------- emission
        /** Particles per second while emitting. */
        public float rate;
        /** Particles at once when the layer begins. */
        public int burst = 20;
        /** Repeat the burst every this many seconds (0 — once). */
        public float burstEvery;
        /** Most particles alive at once in this layer. */
        public int max = 600;

        // ---------------------------------------------------------------- shape
        /** point, sphere (inside), shell (surface), disc, ring, cone, box, column, line. */
        public String shape = "point";
        public float radius = 0.5f, radiusInner, height = 1, angle = 30;
        /** A burst on a ring stands evenly around it (orbs, runes) instead of at random places. */
        public boolean even;
        public float offsetX, offsetY, offsetZ;
        /**
         * Turn of the whole layer, degrees around X, Y and Z: the shape it emits from, the way the
         * particles fly, the axis of the orbit and the plane of lying sprites (a standing ring: X 90).
         */
        public float rotX, rotY, rotZ;

        // ---------------------------------------------------------------- motion
        /** out (from the centre), up, down, random, tangent (around), axis (along the line), none. */
        public String direction = "out";
        public float speed = 2, speedRandom = 0.5f;
        /** Blocks/s² down (negative — up). */
        public float gravity;
        /** Share of the speed lost per second (0..10). */
        public float drag = 0.5f;
        /** Degrees per second the particles turn around the effect's centre (orbits). */
        public float orbit;
        /** Wobbly random force (blocks/s²). */
        public float turbulence;
        /** Pulled towards the centre (blocks/s², negative pushes away). */
        public float attract;
        /** Move with the effect when it follows an entity. */
        public boolean local = true;

        // ---------------------------------------------------------------- look
        /** glow, spark, smoke, ring, star, flare, square, circle or image:<project image>. */
        public String texture = "glow";
        /** add (glowing) or alpha (smoke, dust). */
        public String blend = "add";
        /** billboard (faces the camera), stretch (along its motion), flat (lying), beam (a ray along its direction). */
        public String render = "billboard";
        /** Seconds a particle lives (± lifeRandom). */
        public float life = 1, lifeRandom = 0.3f;
        /** Size over the life (blocks), evenly spaced keys. */
        public float[] size = {0.4f, 0.1f};
        public float sizeRandom = 0.2f;
        /** Opacity over the life, 0..1. */
        public float[] alpha = {0, 1, 1, 0};
        /** Colour over the life, "#RRGGBB" keys. */
        public String[] color = {"#FFFFFF"};
        /** Each particle picks its own colour from the gradient instead of going through it. */
        public boolean colorRandom;
        /** Brightness multiplier (bloom-ish for add). */
        public float intensity = 1;
        /** Degrees per second the sprite spins (± spinRandom). */
        public float spin, spinRandom = 0;
        /** Length of stretched particles per unit of speed. */
        public float stretch = 0.15f;
        /** Length of beams (blocks). */
        public float beamLength = 20;

        // ---------------------------------------------------------------- screen layers
        /** flash: colour and strength (0..1); shake: strength 0..1. */
        public String flashColor = "#FFFFFF";
        public float strength = 0.8f;
        /** Felt within this distance (blocks); weaker further away. 0 — everywhere. */
        public float range = 64;

        /** Fills what older or hand-written files leave out. */
        void fix() {
            if (name == null) name = "Слой";
            if (type == null) type = "particles";
            if (shape == null) shape = "point";
            if (direction == null) direction = "out";
            if (texture == null) texture = "glow";
            if (blend == null) blend = "add";
            if (render == null) render = "billboard";
            if (size == null || size.length == 0) size = new float[]{0.4f};
            if (alpha == null || alpha.length == 0) alpha = new float[]{1};
            if (color == null || color.length == 0) color = new String[]{"#FFFFFF"};
            if (flashColor == null) flashColor = "#FFFFFF";
            max = Math.max(1, Math.min(20000, max));
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static FxDef fromJson(String json) {
        FxDef d = null;
        try {
            d = GSON.fromJson(json, FxDef.class);
        } catch (Exception ignored) {
        }
        if (d == null) d = new FxDef();
        if (d.layers == null) d.layers = new ArrayList<>();
        if (d.sound == null) d.sound = "";
        for (Layer l : d.layers) l.fix();
        return d;
    }

    public FxDef copy() {
        return fromJson(toJson());
    }

    // ------------------------------------------------------------------ curves

    /** Value of evenly spaced keys at {@code t} (0..1), linear between them. */
    public static float curve(float[] keys, float t) {
        if (keys == null || keys.length == 0) return 1;
        if (keys.length == 1) return keys[0];
        float u = Math.max(0, Math.min(1, t)) * (keys.length - 1);
        int i = Math.min(keys.length - 2, (int) u);
        float f = u - i;
        return keys[i] + (keys[i + 1] - keys[i]) * f;
    }

    public static int parseColor(String s) {
        if (s == null) return 0xFFFFFF;
        String h = s.trim().replace("#", "");
        try {
            if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            return Integer.parseInt(h.substring(0, Math.min(6, h.length())), 16);
        } catch (Exception e) {
            return 0xFFFFFF;
        }
    }

    /** RGB of a gradient at {@code t}. */
    public static int gradient(int[] colors, float t) {
        if (colors.length == 0) return 0xFFFFFF;
        if (colors.length == 1) return colors[0];
        float u = Math.max(0, Math.min(1, t)) * (colors.length - 1);
        int i = Math.min(colors.length - 2, (int) u);
        float f = u - i;
        int a = colors[i], b = colors[i + 1];
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * f);
        return (r << 16) | (g << 8) | bl;
    }

    public static float[] parseCurve(String s, float[] fallback) {
        try {
            String[] p = s.split("[,;\\s]+");
            List<Float> out = new ArrayList<>();
            for (String x : p) if (!x.isBlank()) out.add(Float.parseFloat(x.trim()));
            if (out.isEmpty()) return fallback;
            float[] r = new float[out.size()];
            for (int i = 0; i < r.length; i++) r[i] = out.get(i);
            return r;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String curveText(float[] k) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < k.length; i++) {
            if (i > 0) b.append(", ");
            float v = Math.round(k[i] * 1000) / 1000f;
            b.append(v == (long) v ? String.valueOf((long) v) : String.valueOf(v));
        }
        return b.toString();
    }

    public static Layer layer(String name) {
        Layer l = new Layer();
        l.name = name;
        return l;
    }
}
