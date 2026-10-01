package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.script.PlayerJumpStrengthOverrides;

@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpMixin {
    @Inject(method = "getJumpPower", at = @At("HEAD"), cancellable = true)
    private void spraute$customJumpPower(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;
        Float power = PlayerJumpStrengthOverrides.resolve(player);
        if (power == null) return;
        float factor = 1f;
        try {
            factor = SprauteEntityCompat.level(self)
                    .getBlockState(self.blockPosition())
                    .getBlock()
                    .getJumpFactor();
        } catch (RuntimeException ignored) {
        }
        cir.setReturnValue(power * factor);
    }
}
