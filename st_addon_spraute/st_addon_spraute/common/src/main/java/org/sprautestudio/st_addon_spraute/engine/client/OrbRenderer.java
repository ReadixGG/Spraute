package org.sprautestudio.st_addon_spraute.engine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.runtime.orb.Orbs;
import org.sprautestudio.st_addon_spraute.runtime.orb.SprauteOrb;
import org.sprautestudio.st_addon_spraute.platform.ClientHooks;

/** Draws story orbs: a glowing sprite facing the camera (a picture or the tinted experience orb), or a spinning item. */
public class OrbRenderer extends EntityRenderer<SprauteOrb> {
    private static final ResourceLocation XP = new ResourceLocation("minecraft", "textures/entity/experience_orb.png");

    public OrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.08f;
    }

    public static final class Registration {
        public static void registerHooks() {
            ClientHooks.REGISTER_RENDERERS.add(Registration::renderers);
        }

        public static void renderers(ClientHooks.RegisterRenderers e) {
            e.registerEntityRenderer(Orbs.ORB.get(), OrbRenderer::new);
            e.registerEntityRenderer(Orbs.CAMERA.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
            e.registerEntityRenderer(Orbs.MODEL.get(), org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelRenderer::new);
        }
    }

    @Override
    public void render(SprauteOrb orb, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = orb.age + partial;
        float size = orb.size();
        // fade in, fade out in the last second
        int life = orb.life();
        float alpha = Math.min(1, t / 6f);
        if (life > 0) alpha = Math.min(alpha, Math.max(0, (life - t) / 20f));
        float pulse = 1 + 0.08f * Mth.sin(t * 0.25f);
        String look = orb.look();
        pose.pushPose();
        pose.translate(0, size / 2 + 0.05f * Mth.sin(t * 0.1f), 0);
        if (look.startsWith("item:")) {
            pose.mulPose(Axis.YP.rotationDegrees(t * 4));
            pose.scale(size * 2 * pulse, size * 2 * pulse, size * 2 * pulse);
            Minecraft.getInstance().getItemRenderer().renderStatic(org.sprautestudio.st_addon_spraute.runtime.ItemSpec.stack(look.substring(5), 1), ItemDisplayContext.GROUND, 0xF000F0,
                    OverlayTexture.NO_OVERLAY, pose, buffers, orb.level(), orb.getId());
            pose.popPose();
            return;
        }
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(size * pulse, size * pulse, size * pulse);
        ResourceLocation tex = look.isEmpty() ? XP : ResourceLocation.tryParse(look);
        if (tex == null) tex = XP;
        float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
        if (tex == XP) {
            // the experience orb sheet: 4x4 frames of 16 px, the middle-sized one
            int icon = 3;
            u0 = (icon % 4 * 16) / 64f;
            u1 = (icon % 4 * 16 + 16) / 64f;
            v0 = (icon / 4 * 16) / 64f;
            v1 = (icon / 4 * 16 + 16) / 64f;
        }
        int c = orb.color();
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255, a = Math.round(((c >>> 24) / 255f) * alpha * 255);
        VertexConsumer vc = buffers.getBuffer(RenderType.itemEntityTranslucentCull(tex));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        vertex(vc, m, n, -0.5f, -0.5f, r, g, b, a, u0, v1);
        vertex(vc, m, n, 0.5f, -0.5f, r, g, b, a, u1, v1);
        vertex(vc, m, n, 0.5f, 0.5f, r, g, b, a, u1, v0);
        vertex(vc, m, n, -0.5f, 0.5f, r, g, b, a, u0, v0);
        pose.popPose();
        super.render(orb, yaw, partial, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, int r, int g, int b, int a, float u, float v) {
        vc.vertex(m, x, y, 0).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SprauteOrb orb) {
        return XP;
    }
}
