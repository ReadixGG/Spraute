package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.spraute_engine.platform.Hooks;
import org.sprautestudio.spraute_engine.platform.TickPhase;
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void spraute$tickStart(CallbackInfo ci) {
        Hooks.PLAYER_TICK.fire(new Hooks.PlayerTick(TickPhase.START, (Player) (Object) this));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void spraute$tickEnd(CallbackInfo ci) {
        Hooks.PLAYER_TICK.fire(new Hooks.PlayerTick(TickPhase.END, (Player) (Object) this));
    }

    /** A player's own version of this method: LivingEntity's is not called. */
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void spraute$hurt(DamageSource source, float amount, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!self.isInvulnerableTo(source)) Hooks.LIVING_HURT.fire(new Hooks.LivingHurt(self, source, amount));
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"))
    private void spraute$tossed(ItemStack stack, boolean scatter, boolean thrower, CallbackInfoReturnable<ItemEntity> cir) {
        if (cir.getReturnValue() != null) Hooks.ITEM_TOSS.fire(new Hooks.ItemToss(cir.getReturnValue(), (Player) (Object) this));
    }

    @Inject(method = "giveExperienceLevels", at = @At("HEAD"))
    private void spraute$levels(int levels, CallbackInfo ci) {
        Hooks.XP_LEVEL_CHANGE.fire(new Hooks.XpLevelChange((Player) (Object) this, levels));
    }
}
