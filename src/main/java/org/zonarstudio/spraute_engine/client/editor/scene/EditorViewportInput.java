package org.zonarstudio.spraute_engine.client.editor.scene;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.zonarstudio.spraute_engine.client.editor.SprauteEditorState;

/**
 * Camera fly / look inside the embedded world viewport while the editor screen stays open.
 * Uses raw GLFW cursor capture because MouseHandler.grabMouse() force-closes the screen.
 *
 * <p>Look and WASD movement run every render frame (scaled by {@link Minecraft#getDeltaFrameTime()}),
 * not on the 20 Hz client tick — tick-stepped flight felt jerky even at high FPS.
 */
public final class EditorViewportInput {
    private static float minX, minY, maxX, maxY;
    private static boolean captured;
    private static boolean active;
    private static double lastCursorX;
    private static double lastCursorY;
    private static boolean firstFrame;
    /** Frames to ignore viewport click→capture after opening the editor. */
    private static int suppressFrames;
    /** Blocks per game-tick at base speed (same feel as the old 0.55 / tick). */
    private static final float BASE_SPEED_PER_TICK = 0.55f;
    private static final float MIN_SPEED_SCALE = 0.08f;
    private static final float MAX_SPEED_SCALE = 24f;
    /** Wheel multiplier on top of {@link #BASE_SPEED_PER_TICK}. */
    private static float speedScale = 1f;

    private EditorViewportInput() {}

    public static void suppressInteraction(int frames) {
        suppressFrames = Math.max(suppressFrames, Math.max(0, frames));
    }

    public static boolean interactionAllowed() {
        return suppressFrames <= 0;
    }

    public static void setViewportRect(float x, float y, float w, float h) {
        minX = x;
        minY = y;
        maxX = x + w;
        maxY = y + h;
        active = w > 4f && h > 4f;
    }

    public static boolean isActive() { return active; }

    public static boolean contains(double mouseX, double mouseY) {
        return active && mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY;
    }

    public static boolean isCaptured() { return captured; }

    public static float speedScale() {
        return speedScale;
    }

    /** Scroll up = faster, down = slower. Call while the fly-cam is captured. */
    public static void addSpeedFromWheel(double delta) {
        if (delta == 0d || Double.isNaN(delta)) return;
        speedScale *= (float) Math.pow(1.2, delta);
        if (speedScale < MIN_SPEED_SCALE) speedScale = MIN_SPEED_SCALE;
        if (speedScale > MAX_SPEED_SCALE) speedScale = MAX_SPEED_SCALE;
    }

