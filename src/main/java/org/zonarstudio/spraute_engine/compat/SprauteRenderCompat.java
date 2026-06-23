package org.zonarstudio.spraute_engine.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;

/**
 * Version-specific rendering helpers (rotation axes, item display context).
 * Rotations are applied directly to the {@link PoseStack} to avoid exposing the
 * version-specific quaternion type ({@code com.mojang.math.Quaternion} on 1.19.x,
 * {@code org.joml.Quaternionf} on 1.20.1+).
 */
public final class SprauteRenderCompat {
    private SprauteRenderCompat() {}

    public static void rotateX(PoseStack poseStack, float degrees) {
        //? if >=1.20.1 {
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(degrees));
        //?} else {
        /*poseStack.mulPose(com.mojang.math.Vector3f.XP.rotationDegrees(degrees));
        *///?}
    }

    public static void rotateY(PoseStack poseStack, float degrees) {
        //? if >=1.20.1 {
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(degrees));
        //?} else {
        /*poseStack.mulPose(com.mojang.math.Vector3f.YP.rotationDegrees(degrees));
        *///?}
    }

    public static void rotateZ(PoseStack poseStack, float degrees) {
        //? if >=1.20.1 {
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
        //?} else {
        /*poseStack.mulPose(com.mojang.math.Vector3f.ZP.rotationDegrees(degrees));
        *///?}
    }

    public static void renderItemInHand(ItemRenderer renderer, ItemStack stack, boolean leftHand,
                                        PoseStack poseStack, MultiBufferSource buffer, int packedLight, int entityId) {
        //? if >=1.20.1 {
        renderer.renderStatic(
                stack,
                leftHand ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                         : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, null, entityId);
        //?} else {
        /*renderer.renderStatic(
                stack,
                leftHand ? net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.THIRD_PERSON_LEFT_HAND
                         : net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entityId);
        *///?}
    }

    public static void renderFixedItem(ItemRenderer renderer, ItemStack stack,
                                       PoseStack poseStack, MultiBufferSource buffer, int packedLight, int entityId) {
        //? if >=1.20.1 {
        renderer.renderStatic(
                stack,
                net.minecraft.world.item.ItemDisplayContext.FIXED,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, null, entityId);
        //?} else {
        /*renderer.renderStatic(
                stack,
                net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIXED,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entityId);
        *///?}
    }
}
