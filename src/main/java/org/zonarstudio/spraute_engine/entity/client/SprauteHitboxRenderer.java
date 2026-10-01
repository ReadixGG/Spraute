package org.zonarstudio.spraute_engine.entity.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.zonarstudio.spraute_engine.entity.SprauteHitboxEntity;

/** Intentionally renders nothing; editor/debug lines visualize HITBOX nodes. */
public final class SprauteHitboxRenderer extends EntityRenderer<SprauteHitboxEntity> {
    private static final ResourceLocation EMPTY =
            new ResourceLocation("minecraft", "textures/misc/white.png");

    public SprauteHitboxRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(SprauteHitboxEntity entity) {
        return EMPTY;
    }
}
