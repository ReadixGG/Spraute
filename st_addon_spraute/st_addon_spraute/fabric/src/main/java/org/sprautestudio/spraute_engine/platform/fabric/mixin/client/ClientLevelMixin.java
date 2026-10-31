package org.sprautestudio.spraute_engine.platform.fabric.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.spraute_engine.platform.Hooks;
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void spraute$join(int id, Entity entity, CallbackInfo ci) {
        Hooks.EntityJoin e = new Hooks.EntityJoin(entity, (ClientLevel) (Object) this);
        Hooks.ENTITY_JOIN.fire(e);
        if (e.isCanceled()) ci.cancel();
    }
}
