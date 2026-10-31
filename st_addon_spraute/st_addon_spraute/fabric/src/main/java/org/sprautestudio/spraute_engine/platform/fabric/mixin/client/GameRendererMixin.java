package org.sprautestudio.spraute_engine.platform.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.sprautestudio.spraute_engine.platform.ClientHooks;
import org.sprautestudio.spraute_engine.platform.Hooks;
import org.sprautestudio.spraute_engine.platform.TickPhase;
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void spraute$renderStart(float partial, long time, boolean world, CallbackInfo ci) {
        ClientHooks.RENDER_TICK.fire(new ClientHooks.RenderTick(TickPhase.START, partial));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void spraute$renderEnd(float partial, long time, boolean world, CallbackInfo ci) {
        ClientHooks.RENDER_TICK.fire(new ClientHooks.RenderTick(TickPhase.END, partial));
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void spraute$hand(PoseStack pose, Camera camera, float partial, CallbackInfo ci) {
        ClientHooks.RenderHand e = new ClientHooks.RenderHand();
        ClientHooks.RENDER_HAND.fire(e);
        if (e.isCanceled()) ci.cancel();
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void spraute$fov(Camera camera, float partial, boolean configured, CallbackInfoReturnable<Double> cir) {
        ClientHooks.ComputeFov e = new ClientHooks.ComputeFov(cir.getReturnValueD());
        ClientHooks.COMPUTE_FOV.fire(e);
        cir.setReturnValue(e.getFOV());
    }

    /** The game has no roll: the camera is turned here, before the game's own two turns of the view. */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", shift = At.Shift.AFTER))
    private void spraute$angles(float partial, long time, PoseStack pose, CallbackInfo ci) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        ClientHooks.CameraAngles e = new ClientHooks.CameraAngles(camera.getYRot(), camera.getXRot(), 0);
        ClientHooks.CAMERA_ANGLES.fire(e);
        ((CameraAccessor) camera).spraute$setRotation(e.getYaw(), e.getPitch());
        pose.mulPose(Axis.ZP.rotationDegrees(e.getRoll()));
    }
}
