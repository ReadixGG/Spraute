package org.zonarstudio.spraute_engine.client.editor.scene;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.mixin.TextureSheetParticleAccessor;
import org.zonarstudio.spraute_engine.registry.CustomParticleRegistry;

/**
 * Preview icons for particle types in the inspector (atlas sprite or workspace texture).
 */
public final class EditorParticleIcons {
    /** Either a particle-atlas UV region, or a standalone GL texture (custom workspace png). */
    public record Icon(int glId, float u0, float v0, float u1, float v1, boolean fullTexture) {
        public static Icon atlas(int glId, float u0, float v0, float u1, float v1) {
            return new Icon(glId, u0, v0, u1, v1, false);
        }

        public static Icon full(int glId) {
            return new Icon(glId, 0f, 0f, 1f, 1f, true);
        }
    }

    private EditorParticleIcons() {}

    /** Icon for a particle registry id, or null if unknown / no sprite. */
    public static Icon icon(String particleId) {
        if (particleId == null || particleId.isBlank()) return null;

        // Custom create-particle defs: prefer workspace texture preview.
        String bare = bareId(particleId);
        CustomParticleRegistry.CustomParticleDef def = CustomParticleRegistry.PARTICLES.get(bare);
        if (def == null && particleId.startsWith(Spraute_engine.MODID + ":")) {
            def = CustomParticleRegistry.PARTICLES.get(particleId.substring(Spraute_engine.MODID.length() + 1));
        }
        if (def != null && def.texture != null && !def.texture.isBlank()) {
            var tex = EditorTexturePreview.get(def.texture);
            if (tex != null) return Icon.full(tex.glId());
        }

        return spriteFromEngine(particleId);
    }

    private static String bareId(String particleId) {
        int colon = particleId.indexOf(':');
        return colon >= 0 ? particleId.substring(colon + 1) : particleId;
    }

    private static Icon spriteFromEngine(String particleId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.particleEngine == null) return null;
        ResourceLocation key = ResourceLocation.tryParse(particleId.trim());
        if (key == null) return null;
        ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(key);
        if (!(type instanceof ParticleOptions options)) return null;
        try {
            Particle created = mc.particleEngine.createParticle(options, 0, 0, 0, 0, 0, 0);
            if (!(created instanceof TextureSheetParticle sheet)) {
                if (created != null) created.remove();
                return null;
            }
            TextureAtlasSprite sprite = ((TextureSheetParticleAccessor) sheet).spraute$getSprite();
            created.remove();
            if (sprite == null) return null;
            int atlasId = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES).getId();
            return Icon.atlas(atlasId, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
        } catch (Exception e) {
            return null;
        }
    }
}
