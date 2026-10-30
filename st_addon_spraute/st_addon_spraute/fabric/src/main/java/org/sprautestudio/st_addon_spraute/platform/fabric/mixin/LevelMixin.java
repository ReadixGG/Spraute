package org.sprautestudio.st_addon_spraute.platform.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.sprautestudio.st_addon_spraute.platform.fabric.FabricParts;
import org.sprautestudio.st_addon_spraute.runtime.models.BodyPart;
import org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity;

import java.util.List;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Level.class)
public abstract class LevelMixin {
    /** What the game looks through to aim: the characters' parts join the answer, like the ender dragon's. */
    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", at = @At("RETURN"))
    private void spraute$parts(Entity except, AABB box, Predicate<? super Entity> filter, CallbackInfoReturnable<List<Entity>> cir) {
        for (SprauteModelEntity body : FabricParts.bodies((Level) (Object) this)) {
            for (BodyPart part : body.parts()) {
                Entity e = part.entity();
                if (part.active() && e != except && e.getBoundingBox().intersects(box) && filter.test(e)) cir.getReturnValue().add(e);
            }
        }
    }
}
