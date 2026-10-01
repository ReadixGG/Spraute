package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.zonarstudio.spraute_engine.client.BillboardClientState;
import org.zonarstudio.spraute_engine.client.WorldCanvasTextureManager;
import org.zonarstudio.spraute_engine.entity.SprauteWorldCanvasEntity;

/**
 * Draws a runtime {@code worldCanvas} panel: one textured quad sampling the texture that
 * {@link WorldCanvasTextureManager} keeps up to date. Distance/frustum culling already happened
 * in the entity ({@code shouldRenderAtSqrDistance}/{@code getBoundingBoxForCulling}).
 */
public class SprauteWorldCanvasRenderer extends EntityRenderer<SprauteWorldCanvasEntity> {

    public SprauteWorldCanvasRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(SprauteWorldCanvasEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!BillboardClientState.shouldRender()) return;
        String json = entity.getJson();
        if (json.isEmpty()) return;
        double distSq = this.entityRenderDispatcher.distanceToSqr(entity);
        WorldCanvasTextureManager.Handle handle = WorldCanvasTextureManager.request(
                WorldCanvasTextureManager.KEY_PREFIX + "e" + entity.getId(), json, entity.getResolution(), distSq);
        if (handle == null) return; // first frame after spawn: texture renders next frame

        float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        WorldCanvasTextureManager.drawPanel(poseStack, handle, entity.getWorldWidth(), entity.getWorldHeight(),
                entity.getMode(), entityYaw, pitch, entity.getRoll(),
                this.entityRenderDispatcher.camera, entity.getId());
    }

    @Override
    public ResourceLocation getTextureLocation(SprauteWorldCanvasEntity entity) {
        // Unused: the panel binds an off-screen render target's texture id directly.
        return new ResourceLocation("minecraft", "textures/misc/white.png");
    }
}
