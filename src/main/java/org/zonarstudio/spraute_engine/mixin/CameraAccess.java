package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccess {
    @Accessor("detached")
    void spraute$setDetached(boolean detached);
}
