package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import org.zonarstudio.spraute_engine.entity.SprauteOrbEntity;

public class SprauteOrbRenderer extends EntityRenderer<SprauteOrbEntity> {

    public SprauteOrbRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.5F;
    }

    @Override
    public void render(SprauteOrbEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight) {
        pMatrixStack.pushPose();

        String texStr = pEntity.getTexture();
        if (texStr == null || texStr.isEmpty()) texStr = "minecraft:textures/entity/experience_orb.png";
        ResourceLocation texture = texStr.contains(":") ? new ResourceLocation(texStr) : new ResourceLocation("minecraft", texStr);

        pMatrixStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pMatrixStack.mulPose(com.mojang.math.Vector3f.YP.rotationDegrees(180.0F));
        
        float scale = 0.3F;
        pMatrixStack.scale(scale, scale, scale);

        VertexConsumer vertexconsumer = pBuffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose posestack$pose = pMatrixStack.last();
        Matrix4f matrix4f = posestack$pose.pose();
        Matrix3f matrix3f = posestack$pose.normal();
        
        // simple bobbing
        int age = pEntity.age;
        float time = ((float)age + pPartialTicks) / 2.0F;
        int r = (int)((Math.sin(time + 0.0F) + 1.0F) * 0.5F * 255.0F);
        int g = 255;
        int b = (int)((Math.sin(time + 4.18879F) + 1.0F) * 0.1F * 255.0F);
        
        vertex(vertexconsumer, matrix4f, matrix3f, -0.5F, -0.25F, 255, 255, 255, 0.0F, 1.0F, pPackedLight);
        vertex(vertexconsumer, matrix4f, matrix3f, 0.5F, -0.25F, 255, 255, 255, 1.0F, 1.0F, pPackedLight);
        vertex(vertexconsumer, matrix4f, matrix3f, 0.5F, 0.75F, 255, 255, 255, 1.0F, 0.0F, pPackedLight);
        vertex(vertexconsumer, matrix4f, matrix3f, -0.5F, 0.75F, 255, 255, 255, 0.0F, 0.0F, pPackedLight);

        pMatrixStack.popPose();
        super.render(pEntity, pEntityYaw, pPartialTicks, pMatrixStack, pBuffer, pPackedLight);
    }

    private static void vertex(VertexConsumer pConsumer, Matrix4f pPose, Matrix3f pNormal, float pX, float pY, int pRed, int pGreen, int pBlue, float pU, float pV, int pPackedLight) {
        pConsumer.vertex(pPose, pX, pY, 0.0F).color(pRed, pGreen, pBlue, 128).uv(pU, pV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(pPackedLight).normal(pNormal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SprauteOrbEntity pEntity) {
        String texStr = pEntity.getTexture();
        if (texStr == null || texStr.isEmpty()) return new ResourceLocation("minecraft", "textures/entity/experience_orb.png");
        return texStr.contains(":") ? new ResourceLocation(texStr) : new ResourceLocation("minecraft", texStr);
    }
}