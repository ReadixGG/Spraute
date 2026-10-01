package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.GameRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.zonarstudio.spraute_engine.client.CameraHandler;

/**
 * Vanilla {@code renderItemInHand} only runs in first person and when HUD is
 * visible. An offset player camera still wants the first-person arm when
 * {@code showHand} is on.
 */
@Mixin(GameRenderer.class)
public class GameRendererHandMixin {
    @Redirect(method = "renderItemInHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean spraute$firstPersonHand(CameraType type) {
        if (CameraHandler.shouldForceFirstPersonHand()) return true;
        return type.isFirstPerson();
    }

    @Redirect(method = "renderItemInHand", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Options;hideGui:Z", opcode = Opcodes.GETFIELD))
    private boolean spraute$hideGuiHand(Options options) {
        if (CameraHandler.shouldForceFirstPersonHand()) return false;
        return options.hideGui;
    }
}
