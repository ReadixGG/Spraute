package org.sprautestudio.spraute_engine.runtime.models;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A turned box of a hitbox part, in the body's space (blocks from its feet, the world's axes): the game
 * itself only knows upright boxes, so a part is picked by the upright box around this one and the hit is
 * then checked against the turned box.
 *
 * <p>A part stands on its anchor: the anchor point is at the middle of its bottom face, and the part
 * turns around it — a tail anchored at the back and turned 90° lies backwards.
 */
public record PartShape(Vector3f center, Quaternionf rot, Vector3f half) {

    /** Where a bone of the model is now, in the body's space: its pivot and its turn. */
    public record BonePose(Vector3f pos, Quaternionf rot, long at) {
    }

    /**
     * The part's box. With a bone pose it rides on that bone (anchor = offset in the bone's frame); without
     * one it stands from the feet, turned with the body.
     */
    public static PartShape of(EntityLibrary.Def.Part p, float scale, float bodyYaw, BonePose bone) {
        Quaternionf base = bone != null ? new Quaternionf(bone.rot()) : new Quaternionf().rotationY((float) -Math.toRadians(bodyYaw));
        Vector3f origin = bone != null ? new Vector3f(bone.pos()) : new Vector3f();
        Vector3f anchor = base.transform(new Vector3f(p.x, p.y, p.z).mul(scale)).add(origin);
        Quaternionf rot = new Quaternionf(base).rotateY((float) Math.toRadians(p.ry)).rotateX((float) Math.toRadians(p.rx))
                .rotateZ((float) Math.toRadians(p.rz));
        float depth = p.depth > 0 ? p.depth : p.width;
        Vector3f half = new Vector3f(p.width / 2, p.height / 2, depth / 2).mul(scale);
        Vector3f center = rot.transform(new Vector3f(0, half.y, 0)).add(anchor);
        return new PartShape(center, rot, half);
    }

    /** The eight corners (bottom four, then top four). */
    public Vector3f[] corners() {
        Vector3f[] out = new Vector3f[8];
        int i = 0;
        for (int dy = -1; dy <= 1; dy += 2)
            for (int dx = -1; dx <= 1; dx += 2)
                for (int dz = -1; dz <= 1; dz += 2)
                    out[i++] = rot.transform(new Vector3f(dx * half.x, dy * half.y, dz * half.z)).add(center);
        return out;
    }

    /** The upright box around it, moved to a body standing at (x, y, z). */
    public AABB bounds(double x, double y, double z) {
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        for (Vector3f c : corners()) {
            x0 = Math.min(x0, c.x);
            y0 = Math.min(y0, c.y);
            z0 = Math.min(z0, c.z);
            x1 = Math.max(x1, c.x);
            y1 = Math.max(y1, c.y);
            z1 = Math.max(z1, c.z);
        }
        return new AABB(x + x0, y + y0, z + z0, x + x1, y + y1, z + z1);
    }

    /**
     * Where a segment (from → to, in the body's space) first enters the box: 0..1 along it, or -1 when it
     * misses. A point inside counts as 0.
     */
    public double hit(Vec3 from, Vec3 to) {
        Quaternionf inv = new Quaternionf(rot).conjugate();
        Vector3f a = inv.transform(new Vector3f((float) from.x, (float) from.y, (float) from.z).sub(center));
        Vector3f b = inv.transform(new Vector3f((float) to.x, (float) to.y, (float) to.z).sub(center));
        float[] o = {a.x, a.y, a.z}, d = {b.x - a.x, b.y - a.y, b.z - a.z}, h = {half.x, half.y, half.z};
        double t0 = 0, t1 = 1;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-7) {
                if (o[i] < -h[i] || o[i] > h[i]) return -1;
                continue;
            }
            double ta = (-h[i] - o[i]) / d[i], tb = (h[i] - o[i]) / d[i];
            if (ta > tb) {
                double t = ta;
                ta = tb;
                tb = t;
            }
            t0 = Math.max(t0, ta);
            t1 = Math.min(t1, tb);
            if (t0 > t1) return -1;
        }
        return t0;
    }
}
