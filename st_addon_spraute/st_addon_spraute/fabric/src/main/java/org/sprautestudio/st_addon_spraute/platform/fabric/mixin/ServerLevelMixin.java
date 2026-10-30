package org.sprautestudio.st_addon_spraute.platform.fabric.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.entity.Entity;
import org.sprautestudio.st_addon_spraute.platform.fabric.FabricParts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "save", at = @At("HEAD"))
    private void spraute$save(ProgressListener progress, boolean flush, boolean skip, CallbackInfo ci) {
        if (!skip) Hooks.LEVEL_SAVE.fire(new Hooks.LevelSave((ServerLevel) (Object) this));
    }

    /** A hit or a click comes by id: this is where the ender dragon's parts are found, and now the characters'. */
    @Inject(method = "getEntityOrPart", at = @At("RETURN"), cancellable = true)
    private void spraute$part(int id, CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() != null) return;
        Entity part = FabricParts.byId((ServerLevel) (Object) this, id);
        if (part != null) cir.setReturnValue(part);
    }
}
