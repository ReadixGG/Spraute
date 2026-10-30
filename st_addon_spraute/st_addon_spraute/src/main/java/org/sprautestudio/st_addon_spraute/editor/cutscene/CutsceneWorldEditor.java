package org.sprautestudio.st_addon_spraute.editor.cutscene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.cutscene.CutsceneDoc;
import org.sprautestudio.st_addon_spraute.cutscene.client.CameraDirector;
import org.sprautestudio.st_addon_spraute.editor.ClientEvents;
import org.sprautestudio.st_addon_spraute.editor.SprauteScreen;

/**
 * Editing a cutscene right in the world: the editor flies a free camera, sees the whole path,
 * every frame with its number and a pyramid of where it looks, and edits frames with keys:
 * add one here, aim at a frame and click to select it, move it to the camera, carry it, delete,
 * jump into its view, play the scene from here.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class CutsceneWorldEditor {
    private CutsceneWorldEditor() {}

    private static CutsceneTab tab;
    private static int hover = -1;
    private static boolean carry;
    /** Watching a preview: the editor comes back when it ends. */
    private static boolean previewing;
    private static String toast = "";
    private static long toastAt;

    public static boolean active() {
        return tab != null;
    }

    static void start(CutsceneTab t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        tab = t;
        carry = false;
        mc.setScreen(null);
        CutsceneDoc.Key k = t.selectedKey();
        CutsceneDoc.Pose start = new CutsceneDoc.Pose();
        if (k != null) {
            Vec3 o = t.origin();
            start.x = k.x + o.x;
            start.y = k.y + o.y;
            start.z = k.z + o.z;
            start.yaw = k.yaw;
            start.pitch = k.pitch;
        } else {
            Vec3 e = mc.player.getEyePosition(1);
            start.x = e.x;
            start.y = e.y;
            start.z = e.z;
            start.yaw = mc.player.getYRot();
            start.pitch = mc.player.getXRot();
        }
        CameraDirector.startFly(start);
        say("Редактор катсцены в мире");
    }

    private static void stop() {
        CameraDirector.stopFly();
        tab = null;
        carry = false;
        previewing = false;
    }

    private static void say(String s) {
        toast = s;
        toastAt = System.currentTimeMillis();
    }

    // ================================================================== geometry

    private static Vec3 keyPos(CutsceneDoc.Key k, Vec3 o) {
        return new Vec3(k.x + o.x, k.y + o.y, k.z + o.z);
    }

    private static Vec3 forward(float yaw, float pitch) {
        double ya = Math.toRadians(yaw), pa = Math.toRadians(pitch);
        return new Vec3(-Math.sin(ya) * Math.cos(pa), -Math.sin(pa), Math.cos(ya) * Math.cos(pa));
    }

    /** The frame closest to the centre of the view (within a few degrees). */
    private static int aimed() {
        CutsceneDoc.Pose cam = CameraDirector.pose();
        Vec3 eye = new Vec3(cam.x, cam.y, cam.z), dir = forward(cam.yaw, cam.pitch);
        CutsceneDoc doc = tab.doc();
        Vec3 o = tab.origin();
        int best = -1;
        double bestAngle = Math.toRadians(5);
        for (int i = 0; i < doc.keys.size(); i++) {
            Vec3 to = keyPos(doc.keys.get(i), o).subtract(eye);
            double len = to.length();
            if (len < 0.3 || len > 96) continue;
            double ang = Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(dir))));
            // nearer frames are easier to hit
            double allow = Math.max(bestAngle, Math.atan(0.35 / len));
            if (ang < allow && (best < 0 || ang < bestAngle)) {
                best = i;
                bestAngle = ang;
            }
        }
        return best;
    }

    // ================================================================== ticking and input

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || tab == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            stop();
            return;
        }
        if (previewing) {
            return;
        }
        // the editor key opened the IDE: leave the world editor
        if (mc.screen instanceof SprauteScreen) {
            stop();
            return;
        }
        if (!CameraDirector.flying()) {
            stop();
            return;
        }
        hover = aimed();
        if (carry) tab.setSelectedTo(CameraDirector.pose(), false);
        while (CutsceneRecorder.FRAME.consumeClick()) addHere();
    }

    private static void addHere() {
        tab.addFromPose(CameraDirector.pose());
        Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.6f);
        say("Кадр " + (tab.selectedIndex() + 1) + " добавлен");
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (tab == null || previewing || e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        boolean shift = (e.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        CutsceneDoc doc = tab.doc();
        switch (e.getKey()) {
            case GLFW.GLFW_KEY_R -> {
                if (tab.selectedKey() != null) {
                    tab.setSelectedTo(CameraDirector.pose(), true);
                    say("Кадр " + (tab.selectedIndex() + 1) + " перенесён сюда");
                }
            }
            case GLFW.GLFW_KEY_G -> {
                if (tab.selectedKey() == null) return;
                carry = !carry;
                if (carry) tab.snapshotNow();
                say(carry ? "Кадр летит с камерой — G, чтобы отпустить" : "Кадр отпущен");
            }
            case GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_DELETE -> {
                if (tab.selectedKey() != null) {
                    int n = tab.selectedIndex() + 1;
                    tab.deleteSelected();
                    carry = false;
                    say("Кадр " + n + " удалён");
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> jumpToSelected();
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_COMMA -> {
                if (tab.selectedIndex() > 0) tab.selectIndex(tab.selectedIndex() - 1);
                jumpIf(shift);
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PERIOD -> {
                if (tab.selectedIndex() < doc.keys.size() - 1) tab.selectIndex(tab.selectedIndex() + 1);
                jumpIf(shift);
            }
            case GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET -> {
                CutsceneDoc.Key k = tab.selectedKey();
                if (k == null) return;
                tab.snapshotNow();
                float step = shift ? 1f : 0.25f;
                k.t = Math.max(0, k.t + (e.getKey() == GLFW.GLFW_KEY_RIGHT_BRACKET ? step : -step));
                tab.changed();
                say("Время кадра: " + k.t + " с");
            }
            case GLFW.GLFW_KEY_P -> preview(shift ? Math.max(0, tab.selectedIndex()) : 0);
            case GLFW.GLFW_KEY_F -> {
                tab.toggleFlow();
                say(doc.flow ? "Сплошное движение по всей линии" : "Движение от кадра к кадру");
            }
            default -> {
            }
        }
    }

    private static void jumpIf(boolean jump) {
        if (jump) jumpToSelected();
        else if (tab.selectedKey() != null) say("Выбран кадр " + (tab.selectedIndex() + 1));
    }

    private static void jumpToSelected() {
        CutsceneDoc.Key k = tab.selectedKey();
        if (k == null) return;
        Vec3 o = tab.origin();
        CutsceneDoc.Pose p = new CutsceneDoc.Pose();
        p.x = k.x + o.x;
        p.y = k.y + o.y;
        p.z = k.z + o.z;
        p.yaw = k.yaw;
        p.pitch = k.pitch;
        CameraDirector.flyTo(p);
        say("Вид из кадра " + (tab.selectedIndex() + 1));
    }

    private static void preview(int from) {
        CutsceneDoc.Pose back = CameraDirector.pose();
        previewing = true;
        CameraDirector.stopFly();
        tab.previewFrom(from, () -> {
            previewing = false;
            if (tab != null) {
                CameraDirector.startFly(back);
                say("Просмотр окончен");
            }
        });
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre e) {
        if (tab == null || previewing || Minecraft.getInstance().screen != null || e.getAction() != GLFW.GLFW_PRESS) return;
        if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (hover >= 0) {
                tab.selectIndex(hover);
                say("Выбран кадр " + (hover + 1));
            }
            e.setCanceled(true);
        } else if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            addHere();
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        if (tab == null || previewing || Minecraft.getInstance().screen != null) return;
        CameraDirector.flySpeed = Math.max(0.5, Math.min(64, CameraDirector.flySpeed * (e.getScrollDelta() > 0 ? 1.25 : 0.8)));
        say("Скорость полёта: " + Math.round(CameraDirector.flySpeed * 10) / 10.0);
        e.setCanceled(true);
    }

    // ================================================================== drawing in the world

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (tab == null || e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        CutsceneDoc doc = tab.doc();
        Vec3 o = tab.origin();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose last = ps.last();
        // the path
        if (doc.keys.size() > 1) {
            double dur = doc.duration();
            int n = Math.max(40, (int) (dur * 24));
            CutsceneDoc.Pose prev = doc.at(0);
            for (int i = 1; i <= n; i++) {
                CutsceneDoc.Pose cur = doc.at(dur * i / n);
                // colour from start (blue) to end (green) to see the direction
                float k = i / (float) n;
                line(vc, last, prev.x + o.x, prev.y + o.y, prev.z + o.z, cur.x + o.x, cur.y + o.y, cur.z + o.z,
                        0.3f + 0.1f * k, 0.75f + 0.25f * k, 1f - 0.6f * k, 1f);
                prev = cur;
            }
        }
        int sel = tab.selectedIndex();
        float aspect = mc.getWindow().getWidth() / (float) Math.max(1, mc.getWindow().getHeight());
        for (int i = 0; i < doc.keys.size(); i++) {
            CutsceneDoc.Key k = doc.keys.get(i);
            Vec3 p = keyPos(k, o);
            float r = 1, g = 1, b = 1;
            if (i == sel) {
                r = 1;
                g = 0.8f;
                b = 0.2f;
            } else if (i == hover) {
                r = 0.4f;
                g = 1;
                b = 0.6f;
            }
            box(vc, last, p, i == sel ? 0.18 : 0.12, r, g, b);
            frustum(vc, last, p, k, aspect, r, g, b);
        }
        ps.popPose();
        buf.endBatch(RenderType.lines());
        // numbers above the frames
        Font font = mc.font;
        for (int i = 0; i < doc.keys.size(); i++) {
            CutsceneDoc.Key k = doc.keys.get(i);
            Vec3 p = keyPos(k, o).subtract(cam);
            ps.pushPose();
            ps.translate(p.x, p.y + 0.45, p.z);
            ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            ps.scale(-0.03f, -0.03f, 0.03f);
            String label = (i + 1) + "  " + k.t + "с" + (k.mark.isEmpty() ? "" : "  ⚑" + k.mark);
            Matrix4f m = ps.last().pose();
            int color = i == sel ? 0xFFFFCB6B : i == hover ? 0xFF66FF99 : 0xFFFFFFFF;
            font.drawInBatch(label, -font.width(label) / 2f, 0, color, false, m, buf, Font.DisplayMode.SEE_THROUGH, 0x66000000, 0xF000F0);
            ps.popPose();
        }
        buf.endBatch();
    }

    private static void line(VertexConsumer vc, PoseStack.Pose p, double x1, double y1, double z1, double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5) return;
        dx /= len;
        dy /= len;
        dz /= len;
        vc.vertex(p.pose(), (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(p.normal(), dx, dy, dz).endVertex();
        vc.vertex(p.pose(), (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(p.normal(), dx, dy, dz).endVertex();
    }

    private static void box(VertexConsumer vc, PoseStack.Pose p, Vec3 c, double s, float r, float g, float b) {
        double x0 = c.x - s, x1 = c.x + s, y0 = c.y - s, y1 = c.y + s, z0 = c.z - s, z1 = c.z + s;
        double[][] e = {
                {x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        for (double[] l : e) line(vc, p, l[0], l[1], l[2], l[3], l[4], l[5], r, g, b, 1);
    }

    /** A pyramid from the frame's position showing its view: direction, field of view and roll. */
    private static void frustum(VertexConsumer vc, PoseStack.Pose p, Vec3 at, CutsceneDoc.Key k, float aspect, float r, float g, float b) {
        Vec3 f = forward(k.yaw, k.pitch);
        Vec3 up0 = new Vec3(0, 1, 0);
        Vec3 right = f.cross(up0);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(f).normalize();
        double roll = Math.toRadians(k.roll);
        Vec3 rr = right.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
        Vec3 uu = up.scale(Math.cos(roll)).subtract(right.scale(Math.sin(roll)));
        double len = 0.9;
        double hh = Math.tan(Math.toRadians(k.fov) / 2) * len, hw = hh * aspect;
        Vec3 c = at.add(f.scale(len));
        Vec3[] q = {c.add(rr.scale(-hw)).add(uu.scale(hh)), c.add(rr.scale(hw)).add(uu.scale(hh)),
                c.add(rr.scale(hw)).add(uu.scale(-hh)), c.add(rr.scale(-hw)).add(uu.scale(-hh))};
        for (int i = 0; i < 4; i++) {
            Vec3 a = q[i], bb = q[(i + 1) % 4];
            line(vc, p, at.x, at.y, at.z, a.x, a.y, a.z, r, g, b, 0.8f);
            line(vc, p, a.x, a.y, a.z, bb.x, bb.y, bb.z, r, g, b, 1);
        }
        // a small triangle marks the top of the picture
        Vec3 top = c.add(uu.scale(hh * 1.35));
        line(vc, p, q[0].x, q[0].y, q[0].z, top.x, top.y, top.z, r, g, b, 1);
        line(vc, p, top.x, top.y, top.z, q[1].x, q[1].y, q[1].z, r, g, b, 1);
    }

    // ================================================================== help on screen

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        if (tab == null || previewing) return;
        Minecraft mc = Minecraft.getInstance();
        var g = e.getGuiGraphics();
        String frame = CutsceneRecorder.FRAME.getTranslatedKeyMessage().getString();
        String back = ClientEvents.OPEN_EDITOR.getTranslatedKeyMessage().getString();
        CutsceneDoc doc = tab.doc();
        CutsceneDoc.Key k = tab.selectedKey();
        String[] lines = {
                "КАТСЦЕНА «" + tab.title() + "» — кадров: " + doc.keys.size() + ", " + doc.duration() + " с" + (doc.flow ? ", сплошное движение" : ""),
                "WASD, пробел, Shift — полёт · Ctrl — быстрее · колёсико — скорость (" + Math.round(CameraDirector.flySpeed * 10) / 10.0 + ")",
                "ПКМ или [" + frame + "] — кадр здесь · ЛКМ по кадру — выбрать · ← → — соседний (Shift — перейти в него)",
                "R — перенести кадр сюда · G — нести кадр с камерой · X — удалить · Enter — вид из кадра",
                "[ ] — время кадра (Shift — по 1 с) · F — сплошное движение · P — просмотр (Shift+P — с кадра) · [" + back + "] — в редактор"};
        int y = 6;
        for (int i = 0; i < lines.length; i++) {
            int w = mc.font.width(lines[i]);
            g.fill(4, y - 2, 10 + w, y + 9, 0x99000000);
            g.drawString(mc.font, lines[i], 7, y, i == 0 ? 0xFFFFCB6B : 0xFFE0E0E0, false);
            y += 12;
        }
        if (k != null) {
            String s = "Кадр " + (tab.selectedIndex() + 1) + ": " + k.t + " с · поворот " + Math.round(k.yaw) + "° · наклон " + Math.round(k.pitch)
                    + "° · FOV " + Math.round(k.fov) + (carry ? " · НЕСЁТСЯ" : "");
            int w = mc.font.width(s);
            int sw = e.getWindow().getGuiScaledWidth(), sh = e.getWindow().getGuiScaledHeight();
            g.fill((sw - w) / 2 - 4, sh - 30, (sw + w) / 2 + 4, sh - 18, 0x99000000);
            g.drawString(mc.font, s, (sw - w) / 2, sh - 28, carry ? 0xFF66FF99 : 0xFFFFFFFF, false);
        }
        if (!toast.isEmpty() && System.currentTimeMillis() - toastAt < 2000) {
            int sw = e.getWindow().getGuiScaledWidth();
            int w = mc.font.width(toast);
            g.fill((sw - w) / 2 - 6, 86, (sw + w) / 2 + 6, 100, 0xAA000000);
            g.drawString(mc.font, Component.literal(toast), (sw - w) / 2, 89, 0xFFFFFFFF, false);
        }
    }
}
