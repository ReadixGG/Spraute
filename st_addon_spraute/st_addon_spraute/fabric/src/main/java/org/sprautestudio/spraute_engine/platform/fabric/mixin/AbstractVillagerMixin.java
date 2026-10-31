package org.sprautestudio.spraute_engine.platform.fabric.mixin;

import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixin {
    @Inject(method = "notifyTrade", at = @At("HEAD"))
    private void spraute$trade(MerchantOffer offer, CallbackInfo ci) {
        AbstractVillager self = (AbstractVillager) (Object) this;
        if (self.getTradingPlayer() != null) Hooks.TRADE.fire(new Hooks.Trade(self.getTradingPlayer(), self, offer));
    }
}
