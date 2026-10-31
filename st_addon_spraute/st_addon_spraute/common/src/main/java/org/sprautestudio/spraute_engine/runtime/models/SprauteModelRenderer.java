package org.sprautestudio.spraute_engine.runtime.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.core.client.SpGeoRenderer;
import org.sprautestudio.spraute_engine.core.math.SpMatrix4;
import org.sprautestudio.spraute_engine.core.math.SpVec3;
import org.sprautestudio.spraute_engine.core.model.SpGeoModel;
import org.sprautestudio.spraute_engine.core.model.SpModelInstance;

import java.util.Map;
import java.util.WeakHashMap;

/** Draws Spraute entities: the type's Bedrock model, posed by the body's animation layers. */
public class SprauteModelRenderer extends EntityRenderer<SprauteModelEntity> {
    private static final ResourceLocation MISSING = new ResourceLocation("minecraft", "textures/misc/unknown_pack.png");
    private static final String[] RIGHT = {"RightHandItem", "Right_placeholder", "RightItem", "right_item", "item_right", "RightHand", "right_hand", "hand_right"};
    private static final String[] LEFT = {"LeftHandItem", "Left_placeholder", "LeftItem", "left_item", "item_left", "LeftHand", "left_hand", "hand_left"};
    private static final String[] RIGHT_ARM = {"RightArm", "right_arm", "arm_right"};
    private static final String[] LEFT_ARM = {"LeftArm", "left_arm", "arm_left"};

    private static final class Posed {
        final SpGeoModel model;
        final SpModelInstance instance;
        float pupilLeft, pupilRight;

        Posed(SpGeoModel model, SpModelInstance instance) {
            this.model = model;
            this.instance = instance;
        }
    }

    /** How far from the middle of the head each eye is (blocks), the largest turn of an eye and how far a pupil shifts at it. */
    private static final float EYE_OFFSET = 0.1f, EYE_TURN = 30, PUPIL_SHIFT = 0.3f, PUPIL_EASE = 0.2f;

    private static final Map<SprauteModelEntity, Posed> INSTANCES = new WeakHashMap<>();
    /** Set while a portrait is drawn: the rest pose, only these bones. */
    private static boolean portrait;
    private static String[] portraitBones;
    private boolean warned;

