package org.sprautestudio.st_addon_spraute.editor.path;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.cutscene.CutsceneDoc;
import org.sprautestudio.st_addon_spraute.cutscene.client.CameraDirector;
import org.sprautestudio.st_addon_spraute.editor.ClientEvents;
import org.sprautestudio.st_addon_spraute.editor.SprauteScreen;
import org.sprautestudio.st_addon_spraute.path.PathDef;

import java.util.List;

/**
 * Editing a path right in the world: a free camera, the route drawn as a line over the ground with
 * posts at its points. Right click on a block puts a new point on it (after the chosen one); left click
 * on a point takes it, and it follows the aim over the blocks until the next click. The IDE key goes
 * back to the editor.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class PathWorldEditor {
    private PathWorldEditor() {
    }

    private static PathTab tab;
    private static int hover = -1, grabbed = -1;
    private static String toast = "";
    private static long toastAt;
    /** Where the points are on the screen (for their numbers), filled while drawing the world. */
    private static float[][] screen = new float[0][];

    public static boolean active() {
        return tab != null;
    }

    static PathTab tab() {
        return tab;
    }

    static void start(PathTab t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        tab = t;
        hover = grabbed = -1;
        mc.setScreen(null);
        Vec3 e = mc.player.getEyePosition(1);
        CutsceneDoc.Pose start = new CutsceneDoc.Pose();
        PathDef d = t.doc();
        Vec3 c = center(d);
        if (c != null && c.distanceTo(e) > 48) {
            start.x = c.x;
            start.y = c.y + 14;
            start.z = c.z + 14;
            Vec3 to = c.subtract(start.x, start.y, start.z);
            start.yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            start.pitch = (float) Math.toDegrees(-Math.atan2(to.y, Math.hypot(to.x, to.z)));
        } else {
            start.x = e.x;
            start.y = e.y;
            start.z = e.z;
            start.yaw = mc.player.getYRot();
            start.pitch = mc.player.getXRot();
        }
        CameraDirector.startFly(start);
        say("Редактор пути в мире");
    }

    static void stopEditing() {
        if (tab == null) return;
        CameraDirector.stopFly();
        tab = null;
        grabbed = -1;
    }

    private static Vec3 center(PathDef d) {
        if (d.points.isEmpty()) return null;
        double x = 0, y = 0, z = 0;
        for (PathDef.Point p : d.points) {
            x += p.x;
            y += p.y;
            z += p.z;
        }
        int n = d.points.size();
        return new Vec3(x / n, y / n, z / n);
    }

    private static void say(String s) {
        toast = s;
        toastAt = System.currentTimeMillis();
    }

    private static void click() {
        var p = Minecraft.getInstance().player;
        if (p != null) p.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.6f);
    }

    private static Vec3 eye() {
        CutsceneDoc.Pose p = CameraDirector.pose();
        return new Vec3(p.x, p.y, p.z);
    }

    private static Vec3 dir() {
        CutsceneDoc.Pose p = CameraDirector.pose();
        double ya = Math.toRadians(p.yaw), pa = Math.toRadians(p.pitch);
        return new Vec3(-Math.sin(ya) * Math.cos(pa), -Math.sin(pa), Math.cos(ya) * Math.cos(pa));
    }

    private static BlockHitResult aimBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        Vec3 o = eye(), to = o.add(dir().scale(160));
        BlockHitResult h = mc.level.clip(new ClipContext(o, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return h.getType() == HitResult.Type.BLOCK ? h : null;
    }

    /** Where a point would stand: on top of the aimed block (or in front of a wall). */
    private static Vec3 aimSpot() {
        BlockHitResult h = aimBlock();
        if (h == null) return null;
        BlockPos b = h.getBlockPos().relative(h.getDirection());
        return new Vec3(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
    }

    /** The point closest to the middle of the view. */
    private static int aimedPoint(PathDef d) {
        Vec3 e = eye(), f = dir();
        int best = -1;
        double bestAng = Math.toRadians(6);
        for (int i = 0; i < d.points.size(); i++) {
            Vec3 at = d.points.get(i).vec().add(0, 0.9, 0);
            Vec3 to = at.subtract(e);
            double len = to.length();
            if (len < 0.3 || len > 200) continue;
            double ang = Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(f))));
            double allow = Math.max(Math.toRadians(2.5), Math.atan(0.9 / len));
            if (ang < allow && ang < bestAng) {
                best = i;
                bestAng = ang;
            }
        }
        return best;
    }

    // ================================================================== input

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || tab == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen instanceof SprauteScreen || !CameraDirector.flying()) {
            stopEditing();
            return;
        }
        PathDef d = tab.doc();
        if (grabbed >= 0 && grabbed < d.points.size()) {
            Vec3 s = aimSpot();
            if (s != null) {
                PathDef.Point p = d.points.get(grabbed);
                p.x = s.x;
                p.y = s.y;
                p.z = s.z;
            }
        }
        hover = grabbed >= 0 ? grabbed : aimedPoint(d);
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre e) {
        if (tab == null || Minecraft.getInstance().screen != null) return;
        PathDef d = tab.doc();
        if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            e.setCanceled(true);
            if (e.getAction() != GLFW.GLFW_PRESS) return;
            if (grabbed >= 0) {
                grabbed = -1;
                tab.edited(true);
                say("Поставлено");
            } else if (hover >= 0) {
                tab.snapshot();
                grabbed = hover;
                tab.sel = hover;
                click();
                say("Точка " + (hover + 1) + " — ведите прицелом, ЛКМ — поставить");
            }
        } else if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            e.setCanceled(true);
            if (e.getAction() != GLFW.GLFW_PRESS) return;
            if (hover >= 0 && grabbed < 0) {
                // a right click on a point chooses it (new points go after it)
                tab.sel = hover;
                tab.edited(true);
                say("Выбрана точка " + (hover + 1) + " — новые встанут после неё");
                return;
            }
            Vec3 s = aimSpot();
            if (s == null) {
                say("Наведите прицел на блок");
                return;
            }
            tab.addPoint(s);
            click();
            say("Точка " + (tab.sel + 1) + " из " + d.points.size());
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (tab == null || e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        boolean ctrl = (e.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        PathDef d = tab.doc();
        switch (e.getKey()) {
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                int i = hover >= 0 ? hover : tab.sel;
                if (i >= 0) {
                    grabbed = -1;
                    tab.removePoint(i);
                    say("Точка убрана — осталось " + d.points.size());
                }
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (ClientEvents.isEditorKey(e.getKey()) || d.points.isEmpty()) return;
                tab.sel = (tab.sel + 1) % d.points.size();
                tab.edited(true);
                say("Выбрана точка " + (tab.sel + 1));
            }
            case GLFW.GLFW_KEY_P -> {
                // a pause at the chosen point: 0 → 1 → 2 → 3 → 5 → 0 s
                if (tab.sel < 0 || tab.sel >= d.points.size()) return;
                tab.snapshot();
                PathDef.Point p = d.points.get(tab.sel);
                double[] steps = {0, 1, 2, 3, 5};
                int k = 0;
                for (int i = 0; i < steps.length; i++) if (Math.abs(steps[i] - p.wait) < 0.01) k = i;
                p.wait = steps[(k + 1) % steps.length];
                tab.edited(true);
                say("Точка " + (tab.sel + 1) + ": постоять " + PathTab.fmt(p.wait) + " с");
            }
            case GLFW.GLFW_KEY_R -> {
                if (tab.sel < 0 || tab.sel >= d.points.size()) return;
                tab.snapshot();
                d.points.get(tab.sel).yaw = (double) Math.round(CameraDirector.pose().yaw);
                tab.edited(true);
                say("Точка " + (tab.sel + 1) + ": смотреть туда же, куда камера");
            }
            case GLFW.GLFW_KEY_M -> {
                tab.snapshot();
                d.mode = switch (d.mode) {
                    case "once" -> "loop";
                    case "loop" -> "pingpong";
                    default -> "once";
                };
                tab.edited(true);
                say("Как идти: " + modeName(d.mode));
            }
            case GLFW.GLFW_KEY_Z -> {
                if (ctrl && tab.undoPath()) say("Отменено");
            }
            case GLFW.GLFW_KEY_C -> {
                Vec3 c = center(d);
                if (c == null) return;
                CutsceneDoc.Pose p = new CutsceneDoc.Pose();
                p.x = c.x;
                p.y = c.y + 14;
                p.z = c.z + 14;
                Vec3 to = c.subtract(p.x, p.y, p.z);
                p.yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                p.pitch = (float) Math.toDegrees(-Math.atan2(to.y, Math.hypot(to.x, to.z)));
                CameraDirector.flyTo(p);
                say("К пути");
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                tab.saveNow();
                say("Сохранено");
            }
            default -> {
            }
        }
    }

    private static String modeName(String m) {
        return switch (m) {
            case "loop" -> "по кругу";
            case "pingpong" -> "туда-обратно";
            default -> "один раз";
        };
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        if (tab == null || Minecraft.getInstance().screen != null) return;
        CameraDirector.flySpeed = Math.max(0.5, Math.min(64, CameraDirector.flySpeed * (e.getScrollDelta() > 0 ? 1.25 : 0.8)));
        say("Скорость полёта: " + Math.round(CameraDirector.flySpeed * 10) / 10.0);
        e.setCanceled(true);
    }

    // ================================================================== drawing

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (tab == null || e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        PathDef d = tab.doc();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose last = ps.last();
        List<PathDef.Point> pts = d.points;
        float r0 = ((d.color >> 16) & 255) / 255f, g0 = ((d.color >> 8) & 255) / 255f, b0 = (d.color & 255) / 255f;
        for (int i = 1; i < pts.size(); i++) {
            Vec3 a = pts.get(i - 1).vec().add(0, 0.1, 0), b = pts.get(i).vec().add(0, 0.1, 0);
            line(vc, last, a, b, r0, g0, b0, 1);
            // a little arrow in the middle: the way it walks
            Vec3 m = a.add(b).scale(0.5), dirv = b.subtract(a).normalize(), side = new Vec3(-dirv.z, 0, dirv.x).scale(0.35);
            line(vc, last, m, m.subtract(dirv.scale(0.5)).add(side), r0, g0, b0, 1);
            line(vc, last, m, m.subtract(dirv.scale(0.5)).subtract(side), r0, g0, b0, 1);
        }
        if (d.mode.equals("loop") && pts.size() > 2)
            line(vc, last, pts.get(pts.size() - 1).vec().add(0, 0.1, 0), pts.get(0).vec().add(0, 0.1, 0), r0, g0, b0, 0.4f);
        for (int i = 0; i < pts.size(); i++) {
            PathDef.Point p = pts.get(i);
            boolean on = i == hover || i == grabbed, chosen = i == tab.sel;
            float r = on ? 1 : i == 0 ? 0.48f : i == pts.size() - 1 ? 1f : r0, g = on ? 0.88f : i == 0 ? 0.88f : i == pts.size() - 1 ? 0.42f : g0,
                    b = on ? 0.3f : i == 0 ? 0.54f : i == pts.size() - 1 ? 0.42f : b0;
            double s = chosen ? 0.32 : 0.22;
            // a post as tall as a character, with a box on top
            line(vc, last, p.vec(), p.vec().add(0, 1.8, 0), r, g, b, 1);
            box(vc, last, p.x - s, p.y + 1.8 - s, p.z - s, p.x + s, p.y + 1.8 + s, p.z + s, r, g, b, 1);
            box(vc, last, p.x - 0.5, p.y, p.z - 0.5, p.x + 0.5, p.y + 0.02, p.z + 0.5, r, g, b, chosen ? 1 : 0.5f);
            if (p.yaw != null) {
                double ya = Math.toRadians(p.yaw);
                Vec3 f = new Vec3(-Math.sin(ya), 0, Math.cos(ya)).scale(0.8);
                line(vc, last, p.vec().add(0, 1.5, 0), p.vec().add(0, 1.5, 0).add(f), 0.6f, 0.9f, 1, 1);
            }
        }
        // where a right click would put a point
        Vec3 s = grabbed < 0 && hover < 0 ? aimSpot() : null;
        if (s != null) {
            box(vc, last, s.x - 0.5, s.y, s.z - 0.5, s.x + 0.5, s.y + 0.05, s.z + 0.5, 0.4f, 1, 0.6f, 1);
            if (tab.sel >= 0 && tab.sel < pts.size()) line(vc, last, pts.get(tab.sel).vec().add(0, 0.1, 0), s.add(0, 0.1, 0), 0.4f, 1, 0.6f, 0.5f);
        }
        ps.popPose();
        buf.endBatch(RenderType.lines());
        // the screen places of the posts' tops, for their numbers
        Matrix4f proj = e.getProjectionMatrix(), view = e.getPoseStack().last().pose();
        float[][] sc = new float[pts.size()][];
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        for (int i = 0; i < pts.size(); i++) {
            PathDef.Point p = pts.get(i);
            Vector4f v = new Vector4f((float) (p.x - cam.x), (float) (p.y + 2.3 - cam.y), (float) (p.z - cam.z), 1);
            view.transform(v);
            proj.transform(v);
            if (v.w <= 0.05f) continue;
            sc[i] = new float[]{(v.x / v.w * 0.5f + 0.5f) * sw, (1 - (v.y / v.w * 0.5f + 0.5f)) * sh};
        }
        screen = sc;
    }

    private static void line(VertexConsumer vc, PoseStack.Pose p, Vec3 a, Vec3 b, float r, float g, float bl, float al) {
        float dx = (float) (b.x - a.x), dy = (float) (b.y - a.y), dz = (float) (b.z - a.z);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5) return;
        dx /= len;
        dy /= len;
        dz /= len;
        vc.vertex(p.pose(), (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, al).normal(p.normal(), dx, dy, dz).endVertex();
        vc.vertex(p.pose(), (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, al).normal(p.normal(), dx, dy, dz).endVertex();
    }

    private static void box(VertexConsumer vc, PoseStack.Pose p, double x0, double y0, double z0, double x1, double y1, double z1,
                            float r, float g, float b, float a) {
        double[][] e = {
                {x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        for (double[] l : e) line(vc, p, new Vec3(l[0], l[1], l[2]), new Vec3(l[3], l[4], l[5]), r, g, b, a);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        if (tab == null) return;
        Minecraft mc = Minecraft.getInstance();
        var g = e.getGuiGraphics();
        PathDef d = tab.doc();
        // the numbers over the posts
        float[][] sc = screen;
        for (int i = 0; i < sc.length && i < d.points.size(); i++) {
            if (sc[i] == null) continue;
            PathDef.Point p = d.points.get(i);
            String s = (i + 1) + (p.wait > 0 ? " ⏸" + PathTab.fmt(p.wait) : "");
            int w = mc.font.width(s);
            int x = Math.round(sc[i][0]) - w / 2, y = Math.round(sc[i][1]) - 10;
            g.fill(x - 2, y - 1, x + w + 2, y + 9, i == tab.sel ? 0xCC8B6CFF : 0x99000000);
            g.drawString(mc.font, s, x, y, 0xFFFFFFFF, false);
        }
        String back = ClientEvents.OPEN_EDITOR.getTranslatedKeyMessage().getString();
        String[] lines = {
                "ПУТЬ «" + tab.title() + "» — " + d.points.size() + " точек, " + PathTab.fmt(d.length()) + " бл., " + modeName(d.mode) + (tab.isDirty() ? "  •  не сохранено" : ""),
                "WASD, пробел, Shift — полёт · Ctrl — быстрее · колёсико — скорость (" + Math.round(CameraDirector.flySpeed * 10) / 10.0 + ")",
                "ПКМ по блоку — новая точка (после выбранной) · ПКМ по точке — выбрать · ЛКМ по точке — перенести",
                "Tab — следующая · Delete — убрать · P — постоять 0/1/2/3/5 с · R — смотреть как камера · M — один раз / круг / туда-обратно",
                "C — к пути · Ctrl+Z — отменить · Enter — сохранить · [" + back + "] — в редактор"};
        int y = 6;
        for (int i = 0; i < lines.length; i++) {
            int w = mc.font.width(lines[i]);
            g.fill(4, y - 2, 10 + w, y + 9, 0x99000000);
            g.drawString(mc.font, lines[i], 7, y, i == 0 ? (d.color | 0xFF000000) : 0xFFE0E0E0, false);
            y += 12;
        }
        int sw = e.getWindow().getGuiScaledWidth(), sh = e.getWindow().getGuiScaledHeight();
        g.fill(sw / 2 - 4, sh / 2, sw / 2 + 5, sh / 2 + 1, 0xCCFFFFFF);
        g.fill(sw / 2, sh / 2 - 4, sw / 2 + 1, sh / 2 + 5, 0xCCFFFFFF);
        if (!toast.isEmpty() && System.currentTimeMillis() - toastAt < 2000) {
            int tw = mc.font.width(toast);
            g.fill((sw - tw) / 2 - 6, 86, (sw + tw) / 2 + 6, 100, 0xAA000000);
            g.drawString(mc.font, toast, (sw - tw) / 2, 89, 0xFFFFFFFF, false);
        }
    }
}
