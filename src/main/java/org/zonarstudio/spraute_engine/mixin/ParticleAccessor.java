package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("gravity")
    void spraute$setGravity(float gravity);

    @Accessor("lifetime")
    void spraute$setLifetime(int lifetime);

    @Accessor("lifetime")
    int spraute$getLifetime();

    @Accessor("age")
    int spraute$getAge();

    @Accessor("alpha")
    void spraute$setAlpha(float alpha);

    @Accessor("removed")
    boolean spraute$isRemoved();

    /** When true, particles die / bounce on blocks — kills burst FX near the ground. */
    @Accessor("hasPhysics")
    void spraute$setHasPhysics(boolean hasPhysics);
}
