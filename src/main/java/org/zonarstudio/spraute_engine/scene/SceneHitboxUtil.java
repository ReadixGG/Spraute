package org.zonarstudio.spraute_engine.scene;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** World-space oriented-box helpers for scene HITBOX nodes. */
public final class SceneHitboxUtil {
    private SceneHitboxUtil() {}

    public static final class OrientedBox {
        public final double cx, cy, cz;
        public final double halfWidth, halfHeight, halfDepth;
        public final double cos, sin;

        private OrientedBox(double cx, double cy, double cz,
                            double halfWidth, double halfHeight, double halfDepth,
                            double cos, double sin) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.halfWidth = halfWidth;
            this.halfHeight = halfHeight;
            this.halfDepth = halfDepth;
            this.cos = cos;
            this.sin = sin;
        }
    }

    public static AABB worldAabb(SceneNode node, SceneTransform world) {
        return worldAabb(world, node.hitboxWidth, node.hitboxHeight, node.hitboxDepth,
                node.hitboxOffsetX, node.hitboxOffsetY, node.hitboxOffsetZ);
    }

    public static AABB worldAabb(SceneTransform world, float width, float height, float depth,
                                   float offsetX, float offsetY, float offsetZ) {
        OrientedBox box = orientedBox(world, width, height, depth, offsetX, offsetY, offsetZ);
        double extentX = Math.abs(box.cos) * box.halfWidth + Math.abs(box.sin) * box.halfDepth;
        double extentZ = Math.abs(box.sin) * box.halfWidth + Math.abs(box.cos) * box.halfDepth;
        return new AABB(box.cx - extentX, box.cy - box.halfHeight, box.cz - extentZ,
                box.cx + extentX, box.cy + box.halfHeight, box.cz + extentZ);
    }

    public static OrientedBox orientedBox(SceneNode node, SceneTransform world) {
        return orientedBox(world, node.hitboxWidth, node.hitboxHeight, node.hitboxDepth,
                node.hitboxOffsetX, node.hitboxOffsetY, node.hitboxOffsetZ);
    }

    public static OrientedBox orientedBox(SceneTransform world,
                                          float width, float height, float depth,
                                          float offsetX, float offsetY, float offsetZ) {
        double yaw = Math.toRadians(world.ry);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double ox = offsetX * world.sx;
        double oy = offsetY * world.sy;
        double oz = offsetZ * world.sz;
        double centerX = world.px + ox * cos + oz * sin;
        double centerZ = world.pz - ox * sin + oz * cos;
        double bottomY = world.py + oy;
        double halfWidth = Math.max(0.01, width) * Math.abs(world.sx) * 0.5;
        double boxHeight = Math.max(0.01, height) * Math.abs(world.sy);
        double halfDepth = Math.max(0.01, depth) * Math.abs(world.sz) * 0.5;
        return new OrientedBox(centerX, bottomY + boxHeight * 0.5, centerZ,
                halfWidth, boxHeight * 0.5, halfDepth, cos, sin);
    }

    /** Exact AABB-vs-yaw-rotated-box overlap test (2D SAT + vertical axis). */
    public static boolean intersects(AABB mover, OrientedBox box) {
        return separation(mover, box) != null;
    }

    /** Minimum SAT translation to separate a player AABB from the rotated hitbox. */
    public static Vec3 resolvePushOut(AABB mover, OrientedBox box) {
        Separation separation = separation(mover, box, false);
        if (separation == null) return Vec3.ZERO;
        double amount = separation.overlap + 1.0E-4;
        return new Vec3(separation.axisX * separation.sign * amount,
                separation.axisY * separation.sign * amount,
                separation.axisZ * separation.sign * amount);
    }

    /**
     * Solid wall push: prefer horizontal separation so side contacts feel like walls,
     * not sticky floors. Use upward push only when clearly standing on top.
     */
    public static Vec3 resolveSolidPush(AABB mover, OrientedBox box, Vec3 velocity) {
        double top = box.cy + box.halfHeight;
        boolean onTop = mover.minY >= top - 0.35
                && mover.minY <= top + 0.05
                && Math.abs((mover.minX + mover.maxX) * 0.5 - box.cx) < box.halfWidth + 0.4
                && Math.abs((mover.minZ + mover.maxZ) * 0.5 - box.cz) < box.halfDepth + 0.4;

        Separation sep = onTop
                ? separation(mover, box, false)
                : separationHorizontalOnly(mover, box);
        if (sep == null) sep = separation(mover, box, false);
        if (sep == null) return Vec3.ZERO;

        double amount = sep.overlap + 1.0E-3;
        return new Vec3(sep.axisX * sep.sign * amount,
                sep.axisY * sep.sign * amount,
                sep.axisZ * sep.sign * amount);
    }

    /** Eight world-space corners, bottom four followed by top four. */
    public static Vec3[] corners(OrientedBox box) {
        Vec3[] out = new Vec3[8];
        int index = 0;
        for (int y = -1; y <= 1; y += 2) {
            for (int z = -1; z <= 1; z += 2) {
                for (int x = -1; x <= 1; x += 2) {
                    double lx = x * box.halfWidth;
                    double lz = z * box.halfDepth;
                    out[index++] = new Vec3(
                            box.cx + lx * box.cos + lz * box.sin,
                            box.cy + y * box.halfHeight,
                            box.cz - lx * box.sin + lz * box.cos);
                }
            }
        }
        return out;
    }

    private static Separation separation(AABB mover, OrientedBox box) {
        return separation(mover, box, false);
    }

    private static Separation separation(AABB mover, OrientedBox box, boolean preferHorizontal) {
        double px = (mover.minX + mover.maxX) * 0.5;
        double py = (mover.minY + mover.maxY) * 0.5;
        double pz = (mover.minZ + mover.maxZ) * 0.5;
        double phx = (mover.maxX - mover.minX) * 0.5;
        double phy = (mover.maxY - mover.minY) * 0.5;
        double phz = (mover.maxZ - mover.minZ) * 0.5;
        double dx = px - box.cx;
        double dy = py - box.cy;
        double dz = pz - box.cz;

        Separation ySep = axis(0, 1, 0, dy, phy + box.halfHeight - Math.abs(dy), null);
        if (ySep == null) return null;

        Separation best = preferHorizontal ? null : ySep;

        double boxOnX = Math.abs(box.cos) * box.halfWidth
                + Math.abs(box.sin) * box.halfDepth;
        best = axis(1, 0, 0, dx, phx + boxOnX - Math.abs(dx), best);
        if (best == null && !preferHorizontal) return null;
        if (best == null) return null; // no X overlap and we skipped Y

        double boxOnZ = Math.abs(box.sin) * box.halfWidth
                + Math.abs(box.cos) * box.halfDepth;
        best = axis(0, 0, 1, dz, phz + boxOnZ - Math.abs(dz), best);
        if (best == null) return null;

        double du = dx * box.cos - dz * box.sin;
        double playerOnU = phx * Math.abs(box.cos) + phz * Math.abs(box.sin);
        best = axis(box.cos, 0, -box.sin, du,
                playerOnU + box.halfWidth - Math.abs(du), best);
        if (best == null) return null;

        double dv = dx * box.sin + dz * box.cos;
        double playerOnV = phx * Math.abs(box.sin) + phz * Math.abs(box.cos);
        best = axis(box.sin, 0, box.cos, dv,
                playerOnV + box.halfDepth - Math.abs(dv), best);
        if (best == null) return null;

        if (preferHorizontal && best == null) {
            // Should not happen; fall back to Y.
            return ySep;
        }
        // If preferHorizontal found a horizontal axis, use it; else allow Y for standing-on-top.
        if (preferHorizontal && Math.abs(best.axisY) > 0.5) {
            Separation horiz = separationHorizontalOnly(mover, box);
            double top = box.cy + box.halfHeight;
            boolean onTop = mover.minY >= top - 0.35 && dy > 0;
            if (!onTop && horiz != null) return horiz;
            return ySep;
        }
        return best;
    }

    private static Separation separationHorizontalOnly(AABB mover, OrientedBox box) {
        double px = (mover.minX + mover.maxX) * 0.5;
        double py = (mover.minY + mover.maxY) * 0.5;
        double pz = (mover.minZ + mover.maxZ) * 0.5;
        double phx = (mover.maxX - mover.minX) * 0.5;
        double phy = (mover.maxY - mover.minY) * 0.5;
        double phz = (mover.maxZ - mover.minZ) * 0.5;
        double dx = px - box.cx;
        double dy = py - box.cy;
        double dz = pz - box.cz;

        // Must still overlap vertically or we're not colliding.
        if (phy + box.halfHeight - Math.abs(dy) <= 0) return null;

        Separation best = null;
        double boxOnX = Math.abs(box.cos) * box.halfWidth + Math.abs(box.sin) * box.halfDepth;
        best = axis(1, 0, 0, dx, phx + boxOnX - Math.abs(dx), best);
        if (best == null) return null;
        double boxOnZ = Math.abs(box.sin) * box.halfWidth + Math.abs(box.cos) * box.halfDepth;
        best = axis(0, 0, 1, dz, phz + boxOnZ - Math.abs(dz), best);
        if (best == null) return null;
        double du = dx * box.cos - dz * box.sin;
        double playerOnU = phx * Math.abs(box.cos) + phz * Math.abs(box.sin);
        best = axis(box.cos, 0, -box.sin, du, playerOnU + box.halfWidth - Math.abs(du), best);
        if (best == null) return null;
        double dv = dx * box.sin + dz * box.cos;
        double playerOnV = phx * Math.abs(box.sin) + phz * Math.abs(box.cos);
        return axis(box.sin, 0, box.cos, dv, playerOnV + box.halfDepth - Math.abs(dv), best);
    }

    private static Separation axis(double x, double y, double z, double projection,
                                   double overlap, Separation current) {
        if (overlap <= 0) return null;
        Separation candidate = new Separation(x, y, z,
                projection >= 0 ? 1 : -1, overlap);
        return current == null || overlap < current.overlap ? candidate : current;
    }

    private static final class Separation {
        final double axisX, axisY, axisZ, sign, overlap;

        Separation(double axisX, double axisY, double axisZ, double sign, double overlap) {
            this.axisX = axisX;
            this.axisY = axisY;
            this.axisZ = axisZ;
            this.sign = sign;
            this.overlap = overlap;
        }
    }
}
