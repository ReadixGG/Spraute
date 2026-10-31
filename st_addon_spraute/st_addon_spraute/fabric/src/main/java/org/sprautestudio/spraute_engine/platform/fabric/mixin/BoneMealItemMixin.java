package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(BoneMealItem.class)
public abstract class BoneMealItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"))
    private void spraute$used(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getPlayer() != null) Hooks.BONEMEAL.fire(new Hooks.Bonemeal(context.getPlayer(), context.getClickedPos(), context.getLevel().getBlockState(context.getClickedPos())));
    }
}
