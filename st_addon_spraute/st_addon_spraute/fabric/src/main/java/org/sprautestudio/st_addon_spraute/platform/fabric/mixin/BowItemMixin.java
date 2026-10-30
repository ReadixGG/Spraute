package org.sprautestudio.st_addon_spraute.platform.fabric.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
@Mixin(BowItem.class)
public abstract class BowItemMixin {
    @Inject(method = "releaseUsing", at = @At("HEAD"))
    private void spraute$loose(ItemStack bow, Level level, LivingEntity user, int timeLeft, CallbackInfo ci) {
        if (user instanceof Player p) Hooks.ARROW_LOOSE.fire(new Hooks.ArrowLoose(p, bow, ((BowItem) (Object) this).getUseDuration(bow) - timeLeft));
    }
}
