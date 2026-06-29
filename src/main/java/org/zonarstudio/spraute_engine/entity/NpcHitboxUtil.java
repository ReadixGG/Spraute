package org.zonarstudio.spraute_engine.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.zonarstudio.spraute_engine.core.math.SpMatrix4;
import org.zonarstudio.spraute_engine.core.math.SpVec3;

/** World-space AABB helpers for NPC hitboxes. */
public final class NpcHitboxUtil {
    private NpcHitboxUtil() {}

    public static final float PRESET_PLAYER_W = 0.6f;
    public static final float PRESET_PLAYER_H = 1.8f;
    public static final float PRESET_SMALL_W = 0.4f;
    public static final float PRESET_SMALL_H = 1.0f;
    public static final float PRESET_LARGE_W = 1.2f;
    public static final float PRESET_LARGE_H = 2.4f;

    public static AABB mainHitbox(SprauteNpcEntity entity, float width, float height,
                                  float offsetX, float offsetY, float offsetZ) {
        double[] rotated = rotateXZ(offsetX, offsetZ, entity.yBodyRot);
        double cx = entity.getX() + rotated[0];
        double cy = entity.getY() + offsetY;
        double cz = entity.getZ() + rotated[1];
        double hw = width * 0.5;
        // Bottom of AABB is at the entity's feet (cy); top is cy + full height
        return new AABB(cx - hw, cy, cz - hw, cx + hw, cy + height, cz + hw);
    }

    public static AABB boneHitbox(SprauteNpcEntity entity, SpMatrix4 boneMatrix, NpcBoneHitbox hb) {
        SpVec3 pivot = boneMatrixToWorld(entity, boneMatrix);
        double[] rotated = rotateXZ(hb.offsetX / 16.0, hb.offsetZ / 16.0, entity.yBodyRot);
        double ox = rotated[0];
        double oy = hb.offsetY / 16.0;
        double oz = rotated[1];
        double cx = pivot.x + ox;
        double cy = pivot.y + oy;
        double cz = pivot.z + oz;
        double hw = hb.width * 0.5;
        double hh = hb.height * 0.5;
        double hd = hb.depth * 0.5;
        return new AABB(cx - hw, cy - hh, cz - hd, cx + hw, cy + hh, cz + hd);
    }

    public static SpVec3 boneMatrixToWorld(SprauteNpcEntity entity, SpMatrix4 matrix) {
        float mx = matrix.m[12] / 16.0f;
        float my = matrix.m[13] / 16.0f;
        float mz = matrix.m[14] / 16.0f;

        float rad = (float) Math.toRadians(180.0 - entity.yBodyRot);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float rotX = mx * cos + mz * sin;
        float rotZ = -mx * sin + mz * cos;

        return new SpVec3(
                (float) (entity.getX() + rotX),
                (float) (entity.getY() + my),
                (float) (entity.getZ() + rotZ)
        );
    }

    public static AABB union(AABB base, AABB other) {
        if (base == null) return other;
        if (other == null) return base;
        return base.minmax(other);
    }

    private static double[] rotateXZ(double localX, double localZ, float bodyYaw) {
        float rad = (float) Math.toRadians(180.0 - bodyYaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new double[]{
                localX * cos + localZ * sin,
                -localX * sin + localZ * cos
        };
    }
}
