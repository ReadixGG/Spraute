package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;

import java.util.OptionalInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    /** A player's own version of this method: LivingEntity's is not called. */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void spraute$die(DamageSource source, CallbackInfo ci) {
        Hooks.LivingDeath e = new Hooks.LivingDeath((ServerPlayer) (Object) this, source);
        Hooks.LIVING_DEATH.fire(e);
        if (e.isCanceled()) ci.cancel();
    }

    @Inject(method = "openMenu", at = @At("RETURN"))
    private void spraute$opened(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (cir.getReturnValue().isPresent()) Hooks.CONTAINER_OPEN.fire(new Hooks.ContainerOpen(self, self.containerMenu));
    }

    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void spraute$closed(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Hooks.CONTAINER_CLOSE.fire(new Hooks.ContainerClose(self, self.containerMenu));
    }
}
