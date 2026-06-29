package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteEndMode;
import org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath;
import org.zonarstudio.spraute_engine.network.CameraPacket;

import java.util.List;

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

    private static boolean lockMovement = false;
    private static byte hideGui = CameraPacket.HIDE_NONE;

    // ---- Route playback (continuous, frame-rate independent, no smoothstep jerk) ----
    private static boolean routeMode = false;
    private static List<CameraRoutePath.Sample> routeSamples = List.of();
    private static float[] routeTimeline = new float[0];
    private static long routeStartMs;
    private static float routeTotalDuration;
    private static float routeReturnSmooth;
    private static CameraRouteEndMode routeEndMode;
    private static boolean routeOverrideLook;
    private static double routeLookX, routeLookY, routeLookZ;

    // Smooth cross-fade transition: blend from previous camera pos to route[0] before playback
    private static boolean routeTransActive = false;
    private static double routeTransFromX, routeTransFromY, routeTransFromZ;
    private static float routeTransFromYaw, routeTransFromPitch;
    private static float routeTransDurSec;
    private static long routeTransStartMs;

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

    public static boolean isLockMovement() {
        return active && lockMovement;
    }

    public static byte getHideGui() {
        return active ? hideGui : CameraPacket.HIDE_NONE;
    }

    public static boolean shouldHideVanillaHud() {
        byte mode = getHideGui();
        return mode == CameraPacket.HIDE_MINECRAFT || mode == CameraPacket.HIDE_ALL;
    }

    public static boolean shouldHideScriptGui() {
        return active && hideGui == CameraPacket.HIDE_ALL;
    }

    public static void startCamera(double x, double y, double z,
                                   float yaw, float pitch,
                                   float time, boolean doSmooth, float doSmoothTime,
                                   String dim,
                                   byte laMode, int laEntityId,
                                   double laX, double laY, double laZ,
                                   boolean lockPlayerMovement, byte hideGuiMode) {
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
        lockMovement = lockPlayerMovement;
        hideGui = hideGuiMode;

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
        stopCamera(0.5f);
    }

    /**
     * Smoothly return the view to the player over {@code returnSmoothTime} seconds.
     * Cancels any in-flight camera move / look-at tracking.
     */
    public static void stopCamera(float returnSmoothTime) {
        if (!active) return;

        lookAtMode = CameraPacket.LOOK_NONE;
        lookAtEntityId = -1;
        moving = false;

        if (returnSmoothTime <= 0f) {
            resetCamera();
            return;
        }

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
        smoothTimeSec = Math.max(returnSmoothTime, 0.01f);
        phase = Phase.TRANSITION_OUT;
    }

    /** Instantly release cinematic camera and return control to the player. */
    public static void resetCamera() {
        resetToInactive();
    }

    public static void moveCamera(double x, double y, double z,
                                  float yaw, float pitch, float moveSmoothTime) {
        moveCamera(x, y, z, yaw, pitch, moveSmoothTime,
                CameraPacket.LOOK_NONE, -1, 0, 0, 0, lockMovement, hideGui);
    }

    public static void moveCamera(double x, double y, double z,
                                  float yaw, float pitch, float moveSmoothTime,
                                  byte laMode, int laEntityId,
                                  double laX, double laY, double laZ) {
        moveCamera(x, y, z, yaw, pitch, moveSmoothTime, laMode, laEntityId, laX, laY, laZ, lockMovement, hideGui);
    }

    public static void moveCamera(double x, double y, double z,
                                  float yaw, float pitch, float moveSmoothTime,
                                  byte laMode, int laEntityId,
                                  double laX, double laY, double laZ,
                                  boolean lockPlayerMovement, byte hideGuiMode) {
        if (!active) {
            startCamera(x, y, z, yaw, pitch, 0, true, moveSmoothTime, "",
                    laMode, laEntityId, laX, laY, laZ, lockPlayerMovement, hideGuiMode);
            return;
        }

        lockMovement = lockPlayerMovement;
        hideGui = hideGuiMode;

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

        lookAtMode = laMode;
        lookAtEntityId = laEntityId;
        lookAtX = laX;
        lookAtY = laY;
        lookAtZ = laZ;
        lookAtIsBlock = (laMode != CameraPacket.LOOK_NONE && laEntityId == -1);

        if (lookAtMode == CameraPacket.LOOK_ONCE || lookAtMode == CameraPacket.LOOK_TRACK) {
            float[] angles = computeLookAtAngles(x, y, z);
            if (angles != null) {
                moveToYaw = angles[0];
                moveToPitch = angles[1];
            }
        }

        targetX = x;
        targetY = y;
        targetZ = z;
        targetYaw = moveToYaw;
        targetPitch = moveToPitch;
    }

    // =====================================================================
    //  Continuous route playback
    // =====================================================================

    /**
     * Start frame-by-frame route playback. The camera position is interpolated every
     * render frame from the pre-computed sample list using real elapsed time — no
     * smoothstep, no per-tick jumps, perfectly smooth.
     *
     * @param samples      pre-built sample list (≥ 2)
     * @param timeline     per-sample timestamps (same length as samples), seconds from t=0
     * @param totalDuration total flight time in seconds
     * @param returnSmooth  seconds for return transition (≥ 0)
     * @param lock          lock player movement during playback
     * @param hide          hide-GUI mode byte
     * @param overrideLook  if true, look at the fixed point (lx,ly,lz)
     * @param lx,ly,lz     look-at coordinates (used only when overrideLook=true)
     * @param endMode       what to do when the route ends
     */
    public static void startRoute(List<CameraRoutePath.Sample> samples, float[] timeline,
                                  float totalDuration,
                                  float returnSmooth, boolean lock, byte hide,
                                  boolean overrideLook, double lx, double ly, double lz,
                                  CameraRouteEndMode endMode, float transitionSec) {
        if (samples == null || samples.size() < 2) return;

        CameraRoutePath.Sample first = samples.get(0);

        // Capture current camera position for cross-fade transition
        boolean doTransition = transitionSec > 0f && active;
        if (doTransition) {
            routeTransFromX = currentX;
            routeTransFromY = currentY;
            routeTransFromZ = currentZ;
            routeTransFromYaw = currentYaw;
            routeTransFromPitch = currentPitch;
            routeTransDurSec = transitionSec;
            routeTransStartMs = System.currentTimeMillis();
            routeTransActive = true;
        } else {
            routeTransActive = false;
        }

        // Place camera at first sample (if no transition, instant; if transition, will be overridden)
        float yaw = first.yaw();
        float pitch = first.pitch();
        if (overrideLook) {
            float[] a = CameraRoutePath.lookAtAngles(first.x(), first.y(), first.z(), lx, ly, lz);
            yaw = a[0]; pitch = a[1];
        }
        startCamera(first.x(), first.y(), first.z(), yaw, pitch,
                0, false, 0, "",
                CameraPacket.LOOK_NONE, -1, 0, 0, 0, lock, hide);

        routeSamples = samples;
        routeTimeline = (timeline != null && timeline.length == samples.size()) ? timeline : null;
        routeTotalDuration = Math.max(0.05f, totalDuration);
        routeReturnSmooth = Math.max(0f, returnSmooth);
        routeEndMode = endMode != null ? endMode : CameraRouteEndMode.RETURN_TO_PLAYER;
        routeOverrideLook = overrideLook;
        routeLookX = lx; routeLookY = ly; routeLookZ = lz;
        routeMode = true;
        // Route clock starts after the cross-fade transition ends
        routeStartMs = doTransition
                ? System.currentTimeMillis() + (long)(transitionSec * 1000f)
                : System.currentTimeMillis();
    }

    /** Backward-compat overload — no transition. */
    public static void startRoute(List<CameraRoutePath.Sample> samples, float[] timeline,
                                  float totalDuration,
                                  float returnSmooth, boolean lock, byte hide,
                                  boolean overrideLook, double lx, double ly, double lz,
                                  CameraRouteEndMode endMode) {
        startRoute(samples, timeline, totalDuration, returnSmooth, lock, hide,
                overrideLook, lx, ly, lz, endMode, 0f);
    }

    public static boolean isRouteActive() { return routeMode; }

    /**
     * Update the look-at direction without moving the camera.
     * Works while a camera route is playing (as long as the route has no built-in look override).
     */
    public static void setExternalLookAt(byte mode, int entityId,
                                         double lx, double ly, double lz) {
        lookAtMode = mode;
        lookAtEntityId = entityId;
        lookAtX = lx;
        lookAtY = ly;
        lookAtZ = lz;
        lookAtIsBlock = (mode != CameraPacket.LOOK_NONE && entityId == -1);
    }

    private static void stopRouteMode() {
        routeMode = false;
        routeTransActive = false;
        routeSamples = List.of();
        routeTimeline = new float[0];
    }

    private static void resetToInactive() {
        active = false;
        phase = Phase.INACTIVE;
        moving = false;
        lookAtMode = CameraPacket.LOOK_NONE;
        lookAtEntityId = -1;
        lockMovement = false;
        hideGui = CameraPacket.HIDE_NONE;
        stopRouteMode();
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

        // ---- Route mode: smooth continuous interpolation ----
        if (routeMode) {
            // Cross-fade transition: blend from previous camera position to route[0]
            if (routeTransActive) {
                float transElapsed = (now - routeTransStartMs) / 1000f;
                float t = Math.min(transElapsed / Math.max(0.001f, routeTransDurSec), 1f);
                float s = smoothstep(t);
                CameraRoutePath.Sample first = routeSamples.get(0);
                double toX = first.x(), toY = first.y(), toZ = first.z();
                float toYaw = first.yaw(), toPitch = first.pitch();
                if (routeOverrideLook) {
                    float[] a = CameraRoutePath.lookAtAngles(toX, toY, toZ, routeLookX, routeLookY, routeLookZ);
                    toYaw = a[0]; toPitch = a[1];
                }
                currentX = routeTransFromX + (toX - routeTransFromX) * s;
                currentY = routeTransFromY + (toY - routeTransFromY) * s;
                currentZ = routeTransFromZ + (toZ - routeTransFromZ) * s;
                currentYaw = lerpAngle(routeTransFromYaw, toYaw, s);
                currentPitch = routeTransFromPitch + (toPitch - routeTransFromPitch) * s;
                event.setYaw(currentYaw);
                event.setPitch(currentPitch);
                if (cameraPositionField != null) {
                    try { cameraPositionField.set(event.getCamera(), new Vec3(currentX, currentY, currentZ)); }
                    catch (Exception ignored) {}
                }
                if (t >= 1f) routeTransActive = false;
                return;
            }

            float elapsed = (now - routeStartMs) / 1000f;
            if (elapsed >= routeTotalDuration) {
                stopRouteMode();
                if (routeEndMode == CameraRouteEndMode.RETURN_TO_PLAYER) {
                    if (routeReturnSmooth > 0f) {
                        stopCamera(routeReturnSmooth);
                    } else {
                        resetCamera();
                    }
                }
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.translatable("spraute_engine.camera_route.preview_done"), true);
                }
                return;
            }

            List<CameraRoutePath.Sample> rs = routeSamples;
            int n = rs.size();
            float fIdx;
            if (routeTimeline != null && routeTimeline.length == n && routeTimeline[n - 1] > 0f) {
                // Binary-search timeline to find fractional sample index
                float totalTime = routeTimeline[n - 1];
                float t = elapsed / totalTime * (n - 1);
                // Use timeline for accurate per-segment time
                int lo = 0, hi = n - 2;
                while (lo < hi) {
                    int mid = (lo + hi + 1) >> 1;
                    if (routeTimeline[mid] <= elapsed) lo = mid; else hi = mid - 1;
                }
                int i0 = lo;
                float t0 = routeTimeline[i0];
                float t1 = routeTimeline[Math.min(i0 + 1, n - 1)];
                float segFrac = (t1 > t0) ? (elapsed - t0) / (t1 - t0) : 0f;
                segFrac = Math.max(0f, Math.min(1f, segFrac));
                fIdx = i0 + segFrac;
            } else {
                // Uniform distribution
                fIdx = elapsed / routeTotalDuration * (n - 1);
            }

            int i0 = Math.max(0, Math.min((int) fIdx, n - 2));
            float frac = fIdx - i0;
            CameraRoutePath.Sample a = rs.get(i0);
            CameraRoutePath.Sample b = rs.get(Math.min(i0 + 1, n - 1));

            currentX = a.x() + (b.x() - a.x()) * frac;
            currentY = a.y() + (b.y() - a.y()) * frac;
            currentZ = a.z() + (b.z() - a.z()) * frac;
            currentYaw = CameraRoutePath.lerpAngle(a.yaw(), b.yaw(), frac);
            currentPitch = a.pitch() + (b.pitch() - a.pitch()) * frac;

            if (routeOverrideLook) {
                float[] angles = CameraRoutePath.lookAtAngles(currentX, currentY, currentZ,
                        routeLookX, routeLookY, routeLookZ);
                currentYaw = angles[0];
                currentPitch = angles[1];
            } else if (lookAtMode != CameraPacket.LOOK_NONE) {
                // External look-at override applied on top of the route path
                float[] angles = computeLookAtAngles(currentX, currentY, currentZ);
                if (angles != null) {
                    currentYaw = angles[0];
                    currentPitch = angles[1];
                }
                if (lookAtMode == CameraPacket.LOOK_ONCE) {
                    lookAtMode = CameraPacket.LOOK_NONE;
                }
            }

            event.setYaw(currentYaw);
            event.setPitch(currentPitch);
            if (cameraPositionField != null) {
                try {
                    cameraPositionField.set(event.getCamera(), new Vec3(currentX, currentY, currentZ));
                } catch (Exception ignored) {}
            }
            return;
        }

        if (moving) {
            float elapsed = (now - moveStartMs) / 1000f;
            float t = Math.min(elapsed / moveSmoothTimeSec, 1f);
            float s = smoothstep(t);

            currentX = moveFromX + (moveToX - moveFromX) * s;
            currentY = moveFromY + (moveToY - moveFromY) * s;
            currentZ = moveFromZ + (moveToZ - moveFromZ) * s;

            if (lookAtMode == CameraPacket.LOOK_TRACK) {
                float[] angles = computeLookAtAngles(currentX, currentY, currentZ);
                if (angles != null) {
                    moveToYaw = angles[0];
                    moveToPitch = angles[1];
                    targetYaw = angles[0];
                    targetPitch = angles[1];
                }
            }

            currentYaw = lerpAngle(moveFromYaw, moveToYaw, s);
            currentPitch = moveFromPitch + (moveToPitch - moveFromPitch) * s;

            if (t >= 1f) {
                moving = false;
                if (lookAtMode == CameraPacket.LOOK_ONCE) {
                    lookAtMode = CameraPacket.LOOK_NONE;
                }
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
