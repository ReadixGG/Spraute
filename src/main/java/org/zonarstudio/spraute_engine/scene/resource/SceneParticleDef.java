package org.zonarstudio.spraute_engine.scene.resource;

import org.zonarstudio.spraute_engine.particle.ParticleEffectDef;

/** Scene payload mirroring {@code create particle id { texture = "…" }}. */
public final class SceneParticleDef {
    /** Registry id without namespace → {@code spraute_engine:<id>}. */
    public String id = "";
    /** Texture path, e.g. {@code textures/particle/spark.png}. */
    public String texture = "";
    /** Canonical effect. Null in old files and populated by {@link #migrate()}. */
    public ParticleEffectDef effect;

    public ParticleEffectDef migrate() {
        if (effect == null) effect = ParticleEffectDef.fromLegacy(null, texture);
        effect.normalize();
        if ((effect.renderer.texture == null || effect.renderer.texture.isBlank()) && texture != null) {
            effect.renderer.texture = texture;
        }
        texture = effect.renderer.texture;
        return effect;
    }

    public SceneParticleDef copy() {
        SceneParticleDef c = new SceneParticleDef();
        c.id = id;
        c.texture = texture;
        c.effect = effect == null ? null : effect.copy();
        return c;
    }
}
