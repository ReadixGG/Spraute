package org.zonarstudio.spraute_engine.script.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class EntityFacingUtil {
    private EntityFacingUtil() {}

    public static Map<String, Object> buildFacing(LivingEntity entity) {
        float yaw = entity.getYRot();
        float pitch = entity.getXRot();
        Vec3 look = entity.getViewVector(1.0f);
        String horizontal = horizontalFromYaw(yaw);
        int hx = 0;
        int hz = 0;
        switch (horizontal) {
            case "north" -> hz = -1;
            case "south" -> hz = 1;
            case "east" -> hx = 1;
            case "west" -> hx = -1;
            default -> { }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("yaw", (double) yaw);
        out.put("pitch", (double) pitch);
        out.put("horizontal", horizontal);
        out.put("direction", primaryDirection(yaw, pitch));
        out.put("dx", look.x);
        out.put("dy", look.y);
        out.put("dz", look.z);
        out.put("hx", hx);
        out.put("hz", hz);
        return out;
    }

    /** {@code null} if the vector is too small to define a facing. */
    public static float[] yawPitchFromDir(double dx, double dy, double dz) {
        if (dx * dx + dy * dy + dz * dz < 1.0e-12) return null;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(1.0e-6, horiz))));
        pitch = Math.max(-90f, Math.min(90f, pitch));
        return new float[]{yaw, pitch};
    }

    public static String horizontalFromYaw(float yaw) {
        float n = (yaw % 360f + 360f) % 360f;
        if (n >= 315f || n < 45f) return "south";
        if (n < 135f) return "west";
        if (n < 225f) return "north";
        return "east";
    }

    private static String primaryDirection(float yaw, float pitch) {
        if (pitch < -45f) return "up";
        if (pitch > 45f) return "down";
        return horizontalFromYaw(yaw);
    }
}
