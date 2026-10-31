package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void spraute$placed(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && context.getPlayer() != null) {
            Hooks.BLOCK_PLACE.fire(new Hooks.BlockPlace(context.getPlayer(), context.getClickedPos(), context.getLevel().getBlockState(context.getClickedPos())));
        }
    }
}
