package org.sprautestudio.spraute_engine.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * A zone of a project: a place in the world (a box of blocks, a ball or an upright cylinder).
 * Stories hear when the player comes in or goes out, may show its name on the screen and may make
 * its edge a wall that keeps the player in or out.
 */
public class ZoneDef {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** box, sphere, cylinder. */
    public String shape = "box";
    /** Box: two opposite corner blocks (both inside). */
    public int x1, y1, z1, x2 = 10, y2 = 5, z2 = 10;
    /** Sphere and cylinder: the centre; the cylinder goes up from cy by height. */
    public double cx, cy, cz, radius = 8, height = 6;
    /** "" — any world, else e.g. minecraft:overworld. */
    public String dimension = "";
    /** Off zones hear nothing and hold nobody (zone on / zone off). */
    public boolean enabled = true;
    /** Shown in the middle of the screen when the player comes in: "" — nothing. */
    public String title = "", subtitle = "";
    /** never, first (only the first time), always. */
    public String titleMode = "first";
    /** none, in (can't leave), out (can't come in). */
    public String wall = "none";
    public String wallText = "";
    /** The colour of its outline. */
    public int color = 0xFF4FC3F7;
    /** Not drawn while playing (only in the 3D editor). Off — every player sees its outline (an arena's edge…). */
    public boolean hideInGame = true;
    /** More pieces of the zone besides the main one (the fields above): the zone is all of them together. */
    public java.util.List<ZoneShape> more = new java.util.ArrayList<>();
    /** Which piece (in their order, from 0) is the edited one — the one in the fields above. */
    public int editIndex = 0;

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ZoneDef fromJson(String json) {
        ZoneDef d = null;
        try {
            d = GSON.fromJson(json, ZoneDef.class);
        } catch (Exception ignored) {
        }
        if (d == null) d = new ZoneDef();
        d.fix();
        return d;
    }

    public void fix() {
        if (shape == null || !(shape.equals("box") || shape.equals("sphere") || shape.equals("cylinder"))) shape = "box";
        if (dimension == null) dimension = "";
        if (title == null) title = "";
        if (subtitle == null) subtitle = "";
        if (titleMode == null) titleMode = "first";
        if (wall == null) wall = "none";
        if (wallText == null) wallText = "";
        radius = Math.max(0.5, radius);
        height = Math.max(0.5, height);
        if (more == null) more = new java.util.ArrayList<>();
        more.removeIf(java.util.Objects::isNull);
        for (ZoneShape s : more) s.fix();
        editIndex = Math.max(0, Math.min(editIndex, more.size()));
    }

    // ------------------------------------------------------------------ pieces

    /** The main piece (the one being edited), as a shape. */
    public ZoneShape primary() {
        ZoneShape s = new ZoneShape();
        s.shape = shape;
        s.x1 = x1;
        s.y1 = y1;
        s.z1 = z1;
        s.x2 = x2;
        s.y2 = y2;
        s.z2 = z2;
        s.cx = cx;
        s.cy = cy;
        s.cz = cz;
        s.radius = radius;
        s.height = height;
        return s;
    }

    public void setPrimary(ZoneShape s) {
        shape = s.shape;
        x1 = s.x1;
        y1 = s.y1;
        z1 = s.z1;
        x2 = s.x2;
        y2 = s.y2;
        z2 = s.z2;
        cx = s.cx;
        cy = s.cy;
        cz = s.cz;
        radius = s.radius;
        height = s.height;
    }

    /** Every piece: the main one first. */
    public java.util.List<ZoneShape> shapes() {
        java.util.List<ZoneShape> out = new java.util.ArrayList<>();
        out.add(primary());
        out.addAll(more);
        return out;
    }

    public int pieces() {
        return 1 + more.size();
    }

    /** All pieces in their own order (numbers that don't change while editing). */
    public java.util.List<ZoneShape> ordered() {
        java.util.List<ZoneShape> all = new java.util.ArrayList<>(more);
        all.add(Math.max(0, Math.min(editIndex, more.size())), primary());
        return all;
    }

    private void setAll(java.util.List<ZoneShape> all, int k) {
        k = Math.max(0, Math.min(k, all.size() - 1));
        setPrimary(all.get(k));
        java.util.List<ZoneShape> rest = new java.util.ArrayList<>(all);
        rest.remove(k);
        more = rest;
        editIndex = k;
    }

    /** Edits piece k (in their order, from 0). */
    public void editPiece(int k) {
        setAll(ordered(), k);
    }

    public void nextPiece() {
        setAll(ordered(), (editIndex + 1) % pieces());
    }

    /** The piece of {@link #more} at i, as a number in the order. */
    public int orderOfMore(int i) {
        return i < editIndex ? i : i + 1;
    }

