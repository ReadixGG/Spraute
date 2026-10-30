package org.sprautestudio.st_addon_spraute.editor.assets;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.sprautestudio.st_addon_spraute.lang.Specs;
import org.sprautestudio.st_addon_spraute.runtime.Anims;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.core.anim.Animation;
import org.sprautestudio.st_addon_spraute.core.anim.LoopMode;
import org.sprautestudio.st_addon_spraute.core.anim.AnimationSystem;
import org.sprautestudio.st_addon_spraute.core.anim.ConfiguredAnimation;
import org.sprautestudio.st_addon_spraute.core.anim.IAnimatedObject;

import java.util.Optional;

/**
 * Live 3D preview of an entity type. Drag to rotate, wheel to zoom, double click to reset.
 * The entity is a client-only instance that is never added to the level; only its animation
 * system is ticked, so animations can be previewed without touching the world.
 */
public class ModelView extends Widget implements UI.CursorProvider {
    private EntityType<?> type;
    private Entity entity;
    private boolean created;
    private String error;
    private float yaw = 205, pitch = 8, zoom = 1, targetZoom = 1;
    private boolean autoSpin;
    private String playing;
    /** Text shown when nothing is selected. */
    public String emptyText = "Выберите модель";

    // ---------------------------------------------------------------- hitboxes drawn over the model

    /** A hitbox to draw: a (maybe turned) box in the body's space, blocks from the feet. */
    public record Box(org.sprautestudio.st_addon_spraute.runtime.models.PartShape shape, int color, String label, boolean selected) {
    }

    /** The preview body (for the bones its parts ride on), or null. */
    public Entity previewEntity() {
        return created ? entity : null;
    }

    /** Hitboxes to draw over the model (null — none). */
    public java.util.function.Supplier<java.util.List<Box>> boxes;
    /** A box was clicked (its index in boxes()), or -1 — empty space. */
    public java.util.function.IntConsumer onPickBox;
    /** The selected box is dragged by this many blocks (x, y, z); release — finished. */
    public MoveListener onMoveBox;

    public interface MoveListener {
        /** dx, dy, dz — the motion in the model's space; sx, sy — on the screen (blocks; +sx right, +sy down). */
        void moved(float dx, float dy, float dz, float sx, float sy);

        void released();
    }

    private float lastCx, lastCy, lastScale;
    private boolean draggingBox;

    private Matrix4f view(float scale) {
        return new Matrix4f().scaling(scale, scale, -scale).rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
    }

    private float[] project(Matrix4f m, float px, float py, float pz) {
        org.joml.Vector3f v = m.transformPosition(new org.joml.Vector3f(px, py, pz));
        return new float[]{lastCx + v.x, lastCy + v.y};
    }

    private float[][] corners(Matrix4f m, Box b) {
        org.joml.Vector3f[] c = b.shape().corners();
        float[][] out = new float[8][];
        for (int i = 0; i < 8; i++) out[i] = project(m, c[i].x, c[i].y, c[i].z);
        return out;
    }

    private void paintBoxes(Canvas c) {
        if (boxes == null) return;
        java.util.List<Box> list = boxes.get();
        if (list == null || list.isEmpty()) return;
        Matrix4f m = view(lastScale);
        int[][] edges = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {4, 5}, {4, 6}, {5, 7}, {6, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (Box b : list) {
            float[][] p = corners(m, b);
            int col = b.selected() ? 0xFFFFE066 : b.color();
            float th = b.selected() ? 2f : 1f;
            for (int[] e : edges) c.line(p[e[0]][0], p[e[0]][1], p[e[1]][0], p[e[1]][1], th, col);
            if (b.label() != null && !b.label().isEmpty()) {
                var sh = b.shape();
                org.joml.Vector3f t = sh.rot().transform(new org.joml.Vector3f(0, sh.half().y, 0)).add(sh.center());
                float[] top = project(m, t.x, t.y, t.z);
                c.textCentered(b.label(), (int) top[0], (int) top[1] - 11, col);
            }
        }
    }

    /** The smallest box under the mouse, or -1. */
    private int boxAt(double mx, double my) {
        if (boxes == null) return -1;
        java.util.List<Box> list = boxes.get();
        if (list == null) return -1;
        Matrix4f m = view(lastScale);
        int best = -1;
        float bestArea = Float.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            float[][] p = corners(m, list.get(i));
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
            for (float[] q : p) {
                x0 = Math.min(x0, q[0]);
                y0 = Math.min(y0, q[1]);
                x1 = Math.max(x1, q[0]);
                y1 = Math.max(y1, q[1]);
            }
            float area = (x1 - x0) * (y1 - y0);
            if (mx >= x0 && mx <= x1 && my >= y0 && my <= y1 && area < bestArea) {
                best = i;
                bestArea = area;
            }
        }
        return best;
    }

