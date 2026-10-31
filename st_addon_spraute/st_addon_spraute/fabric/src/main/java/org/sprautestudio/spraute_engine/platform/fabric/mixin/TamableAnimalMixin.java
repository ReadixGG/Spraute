package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin {
    @Inject(method = "tame", at = @At("HEAD"))
    private void spraute$tamed(Player player, CallbackInfo ci) {
        Hooks.ANIMAL_TAME.fire(new Hooks.AnimalTame((TamableAnimal) (Object) this, player));
    }
}
