package org.sprautestudio.st_addon_spraute.platform.fabric.mixin.client;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.st_addon_spraute.platform.ClientHooks;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("TAIL"))
    private void spraute$key(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        ClientHooks.KEY.fire(new ClientHooks.Key(key, scanCode, action, modifiers));
    }
}
