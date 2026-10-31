package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(PersistentEntitySectionManager.class)
public abstract class EntitySectionMixin {
    /** Every way into a level, a chunk loaded from disk too: as with Forge, a story can refuse an entity here. */
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void spraute$join(EntityAccess added, boolean worldGen, CallbackInfoReturnable<Boolean> cir) {
        if (!(added instanceof Entity entity)) return;
        Hooks.EntityJoin e = new Hooks.EntityJoin(entity, entity.level());
        Hooks.ENTITY_JOIN.fire(e);
        if (e.isCanceled()) cir.setReturnValue(false);
    }
}
