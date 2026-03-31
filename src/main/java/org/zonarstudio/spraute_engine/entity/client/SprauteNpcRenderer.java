package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.core.math.SpVec3;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.core.parser.SpAnimationParser;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders NPC entities using the Spraute Engine's custom geo model pipeline.
 * Each entity gets its own {@link SpModelInstance} so bone transforms don't collide.
 */
public class SprauteNpcRenderer extends EntityRenderer<SprauteNpcEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> WARNED_ENTITIES = new HashSet<>();
    private static final Set<UUID> WARNED_HEAD_BONE = new HashSet<>();
    private static final Map<UUID, InstanceEntry> INSTANCES = new ConcurrentHashMap<>();
    private static final float BLEND_SECONDS = 0.2f;
    private static final float BLEND_TICKS = BLEND_SECONDS * 20f;

    public SprauteNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.5f;
    }

    @Override
    public ResourceLocation getTextureLocation(SprauteNpcEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(Spraute_engine.MODID, entity.getTexture());
    }

    @Override
    public boolean shouldShowName(SprauteNpcEntity entity) {
        return entity.isCustomNameVisible() && entity.hasCustomName();
    }

    @Override
    public void render(SprauteNpcEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        try {
            String modelPath = entity.getModel();
            if (modelPath == null || modelPath.isEmpty()) {
                super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                return;
            }

            SpGeoModel model = SpModelCache.getOrLoad(modelPath);
            if (model.boneMap.isEmpty()) {
                super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                return;
            }

            InstanceEntry entry = getOrCreateInstance(entity.getUUID(), modelPath, model);
            SpModelInstance instance = entry.instance;

            instance.resetAnims();
            applyOverlayAnimations(entity, partialTick, instance, entry);
            applyHeadBoneLook(entity, partialTick, instance, entry);
            instance.computeTransforms();

            poseStack.pushPose();

            // Rotate model to face entity's body yaw direction.
            // The X-negate (Bedrock→MC mirror) is handled per-vertex inside SpGeoRenderer,
            // so PoseStack stays clean — no scale(-1,1,1) that would break normals/winding.
            float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
            poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F - bodyYaw));

            ResourceLocation textureLoc = getTextureLocation(entity);
            RenderType renderType = RenderType.entityTranslucent(textureLoc);
            VertexConsumer consumer = bufferSource.getBuffer(renderType);

            int overlay = OverlayTexture.pack(
                OverlayTexture.u(0f),
                OverlayTexture.v(entity.hurtTime > 0 || !entity.isAlive())
            );

            SpGeoRenderer.render(instance, poseStack, consumer, packedLight, overlay,
                                 1f, 1f, 1f, 1f);

            SprauteNpcItemLayer.render(instance, entity, poseStack, bufferSource, packedLight);

            poseStack.popPose();

            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

            WARNED_ENTITIES.remove(entity.getUUID());
        } catch (Exception e) {
            try { poseStack.popPose(); } catch (Exception ignored) {}

            if (WARNED_ENTITIES.add(entity.getUUID())) {
                LOGGER.error("[Spraute Engine] Failed to render NPC '{}': {}",
                        entity.getCustomName() != null ? entity.getCustomName().getString() : entity.getStringUUID(),
                        e.getMessage(), e);
            }
        }
    }

    /** Same limits as {@link SprauteNpcEntity} server-side neck clamp (authoritative). */
    private static final float MAX_HEAD_YAW = SprauteNpcEntity.MAX_HEAD_YAW;
    private static final float MAX_HEAD_PITCH_UP = SprauteNpcEntity.MAX_HEAD_PITCH_UP;
    private static final float MAX_HEAD_PITCH_DOWN = SprauteNpcEntity.MAX_HEAD_PITCH_DOWN;
    /** Torso-local head yaw: tick rate ~20 Hz via approachDegrees; render interpolates O→current with partialTick. */
    private static final float HEAD_LOCAL_SMOOTH_SPEED = 12f;
    private static final float HEAD_LOOK_PITCH_SPEED = 4f;

    /** Server sends world yaw; resolve Bedrock bone name. */
    private static SpVec3 resolveHeadAnimRotation(SprauteNpcEntity entity, SpModelInstance instance) {
        String wanted = entity.getHeadBoneName();
        if (wanted != null && !wanted.isEmpty()) {
            SpVec3 v = instance.boneAnimRotation.get(wanted);
            if (v != null) return v;
            for (String k : instance.boneAnimRotation.keySet()) {
                if (k != null && k.equalsIgnoreCase(wanted)) return instance.boneAnimRotation.get(k);
            }
        }
        SpVec3 head = instance.boneAnimRotation.get("Head");
        if (head != null) return head;
        return instance.boneAnimRotation.get("head");
    }

    /**
     * Torso-local head yaw/pitch: tick updates {@code smooth*}, render lerps {@code *O} to current with
     * {@code partialTick} (same idea as vanilla {@code yBodyRotO}/{@code yBodyRot}).
     */
    private void applyHeadBoneLook(SprauteNpcEntity entity, float partialTick, SpModelInstance instance, InstanceEntry entry) {
        SpVec3 rot = resolveHeadAnimRotation(entity, instance);
        if (rot == null) {
            if (WARNED_HEAD_BONE.add(entity.getUUID())) {
                LOGGER.warn("[Spraute] Head bone '{}' not in model '{}'; head look disabled. Sample keys: {}",
                    entity.getHeadBoneName(), entry.modelPath,
                    instance.boneAnimRotation.keySet().stream().limit(12).toList());
            }
            return;
        }

        float lookAzimuthWorld = 0f;
        float targetPitch = 0f;
        if (entity.isHeadLookActive()) {
            lookAzimuthWorld = Mth.wrapDegrees(entity.getHeadLookYaw());
            targetPitch = Mth.clamp(entity.getHeadLookPitch(), -MAX_HEAD_PITCH_UP, MAX_HEAD_PITCH_DOWN);
        }

        int headLookGen = entity.getHeadLookTargetGen();
        if (headLookGen != entry.lastHeadLookTargetGen) {
            entry.lastHeadLookTargetGen = headLookGen;
            if (!entity.isHeadLookActive()) {
                entry.smoothHeadPitch = 0f;
            }
        }

        if (entity.tickCount != entry.lastHeadSmoothTick) {
            entry.lastHeadSmoothTick = entity.tickCount;

            entry.smoothHeadLocalYawO = entry.smoothHeadLocalYaw;
            entry.smoothHeadPitchO = entry.smoothHeadPitch;

            float targetLocal = 0f;
            if (entity.isHeadLookActive()) {
                targetLocal = Mth.degreesDifference(entity.yBodyRot, lookAzimuthWorld);
                targetLocal = Mth.clamp(targetLocal, -MAX_HEAD_YAW, MAX_HEAD_YAW);
            }

            entry.smoothHeadLocalYaw = Mth.approachDegrees(entry.smoothHeadLocalYaw, targetLocal, HEAD_LOCAL_SMOOTH_SPEED);
            entry.smoothHeadLocalYaw = Mth.clamp(entry.smoothHeadLocalYaw, -MAX_HEAD_YAW, MAX_HEAD_YAW);
            entry.smoothHeadPitch = Mth.approach(entry.smoothHeadPitch, targetPitch, HEAD_LOOK_PITCH_SPEED);
            entry.smoothHeadPitch = Mth.clamp(entry.smoothHeadPitch, -MAX_HEAD_PITCH_UP, MAX_HEAD_PITCH_DOWN);
        }

        float renderLocalYaw = Mth.clamp(
                Mth.rotLerp(partialTick, entry.smoothHeadLocalYawO, entry.smoothHeadLocalYaw),
                -MAX_HEAD_YAW, MAX_HEAD_YAW);
        float renderPitch = Mth.clamp(
                Mth.lerp(partialTick, entry.smoothHeadPitchO, entry.smoothHeadPitch),
                -MAX_HEAD_PITCH_UP, MAX_HEAD_PITCH_DOWN);

        if (Math.abs(renderLocalYaw) > 0.01f || Math.abs(renderPitch) > 0.01f) {
            rot.y -= renderLocalYaw;
            rot.x += renderPitch;
        }
    }

    private static final String AUTO_IDLE_KEY = "__auto_idle__";
    private static final String AUTO_WALK_KEY = "__auto_walk__";

    private void applyOverlayAnimations(SprauteNpcEntity entity, float partialTick, SpModelInstance instance, InstanceEntry entry) {
        syncOverlayState(entity, partialTick, entry);
        syncAutoAnims(entity, partialTick, entry);

        if (entry.layers.isEmpty()) return;

        SpAnimationParser.AnimationSet set = SpAnimationCache.getOrLoad(entity.getAnimation());
        float now = entity.tickCount + partialTick;
        float weight = entity.getOverlayWeight();

        Iterator<Map.Entry<String, OverlayLayerState>> it = entry.layers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, OverlayLayerState> mapEntry = it.next();
            OverlayLayerState layer = mapEntry.getValue();
            SpAnimationParser.AnimationClip clip = set.get(layer.clipName);
            if (clip == null) continue;

            float elapsedSec = org.zonarstudio.spraute_engine.client.SprauteScriptScreen.disableEntityAnimations ? 0f : Math.max(0f, (now - layer.startTick) / 20f);
            if (layer.mode == SprauteNpcEntity.OVERLAY_ONCE) {
                float lengthSec = clip.getLengthSec();
                if (lengthSec > 0f && elapsedSec >= lengthSec && layer.fadeOutStartTick < 0f) {
                    layer.fadeOutStartTick = now;
                }
            }

            float layerBlend = getLayerBlend(now, layer);
            if (layerBlend <= 0f && layer.fadeOutStartTick >= 0f) {
                it.remove();
                continue;
            }

            // Auto-anim layers always use full weight 1.0 (they are the base pose)
            float effectiveWeight = (mapEntry.getKey().startsWith("__auto_")) ? layerBlend : weight * layerBlend;

            if (layer.mode == SprauteNpcEntity.OVERLAY_LOOP
                || layer.mode == SprauteNpcEntity.OVERLAY_ONCE
                || layer.mode == SprauteNpcEntity.OVERLAY_FREEZE) {
                boolean playbackLoop = layer.mode == SprauteNpcEntity.OVERLAY_LOOP;
                clip.apply(instance, elapsedSec, effectiveWeight, playbackLoop, layer.additive);
            }
        }
    }

    /**
     * Automatically start/stop idle and walk layers based on the entity's moving state.
     * These layers use reserved keys "__auto_idle__" and "__auto_walk__".
     * They are managed here so they blend in/out smoothly as the NPC starts/stops moving.
     * Manual stopOverlayAnimation() calls do NOT suppress auto-anims; they will resume next frame.
     */
    private void syncAutoAnims(SprauteNpcEntity entity, float partialTick, InstanceEntry entry) {
        String idleAnimName = entity.getIdleAnim();
        String walkAnimName = entity.getWalkAnim();
        boolean hasIdle = idleAnimName != null && !idleAnimName.isEmpty();
        boolean hasWalk = walkAnimName != null && !walkAnimName.isEmpty();
        if (!hasIdle && !hasWalk) return;

        boolean moving = entity.isMovingSynced();
        float now = entity.tickCount + partialTick;

        if (hasIdle) {
            OverlayLayerState idleLayer = entry.layers.get(AUTO_IDLE_KEY);
            boolean idleShouldPlay = !moving;
            if (idleShouldPlay) {
                if (idleLayer == null) {
                    idleLayer = new OverlayLayerState();
                    idleLayer.clipName = idleAnimName;
                    idleLayer.mode = SprauteNpcEntity.OVERLAY_LOOP;
                    idleLayer.startTick = now;
                    idleLayer.fadeInStartTick = now;
                    idleLayer.fadeOutStartTick = -1f;
                    entry.layers.put(AUTO_IDLE_KEY, idleLayer);
                } else if (idleLayer.fadeOutStartTick >= 0f) {
                    // Fading out — restart it
                    idleLayer.clipName = idleAnimName;
                    idleLayer.mode = SprauteNpcEntity.OVERLAY_LOOP;
                    idleLayer.startTick = now;
                    idleLayer.fadeInStartTick = now;
                    idleLayer.fadeOutStartTick = -1f;
                }
            } else {
                if (idleLayer != null && idleLayer.fadeOutStartTick < 0f) {
                    idleLayer.fadeOutStartTick = now;
                }
            }
        }

        if (hasWalk) {
            OverlayLayerState walkLayer = entry.layers.get(AUTO_WALK_KEY);
            boolean walkShouldPlay = moving;
            if (walkShouldPlay) {
                if (walkLayer == null) {
                    walkLayer = new OverlayLayerState();
                    walkLayer.clipName = walkAnimName;
                    walkLayer.mode = SprauteNpcEntity.OVERLAY_LOOP;
                    walkLayer.startTick = now;
                    walkLayer.fadeInStartTick = now;
                    walkLayer.fadeOutStartTick = -1f;
                    entry.layers.put(AUTO_WALK_KEY, walkLayer);
                } else if (walkLayer.fadeOutStartTick >= 0f) {
                    walkLayer.clipName = walkAnimName;
                    walkLayer.mode = SprauteNpcEntity.OVERLAY_LOOP;
                    walkLayer.startTick = now;
                    walkLayer.fadeInStartTick = now;
                    walkLayer.fadeOutStartTick = -1f;
                }
            } else {
                if (walkLayer != null && walkLayer.fadeOutStartTick < 0f) {
                    walkLayer.fadeOutStartTick = now;
                }
            }
        }
    }

    private float getLayerBlend(float nowTick, OverlayLayerState layer) {
        float inAlpha = Mth.clamp((nowTick - layer.fadeInStartTick) / BLEND_TICKS, 0f, 1f);
        if (layer.fadeOutStartTick < 0f) {
            return inAlpha;
        }
        float outAlpha = 1f - Mth.clamp((nowTick - layer.fadeOutStartTick) / BLEND_TICKS, 0f, 1f);
        return inAlpha * outAlpha;
    }

    private void syncOverlayState(SprauteNpcEntity entity, float partialTick, InstanceEntry entry) {
        int commandId = entity.getOverlayCommandId();
        if (entry.lastOverlayCommandId == commandId) return;
        entry.lastOverlayCommandId = commandId;
        float now = entity.tickCount + partialTick;

        String clipName = entity.getOverlayAnim();
        byte mode = entity.getOverlayMode();

        if (mode == SprauteNpcEntity.OVERLAY_NONE) {
            if (clipName == null || clipName.isEmpty()) {
                for (OverlayLayerState layer : entry.layers.values()) {
                    if (layer.fadeOutStartTick < 0f) layer.fadeOutStartTick = now;
                }
            } else {
                OverlayLayerState layer = entry.layers.get(clipName.toLowerCase(Locale.ROOT));
                if (layer != null && layer.fadeOutStartTick < 0f) layer.fadeOutStartTick = now;
            }
            return;
        }

        if (clipName == null || clipName.isEmpty()) return;
        String key = clipName.toLowerCase(Locale.ROOT);
        if (!entity.isOverlayAdditive()) {
            for (Map.Entry<String, OverlayLayerState> e : entry.layers.entrySet()) {
                if (!e.getKey().equals(key) && e.getValue().fadeOutStartTick < 0f) {
                    e.getValue().fadeOutStartTick = now;
                }
            }
        }

        OverlayLayerState layer = entry.layers.get(key);
        if (layer == null) {
            layer = new OverlayLayerState();
            entry.layers.put(key, layer);
        }
        layer.clipName = clipName;
        layer.mode = mode;
        layer.startTick = now;
        layer.fadeInStartTick = now;
        layer.fadeOutStartTick = -1f;
        layer.additive = entity.isOverlayAdditive();
    }

    private InstanceEntry getOrCreateInstance(UUID entityId, String modelPath, SpGeoModel model) {
        InstanceEntry entry = INSTANCES.get(entityId);
        if (entry != null && entry.modelPath.equals(modelPath)) {
            return entry;
        }
        SpModelInstance instance = new SpModelInstance(model);
        InstanceEntry created = new InstanceEntry(modelPath, instance);
        INSTANCES.put(entityId, created);
        return created;
    }

    /** Gets the absolute world position of a bone for particle effects. */
    public SpVec3 getBoneWorldPosition(SprauteNpcEntity entity, String boneName, float partialTick) {
        InstanceEntry entry = INSTANCES.get(entity.getUUID());
        if (entry == null || entry.instance == null) return null;
        
        org.zonarstudio.spraute_engine.core.math.SpMatrix4 matrix = entry.instance.getBoneMatrix(boneName);
        if (matrix == null) return null;

        // Model space translation
        float mx = matrix.m[12];
        float my = matrix.m[13];
        float mz = matrix.m[14];

        // Convert Bedrock/Blockbench model space (scale 16) to Minecraft world space.
        mx /= 16.0f;
        my /= 16.0f;
        mz /= 16.0f;

        // Entity rotation
        float bodyYaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        // Rotation around Y (180 - yaw)
        float rad = (float) Math.toRadians(180.0 - bodyYaw);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        // Standard 2D rotation for X and Z
        float rotX = mx * cos + mz * sin;
        float rotZ = -mx * sin + mz * cos;

        // Add entity world position
        double x = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX()) + rotX;
        double y = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + my;
        double z = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ()) + rotZ;

        return new SpVec3((float)x, (float)y, (float)z);
    }

    private static final class InstanceEntry {
        final String modelPath;
        final SpModelInstance instance;
        final Map<String, OverlayLayerState> layers = new LinkedHashMap<>();
        int lastOverlayCommandId = Integer.MIN_VALUE;
        int lastHeadLookTargetGen = Integer.MIN_VALUE;
        int lastHeadSmoothTick = Integer.MIN_VALUE;

        float smoothHeadLocalYaw = 0f;
        float smoothHeadLocalYawO = 0f;
        float smoothHeadPitch = 0f;
        float smoothHeadPitchO = 0f;

        InstanceEntry(String modelPath, SpModelInstance instance) {
            this.modelPath = modelPath;
            this.instance = instance;
        }
    }

    private static final class OverlayLayerState {
        String clipName;
        float startTick;
        float fadeInStartTick;
        float fadeOutStartTick = -1f;
        byte mode;
        /** Matches {@link SprauteNpcEntity#isOverlayAdditive()} at overlay start; auto layers always true. */
        boolean additive = true;
    }
}
