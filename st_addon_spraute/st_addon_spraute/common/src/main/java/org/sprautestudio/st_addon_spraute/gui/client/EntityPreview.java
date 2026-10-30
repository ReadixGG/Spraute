package org.sprautestudio.st_addon_spraute.gui.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.sprautestudio.st_addon_spraute.lang.Specs;
import org.sprautestudio.st_addon_spraute.runtime.Anims;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.core.anim.LoopMode;
import org.sprautestudio.st_addon_spraute.core.anim.AnimationSystem;
import org.sprautestudio.st_addon_spraute.core.anim.ConfiguredAnimation;
import org.sprautestudio.st_addon_spraute.core.anim.IAnimatedObject;

/** A client-only entity for GUIs: never added to the level, only its animation system is ticked. */
public final class EntityPreview {
    private final String typeId;
    private Entity entity;
    private boolean created;
    private String anim = "";
    public String error;

    public EntityPreview(String typeId) {
        this.typeId = typeId;
    }

    public String typeId() {
        return typeId;
    }

    public Entity entity() {
        if (created) return entity;
        created = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        ResourceLocation rl = ResourceLocation.tryParse(typeId.contains(":") ? typeId : "minecraft:" + typeId);
        EntityType<?> type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
        if (type == null) {
            error = "Нет сущности " + typeId;
            return null;
        }
        try {
            entity = type.create(mc.level);
            if (entity != null && mc.player != null) entity.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        } catch (Throwable t) {
            entity = null;
            error = t.getClass().getSimpleName();
        }
        return entity;
    }

    private AnimationSystem system() {
        try {
            return entity instanceof IAnimatedObject ao ? ao.getAnimationSystem() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Plays (or keeps playing) an animation; an empty id stops it. */
    public void animate(String id, String layer, boolean loop, float speed) {
        if (id == null) id = "";
        if (id.equals(anim)) return;
        anim = id;
        if (entity() == null) return;
        AnimationSystem sys = system();
        if (sys == null) return;
        try {
            String l = Specs.layerName(layer == null || layer.isBlank() ? "action" : layer);
            if (l == null || sys.getLayer(l) == null) l = "action";
            if (id.isBlank()) {
                sys.stopAnimation(l, 0.2f);
                return;
            }
            String layerName = l;
            Anims.resolve(id).ifPresent(a -> {
                ConfiguredAnimation ca = a.configure().transitionTime(0.2f);
                if (loop) ca.loopMode(LoopMode.LOOP);
                if (speed > 0 && speed != 1) ca.speed(speed);
                sys.startAnimationAt(layerName, ca);
            });
        } catch (Throwable ignored) {
        }
    }

    public void tick() {
        if (!created || entity == null) return;
        entity.tickCount++;
    }

    /**
     * Draws the entity standing on (cx, feetY) fitted into a box of the given height.
     * {@code lookX/lookY} (-1..1) turn head and body like the inventory player model.
     */
    public void render(Canvas c, float cx, float feetY, float boxW, float boxH, float zoom, float yaw, float pitch, float lookX, float lookY) {
        Entity e = entity();
        if (e == null) return;
        float eh = Math.max(0.4f, e.getBbHeight()), ew = Math.max(0.4f, e.getBbWidth());
        float scale = Math.min(boxW * 0.9f / (ew * 1.4f), boxH * 0.92f / eh) * zoom;
        c.flush();
        PoseStack ps = c.g.pose();
        PoseStack.Pose outer = ps.last();
        ps.pushPose();
        try {
            ps.translate(cx, feetY, 200);
            ps.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
            Quaternionf q = new Quaternionf().rotateZ((float) Math.PI);
            q.mul(new Quaternionf().rotateX((float) Math.toRadians(pitch + lookY * 20)));
            ps.mulPose(q);
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
            float body = lookX * 20, head = lookX * 40;
            e.setYRot(head);
            e.yRotO = head;
            e.setXRot(-lookY * 20);
            e.xRotO = e.getXRot();
            if (e instanceof LivingEntity le) {
                le.yBodyRot = le.yBodyRotO = body;
                le.yHeadRot = le.yHeadRotO = head;
            }
            Lighting.setupForEntityInInventory();
            EntityRenderDispatcher d = Minecraft.getInstance().getEntityRenderDispatcher();
            d.setRenderShadow(false);
            float partial = Minecraft.getInstance().getFrameTime();
            RenderSystem.runAsFancy(() -> d.render(e, 0, 0, 0, 0, partial, ps, c.g.bufferSource(), LightTexture.FULL_BRIGHT));
            c.g.flush();
            d.setRenderShadow(true);
        } catch (Throwable t) {
            error = t.getClass().getSimpleName();
            try {
                c.g.flush();
            } catch (Throwable ignored) {
            }
        } finally {
            for (int i = 0; i < 64 && ps.last() != outer; i++) ps.popPose();
            Lighting.setupFor3DItems();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }
}
