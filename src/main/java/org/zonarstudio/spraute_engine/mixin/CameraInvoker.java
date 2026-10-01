package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Official camera pose helpers (also refresh look vectors / block-pos caches).
 * Prefer these over reflecting Vec3/Quaternion fields — order differs across mappings.
 * <p>
 * Forge's {@code setAnglesInternal} only writes yaw/pitch floats; particle billboards
 * read {@link Camera#rotation()}, so cinematic views must call {@code setRotation}.
 */
@Mixin(Camera.class)
public interface CameraInvoker {
    @Invoker("setPosition")
    void spraute$setPosition(double x, double y, double z);

    @Invoker("setRotation")
    void spraute$setRotation(float yRot, float xRot);
}
