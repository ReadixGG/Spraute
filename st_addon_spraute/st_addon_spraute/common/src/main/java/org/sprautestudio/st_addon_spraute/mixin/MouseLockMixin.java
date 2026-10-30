package org.sprautestudio.st_addon_spraute.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "camera lock": the mouse does not turn the player at all. Holding the view back each tick instead let the
 * mouse turn it between ticks and jerk it back — the camera shook. SRG names: the game runs with them.
 */
@Mixin(MouseHandler.class)
public abstract class MouseLockMixin {
    @Shadow(remap = false, aliases = "f_91516_")
    private double accumulatedDX;
    @Shadow(remap = false, aliases = "f_91517_")
    private double accumulatedDY;

    /** turnPlayer */
    @Inject(method = {"m_91523_", "turnPlayer"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void spraute$lockView(CallbackInfo ci) {
        if (!org.sprautestudio.st_addon_spraute.cutscene.client.CameraDirector.locksView()) return;
        // what the mouse moved is dropped, so nothing jumps when the lock is lifted
        accumulatedDX = 0;
        accumulatedDY = 0;
        ci.cancel();
    }
}
