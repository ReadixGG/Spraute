package org.sprautestudio.st_addon_spraute.platform.fabric.mixin;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow
    private Level level;
    @Shadow
    private double x;
    @Shadow
    private double y;
    @Shadow
    private double z;

    @Inject(method = "explode", at = @At("TAIL"))
    private void spraute$explode(CallbackInfo ci) {
        Hooks.EXPLOSION_DETONATE.fire(new Hooks.ExplosionDetonate((Explosion) (Object) this, level, new Vec3(x, y, z)));
    }
}
