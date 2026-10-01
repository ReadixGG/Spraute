package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zonarstudio.spraute_engine.client.CameraHandler;

@Mixin(Entity.class)
public class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void spraute$separateCameraTurn(double yRot, double xRot, CallbackInfo ci) {
        if (!((Object) this instanceof LocalPlayer)) return;
        if (CameraHandler.consumeTurnAsCamera(yRot, xRot)) {
            ci.cancel();
        }
    }
}
