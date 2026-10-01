package org.zonarstudio.spraute_engine.client.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.scene.PostProcessSettings;

/**
 * Copies the world color buffer and runs the composite post stack
 * after the level, before hand / HUD (Unity: camera stack, not overlay UI).
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class PostProcessRenderer {
    private static TextureTarget ping;
    private static int pingW;
    private static int pingH;

    private PostProcessRenderer() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderLevel(RenderLevelStageEvent event) {
        //? if >=1.20.1 {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        //?} else {
        /*if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        *///?}
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (PostProcessShaders.composite() == null) return;
        Vec3 cam = PostProcessClient.cameraPos();
        PostProcessSettings s = PostProcessClient.blended(cam.x, cam.y, cam.z);
        if (s == null || s.isVisuallyIdentity()) return;
        try {
            apply(s);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().debug("[Spraute Post] skip: {}", t.toString());
        }
    }

    private static void apply(PostProcessSettings s) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) return;
        int w = main.width;
        int h = main.height;
        if (w < 2 || h < 2) return;
        ensurePing(w, h);
        if (ping == null) return;

        int mainFbo = main.frameBufferId;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ping.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);

        ShaderInstance shader = PostProcessShaders.composite();
        if (shader == null) return;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        try {
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, ping.getColorTextureId());
            setUniform1(shader, "Exposure", s.grade ? s.exposure : 0f);
            setUniform1(shader, "Contrast", s.grade ? s.contrast : 0f);
            setUniform1(shader, "Saturation", s.grade ? s.saturation : 0f);
            setUniform1(shader, "Temperature", s.grade ? s.temperature : 0f);
            setUniform1(shader, "Tint", s.grade ? s.tint : 0f);
            setUniform3(shader, "Filter",
                    s.grade ? s.filterR : 1f,
                    s.grade ? s.filterG : 1f,
                    s.grade ? s.filterB : 1f);
            setUniform1(shader, "Vignette", s.vignette ? s.vignetteIntensity : 0f);
            setUniform1(shader, "VignetteSmooth", s.vignette ? s.vignetteSmooth : 0.45f);
            setUniform1(shader, "BloomIntensity", s.bloom ? s.bloomIntensity : 0f);
            setUniform1(shader, "BloomThreshold", s.bloom ? s.bloomThreshold : 0.75f);
            setUniform1(shader, "Chromatic", s.chromatic ? s.chromaticAmount : 0f);
            setUniform1(shader, "Grain", s.grain ? s.grainAmount : 0f);
            setUniform1(shader, "Sharpen", s.sharpen ? s.sharpenAmount : 0f);
            float time = (mc.level.getGameTime() + mc.getFrameTime()) * 0.05f;
            setUniform1(shader, "Time", time);
            var inSize = shader.getUniform("InSize");
            if (inSize != null) inSize.set((float) w, (float) h);

            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.vertex(-1, -1, 0).uv(0f, 0f).endVertex();
            buffer.vertex(1, -1, 0).uv(1f, 0f).endVertex();
            buffer.vertex(1, 1, 0).uv(1f, 1f).endVertex();
            buffer.vertex(-1, 1, 0).uv(0f, 1f).endVertex();
            //? if >=1.20.1 {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.end());
            //?} else {
            /*buffer.end();
            Tesselator.getInstance().end();
            *///?}
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            main.bindWrite(true);
        }
    }

    private static void setUniform1(ShaderInstance shader, String name, float v) {
        var u = shader.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void setUniform3(ShaderInstance shader, String name, float x, float y, float z) {
        var u = shader.getUniform(name);
        if (u != null) u.set(x, y, z);
    }

    private static void ensurePing(int w, int h) {
        if (ping != null && pingW == w && pingH == h) return;
        if (ping != null) ping.destroyBuffers();
        ping = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        pingW = w;
        pingH = h;
    }

    public static void dispose() {
        if (ping != null) {
            ping.destroyBuffers();
            ping = null;
        }
        pingW = 0;
        pingH = 0;
    }
}
