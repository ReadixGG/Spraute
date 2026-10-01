package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zonarstudio.spraute_engine.client.CameraScreenPick;

@Mixin(GameRenderer.class)
public class GameRendererPickMixin {
    @Shadow @Final
    Minecraft minecraft;

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void spraute$screenCenterPick(float partialTick, CallbackInfo ci) {
        if (CameraScreenPick.apply(minecraft, partialTick)) {
            ci.cancel();
        }
    }
}
