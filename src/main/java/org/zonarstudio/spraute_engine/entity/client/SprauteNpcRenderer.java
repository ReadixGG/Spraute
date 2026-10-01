package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import org.zonarstudio.spraute_engine.compat.SprauteRenderCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;
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

    private static boolean hasPlayerSkinOverlay(SprauteNpcEntity entity) {
        String overlay = entity.getPlayerSkinOverlayUuid();
        if (overlay != null && !overlay.isEmpty()) return true;
        String tex = entity.getTexture();
        return tex != null && tex.startsWith("player_skin:");
    }

    /** MC skins paint the face on the NORTH unwrap slot; Bedrock geo faces +Z (SOUTH). Flip head so the face looks at camera. */
    private static void applyPlayerSkinHeadFacing(SprauteNpcEntity entity, SpModelInstance instance) {
        if (!hasPlayerSkinOverlay(entity)) return;
        for (var entry : instance.boneAnimRotation.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("head")) {
                entry.getValue().y += 180f;
                return;
            }
        }
    }

    private static final ResourceLocation FALLBACK_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/unknown_pack.png");

    @Override
    public ResourceLocation getTextureLocation(SprauteNpcEntity entity) {
        String overlay = entity.getPlayerSkinOverlayUuid();
        if (overlay != null && !overlay.isEmpty()) {
            try {
                java.util.UUID u = java.util.UUID.fromString(overlay);
                ResourceLocation skin = org.zonarstudio.spraute_engine.client.PlayerSkinTextures.resolve(u);
                if (skin != null) return skin;
            } catch (Exception ignored) {
            }
        }
        String tex = entity.getTexture();
        if (tex != null && tex.startsWith("player_skin:")) {
            ResourceLocation skin = org.zonarstudio.spraute_engine.client.PlayerSkinTextures.resolveFromTextureKey(tex);
            if (skin != null) return skin;
        }
        if (tex == null || tex.isEmpty()) return FALLBACK_TEXTURE;
        SprauteResourcePath.Result parsed = SprauteResourcePath.parse(tex, SprauteResourcePath.Kind.TEXTURE);
        if (!parsed.ok()) {
            SprauteResourcePath.warnClientPlayerOnce("tex:" + entity.getUUID(), parsed);
            return FALLBACK_TEXTURE;
        }
        return parsed.location();
    }

    @Override
    protected void renderNameTag(SprauteNpcEntity entity,
                                 net.minecraft.network.chat.Component displayName,
                                 PoseStack poseStack,
                                 MultiBufferSource bufferSource,
                                 int packedLight) {
        float extra = entity.getNameYOffset();
        if (extra != 0f) {
            poseStack.pushPose();
            poseStack.translate(0.0, extra, 0.0);
            super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight);
            poseStack.popPose();
        } else {
            super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight);
        }
    }

    @Override
    public boolean shouldShowName(SprauteNpcEntity entity) {
        if (org.zonarstudio.spraute_engine.client.SprauteScriptScreen.hideEntityNameTag) return false;
        return entity.isCustomNameVisible() && entity.hasCustomName();
    }

    @Override
    public boolean shouldRender(SprauteNpcEntity entity, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        String path=entity.getModel();
        if (path == null || path.isEmpty()) return super.shouldRender(entity,frustum,cameraX,cameraY,cameraZ);
        SpGeoModel model=SpModelCache.getOrLoad(path);
        float radius=GeoEntityBounds.radiusBlocks(model.baked.bounds,
                entity.getModelScaleX(),entity.getModelScaleY(),entity.getModelScaleZ());
        double dx=entity.getX()-cameraX,dy=entity.getY()-cameraY,dz=entity.getZ()-cameraZ;
        float cap=entity.getRenderDistance() > 0 ? entity.getRenderDistance()
                : org.zonarstudio.spraute_engine.config.SprauteConfig.get().geoMaxRenderDistance;
        if (!entity.isForceVisible() && cap > 0 && dx*dx+dy*dy+dz*dz > (cap+radius)*(cap+radius)) {
            GeoRenderStats.culled();
            return false;
        }
        boolean visible=frustum.isVisible(GeoEntityBounds.world(entity,model.baked.bounds,
                entity.getModelScaleX(),entity.getModelScaleY(),entity.getModelScaleZ(),
                org.zonarstudio.spraute_engine.config.SprauteConfig.get().geoCullingMargin));
        if (!visible) GeoRenderStats.culled();
        return visible;
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
            net.minecraft.world.phys.Vec3 camera=net.minecraft.client.Minecraft.getInstance()
                    .gameRenderer.getMainCamera().getPosition();
            double distance=Math.sqrt(entity.distanceToSqr(camera));
            float radius=GeoEntityBounds.radiusBlocks(model.baked.bounds,
                    entity.getModelScaleX(),entity.getModelScaleY(),entity.getModelScaleZ());
            GeoRenderPolicy.Decision policy=GeoRenderPolicy.decide(distance,radius,!entity.isLodEnabled());
            if (!entity.isForceVisible() && policy.hidden()) {
                GeoRenderStats.culled();
                super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                return;
            }
            int interval=entity.isForceVisible() ? 1 : policy.animationInterval();
            boolean sampleDue=entry.lastPoseTick==Integer.MIN_VALUE || interval<=1
                    || (entity.tickCount-entry.lastPoseTick>=interval
                    && Math.floorMod(entity.getUUID().hashCode(),interval)==Math.floorMod(entity.tickCount,interval));
            if (sampleDue) {
                long transformStart=GeoRenderStats.begin();
                boolean interpolate=entry.lastPoseTick != Integer.MIN_VALUE && interval > 1;
                if (interpolate) instance.captureInterpolationFrom();
                instance.resetAnims();
                applyOverlayAnimations(entity, partialTick, instance, entry);
                applyHeadBoneLook(entity, partialTick, instance, entry);
                applyPlayerSkinHeadFacing(entity, instance);
                instance.computeTransforms();
                if (interpolate) {
                    instance.captureInterpolationTarget();
                } else if (interval > 1) {
                    instance.captureInterpolationFrom();
                    instance.captureInterpolationTarget();
                }
                entry.lastPoseTick=entity.tickCount;
                entry.poseInterval=interval;
                GeoRenderStats.transformed(transformStart);
            }
            if (entry.poseInterval > 1) {
                instance.applyInterpolation((entity.tickCount+partialTick-entry.lastPoseTick)/entry.poseInterval);
            }

            poseStack.pushPose();

            boolean hasCustomDeathAnim = false;
            if (!entity.isAlive() || entity.getHealth() <= 0) {
                String deathAnimName = entity.getDeathAnim();
                if (deathAnimName != null && !deathAnimName.isEmpty()) {
                    org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.AnimationSet set = SpAnimationCache.getOrLoad(entity.getAnimation());
                    if (set != null && set.get(deathAnimName) != null) {
                        hasCustomDeathAnim = true;
                    }
                }
            }

            // Rotate model to face entity's body yaw direction.
            // The X-negate (Bedrock→MC mirror) is handled per-vertex inside SpGeoRenderer,
            // so PoseStack stays clean — no scale(-1,1,1) that would break normals/winding.
            float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
            SprauteRenderCompat.rotateY(poseStack, 180.0F - bodyYaw);
            SprauteRenderCompat.rotateX(poseStack, entity.getModelRotationX());
            SprauteRenderCompat.rotateY(poseStack, entity.getModelRotationY());
            SprauteRenderCompat.rotateZ(poseStack, entity.getModelRotationZ());
            poseStack.scale(entity.getModelScaleX(), entity.getModelScaleY(), entity.getModelScaleZ());

            if (entity.deathTime > 0 && !hasCustomDeathAnim) {
                float f = ((float)entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
                f = Mth.sqrt(f);
                if (f > 1.0F) f = 1.0F;
                SprauteRenderCompat.rotateZ(poseStack, f * 90.0F);
            }

            ResourceLocation textureLoc = getTextureLocation(entity);

            int overlay = OverlayTexture.pack(
                OverlayTexture.u(0f),
                OverlayTexture.v(entity.hurtTime > 0 || !entity.isAlive())
            );

            float opacity = entity.getOpacity();
            long drawStart=GeoRenderStats.begin();
            boolean gpu=false;
            boolean translucent = entity.usesTranslucentGeo();
            String backend=org.zonarstudio.spraute_engine.config.SprauteConfig.get().geoRenderBackend;
            if (opacity > .001f && !"compat".equals(backend) && !entity.isCurrentlyGlowing()) {
                gpu=SpGeoGpuRenderer.render(instance,poseStack,textureLoc,packedLight,overlay,
                        1f,1f,1f,opacity,translucent,entity.uiRenderBones,
                        policy.lod());
            }
            if (opacity > .001f && !gpu) {
                VertexConsumer consumer = bufferSource.getBuffer(
                        SprauteRenderCompat.geoEntity(textureLoc, translucent));
                SpGeoRenderer.render(instance, poseStack, consumer, packedLight, overlay,
                                     1f, 1f, 1f, opacity, entity.uiRenderBones, policy.lod());
            }
            GeoRenderStats.drawn(drawStart);

            SprauteNpcItemLayer.render(instance, entity, poseStack, bufferSource, packedLight);

            poseStack.popPose();

            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            GeoRenderStats.rendered(model.baked.vertexCount,model.baked.faceCount,
                    model.baked.pages.size(),!gpu,policy.lod());

            WARNED_ENTITIES.remove(entity.getUUID());
        } catch (Exception e) {
            try { poseStack.popPose(); } catch (Exception ignored) {}

            if (WARNED_ENTITIES.add(entity.getUUID())) {
                LOGGER.error("[Spraute Engine] Failed to render NPC '{}': {}",
                        entity.getCustomName() != null ? entity.getCustomName().getString() : entity.getStringUUID(),
                        e.getMessage(), e);
                String modelPath = entity.getModel();
                if (modelPath != null && !modelPath.isEmpty()) {
                    SprauteResourcePath.Result parsed = SprauteResourcePath.parse(modelPath, SprauteResourcePath.Kind.MODEL);
                    if (!parsed.ok()) {
                        SprauteResourcePath.warnClientPlayerOnce("render:" + entity.getUUID(), parsed);
                    }
                }
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

        boolean unlimited = entity.isHeadLookUnlimited();

        if (entity.tickCount != entry.lastHeadSmoothTick) {
            entry.lastHeadSmoothTick = entity.tickCount;

            entry.smoothHeadLocalYawO = entry.smoothHeadLocalYaw;
            entry.smoothHeadPitchO = entry.smoothHeadPitch;

            float targetLocal = 0f;
            if (entity.isHeadLookActive()) {
                targetLocal = Mth.degreesDifference(entity.yBodyRot, lookAzimuthWorld);
                if (!unlimited) {
                    targetLocal = Mth.clamp(targetLocal, -MAX_HEAD_YAW, MAX_HEAD_YAW);
                }
            }

            entry.smoothHeadLocalYaw = Mth.approachDegrees(entry.smoothHeadLocalYaw, targetLocal, HEAD_LOCAL_SMOOTH_SPEED);
            if (!unlimited) {
                entry.smoothHeadLocalYaw = Mth.clamp(entry.smoothHeadLocalYaw, -MAX_HEAD_YAW, MAX_HEAD_YAW);
            }
            entry.smoothHeadPitch = Mth.approach(entry.smoothHeadPitch, targetPitch, HEAD_LOOK_PITCH_SPEED);
            entry.smoothHeadPitch = Mth.clamp(entry.smoothHeadPitch, -MAX_HEAD_PITCH_UP, MAX_HEAD_PITCH_DOWN);
        }

        float renderLocalYaw = unlimited
                ? Mth.rotLerp(partialTick, entry.smoothHeadLocalYawO, entry.smoothHeadLocalYaw)
                : Mth.clamp(
                Mth.rotLerp(partialTick, entry.smoothHeadLocalYawO, entry.smoothHeadLocalYaw),
                -MAX_HEAD_YAW, MAX_HEAD_YAW);
        float renderPitch = Mth.clamp(
                Mth.lerp(partialTick, entry.smoothHeadPitchO, entry.smoothHeadPitch),
                -MAX_HEAD_PITCH_UP, MAX_HEAD_PITCH_DOWN);

        if (Math.abs(renderLocalYaw) > 0.01f || Math.abs(renderPitch) > 0.01f) {
            // SpModelInstance applies bone Y as +(rest+anim); add so look yaw stays correct.
            rot.y += renderLocalYaw;
            rot.x += renderPitch;
        }
    }

    private static final String AUTO_IDLE_KEY = "__auto_idle__";
    private static final String AUTO_WALK_KEY = "__auto_walk__";

    /** If preferred clip is missing (e.g. default fly_idle), fall back to ground idle/walk. */
    private static String resolveAutoClipName(SprauteNpcEntity entity, String preferred, String fallback) {
        SpAnimationParser.AnimationSet set = SpAnimationCache.getOrLoad(entity.getAnimation());
        if (set == null) return preferred;
        if (usableClip(set, preferred)) return preferred;
        if (usableClip(set, fallback)) return fallback;
        String playable = set.firstPlayableName(preferred);
        return playable == null || playable.isEmpty() ? preferred : playable;
    }

    private static boolean usableClip(SpAnimationParser.AnimationSet set, String name) {
        return name != null && !name.isEmpty()
                && !SpAnimationParser.isMetaClipName(name)
                && set.get(name) != null;
    }

    private void applyOverlayAnimations(SprauteNpcEntity entity, float partialTick, SpModelInstance instance, InstanceEntry entry) {
        syncOverlayState(entity, partialTick, entry);
        syncAutoAnims(entity, partialTick, entry);

        if (entry.layers.isEmpty()) return;

        SpAnimationParser.AnimationSet set = SpAnimationCache.getOrLoad(entity.getAnimation());
        float now = entity.tickCount + partialTick;
        float weight = entity.getOverlayWeight();

        // Two passes so auto base (idle/walk) always blends under manual overlays,
        // regardless of LinkedHashMap insertion order.
        applyLayerPass(entry, set, instance, now, weight, true);
        applyLayerPass(entry, set, instance, now, weight, false);
    }

    private void applyLayerPass(InstanceEntry entry, SpAnimationParser.AnimationSet set,
                                SpModelInstance instance, float now, float weight, boolean autoPass) {
        Iterator<Map.Entry<String, OverlayLayerState>> it = entry.layers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, OverlayLayerState> mapEntry = it.next();
            boolean auto = isAutoLayerKey(mapEntry.getKey());
            if (auto != autoPass) continue;

            OverlayLayerState layer = mapEntry.getValue();
            SpAnimationParser.AnimationClip clip = set.get(layer.clipName);
            if (clip == null) continue;

            float elapsedSec = org.zonarstudio.spraute_engine.client.SprauteScriptScreen.disableEntityAnimations
                    ? 0f : Math.max(0f, (now - layer.startTick) / 20f);
            if (layer.mode == SprauteNpcEntity.OVERLAY_ONCE) {
                float lengthSec = clip.getLengthSec();
                if (lengthSec > 0f && elapsedSec >= lengthSec && layer.fadeOutStartTick < 0f) {
                    layer.fadeOutStartTick = now;
                }
            }

            float layerBlend = getLayerBlend(now, layer);
            if (layerBlend <= 0f && layer.fadeOutStartTick >= 0f) {
                if (auto) continue;
                it.remove();
                continue;
            }

            float effectiveWeight = auto ? layerBlend : weight * layerBlend;
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
        boolean flying = entity.isFlying();
        boolean swimming = entity.isSwimmingScript() || entity.isInWater();
        boolean dead = !entity.isAlive() || entity.getHealth() <= 0;
        
        String idleAnimName = flying ? entity.getFlyIdleAnim() : (swimming ? entity.getSwimIdleAnim() : entity.getIdleAnim());
        String walkAnimName = flying ? entity.getFlyWalkAnim() : (swimming ? entity.getSwimWalkAnim() : entity.getWalkAnim());
        idleAnimName = resolveAutoClipName(entity, idleAnimName, entity.getIdleAnim());
        walkAnimName = resolveAutoClipName(entity, walkAnimName, entity.getWalkAnim());
        String deathAnimName = entity.getDeathAnim();

        float now = entity.tickCount + partialTick;

        if (dead) {
            if (deathAnimName != null && !deathAnimName.isEmpty()) {
                OverlayLayerState deathLayer = entry.layers.get("__auto_death__");
                if (deathLayer == null) {
                    deathLayer = new OverlayLayerState();
                    deathLayer.clipName = deathAnimName;
                    deathLayer.mode = SprauteNpcEntity.OVERLAY_FREEZE;
                    deathLayer.startTick = now;
                    deathLayer.fadeInStartTick = now;
                    deathLayer.fadeOutStartTick = -1f;
                    deathLayer.additive = false;
                    entry.layers.put("__auto_death__", deathLayer);
                } else if (deathLayer.fadeOutStartTick >= 0f) {
                    deathLayer.fadeOutStartTick = -1f;
                }
            }
            
            // Fade out idle and walk
            if (entry.layers.containsKey(AUTO_IDLE_KEY)) {
                OverlayLayerState idleLayer = entry.layers.get(AUTO_IDLE_KEY);
                if (idleLayer.fadeOutStartTick < 0f) idleLayer.fadeOutStartTick = now;
            }
            if (entry.layers.containsKey(AUTO_WALK_KEY)) {
                OverlayLayerState walkLayer = entry.layers.get(AUTO_WALK_KEY);
                if (walkLayer.fadeOutStartTick < 0f) walkLayer.fadeOutStartTick = now;
            }
            return;
        } else {
            // If respawned, fade out death anim
            if (entry.layers.containsKey("__auto_death__")) {
                OverlayLayerState deathLayer = entry.layers.get("__auto_death__");
                if (deathLayer.fadeOutStartTick < 0f) deathLayer.fadeOutStartTick = now;
            }
        }

        boolean hasIdle = idleAnimName != null && !idleAnimName.isEmpty();
        boolean hasWalk = walkAnimName != null && !walkAnimName.isEmpty();
        if (!hasIdle && !hasWalk) return;

        // When idle and walk use the SAME clip (common for flying NPCs like Raya), play a single
        // continuous loop. Otherwise rapid moving<->idle toggling restarts the clip every few ticks
        // and the animation appears frozen at frame 0.
        if (hasIdle && hasWalk && idleAnimName.equals(walkAnimName)) {
            // Fade out any walk layer so only the idle layer drives the continuous loop.
            OverlayLayerState walkLayer = entry.layers.get(AUTO_WALK_KEY);
            if (walkLayer != null && walkLayer.fadeOutStartTick < 0f) {
                walkLayer.fadeOutStartTick = now;
            }
            OverlayLayerState idleLayer = entry.layers.get(AUTO_IDLE_KEY);
            if (idleLayer == null) {
                idleLayer = new OverlayLayerState();
                idleLayer.clipName = idleAnimName;
                idleLayer.mode = SprauteNpcEntity.OVERLAY_LOOP;
                idleLayer.startTick = now;
                idleLayer.fadeInStartTick = now;
                idleLayer.fadeOutStartTick = -1f;
                idleLayer.additive = false;
                entry.layers.put(AUTO_IDLE_KEY, idleLayer);
            } else if (idleLayer.fadeOutStartTick >= 0f) {
                resumeAutoLayer(idleLayer, idleAnimName, now);
            }
            return;
        }

        boolean moving = entity.isMovingSynced();

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
                    idleLayer.additive = false;
                    entry.layers.put(AUTO_IDLE_KEY, idleLayer);
                } else if (idleLayer.fadeOutStartTick >= 0f) {
                    // Resume same timeline — ease back under remaining overlays.
                    resumeAutoLayer(idleLayer, idleAnimName, now);
                } else if (!idleAnimName.equals(idleLayer.clipName)) {
                    idleLayer.clipName = idleAnimName;
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
                    walkLayer.additive = false;
                    entry.layers.put(AUTO_WALK_KEY, walkLayer);
                } else if (walkLayer.fadeOutStartTick >= 0f) {
                    resumeAutoLayer(walkLayer, walkAnimName, now);
                } else if (!walkAnimName.equals(walkLayer.clipName)) {
                    walkLayer.clipName = walkAnimName;
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

    private static boolean isAutoLayerKey(String key) {
        return key != null && key.startsWith("__auto_");
    }

    /** Cancel fade-out without resetting clip time; continue fade-in from current weight. */
    private void resumeAutoLayer(OverlayLayerState layer, String animName, float now) {
        float blend = getLayerBlend(now, layer);
        layer.clipName = animName;
        layer.mode = SprauteNpcEntity.OVERLAY_LOOP;
        layer.fadeOutStartTick = -1f;
        layer.additive = false;
        // Keep startTick so the clip phase continues (no body pop to frame 0).
        if (blend < 0.999f) {
            layer.fadeInStartTick = now - blend * BLEND_TICKS;
        } else {
            layer.fadeInStartTick = now - BLEND_TICKS;
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
            // Never fade auto idle/walk here — they keep playing and remaining
            // overlays blend out toward their continuing pose.
            if (clipName == null || clipName.isEmpty()) {
                for (Map.Entry<String, OverlayLayerState> e : entry.layers.entrySet()) {
                    if (isAutoLayerKey(e.getKey())) continue;
                    if (e.getValue().fadeOutStartTick < 0f) e.getValue().fadeOutStartTick = now;
                }
            } else {
                String stopKey = clipName.toLowerCase(Locale.ROOT);
                if (!isAutoLayerKey(stopKey)) {
                    OverlayLayerState layer = entry.layers.get(stopKey);
                    if (layer != null && layer.fadeOutStartTick < 0f) layer.fadeOutStartTick = now;
                }
            }
            return;
        }

        if (clipName == null || clipName.isEmpty()) return;
        String key = clipName.toLowerCase(Locale.ROOT);

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

    public static void clearInstances() {
        INSTANCES.clear();
    }

    public static void removeInstance(UUID entityId) {
        if (entityId != null) INSTANCES.remove(entityId);
    }

    private InstanceEntry getOrCreateInstance(UUID entityId, String modelPath, SpGeoModel model) {
        InstanceEntry entry = INSTANCES.get(entityId);
        if (entry != null && entry.modelPath.equals(modelPath)
                && entry.instance.getModel().boneMap.size() == model.boneMap.size()) {
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

        return modelPointToWorld(entity, matrix.m[12], matrix.m[13], matrix.m[14], partialTick);
    }

    /** World position of a Bedrock locator (e.g. {@code lead_hold}) after bone animation. */
    public SpVec3 getLocatorWorldPosition(SprauteNpcEntity entity, String locatorName, float partialTick) {
        InstanceEntry entry = INSTANCES.get(entity.getUUID());
        if (entry == null || entry.instance == null) return null;
        SpVec3 model = entry.instance.getLocatorModelPosition(locatorName);
        if (model == null) return null;
        // Same conversion path as getBoneWorldPosition (bone matrix translation space).
        return modelPointToWorld(entity, model.x, model.y, model.z, partialTick);
    }

    private static SpVec3 modelPointToWorld(SprauteNpcEntity entity,
                                            float mxPx, float myPx, float mzPx, float partialTick) {
        float mx = mxPx / 16.0f;
        float my = myPx / 16.0f;
        float mz = mzPx / 16.0f;

        float bodyYaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float rad = (float) Math.toRadians(180.0 - bodyYaw);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        float rotX = mx * cos + mz * sin;
        float rotZ = -mx * sin + mz * cos;

        double x = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX()) + rotX;
        double y = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + my;
        double z = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ()) + rotZ;

        return new SpVec3((float) x, (float) y, (float) z);
    }

    private static final class InstanceEntry {
        final String modelPath;
        final SpModelInstance instance;
        final Map<String, OverlayLayerState> layers = new LinkedHashMap<>();
        int lastOverlayCommandId = Integer.MIN_VALUE;
        int lastHeadLookTargetGen = Integer.MIN_VALUE;
        int lastHeadSmoothTick = Integer.MIN_VALUE;
        int lastPoseTick = Integer.MIN_VALUE;
        int poseInterval = 1;

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
