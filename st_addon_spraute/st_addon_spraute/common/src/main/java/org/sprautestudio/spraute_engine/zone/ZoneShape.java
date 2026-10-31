package org.sprautestudio.spraute_engine.zone;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** One piece of a zone: a box of blocks, a ball or an upright cylinder. A zone is all its pieces together. */
public class ZoneShape {
    /** box, sphere, cylinder. */
    public String shape = "box";
    public int x1, y1, z1, x2 = 10, y2 = 5, z2 = 10;
    public double cx, cy, cz, radius = 8, height = 6;

    public ZoneShape copy() {
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

    public void fix() {
        if (shape == null || !(shape.equals("box") || shape.equals("sphere") || shape.equals("cylinder"))) shape = "box";
        radius = Math.max(0.5, radius);
        height = Math.max(0.5, height);
    }

    public void move(int dx, int dy, int dz) {
        x1 += dx;
        x2 += dx;
        y1 += dy;
        y2 += dy;
        z1 += dz;
        z2 += dz;
        cx += dx;
        cy += dy;
        cz += dz;
    }

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

    public boolean contains(double x, double y, double z) {
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

    public double volume() {
        return switch (shape) {
            case "sphere" -> 4.0 / 3 * Math.PI * radius * radius * radius;
            case "cylinder" -> Math.PI * radius * radius * height;
            default -> (maxX() - minX()) * (maxY() - minY()) * (maxZ() - minZ());
        };
    }

    /** A random point inside, on the floor for boxes and cylinders. */
    public Vec3 randomPoint(Random r) {
        for (int i = 0; i < 64; i++) {
            double x = minX() + r.nextDouble() * (maxX() - minX());
            double z = minZ() + r.nextDouble() * (maxZ() - minZ());
            double y = shape.equals("sphere") ? cy - radius + r.nextDouble() * radius * 2 : minY();
            if (contains(x, y + 0.01, z)) return new Vec3(x, y, z);
        }
        return new Vec3((minX() + maxX()) / 2, shape.equals("sphere") ? cy : minY(), (minZ() + maxZ()) / 2);
    }

    public String size() {
        return switch (shape) {
            case "sphere" -> "шар, радиус " + fmt(radius);
            case "cylinder" -> "цилиндр, радиус " + fmt(radius) + ", высота " + fmt(height);
            default -> (Math.abs(x2 - x1) + 1) + " × " + (Math.abs(y2 - y1) + 1) + " × " + (Math.abs(z2 - z1) + 1) + " блоков";
        };
    }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }
}
