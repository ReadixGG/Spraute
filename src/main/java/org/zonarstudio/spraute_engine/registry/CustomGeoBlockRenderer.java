package org.zonarstudio.spraute_engine.registry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.entity.client.SpGeoRenderer;
import org.zonarstudio.spraute_engine.entity.client.SpModelCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomGeoBlockRenderer implements BlockEntityRenderer<CustomGeoBlockEntity> {

    private static class InstanceEntry {
        SpModelInstance instance;
        long lastUsedTime;
    }
    private static final Map<BlockPosKey, InstanceEntry> INSTANCES = new ConcurrentHashMap<>();

    private static class BlockPosKey {
        int x, y, z;
        BlockPosKey(net.minecraft.core.BlockPos p) { x = p.getX(); y = p.getY(); z = p.getZ(); }
        @Override public boolean equals(Object o) { if (o instanceof BlockPosKey k) return x==k.x && y==k.y && z==k.z; return false; }
        @Override public int hashCode() { return java.util.Objects.hash(x, y, z); }
    }

    public CustomGeoBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CustomGeoBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!(blockEntity.getBlockState().getBlock() instanceof CustomGeoBlock customBlock)) return;

        String modelPath = customBlock.getModelPath();
        String texturePath = customBlock.getTexturePath();

        if (modelPath == null || modelPath.isEmpty() || texturePath == null || texturePath.isEmpty()) return;

        try {
            SpGeoModel model = SpModelCache.getOrLoad(modelPath);
            if (model.boneMap.isEmpty()) return;

            BlockPosKey key = new BlockPosKey(blockEntity.getBlockPos());
            InstanceEntry entry = INSTANCES.computeIfAbsent(key, k -> {
                InstanceEntry e = new InstanceEntry();
                e.instance = new SpModelInstance(model);
                return e;
            });
            entry.lastUsedTime = System.currentTimeMillis();

            if (entry.instance.getModel() != model) {
                entry.instance = new SpModelInstance(model);
            }

            SpModelInstance instance = entry.instance;
            instance.resetAnims();
            instance.computeTransforms();

            poseStack.pushPose();
            
            poseStack.translate(0.5, 0, 0.5);
            poseStack.mulPose(Vector3f.ZP.rotationDegrees(180.0F));
            poseStack.translate(0, -1.5, 0);

            ResourceLocation textureLoc = new ResourceLocation(Spraute_engine.MODID, texturePath);
            RenderType renderType = RenderType.entityTranslucent(textureLoc);
            VertexConsumer consumer = bufferSource.getBuffer(renderType);

            SpGeoRenderer.render(instance, poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);

            poseStack.popPose();
        } catch (Exception e) {
        }
    }
}
