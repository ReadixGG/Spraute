package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zonarstudio.spraute_engine.client.PlayerControlHandler;

@Mixin(Options.class)
public abstract class OptionsCameraTypeMixin {
    @Inject(method = "setCameraType", at = @At("HEAD"), cancellable = true)
    private void spraute$lockPerspective(CameraType type, CallbackInfo ci) {
        if (!PlayerControlHandler.isPerspectiveLocked() || type == null) return;
        CameraType locked = PlayerControlHandler.lockedPerspective();
        if (locked != null && type != locked) ci.cancel();
    }
}
