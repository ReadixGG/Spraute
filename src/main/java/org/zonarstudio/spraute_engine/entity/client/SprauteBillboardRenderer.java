package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
//? if >=1.20.1 {
import org.joml.Matrix3f;
import org.joml.Matrix4f;
//?} else {
/*import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
*///?}
import org.zonarstudio.spraute_engine.compat.SprauteRenderCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.zonarstudio.spraute_engine.entity.SprauteBillboardEntity;

public class SprauteBillboardRenderer extends EntityRenderer<SprauteBillboardEntity> {

    public SprauteBillboardRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(SprauteBillboardEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight) {
        pMatrixStack.pushPose();

        String texStr = pEntity.getTexture();
        if (texStr == null || texStr.isEmpty()) texStr = "minecraft:textures/missing_no.png";
        ResourceLocation texture = texStr.contains(":") ? new ResourceLocation(texStr) : new ResourceLocation("minecraft", texStr);

        pMatrixStack.translate(0.0D, pEntity.getBillboardHeight() / 2.0D, 0.0D);
        pMatrixStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        SprauteRenderCompat.rotateY(pMatrixStack, 180.0F);
        
        float w = pEntity.getBillboardWidth();
        float h = pEntity.getBillboardHeight();
        
        RenderType renderType = pEntity.isSeeThrough() ? RenderType.text(texture) : RenderType.entityCutoutNoCull(texture);


        VertexConsumer vertexconsumer = pBuffer.getBuffer(renderType);
        PoseStack.Pose posestack$pose = pMatrixStack.last();
        Matrix4f matrix4f = posestack$pose.pose();
        Matrix3f matrix3f = posestack$pose.normal();
        
        vertex(vertexconsumer, matrix4f, matrix3f, -w / 2.0F, -h / 2.0F, 255, 255, 255, 0.0F, 1.0F, pPackedLight);
        vertex(vertexconsumer, matrix4f, matrix3f, w / 2.0F, -h / 2.0F, 255, 255, 255, 1.0F, 1.0F, pPackedLight);
        vertex(vertexconsumer, matrix4f, matrix3f, w / 2.0F, h / 2.0F, 255, 255, 255, 1.0F, 0.0F, pPackedLight);
        vertex(vertexconsumer, matrix4f, matrix3f, -w / 2.0F, h / 2.0F, 255, 255, 255, 0.0F, 0.0F, pPackedLight);

        pMatrixStack.popPose();
        super.render(pEntity, pEntityYaw, pPartialTicks, pMatrixStack, pBuffer, pPackedLight);
    }

    private static void vertex(VertexConsumer pConsumer, Matrix4f pPose, Matrix3f pNormal, float pX, float pY, int pRed, int pGreen, int pBlue, float pU, float pV, int pPackedLight) {
        pConsumer.vertex(pPose, pX, pY, 0.0F).color(pRed, pGreen, pBlue, 255).uv(pU, pV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(pPackedLight).normal(pNormal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SprauteBillboardEntity pEntity) {
        String texStr = pEntity.getTexture();
        if (texStr == null || texStr.isEmpty()) return new ResourceLocation("minecraft", "textures/missing_no.png");
        return texStr.contains(":") ? new ResourceLocation(texStr) : new ResourceLocation("minecraft", texStr);
    }
}
