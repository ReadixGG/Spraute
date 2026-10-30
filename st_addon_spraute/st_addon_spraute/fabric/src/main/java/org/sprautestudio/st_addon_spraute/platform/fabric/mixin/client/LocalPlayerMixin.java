package org.sprautestudio.st_addon_spraute.platform.fabric.mixin.client;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.st_addon_spraute.platform.ClientHooks;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow
    public Input input;

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void spraute$input(CallbackInfo ci) {
        ClientHooks.MOVEMENT_INPUT.fire(new ClientHooks.MovementInput(input));
    }
}
