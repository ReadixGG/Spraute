package org.sprautestudio.st_addon_spraute.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "skin player …": the story's texture instead of the player's own. SRG names: the game runs with them. */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerSkinMixin {
    /** getSkinTextureLocation */
    @Inject(method = {"m_108560_", "getSkinTextureLocation"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void spraute$skin(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation own = org.sprautestudio.st_addon_spraute.core.client.PlayerSkins.of(((net.minecraft.world.entity.Entity) (Object) this).getUUID());
        if (own != null) cir.setReturnValue(own);
    }
}
