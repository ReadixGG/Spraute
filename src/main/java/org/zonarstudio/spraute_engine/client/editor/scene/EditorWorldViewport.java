package org.zonarstudio.spraute_engine.client.editor.scene;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Copies the main world render into a texture for display inside the editor viewport.
 * Capture must run before the editor paints an opaque fullscreen fill.
 */
public final class EditorWorldViewport {
    private static TextureTarget target;
    private static int lastW;
    private static int lastH;
    private static int cachedTex = -1;
    private static int pendingW;
    private static int pendingH;
    private static boolean captureRequested;

    private EditorWorldViewport() {}

    public static void requestCapture(int width, int height) {
        if (width < 4 || height < 4) return;
        pendingW = width;
        pendingH = height;
        captureRequested = true;
    }

    /** Call at the start of {@code Screen.render}, before overwriting the framebuffer. */
    public static void flushCapture() {
        if (!captureRequested) return;
        captureRequested = false;
        cachedTex = blitMainFramebuffer(pendingW, pendingH);
    }

    public static int textureId() {
        return cachedTex;
    }

    /** Aspect ratio of the unmodified Minecraft world framebuffer. */
    public static float sourceAspect() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.height <= 0) return 16f / 9f;
        return (float) main.width / main.height;
    }

    private static int blitMainFramebuffer(int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getMainRenderTarget() == null) return -1;

        if (target == null || width != lastW || height != lastH) {
            if (target != null) target.destroyBuffers();
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            lastW = width;
            lastH = height;
        }

        int mainFbo = mc.getMainRenderTarget().frameBufferId;
        int mainW = mc.getMainRenderTarget().width;
        int mainH = mc.getMainRenderTarget().height;

        target.bindWrite(true);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        // The UI allocates a destination rectangle with this exact source aspect.
        // Copy the complete frame: uniform scale only, no crop and no distortion.
        GL30.glBlitFramebuffer(0, 0, mainW, mainH, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        // The world framebuffer's alpha is garbage after translucent GUI draws
        // (shadows, gradients). ImGui blends the captured texture by its alpha,
        // which visually inverts soft shadows — force the copy fully opaque.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
        GL11.glColorMask(false, false, false, true);
        GL11.glClearColor(0f, 0f, 0f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glColorMask(true, true, true, true);
        mc.getMainRenderTarget().bindWrite(true);
        return target.getColorTextureId();
    }

    public static void dispose() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        lastW = 0;
        lastH = 0;
        cachedTex = -1;
        captureRequested = false;
    }
}
