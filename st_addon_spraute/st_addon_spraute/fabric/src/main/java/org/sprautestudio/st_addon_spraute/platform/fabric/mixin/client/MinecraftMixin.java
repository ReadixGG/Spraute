package org.sprautestudio.st_addon_spraute.platform.fabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.st_addon_spraute.platform.ClientHooks;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public LocalPlayer player;
    @Shadow
    public HitResult hitResult;
    @Shadow
    protected int missTime;

    /** Whether the action is stopped; a stopped one may still swing the hand. */
    @Unique
    private boolean spraute$stopped() {
        ClientHooks.InteractionKey e = new ClientHooks.InteractionKey(true);
        ClientHooks.INTERACTION_KEY.fire(e);
        if (!e.isCanceled()) return false;
        if (e.shouldSwingHand() && player != null) player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void spraute$attack(CallbackInfoReturnable<Boolean> cir) {
        if (missTime > 0 || hitResult == null || player == null) return;
        if (spraute$stopped()) {
            cir.setReturnValue(false);
        } else if (hitResult.getType() == HitResult.Type.MISS) {
            ClientHooks.LEFT_CLICK_EMPTY.fire(new ClientHooks.LeftClickEmpty());
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void spraute$use(CallbackInfo ci) {
        if (spraute$stopped()) ci.cancel();
    }

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void spraute$pick(CallbackInfo ci) {
        if (spraute$stopped()) ci.cancel();
    }

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen spraute$screen(Screen screen) {
        if (screen == null) return null;
        ClientHooks.ScreenOpening e = new ClientHooks.ScreenOpening(screen);
        ClientHooks.SCREEN_OPENING.fire(e);
        return e.getNewScreen();
    }
}
