package org.sprautestudio.st_addon_spraute.platform.fabric.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Unique
    private ItemStack spraute$before;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void spraute$touch(Player player, CallbackInfo ci) {
        spraute$before = ((ItemEntity) (Object) this).getItem().copy();
    }

    /** By now the stack may be empty: what was picked up is the copy. */
    @Inject(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;take(Lnet/minecraft/world/entity/Entity;I)V"))
    private void spraute$picked(Player player, CallbackInfo ci) {
        Hooks.ITEM_PICKUP.fire(new Hooks.ItemPickup(player, spraute$before));
    }
}
