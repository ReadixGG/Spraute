package org.sprautestudio.st_addon_spraute.cutscene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A cutscene: camera keyframes on a timeline plus how the scene is presented (cinema bars, hidden
 * HUD, fades, skipping). Stored as a .scut file in the project, played by {@code cutscene play}.
 */
public final class CutsceneDoc {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String title = "";
    /** Catmull-Rom curve through the points; off — straight lines between them. */
    public boolean smooth = true;
    /** Cinema bars: share of the screen height covered at the top and at the bottom. */
    public float bars = 0.11f;
    public boolean hideHud = true;
    /** Which interface the cutscene hides: none, minecraft, storytelling, custom or all (null: from hideHud). */
    public String hide;

    public static final java.util.List<String> HIDE_MODES = java.util.List.of("all", "minecraft", "storytelling", "custom", "none");

    public static String hideTitle(String m) {
        return switch (m) {
            case "all" -> "весь интерфейс";
            case "minecraft" -> "Minecraft (хотбар, сердца, прицел…)";
            case "storytelling" -> "Spraute (HP, монеты, подсказки)";
            case "custom" -> "свои наложения (overlay)";
            default -> "ничего не скрывать";
        };
    }

    public String hideMode() {
        return hide != null && HIDE_MODES.contains(hide) ? hide : hideHud ? "all" : "none";
    }
    public boolean lockPlayer = true;
    public boolean skippable = true;
    /** Seconds of smooth transition from the player's view into the first frame and back after the end. */
    public float blendIn = 0.8f, blendOut = 0.8f;
    /** Fade from this colour at the start / into it at the end, seconds (0 — none). */
    public String fadeColor = "#000000";
    public float fadeIn = 0, fadeOut = 0;
    /** Positions are relative to where the cutscene is played (cutscene play "x" at alice). */
    public boolean relative = false;
    /**
     * One continuous move along the whole line at an even speed (frame times only set the total
     * length), eased once over the whole cutscene by {@link #flowEase}. Off — each move between two
     * frames is timed and eased on its own.
     */
    public boolean flow = false;
    public String flowEase = "ease_in_out_sine";

    public List<Key> keys = new ArrayList<>();

    /** A camera keyframe. The ease, look target, caption and shake apply to the move from this frame to the next. */
    public static final class Key {
        /** Seconds from the start. */
        public float t;
        public double x, y, z;
        public float yaw, pitch, roll;
        public float fov = 70;
        public String ease = "smooth";
        /** Keep looking at: "player", a story character, or empty for the keyed rotation. */
        public String look = "";
        /** Subtitle shown during the move from this frame. */
        public String caption = "";
        /** Event fired when the frame is reached: on cutscenemark "name". */
        public String mark = "";
        /** Camera shake strength during the move, 0..1. */
        public float shake = 0;

        public Key copy() {
            Key k = new Key();
            k.t = t;
            k.x = x;
            k.y = y;
            k.z = z;
            k.yaw = yaw;
            k.pitch = pitch;
            k.roll = roll;
            k.fov = fov;
            k.ease = ease;
            k.look = look;
            k.caption = caption;
            k.mark = mark;
            k.shake = shake;
            return k;
        }
    }

    public float duration() {
        float d = 0;
        for (Key k : keys) d = Math.max(d, k.t);
        return d;
    }

