package org.zonarstudio.spraute_engine.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.zonarstudio.spraute_engine.client.editor.scene.ScenePreviewManager;

/**
 * Ghost NPC/MODEL previews are not entities, so vanilla never marks them glowing.
 * Before the outline batch is flushed we submit their silhouettes and force the
 * entity-outline post-pass flag so {@code entityEffect} runs (vanilla outline).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererOutlineMixin {
    //? if >=1.20.1 {
    @ModifyVariable(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/OutlineBufferSource;endOutlineBatch()V"),
            index = 23)
    private boolean spraute$forcePreviewOutlineFlag(
            boolean entityOutlineRendered,
            PoseStack pose,
            float partialTick,
            long finishNanoTime,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            org.joml.Matrix4f projectionMatrix) {
        boolean submitted = ScenePreviewManager.submitGlowingOutlines(pose, camera.getPosition());
        return entityOutlineRendered || submitted;
    }
    //?} else {
    /*@ModifyVariable(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/OutlineBufferSource;endOutlineBatch()V"),
            index = 24)
    private boolean spraute$forcePreviewOutlineFlag(
            boolean entityOutlineRendered,
            PoseStack pose,
            float partialTick,
            long finishNanoTime,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            com.mojang.math.Matrix4f projectionMatrix) {
        boolean submitted = ScenePreviewManager.submitGlowingOutlines(pose, camera.getPosition());
        return entityOutlineRendered || submitted;
    }
    *///?}
}
