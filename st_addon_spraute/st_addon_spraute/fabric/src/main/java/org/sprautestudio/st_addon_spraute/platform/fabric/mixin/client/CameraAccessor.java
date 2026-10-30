package org.sprautestudio.st_addon_spraute.platform.fabric.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setRotation")
    void spraute$setRotation(float yaw, float pitch);
}
