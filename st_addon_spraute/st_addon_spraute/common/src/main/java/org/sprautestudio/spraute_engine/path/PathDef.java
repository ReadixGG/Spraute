package org.sprautestudio.spraute_engine.path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A route through the world that characters walk ({@code walk alice "обход"}): points in order, each
 * with an optional pause, a speed of its own and an animation to play while standing there. Walked once,
 * in a circle, or there and back.
 */
public class PathDef {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public List<Point> points = new ArrayList<>();
    /** once — to the end and stop; loop — from the last back to the first, forever; pingpong — there and back, forever. */
    public String mode = "once";
    /** Walking speed, the same for every character: 1 — an ordinary walk (2.6 blocks a second), 2 — a run. */
    public double speed = 1;
    /** Line colour in the editors. */
    public int color = 0xFFFFAA33;
    /** Dimension where it is ("" — any). */
    public String dimension = "";

    public static class Point {
        public double x, y, z;
        /** Seconds to stand at it. */
        public double wait;
        /** Its own speed to it (0 — the path's). */
        public double speed;
        /** An animation while standing ("" — none). */
        public String anim = "";
        /** Where to look while standing: yaw in degrees (null — as it came). */
        public Double yaw;

        public Point() {
        }

        public Point(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Vec3 vec() {
            return new Vec3(x, y, z);
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static PathDef fromJson(String json) {
        PathDef d = null;
        try {
            d = GSON.fromJson(json, PathDef.class);
        } catch (Exception ignored) {
        }
        if (d == null) d = new PathDef();
        d.fix();
        return d;
    }

    public void fix() {
        if (points == null) points = new ArrayList<>();
        points.removeIf(java.util.Objects::isNull);
        if (mode == null || !List.of("once", "loop", "pingpong").contains(mode)) mode = "once";
        if (speed <= 0) speed = 1;
        if (dimension == null) dimension = "";
        for (Point p : points) {
            if (p.anim == null) p.anim = "";
            p.wait = Math.max(0, p.wait);
        }
    }

    public double length() {
        double l = 0;
        for (int i = 1; i < points.size(); i++) l += points.get(i).vec().distanceTo(points.get(i - 1).vec());
        return l;
    }

    public static PathDef example(Vec3 at) {
        PathDef d = new PathDef();
        double x = Math.floor(at.x) + 0.5, y = Math.floor(at.y), z = Math.floor(at.z) + 0.5;
        d.points.add(new Point(x, y, z));
        d.points.add(new Point(x + 6, y, z));
        Point p = new Point(x + 6, y, z + 6);
        p.wait = 2;
        d.points.add(p);
        d.points.add(new Point(x, y, z + 6));
        return d;
    }
}