    public SprauteModelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.4f;
    }

    /** Draws entities in the rest pose showing only one bone (null — all of it) while {@code draw} runs. */
    public static void withPortrait(String bone, Runnable draw) {
        portrait = true;
        portraitBones = bone != null ? new String[]{bone} : null;
        try {
            draw.run();
        } finally {
            portrait = false;
            portraitBones = null;
        }
    }

    private static Posed posed(SprauteModelEntity e, SpGeoModel model) {
        Posed p = INSTANCES.get(e);
        if (p == null || p.model != model) {
            p = new Posed(model, new SpModelInstance(model));
            INSTANCES.put(e, p);
        }
        return p;
    }

    /** Each eye turns to the target by itself; with no target they ease back to the middle. */
    private static void pupils(SprauteModelEntity e, Posed p) {
        var at = e.lookPoint();
        float wantL = 0, wantR = 0;
        if (at != null) {
            float head = e.yHeadRot;
            double side = Math.toRadians(head + 90);
            wantL = eyeShift(e, at, head, Math.cos(side) * EYE_OFFSET, Math.sin(side) * EYE_OFFSET);
            wantR = eyeShift(e, at, head, -Math.cos(side) * EYE_OFFSET, -Math.sin(side) * EYE_OFFSET);
        }
        p.pupilLeft += (wantL - p.pupilLeft) * PUPIL_EASE;
        p.pupilRight += (wantR - p.pupilRight) * PUPIL_EASE;
        SpVec3 l = p.instance.animPosition("LEye"), r = p.instance.animPosition("REye");
        if (l != null) l.x += p.pupilLeft;
        if (r != null) r.x += p.pupilRight;
    }

    private static float eyeShift(SprauteModelEntity e, net.minecraft.world.phys.Vec3 at, float head, double ox, double oz) {
        double dx = at.x - (e.getX() + ox), dz = at.z - (e.getZ() + oz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return Mth.clamp(Mth.wrapDegrees(yaw - head) / EYE_TURN, -1, 1) * PUPIL_SHIFT;
    }

    @Override
    public void render(SprauteModelEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        EntityLibrary.Look l = e.look();
        SpGeoModel model = l != null ? l.geometry() : null;
        if (model == null || model.boneMap.isEmpty()) {
            super.render(e, yaw, partial, pose, buffers, light);
            return;
        }
        PoseStack.Pose outer = pose.last();
        try {
            // the body's feet: bones are measured from here (and the turned hitboxes drawn)
            e.renderBase = new Matrix4f(pose.last().pose());
            if (!portrait && Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes()) drawShapes(e, pose, buffers);
            Posed posed = posed(e, model);
            SpModelInstance inst = posed.instance;
            inst.resetAnims();
            if (!portrait) {
                e.getAnimationSystem().apply(inst);
                turnHead(e, inst, partial);
                pupils(e, posed);
            }
            inst.computeTransforms();

            float lift = l.def() != null ? l.def().modelY : 0, scale = l.scale();
            pose.pushPose();
            if (lift != 0) pose.translate(0, lift, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
            if (e.deathTime > 0) {
                float t = Math.min(1, Mth.sqrt((e.deathTime + partial - 1) / 20f * 1.6f));
                pose.mulPose(Axis.ZP.rotationDegrees(t * 90));
            }
            if (scale != 1) pose.scale(scale, scale, scale);
            int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(e.hurtTime > 0 || e.deathTime > 0));
            // transparent pixels (the skin's outer layer, glass, hair) stay see-through instead of black
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(texture(e)));
            SpGeoRenderer.render(inst, pose, vc, light, overlay, 1, 1, 1, 1, portrait ? portraitBones : null);
            if (!portrait) {
                measureBones(e, inst, pose);
                heldItems(e, inst, l, pose, buffers, light);
            }
            pose.popPose();
            if (!portrait) super.render(e, yaw, partial, pose, buffers, light);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                SprauteEngine.LOGGER.warn("Spraute: cannot draw entity {}", e.model(), t);
            }
        } finally {
            for (int i = 0; i < 64 && pose.last() != outer; i++) pose.popPose();
        }
    }

    /** The head follows where the body looks, on top of its animation. */
    private static void turnHead(SprauteModelEntity e, SpModelInstance inst, float partial) {
        SpVec3 head = inst.animRotation("head");
        if (head == null) return;
        float yaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot));
        head.add(Mth.lerp(partial, e.xRotO, e.getXRot()), Mth.clamp(yaw, -75, 75), 0);
    }

    /** Moves the pose to a bone: Bedrock pixels to blocks, its turn into the mirrored space the model is drawn in. */
    private static void toBone(PoseStack pose, SpMatrix4 bone) {
        float[] m = bone.m;
        pose.translate(-m[12] / 16f, m[13] / 16f, m[14] / 16f);
        pose.mulPose(new Quaternionf().setFromNormalized(new Matrix3f(
                m[0], -m[1], -m[2],
                -m[4], m[5], m[6],
                -m[8], m[9], m[10])));
    }

    /**
     * Measures the bones the hitbox parts ride on, each time the body is drawn: the bone's pivot and turn in
     * the body's space (from its feet, the world's axes). The parts follow the animation this way.
     */
    private static void measureBones(SprauteModelEntity e, SpModelInstance inst, PoseStack pose) {
        java.util.List<EntityLibrary.Def.Part> defs = e.partList();
        if (defs.isEmpty() || e.renderBase == null) return;
        Matrix4f inv = new Matrix4f(e.renderBase).invert();
        long now = System.currentTimeMillis();
        for (EntityLibrary.Def.Part p : defs) {
            if (p.bone == null || p.bone.isEmpty()) continue;
            SpMatrix4 b = inst.getBoneMatrix(p.bone);
            if (b == null) continue;
            pose.pushPose();
            toBone(pose, b);
            Matrix4f m = new Matrix4f(inv).mul(pose.last().pose());
            e.bonePoses.put(p.bone, new PartShape.BonePose(m.transformPosition(new Vector3f()), m.getNormalizedRotation(new Quaternionf()), now));
            pose.popPose();
        }
    }

    private static SpMatrix4 bone(SpModelInstance inst, String... names) {
        for (String n : names) {
            if (n == null || n.isEmpty()) continue;
            SpMatrix4 m = inst.getBoneMatrix(n);
            if (m != null) return m;
        }
        return null;
    }

    /** The items in its hands: at a hand bone (or the one chosen in the entity type), else at the end of the arm. */
    private static void heldItems(SprauteModelEntity e, SpModelInstance inst, EntityLibrary.Look look, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack main = e.getMainHandItem(), off = e.getOffhandItem();
        if (main.isEmpty() && off.isEmpty()) return;
        EntityLibrary.Def d = look.def() != null ? look.def() : new EntityLibrary.Def();
        if (!main.isEmpty()) hand(e, inst, main, d.itemBone, RIGHT, RIGHT_ARM, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, d, 1, pose, buffers, light);
        if (!off.isEmpty()) hand(e, inst, off, d.offhandBone, LEFT, LEFT_ARM, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, d, -1, pose, buffers, light);
    }

    private static void hand(SprauteModelEntity e, SpModelInstance inst, ItemStack stack, String chosen, String[] hands, String[] arms, ItemDisplayContext ctx,
                             EntityLibrary.Def d, int side, PoseStack pose, MultiBufferSource buffers, int light) {
        SpMatrix4 b = chosen != null && !chosen.isEmpty() ? bone(inst, chosen) : bone(inst, hands);
        boolean arm = false;
        if (b == null) {
            b = bone(inst, arms);
            arm = true;
        }
        if (b == null) return;
        pose.pushPose();
        toBone(pose, b);
        // as Minecraft holds items (ItemInHandLayer): from the arm, down to the fist
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        if (arm) pose.translate(0, 0.125, -0.625);
        // the entity type's own fit (the left hand mirrored)
        pose.translate(d.itemX / 16f * side, d.itemY / 16f, d.itemZ / 16f);
        if (d.itemTurnX != 0) pose.mulPose(Axis.XP.rotationDegrees(d.itemTurnX));
        if (d.itemTurnY != 0) pose.mulPose(Axis.YP.rotationDegrees(d.itemTurnY * side));
        if (d.itemTurnZ != 0) pose.mulPose(Axis.ZP.rotationDegrees(d.itemTurnZ * side));
        if (d.itemScale != 1 && d.itemScale > 0) pose.scale(d.itemScale, d.itemScale, d.itemScale);
        var mc = Minecraft.getInstance();
        mc.getItemRenderer().render(stack, ctx, ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND, pose, buffers, light, OverlayTexture.NO_OVERLAY,
                mc.getItemRenderer().getModel(stack, e.level(), e, 0));
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(SprauteModelEntity e) {
        return texture(e);
    }

    @Override
    protected boolean shouldShowName(SprauteModelEntity e) {
        return e.hasCustomName() && e.isCustomNameVisible();
    }

    /** With hitboxes shown (F3+B): the parts as they really are, turned, in yellow. */
    private static void drawShapes(SprauteModelEntity e, PoseStack pose, MultiBufferSource buffers) {
        var vc = buffers.getBuffer(RenderType.lines());
        var last = pose.last();
        int[][] edges = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {4, 5}, {4, 6}, {5, 7}, {6, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int i = 0; i < SprauteModelEntity.MAX_PARTS; i++) {
            PartShape sh = e.shape(i);
            if (sh == null) continue;
            Vector3f[] c = sh.corners();
            for (int[] ed : edges) {
                Vector3f a = c[ed[0]], b = c[ed[1]];
                Vector3f n = new Vector3f(b).sub(a);
                if (n.lengthSquared() < 1e-8) continue;
                n.normalize();
                vc.vertex(last.pose(), a.x, a.y, a.z).color(1f, 0.85f, 0.2f, 1f).normal(last.normal(), n.x, n.y, n.z).endVertex();
                vc.vertex(last.pose(), b.x, b.y, b.z).color(1f, 0.85f, 0.2f, 1f).normal(last.normal(), n.x, n.y, n.z).endVertex();
            }
        }
    }

    static ResourceLocation texture(SprauteModelEntity e) {
        ResourceLocation over = e.textureOverride();
        if (over != null) return over;
        EntityLibrary.Look l = e.look();
        return l != null && l.texture() != null ? l.texture() : MISSING;
    }
}
