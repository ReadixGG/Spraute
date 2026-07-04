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

    /**
     * Apply the rotation part of a Bedrock bone world-matrix (column-major float[16]) to the PoseStack,
     * converting from Bedrock space to MC space via S·R·S where S=diag(-1,1,1) (X-axis reflection).
     *
     * S·R·S elements R_mc[row][col] in terms of SpMatrix4.m[]:
     *   Row 0: ( m[ 0], -m[ 4], -m[ 8])
     *   Row 1: (-m[ 1],  m[ 5],  m[ 9])
     *   Row 2: (-m[ 2],  m[ 6],  m[10])
     * det(S·R·S)=+1, so this IS a proper rotation (representable as quaternion).
     */
    public static void applyBoneRotation(PoseStack poseStack, float[] m) {
        //? if >=1.20.1 {
        // JOML Matrix3f(m00,m01,m02, m10,m11,m12, m20,m21,m22) is col-major: m{col}{row}.
        // Col 0 = (R_mc[0][0], R_mc[1][0], R_mc[2][0]) = ( m[0], -m[1], -m[2])
        // Col 1 = (R_mc[0][1], R_mc[1][1], R_mc[2][1]) = (-m[4],  m[5],  m[6])
        // Col 2 = (R_mc[0][2], R_mc[1][2], R_mc[2][2]) = (-m[8],  m[9], m[10])
        org.joml.Quaternionf q = new org.joml.Quaternionf();
        q.setFromNormalized(new org.joml.Matrix3f(
                 m[0], -m[1], -m[2],
                -m[4],  m[5],  m[6],
                -m[8],  m[9],  m[10]));
        poseStack.mulPose(q);
        //?} else {
        /*// Shepperd's method on R_mc = S·R·S.
        // Elements: rmc[row][col] as noted above.
        float rmc00 =  m[0]; float rmc01 = -m[4]; float rmc02 = -m[8];
        float rmc10 = -m[1]; float rmc11 =  m[5]; float rmc12 =  m[9];
        float rmc20 = -m[2]; float rmc21 =  m[6]; float rmc22 =  m[10];
        float trace = rmc00 + rmc11 + rmc22; // = m[0]+m[5]+m[10]
        float qx, qy, qz, qw;
        if (trace > 0f) {
            float s = 0.5f / (float) Math.sqrt(trace + 1f);
            qw = 0.25f / s;
            qx = (rmc21 - rmc12) * s; // m[6]  - m[9]
            qy = (rmc02 - rmc20) * s; // m[2]  - m[8]  (negated vs Bedrock)
            qz = (rmc10 - rmc01) * s; // m[4]  - m[1]  (negated vs Bedrock)
        } else if (rmc00 > rmc11 && rmc00 > rmc22) {
            float s = 2f * (float) Math.sqrt(1f + rmc00 - rmc11 - rmc22);
            qw = (rmc21 - rmc12) / s;
            qx = 0.25f * s;
            qy = (rmc01 + rmc10) / s; // (-m[4]) + (-m[1])
            qz = (rmc02 + rmc20) / s; // (-m[8]) + (-m[2])
        } else if (rmc11 > rmc22) {
            float s = 2f * (float) Math.sqrt(1f + rmc11 - rmc00 - rmc22);
            qw = (rmc02 - rmc20) / s;
            qx = (rmc01 + rmc10) / s;
            qy = 0.25f * s;
            qz = (rmc12 + rmc21) / s; // m[9] + m[6]
        } else {
            float s = 2f * (float) Math.sqrt(1f + rmc22 - rmc00 - rmc11);
            qw = (rmc10 - rmc01) / s;
            qx = (rmc02 + rmc20) / s;
            qy = (rmc12 + rmc21) / s;
            qz = 0.25f * s;
        }
        poseStack.mulPose(new com.mojang.math.Quaternion(qx, qy, qz, qw));
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
