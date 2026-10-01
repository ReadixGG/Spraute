package org.zonarstudio.spraute_engine.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class SprauteEditorState {
    private static boolean active;
    private static boolean flightMode;
    private static double anchorX, anchorY, anchorZ;
    private static float anchorYaw, anchorPitch;
    private static SprauteEngineEditorScreen editorScreen;
    private static RemotePlayer editorCamera;
    private static boolean oldHideGui;
    private static boolean hideGuiOverridden;

    private SprauteEditorState() {}

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (active && mc.player != null) {
            if (editorScreen == null) editorScreen = new SprauteEngineEditorScreen();
            prepareEditorInput();
            mc.setScreen(editorScreen);
            hideMinecraftGui();
            // The camera detaches only while the editor GUI is open.
            if (editorCamera != null) {
                syncCameraToPlayer(mc.player);
                mc.setCameraEntity(editorCamera);
            }
        }
    }

    /** Called when the editor screen is hidden (N) but the edit mode stays enabled. */
    public static void onEditorHidden() {
        Minecraft mc = Minecraft.getInstance();
        org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.stop();
        org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.endCapture();
        org.zonarstudio.spraute_engine.client.editor.scene.EditorGizmo.endDrag();
        org.zonarstudio.spraute_engine.client.editor.scene.EditorMarqueeSelect.cancel();
        org.zonarstudio.spraute_engine.client.editor.scene.CanvasViewportEditor.cancelInteraction();
        // Flight overlay still draws ImGui — keep accepting there; otherwise gate off
        // so gameplay clicks/keys do not fill the ImGui event queue.
        if (!flightMode) {
            org.zonarstudio.spraute_engine.client.imgui.SprauteImGuiBackend.setAcceptingInput(false);
        }
        if (mc.player != null) mc.setCameraEntity(mc.player);
        restoreMinecraftGui();
    }

    /** Clear sticky capture/drag and open the ImGui input gate cleanly. */
    private static void prepareEditorInput() {
        org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.endCapture();
        org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.suppressInteraction(4);
        org.zonarstudio.spraute_engine.client.editor.scene.EditorGizmo.endDrag();
        org.zonarstudio.spraute_engine.client.editor.scene.EditorMarqueeSelect.cancel();
        org.zonarstudio.spraute_engine.client.editor.scene.CanvasViewportEditor.cancelInteraction();
        org.zonarstudio.spraute_engine.client.imgui.SprauteImGuiBackend.setAcceptingInput(true);
    }

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (!active) {
            enable(mc.player);
            open();
            return;
        }
        if (mc.screen instanceof SprauteEngineEditorScreen screen) {
            screen.onClose();
        }
        stopEditor();
        mc.setScreen(null);
    }

    /** Enable editor mode without opening its screen; N opens it afterwards. */
    public static void enable() {
        enable(Minecraft.getInstance().player);
    }

    private static void enable(LocalPlayer player) {
        if (active || player == null) return;
        Minecraft mc = Minecraft.getInstance();
        anchorX = player.getX();
        anchorY = player.getY();
        anchorZ = player.getZ();
        anchorYaw = player.getYRot();
        anchorPitch = player.getXRot();
        //? if >=1.20.1 {
        editorCamera = new RemotePlayer(mc.level, player.getGameProfile());
        //?} else {
        /*editorCamera = new RemotePlayer(mc.level, player.getGameProfile(), null);
        *///?}
        syncCameraToPlayer(player);
        editorCamera.noPhysics = true;
        editorCamera.setNoGravity(true);
        active = true;
    }

    /**
     * moveTo + explicit history: the entity is never ticked by the level, so any
     * stale old-position values make the render camera lerp against (0,0,0).
     */
    private static void syncCameraToPlayer(LocalPlayer player) {
        if (editorCamera == null) return;
        setCameraPose(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    /** Teleport the detached editor camera (fly-cam), snapping interpolation history. */
    public static void moveCameraTo(double x, double y, double z, float yaw, float pitch) {
        if (editorCamera == null) return;
        setCameraPose(x, y, z, yaw, pitch);
    }

    private static void setCameraPose(double x, double y, double z, float yaw, float pitch) {
        if (editorCamera == null) return;
        editorCamera.moveTo(x, y, z, yaw, pitch);
        editorCamera.xo = x;
        editorCamera.yo = y;
        editorCamera.zo = z;
        editorCamera.xOld = x;
        editorCamera.yOld = y;
        editorCamera.zOld = z;
        editorCamera.yRotO = yaw;
        editorCamera.xRotO = pitch;
        editorCamera.yHeadRot = yaw;
        editorCamera.yHeadRotO = yaw;
        editorCamera.yBodyRot = yaw;
        editorCamera.yBodyRotO = yaw;
        editorCamera.setDeltaMovement(Vec3.ZERO);
    }

    public static void enterFlight() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || flightMode) return;
        if (!active) enable(player);
        flightMode = true;
        prepareEditorInput();
        mc.setScreen(null);
    }

    public static void returnToEditor() {
        if (!flightMode) {
            open();
            return;
        }
        flightMode = false;
        prepareEditorInput();
        Minecraft.getInstance().setScreen(editorScreen != null ? editorScreen : new SprauteEngineEditorScreen());
    }

    public static void stopEditor() {
        Minecraft mc = Minecraft.getInstance();
        org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.stop();
        org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.endCapture();
        org.zonarstudio.spraute_engine.client.editor.scene.EditorGizmo.endDrag();
        org.zonarstudio.spraute_engine.client.editor.scene.CanvasViewportEditor.cancelInteraction();
        org.zonarstudio.spraute_engine.client.imgui.SprauteImGuiBackend.setAcceptingInput(false);
        if (mc.player != null) mc.setCameraEntity(mc.player);
        restoreMinecraftGui();
        flightMode = false;
        active = false;
        editorScreen = null;
        editorCamera = null;
    }

    private static void hideMinecraftGui() {
        Minecraft mc = Minecraft.getInstance();
        if (!hideGuiOverridden) {
            oldHideGui = mc.options.hideGui;
            hideGuiOverridden = true;
        }
        mc.options.hideGui = true;
    }

    private static void restoreMinecraftGui() {
        if (!hideGuiOverridden) return;
        Minecraft.getInstance().options.hideGui = oldHideGui;
        hideGuiOverridden = false;
    }

    public static void tick() {
        if (active && editorCamera != null) {
            editorCamera.noPhysics = true;
            editorCamera.setNoGravity(true);
            editorCamera.setDeltaMovement(0, 0, 0);
            editorCamera.fallDistance = 0f;
            // When fly capture is off, keep interpolation history glued to the pose
            // so the unticked RemotePlayer does not jitter on partialTicks.
            if (!org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.isCaptured()) {
                org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.freezeCameraInterpolation();
            }
        }
    }

    public static boolean isFlightMode() {
        return flightMode;
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * True while Minecraft renders through the detached editor fly-cam.
     * Player camera settings (rest offset, boom, FOV) and cinematics must leave
     * that view alone — the editor viewport is authoring space, not gameplay.
     */
    public static boolean isRenderingEditorCamera() {
        return active && editorCamera != null
                && Minecraft.getInstance().getCameraEntity() == editorCamera;
    }

    public static SprauteEngineEditorScreen editorScreen() {
        return editorScreen;
    }

    public static Entity cameraEntity() {
        return editorCamera;
    }

    /**
     * Position of the view the user flies/looks with in the editor (detached camera),
     * not the real player model. Falls back to the render camera / local player.
     */
    public static Vec3 viewPosition() {
        if (editorCamera != null) {
            return editorCamera.getEyePosition(1f);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && mc.level != null) {
            return mc.gameRenderer.getMainCamera().getPosition();
        }
        if (mc.player != null) {
            return mc.player.getEyePosition(1f);
        }
        return Vec3.ZERO;
    }

    /** {@code x, y, z} from {@link #viewPosition()} for pasting into scripts. */
    public static String formatViewCoords() {
        Vec3 p = viewPosition();
        return formatCoord(p.x) + ", " + formatCoord(p.y) + ", " + formatCoord(p.z);
    }

    public static String formatViewCoord(char axis) {
        Vec3 p = viewPosition();
        return switch (Character.toLowerCase(axis)) {
            case 'y' -> formatCoord(p.y);
            case 'z' -> formatCoord(p.z);
            default -> formatCoord(p.x);
        };
    }

    /** Block under the editor fly-cam crosshair, or {@code null} if sky/miss. */
    public static BlockPos viewTargetBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = editorCamera != null ? editorCamera : mc.player;
        if (entity == null) return null;
        Vec3 start = viewPosition();
        if (mc.gameRenderer == null) return null;
        Vec3 look = new Vec3(mc.gameRenderer.getMainCamera().getLookVector());
        if (look.lengthSqr() < 1e-12) return null;
        Vec3 end = start.add(look.normalize().scale(256.0));
        BlockHitResult hit = mc.level.clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return hit.getBlockPos();
    }

    public static boolean hasViewTargetBlock() {
        return viewTargetBlock() != null;
    }

    public static String formatViewBlockCoord(char axis) {
        BlockPos pos = viewTargetBlock();
        if (pos == null) return "0";
        return switch (Character.toLowerCase(axis)) {
            case 'y' -> formatCoord(pos.getY());
            case 'z' -> formatCoord(pos.getZ());
            default -> formatCoord(pos.getX());
        };
    }

    public static String formatViewBlockCoords() {
        BlockPos pos = viewTargetBlock();
        if (pos == null) return "0, 0, 0";
        return formatCoord(pos.getX()) + ", " + formatCoord(pos.getY()) + ", " + formatCoord(pos.getZ());
    }

    public static String formatCoord(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            return Long.toString(Math.round(v));
        }
        String s = String.format(Locale.US, "%.3f", v);
        // Trim trailing zeros: 1.500 -> 1.5
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    public static boolean hasDetachedCamera() {
        return active && editorCamera != null;
    }

    public static double anchorX() { return anchorX; }
    public static double anchorY() { return anchorY; }
    public static double anchorZ() { return anchorZ; }
    public static float anchorYaw() { return anchorYaw; }
    public static float anchorPitch() { return anchorPitch; }
}
