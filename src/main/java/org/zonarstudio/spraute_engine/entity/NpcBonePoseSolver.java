package org.zonarstudio.spraute_engine.entity;

import net.minecraft.server.packs.resources.ResourceManager;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.core.model.SpResourceCache;
import org.zonarstudio.spraute_engine.core.parser.SpAnimationParser;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side bone pose for hitbox placement. Applies idle/walk clips (same names as client auto-anims).
 */
public final class NpcBonePoseSolver {
    private static final Map<UUID, SolverEntry> ENTRIES = new ConcurrentHashMap<>();

    private NpcBonePoseSolver() {}

    public static SpModelInstance solve(SprauteNpcEntity entity, ResourceManager rm) {
        if (entity == null || rm == null) return null;
        String modelPath = entity.getModel();
        SpGeoModel model = SpResourceCache.getModel(rm, modelPath);
        if (model.boneMap.isEmpty()) return null;

        SolverEntry entry = ENTRIES.get(entity.getUUID());
        if (entry == null || !entry.modelPath.equals(modelPath)) {
            entry = new SolverEntry(modelPath, new SpModelInstance(model));
            ENTRIES.put(entity.getUUID(), entry);
        }

        SpModelInstance instance = entry.instance;
        instance.resetAnims();

        SpAnimationParser.AnimationSet set = SpResourceCache.getAnimation(rm, entity.getAnimation());
        float elapsedSec = entity.tickCount / 20f;

        String clipName = pickBaseClip(entity);
        SpAnimationParser.AnimationClip clip = set.get(clipName);
        if (clip != null) {
            clip.apply(instance, elapsedSec, 1f, true, false);
        }

        applyHeadLook(entity, instance);
        instance.computeTransforms();
        return instance;
    }

    public static void remove(UUID entityId) {
        ENTRIES.remove(entityId);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    private static String pickBaseClip(SprauteNpcEntity entity) {
        if (!entity.isAlive() || entity.getHealth() <= 0) {
            String death = entity.getDeathAnim();
            if (death != null && !death.isEmpty()) return death;
        }
        if (entity.isFlying()) {
            if (entity.isMoving()) {
                String walk = entity.getFlyWalkAnim();
                if (walk != null && !walk.isEmpty()) return walk;
            }
            String idle = entity.getFlyIdleAnim();
            if (idle != null && !idle.isEmpty()) return idle;
        }
        if (entity.isSwimmingScript() || entity.isInWater()) {
            if (entity.isMoving()) {
                String walk = entity.getSwimWalkAnim();
                if (walk != null && !walk.isEmpty()) return walk;
            }
            String idle = entity.getSwimIdleAnim();
            if (idle != null && !idle.isEmpty()) return idle;
        }
        if (entity.isMoving()) {
            String walk = entity.getWalkAnim();
            if (walk != null && !walk.isEmpty()) return walk;
        }
        String idle = entity.getIdleAnim();
        return idle != null ? idle : "idle";
    }

    private static void applyHeadLook(SprauteNpcEntity entity, SpModelInstance instance) {
        if (!entity.isHeadLookActive()) return;
        String headBone = entity.getHeadBoneName();
        if (headBone == null || headBone.isEmpty()) return;

        org.zonarstudio.spraute_engine.core.math.SpVec3 rot = instance.boneAnimRotation.get(headBone);
        if (rot == null) return;

        float bodyYaw = entity.yBodyRot;
        float headYaw = entity.getHeadLookYaw();
        float headPitch = entity.getHeadLookPitch();
        float localYaw = net.minecraft.util.Mth.wrapDegrees(headYaw - bodyYaw);
        localYaw = net.minecraft.util.Mth.clamp(localYaw, -SprauteNpcEntity.MAX_HEAD_YAW, SprauteNpcEntity.MAX_HEAD_YAW);
        float pitch = net.minecraft.util.Mth.clamp(headPitch,
                -SprauteNpcEntity.MAX_HEAD_PITCH_UP, SprauteNpcEntity.MAX_HEAD_PITCH_DOWN);

        rot.y -= localYaw;
        rot.x += pitch;
    }

    private static final class SolverEntry {
        final String modelPath;
        final SpModelInstance instance;

        SolverEntry(String modelPath, SpModelInstance instance) {
            this.modelPath = modelPath;
            this.instance = instance;
        }
    }
}
