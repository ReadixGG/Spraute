package org.sprautestudio.st_addon_spraute.platform.fabric.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique
    private boolean spraute$falling;

    @Shadow
    public abstract ItemStack getUseItem();

    @Shadow
    public abstract boolean causeFallDamage(float distance, float multiplier, DamageSource source);

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void spraute$attacked(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Hooks.LivingAttack e = new Hooks.LivingAttack((LivingEntity) (Object) this, source, amount);
        Hooks.LIVING_ATTACK.fire(e);
        if (e.isCanceled()) cir.setReturnValue(false);
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void spraute$hurt(DamageSource source, float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.isInvulnerableTo(source)) Hooks.LIVING_HURT.fire(new Hooks.LivingHurt(self, source, amount));
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void spraute$die(DamageSource source, CallbackInfo ci) {
        Hooks.LivingDeath e = new Hooks.LivingDeath((LivingEntity) (Object) this, source);
        Hooks.LIVING_DEATH.fire(e);
        if (e.isCanceled()) ci.cancel();
    }

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void spraute$usedItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isUsingItem()) Hooks.USE_ITEM_FINISH.fire(new Hooks.UseItemFinish(self, getUseItem().copy()));
    }

    @Inject(method = "handleEquipmentChanges", at = @At("HEAD"))
    private void spraute$equipment(Map<EquipmentSlot, ItemStack> changes, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        changes.forEach((slot, stack) -> Hooks.EQUIPMENT_CHANGE.fire(new Hooks.EquipmentChange(self, slot, stack)));
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void spraute$jumped(CallbackInfo ci) {
        Hooks.LIVING_JUMP.fire(new Hooks.LivingJump((LivingEntity) (Object) this));
    }

    /** With other numbers: the fall is counted over, once. */
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void spraute$fall(float distance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (spraute$falling) return;
        Hooks.LivingFall e = new Hooks.LivingFall((LivingEntity) (Object) this, distance, multiplier);
        Hooks.LIVING_FALL.fire(e);
        if (e.getDistance() == distance && e.getDamageMultiplier() == multiplier) return;
        spraute$falling = true;
        try {
            cir.setReturnValue(causeFallDamage(e.getDistance(), e.getDamageMultiplier(), source));
        } finally {
            spraute$falling = false;
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
    private void spraute$effect(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) Hooks.EFFECT_ADDED.fire(new Hooks.EffectAdded((LivingEntity) (Object) this, effect));
    }
}