    public void sort() {
        keys.sort(Comparator.comparingDouble(k -> k.t));
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static CutsceneDoc fromJson(String json) {
        CutsceneDoc d = null;
        try {
            d = GSON.fromJson(json, CutsceneDoc.class);
        } catch (Exception ignored) {
        }
        if (d == null) d = new CutsceneDoc();
        if (d.keys == null) d.keys = new ArrayList<>();
        for (Key k : d.keys) {
            if (k.ease == null) k.ease = "smooth";
            if (k.look == null) k.look = "";
            if (k.caption == null) k.caption = "";
            if (k.mark == null) k.mark = "";
        }
        if (d.fadeColor == null) d.fadeColor = "#000000";
        if (d.title == null) d.title = "";
        if (d.flowEase == null) d.flowEase = "ease_in_out_sine";
        d.sort();
        return d;
    }

    // ================================================================== evaluation

    /** Camera pose: position, rotation, fov, shake. */
    public static final class Pose {
        public double x, y, z;
        public float yaw, pitch, roll, fov = 70, shake;
        /** Index of the frame the move starts from. */
        public int segment;

        public Pose set(Pose o) {
            x = o.x;
            y = o.y;
            z = o.z;
            yaw = o.yaw;
            pitch = o.pitch;
            roll = o.roll;
            fov = o.fov;
            shake = o.shake;
            segment = o.segment;
            return this;
        }

        /** Blend towards {@code o} by {@code k} (angles take the short way round). */
        public Pose lerp(Pose o, double k) {
            x += (o.x - x) * k;
            y += (o.y - y) * k;
            z += (o.z - z) * k;
            yaw += (float) (wrap(o.yaw - yaw) * k);
            pitch += (float) ((o.pitch - pitch) * k);
            roll += (float) ((o.roll - roll) * k);
            fov += (float) ((o.fov - fov) * k);
            shake += (float) ((o.shake - shake) * k);
            return this;
        }
    }

    public static float wrap(float a) {
        a %= 360;
        if (a >= 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    /** The camera at time {@code t} seconds; look targets are applied by the player. */
    public Pose at(double t) {
        Pose p = new Pose();
        if (keys.isEmpty()) return p;
        if (keys.size() == 1 || t <= keys.get(0).t) return from(p, keys.get(0), 0);
        int n = keys.size();
        if (flow) return atFlow(t);
        if (t >= keys.get(n - 1).t) return from(p, keys.get(n - 1), n - 1);
        int i = 0;
        while (i < n - 2 && t >= keys.get(i + 1).t) i++;
        Key a = keys.get(i), b = keys.get(i + 1);
        double span = Math.max(1e-4, b.t - a.t);
        double u = Ease.apply(a.ease, (t - a.t) / span);
        Key a0 = keys.get(Math.max(0, i - 1)), b1 = keys.get(Math.min(n - 1, i + 2));
        if (smooth) {
            p.x = catmull(a0.x, a.x, b.x, b1.x, u);
            p.y = catmull(a0.y, a.y, b.y, b1.y, u);
            p.z = catmull(a0.z, a.z, b.z, b1.z, u);
        } else {
            p.x = a.x + (b.x - a.x) * u;
            p.y = a.y + (b.y - a.y) * u;
            p.z = a.z + (b.z - a.z) * u;
        }
        p.yaw = (float) (a.yaw + wrap(b.yaw - a.yaw) * u);
        p.pitch = (float) (a.pitch + (b.pitch - a.pitch) * u);
        p.roll = (float) (a.roll + (b.roll - a.roll) * u);
        p.fov = (float) (a.fov + (b.fov - a.fov) * u);
        p.shake = a.shake;
        p.segment = i;
        return p;
    }

    private static Pose from(Pose p, Key k, int i) {
        p.x = k.x;
        p.y = k.y;
        p.z = k.z;
        p.yaw = k.yaw;
        p.pitch = k.pitch;
        p.roll = k.roll;
        p.fov = k.fov;
        p.shake = k.shake;
        p.segment = i;
        return p;
    }

    private double[] point(int i, double u) {
        int n = keys.size();
        Key a = keys.get(i), b = keys.get(Math.min(n - 1, i + 1));
        if (!smooth) return new double[]{a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u, a.z + (b.z - a.z) * u};
        Key a0 = keys.get(Math.max(0, i - 1)), b1 = keys.get(Math.min(n - 1, i + 2));
        return new double[]{catmull(a0.x, a.x, b.x, b1.x, u), catmull(a0.y, a.y, b.y, b1.y, u), catmull(a0.z, a.z, b.z, b1.z, u)};
    }

    /** Even speed along the whole curve: the time picks a distance, the distance a point. */
    private Pose atFlow(double t) {
        int n = keys.size();
        float t0 = keys.get(0).t, t1 = keys.get(n - 1).t;
        if (t >= t1) return from(new Pose(), keys.get(n - 1), n - 1);
        double g = Ease.apply(flowEase, (t - t0) / Math.max(1e-4, t1 - t0));
        final int steps = 24;
        double[][] cum = new double[n - 1][steps + 1];
        double[] start = new double[n];
        for (int i = 0; i < n - 1; i++) {
            double len = 0;
            double[] prev = point(i, 0);
            for (int s = 1; s <= steps; s++) {
                double[] cur = point(i, s / (double) steps);
                len += Math.sqrt(sq(cur[0] - prev[0]) + sq(cur[1] - prev[1]) + sq(cur[2] - prev[2]));
                cum[i][s] = len;
                prev = cur;
            }
            start[i + 1] = start[i] + len;
        }
        double total = start[n - 1];
        int seg;
        double u;
        if (total < 1e-6) {
            double f = g * (n - 1);
            seg = Math.min(n - 2, (int) f);
            u = f - seg;
        } else {
            double dist = g * total;
            seg = 0;
            while (seg < n - 2 && dist > start[seg + 1]) seg++;
            double local = dist - start[seg];
            int s = 1;
            while (s < steps && cum[seg][s] < local) s++;
            double c0 = cum[seg][s - 1], c1 = cum[seg][s];
            u = (s - 1 + (c1 > c0 ? (local - c0) / (c1 - c0) : 0)) / steps;
        }
        Pose p = new Pose();
        double[] pos = point(seg, u);
        p.x = pos[0];
        p.y = pos[1];
        p.z = pos[2];
        // angles along a smooth curve too, unwrapped so they never spin the long way
        float[] yaw = new float[n];
        yaw[0] = keys.get(0).yaw;
        for (int i = 1; i < n; i++) yaw[i] = yaw[i - 1] + wrap(keys.get(i).yaw - keys.get(i - 1).yaw);
        int a0 = Math.max(0, seg - 1), a = seg, b = Math.min(n - 1, seg + 1), b1 = Math.min(n - 1, seg + 2);
        p.yaw = (float) catmull(yaw[a0], yaw[a], yaw[b], yaw[b1], u);
        p.pitch = (float) catmull(keys.get(a0).pitch, keys.get(a).pitch, keys.get(b).pitch, keys.get(b1).pitch, u);
        p.roll = (float) catmull(keys.get(a0).roll, keys.get(a).roll, keys.get(b).roll, keys.get(b1).roll, u);
        p.fov = (float) catmull(keys.get(a0).fov, keys.get(a).fov, keys.get(b).fov, keys.get(b1).fov, u);
        p.pitch = Math.max(-90, Math.min(90, p.pitch));
        p.shake = keys.get(seg).shake;
        p.segment = seg;
        return p;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Centripetal-free uniform Catmull-Rom: smooth through b and c. */
    private static double catmull(double a, double b, double c, double d, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
    }

    public static CutsceneDoc example(String name) {
        CutsceneDoc d = new CutsceneDoc();
        d.title = name;
        return d;
    }
}