    /** A new piece next to the edited one (a copy, moved aside); it becomes the edited one, last in the order. */
    public void addPiece() {
        ZoneShape cur = primary();
        ZoneShape n = cur.copy();
        n.move((int) Math.ceil(cur.maxX() - cur.minX()) + 1, 0, 0);
        java.util.List<ZoneShape> all = ordered();
        all.add(n);
        setAll(all, all.size() - 1);
    }

    /** Removes the edited piece (a neighbour becomes edited); the last piece stays. */
    public boolean removePiece() {
        if (more.isEmpty()) return false;
        java.util.List<ZoneShape> all = ordered();
        int k = editIndex;
        all.remove(k);
        setAll(all, Math.min(k, all.size() - 1));
        return true;
    }

    public double uMinX() {
        double v = minX();
        for (ZoneShape s : more) v = Math.min(v, s.minX());
        return v;
    }

    public double uMaxX() {
        double v = maxX();
        for (ZoneShape s : more) v = Math.max(v, s.maxX());
        return v;
    }

    public double uMinY() {
        double v = minY();
        for (ZoneShape s : more) v = Math.min(v, s.minY());
        return v;
    }

    public double uMaxY() {
        double v = maxY();
        for (ZoneShape s : more) v = Math.max(v, s.maxY());
        return v;
    }

    public double uMinZ() {
        double v = minZ();
        for (ZoneShape s : more) v = Math.min(v, s.minZ());
        return v;
    }

    public double uMaxZ() {
        double v = maxZ();
        for (ZoneShape s : more) v = Math.max(v, s.maxZ());
        return v;
    }

    public static ZoneDef example(String name, Vec3 at) {
        ZoneDef d = new ZoneDef();
        int x = (int) Math.floor(at.x), y = (int) Math.floor(at.y), z = (int) Math.floor(at.z);
        d.x1 = x - 5;
        d.y1 = y - 1;
        d.z1 = z - 5;
        d.x2 = x + 5;
        d.y2 = y + 5;
        d.z2 = z + 5;
        d.cx = x + 0.5;
        d.cy = y;
        d.cz = z + 0.5;
        d.title = name;
        return d;
    }

    // ------------------------------------------------------------------ geometry

    public double minX() {
        return shape.equals("box") ? Math.min(x1, x2) : cx - radius;
    }

    public double maxX() {
        return shape.equals("box") ? Math.max(x1, x2) + 1 : cx + radius;
    }

    public double minY() {
        return shape.equals("box") ? Math.min(y1, y2) : shape.equals("sphere") ? cy - radius : cy;
    }

    public double maxY() {
        return shape.equals("box") ? Math.max(y1, y2) + 1 : shape.equals("sphere") ? cy + radius : cy + height;
    }

    public double minZ() {
        return shape.equals("box") ? Math.min(z1, z2) : cz - radius;
    }

    public double maxZ() {
        return shape.equals("box") ? Math.max(z1, z2) + 1 : cz + radius;
    }

    /** Inside any piece. */
    public boolean contains(double x, double y, double z) {
        if (containsMain(x, y, z)) return true;
        for (ZoneShape s : more) if (s.contains(x, y, z)) return true;
        return false;
    }

    /** Inside the main piece. */
    public boolean containsMain(double x, double y, double z) {
        switch (shape) {
            case "sphere" -> {
                double dx = x - cx, dy = y - cy, dz = z - cz;
                return dx * dx + dy * dy + dz * dz <= radius * radius;
            }
            case "cylinder" -> {
                double dx = x - cx, dz = z - cz;
                return y >= cy && y <= cy + height && dx * dx + dz * dz <= radius * radius;
            }
            default -> {
                return x >= minX() && x < maxX() && y >= minY() && y < maxY() && z >= minZ() && z < maxZ();
            }
        }
    }

    /** Does an entity standing at feet (x, y, z) count as inside: its feet or a bit above them. */
    public boolean holds(double x, double y, double z) {
        return contains(x, y + 0.1, z) || contains(x, y + 1, z);
    }

    /** The middle of all pieces (on the floor). */
    public Vec3 center() {
        if (more.isEmpty()) return new Vec3((minX() + maxX()) / 2, shape.equals("sphere") ? cy : minY(), (minZ() + maxZ()) / 2);
        return new Vec3((uMinX() + uMaxX()) / 2, uMinY(), (uMinZ() + uMaxZ()) / 2);
    }

    /** A random point inside, on the floor for boxes and cylinders (for spawning things); bigger pieces get more. */
    public Vec3 randomPoint(Random r) {
        java.util.List<ZoneShape> all = shapes();
        double total = 0;
        for (ZoneShape s : all) total += Math.max(0.001, s.volume());
        double pick = r.nextDouble() * total;
        for (ZoneShape s : all) {
            pick -= Math.max(0.001, s.volume());
            if (pick <= 0) return s.randomPoint(r);
        }
        return all.get(0).randomPoint(r);
    }

    /** The main piece's size, and how many more there are. */
    public String size() {
        return primary().size() + (more.isEmpty() ? "" : "  · частей: " + pieces());
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }
}
