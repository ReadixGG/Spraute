package org.sprautestudio.st_addon_spraute.platform.fabric.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
    @Shadow
    private Player player;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void spraute$smelted(ItemStack stack, CallbackInfo ci) {
        Hooks.ITEM_SMELTED.fire(new Hooks.ItemSmelted(player, stack));
    }
}