    /** Called on the preview entity right after it is created (e.g. to pick a custom model). */
    private java.util.function.Consumer<Entity> setup;

    /** A type with a setup step; always recreates the preview. */
    public void setType(EntityType<?> t, java.util.function.Consumer<Entity> setup) {
        this.setup = setup;
        type = null;
        setType(t);
    }

    public void setType(EntityType<?> t) {
        if (t == type) return;
        type = t;
        entity = null;
        created = false;
        error = null;
        playing = null;
    }

    public EntityType<?> type() {
        return type;
    }

    public void setAutoSpin(boolean spin) {
        autoSpin = spin;
    }

    public boolean autoSpin() {
        return autoSpin;
    }

    public String playing() {
        return playing;
    }

    public boolean isAnimated() {
        return entity() instanceof IAnimatedObject;
    }

    private Entity entity() {
        if (created) return entity;
        created = true;
        Minecraft mc = Minecraft.getInstance();
        if (type == null || mc.level == null) return null;
        try {
            entity = type.create(mc.level);
            if (entity == null) {
                error = "Эта сущность не создаётся отдельно";
            } else {
                if (mc.player != null) entity.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                if (setup != null) setup.accept(entity);
            }
        } catch (Throwable t) {
            entity = null;
            error = "Не удалось создать модель: " + t.getClass().getSimpleName();
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

    /** Plays an animation on a layer; returns an error text or null. */
    public String play(String animId, String layer, boolean loop, float speed) {
        Entity e = entity();
        if (e == null) return error != null ? error : "Нет модели";
        AnimationSystem sys = system();
        if (sys == null) return "Эта модель не поддерживает анимации";
        Optional<Animation> a = Anims.resolve(animId);
        if (a.isEmpty()) return "Анимация не найдена";
        String layerName = Specs.layerName(layer);
        if (layerName == null || sys.getLayer(layerName) == null) {
            layerName = sys.getLayer("action") != null ? "action" : sys.getLayerNames().stream().findFirst().orElse(null);
        }
        if (layerName == null) return "У модели нет слоёв анимации";
        try {
            ConfiguredAnimation ca = a.get().configure().transitionTime(0.2f);
            if (loop) ca.loopMode(LoopMode.LOOP);
            if (speed != 1) ca.speed(speed);
            sys.startAnimationAt(layerName, ca);
            playing = animId;
            return null;
        } catch (Throwable t) {
            return "Ошибка анимации: " + t.getMessage();
        }
    }

    /** Plays an animation already picked (one of a model's own). */
    public String play(ConfiguredAnimation ca, String label) {
        Entity e = entity();
        if (e == null) return error != null ? error : "Нет модели";
        AnimationSystem sys = system();
        if (sys == null) return "Эта модель не поддерживает анимации";
        String layerName = sys.getLayer("action") != null ? "action" : sys.getLayerNames().stream().findFirst().orElse(null);
        if (layerName == null) return "У модели нет слоёв анимации";
        try {
            sys.startAnimationAt(layerName, ca.transitionTime(0.2f).loopMode(LoopMode.LOOP));
            playing = label;
            return null;
        } catch (Throwable t) {
            return "Ошибка анимации: " + t.getMessage();
        }
    }

    public void stopAll() {
        AnimationSystem sys = system();
        playing = null;
        if (sys == null) return;
        try {
            for (String l : sys.getLayerNames()) sys.stopAnimation(l, 0.2f);
        } catch (Throwable ignored) {
        }
    }

    public void resetView() {
        yaw = 205;
        pitch = 8;
        targetZoom = 1;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isShown()) return;
        Entity e = created ? entity : null;
        if (e == null) return;
        e.tickCount++;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        // stage: vignette background and a floor grid
        c.gradient(x, y, w, h, 0xFF1F2130, 0xFF121319);
        int floorY = y + h * 3 / 4;
        for (int i = -8; i <= 8; i++) {
            int gx = x + w / 2 + i * w / 18;
            c.fill(gx, floorY, 1, h - (floorY - y), 0x10FFFFFF);
        }
        for (int k = 0; k < 5; k++) c.fill(x, floorY + k * k * 3, w, 1, 0x12FFFFFF);
        c.fill(x, floorY, w, 1, 0x22FFFFFF);

        if (autoSpin) yaw += dt * 25;
        zoom += (targetZoom - zoom) * Math.min(1, dt * 12);

        if (type == null) {
            c.icon(Icon.CUBE, x + w / 2 - 4, y + h / 2 - 16, Theme.TEXT_MUTED);
            c.textCentered(emptyText, x + w / 2, y + h / 2, Theme.TEXT_MUTED);
            return;
        }
        Entity e = entity();
        if (e == null) {
            c.icon(Icon.WARNING, x + w / 2 - 4, y + h / 2 - 16, Theme.WARNING);
            c.textCentered(error != null ? error : "Нет мира — войдите в мир для превью", x + w / 2, y + h / 2, Theme.TEXT_DIM);
            return;
        }
        float eh = Math.max(0.4f, e.getBbHeight()), ew = Math.max(0.4f, e.getBbWidth());
        float scale = Math.min(w * 0.55f / (ew * 1.6f), h * 0.62f / eh) * zoom;
        float cx = x + w / 2f, cy = floorY - 2;
        // soft shadow under the model
        int sw = (int) (ew * scale * 1.1f);
        c.round((int) cx - sw / 2, (int) cy - 2, sw, 5, 2, 0x44000000);
        lastCx = cx;
        lastCy = cy;
        lastScale = scale;
        renderEntity(c, e, cx, cy, scale);
        paintBoxes(c);
        if (error != null) c.textCentered(error, x + w / 2, y + 8, Theme.ERROR);
    }

    private void renderEntity(Canvas c, Entity e, float cx, float cy, float scale) {
        c.flush();
        PoseStack ps = c.g.pose();
        PoseStack.Pose outer = ps.last();
        ps.pushPose();
        try {
            ps.translate(cx, cy, 200);
            ps.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
            Quaternionf q = new Quaternionf().rotateZ((float) Math.PI);
            q.mul(new Quaternionf().rotateX((float) Math.toRadians(pitch)));
            ps.mulPose(q);
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
            e.setYRot(0);
            e.yRotO = 0;
            e.setXRot(0);
            e.xRotO = 0;
            if (e instanceof LivingEntity le) {
                le.yBodyRot = le.yBodyRotO = 0;
                le.yHeadRot = le.yHeadRotO = 0;
            }
            Lighting.setupForEntityInInventory();
            EntityRenderDispatcher d = Minecraft.getInstance().getEntityRenderDispatcher();
            d.setRenderShadow(false);
            float partial = Minecraft.getInstance().getFrameTime();
            RenderSystem.runAsFancy(() -> d.render(e, 0, 0, 0, 0, partial, ps, c.g.bufferSource(), LightTexture.FULL_BRIGHT));
            c.g.flush();
            d.setRenderShadow(true);
        } catch (Throwable t) {
            if (error == null) org.sprautestudio.st_addon_spraute.St_addon_spraute.LOGGER.warn("Spraute: model preview failed", t);
            error = "Ошибка отрисовки: " + t.getClass().getSimpleName();
            try {
                c.g.flush();
            } catch (Throwable ignored) {
            }
        } finally {
            // a renderer that failed half-way leaves its own transforms pushed: unwind to ours,
            // or everything drawn after the preview would be moved off the screen
            for (int i = 0; i < 64 && ps.last() != outer; i++) ps.popPose();
            Lighting.setupFor3DItems();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            // the model must not hide UI drawn later (popups, tooltips)
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        draggingBox = false;
        if (button == 0 && onPickBox != null && boxes != null && lastScale > 0) {
            int i = boxAt(mx, my);
            onPickBox.accept(i);
            // a picked hitbox follows the mouse; elsewhere the drag turns the view
            draggingBox = i >= 0 && onMoveBox != null;
        }
        return button == 0 || button == 1;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingBox && onMoveBox != null) onMoveBox.released();
        draggingBox = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingBox && button == 0 && lastScale > 0) {
            // screen motion back into the model's space: along the screen plane at the box's depth
            org.joml.Matrix3f r = new org.joml.Matrix3f().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(pitch))
                    .rotateY((float) Math.toRadians(yaw));
            org.joml.Vector3f d = new org.joml.Vector3f((float) dx / lastScale, (float) dy / lastScale, 0);
            r.transpose().transform(d);
            onMoveBox.moved(d.x, d.y, d.z, (float) dx / lastScale, (float) dy / lastScale);
            return true;
        }
        yaw += (float) dx * 1.4f;
        pitch = Math.max(-60, Math.min(60, pitch - (float) dy * 1.0f));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        targetZoom = Math.max(0.3f, Math.min(4f, targetZoom * (delta > 0 ? 1.15f : 1 / 1.15f)));
        return true;
    }

    @Override
    public boolean mouseDoubleClicked(double mx, double my, int button) {
        resetView();
        return true;
    }

    @Override
    public int cursor(int mx, int my) {
        return UI.CURSOR_HAND;
    }
}
