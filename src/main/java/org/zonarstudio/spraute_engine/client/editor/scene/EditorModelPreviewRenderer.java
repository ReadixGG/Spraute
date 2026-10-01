package org.zonarstudio.spraute_engine.client.editor.scene;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.zonarstudio.spraute_engine.compat.SprauteRenderCompat;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.entity.client.SpGeoRenderer;
import org.zonarstudio.spraute_engine.entity.client.SpModelCache;
import org.zonarstudio.spraute_engine.scene.SceneNode;

/**
 * Renders the selected NPC's model into an off-screen framebuffer so the
 * Inspector can show a rotatable, zoomable 3D preview via {@code ImGui.image()}.
 */
public final class EditorModelPreviewRenderer {
    public static final int SIZE = 512;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static TextureTarget target;
    private static SpModelInstance instance;
    private static String instanceModelPath;

    private static float yaw = 30f;
    private static float pitch = 15f;
    private static float zoom = 3.5f;

    private EditorModelPreviewRenderer() {}

    public static void rotate(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch = Math.max(-89f, Math.min(89f, pitch + deltaPitch));
    }

    public static void addZoom(float delta) {
        zoom = Math.max(0.8f, Math.min(16f, zoom - delta));
    }

    public static void invalidate() {
        instance = null;
        instanceModelPath = null;
    }

    /** Renders the node's model into the FBO and returns the GL texture id, or -1. */
    public static int render(SceneNode node) {
        Minecraft mc = Minecraft.getInstance();
        if (node.model == null || node.model.isEmpty()) return -1;
        SpGeoModel model = SpModelCache.getOrLoad(node.model);
        if (model.boneMap.isEmpty()) return -1;

        if (instance == null || !node.model.equals(instanceModelPath)) {
            instance = new SpModelInstance(model);
            instanceModelPath = node.model;
        }
        instance.resetAnims();
        if (AnimatorTimelineSession.appliesTo(node.id)) {
            AnimatorTimelineSession.applyPreview(instance);
        } else if (node.animation != null && !node.animation.isEmpty()) {
            var set = org.zonarstudio.spraute_engine.entity.client.SpAnimationCache.getOrLoad(node.animation);
            if (set != null) {
                var clip = set.playable(node.idleAnimation);
                if (clip != null) {
                    float timeSec = (System.currentTimeMillis() % 3_600_000L) / 1000f;
                    clip.apply(instance, timeSec, 1f, true, false);
                }
            }
        }
        instance.computeTransforms();

        if (target == null) {
            target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        }

        target.setClearColor(0.016f, 0.055f, 0.122f, 1f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        // Swap in our own perspective projection, restore everything afterwards.
        //? if >=1.20.1 {
        org.joml.Matrix4f oldProjection = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());
        com.mojang.blaze3d.vertex.VertexSorting oldSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(
                new org.joml.Matrix4f().perspective((float) Math.toRadians(45f), 1f, 0.05f, 200f),
                com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        //?} else {
        /*com.mojang.math.Matrix4f oldProjection = RenderSystem.getProjectionMatrix().copy();
        RenderSystem.setProjectionMatrix(
                com.mojang.math.Matrix4f.perspective(45.0, 1f, 0.05f, 200f));
        *///?}

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        Lighting.setupForEntityInInventory();
        RenderSystem.enableDepthTest();

        try {
            PoseStack pose = new PoseStack();
            pose.translate(0, -1.0, -zoom);
            SprauteRenderCompat.rotateX(pose, pitch);
            SprauteRenderCompat.rotateY(pose, yaw + 180f);

            ResourceLocation texture = ScenePreviewManager.resolveTexture(node.texture);
            MultiBufferSource.BufferSource buffers =
                    MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
            boolean translucent = node.translucent || node.opacity < 0.999f;
            VertexConsumer consumer = buffers.getBuffer(
                    SprauteRenderCompat.geoEntity(texture, translucent));
            SpGeoRenderer.render(instance, pose, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    1f, 1f, 1f, node.opacity, null, 0);
            if (node.glowing) {
                // The inspector preview has no Entity for vanilla's outline
                // pipeline. A slightly enlarged emissive pass provides an
                // immediate, clearly visible approximation of runtime glow.
                pose.pushPose();
                pose.scale(1.025f, 1.025f, 1.025f);
                VertexConsumer glow = buffers.getBuffer(SprauteRenderCompat.geoEntityEmissive(texture));
                SpGeoRenderer.render(instance, pose, glow, FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        .55f, .82f, 1f, .42f, null);
                pose.popPose();
            }
            buffers.endBatch();
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            //? if >=1.20.1 {
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
            //?} else {
            /*RenderSystem.setProjectionMatrix(oldProjection);
            *///?}
            Lighting.setupFor3DItems();
            mc.getMainRenderTarget().bindWrite(true);
        }

        return target.getColorTextureId();
    }
}
