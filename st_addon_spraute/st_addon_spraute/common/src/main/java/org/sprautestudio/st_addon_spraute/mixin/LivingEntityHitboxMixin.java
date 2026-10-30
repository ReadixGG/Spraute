package org.sprautestudio.st_addon_spraute.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.sprautestudio.st_addon_spraute.hitbox.Hitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pushing and aiming for characters with a hitbox setting (SRG names, as the game runs). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHitboxMixin {
    /** isPushable: solid, fixed and ghost bodies are not moved by others. */
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void spraute$pushable(CallbackInfoReturnable<Boolean> cir) {
        int f = Hitboxes.flags((Entity) (Object) this);
        if ((f & (Hitboxes.SOLID | Hitboxes.FIXED | Hitboxes.GHOST)) != 0) cir.setReturnValue(false);
    }

    /** pushEntities: ghosts (and walls) don't shove anybody either. */
    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void spraute$pushOthers(CallbackInfo ci) {
        int f = Hitboxes.flags((Entity) (Object) this);
        if ((f & (Hitboxes.SOLID | Hitboxes.GHOST)) != 0) ci.cancel();
    }

    /** isPickable: can't be aimed at. */
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void spraute$pickable(CallbackInfoReturnable<Boolean> cir) {
        int f = Hitboxes.flags((Entity) (Object) this);
        if ((f & Hitboxes.NOPICK) != 0) cir.setReturnValue(false);
    }
}
