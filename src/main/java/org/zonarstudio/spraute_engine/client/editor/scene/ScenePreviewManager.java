package org.zonarstudio.spraute_engine.client.editor.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.client.editor.SprauteEditorState;
import org.zonarstudio.spraute_engine.client.editor.SprauteEngineEditorScreen;
import org.zonarstudio.spraute_engine.compat.SprauteRenderCompat;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.core.parser.SpAnimationParser;
import org.zonarstudio.spraute_engine.entity.client.SpAnimationCache;
import org.zonarstudio.spraute_engine.entity.client.SpModelCache;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneLightRuntime;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneParticleEmitter;
import org.zonarstudio.spraute_engine.scene.PlayerCameraPose;
import org.zonarstudio.spraute_engine.scene.SceneTransform;
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Editor-only NPC previews rendered directly into the world. Previews never
 * become entities, are never saved to the level and vanish when the editor closes.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class ScenePreviewManager {
    private static final ResourceLocation FALLBACK_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/unknown_pack.png");
    private static final Map<String, InstanceEntry> INSTANCES = new HashMap<>();
    private static final Map<String, Integer> PARTICLE_PREVIEW_COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<String, Float> PARTICLE_PREVIEW_ACCUM = new ConcurrentHashMap<>();
    /** Running shape-sample offset per previewed node — see {@code SceneParticleEmitter.buildCustomBurst}. */
    private static final Map<String, Long> PARTICLE_PREVIEW_INDEX = new ConcurrentHashMap<>();
    /** Editor-only light blocks: nodeId → placed position (cleared when editor closes). */
    private static final Map<String, BlockPos> LIGHT_PREVIEW_POS = new HashMap<>();
    private static final Map<String, Integer> LIGHT_PREVIEW_LEVEL = new HashMap<>();

    private ScenePreviewManager() {}

    /** Previews are visible while the editor screen is open or during free flight. */
    public static boolean previewsVisible() {
        Minecraft mc = Minecraft.getInstance();
        return SceneEditorSession.document() != null
                && (mc.screen instanceof SprauteEngineEditorScreen || SprauteEditorState.isFlightMode());
    }

    public static void invalidateInstance(String nodeId) {
        INSTANCES.remove(nodeId);
        PARTICLE_PREVIEW_COOLDOWN.remove(nodeId);
        PARTICLE_PREVIEW_ACCUM.remove(nodeId);
        PARTICLE_PREVIEW_INDEX.remove(nodeId);
    }

    public static void clearInstances() {
        INSTANCES.clear();
        PARTICLE_PREVIEW_COOLDOWN.clear();
        PARTICLE_PREVIEW_ACCUM.clear();
        PARTICLE_PREVIEW_INDEX.clear();
        clearLightPreviews();
    }

    /**
     * One-shot editor preview for a PARTICLE node (used when loop is off — «Запустить»).
     * Works even if «Предпросмотр в мире» is unchecked.
     */
    public static void playParticlePreviewOnce(String nodeId) {
        if (nodeId == null || nodeId.isEmpty() || !previewsVisible()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.particleEngine == null) return;
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return;

        doc.walk((node, parentWorld) -> {
            if (!nodeId.equals(node.id) || node.type != SceneNodeType.PARTICLE || !node.active) return;
            SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
            int spawned = Math.max(1, node.particleCount);
            long idx = PARTICLE_PREVIEW_INDEX.getOrDefault(node.id, 0L);
            SceneParticleEmitter.emitClient(mc.level, mc.particleEngine, node, world, mc.level.random, spawned, idx);
            PARTICLE_PREVIEW_INDEX.put(node.id, idx + spawned);
            int interval = particlePreviewInterval(node);
            PARTICLE_PREVIEW_COOLDOWN.put(node.id, interval - 1);
            PARTICLE_PREVIEW_COOLDOWN.put(node.id + ":once", 1);
        });
    }

    /** Client tick: spawn preview particles for PARTICLE nodes while the editor is open. */
    public static void tickParticlePreviews() {
        if (!previewsVisible()) {
            PARTICLE_PREVIEW_COOLDOWN.clear();
            PARTICLE_PREVIEW_ACCUM.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || mc.particleEngine == null) return;
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return;
        // Live runtime already emits from the server — avoid double spawn.
        if (SceneEditorSession.isLiveRuntime()) return;

        doc.walk((node, parentWorld) -> {
            if (node.type != SceneNodeType.PARTICLE || !node.previewEnabled || !node.active) return;
            var spec = node.particles(false);
            boolean loop = spec != null && spec.custom && spec.emission != null
                    ? spec.emission.loop
                    : node.particleLoop;
            if (!loop) return;
            SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
            if (spec != null && spec.custom && spec.emission != null && spec.emission.enabled
                    && spec.emission.rate > 0f) {
                float acc = PARTICLE_PREVIEW_ACCUM.getOrDefault(node.id, 0f) + spec.emission.rate / 20f;
                int count = Math.min(512, (int) acc);
                PARTICLE_PREVIEW_ACCUM.put(node.id, acc - count);
                if (count > 0) {
                    long idx = PARTICLE_PREVIEW_INDEX.getOrDefault(node.id, 0L);
                    SceneParticleEmitter.emitClient(mc.level, mc.particleEngine, node, world,
                            mc.level.random, count, idx);
                    PARTICLE_PREVIEW_INDEX.put(node.id, idx + count);
                }
                return;
            }
            int cd = PARTICLE_PREVIEW_COOLDOWN.getOrDefault(node.id, 0);
            if (cd > 0) {
                PARTICLE_PREVIEW_COOLDOWN.put(node.id, cd - 1);
                return;
            }
            int spawned = Math.max(1, node.particleCount);
            long idx = PARTICLE_PREVIEW_INDEX.getOrDefault(node.id, 0L);
            SceneParticleEmitter.emitClient(mc.level, mc.particleEngine, node, world, mc.level.random, spawned, idx);
            PARTICLE_PREVIEW_INDEX.put(node.id, idx + spawned);
            PARTICLE_PREVIEW_COOLDOWN.put(node.id, Math.max(1, node.particleInterval) - 1);
        });
    }

    private static int particlePreviewInterval(SceneNode node) {
        var spec = node.particles(false);
        if (spec == null || !spec.custom || spec.emission.rate <= 0f) {
            return Math.max(1, node.particleInterval);
        }
        return Math.max(1, Math.round(Math.max(1, node.particleCount) * 20f / spec.emission.rate));
    }

    /**
     * Places real {@code minecraft:light} for LIGHT nodes while editing (integrated server only).
     * Live scenes already place lights via {@link SceneLightRuntime} — skipped here.
     */
    public static void tickLightPreviews() {
        if (!previewsVisible() || SceneEditorSession.isLiveRuntime()) {
            clearLightPreviews();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        var server = mc.getSingleplayerServer();
        if (server == null) return; // remote client — visual radius only
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return;
        ServerLevel level = server.getLevel(mc.level.dimension());
        if (level == null) return;

        Set<String> seen = new HashSet<>();
        doc.walk((node, parentWorld) -> {
            if (node.type != SceneNodeType.LIGHT) return;
            boolean on = node.active && node.previewEnabled && node.lightEnabled && node.lightLevel > 0;
            if (!on) {
                clearOneLightPreview(level, node.id);
                return;
            }
            seen.add(node.id);
            SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
            BlockPos target = new BlockPos(Mth.floor(world.px), Mth.floor(world.py), Mth.floor(world.pz));
            int lvl = Mth.clamp(node.lightLevel, 0, 15);
            BlockPos prev = LIGHT_PREVIEW_POS.get(node.id);
            Integer prevLvl = LIGHT_PREVIEW_LEVEL.get(node.id);
            // Re-verify: another light may have cleared our shared cell (duplicate at same pos).
            if (target.equals(prev) && prevLvl != null && prevLvl == lvl
                    && SceneLightRuntime.isLightAt(level, target, lvl)) {
                return;
            }
            clearOneLightPreview(level, node.id);
            if (SceneLightRuntime.tryPlace(level, target, lvl)) {
                LIGHT_PREVIEW_POS.put(node.id, target.immutable());
                LIGHT_PREVIEW_LEVEL.put(node.id, lvl);
            }
        });
        for (String id : new ArrayList<>(LIGHT_PREVIEW_POS.keySet())) {
            if (!seen.contains(id)) clearOneLightPreview(level, id);
        }
    }

    private static void clearLightPreviews() {
        Minecraft mc = Minecraft.getInstance();
        ServerLevel level = null;
        if (mc.getSingleplayerServer() != null && mc.level != null) {
            level = mc.getSingleplayerServer().getLevel(mc.level.dimension());
        }
        if (level != null) {
            for (String id : new ArrayList<>(LIGHT_PREVIEW_POS.keySet())) {
                clearOneLightPreview(level, id);
            }
        }
        LIGHT_PREVIEW_POS.clear();
        LIGHT_PREVIEW_LEVEL.clear();
    }

    private static void clearOneLightPreview(ServerLevel level, String nodeId) {
        BlockPos pos = LIGHT_PREVIEW_POS.remove(nodeId);
        LIGHT_PREVIEW_LEVEL.remove(nodeId);
        if (pos == null) return;
        // Shared cell: keep the block if another preview light still claims this pos.
        if (LIGHT_PREVIEW_POS.containsValue(pos)) return;
        SceneLightRuntime.clearLightBlock(level, pos);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        //? if >=1.20.1 {
        boolean previewStage = event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        boolean gizmoStage = event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if (!previewStage && !gizmoStage) return;
        //?} else {
        /*boolean previewStage = event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        boolean gizmoStage = previewStage;
        if (!previewStage) return;
        *///?}
        if (!previewsVisible()) return;
        Minecraft mc = Minecraft.getInstance();
        SceneDocument doc = SceneEditorSession.document();
        if (mc.level == null || doc == null) return;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = event.getCamera().getPosition();

        if (previewStage) {
            boolean live = SceneEditorSession.isLiveRuntime();
            doc.walk((node, parentWorld) -> {
                if (node.type == SceneNodeType.HITBOX) {
                    if (!EditorGizmo.overlaysEnabled() || !node.showHitbox) return;
                    SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
                    renderNpcHitbox(node, world, pose, buffers, cam);
                    return;
                }
                if (node.type == SceneNodeType.PARTICLE) {
                    if (!EditorGizmo.overlaysEnabled() || !node.previewEnabled) return;
                    SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
                    renderParticleShape(node, world, pose, buffers, cam);
                    return;
                }
                // LIGHT: real illumination via tickLightPreviews() — no yellow overlay markers.
                if (node.type == SceneNodeType.LIGHT) return;
                if (node.type == SceneNodeType.AUDIO_SOURCE) {
                    // Selection stage always draws zones; here only the persistent preview.
                    if (!EditorGizmo.overlaysEnabled()
                            || !node.showAudio || SceneEditorSession.isSelected(node.id)) return;
                    SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
                    renderAudioSource(node, world, pose, buffers, cam);
                    return;
                }
                // Live scene: real world entities are shown — skip ghost NPC/MODEL previews.
                // Exception: a node being edited in the Timeline still needs the ghost so
                // keyframe edits are visible over the live entity.
                if (live && !AnimatorTimelineSession.appliesTo(node.id)) return;
                if (!node.previewEnabled) return;
                if (org.zonarstudio.spraute_engine.scene.SceneWorldCanvasRuntime.isWorldCanvas(node)) {
                    if (!node.active) return;
                    SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
                    WorldCanvasEditorPreview.render(doc, node, world, pose, cam);
                    return;
                }
                if (node.type == SceneNodeType.BILLBOARD) {
                    if (!org.zonarstudio.spraute_engine.client.BillboardClientState.shouldRender()) return;
                    SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
                    renderBillboardPreview(node, world, pose, buffers, cam);
                    return;
                }
                if (node.type != SceneNodeType.NPC && node.type != SceneNodeType.MODEL) return;
                SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
                renderNpcPreview(node, world, pose, buffers, cam);
                if (EditorGizmo.overlaysEnabled() && node.showHitbox) {
                    renderNpcHitbox(node, world, pose, buffers, cam);
                }
            });
            // Draw as world geometry with the existing depth buffer. The grid is
            // occluded by blocks/entities instead of covering the overview.
            EditorGrid.render(pose, buffers, cam);
            buffers.endBatch();
        }

        if (gizmoStage) {
            if (!EditorGizmo.overlaysEnabled() && !EditorGizmo.gizmosEnabled()) return;
            // AFTER_PARTICLES retains the normal world pose stack in both supported
            // versions. Clearing only depth keeps color intact and puts controls
            // above blocks, translucent geometry, NPCs and particles.
            RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            if (EditorGizmo.overlaysEnabled()) {
                renderCameraLooks(doc, pose, buffers, cam);
            }
            renderSelectionAndGizmo(doc, pose, buffers, cam);
            buffers.endBatch();
        }
    }

    /**
     * Submits glowing ghost NPC/MODEL silhouettes into vanilla's outline buffer.
     * Called from {@code LevelRenderer} immediately before {@code endOutlineBatch}
     * so the entity-outline post-pass produces the real runtime glow (not a white fill).
     *
     * @return true if any silhouette was written (forces {@code entityEffect.process})
     */
    public static boolean submitGlowingOutlines(PoseStack pose, Vec3 cam) {
        if (!previewsVisible()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.levelRenderer == null) return false;
        if (!mc.levelRenderer.shouldShowEntityOutlines()) return false;
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return false;

        boolean live = SceneEditorSession.isLiveRuntime();
        boolean[] any = {false};
        var outlines = mc.renderBuffers().outlineBufferSource();
        outlines.setColor(255, 255, 255, 255);

        doc.walk((node, parentWorld) -> {
            if (!node.glowing || !node.previewEnabled) return;
            if (node.type != SceneNodeType.NPC && node.type != SceneNodeType.MODEL) return;
            if (live && !AnimatorTimelineSession.appliesTo(node.id)) return;
            if (node.model == null || node.model.isEmpty()) return;
            SpGeoModel model = SpModelCache.getOrLoad(node.model);
            if (model.boneMap.isEmpty()) return;

            SpModelInstance instance = getInstance(node, model);
            instance.resetAnims();
            if (AnimatorTimelineSession.appliesTo(node.id)) {
                AnimatorTimelineSession.applyPreview(instance);
            } else {
                applyPreviewAnimation(node, instance);
            }
            instance.computeTransforms();

            SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
            ResourceLocation texture = resolveTexture(node.texture);
            VertexConsumer outline = outlines.getBuffer(RenderType.outline(texture));

            pose.pushPose();
            pose.translate(world.px - cam.x, world.py - cam.y, world.pz - cam.z);
            SprauteRenderCompat.rotateY(pose, 180.0f - (float) world.ry);
            SprauteRenderCompat.rotateX(pose, (float) world.rx);
            SprauteRenderCompat.rotateZ(pose, (float) world.rz);
            pose.scale((float) world.sx, (float) world.sy, (float) world.sz);
            org.zonarstudio.spraute_engine.entity.client.SpGeoRenderer.render(
                    instance, pose, outline, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f, null);
            pose.popPose();
            any[0] = true;
        });
        // Do NOT endOutlineBatch here — LevelRenderer flushes and runs entityEffect.
        return any[0];
    }

    private static void renderNpcPreview(SceneNode node, SceneTransform world,
                                         PoseStack pose, MultiBufferSource buffers, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (node.model == null || node.model.isEmpty()) return;
        SpGeoModel model = SpModelCache.getOrLoad(node.model);
        if (model.boneMap.isEmpty()) return;

        SpModelInstance instance = getInstance(node, model);
        instance.resetAnims();
        if (AnimatorTimelineSession.appliesTo(node.id)) {
            // Timeline drives the pose: edited clip at the playhead, live while editing keys.
            AnimatorTimelineSession.applyPreview(instance);
        } else {
            applyPreviewAnimation(node, instance);
        }
        instance.computeTransforms();

        pose.pushPose();
        pose.translate(world.px - cam.x, world.py - cam.y, world.pz - cam.z);
        SprauteRenderCompat.rotateY(pose, 180.0f - (float) world.ry);
        SprauteRenderCompat.rotateX(pose, (float) world.rx);
        SprauteRenderCompat.rotateZ(pose, (float) world.rz);
        pose.scale((float) world.sx, (float) world.sy, (float) world.sz);

        ResourceLocation texture = resolveTexture(node.texture);
        boolean translucent = node.translucent || node.opacity < 0.999f;
        VertexConsumer consumer = buffers.getBuffer(
                SprauteRenderCompat.geoEntity(texture, translucent));

        //? if >=1.20.1 {
        BlockPos lightPos = BlockPos.containing(world.px, world.py + 1, world.pz);
        //?} else {
        /*BlockPos lightPos = new BlockPos(world.px, world.py + 1, world.pz);
        *///?}
        int light = LightTexture.pack(
                mc.level.getBrightness(LightLayer.BLOCK, lightPos),
                mc.level.getBrightness(LightLayer.SKY, lightPos));

        org.zonarstudio.spraute_engine.entity.client.SpGeoRenderer.render(
                instance, pose, consumer, light, OverlayTexture.NO_OVERLAY,
                1f, 1f, 1f, node.opacity * 0.95f, null, 0);

        // Glowing uses vanilla entity outline (see submitGlowingOutlines + mixin).

        // Selected bone highlight: white box around the posed bone cubes.
        String selBone = AnimatorTimelineSession.selectedBone();
        if (selBone != null && node.id.equals(SceneEditorSession.selectedNodeId())) {
            var spBone = model.getBone(selBone);
            var boneWorld = instance.getBoneMatrix(selBone);
            if (spBone != null && boneWorld != null && !spBone.cubes.isEmpty()) {
                VertexConsumer boneLines = buffers.getBuffer(RenderType.lines());
                for (var cube : spBone.cubes) {
                    double[] b = EditorBonePicker.cubeModelBounds(cube, spBone, boneWorld);
                    // Model pixels → MC block units with Bedrock X-mirror.
                    drawModelSpaceBox(pose, boneLines,
                            -b[3] / 16.0, b[1] / 16.0, b[2] / 16.0,
                            -b[0] / 16.0, b[4] / 16.0, b[5] / 16.0);
                }
            }
        }
        pose.popPose();
    }

    private static void drawModelSpaceBox(PoseStack pose, VertexConsumer lines,
                                          double x0, double y0, double z0,
                                          double x1, double y1, double z1) {
        LevelRenderer.renderLineBox(pose, lines, x0, y0, z0, x1, y1, z1, 1f, .62f, .17f, 1f);
    }

    /** Camera-facing translucent quad matching the runtime billboard entity. */
    private static void renderBillboardPreview(SceneNode node, SceneTransform world,
                                               PoseStack pose, MultiBufferSource buffers, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        float w = Math.max(0.01f, node.billboardWidth * (float) Math.abs(world.sx));
        float h = Math.max(0.01f, node.billboardHeight * (float) Math.abs(world.sy));

        pose.pushPose();
        pose.translate(world.px - cam.x, world.py + h / 2.0 - cam.y, world.pz - cam.z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        SprauteRenderCompat.rotateY(pose, 180.0f);

        ResourceLocation texture = resolveTexture(node.texture);
        RenderType previewType = node.billboardSeeThrough
                ? RenderType.textSeeThrough(texture)
                : SprauteRenderCompat.geoEntity(texture);
        VertexConsumer consumer = buffers.getBuffer(previewType);

        //? if >=1.20.1 {
        BlockPos lightPos = BlockPos.containing(world.px, world.py + 1, world.pz);
        //?} else {
        /*BlockPos lightPos = new BlockPos(world.px, world.py + 1, world.pz);
        *///?}
        int light = LightTexture.pack(
                mc.level.getBrightness(LightLayer.BLOCK, lightPos),
                mc.level.getBrightness(LightLayer.SKY, lightPos));

        var last = pose.last();
        billboardVertex(consumer, last, -w / 2f, -h / 2f, 0f, 1f, light);
        billboardVertex(consumer, last, w / 2f, -h / 2f, 1f, 1f, light);
        billboardVertex(consumer, last, w / 2f, h / 2f, 1f, 0f, light);
        billboardVertex(consumer, last, -w / 2f, h / 2f, 0f, 0f, light);
        pose.popPose();
    }

    private static void billboardVertex(VertexConsumer consumer, PoseStack.Pose last,
                                        float x, float y, float u, float v, int light) {
        consumer.vertex(last.pose(), x, y, 0f)
                .color(1f, 1f, 1f, 0.95f)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(last.normal(), 0f, 1f, 0f)
                .endVertex();
    }

    private static void applyPreviewAnimation(SceneNode node, SpModelInstance instance) {
        String file = node.animation;
        if ((file == null || file.isEmpty()) && node.children != null) {
            for (SceneNode child : node.children) {
                if (child.type == SceneNodeType.ANIMATOR
                        && child.animatorFile != null && !child.animatorFile.isBlank()) {
                    file = child.animatorFile;
                    break;
                }
            }
        }
        if (file == null || file.isEmpty()) return;
        SpAnimationParser.AnimationSet set = SpAnimationCache.getOrLoad(file);
        if (set == null) return;
        SpAnimationParser.AnimationClip clip = set.playable(node.idleAnimation);
        if (clip == null) return;
        float timeSec = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        clip.apply(instance, timeSec, 1f, true, false);
    }

    private static void renderSelectionAndGizmo(SceneDocument doc, PoseStack pose,
                                                MultiBufferSource buffers, Vec3 cam) {
        SceneNode selected = SceneEditorSession.selectedNode();
        // GUI nodes live in 2D canvas space: no world selection box / 3D gizmo. A world-space
        // canvas is an object in the world, so it gets both.
        if (selected == null || !selected.hasTransform() || !EditorGizmo.worldEditable(selected)) return;

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        // Draw a selection box for every selected transformable (non-UI) node.
        if (EditorGizmo.overlaysEnabled()) {
            for (String id : SceneEditorSession.selectedNodeIds()) {
                SceneNode n = doc.findNode(id);
                if (n == null || !n.hasTransform() || !EditorGizmo.worldEditable(n)) continue;
                SceneTransform w = doc.worldTransform(n.id);
                if (w == null) continue;
                AABB bounds = EditorGizmo.nodeBounds(n, w);
                boolean primary = id.equals(selected.id);
                if (primary) renderBlockedParts(bounds, pose);
                LevelRenderer.renderLineBox(pose, lines, bounds,
                        primary ? 0.35f : 0.55f,
                        primary ? 0.68f : 0.78f,
                        primary ? 1f : 0.45f,
                        primary ? 1f : 0.75f);
            }
        }

        SceneTransform world = doc.worldTransform(selected.id);
        if (world == null) {
            pose.popPose();
            return;
        }

        // AUDIO_SOURCE: always show full-volume + outer falloff zones when selected.
        if (EditorGizmo.overlaysEnabled() && selected.type == SceneNodeType.AUDIO_SOURCE) {
            renderAudioSourceZones(selected, world, pose, lines);
        }

        if (EditorGizmo.gizmosEnabled()
                && EditorGizmo.mode() != EditorGizmo.Mode.SELECT
                && selected.type != SceneNodeType.POST_PROCESS) {
            Vec3 origin = EditorGizmo.gizmoOrigin(doc, selected);
            if (origin == null) origin = new Vec3(world.px, world.py, world.pz);
            float len = EditorGizmo.gizmoLength(origin);
            EditorGizmo.Mode gizmoMode = EditorGizmo.mode();
            if (gizmoMode == EditorGizmo.Mode.MOVE) {
                drawCenterHandle(pose, lines, origin, EditorGizmo.hoverAxis() == EditorGizmo.Axis.UNIFORM
                        || EditorGizmo.dragAxis() == EditorGizmo.Axis.UNIFORM);
                for (EditorGizmo.Axis axis : new EditorGizmo.Axis[]{EditorGizmo.Axis.X, EditorGizmo.Axis.Y, EditorGizmo.Axis.Z}) {
                    Vec3 dir = EditorGizmo.axisDirection(axis, world);
                    drawAxisArrow(pose, lines, origin, dir, len, axis, axis == EditorGizmo.dragAxis()
                            || (!EditorGizmo.isDragging() && axis == EditorGizmo.hoverAxis()));
                }
            } else if (gizmoMode == EditorGizmo.Mode.ROTATE) {
                drawCenterHandle(pose, lines, origin, EditorGizmo.hoverAxis() == EditorGizmo.Axis.UNIFORM
                        || EditorGizmo.dragAxis() == EditorGizmo.Axis.UNIFORM);
                for (EditorGizmo.Axis axis : new EditorGizmo.Axis[]{EditorGizmo.Axis.X, EditorGizmo.Axis.Y, EditorGizmo.Axis.Z}) {
                    if (selected.type == SceneNodeType.HITBOX && axis != EditorGizmo.Axis.Y) continue;
                    Vec3 normal = EditorGizmo.axisDirection(axis, world);
                    float[] c = axisColor(axis, axis == EditorGizmo.dragAxis()
                            || (!EditorGizmo.isDragging() && axis == EditorGizmo.hoverAxis()));
                    drawCircle(pose, lines, origin, normal, len, c[0], c[1], c[2]);
                }
            } else if (gizmoMode == EditorGizmo.Mode.SCALE) {
                drawCenterHandle(pose, lines, origin, EditorGizmo.hoverAxis() == EditorGizmo.Axis.UNIFORM
                        || EditorGizmo.dragAxis() == EditorGizmo.Axis.UNIFORM);
                for (EditorGizmo.Axis axis : new EditorGizmo.Axis[]{EditorGizmo.Axis.X, EditorGizmo.Axis.Y, EditorGizmo.Axis.Z}) {
                    Vec3 dir = EditorGizmo.axisDirection(axis, world);
                    float[] c = axisColor(axis, axis == EditorGizmo.dragAxis()
                            || (!EditorGizmo.isDragging() && axis == EditorGizmo.hoverAxis()));
                    drawScaleHandle(pose, lines, origin.add(dir.scale(len)), c[0], c[1], c[2]);
                }
            }
        }
        pose.popPose();
    }

    /**
     * World overlay for CAMERA nodes: look arrow + FOV pyramid from the actual
     * view pose (follow-player boom / orbit included), not the raw local offset.
     */
    private static void renderCameraLooks(SceneDocument doc, PoseStack pose,
                                          MultiBufferSource buffers, Vec3 cam) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        doc.walk((node, ignored) -> {
            if (node.type != SceneNodeType.CAMERA) return;
            if (!EditorGizmo.playerCameraGizmosEnabled() && isPlayerCamera(doc, node)) return;
            renderCameraFrustum(doc, node, pose, lines);
        });
        pose.popPose();
    }

    private static boolean isPlayerCamera(SceneDocument doc, SceneNode node) {
        if (doc == null || node == null) return false;
        SceneNode parent = doc.findParent(node.id);
        return parent != null && parent.type == SceneNodeType.PLAYER;
    }

    private static void renderCameraFrustum(SceneDocument doc, SceneNode node,
                                            PoseStack pose, VertexConsumer lines) {
        SceneTransform world = PlayerCameraPose.worldTransform(doc, node);
        if (world == null) return;
        Vec3 origin = new Vec3(world.px, world.py, world.pz);
        Vec3 forward = Vec3.directionFromRotation((float) world.rx, (float) world.ry);
        Vec3 right = Vec3.directionFromRotation(0f, (float) world.ry + 90f);
        Vec3 up = forward.cross(right).scale(-1);
        if (up.lengthSqr() < 1e-8) up = new Vec3(0, 1, 0);
        else up = up.normalize();
        right = right.normalize();
        forward = forward.normalize();

        boolean selected = SceneEditorSession.isSelected(node.id);
        float r = selected ? 0.86f : 0.62f;
        float g = selected ? 0.52f : 0.40f;
        float b = selected ? 1f : 0.78f;

        float vfov = node.cameraFov > 1f ? node.cameraFov : 70f;
        vfov = Mth.clamp(vfov, 10f, 170f);
        Minecraft mc = Minecraft.getInstance();
        float aspect = Math.max(0.35f, (float) mc.getWindow().getGuiScaledWidth()
                / Math.max(1, mc.getWindow().getGuiScaledHeight()));
        float far = selected ? 3.4f : 2.2f;
        float halfH = far * (float) Math.tan(Math.toRadians(vfov * 0.5));
        float halfW = halfH * aspect;

        Vec3 farC = origin.add(forward.scale(far));
        Vec3 fu = up.scale(halfH);
        Vec3 fr = right.scale(halfW);
        Vec3[] farCorners = {
                farC.add(fu).subtract(fr),
                farC.add(fu).add(fr),
                farC.subtract(fu).add(fr),
                farC.subtract(fu).subtract(fr)
        };
        for (Vec3 corner : farCorners) {
            drawLine(pose, lines, origin, corner, r, g, b);
        }
        for (int i = 0; i < 4; i++) {
            drawLine(pose, lines, farCorners[i], farCorners[(i + 1) % 4], r, g, b);
        }

        float near = 0.28f;
        float nScale = near / far;
        Vec3 nearC = origin.add(forward.scale(near));
        Vec3 nu = fu.scale(nScale);
        Vec3 nr = fr.scale(nScale);
        Vec3[] nearCorners = {
                nearC.add(nu).subtract(nr),
                nearC.add(nu).add(nr),
                nearC.subtract(nu).add(nr),
                nearC.subtract(nu).subtract(nr)
        };
        for (int i = 0; i < 4; i++) {
            drawLine(pose, lines, nearCorners[i], nearCorners[(i + 1) % 4], r, g, b);
        }

        drawArrow(pose, lines, origin, forward, far * 0.92f, r, g, b);

        double body = 0.11;
        LevelRenderer.renderLineBox(pose, lines,
                new AABB(origin.x - body, origin.y - body, origin.z - body,
                        origin.x + body, origin.y + body, origin.z + body),
                r, g, b, 1f);
    }

    /**
     * Paints the union of selected-node volume ∩ solid blocks as a single overlay
     * so overlapping block cells do not stack darkness.
     */
    private static void renderBlockedParts(AABB nodeBounds, PoseStack pose) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || nodeBounds == null) return;

        int minX = Mth.floor(nodeBounds.minX);
        int minY = Mth.floor(nodeBounds.minY);
        int minZ = Mth.floor(nodeBounds.minZ);
        int maxX = Mth.floor(nodeBounds.maxX);
        int maxY = Mth.floor(nodeBounds.maxY);
        int maxZ = Mth.floor(nodeBounds.maxZ);

        long blockCount = (long) (maxX - minX + 1)
                * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (blockCount > 4096L) return;

        AABB union = null;
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            var state = mc.level.getBlockState(pos);
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(mc.level, pos);
            if (shape.isEmpty()) continue;

            for (AABB localBox : shape.toAabbs()) {
                AABB blockBox = localBox.move(pos);
                if (!nodeBounds.intersects(blockBox)) continue;
                AABB intersection = nodeBounds.intersect(blockBox);
                union = union == null ? intersection : union.minmax(intersection);
            }
        }

        if (union == null) return;

        BufferBuilder fill = Tesselator.getInstance().getBuilder();
        fill.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addFilledBox(pose, fill, union, 0.16f, 0.17f, 0.19f, 0.38f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(fill.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void addFilledBox(PoseStack pose, VertexConsumer out, AABB box,
                                     float r, float g, float b, float a) {
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        quad(out, pose, x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0, r,g,b,a);
        quad(out, pose, x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1, r,g,b,a);
        quad(out, pose, x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, r,g,b,a);
        quad(out, pose, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, r,g,b,a);
        quad(out, pose, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, r,g,b,a);
        quad(out, pose, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, r,g,b,a);
    }

    private static void quad(VertexConsumer out, PoseStack pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a) {
        var matrix = pose.last().pose();
        out.vertex(matrix, ax, ay, az).color(r, g, b, a).endVertex();
        out.vertex(matrix, bx, by, bz).color(r, g, b, a).endVertex();
        out.vertex(matrix, cx, cy, cz).color(r, g, b, a).endVertex();
        out.vertex(matrix, dx, dy, dz).color(r, g, b, a).endVertex();
    }

    private static void renderNpcHitbox(SceneNode node, SceneTransform world, PoseStack pose,
                                        MultiBufferSource buffers, Vec3 cam) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        if (node.type == SceneNodeType.HITBOX) {
            Vec3[] corners = org.zonarstudio.spraute_engine.scene.SceneHitboxUtil.corners(
                    org.zonarstudio.spraute_engine.scene.SceneHitboxUtil.orientedBox(node, world));
            int[][] edges = {
                    {0,1}, {1,3}, {3,2}, {2,0},
                    {4,5}, {5,7}, {7,6}, {6,4},
                    {0,4}, {1,5}, {2,6}, {3,7}
            };
            for (int[] edge : edges) {
                drawLine(pose, lines, corners[edge[0]], corners[edge[1]], 1f, .42f, .12f);
            }
        } else {
            LevelRenderer.renderLineBox(pose, lines, EditorGizmo.nodeBounds(node, world),
                    1f, .42f, .12f, .9f);
        }
        pose.popPose();
    }

    private static void renderAudioSource(SceneNode node, SceneTransform world, PoseStack pose,
                                          MultiBufferSource buffers, Vec3 cam) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        renderAudioSourceZones(node, world, pose, lines);
        pose.popPose();
    }

    /**
     * Inner zone (cyan) = 100% volume; outer zone (orange) = falloff end.
     * Shape follows {@link SceneNode#audioShape}; size uses transform scale.
     */
    private static void renderAudioSourceZones(SceneNode node, SceneTransform world,
                                               PoseStack pose, VertexConsumer lines) {
        float[] outer = org.zonarstudio.spraute_engine.scene.SceneAudioAttenuation.outerExtents(node, world);
        float[] inner = org.zonarstudio.spraute_engine.scene.SceneAudioAttenuation.innerExtents(node, world);
        Vec3 origin = new Vec3(world.px, world.py, world.pz);
        boolean cube = "cube".equals(org.zonarstudio.spraute_engine.scene.SceneAudioAttenuation.normalizeShape(node.audioShape));
        if (cube) {
            LevelRenderer.renderLineBox(pose, lines,
                    new AABB(origin.x - outer[0], origin.y - outer[1], origin.z - outer[2],
                            origin.x + outer[0], origin.y + outer[1], origin.z + outer[2]),
                    1f, 0.55f, 0.15f, 0.95f);
            LevelRenderer.renderLineBox(pose, lines,
                    new AABB(origin.x - inner[0], origin.y - inner[1], origin.z - inner[2],
                            origin.x + inner[0], origin.y + inner[1], origin.z + inner[2]),
                    0.25f, 0.95f, 1f, 1f);
        } else {
            drawEllipsoidRings(pose, lines, world, outer[0], outer[1], outer[2], 1f, 0.55f, 0.15f);
            drawEllipsoidRings(pose, lines, world, inner[0], inner[1], inner[2], 0.25f, 0.95f, 1f);
        }
        // Center cross
        double s = 0.2;
        drawLine(pose, lines, origin.add(-s, 0, 0), origin.add(s, 0, 0), 0.4f, 0.9f, 1f);
        drawLine(pose, lines, origin.add(0, -s, 0), origin.add(0, s, 0), 0.4f, 0.9f, 1f);
        drawLine(pose, lines, origin.add(0, 0, -s), origin.add(0, 0, s), 0.4f, 0.9f, 1f);
    }

    private static void drawEllipsoidRings(PoseStack pose, VertexConsumer lines, SceneTransform world,
                                           float rx, float ry, float rz, float r, float g, float b) {
        drawScaledRing(pose, lines, world, rx, 0f, rz, r, g, b); // XZ
        drawScaledRing(pose, lines, world, rx, ry, 0f, r, g, b); // XY
        drawScaledRing(pose, lines, world, 0f, ry, rz, r, g, b); // YZ
    }

    /**
     * Ellipse in a principal plane. Pass one of: (rx,0,rz)=XZ, (rx,ry,0)=XY, (0,ry,rz)=YZ.
     */
    private static void drawScaledRing(PoseStack pose, VertexConsumer lines, SceneTransform world,
                                       float ax, float ay, float az, float r, float g, float b) {
        Vec3 prev = null;
        int segments = 48;
        for (int i = 0; i <= segments; i++) {
            double ang = (Math.PI * 2.0 * i) / segments;
            float c = (float) Math.cos(ang);
            float s = (float) Math.sin(ang);
            float lx, ly, lz;
            if (ay == 0f) {          // XZ
                lx = c * ax;
                ly = 0f;
                lz = s * az;
            } else if (az == 0f) {   // XY
                lx = c * ax;
                ly = s * ay;
                lz = 0f;
            } else {                // YZ
                lx = 0f;
                ly = c * ay;
                lz = s * az;
            }
            double[] p = org.zonarstudio.spraute_engine.scene.SceneParticleEmitter.localOffset(world, lx, ly, lz);
            Vec3 cur = new Vec3(p[0], p[1], p[2]);
            if (prev != null) drawLine(pose, lines, prev, cur, r, g, b);
            prev = cur;
        }
    }

    /** Wireframe helper for emitter shapes (point / line / sphere / spiral). */
    private static void renderParticleShape(SceneNode node, SceneTransform world, PoseStack pose,
                                            MultiBufferSource buffers, Vec3 cam) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Vec3 origin = new Vec3(world.px, world.py, world.pz);
        String shape = SceneParticleEmitter.normalizeShape(node.particleShape);
        float r = 0.98f, g = 0.72f, b = 0.28f;
        switch (shape) {
            case "line" -> {
                double[] end = SceneParticleEmitter.localOffset(world,
                        node.particleLineX, node.particleLineY, node.particleLineZ);
                drawLine(pose, lines, origin, new Vec3(end[0], end[1], end[2]), r, g, b);
            }
            case "sphere" -> {
                float radius = Math.max(0.05f, node.particleRadius);
                drawCircle(pose, lines, origin, new Vec3(0, 1, 0), radius, r, g, b);
                drawCircle(pose, lines, origin, new Vec3(1, 0, 0), radius, r, g, b);
                drawCircle(pose, lines, origin, new Vec3(0, 0, 1), radius, r, g, b);
            }
            case "spiral" -> {
                float radius = Math.max(0.05f, node.particleRadius);
                float height = node.particleHeight;
                Vec3 prev = null;
                int segments = 48;
                for (int i = 0; i <= segments; i++) {
                    double t = (double) i / segments;
                    double angle = 4 * Math.PI * t;
                    double[] p = SceneParticleEmitter.localOffset(world,
                            (float) (radius * Math.cos(angle)), (float) (height * t),
                            (float) (radius * Math.sin(angle)));
                    Vec3 cur = new Vec3(p[0], p[1], p[2]);
                    if (prev != null) drawLine(pose, lines, prev, cur, r, g, b);
                    prev = cur;
                }
            }
            default -> {
                double s = 0.15;
                drawLine(pose, lines, origin.add(-s, 0, 0), origin.add(s, 0, 0), r, g, b);
                drawLine(pose, lines, origin.add(0, -s, 0), origin.add(0, s, 0), r, g, b);
                drawLine(pose, lines, origin.add(0, 0, -s), origin.add(0, 0, s), r, g, b);
            }
        }
        if ("directional".equals(SceneParticleEmitter.normalizeEmitMode(node.particleEmitMode))) {
            double[] dir = SceneParticleEmitter.localDirection(world,
                    node.particleDirX, node.particleDirY, node.particleDirZ);
            double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            if (len > 1e-6) {
                Vec3 axis = new Vec3(dir[0] / len, dir[1] / len, dir[2] / len);
                float arrowLen = Math.max(0.6f, Math.min(3f, 0.6f + node.particleSpeed * 2f));
                drawArrow(pose, lines, origin, axis, arrowLen, 0.35f, 0.85f, 1f);
                // Cone ring at tip
                float coneDeg = Math.max(0f, Math.min(89f, node.particleCone));
                if (coneDeg > 0.5f) {
                    double rad = Math.toRadians(coneDeg);
                    float ringR = (float) (arrowLen * Math.tan(rad));
                    Vec3 tip = origin.add(axis.scale(arrowLen));
                    drawCircle(pose, lines, tip, axis, Math.max(0.05f, ringR), 0.4f, 0.9f, 1f);
                    // 4 generators from origin to ring
                    Vec3 ref = Math.abs(axis.y) < 0.85 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
                    Vec3 t = ref.cross(axis).normalize();
                    Vec3 bt = axis.cross(t).normalize();
                    for (int i = 0; i < 4; i++) {
                        double ang = Math.PI * 0.5 * i;
                        Vec3 edge = tip.add(t.scale(Math.cos(ang) * ringR)).add(bt.scale(Math.sin(ang) * ringR));
                        drawLine(pose, lines, origin, edge, 0.3f, 0.7f, 0.95f);
                    }
                }
            }
        }
        pose.popPose();
    }

    private static void drawAxisArrow(PoseStack pose, VertexConsumer lines, Vec3 origin, Vec3 direction,
                                      float length, EditorGizmo.Axis axis, boolean active) {
        float[] c = axisColor(axis, active);
        drawArrow(pose, lines, origin, direction, length, c[0], c[1], c[2]);
    }

    private static float[] axisColor(EditorGizmo.Axis axis, boolean active) {
        float boost = active ? 1.0f : 0.78f;
        return switch (axis) {
            case X -> new float[]{1f * boost, 0.18f * boost, 0.18f * boost};
            case Y -> new float[]{0.20f * boost, 1f * boost, 0.20f * boost};
            case Z -> new float[]{0.20f * boost, 0.48f * boost, 1f * boost};
            case UNIFORM -> new float[]{0.95f * boost, 0.95f * boost, 0.95f * boost};
        };
    }

    private static void drawCircle(PoseStack pose, VertexConsumer lines, Vec3 center, Vec3 normal,
                                   float radius, float r, float g, float b) {
        Vec3 n = normal.normalize();
        Vec3 ref = Math.abs(n.y) < 0.85 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 tangent = ref.cross(n).normalize();
        Vec3 bitangent = n.cross(tangent).normalize();
        Vec3 prev = null;
        int segments = 48;
        for (int i = 0; i <= segments; i++) {
            double ang = (Math.PI * 2.0 * i) / segments;
            Vec3 point = center.add(tangent.scale(Math.cos(ang) * radius))
                    .add(bitangent.scale(Math.sin(ang) * radius));
            if (prev != null) drawLine(pose, lines, prev, point, r, g, b);
            prev = point;
        }
    }

    private static void drawScaleHandle(PoseStack pose, VertexConsumer lines, Vec3 center,
                                        float r, float g, float b) {
        double s = 0.08;
        Vec3[] corners = {
                center.add(-s, -s, -s), center.add(s, -s, -s),
                center.add(s, -s, s), center.add(-s, -s, s),
                center.add(-s, s, -s), center.add(s, s, -s),
                center.add(s, s, s), center.add(-s, s, s)
        };
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] edge : edges) {
            drawLine(pose, lines, corners[edge[0]], corners[edge[1]], r, g, b);
        }
    }

    private static void drawCenterHandle(PoseStack pose, VertexConsumer lines, Vec3 center, boolean active) {
        drawScaleHandle(pose, lines, center, active ? 1f : 0.35f, active ? 1f : 0.35f, active ? 1f : 0.35f);
    }

    private static void drawArrow(PoseStack pose, VertexConsumer lines, Vec3 origin,
                                  Vec3 direction, float length, float r, float g, float b) {
        Vec3 dir = direction.normalize();
        Vec3 tip = origin.add(dir.scale(length));
        drawLine(pose, lines, origin, tip, r, g, b);

        double headLength = Math.max(.10, length * .20);
        double headWidth = Math.max(.055, length * .075);
        Vec3 reference = Math.abs(dir.y) < .85 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 sideA = dir.cross(reference).normalize().scale(headWidth);
        Vec3 sideB = dir.cross(sideA).normalize().scale(headWidth);
        Vec3 base = tip.subtract(dir.scale(headLength));
        drawLine(pose, lines, tip, base.add(sideA), r, g, b);
        drawLine(pose, lines, tip, base.subtract(sideA), r, g, b);
        drawLine(pose, lines, tip, base.add(sideB), r, g, b);
        drawLine(pose, lines, tip, base.subtract(sideB), r, g, b);
    }

    private static void drawLine(PoseStack pose, VertexConsumer consumer,
                                 Vec3 from, Vec3 to, float r, float g, float b) {
        var last = pose.last();
        Vec3 n = to.subtract(from).normalize();
        consumer.vertex(last.pose(), (float) from.x, (float) from.y, (float) from.z)
                .color(r, g, b, 1f).normal(last.normal(), (float) n.x, (float) n.y, (float) n.z).endVertex();
        consumer.vertex(last.pose(), (float) to.x, (float) to.y, (float) to.z)
                .color(r, g, b, 1f).normal(last.normal(), (float) n.x, (float) n.y, (float) n.z).endVertex();
    }

    /** Posed preview instance of a node, if one was rendered; used by bone picking/highlight. */
    public static SpModelInstance instanceFor(String nodeId) {
        InstanceEntry entry = INSTANCES.get(nodeId);
        return entry == null ? null : entry.instance;
    }

    private static SpModelInstance getInstance(SceneNode node, SpGeoModel model) {
        InstanceEntry entry = INSTANCES.get(node.id);
        if (entry == null || !entry.modelPath.equals(node.model)) {
            entry = new InstanceEntry(node.model, new SpModelInstance(model));
            INSTANCES.put(node.id, entry);
        }
        return entry.instance;
    }

    public static ResourceLocation resolveTexture(String texture) {
        if (texture == null || texture.isEmpty()) return FALLBACK_TEXTURE;
        SprauteResourcePath.Result parsed = SprauteResourcePath.parse(texture, SprauteResourcePath.Kind.TEXTURE);
        return parsed.ok() ? parsed.location() : FALLBACK_TEXTURE;
    }

    private record InstanceEntry(String modelPath, SpModelInstance instance) {}
}
