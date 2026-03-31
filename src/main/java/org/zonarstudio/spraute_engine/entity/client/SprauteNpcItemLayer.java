package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.zonarstudio.spraute_engine.core.math.SpMatrix4;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;

/**
 * Renders held items at bone positions (RightHandItem / LeftHandItem).
 */
public final class SprauteNpcItemLayer {

    private static final String RIGHT_HAND_BONE = "RightHandItem";
    private static final String LEFT_HAND_BONE = "LeftHandItem";

    private SprauteNpcItemLayer() {}

    public static void render(SpModelInstance instance, SprauteNpcEntity entity,
                               PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        ItemStack rightItem = entity.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack leftItem = entity.getItemBySlot(EquipmentSlot.OFFHAND);

        if (!rightItem.isEmpty()) {
            renderItemAtBone(instance, entity, rightItem, RIGHT_HAND_BONE, false, poseStack, bufferSource, packedLight);
        }
        if (!leftItem.isEmpty()) {
            renderItemAtBone(instance, entity, leftItem, LEFT_HAND_BONE, true, poseStack, bufferSource, packedLight);
        }
    }

    private static void renderItemAtBone(SpModelInstance instance, SprauteNpcEntity entity,
                                          ItemStack item, String boneName, boolean leftHand,
                                          PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        SpMatrix4 boneMatrix = instance.getBoneMatrix(boneName);
        if (boneMatrix == null) return;

        poseStack.pushPose();

        var pos = boneMatrix.getTranslation();
        poseStack.translate(pos.x / 16f, pos.y / 16f, pos.z / 16f);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Vector3f.XP.rotationDegrees(-90f));

        Minecraft.getInstance().getItemRenderer().renderStatic(
                item,
                leftHand ? ItemTransforms.TransformType.THIRD_PERSON_LEFT_HAND
                         : ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND,
                packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, bufferSource, entity.getId()
        );

        poseStack.popPose();
    }
}
