package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zonarstudio.spraute_engine.client.PlayerSkinComposer;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSkinMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void spraute$composedSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation custom = PlayerSkinComposer.locationFor((AbstractClientPlayer) (Object) this);
        if (custom != null) cir.setReturnValue(custom);
    }
}
