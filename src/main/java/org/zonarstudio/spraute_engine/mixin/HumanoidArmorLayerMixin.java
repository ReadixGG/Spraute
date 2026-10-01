package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zonarstudio.spraute_engine.item.ScriptCustomArmorItem;

/** Clothing / overlay-only armor must not draw the vanilla worn mesh (missing-texture tile). */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void spraute$skipHiddenArmor(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                         net.minecraft.client.renderer.MultiBufferSource buffer,
                                         LivingEntity entity, EquipmentSlot slot, int packedLight,
                                         net.minecraft.client.model.HumanoidModel<?> model, CallbackInfo ci) {
        if (entity == null || slot == null) return;
        ItemStack stack = entity.getItemBySlot(slot);
        if (ScriptCustomArmorItem.hidesWornModel(stack)) {
            ci.cancel();
        }
    }
}
