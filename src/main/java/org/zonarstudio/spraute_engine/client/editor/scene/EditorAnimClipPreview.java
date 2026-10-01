package org.zonarstudio.spraute_engine.client.editor.scene;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.zonarstudio.spraute_engine.compat.SprauteRenderCompat;
import org.zonarstudio.spraute_engine.core.math.SpVec3;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.core.parser.SpAnimationParser;
import org.zonarstudio.spraute_engine.entity.client.SpAnimationCache;
import org.zonarstudio.spraute_engine.entity.client.SpGeoRenderer;
import org.zonarstudio.spraute_engine.entity.client.SpModelCache;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Small freeze-frame thumbnails of animation clips for the Inspector combo lists.
 */
public final class EditorAnimClipPreview {
    public static final int SIZE = 96;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int MAX_CACHE = 64;
    /** Author/section Blockbench clips store garbage Euler; clamp so GL doesn't blow up. */
    private static final float MAX_PREVIEW_DEG = 3600f;
    private static final float MAX_PREVIEW_POS = 128f;
    private static final float MAX_PREVIEW_SCALE = 16f;

    private static final Map<String, TextureTarget> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TextureTarget> eldest) {
            if (size() <= MAX_CACHE) return false;
            TextureTarget t = eldest.getValue();
            if (t != null) t.destroyBuffers();
            return true;
        }
    };
    private static final Set<String> FAILED = new HashSet<>();

    private EditorAnimClipPreview() {}

    public static void invalidate() {
        for (TextureTarget t : CACHE.values()) {
            if (t != null) t.destroyBuffers();
        }
        CACHE.clear();
        FAILED.clear();
    }

    /**
     * Renders a freeze pose of {@code clipName} and returns a GL color texture id, or -1.
     * Time is ~35% into the clip (readable mid-pose, not always T-pose at 0).
     */
    public static int renderFreeze(String modelPath, String texturePath, String animationPath, String clipName) {
        if (modelPath == null || modelPath.isBlank() || clipName == null || clipName.isBlank()) return -1;
        if (animationPath == null || animationPath.isBlank()) return -1;

        String key = modelPath + "\0" + nullToEmpty(texturePath) + "\0" + animationPath + "\0" + clipName;
        if (FAILED.contains(key)) return -1;
        TextureTarget cached = CACHE.get(key);
        if (cached != null) return cached.getColorTextureId();

        Minecraft mc = Minecraft.getInstance();
        TextureTarget target = null;
        boolean swappedProjection = false;
        boolean pushedModelView = false;
        //? if >=1.20.1 {
        org.joml.Matrix4f oldProjection = null;
        com.mojang.blaze3d.vertex.VertexSorting oldSorting = null;
        //?} else {
        /*com.mojang.math.Matrix4f oldProjection = null;
        *///?}

        try {
            SpGeoModel model = SpModelCache.getOrLoad(modelPath);
            if (model.boneMap.isEmpty()) {
                FAILED.add(key);
                return -1;
            }
            SpAnimationParser.AnimationSet set = SpAnimationCache.getOrLoad(animationPath);
            SpAnimationParser.AnimationClip clip = set.get(clipName);
            if (clip == null) {
                FAILED.add(key);
                return -1;
            }

            SpModelInstance instance = new SpModelInstance(model);
            instance.resetAnims();
            if (!SpAnimationParser.isMetaClipName(clipName) && !clip.bones().isEmpty()) {
                clip.apply(instance, freezeTime(clip), 1f, false, false);
                sanitizePreviewPose(instance);
            }
            instance.computeTransforms();

            target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
            target.setClearColor(0.016f, 0.055f, 0.122f, 1f);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            //? if >=1.20.1 {
            oldProjection = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());
            oldSorting = RenderSystem.getVertexSorting();
            RenderSystem.setProjectionMatrix(
                    new org.joml.Matrix4f().perspective((float) Math.toRadians(40f), 1f, 0.05f, 200f),
                    com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
            swappedProjection = true;
            //?} else {
            /*oldProjection = RenderSystem.getProjectionMatrix().copy();
            RenderSystem.setProjectionMatrix(
                    com.mojang.math.Matrix4f.perspective(40.0, 1f, 0.05f, 200f));
            swappedProjection = true;
            *///?}

            PoseStack modelView = RenderSystem.getModelViewStack();
            modelView.pushPose();
            pushedModelView = true;
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            Lighting.setupForEntityInInventory();
            RenderSystem.enableDepthTest();

            PoseStack pose = new PoseStack();
            pose.translate(0, -1.0, -3.2f);
            SprauteRenderCompat.rotateX(pose, 12f);
            SprauteRenderCompat.rotateY(pose, 210f);

            ResourceLocation texture = ScenePreviewManager.resolveTexture(texturePath);
            MultiBufferSource.BufferSource buffers =
                    MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
            VertexConsumer consumer = buffers.getBuffer(SprauteRenderCompat.geoEntity(texture));
            SpGeoRenderer.render(instance, pose, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    1f, 1f, 1f, 1f, null);
            buffers.endBatch();

            CACHE.put(key, target);
            return target.getColorTextureId();
        } catch (Throwable ignored) {
            FAILED.add(key);
            if (target != null) {
                try {
                    target.destroyBuffers();
                } catch (Throwable ignoredDestroy) {
                }
            }
            return -1;
        } finally {
            if (pushedModelView) {
                RenderSystem.getModelViewStack().popPose();
                RenderSystem.applyModelViewMatrix();
            }
            if (swappedProjection) {
                //? if >=1.20.1 {
                if (oldProjection != null && oldSorting != null) {
                    RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
                }
                //?} else {
                /*if (oldProjection != null) {
                    RenderSystem.setProjectionMatrix(oldProjection);
                }
                *///?}
            }
            Lighting.setupFor3DItems();
            try {
                mc.getMainRenderTarget().bindWrite(true);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void sanitizePreviewPose(SpModelInstance instance) {
        for (SpVec3 r : instance.boneAnimRotation.values()) clampComp(r, MAX_PREVIEW_DEG, 0f);
        for (SpVec3 p : instance.boneAnimPosition.values()) clampComp(p, MAX_PREVIEW_POS, 0f);
        for (SpVec3 s : instance.boneAnimScale.values()) clampComp(s, MAX_PREVIEW_SCALE, 1f);
    }

    private static void clampComp(SpVec3 v, float maxAbs, float fallback) {
        v.x = sane(v.x, maxAbs, fallback);
        v.y = sane(v.y, maxAbs, fallback);
        v.z = sane(v.z, maxAbs, fallback);
    }

    private static float sane(float value, float maxAbs, float fallback) {
        return Float.isFinite(value) && Math.abs(value) <= maxAbs ? value : fallback;
    }

    private static float freezeTime(SpAnimationParser.AnimationClip clip) {
        float len = Math.max(0f, clip.getLengthSec());
        if (len <= 0.05f) return 0f;
        return Math.min(len * 0.35f, len - 0.01f);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
