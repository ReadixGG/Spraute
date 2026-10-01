package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import java.util.ArrayList;
import java.util.List;
//? if >=1.20.1 {
import org.joml.Matrix4f;
//?} else {
/*import com.mojang.math.Matrix4f;
*///?}

/**
 * Offscreen cache for a scroll viewport: rebuild when content/scroll/hover changes,
 * otherwise blit one textured quad per frame.
 */
public final class UiScrollFreezeLayer {
    private TextureTarget target;
    /** Logical widget size (GUI units). */
    private int logicalW;
    private int logicalH;
    /** {@link net.minecraft.client.Minecraft#getWindow()} gui scale when the FBO was allocated. */
    private int fboScale;
    private float cachedScroll = Float.NaN;
    private int cachedHoverKey = Integer.MIN_VALUE;
    private boolean valid;

    private static int guiScale() {
        return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    private static int physicalSize(int logical) {
        return Math.max(1, logical * guiScale());
    }

    public void invalidate() {
        valid = false;
        cachedScroll = Float.NaN;
        cachedHoverKey = Integer.MIN_VALUE;
    }

    public void destroy() {
        invalidate();
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        logicalW = 0;
        logicalH = 0;
        fboScale = 0;
    }

    public boolean matches(int width, int height, float scroll, int hoverKey) {
        return valid
                && target != null
                && logicalW == width
                && logicalH == height
                && fboScale == guiScale()
                && cachedScroll == scroll
                && cachedHoverKey == hoverKey;
    }

    @FunctionalInterface
    public interface ContentRenderer {
        void renderInto(int width, int height);
    }

    /** Rebuild FBO with orthographic GUI projection matching {@code width}×{@code height} GUI units. */
    public void rebuild(int width, int height, float scroll, int hoverKey, ContentRenderer renderer) {
        if (width < 1 || height < 1) return;
        Minecraft mc = Minecraft.getInstance();
        ensureTarget(width, height);
        int pw = physicalSize(width);
        int ph = physicalSize(height);
        target.setClearColor(0f, 0f, 0f, 0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.viewport(0, 0, pw, ph);

        //? if >=1.20.1 {
        Matrix4f oldProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        com.mojang.blaze3d.vertex.VertexSorting oldSort = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0f, (float) width, (float) height, 0f, 1000f, 3000f),
                com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        //?} else {
        /*com.mojang.math.Matrix4f oldProj = RenderSystem.getProjectionMatrix().copy();
        RenderSystem.setProjectionMatrix(
                com.mojang.math.Matrix4f.orthographic(0f, (float) width, 0f, (float) height, 1000f, 3000f));
        *///?}

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.translate(0.0D, 0.0D, -2000.0D);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        try {
            renderer.renderInto(width, height);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            //? if >=1.20.1 {
            RenderSystem.setProjectionMatrix(oldProj, oldSort);
            //?} else {
            /*RenderSystem.setProjectionMatrix(oldProj);
            *///?}
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
        }

        cachedScroll = scroll;
        cachedHoverKey = hoverKey;
        valid = true;
    }

    //? if >=1.20.1 {
    public void blit(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        if (target == null || !valid) return;
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        setLinearTextureFilter(target.getColorTextureId());
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // FBO color attachment is Y-flipped relative to GUI.
        float x1 = x + width;
        float y1 = y + height;
        buffer.vertex(matrix, (float) x, (float) y, 0f).uv(0f, 1f).endVertex();
        buffer.vertex(matrix, (float) x, y1, 0f).uv(0f, 0f).endVertex();
        buffer.vertex(matrix, x1, y1, 0f).uv(1f, 0f).endVertex();
        buffer.vertex(matrix, x1, (float) y, 0f).uv(1f, 1f).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
    }

    /**
     * Blit the FBO with a soft alpha falloff on each edge ({@code fl/ft/fr/fb} in GUI pixels).
     */
    public void blitFeathered(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int width, int height,
                              float alpha, float fl, float ft, float fr, float fb, boolean smooth) {
        if (target == null || !valid) return;
        if (fl <= 0.25f && ft <= 0.25f && fr <= 0.25f && fb <= 0.25f) {
            blit(graphics, x, y, width, height, alpha);
            return;
        }
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        setLinearTextureFilter(target.getColorTextureId());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        emitFeathered(matrix, buffer, x, y, width, height, fl, ft, fr, fb, smooth, alpha);
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableDepthTest();
    }
    //?} else {
    /*public void blit(PoseStack pose, int x, int y, int width, int height, float alpha) {
        if (target == null || !valid) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        setLinearTextureFilter(target.getColorTextureId());
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));

        Matrix4f matrix = pose.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float x1 = x + width;
        float y1 = y + height;
        buffer.vertex(matrix, (float) x, (float) y, 0f).uv(0f, 1f).endVertex();
        buffer.vertex(matrix, (float) x, y1, 0f).uv(0f, 0f).endVertex();
        buffer.vertex(matrix, x1, y1, 0f).uv(1f, 0f).endVertex();
        buffer.vertex(matrix, x1, (float) y, 0f).uv(1f, 1f).endVertex();
        buffer.end();
        Tesselator.getInstance().end();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
    }

    public void blitFeathered(PoseStack pose, int x, int y, int width, int height,
                              float alpha, float fl, float ft, float fr, float fb, boolean smooth) {
        if (target == null || !valid) return;
        if (fl <= 0.25f && ft <= 0.25f && fr <= 0.25f && fb <= 0.25f) {
            blit(pose, x, y, width, height, alpha);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        setLinearTextureFilter(target.getColorTextureId());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        Matrix4f matrix = pose.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        emitFeathered(matrix, buffer, x, y, width, height, fl, ft, fr, fb, smooth, alpha);
        buffer.end();
        Tesselator.getInstance().end();

        RenderSystem.enableDepthTest();
    }
    *///?}

    private static void emitFeathered(Matrix4f matrix, BufferBuilder buffer,
                                      int x, int y, int width, int height,
                                      float fl, float ft, float fr, float fb,
                                      boolean smooth, float alphaMul) {
        if (width < 1 || height < 1) return;
        float aMul = Math.max(0f, Math.min(1f, alphaMul));
        float L = Math.max(0f, Math.min(fl, width * 0.5f));
        float R = Math.max(0f, Math.min(fr, width * 0.5f));
        float T = Math.max(0f, Math.min(ft, height * 0.5f));
        float B = Math.max(0f, Math.min(fb, height * 0.5f));
        List<float[]> xs = axisStops(width, L, R, smooth, false);
        List<float[]> ys = axisStops(height, T, B, smooth, true);
        for (int i = 0; i < xs.size() - 1; i++) {
            float[] x0 = xs.get(i);
            float[] x1 = xs.get(i + 1);
            if (x1[0] - x0[0] < 0.01f) continue;
            for (int j = 0; j < ys.size() - 1; j++) {
                float[] y0 = ys.get(j);
                float[] y1 = ys.get(j + 1);
                if (y1[0] - y0[0] < 0.01f) continue;
                featherVert(buffer, matrix, x + x0[0], y + y0[0], x0[1], y0[1], x0[2] * y0[2] * aMul);
                featherVert(buffer, matrix, x + x0[0], y + y1[0], x0[1], y1[1], x0[2] * y1[2] * aMul);
                featherVert(buffer, matrix, x + x1[0], y + y1[0], x1[1], y1[1], x1[2] * y1[2] * aMul);
                featherVert(buffer, matrix, x + x1[0], y + y0[0], x1[1], y0[1], x1[2] * y0[2] * aMul);
            }
        }
    }

    /** {@code pos, uv, alpha} along one axis. {@code flipUv} for FBO Y (GUI top = uv 1). */
    private static List<float[]> axisStops(float size, float fade0, float fade1, boolean smooth, boolean flipUv) {
        List<float[]> out = new ArrayList<>();
        int steps = smooth ? 6 : 1;
        if (fade0 > 0.25f) {
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                float p = fade0 * t;
                out.add(stop(p, size, edgeAlpha(t, smooth), flipUv));
            }
        } else {
            out.add(stop(0f, size, 1f, flipUv));
        }
        float innerEnd = Math.max(out.get(out.size() - 1)[0], size - Math.max(0f, fade1));
        if (innerEnd - out.get(out.size() - 1)[0] > 0.25f) {
            out.add(stop(innerEnd, size, 1f, flipUv));
        }
        if (fade1 > 0.25f) {
            boolean skipFirst = Math.abs(out.get(out.size() - 1)[0] - (size - fade1)) < 0.25f;
            for (int i = skipFirst ? 1 : 0; i <= steps; i++) {
                float t = i / (float) steps;
                float p = (size - fade1) + fade1 * t;
                out.add(stop(p, size, edgeAlpha(1f - t, smooth), flipUv));
            }
        } else if (Math.abs(out.get(out.size() - 1)[0] - size) > 0.25f) {
            out.add(stop(size, size, 1f, flipUv));
        }
        return out;
    }

    private static float[] stop(float p, float size, float alpha, boolean flipUv) {
        float u = size <= 0.001f ? 0f : p / size;
        return new float[]{p, flipUv ? 1f - u : u, alpha};
    }

    private static float edgeAlpha(float t, boolean smooth) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return smooth ? t * t * (3f - 2f * t) : t;
    }

    private static void featherVert(BufferBuilder buffer, Matrix4f matrix,
                                    float px, float py, float u, float v, float a) {
        int ia = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        buffer.vertex(matrix, px, py, 0f).color(255, 255, 255, ia).uv(u, v).endVertex();
    }

    private void ensureTarget(int width, int height) {
        int scale = guiScale();
        if (target != null && logicalW == width && logicalH == height && fboScale == scale) return;
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        int pw = physicalSize(width);
        int ph = physicalSize(height);
        target = new TextureTarget(pw, ph, true, Minecraft.ON_OSX);
        logicalW = width;
        logicalH = height;
        fboScale = scale;
        valid = false;
    }

    private static void setLinearTextureFilter(int textureId) {
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, textureId);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
    }
}
