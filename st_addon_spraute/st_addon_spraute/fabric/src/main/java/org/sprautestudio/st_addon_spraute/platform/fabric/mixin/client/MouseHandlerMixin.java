package org.sprautestudio.st_addon_spraute.platform.fabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.st_addon_spraute.platform.ClientHooks;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    private Minecraft minecraft;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void spraute$press(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (window != minecraft.getWindow().getWindow()) return;
        ClientHooks.MouseButton e = new ClientHooks.MouseButton(button, action);
        ClientHooks.MOUSE_BUTTON.fire(e);
        if (e.isCanceled()) ci.cancel();
    }

    /** Only in the game itself, as the game's own scrolling of the hotbar. */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void spraute$scroll(long window, double dx, double dy, CallbackInfo ci) {
        if (window != minecraft.getWindow().getWindow() || minecraft.screen != null || minecraft.getOverlay() != null || minecraft.player == null) return;
        double delta = (minecraft.options.discreteMouseScroll().get() ? Math.signum(dy) : dy) * minecraft.options.mouseWheelSensitivity().get();
        ClientHooks.MouseScroll e = new ClientHooks.MouseScroll(delta);
        ClientHooks.MOUSE_SCROLL.fire(e);
        if (e.isCanceled()) ci.cancel();
    }
}
