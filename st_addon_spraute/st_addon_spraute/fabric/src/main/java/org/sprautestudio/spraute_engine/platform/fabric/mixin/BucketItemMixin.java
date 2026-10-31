package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
    @Inject(method = "use", at = @At("HEAD"))
    private void spraute$used(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        Hooks.FILL_BUCKET.fire(new Hooks.FillBucket(player, player.getItemInHand(hand)));
    }
}