    public static void beginCapture() {
        if (captured || !interactionAllowed()) return;
        if (SprauteEditorState.cameraEntity() == null) return;
        captured = true;
        firstFrame = true;
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        if (GLFW.glfwRawMouseMotionSupported()) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_RAW_MOUSE_MOTION, GLFW.GLFW_TRUE);
        }
        freezeCameraInterpolation();
    }

    public static void endCapture() {
        if (!captured) return;
        captured = false;
        // Camera entity is never level-ticked. If xo/yo/zo lag behind x/y/z when
        // capture stops mid-move, the renderer lerps every frame (partialTick 0→1)
        // and the view jerks forward/back. Snap history to the current pose.
        freezeCameraInterpolation();
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        if (GLFW.glfwRawMouseMotionSupported()) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_RAW_MOUSE_MOTION, GLFW.GLFW_FALSE);
        }
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        // Put the cursor back in the middle of the viewport so it stays inside the editor.
        if (active) {
            double scaleX = (double) mc.getWindow().getScreenWidth()
                    / Math.max(1, mc.getWindow().getGuiScaledWidth());
            double scaleY = (double) mc.getWindow().getScreenHeight()
                    / Math.max(1, mc.getWindow().getGuiScaledHeight());
            GLFW.glfwSetCursorPos(window,
                    (minX + maxX) / 2.0 * scaleX,
                    (minY + maxY) / 2.0 * scaleY);
        }
    }

    /** Snap render interpolation history to the live pose (no visual lerp). */
    public static void freezeCameraInterpolation() {
        Entity player = SprauteEditorState.cameraEntity();
        if (player == null) return;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yaw = player.getYRot(), pitch = player.getXRot();
        player.xo = x;
        player.yo = y;
        player.zo = z;
        player.xOld = x;
        player.yOld = y;
        player.zOld = z;
        player.yRotO = yaw;
        player.xRotO = pitch;
        if (player instanceof net.minecraft.world.entity.LivingEntity living) {
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
        }
        player.setDeltaMovement(Vec3.ZERO);
    }

    public static void toggleCapture() {
        if (captured) endCapture(); else beginCapture();
    }

    /**
     * Called once per rendered frame, preferably before the world is drawn
     * ({@code RenderTickEvent} START) so look/move affect this frame's capture.
     */
    public static void updateFrame() {
        if (suppressFrames > 0) suppressFrames--;
        if (!captured) return;
        Minecraft mc = Minecraft.getInstance();
        Entity player = SprauteEditorState.cameraEntity();
        if (player == null) return;

        player.noPhysics = true;
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0f;

        applyLook(mc, player);
        applyFlyMove(mc, player);
        // Frame-rate movement: never leave a lerp gap (that was the 20 Hz stutter).
        freezeCameraInterpolation();
    }

    private static void applyLook(Minecraft mc, Entity player) {
        long window = mc.getWindow().getWindow();
        double[] px = new double[1];
        double[] py = new double[1];
        GLFW.glfwGetCursorPos(window, px, py);
        if (firstFrame) {
            firstFrame = false;
            lastCursorX = px[0];
            lastCursorY = py[0];
            return;
        }
        double dx = px[0] - lastCursorX;
        double dy = py[0] - lastCursorY;
        lastCursorX = px[0];
        lastCursorY = py[0];
        if (dx == 0 && dy == 0) return;

        double sens = mc.options.sensitivity().get() * 0.6 + 0.2;
        double factor = sens * sens * sens * 8.0;
        player.turn(dx * factor, dy * factor);
        // Unticked RemotePlayer: keep head/body yaw in sync for the render camera.
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
        if (player instanceof net.minecraft.world.entity.LivingEntity living) {
            living.yHeadRot = player.getYRot();
            living.yHeadRotO = player.getYRot();
            living.yBodyRot = player.getYRot();
            living.yBodyRotO = player.getYRot();
        }
    }

    private static void applyFlyMove(Minecraft mc, Entity player) {
        long window = mc.getWindow().getWindow();
        float dt = mc.getDeltaFrameTime();
        if (dt <= 0f || Float.isNaN(dt)) return;
        // Hitch protection — avoid teleport jumps after a long frame.
        if (dt > 0.5f) dt = 0.5f;

        float speed = BASE_SPEED_PER_TICK * speedScale * dt;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) {
            speed *= 2.5f;
        }

        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6) flat = new Vec3(0, 0, 1);
        else flat = flat.normalize();
        Vec3 right = flat.cross(new Vec3(0, 1, 0)).normalize();

        double dx = 0, dy = 0, dz = 0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) { dx += look.x; dy += look.y; dz += look.z; }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) { dx -= look.x; dy -= look.y; dz -= look.z; }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) { dx -= right.x; dz -= right.z; }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) { dx += right.x; dz += right.z; }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) dy += 1;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) dy -= 1;

        if (dx == 0 && dy == 0 && dz == 0) return;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        player.setPos(
                player.getX() + dx / len * speed,
                player.getY() + dy / len * speed,
                player.getZ() + dz / len * speed);
    }

    /**
     * Client tick: keep physics flags only. Movement is handled in {@link #updateFrame()}.
     */
    public static void tick() {
        if (!captured) return;
        Entity player = SprauteEditorState.cameraEntity();
        if (player == null) return;
        player.noPhysics = true;
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0f;
    }

    public static float viewportWidth() { return maxX - minX; }
    public static float viewportHeight() { return maxY - minY; }
}
