package org.zonarstudio.spraute_engine.item.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.zonarstudio.spraute_engine.compat.SprauteRenderCompat;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.entity.client.SpGeoRenderer;
import org.zonarstudio.spraute_engine.entity.client.SpModelCache;
import org.zonarstudio.spraute_engine.item.GeoItemTransform;
import org.zonarstudio.spraute_engine.item.GeoItemVisualRegistry;
import org.zonarstudio.spraute_engine.item.GeoItemVisuals;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GeoItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static GeoItemRenderer INSTANCE;

    private static final Map<String, SpModelInstance> INSTANCES = new ConcurrentHashMap<>();

    public GeoItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

  //? if >=1.20.1 {
    @Override
    public void renderByItem(ItemStack stack, net.minecraft.world.item.ItemDisplayContext displayContext,
                             PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (stack.isEmpty()) return;
        ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return;
        GeoItemVisuals visuals = GeoItemVisualRegistry.get(itemId);
        if (visuals == null) return;

        GeoItemTransform transform = switch (displayContext) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> visuals.handFirst;
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> visuals.handThird;
            case GROUND -> visuals.handGround;
            case GUI, FIXED, HEAD -> visuals.gui;
            default -> visuals.handThird;
        };
        renderGeoModel(visuals, transform, poseStack, buffer, packedLight, packedOverlay);
    }
  //?} else {
    /*@Override
    public void renderByItem(ItemStack stack, net.minecraft.client.renderer.block.model.ItemTransforms.TransformType transformType,
                             PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (stack.isEmpty()) return;
        ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return;
        GeoItemVisuals visuals = GeoItemVisualRegistry.get(itemId);
        if (visuals == null) return;

        GeoItemTransform transform;
        switch (transformType) {
            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
                transform = visuals.handFirst;
                break;
            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
                transform = visuals.handThird;
                break;
            case GROUND:
                transform = visuals.handGround;
                break;
            case GUI:
            case FIXED:
            case HEAD:
                transform = visuals.gui;
                break;
            default:
                transform = visuals.handThird;
                break;
        }
        renderGeoModel(visuals, transform, poseStack, buffer, packedLight, packedOverlay);
    }
    *///?}

    private static void renderGeoModel(GeoItemVisuals visuals, GeoItemTransform transform,
                                       PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, int packedOverlay) {
        try {
            SpGeoModel model = SpModelCache.getOrLoad(visuals.geoPath);
            if (model.boneMap.isEmpty()) return;

            SpModelInstance instance = INSTANCES.compute(visuals.geoPath, (k, existing) -> {
                if (existing != null && existing.getModel() == model) return existing;
                return new SpModelInstance(model);
            });
            instance.resetAnims();
            instance.computeTransforms();

            poseStack.pushPose();

            poseStack.translate(transform.ox, transform.oy, transform.oz);
            SprauteRenderCompat.rotateX(poseStack, transform.rx);
            SprauteRenderCompat.rotateY(poseStack, transform.ry);
            SprauteRenderCompat.rotateZ(poseStack, transform.rz);
            poseStack.scale(transform.scale, transform.scale, transform.scale);

            poseStack.translate(0.5, 0, 0.5);
            SprauteRenderCompat.rotateZ(poseStack, 180.0F);
            poseStack.translate(0, -1.5, 0);

            ResourceLocation textureLoc = visuals.textureLocation();
            RenderType renderType = RenderType.entityTranslucent(textureLoc);
            VertexConsumer consumer = buffer.getBuffer(renderType);
            SpGeoRenderer.render(instance, poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                    1.0f, 1.0f, 1.0f, 1.0f, null);

            poseStack.popPose();
        } catch (Exception ignored) {
        }
    }

    public static void clearInstances() {
        INSTANCES.clear();
    }
}
