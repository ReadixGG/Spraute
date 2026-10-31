package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseMixin {
    @Inject(method = "tameWithName", at = @At("HEAD"))
    private void spraute$tamed(Player player, CallbackInfoReturnable<Boolean> cir) {
        Hooks.ANIMAL_TAME.fire(new Hooks.AnimalTame((AbstractHorse) (Object) this, player));
    }
}
