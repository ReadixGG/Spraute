package org.zonarstudio.spraute_engine.scene;

/**
 * Local TRS transform of a scene node. Rotation is Euler degrees.
 * World composition uses parent yaw (Y rotation) for position inheritance —
 * enough for NPC/scene grouping without a full matrix stack.
 */
public final class SceneTransform {
    public double px, py, pz;
    public double rx, ry, rz;
    public double sx = 1, sy = 1, sz = 1;

    public SceneTransform copy() {
        SceneTransform t = new SceneTransform();
        t.px = px; t.py = py; t.pz = pz;
        t.rx = rx; t.ry = ry; t.rz = rz;
        t.sx = sx; t.sy = sy; t.sz = sz;
        return t;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SceneTransform t)) return false;
        return Double.compare(t.px, px) == 0 && Double.compare(t.py, py) == 0 && Double.compare(t.pz, pz) == 0
                && Double.compare(t.rx, rx) == 0 && Double.compare(t.ry, ry) == 0 && Double.compare(t.rz, rz) == 0
                && Double.compare(t.sx, sx) == 0 && Double.compare(t.sy, sy) == 0 && Double.compare(t.sz, sz) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(px, py, pz, rx, ry, rz, sx, sy, sz);
    }

    /** World transform of a child given the parent's world transform. */
    public static SceneTransform composeWorld(SceneTransform parentWorld, SceneTransform local) {
        if (parentWorld == null) return local.copy();
        SceneTransform w = new SceneTransform();
        double rad = Math.toRadians(parentWorld.ry);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double lx = local.px * parentWorld.sx;
        double ly = local.py * parentWorld.sy;
        double lz = local.pz * parentWorld.sz;
        w.px = parentWorld.px + lx * cos + lz * sin;
        w.py = parentWorld.py + ly;
        w.pz = parentWorld.pz - lx * sin + lz * cos;
        w.rx = parentWorld.rx + local.rx;
        w.ry = parentWorld.ry + local.ry;
        w.rz = parentWorld.rz + local.rz;
        w.sx = parentWorld.sx * local.sx;
        w.sy = parentWorld.sy * local.sy;
        w.sz = parentWorld.sz * local.sz;
        return w;
    }

    /** Local transform that keeps {@code world} unchanged under a new parent world transform. */
    public static SceneTransform toLocal(SceneTransform parentWorld, SceneTransform world) {
        if (parentWorld == null) return world.copy();
        SceneTransform l = new SceneTransform();
        double rad = Math.toRadians(parentWorld.ry);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double dx = world.px - parentWorld.px;
        double dy = world.py - parentWorld.py;
        double dz = world.pz - parentWorld.pz;
        // Inverse yaw rotation, then divide by parent scale.
        double ux = dx * cos - dz * sin;
        double uz = dx * sin + dz * cos;
        l.px = parentWorld.sx != 0 ? ux / parentWorld.sx : ux;
        l.py = parentWorld.sy != 0 ? dy / parentWorld.sy : dy;
        l.pz = parentWorld.sz != 0 ? uz / parentWorld.sz : uz;
        l.rx = world.rx - parentWorld.rx;
        l.ry = world.ry - parentWorld.ry;
        l.rz = world.rz - parentWorld.rz;
        l.sx = parentWorld.sx != 0 ? world.sx / parentWorld.sx : world.sx;
        l.sy = parentWorld.sy != 0 ? world.sy / parentWorld.sy : world.sy;
        l.sz = parentWorld.sz != 0 ? world.sz / parentWorld.sz : world.sz;
        return l;
    }
}
