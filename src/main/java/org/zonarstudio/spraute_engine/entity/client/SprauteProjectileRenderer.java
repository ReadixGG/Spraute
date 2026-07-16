package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.compat.SprauteRenderCompat;
import org.zonarstudio.spraute_engine.entity.SprauteProjectileEntity;
//? if >=1.20.1 {
import org.joml.Matrix3f;
import org.joml.Matrix4f;
//?} else {
/*import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
*///?}

public class SprauteProjectileRenderer extends EntityRenderer<SprauteProjectileEntity> {

    public SprauteProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
    }

    @Override
    public void render(SprauteProjectileEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        ResourceLocation texture = resolveTexture(entity.getTexture());
        float w = entity.getProjectileWidth();
        float h = entity.getProjectileHeight();
        int alpha = entity.getAlpha();
        int light = entity.isGlow() ? LightTexture.FULL_BRIGHT : packedLight;

        poseStack.translate(0.0D, h / 2.0D, 0.0D);

        if (entity.isBillboard()) {
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            SprauteRenderCompat.rotateY(poseStack, 180.0F);
        } else {
            Vec3 motion = entity.getDeltaMovement();
            if (motion.lengthSqr() > 1.0E-6) {
                float yaw = (float) (Mth.atan2(motion.x, motion.z) * (180F / Math.PI));
                float pitch = (float) (Mth.atan2(motion.y, motion.horizontalDistance()) * (180F / Math.PI));
                SprauteRenderCompat.rotateY(poseStack, yaw - 90f);
                SprauteRenderCompat.rotateX(poseStack, -pitch);
            }
        }

        SprauteRenderCompat.rotateZ(poseStack, entity.getVisualRotation() + partialTicks * 0);

        RenderType renderType;
        if (entity.isSeeThrough()) {
            renderType = RenderType.text(texture);
        } else if (alpha < 255) {
            renderType = RenderType.entityTranslucent(texture);
        } else {
            renderType = RenderType.entityCutoutNoCull(texture);
        }

        VertexConsumer consumer = buffer.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();

        vertex(consumer, matrix4f, matrix3f, -w / 2f, -h / 2f, 255, 255, 255, alpha, 0f, 1f, light);
        vertex(consumer, matrix4f, matrix3f, w / 2f, -h / 2f, 255, 255, 255, alpha, 1f, 1f, light);
        vertex(consumer, matrix4f, matrix3f, w / 2f, h / 2f, 255, 255, 255, alpha, 1f, 0f, light);
        vertex(consumer, matrix4f, matrix3f, -w / 2f, h / 2f, 255, 255, 255, alpha, 0f, 0f, light);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                               float x, float y, int r, int g, int b, int a,
                               float u, float v, int light) {
        consumer.vertex(pose, x, y, 0f)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, 0f, 1f, 0f)
                .endVertex();
    }

    static ResourceLocation resolveTexture(String texStr) {
        if (texStr == null || texStr.isEmpty()) {
            return new ResourceLocation(Spraute_engine.MODID, "textures/item/missing.png");
        }
        if (texStr.contains(":")) {
            return new ResourceLocation(texStr);
        }
        String path = texStr.replace('\\', '/');
        if (path.startsWith("textures/")) path = path.substring("textures/".length());
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
        return new ResourceLocation(Spraute_engine.MODID, path);
    }

    @Override
    public ResourceLocation getTextureLocation(SprauteProjectileEntity entity) {
        return resolveTexture(entity.getTexture());
    }
}
