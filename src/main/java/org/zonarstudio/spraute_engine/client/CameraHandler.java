package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.network.CameraPacket;

import java.lang.reflect.Field;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public class CameraHandler {

    private static boolean active = false;

    private static double targetX, targetY, targetZ;
    private static float targetYaw, targetPitch;

    private static double startX, startY, startZ;
    private static float startYaw, startPitch;

    private static long startTimeMs;
    private static float durationSec;
    private static boolean smooth;
    private static float smoothTimeSec;
    private static String dimension = "";

    private enum Phase { TRANSITION_IN, HOLDING, TRANSITION_OUT, INACTIVE }
    private static Phase phase = Phase.INACTIVE;

    private static double currentX, currentY, currentZ;
    private static float currentYaw, currentPitch;

    private static double moveFromX, moveFromY, moveFromZ;
    private static float moveFromYaw, moveFromPitch;
    private static double moveToX, moveToY, moveToZ;
    private static float moveToYaw, moveToPitch;
    private static long moveStartMs;
    private static float moveSmoothTimeSec;
    private static boolean moving = false;

    private static byte lookAtMode = CameraPacket.LOOK_NONE;
    private static int lookAtEntityId = -1;
    private static double lookAtX, lookAtY, lookAtZ;
    private static boolean lookAtIsBlock = false;

    private static Field cameraPositionField;

    static {
        try {
            for (Field f : Camera.class.getDeclaredFields()) {
                if (f.getType() == Vec3.class) {
                    f.setAccessible(true);
                    cameraPositionField = f;
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    public static boolean isActive() {
        return active;
    }

    public static void startCamera(double x, double y, double z,
                                   float yaw, float pitch,
                                   float time, boolean doSmooth, float doSmoothTime,
                                   String dim,
                                   byte laMode, int laEntityId,
                                   double laX, double laY, double laZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity camEntity = mc.cameraEntity != null ? mc.cameraEntity : mc.player;
        startX = camEntity.getX();
        startY = camEntity.getEyeY();
        startZ = camEntity.getZ();
        startYaw = camEntity.getYRot();
        startPitch = camEntity.getXRot();

        targetX = x;
        targetY = y;
        targetZ = z;

        durationSec = time;
        smooth = doSmooth;
        smoothTimeSec = Math.max(doSmoothTime, 0.01f);
        dimension = dim;

        lookAtMode = laMode;
        lookAtEntityId = laEntityId;
        lookAtX = laX;
        lookAtY = laY;
        lookAtZ = laZ;
        lookAtIsBlock = (laMode != CameraPacket.LOOK_NONE && laEntityId == -1);

        if (lookAtMode == CameraPacket.LOOK_ONCE) {
            float[] angles = computeLookAtAngles(x, y, z);
            if (angles != null) {
                targetYaw = angles[0];
                targetPitch = angles[1];
                lookAtMode = CameraPacket.LOOK_NONE;
            } else {
                targetYaw = yaw;
                targetPitch = pitch;
            }
        } else if (lookAtMode == CameraPacket.LOOK_TRACK) {
            float[] angles = computeLookAtAngles(x, y, z);
            if (angles != null) {
                targetYaw = angles[0];
                targetPitch = angles[1];
            } else {
                targetYaw = yaw;
                targetPitch = pitch;
            }
        } else {
            targetYaw = yaw;
            targetPitch = pitch;
        }

        currentX = startX;
        currentY = startY;
        currentZ = startZ;
        currentYaw = startYaw;
        currentPitch = startPitch;

        startTimeMs = System.currentTimeMillis();
        phase = smooth ? Phase.TRANSITION_IN : Phase.HOLDING;
        active = true;
        moving = false;

        if (!smooth) {
            currentX = targetX;
            currentY = targetY;
            currentZ = targetZ;
            currentYaw = targetYaw;
            currentPitch = targetPitch;
        }
    }

    public static void stopCamera() {
        if (!active) return;

        lookAtMode = CameraPacket.LOOK_NONE;

        if (smooth) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                Entity camEntity = mc.cameraEntity != null ? mc.cameraEntity : mc.player;
                targetX = camEntity.getX();
                targetY = camEntity.getEyeY();
                targetZ = camEntity.getZ();
                targetYaw = camEntity.getYRot();
                targetPitch = camEntity.getXRot();
            }
            startX = currentX;
            startY = currentY;
            startZ = currentZ;
            startYaw = currentYaw;
            startPitch = currentPitch;
            startTimeMs = System.currentTimeMillis();
            phase = Phase.TRANSITION_OUT;
            moving = false;
        } else {
            resetToInactive();
        }
    }

    public static void moveCamera(double x, double y, double z,
                                  float yaw, float pitch, float moveSmoothTime) {
        if (!active) return;

        moveFromX = currentX;
        moveFromY = currentY;
        moveFromZ = currentZ;
        moveFromYaw = currentYaw;
        moveFromPitch = currentPitch;

        moveToX = x;
        moveToY = y;
        moveToZ = z;
        moveToYaw = yaw;
        moveToPitch = pitch;
        moveSmoothTimeSec = Math.max(moveSmoothTime, 0.01f);
        moveStartMs = System.currentTimeMillis();
        moving = true;

        targetX = x;
        targetY = y;
        targetZ = z;
        targetYaw = yaw;
        targetPitch = pitch;
    }

    private static void resetToInactive() {
        active = false;
        phase = Phase.INACTIVE;
        moving = false;
        lookAtMode = CameraPacket.LOOK_NONE;
        lookAtEntityId = -1;
    }

    private static float smoothstep(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return from + diff * t;
    }

    private static float[] computeLookAtAngles(double camX, double camY, double camZ) {
        double lx, ly, lz;

        if (!lookAtIsBlock && lookAtEntityId != -1) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;
            Entity e = mc.level.getEntity(lookAtEntityId);
            if (e == null) return null;
            lx = e.getX();
            ly = e.getEyeY();
            lz = e.getZ();
        } else if (lookAtIsBlock || lookAtMode != CameraPacket.LOOK_NONE) {
            lx = lookAtX;
            ly = lookAtY;
            lz = lookAtZ;
        } else {
            return null;
        }

        double dx = lx - camX;
        double dy = ly - camY;
        double dz = lz - camZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));

        return new float[]{yaw, pitch};
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active || phase == Phase.INACTIVE) return;

        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        if (moving) {
            float elapsed = (now - moveStartMs) / 1000f;
            float t = Math.min(elapsed / moveSmoothTimeSec, 1f);
            float s = smoothstep(t);

            currentX = moveFromX + (moveToX - moveFromX) * s;
            currentY = moveFromY + (moveToY - moveFromY) * s;
            currentZ = moveFromZ + (moveToZ - moveFromZ) * s;
            currentYaw = lerpAngle(moveFromYaw, moveToYaw, s);
            currentPitch = moveFromPitch + (moveToPitch - moveFromPitch) * s;

            if (t >= 1f) {
                moving = false;
            }
        }

        switch (phase) {
            case TRANSITION_IN -> {
                float elapsed = (now - startTimeMs) / 1000f;
                float t = Math.min(elapsed / smoothTimeSec, 1f);
                float s = smoothstep(t);

                if (lookAtMode == CameraPacket.LOOK_TRACK) {
                    float[] angles = computeLookAtAngles(
                            startX + (targetX - startX) * s,
                            startY + (targetY - startY) * s,
                            startZ + (targetZ - startZ) * s);
                    if (angles != null) {
                        targetYaw = angles[0];
                        targetPitch = angles[1];
                    }
                }

                if (!moving) {
                    currentX = startX + (targetX - startX) * s;
                    currentY = startY + (targetY - startY) * s;
                    currentZ = startZ + (targetZ - startZ) * s;
                    currentYaw = lerpAngle(startYaw, targetYaw, s);
                    currentPitch = startPitch + (targetPitch - startPitch) * s;
                }

                if (t >= 1f) {
                    phase = Phase.HOLDING;
                    startTimeMs = System.currentTimeMillis();
                }
            }
            case HOLDING -> {
                if (lookAtMode == CameraPacket.LOOK_TRACK && !moving) {
                    float[] angles = computeLookAtAngles(currentX, currentY, currentZ);
                    if (angles != null) {
                        targetYaw = angles[0];
                        targetPitch = angles[1];
                    }
                }

                if (!moving) {
                    currentX = targetX;
                    currentY = targetY;
                    currentZ = targetZ;
                    currentYaw = targetYaw;
                    currentPitch = targetPitch;
                }

                if (durationSec > 0f) {
                    float elapsed = (now - startTimeMs) / 1000f;
                    if (elapsed >= durationSec) {
                        if (smooth) {
                            Entity camEntity = mc.cameraEntity != null ? mc.cameraEntity : mc.player;
                            if (camEntity != null) {
                                targetX = camEntity.getX();
                                targetY = camEntity.getEyeY();
                                targetZ = camEntity.getZ();
                                targetYaw = camEntity.getYRot();
                                targetPitch = camEntity.getXRot();
                            }
                            startX = currentX;
                            startY = currentY;
                            startZ = currentZ;
                            startYaw = currentYaw;
                            startPitch = currentPitch;
                            startTimeMs = System.currentTimeMillis();
                            phase = Phase.TRANSITION_OUT;
                            moving = false;
                            lookAtMode = CameraPacket.LOOK_NONE;
                        } else {
                            resetToInactive();
                            return;
                        }
                    }
                }
            }
            case TRANSITION_OUT -> {
                float elapsed = (now - startTimeMs) / 1000f;
                float t = Math.min(elapsed / smoothTimeSec, 1f);
                float s = smoothstep(t);

                Entity camEntity = mc.cameraEntity != null ? mc.cameraEntity : mc.player;
                if (camEntity != null) {
                    double playerX = camEntity.getX();
                    double playerY = camEntity.getEyeY();
                    double playerZ = camEntity.getZ();
                    float playerYaw = camEntity.getYRot();
                    float playerPitch = camEntity.getXRot();

                    currentX = startX + (playerX - startX) * s;
                    currentY = startY + (playerY - startY) * s;
                    currentZ = startZ + (playerZ - startZ) * s;
                    currentYaw = lerpAngle(startYaw, playerYaw, s);
                    currentPitch = startPitch + (playerPitch - startPitch) * s;
                }

                if (t >= 1f) {
                    resetToInactive();
                    return;
                }
            }
        }

        event.setYaw(currentYaw);
        event.setPitch(currentPitch);

        if (cameraPositionField != null) {
            try {
                cameraPositionField.set(event.getCamera(), new Vec3(currentX, currentY, currentZ));
            } catch (Exception ignored) {}
        }
    }
}
