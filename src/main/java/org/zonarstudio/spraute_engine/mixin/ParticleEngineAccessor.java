package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Bypass {@link ParticleEngine#createParticle}'s {@code ParticleStatus} throttle.
 * Scripted / scene bursts already chose an exact count — random client skips make
 * FX look "randomly missing" between runs.
 */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Invoker("makeParticle")
    <T extends ParticleOptions> Particle spraute$makeParticle(
            T options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed);
}
