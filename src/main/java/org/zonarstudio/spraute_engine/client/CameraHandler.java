package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteEndMode;
import org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath;
import org.zonarstudio.spraute_engine.client.editor.SprauteEditorState;
import org.zonarstudio.spraute_engine.mixin.CameraAccess;
import org.zonarstudio.spraute_engine.mixin.CameraInvoker;
import org.zonarstudio.spraute_engine.network.CameraPacket;
import org.zonarstudio.spraute_engine.scene.PlayerCameraPose;

import java.util.List;

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
    private static boolean lookBlending;
    private static float lookBlendFromYaw, lookBlendFromPitch;
    private static float lookBlendToYaw, lookBlendToPitch;
    private static long lookBlendStartMs;
    private static float lookBlendDur;

    private static boolean lockMovement = false;
    private static boolean lockBlocks = false;
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

    private static PlayerCameraPose.Rest packetRest = PlayerCameraPose.VANILLA;

    private static double renderFov;

    private static boolean decoupledReady;
    private static float decoupledYaw;
    private static float decoupledPitch;
    private static float decoupledYawO;
    private static float decoupledPitchO;
    private static boolean lookingAtInteract;

    static {
        PlayerCameraPose.Rig.setClientLook(CameraHandler::decoupledLook);
    }

    public static void setRestPose(PlayerCameraPose.Rest rest) {
        boolean was = packetRest != null && packetRest.rig() != null && packetRest.rig().separateTurn();
        packetRest = rest == null ? PlayerCameraPose.VANILLA : rest;
        boolean now = packetRest.rig() != null && packetRest.rig().separateTurn();
        if (!now || !was) decoupledReady = false;
    }

    /** S2C rest: ignore packets meant for another player (dedicated / mis-routed). */
    public static void applyRestPacket(String playerUuid, PlayerCameraPose.Rest rest) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && playerUuid != null && !playerUuid.isBlank()
                && !PlayerCameraPose.isOwnedByLocalPlayer(playerUuid)) {
            return;
        }
        setRestPose(rest);
    }

    public static PlayerCameraPose.Rest restPose() {
        // Rest pose is owned by a running scene. Authoring-document values must not
        // leak into gameplay while the scene is stopped.
        return packetRest;
    }

    public static boolean isRestCustom() {
        PlayerCameraPose.Rest rest = restPose();
        return rest != null && !rest.isVanilla();
    }

    public static boolean isOverridingView() {
        return (active && phase != Phase.INACTIVE) || isRestCustom();
    }

    public static boolean shouldHideHand() {
        if (HudHideClient.hideHand() || shouldHideVanillaHud()) return true;
        if (active && phase != Phase.INACTIVE) return false;
        PlayerCameraPose.Rest rest = restPose();
        if (rest == null || !rest.isDetached()) return false;
        return !rest.rig().showHand();
    }

    /**
     * Author asked for a first-person arm while the rest pose is offset.
     * Vanilla {@code renderItemInHand} still requires first-person + !hideGui;
     * the mixin treats this as first-person so the arm actually draws.
     */
    public static boolean shouldForceFirstPersonHand() {
        if (HudHideClient.hideHand() || shouldHideVanillaHud()) return false;
        if (SprauteEditorState.isRenderingEditorCamera()) return false;
        PlayerCameraPose.Rest rest = restPose();
        return rest != null && rest.isDetached() && rest.rig() != null && rest.rig().showHand();
    }

    /**
     * WASD is taken from the camera look, not the body.
     * Vanilla boom (follow + look) stays body-relative. Any view whose yaw is
     * independent of the body must remap, otherwise W walks sideways on screen.
     */
    public static boolean moveRelativeToCamera() {
        if (active && phase != Phase.INACTIVE) return true;
        PlayerCameraPose.Rest rest = restPose();
        if (rest == null || rest.rig() == null) return false;
        PlayerCameraPose.Rig rig = rest.rig();
        if (rig.moveRelative() || rig.separateTurn() || !rig.followPlayer()
                || rig.lockYaw() || rig.orbit()) {
            return true;
        }
        // Follow + additive rest yaw: camera looks aside of the body (leftover
        // after toggling follow, or an authored offset). Vanilla WASD is body-relative.
        return Math.abs(Mth.wrapDegrees(rest.yaw())) > 0.5f;
    }

    /**
     * When camera yaw is independent of the mouse (world pose, frozen yaw, or
     * separate-turn), the body faces the walk direction.
     */
    public static boolean shouldFaceWalkDirection() {
        PlayerCameraPose.Rest rest = restPose();
        if (rest == null || rest.rig() == null) return false;
        PlayerCameraPose.Rig rig = rest.rig();
        return rig.separateTurn() || !rig.followPlayer() || rig.lockYaw();
    }

    public static boolean separateTurnActive() {
        if (active && phase != Phase.INACTIVE) return false;
        if (SprauteEditorState.isRenderingEditorCamera()) return false;
        PlayerCameraPose.Rest rest = restPose();
        return rest != null && rest.rig() != null
                && rest.rig().separateTurn() && rest.rig().followPlayer();
    }

    /** Break/place/attack pick uses the camera look (screen centre), not body facing. */
    public static boolean screenRaycastActive() {
        if (SprauteEditorState.isRenderingEditorCamera()) return false;
        PlayerCameraPose.Rest rest = restPose();
        return rest != null && rest.rig() != null && rest.rig().screenRaycast();
    }

    /** Body faces the block/entity the player is mining, attacking or using. */
    public static boolean lookAtInteractActive() {
        if (SprauteEditorState.isRenderingEditorCamera()) return false;
        if (PlayerControlHandler.isLookLocked()) return false;
        PlayerCameraPose.Rest rest = restPose();
        return rest != null && rest.rig() != null && rest.rig().lookAtInteract();
    }

    public static boolean isLookingAtInteract() {
        return lookingAtInteract;
    }

    /** View the crosshair is drawn from: cinematic hold, or the rest camera. */
    public static PlayerCameraPose.World aimView(Entity player, float partialTick) {
        if (active && phase != Phase.INACTIVE) {
            return new PlayerCameraPose.World(currentX, currentY, currentZ, currentYaw, currentPitch);
        }
        if (player == null) return null;
        return PlayerCameraPose.computeView(player, restPose(), partialTick);
    }

    /**
     * Mouse look goes to the camera, not {@code player.yRot}.
     * {@code yRot}/{@code xRot} are vanilla {@link net.minecraft.world.entity.Entity#turn} deltas.
     */
    public static boolean consumeTurnAsCamera(double yRot, double xRot) {
        if (!separateTurnActive()) return false;
        if (PlayerControlHandler.isLookLocked()) return false;
        ensureDecoupled();
        decoupledYaw += (float) yRot * 0.15f;
        decoupledPitch = Mth.clamp(decoupledPitch + (float) xRot * 0.15f, -90f, 90f);
        return true;
    }

    public static float[] decoupledLook(float partialTick) {
        if (!separateTurnActive()) return null;
        ensureDecoupled();
        float pt = Math.max(0f, Math.min(1f, partialTick));
        return new float[]{
                Mth.rotLerp(pt, decoupledYawO, decoupledYaw),
                Mth.lerp(pt, decoupledPitchO, decoupledPitch)
        };
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (decoupledReady) {
                decoupledYawO = decoupledYaw;
                decoupledPitchO = decoupledPitch;
            }
            lookingAtInteract = lookAtInteractActive() && hasInteractTarget();
            return;
        }
        if (event.phase == TickEvent.Phase.END && lookingAtInteract) {
            applyLookAtInteract();
        }
    }

    private static boolean hasInteractTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options == null) return false;
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) return false;
        if (mc.player.swinging) return true;
        if (mc.options.keyAttack.isDown()) return true;
        if (mc.options.keyUse.isDown() && (hit.getType() == HitResult.Type.BLOCK
                || hit.getType() == HitResult.Type.ENTITY)) {
            return true;
        }
        return false;
    }

    private static void applyLookAtInteract() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.hitResult == null) return;
        if (mc.hitResult.getType() == HitResult.Type.MISS) return;
        Vec3 at;
        if (mc.hitResult instanceof EntityHitResult entityHit) {
            at = entityHit.getEntity().getBoundingBox().getCenter();
        } else {
            at = mc.hitResult.getLocation();
        }
        Vec3 eyes = mc.player.getEyePosition(1f);
        double dx = at.x - eyes.x;
        double dy = at.y - eyes.y;
        double dz = at.z - eyes.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz * horiz + dy * dy < 1.0E-6) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        pitch = Mth.clamp(pitch, -90f, 90f);
        float step = bodyTurnStepDegrees();
        float nextYaw = Mth.approachDegrees(mc.player.getYRot(), yaw, step);
        float curPitch = mc.player.getXRot();
        float nextPitch = curPitch + Mth.clamp(pitch - curPitch, -step, step);
        mc.player.setYRot(nextYaw);
        mc.player.setXRot(nextPitch);
        mc.player.setYHeadRot(nextYaw);
        mc.player.setYBodyRot(nextYaw);
    }

    /** Max body yaw/pitch change per tick. {@code turnSmooth} 0 = snap, 1 ≈ 6°/tick. */
    public static float bodyTurnStepDegrees() {
        PlayerCameraPose.Rest rest = restPose();
        float s = rest != null && rest.rig() != null ? rest.rig().turnSmooth() : 0f;
        s = Mth.clamp(s, 0f, 1f);
        if (s <= 0.001f) return 180f;
        return Mth.lerp(s, 90f, 6f);
    }

    private static void ensureDecoupled() {
        if (decoupledReady) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            decoupledYaw = 0f;
            decoupledPitch = 0f;
        } else {
            decoupledYaw = mc.player.getYRot();
            decoupledPitch = mc.player.getXRot();
        }
        decoupledYawO = decoupledYaw;
        decoupledPitchO = decoupledPitch;
        decoupledReady = true;
    }

    /** Yaw WASD should be relative to: cinematic view, rest camera, or the player. */
    public static float movementCameraYaw() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0f;
        if (active && phase != Phase.INACTIVE) return currentYaw;
        PlayerCameraPose.Rest rest = restPose();
        if (rest != null && !rest.isVanilla()) {
            PlayerCameraPose.World view = PlayerCameraPose.computeView(mc.player, rest, 1f);
            if (view != null) return view.yaw();
        }
        return mc.player.getYRot();
    }

    /**
     * Rewrites {@code leftImpulse}/{@code forwardImpulse} so vanilla
     * {@code moveRelative} walks in camera space. Call after Input.tick
     * ({@code MovementInputUpdateEvent}).
     */
    public static void applyRelativeMove(Player player, Input input) {
        if (player == null || input == null) return;
        if (player.isPassenger()) return;
        if (!moveRelativeToCamera()) return;
        float x = input.leftImpulse;
        float z = input.forwardImpulse;
        if (x * x + z * z < 1.0E-6f) return;

        float camYaw = movementCameraYaw();
        if (shouldFaceWalkDirection() && !PlayerControlHandler.isLookLocked()
                && !lookingAtInteract) {
            float moveYaw = camYaw + (float) Math.toDegrees(Math.atan2(-x, z));
            float step = bodyTurnStepDegrees();
            float nextYaw = Mth.approachDegrees(player.getYRot(), moveYaw, step);
            player.setYRot(nextYaw);
            player.setYHeadRot(nextYaw);
            player.setYBodyRot(nextYaw);
            if (step >= 179f) {
                float mag = Mth.sqrt(x * x + z * z);
                input.leftImpulse = 0f;
                input.forwardImpulse = mag;
                return;
            }
        }
        float d = (camYaw - player.getYRot()) * ((float) Math.PI / 180f);
        float sin = Mth.sin(d);
        float cos = Mth.cos(d);
        input.leftImpulse = x * cos - z * sin;
        input.forwardImpulse = z * cos + x * sin;
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Camera → look-subject segment for occlusion clearing.
     * @param out length ≥ 6: camX,Y,Z then targetX,Y,Z
     */
    public static boolean resolveOcclusionSegment(double[] out) {
        if (out == null || out.length < 6) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        Entity camEntity = mc.cameraEntity != null ? mc.cameraEntity : mc.player;

        double cx, cy, cz;
        if (isOverridingView() && (active && phase != Phase.INACTIVE || isRestCustom())) {
            cx = currentX;
            cy = currentY;
            cz = currentZ;
        } else {
            return false;
        }

        double tx, ty, tz;
        if (active && phase != Phase.INACTIVE && lookAtMode != CameraPacket.LOOK_NONE) {
            if (!lookAtIsBlock && lookAtEntityId != -1) {
                Entity e = mc.level.getEntity(lookAtEntityId);
                if (e == null) return false;
                tx = e.getX();
                ty = e.getEyeY();
                tz = e.getZ();
            } else {
                tx = lookAtX;
                ty = lookAtY;
                tz = lookAtZ;
            }
        } else if (routeMode && routeOverrideLook) {
            tx = routeLookX;
            ty = routeLookY;
            tz = routeLookZ;
        } else {
            // Rest / third-person: clear toward the player body (subject).
            tx = camEntity.getX();
            ty = camEntity.getEyeY();
            tz = camEntity.getZ();
        }
        // Ignore near-zero segments (camera sitting on the subject).
        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        if (dx * dx + dy * dy + dz * dz < 0.36) return false;
        out[0] = cx;
        out[1] = cy;
        out[2] = cz;
        out[3] = tx;
        out[4] = ty;
        out[5] = tz;
        return true;
    }

    public static boolean isLockMovement() {
        return active && lockMovement;
    }

    /** When true, player cannot break / place / use world blocks during the cinematic. */
    public static boolean isLockBlocks() {
        return active && lockBlocks;
    }

    public static byte getHideGui() {
        if (active) return hideGui;
        PlayerCameraPose.Rest rest = restPose();
        if (rest == null || rest.rig() == null) return CameraPacket.HIDE_NONE;
        return rest.rig().hideGui();
    }

    public static boolean shouldHideVanillaHud() {
        byte mode = getHideGui();
        return mode == CameraPacket.HIDE_MINECRAFT || mode == CameraPacket.HIDE_ALL;
    }

    public static boolean shouldHideScriptGui() {
        return getHideGui() == CameraPacket.HIDE_ALL;
    }

    public static void startCamera(double x, double y, double z,
                                   float yaw, float pitch,
                                   float time, boolean doSmooth, float doSmoothTime,
                                   String dim,
                                   byte laMode, int laEntityId,
                                   double laX, double laY, double laZ,
                                   boolean lockPlayerMovement, byte hideGuiMode) {
        startCamera(x, y, z, yaw, pitch, time, doSmooth, doSmoothTime, dim,
                laMode, laEntityId, laX, laY, laZ, lockPlayerMovement, hideGuiMode,
                org.zonarstudio.spraute_engine.script.CameraControls.DEFAULT_LOCK_BLOCKS);
    }

    public static void startCamera(double x, double y, double z,
                                   float yaw, float pitch,
                                   float time, boolean doSmooth, float doSmoothTime,
                                   String dim,
                                   byte laMode, int laEntityId,
                                   double laX, double laY, double laZ,
                                   boolean lockPlayerMovement, byte hideGuiMode,
                                   boolean lockPlayerBlocks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity camEntity = mc.cameraEntity != null ? mc.cameraEntity : mc.player;
        PlayerCameraPose.World home = homeWorld(camEntity);
        if (active && phase != Phase.INACTIVE) {
            startX = currentX;
            startY = currentY;
            startZ = currentZ;
            startYaw = currentYaw;
            startPitch = currentPitch;
        } else if (home != null) {
            startX = home.x();
            startY = home.y();
            startZ = home.z();
            startYaw = home.yaw();
            startPitch = home.pitch();
        } else {
            startX = camEntity.getX();
            startY = camEntity.getEyeY();
            startZ = camEntity.getZ();
            startYaw = camEntity.getYRot();
            startPitch = camEntity.getXRot();
        }

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
        lockBlocks = lockPlayerBlocks;
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
        lookBlending = false;

        if (returnSmoothTime <= 0f) {
            resetCamera();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Entity camEntity = mc.cameraEntity != null ? mc.cameraEntity : mc.player;
            PlayerCameraPose.World home = homeWorld(camEntity);
            if (home != null) {
                targetX = home.x();
                targetY = home.y();
                targetZ = home.z();
                targetYaw = home.yaw();
                targetPitch = home.pitch();
            } else {
                targetX = camEntity.getX();
                targetY = camEntity.getEyeY();
                targetZ = camEntity.getZ();
                targetYaw = camEntity.getYRot();
                targetPitch = camEntity.getXRot();
            }
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
                CameraPacket.LOOK_NONE, -1, 0, 0, 0, lockMovement, hideGui, lockBlocks);
    }

    public static void moveCamera(double x, double y, double z,
                                  float yaw, float pitch, float moveSmoothTime,
                                  byte laMode, int laEntityId,
                                  double laX, double laY, double laZ) {
        moveCamera(x, y, z, yaw, pitch, moveSmoothTime, laMode, laEntityId, laX, laY, laZ,
                lockMovement, hideGui, lockBlocks);
    }

    public static void moveCamera(double x, double y, double z,
                                  float yaw, float pitch, float moveSmoothTime,
                                  byte laMode, int laEntityId,
                                  double laX, double laY, double laZ,
                                  boolean lockPlayerMovement, byte hideGuiMode) {
        moveCamera(x, y, z, yaw, pitch, moveSmoothTime, laMode, laEntityId, laX, laY, laZ,
                lockPlayerMovement, hideGuiMode, lockBlocks);
    }

    public static void moveCamera(double x, double y, double z,
                                  float yaw, float pitch, float moveSmoothTime,
                                  byte laMode, int laEntityId,
                                  double laX, double laY, double laZ,
                                  boolean lockPlayerMovement, byte hideGuiMode,
                                  boolean lockPlayerBlocks) {
        // Instant place (no fly-in) when duration is zero / negative.
        if (moveSmoothTime <= 0f) {
            startCamera(x, y, z, yaw, pitch, 0, false, 0f, "",
                    laMode, laEntityId, laX, laY, laZ, lockPlayerMovement, hideGuiMode, lockPlayerBlocks);
            return;
        }
        if (!active) {
            startCamera(x, y, z, yaw, pitch, 0, true, moveSmoothTime, "",
                    laMode, laEntityId, laX, laY, laZ, lockPlayerMovement, hideGuiMode, lockPlayerBlocks);
            return;
        }

        lockMovement = lockPlayerMovement;
        lockBlocks = lockPlayerBlocks;
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
     * @param lockBlocksFlag lock break/place/use of world blocks during playback
     * @param overrideLook  if true, look at the fixed point (lx,ly,lz)
     * @param lx,ly,lz     look-at coordinates (used only when overrideLook=true)
     * @param endMode       what to do when the route ends
     */
    public static void startRoute(List<CameraRoutePath.Sample> samples, float[] timeline,
                                  float totalDuration,
                                  float returnSmooth, boolean lock, byte hide,
                                  boolean overrideLook, double lx, double ly, double lz,
                                  CameraRouteEndMode endMode, float transitionSec) {
        startRoute(samples, timeline, totalDuration, returnSmooth, lock, hide,
                org.zonarstudio.spraute_engine.script.CameraControls.DEFAULT_LOCK_BLOCKS,
                overrideLook, lx, ly, lz, endMode, transitionSec);
    }

    public static void startRoute(List<CameraRoutePath.Sample> samples, float[] timeline,
                                  float totalDuration,
                                  float returnSmooth, boolean lock, byte hide, boolean lockBlocksFlag,
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
                CameraPacket.LOOK_NONE, -1, 0, 0, 0, lock, hide, lockBlocksFlag);

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
     * {@code smoothTime > 0} lerps yaw/pitch in place.
     */
    public static void setExternalLookAt(byte mode, int entityId,
                                         double lx, double ly, double lz) {
        setExternalLookAt(mode, entityId, lx, ly, lz, 0f);
    }

    public static void setExternalLookAt(byte mode, int entityId,
                                         double lx, double ly, double lz, float smoothTime) {
        lookAtMode = mode;
        lookAtEntityId = entityId;
        lookAtX = lx;
        lookAtY = ly;
        lookAtZ = lz;
        lookAtIsBlock = (mode != CameraPacket.LOOK_NONE && entityId == -1);
        lookBlending = false;
        if (smoothTime <= 0f || (!active && !routeMode) || mode == CameraPacket.LOOK_NONE) {
            if (mode == CameraPacket.LOOK_ONCE && active) {
                float[] angles = computeLookAtAngles(currentX, currentY, currentZ);
                if (angles != null) {
                    currentYaw = angles[0];
                    currentPitch = angles[1];
                    targetYaw = angles[0];
                    targetPitch = angles[1];
                    lookAtMode = CameraPacket.LOOK_NONE;
                }
            }
            return;
        }
        float[] to = computeLookAtAngles(currentX, currentY, currentZ);
        if (to == null) return;
        lookBlendFromYaw = currentYaw;
        lookBlendFromPitch = currentPitch;
        lookBlendToYaw = to[0];
        lookBlendToPitch = to[1];
        lookBlendStartMs = System.currentTimeMillis();
        lookBlendDur = Math.max(smoothTime, 0.01f);
        lookBlending = true;
    }

    private static void applyLookBlend(long now) {
        if (!lookBlending) return;
        if (lookAtMode == CameraPacket.LOOK_TRACK) {
            float[] to = computeLookAtAngles(currentX, currentY, currentZ);
            if (to != null) {
                lookBlendToYaw = to[0];
                lookBlendToPitch = to[1];
            }
        }
        float t = Math.min((now - lookBlendStartMs) / 1000f / lookBlendDur, 1f);
        float s = smoothstep(t);
        currentYaw = lerpAngle(lookBlendFromYaw, lookBlendToYaw, s);
        currentPitch = lookBlendFromPitch + (lookBlendToPitch - lookBlendFromPitch) * s;
        targetYaw = currentYaw;
        targetPitch = currentPitch;
        if (t >= 1f) {
            lookBlending = false;
            if (lookAtMode == CameraPacket.LOOK_ONCE) {
                lookAtMode = CameraPacket.LOOK_NONE;
            }
        }
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
        lookBlending = false;
        lockMovement = false;
        lockBlocks = false;
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (SprauteEditorState.isRenderingEditorCamera()) return;
        try {
        if (!active || phase == Phase.INACTIVE) {
            applyRestView(event, mc.player, (float) event.getPartialTick());
            return;
        }

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
                applyCameraPose(event.getCamera(), currentX, currentY, currentZ, currentYaw, currentPitch, true);
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
            } else if (lookAtMode != CameraPacket.LOOK_NONE && !lookBlending) {
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
            applyLookBlend(now);

            event.setYaw(currentYaw);
            event.setPitch(currentPitch);
            applyCameraPose(event.getCamera(), currentX, currentY, currentZ, currentYaw, currentPitch, true);
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
                    if (!lookBlending) {
                        currentYaw = targetYaw;
                        currentPitch = targetPitch;
                    }
                }

                if (durationSec > 0f) {
                    float elapsed = (now - startTimeMs) / 1000f;
                    if (elapsed >= durationSec) {
                        if (smooth) {
                            fillHomeTarget(mc);
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
                    PlayerCameraPose.World home = homeWorld(camEntity);
                    double playerX = home != null ? home.x() : camEntity.getX();
                    double playerY = home != null ? home.y() : camEntity.getEyeY();
                    double playerZ = home != null ? home.z() : camEntity.getZ();
                    float playerYaw = home != null ? home.yaw() : camEntity.getYRot();
                    float playerPitch = home != null ? home.pitch() : camEntity.getXRot();

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

        applyLookBlend(now);
        event.setYaw(currentYaw);
        event.setPitch(currentPitch);
        applyCameraPose(event.getCamera(), currentX, currentY, currentZ, currentYaw, currentPitch, true);
        } finally {
            CameraEffectsClient.apply(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!SprauteEditorState.isRenderingEditorCamera()) {
            PlayerCameraPose.Rest rest = restPose();
            // FOV is independent of detached pose — changing FOV must not require
            // (or imply) overriding the camera position.
            if (rest != null && rest.hasCustomFov()) {
                event.setFOV(rest.fov());
            }
        }
        CameraEffectsClient.applyFov(event);
        // The hand is drawn with a separate fixed FOV pass — only the world pass
        // describes the projection that screen↔world math has to invert.
        if (event.usedConfiguredFov()) renderFov = event.getFOV();
    }

    /**
     * Vertical FOV of the last rendered world frame, after every vanilla and mod
     * modifier. Screen-space math (viewport picking) must use this, not the raw
     * option, or it silently desynchronizes from what the player sees.
     */
    public static double renderFov() {
        return renderFov > 1.0 ? renderFov : Minecraft.getInstance().options.fov().get();
    }

    private static void applyRestView(ViewportEvent.ComputeCameraAngles event, Entity player,
                                      float partialTick) {
        PlayerCameraPose.World home = homeWorld(player, partialTick);
        if (home == null) return;
        currentX = home.x();
        currentY = home.y();
        currentZ = home.z();
        currentYaw = home.yaw();
        currentPitch = home.pitch();
        event.setYaw(currentYaw);
        event.setPitch(currentPitch);
        PlayerCameraPose.Rest rest = restPose();
        // showHand keeps the camera "attached" so vanilla first-person arms draw.
        // Offset / boom still apply via setPosition; only the self-body skip changes.
        boolean detached = rest != null && rest.isDetached() && !rest.rig().showHand();
        applyCameraPose(event.getCamera(), currentX, currentY, currentZ, currentYaw, currentPitch,
                detached);
    }

    private static void fillHomeTarget(Minecraft mc) {
        Entity camEntity = mc.cameraEntity != null ? mc.cameraEntity : mc.player;
        if (camEntity == null) return;
        float pt = mc.getFrameTime();
        PlayerCameraPose.World home = homeWorld(camEntity, pt);
        if (home != null) {
            targetX = home.x();
            targetY = home.y();
            targetZ = home.z();
            targetYaw = home.yaw();
            targetPitch = home.pitch();
        } else {
            targetX = camEntity.getX();
            targetY = camEntity.getEyeY();
            targetZ = camEntity.getZ();
            targetYaw = camEntity.getYRot();
            targetPitch = camEntity.getXRot();
        }
    }

    private static PlayerCameraPose.World homeWorld(Entity player) {
        return homeWorld(player, 1f);
    }

    private static PlayerCameraPose.World homeWorld(Entity player, float partialTick) {
        if (player == null) return null;
        PlayerCameraPose.Rest rest = restPose();
        if (rest == null || rest.isVanilla()) return null;
        return PlayerCameraPose.computeView(player, rest, partialTick);
    }

    /**
     * Position + full look basis. Forge only copies yaw/pitch floats via
     * {@code setAnglesInternal}; particle billboards use {@link Camera#rotation()},
     * so without {@code setRotation} sprites stay edge-on to the cinematic view.
     */
    private static void applyCameraPose(Camera camera, double x, double y, double z,
                                        float yaw, float pitch, boolean detached) {
        if (camera == null) return;
        CameraInvoker inv = (CameraInvoker) camera;
        inv.spraute$setPosition(x, y, z);
        inv.spraute$setRotation(yaw, pitch);
        if (detached) {
            ((CameraAccess) camera).spraute$setDetached(true);
        }
    }
}
