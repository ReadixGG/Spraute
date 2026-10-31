package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(Animal.class)
public abstract class AnimalMixin {
    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("HEAD"))
    private void spraute$baby(ServerLevel level, Animal partner, AgeableMob child, CallbackInfo ci) {
        Hooks.BABY_SPAWN.fire(new Hooks.BabySpawn(child, ((Animal) (Object) this).getLoveCause()));
    }
}
