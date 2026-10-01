package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zonarstudio.spraute_engine.client.PlayerSkinComposer;
import org.zonarstudio.spraute_engine.client.PlayerSkinLayersClient;

/** Hide cape via model part; clothing second-layer is punched in the composed base skin. */
@Mixin(Player.class)
public abstract class PlayerModelPartShownMixin {
    @Inject(method = "isModelPartShown", at = @At("RETURN"), cancellable = true)
    private void spraute$hideOuterSkin(PlayerModelPart part, CallbackInfoReturnable<Boolean> cir) {
        if (part == null || !cir.getReturnValueZ()) return;
        Player self = (Player) (Object) this;
        if (!PlayerSkinLayersClient.hidden(self.getUUID(), part)) return;
        if (part != PlayerModelPart.CAPE
                && self instanceof AbstractClientPlayer client
                && PlayerSkinComposer.locationFor(client) != null) {
            return;
        }
        cir.setReturnValue(false);
    }
}
