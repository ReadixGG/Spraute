package org.sprautestudio.spraute_engine.mixin;

import net.minecraft.world.entity.Entity;
import org.sprautestudio.spraute_engine.hitbox.Hitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "solid" characters: other entities collide with them like with a boat or a shulker. SRG names: the game runs with them. */
@Mixin(Entity.class)
public abstract class EntityHitboxMixin {
    /** canBeCollidedWith */
    @Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
    private void spraute$solid(CallbackInfoReturnable<Boolean> cir) {
        int f = Hitboxes.flags((Entity) (Object) this);
        if ((f & Hitboxes.SOLID) != 0) cir.setReturnValue(true);
    }
}
