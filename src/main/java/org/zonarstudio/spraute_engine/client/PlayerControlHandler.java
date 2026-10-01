package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.script.PlayerMoveDirs;

/**
 * Client-side player control locks independent of cinematic {@link CameraHandler}.
 * Move lock zeros WASD; direction mask zeros selected axes; look lock freezes mouse facing;
 * perspective lock blocks F5 camera cycling.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class PlayerControlHandler {
    private static final float[] DIR_SCRATCH = new float[2];
    private static boolean moveLocked;
    private static boolean lookLocked;
    private static boolean perspectiveLocked;
    private static int moveDirMask;
    private static CameraType lockedPerspective;
    private static float lockedYaw;
    private static float lockedPitch;

    private PlayerControlHandler() {}

    public static boolean isMoveLocked() {
        return moveLocked;
    }

    public static boolean isLookLocked() {
        return lookLocked;
    }

    public static boolean isPerspectiveLocked() {
        return perspectiveLocked;
    }

    public static boolean hasMoveDirLock() {
        return moveDirMask != 0;
    }

    public static int moveDirMask() {
        return moveDirMask;
    }

    public static CameraType lockedPerspective() {
        return lockedPerspective;
    }

    public static void setMoveLocked(boolean locked) {
        moveLocked = locked;
    }

    public static void setLookLocked(boolean locked, float yaw, float pitch) {
        lookLocked = locked;
        if (locked) {
            lockedYaw = yaw;
            lockedPitch = clampPitch(pitch);
            applyLookNow();
        }
    }

    public static void setMoveDirMask(int mask) {
        moveDirMask = mask & (PlayerMoveDirs.RELATIVE | PlayerMoveDirs.WORLD);
    }

    public static void applyMoveDirLocks(Player player, Input input) {
        if (moveDirMask == 0 || input == null) return;
        float yaw = player != null ? player.getYRot() : 0f;
        PlayerMoveDirs.clamp(input.leftImpulse, input.forwardImpulse, yaw, moveDirMask, DIR_SCRATCH);
        input.leftImpulse = DIR_SCRATCH[0];
        input.forwardImpulse = DIR_SCRATCH[1];
        input.up = input.forwardImpulse > 1.0E-4f;
        input.down = input.forwardImpulse < -1.0E-4f;
        input.left = input.leftImpulse > 1.0E-4f;
        input.right = input.leftImpulse < -1.0E-4f;
    }

    public static void setPerspectiveLocked(boolean locked) {
        perspectiveLocked = locked;
        if (locked) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null) lockedPerspective = mc.options.getCameraType();
        } else {
            lockedPerspective = null;
        }
    }

    public static void setLook(float yaw, float pitch) {
        lockedYaw = yaw;
        lockedPitch = clampPitch(pitch);
        applyLookNow();
    }

    public static void clear() {
        moveLocked = false;
        lookLocked = false;
        perspectiveLocked = false;
        moveDirMask = 0;
        lockedPerspective = null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (perspectiveLocked) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null) {
                if (lockedPerspective == null) {
                    lockedPerspective = mc.options.getCameraType();
                } else if (mc.options.getCameraType() != lockedPerspective) {
                    mc.options.setCameraType(lockedPerspective);
                }
            }
        }
        if (!lookLocked) return;
        applyLookNow();
    }

    private static void applyLookNow() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.setYRot(lockedYaw);
        player.setXRot(lockedPitch);
        player.yHeadRot = lockedYaw;
        player.yRotO = lockedYaw;
        player.xRotO = lockedPitch;
        player.yHeadRotO = lockedYaw;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }
}
