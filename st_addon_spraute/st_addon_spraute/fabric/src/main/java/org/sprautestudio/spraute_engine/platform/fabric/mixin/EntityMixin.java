package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import org.sprautestudio.spraute_engine.platform.fabric.EntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(Entity.class)
public abstract class EntityMixin implements EntityData {
    @Unique
    private CompoundTag spraute$data;

    @Override
    public CompoundTag spraute$data() {
        if (spraute$data == null) spraute$data = new CompoundTag();
        return spraute$data;
    }

    /** Under Forge's name, so that a world keeps this data when it moves between loaders. */
    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    private void spraute$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (spraute$data != null && !spraute$data.isEmpty()) tag.put("ForgeData", spraute$data.copy());
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void spraute$load(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("ForgeData", Tag.TAG_COMPOUND)) spraute$data = tag.getCompound("ForgeData");
    }

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("RETURN"))
    private void spraute$mounted(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) Hooks.ENTITY_MOUNT.fire(new Hooks.EntityMount((Entity) (Object) this, vehicle, true));
    }

    @Inject(method = "removeVehicle", at = @At("HEAD"))
    private void spraute$dismounted(CallbackInfo ci) {
        Entity vehicle = ((Entity) (Object) this).getVehicle();
        if (vehicle != null) Hooks.ENTITY_MOUNT.fire(new Hooks.EntityMount((Entity) (Object) this, vehicle, false));
    }
}
