package org.zonarstudio.spraute_engine.particle;

import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.registry.CustomParticleRegistry;
import org.zonarstudio.spraute_engine.scene.SceneNode;

/** Resolution and migration entry point used by runtime, editor and scripts. */
public final class ParticleEffects {
    private ParticleEffects() {}

    public static boolean isCustom(String id) {
        if (id == null || id.isBlank()) return false;
        if (id.startsWith(Spraute_engine.MODID + ":")) return true;
        if (!id.contains(":")) return CustomParticleRegistry.PARTICLES.containsKey(id);
        return false;
    }

    public static ParticleEffectDef resolve(SceneNode node) {
        if (node == null) return new ParticleEffectDef();
        if (node.particleEffect != null) {
            node.particleEffect.normalize();
            return node.particleEffect.copy();
        }
        String id = node.particleType == null ? "" : node.particleType;
        String bare = id.startsWith(Spraute_engine.MODID + ":")
                ? id.substring(Spraute_engine.MODID.length() + 1) : id;
        CustomParticleRegistry.CustomParticleDef registered = CustomParticleRegistry.PARTICLES.get(bare);
        String texture = registered == null ? "" : registered.texture;
        ParticleEffectDef effect = registered == null || registered.effect == null
                ? ParticleEffectDef.fromLegacy(node, texture)
                : registered.effect.copy();
        applyLegacyInstanceOverrides(effect, node);
        effect.normalize();
        return effect;
    }

    /** Old flat fields remain instance overrides, preserving old scene behavior. */
    public static void applyLegacyInstanceOverrides(ParticleEffectDef effect, SceneNode node) {
        if (effect == null || node == null) return;
        ParticleEffectDef legacy = ParticleEffectDef.fromLegacy(node, effect.renderer.texture);
        effect.emission.loop = legacy.emission.loop;
        effect.emission.bursts = legacy.emission.bursts;
        effect.shape.type = legacy.shape.type;
        effect.shape.radius = legacy.shape.radius;
        effect.shape.height = legacy.shape.height;
        effect.shape.lineX = legacy.shape.lineX;
        effect.shape.lineY = legacy.shape.lineY;
        effect.shape.lineZ = legacy.shape.lineZ;
        effect.initial.speedMin = legacy.initial.speedMin;
        effect.initial.speedMax = legacy.initial.speedMax;
        effect.initial.directionX = legacy.initial.directionX;
        effect.initial.directionY = legacy.initial.directionY;
        effect.initial.directionZ = legacy.initial.directionZ;
        effect.initial.cone = legacy.initial.cone;
        if (node.particleLifetime > 0) {
            effect.initial.lifetimeMin = legacy.initial.lifetimeMin;
            effect.initial.lifetimeMax = legacy.initial.lifetimeMax;
        }
        effect.motion.gravityY = legacy.motion.gravityY;
        effect.collision.enabled = legacy.collision.enabled;
    }
}
