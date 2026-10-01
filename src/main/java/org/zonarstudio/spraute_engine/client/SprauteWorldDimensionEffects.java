package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
//? if >=1.20.1 {
import org.joml.Matrix4f;
//?} else {
/*import com.mojang.math.Matrix4f;
*///?}
import org.zonarstudio.spraute_engine.registry.CustomWorldRegistry;

/**
 * Client sky/fog/clouds for {@code create world} dimensions.
 * Vanilla sky uses biome colors — we draw a solid sky instead so {@code sky = "#…"} is visible.
 */
public class SprauteWorldDimensionEffects extends net.minecraft.client.renderer.DimensionSpecialEffects {

    private final Vec3 fogColor;
    private final Vec3 skyColor;
    private final boolean noClouds;

    public SprauteWorldDimensionEffects(CustomWorldRegistry.CustomWorldDef def) {
        // First ctor arg is cloud altitude (NaN = vanilla skips clouds), not ambient light.
        super(def.clouds ? 192.0f : Float.NaN, true, SkyType.NORMAL, false, false);
        this.fogColor = rgbToVec(def.fogColor);
        this.skyColor = rgbToVec(def.skyColor);
        this.noClouds = !def.clouds;
    }

    private static Vec3 rgbToVec(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        return new Vec3(r, g, b);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float brightness) {
        float t = Mth.clamp(brightness, 0.0f, 1.0f);
        // Ignore biome fog — use configured sky/fog so the tint is always visible.
        return new Vec3(
                Mth.lerp(t, fogColor.x(), skyColor.x()),
                Mth.lerp(t, fogColor.y(), skyColor.y()),
                Mth.lerp(t, fogColor.z(), skyColor.z())
        );
    }

    @Override
    public boolean isFoggyAt(int blockX, int blockY) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTick) {
        return null;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                double camX, double camY, double camZ, Matrix4f projectionMatrix) {
        return noClouds;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                             Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        setupFog.run();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float r = (float) skyColor.x();
        float g = (float) skyColor.y();
        float b = (float) skyColor.z();
        float fr = (float) fogColor.x();
        float fg = (float) fogColor.y();
        float fb = (float) fogColor.z();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        Matrix4f mat = poseStack.last().pose();

        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(mat, 0f, 16f, 0f).color(r, g, b, 1f).endVertex();
        for (int i = 0; i <= 16; i++) {
            float ang = (float) (i * (Math.PI * 2.0 / 16.0));
            float x = Mth.sin(ang) * 64f;
            float z = Mth.cos(ang) * 64f;
            buffer.vertex(mat, x, -8f, z).color(r, g, b, 1f).endVertex();
        }
        tesselator.end();

        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 16; i++) {
            float ang = (float) (i * (Math.PI * 2.0 / 16.0));
            float x = Mth.sin(ang) * 64f;
            float z = Mth.cos(ang) * 64f;
            buffer.vertex(mat, x, -8f, z).color(r, g, b, 1f).endVertex();
            buffer.vertex(mat, x, -32f, z).color(fr, fg, fb, 1f).endVertex();
        }
        tesselator.end();

        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(mat, 0f, -48f, 0f).color(fr, fg, fb, 1f).endVertex();
        for (int i = 0; i <= 16; i++) {
            float ang = (float) (i * (Math.PI * 2.0 / 16.0));
            float x = Mth.sin(ang) * 64f;
            float z = Mth.cos(ang) * 64f;
            buffer.vertex(mat, x, -32f, z).color(fr, fg, fb, 1f).endVertex();
        }
        tesselator.end();

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        return true;
    }

    public Vec3 skyColor() { return skyColor; }
    public Vec3 fogColor() { return fogColor; }
}
