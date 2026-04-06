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
            
            net.minecraft.core.Direction blockDir = net.minecraft.core.Direction.NORTH;
            if (blockEntity.getBlockState().hasProperty(CustomGeoBlock.FACING)) {
                blockDir = blockEntity.getBlockState().getValue(CustomGeoBlock.FACING);
                poseStack.mulPose(Vector3f.YP.rotationDegrees(-blockDir.toYRot()));
            }
            
            poseStack.mulPose(Vector3f.ZP.rotationDegrees(180.0F));
            poseStack.translate(0, -1.5, 0);

            ResourceLocation textureLoc = texturePath.contains(":") ? new ResourceLocation(texturePath) : new ResourceLocation(Spraute_engine.MODID, texturePath);
            RenderType renderType = RenderType.entityTranslucent(textureLoc);
            VertexConsumer consumer = bufferSource.getBuffer(renderType);

            SpGeoRenderer.render(instance, poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);

            poseStack.popPose();
        } catch (Exception e) {
        }
        
        if (!blockEntity.displays.isEmpty()) {
            poseStack.pushPose();
            
            poseStack.translate(0.5, 0.0, 0.5);
            if (blockEntity.getBlockState().hasProperty(CustomGeoBlock.FACING)) {
                net.minecraft.core.Direction blockDir = blockEntity.getBlockState().getValue(CustomGeoBlock.FACING);
                poseStack.mulPose(Vector3f.YP.rotationDegrees(-blockDir.toYRot()));
            }
            poseStack.translate(-0.5, 0.0, -0.5);
            
            for (Map.Entry<String, CustomGeoBlockEntity.BlockDisplay> dEntry : blockEntity.displays.entrySet()) {
                String dId = dEntry.getKey();
                CustomGeoBlockEntity.BlockDisplay d = dEntry.getValue();
                
                if (d.itemOrModel == null || d.itemOrModel.isEmpty()) continue;
                
                if (d.displayType == 0) {
                    net.minecraft.world.item.Item mcItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        new ResourceLocation(d.itemOrModel.contains(":") ? d.itemOrModel : "minecraft:" + d.itemOrModel)
                    );
                    if (mcItem == null || mcItem == net.minecraft.world.item.Items.AIR) continue;
                    
                    poseStack.pushPose();
                    poseStack.translate(d.ox, d.oy, d.oz);
                    poseStack.mulPose(Vector3f.XP.rotationDegrees(d.rx));
                    poseStack.mulPose(Vector3f.YP.rotationDegrees(d.ry));
                    poseStack.mulPose(Vector3f.ZP.rotationDegrees(d.rz));
                    poseStack.scale(d.scale, d.scale, d.scale);
                    
                    net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(
                        new net.minecraft.world.item.ItemStack(mcItem),
                        net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIXED,
                        packedLight, packedOverlay, poseStack, bufferSource, 0
                    );
                    
                    poseStack.popPose();
                } else if (d.displayType == 1) {
                    try {
                        SpGeoModel modelToDraw = SpModelCache.getOrLoad(d.itemOrModel);
                        if (!modelToDraw.boneMap.isEmpty()) {
                            BlockPosKey key = new BlockPosKey(blockEntity.getBlockPos());
                            String cacheKey = key.x + "_" + key.y + "_" + key.z + "_" + dId;
                            
                            // We can reuse the INSTANCES map, just change the key type or use a string map.
                            // Let's just create a new map for displays to keep it simple, or use a quick static map.
                            // Actually, let's just instantiate it, it's fast enough for simple models.
                            SpModelInstance mInstance = new SpModelInstance(modelToDraw);
                            mInstance.resetAnims();
                            mInstance.computeTransforms();
                            
                            poseStack.pushPose();
                            poseStack.translate(d.ox, d.oy, d.oz);
                            poseStack.mulPose(Vector3f.XP.rotationDegrees(d.rx));
                            poseStack.mulPose(Vector3f.YP.rotationDegrees(d.ry));
                            poseStack.mulPose(Vector3f.ZP.rotationDegrees(d.rz));
                            poseStack.scale(d.scale, d.scale, d.scale);

                            poseStack.translate(0.5, 0, 0.5);
                            poseStack.mulPose(Vector3f.ZP.rotationDegrees(180.0F));
                            poseStack.translate(0, -1.5, 0);

                            ResourceLocation texLoc = d.texture.contains(":") ? new ResourceLocation(d.texture) : new ResourceLocation(Spraute_engine.MODID, d.texture);
                            RenderType rType = RenderType.entityTranslucent(texLoc);
                            VertexConsumer cons = bufferSource.getBuffer(rType);
                            SpGeoRenderer.render(mInstance, poseStack, cons, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
                            
                            poseStack.popPose();
                        }
                    } catch (Exception ex) {}
                } else if (d.displayType == 2) {
                    net.minecraft.world.level.block.Block mcBlock = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                        new ResourceLocation(d.itemOrModel.contains(":") ? d.itemOrModel : "minecraft:" + d.itemOrModel)
                    );
                    if (mcBlock == null || mcBlock == net.minecraft.world.level.block.Blocks.AIR) continue;
                    
                    poseStack.pushPose();
                    poseStack.translate(d.ox, d.oy, d.oz);
                    poseStack.mulPose(Vector3f.XP.rotationDegrees(d.rx));
                    poseStack.mulPose(Vector3f.YP.rotationDegrees(d.ry));
                    poseStack.mulPose(Vector3f.ZP.rotationDegrees(d.rz));
                    poseStack.scale(d.scale, d.scale, d.scale);
                    
                    net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                        mcBlock.defaultBlockState(),
                        poseStack, bufferSource, packedLight, packedOverlay, net.minecraftforge.client.model.data.ModelData.EMPTY, null
                    );
                    
                    poseStack.popPose();
                }
            }
            poseStack.popPose();
        }
    }
}
