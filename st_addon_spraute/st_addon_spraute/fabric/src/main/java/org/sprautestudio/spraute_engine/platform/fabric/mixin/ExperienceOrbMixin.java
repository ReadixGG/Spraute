package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void spraute$touch(Player player, CallbackInfo ci) {
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        if (!self.level().isClientSide && player.takeXpDelay == 0) Hooks.XP_PICKUP.fire(new Hooks.XpPickup(player, self));
    }
}
