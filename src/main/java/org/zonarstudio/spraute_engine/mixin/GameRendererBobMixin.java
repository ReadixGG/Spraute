package org.zonarstudio.spraute_engine.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zonarstudio.spraute_engine.client.CameraEffectsClient;

@Mixin(GameRenderer.class)
public abstract class GameRendererBobMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void spraute$disableBobView(PoseStack pose, float partialTick, CallbackInfo ci) {
        if (CameraEffectsClient.suppressVanillaBob()) ci.cancel();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void spraute$disableBobHurt(PoseStack pose, float partialTick, CallbackInfo ci) {
        if (CameraEffectsClient.suppressVanillaHurt()) ci.cancel();
    }
}
