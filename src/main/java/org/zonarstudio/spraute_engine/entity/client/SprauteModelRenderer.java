package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.compat.SprauteRenderCompat;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.core.parser.SpAnimationParser;
import org.zonarstudio.spraute_engine.entity.SprauteModelEntity;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client renderer for {@link SprauteModelEntity}. */
public class SprauteModelRenderer extends EntityRenderer<SprauteModelEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> WARNED_ENTITIES = new HashSet<>();
    private static final Map<UUID, InstanceEntry> INSTANCES = new ConcurrentHashMap<>();
    private static final float BLEND_SECONDS = 0.2f;
    private static final float BLEND_TICKS = BLEND_SECONDS * 20f;
    private static final String AUTO_IDLE_KEY = "__auto_idle__";
    private static final ResourceLocation FALLBACK_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/unknown_pack.png");

    public SprauteModelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.5f;
    }

    @Override
    public ResourceLocation getTextureLocation(SprauteModelEntity entity) {
        String tex = entity.getTexture();
        if (tex == null || tex.isEmpty()) return FALLBACK_TEXTURE;
        SprauteResourcePath.Result parsed = SprauteResourcePath.parse(tex, SprauteResourcePath.Kind.TEXTURE);
        if (!parsed.ok()) {
            SprauteResourcePath.warnClientPlayerOnce("tex:" + entity.getUUID(), parsed);
            return FALLBACK_TEXTURE;
        }
        return parsed.location();
    }

    @Override
    public boolean shouldShowName(SprauteModelEntity entity) {
        if (org.zonarstudio.spraute_engine.client.SprauteScriptScreen.hideEntityNameTag) return false;
        return entity.isCustomNameVisible() && entity.hasCustomName();
    }

    @Override
    public boolean shouldRender(SprauteModelEntity entity, Frustum frustum,
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
    public void render(SprauteModelEntity entity, float entityYaw, float partialTick,
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
            if (entity.getOpacity() <= .001f) {
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
                applyAnimations(entity, partialTick, instance, entry);
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
            float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            SprauteRenderCompat.rotateY(poseStack, 180.0F - yaw);
            SprauteRenderCompat.rotateX(poseStack, entity.getModelRotationX());
            SprauteRenderCompat.rotateY(poseStack, entity.getModelRotationY());
            SprauteRenderCompat.rotateZ(poseStack, entity.getModelRotationZ());
            poseStack.scale(entity.getModelScaleX(), entity.getModelScaleY(), entity.getModelScaleZ());

            ResourceLocation textureLoc = getTextureLocation(entity);
            int overlay = OverlayTexture.pack(OverlayTexture.u(0f), OverlayTexture.v(false));
            long drawStart=GeoRenderStats.begin();
            boolean gpu=false;
            boolean translucent = entity.usesTranslucentGeo();
            String backend=org.zonarstudio.spraute_engine.config.SprauteConfig.get().geoRenderBackend;
            if (entity.getOpacity() > .001f && !"compat".equals(backend) && !entity.isCurrentlyGlowing()) {
                gpu=SpGeoGpuRenderer.render(instance,poseStack,textureLoc,packedLight,overlay,
                        1f,1f,1f,entity.getOpacity(),translucent,null,
                        policy.lod());
            }
            if (entity.getOpacity() > .001f && !gpu) {
                VertexConsumer consumer = bufferSource.getBuffer(
                        SprauteRenderCompat.geoEntity(textureLoc, translucent));
                SpGeoRenderer.render(instance, poseStack, consumer, packedLight, overlay,
                        1f, 1f, 1f, entity.getOpacity(), null, policy.lod());
            }
            GeoRenderStats.drawn(drawStart);
            poseStack.popPose();

            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            GeoRenderStats.rendered(model.baked.vertexCount,model.baked.faceCount,
                    model.baked.pages.size(),!gpu,policy.lod());
            WARNED_ENTITIES.remove(entity.getUUID());
        } catch (Exception e) {
            try { poseStack.popPose(); } catch (Exception ignored) {}
            if (WARNED_ENTITIES.add(entity.getUUID())) {
                LOGGER.error("[Spraute Engine] Failed to render model entity '{}': {}",
                        entity.getCustomName() != null ? entity.getCustomName().getString() : entity.getStringUUID(),
                        e.getMessage(), e);
            }
        }
    }

    private void applyAnimations(SprauteModelEntity entity, float partialTick,
                                 SpModelInstance instance, InstanceEntry entry) {
        syncOverlayState(entity, partialTick, entry);
        syncBaseIdle(entity, partialTick, entry);
        if (entry.layers.isEmpty()) return;

        SpAnimationParser.AnimationSet set = SpAnimationCache.getOrLoad(entity.getAnimation());
        if (set == null) return;

        float now = entity.tickCount + partialTick;
        float weight = entity.getOverlayWeight();
        applyLayerPass(entry, set, instance, now, weight, true);
        applyLayerPass(entry, set, instance, now, weight, false);
    }

    private void applyLayerPass(InstanceEntry entry, SpAnimationParser.AnimationSet set,
                                SpModelInstance instance, float now, float weight, boolean autoPass) {
        Iterator<Map.Entry<String, OverlayLayerState>> it = entry.layers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, OverlayLayerState> mapEntry = it.next();
            boolean auto = AUTO_IDLE_KEY.equals(mapEntry.getKey());
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

    private void syncBaseIdle(SprauteModelEntity entity, float partialTick, InstanceEntry entry) {
        String animFile = entity.getAnimation();
        if (animFile == null || animFile.isEmpty()) return;
        String idleName = entity.getIdleAnim();
        if (idleName == null || idleName.isEmpty()) return;

        SpAnimationParser.AnimationSet set = SpAnimationCache.getOrLoad(animFile);
        if (set == null) return;
        if (set.get(idleName) == null) {
            idleName = set.firstPlayableName(idleName);
            if (idleName == null || idleName.isEmpty()) return;
        }

        float now = entity.tickCount + partialTick;
        OverlayLayerState idleLayer = entry.layers.get(AUTO_IDLE_KEY);
        if (idleLayer == null) {
            idleLayer = new OverlayLayerState();
            idleLayer.clipName = idleName;
            idleLayer.mode = SprauteNpcEntity.OVERLAY_LOOP;
            idleLayer.startTick = now;
            idleLayer.fadeInStartTick = now;
            idleLayer.fadeOutStartTick = -1f;
            // Absolute base under overlays (same as NPC idle).
            idleLayer.additive = false;
            entry.layers.put(AUTO_IDLE_KEY, idleLayer);
        } else if (idleLayer.fadeOutStartTick >= 0f) {
            resumeAutoLayer(idleLayer, idleName, now);
        } else if (!idleName.equals(idleLayer.clipName)) {
            idleLayer.clipName = idleName;
            idleLayer.startTick = now;
            idleLayer.fadeInStartTick = now;
            idleLayer.fadeOutStartTick = -1f;
            idleLayer.additive = false;
        }
    }

    private void syncOverlayState(SprauteModelEntity entity, float partialTick, InstanceEntry entry) {
        int commandId = entity.getOverlayCommandId();
        if (entry.lastOverlayCommandId == commandId) return;
        entry.lastOverlayCommandId = commandId;
        float now = entity.tickCount + partialTick;

        String clipName = entity.getOverlayAnim();
        byte mode = entity.getOverlayMode();
        if (mode == SprauteNpcEntity.OVERLAY_NONE) {
            if (clipName == null || clipName.isEmpty()) {
                for (Map.Entry<String, OverlayLayerState> e : entry.layers.entrySet()) {
                    // Fade manual overlays only — idle keeps playing underneath.
                    if (AUTO_IDLE_KEY.equals(e.getKey())) continue;
                    if (e.getValue().fadeOutStartTick < 0f) e.getValue().fadeOutStartTick = now;
                }
            } else {
                String stopKey = clipName.toLowerCase(Locale.ROOT);
                if (!AUTO_IDLE_KEY.equals(stopKey)) {
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

    private void resumeAutoLayer(OverlayLayerState layer, String animName, float now) {
        float blend = getLayerBlend(now, layer);
        layer.clipName = animName;
        layer.mode = SprauteNpcEntity.OVERLAY_LOOP;
        layer.fadeOutStartTick = -1f;
        layer.additive = false;
        if (blend < 0.999f) {
            layer.fadeInStartTick = now - blend * BLEND_TICKS;
        } else {
            layer.fadeInStartTick = now - BLEND_TICKS;
        }
    }

    private float getLayerBlend(float nowTick, OverlayLayerState layer) {
        float inAlpha = Mth.clamp((nowTick - layer.fadeInStartTick) / BLEND_TICKS, 0f, 1f);
        if (layer.fadeOutStartTick < 0f) return inAlpha;
        float outAlpha = 1f - Mth.clamp((nowTick - layer.fadeOutStartTick) / BLEND_TICKS, 0f, 1f);
        return inAlpha * outAlpha;
    }

    public static void clearInstances() {
        INSTANCES.clear();
    }

    public static void removeInstance(UUID entityId) {
        if (entityId != null) INSTANCES.remove(entityId);
    }

    private InstanceEntry getOrCreateInstance(UUID entityId, String modelPath, SpGeoModel model) {
        InstanceEntry entry = INSTANCES.get(entityId);
        if (entry == null || !entry.modelPath.equals(modelPath)
                || entry.instance.getModel().boneMap.size() != model.boneMap.size()) {
            entry = new InstanceEntry(modelPath, new SpModelInstance(model));
            INSTANCES.put(entityId, entry);
        }
        return entry;
    }

    private static class InstanceEntry {
        final String modelPath;
        final SpModelInstance instance;
        final Map<String, OverlayLayerState> layers = new LinkedHashMap<>();
        int lastOverlayCommandId = -1;
        int lastPoseTick = Integer.MIN_VALUE;
        int poseInterval = 1;

        InstanceEntry(String modelPath, SpModelInstance instance) {
            this.modelPath = modelPath;
            this.instance = instance;
        }
    }

    private static class OverlayLayerState {
        String clipName = "";
        byte mode = SprauteNpcEntity.OVERLAY_NONE;
        float startTick;
        float fadeInStartTick;
        float fadeOutStartTick = -1f;
        boolean additive;
    }
}
