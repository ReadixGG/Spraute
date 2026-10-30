package org.sprautestudio.st_addon_spraute.editor.zone;

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
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.cutscene.CutsceneDoc;
import org.sprautestudio.st_addon_spraute.cutscene.client.CameraDirector;
import org.sprautestudio.st_addon_spraute.editor.ClientEvents;
import org.sprautestudio.st_addon_spraute.editor.SprauteScreen;
import org.sprautestudio.st_addon_spraute.zone.ZoneDef;
import org.sprautestudio.st_addon_spraute.platform.ClientHooks;
import org.sprautestudio.st_addon_spraute.platform.TickPhase;

import java.util.ArrayList;
import java.util.List;

/**
 * Editing a zone right in the world: a free camera, the zone drawn with handles on its faces (or its
 * centre, radius and top). Right click on blocks puts the corners (one, then the other) or the centre;
 * left click grabs a handle and it follows the aim along its axis until the next click; G carries the
 * whole zone with the aim. The IDE key goes back to the editor.
 */
public final class ZoneWorldEditor {
    public static void registerHooks() {
        ClientHooks.CLIENT_TICK.add(ZoneWorldEditor::onTick);
        ClientHooks.MOUSE_BUTTON.add(ZoneWorldEditor::onMouse);
        ClientHooks.KEY.add(ZoneWorldEditor::onKey);
        ClientHooks.MOUSE_SCROLL.add(ZoneWorldEditor::onScroll);
        ClientHooks.RENDER_LEVEL.add(ZoneWorldEditor::onRenderLevel);
        ClientHooks.RENDER_GUI.add(ZoneWorldEditor::onGui);
    }

    private ZoneWorldEditor() {
    }

    private static ZoneTab tab;
    /** A handle: where it is, the axis it moves along (null — free, with the aim), its id. */
    private record Handle(String id, Vec3 at, Vec3 axis) {
    }

    private static String hover, grabbed;
    private static boolean carry;
    private static Vec3 carryOffset;
    /** Which corner the next right click puts. */
    private static int nextCorner = 1;
    private static String toast = "";
    private static long toastAt;

    public static boolean active() {
        return tab != null;
    }

    static void start(ZoneTab t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        tab = t;
        hover = grabbed = null;
        carry = false;
        nextCorner = 1;
        mc.setScreen(null);
        Vec3 e = mc.player.getEyePosition(1);
        ZoneDef d = t.doc();
        CutsceneDoc.Pose start = new CutsceneDoc.Pose();
        // start a bit back from the zone if it is far, else where the player is
        Vec3 c = d.center();
        if (c.distanceTo(e) > 48) {
            double size = Math.max(d.maxX() - d.minX(), d.maxZ() - d.minZ());
            start.x = c.x;
            start.y = d.maxY() + size * 0.6 + 4;
            start.z = d.maxZ() + size * 0.6 + 4;
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
        say("Редактор зоны в мире");
    }

    private static void stop() {
        CameraDirector.stopFly();
        tab = null;
        grabbed = null;
        carry = false;
    }

    private static void say(String s) {
        toast = s;
        toastAt = System.currentTimeMillis();
    }

    private static void click() {
        var p = Minecraft.getInstance().player;
        if (p != null) p.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.6f);
    }

    // ================================================================== geometry

    private static Vec3 eye() {
        CutsceneDoc.Pose p = CameraDirector.pose();
        return new Vec3(p.x, p.y, p.z);
    }

    private static Vec3 dir() {
        CutsceneDoc.Pose p = CameraDirector.pose();
        double ya = Math.toRadians(p.yaw), pa = Math.toRadians(p.pitch);
        return new Vec3(-Math.sin(ya) * Math.cos(pa), -Math.sin(pa), Math.cos(ya) * Math.cos(pa));
    }

    /** The block the camera looks at (null — sky). */
    private static BlockHitResult aimBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        Vec3 o = eye(), to = o.add(dir().scale(160));
        BlockHitResult h = mc.level.clip(new ClipContext(o, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return h.getType() == HitResult.Type.BLOCK ? h : null;
    }

    private static List<Handle> handles(ZoneDef d) {
        List<Handle> out = new ArrayList<>();
        double mx = (d.minX() + d.maxX()) / 2, my = (d.minY() + d.maxY()) / 2, mz = (d.minZ() + d.maxZ()) / 2;
        Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);
        switch (d.shape) {
            case "box" -> {
                out.add(new Handle("-x", new Vec3(d.minX(), my, mz), X));
                out.add(new Handle("+x", new Vec3(d.maxX(), my, mz), X));
                out.add(new Handle("-y", new Vec3(mx, d.minY(), mz), Y));
                out.add(new Handle("+y", new Vec3(mx, d.maxY(), mz), Y));
                out.add(new Handle("-z", new Vec3(mx, my, d.minZ()), Z));
                out.add(new Handle("+z", new Vec3(mx, my, d.maxZ()), Z));
            }
            case "sphere" -> {
                out.add(new Handle("r", new Vec3(d.cx + d.radius, d.cy, d.cz), X));
                out.add(new Handle("ry", new Vec3(d.cx, d.cy + d.radius, d.cz), Y));
                out.add(new Handle("cy", new Vec3(d.cx, d.cy, d.cz), Y));
            }
            default -> {
                out.add(new Handle("r", new Vec3(d.cx + d.radius, d.cy + d.height / 2, d.cz), X));
                out.add(new Handle("h", new Vec3(d.cx, d.cy + d.height, d.cz), Y));
                out.add(new Handle("cy", new Vec3(d.cx, d.cy, d.cz), Y));
            }
        }
        return out;
    }

    /** The handle closest to the middle of the view. */
    private static String aimedHandle(ZoneDef d) {
        Vec3 e = eye(), f = dir();
        String best = null;
        double bestAng = Math.toRadians(6);
        for (Handle h : handles(d)) {
            Vec3 to = h.at().subtract(e);
            double len = to.length();
            if (len < 0.3 || len > 200) continue;
            double ang = Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(f))));
            double allow = Math.max(Math.toRadians(2.5), Math.atan(0.6 / len));
            if (ang < allow && ang < bestAng) {
                best = h.id();
                bestAng = ang;
            }
        }
        return best;
    }

    /** Where along the line (at + t·axis) the view ray passes closest. */
    private static Vec3 alongAxis(Vec3 at, Vec3 axis) {
        Vec3 o = eye(), d = dir();
        Vec3 w0 = at.subtract(o);
        double b = axis.dot(d), dd = axis.dot(w0), e = d.dot(w0);
        double denom = 1 - b * b;
        if (Math.abs(denom) < 1e-4) return null;
        double t = (b * e - dd) / denom;
        return at.add(axis.scale(t));
    }

    /** Puts the box corners in order (x1 ≤ x2 …) so the faces map to them. */
    private static void order(ZoneDef d) {
        int a;
        if (d.x1 > d.x2) { a = d.x1; d.x1 = d.x2; d.x2 = a; }
        if (d.y1 > d.y2) { a = d.y1; d.y1 = d.y2; d.y2 = a; }
        if (d.z1 > d.z2) { a = d.z1; d.z1 = d.z2; d.z2 = a; }
    }

    private static void drag(ZoneDef d) {
        Handle h = null;
        for (Handle x : handles(d)) if (x.id().equals(grabbed)) h = x;
        if (h == null) return;
        Vec3 p = alongAxis(h.at(), h.axis());
        if (p == null) return;
        switch (grabbed) {
            case "-x" -> d.x1 = Math.min(d.x2, (int) Math.round(p.x));
            case "+x" -> d.x2 = Math.max(d.x1, (int) Math.round(p.x) - 1);
            case "-y" -> d.y1 = Math.min(d.y2, (int) Math.round(p.y));
            case "+y" -> d.y2 = Math.max(d.y1, (int) Math.round(p.y) - 1);
            case "-z" -> d.z1 = Math.min(d.z2, (int) Math.round(p.z));
            case "+z" -> d.z2 = Math.max(d.z1, (int) Math.round(p.z) - 1);
            case "r" -> d.radius = Math.max(0.5, Math.round(Math.abs(p.x - d.cx) * 2) / 2.0);
            case "ry" -> d.radius = Math.max(0.5, Math.round(Math.abs(p.y - d.cy) * 2) / 2.0);
            case "h" -> d.height = Math.max(0.5, Math.round((p.y - d.cy) * 2) / 2.0);
            case "cy" -> d.cy = Math.round(p.y * 2) / 2.0;
            default -> {
            }
        }
        tab.edited(false);
    }

    /** Carry: the zone's floor centre follows the block under the aim. */
    private static void carryTo(ZoneDef d) {
        BlockHitResult h = aimBlock();
        if (h == null) return;
        BlockPos b = h.getBlockPos().relative(h.getDirection());
        Vec3 target = new Vec3(b.getX() + 0.5, b.getY(), b.getZ() + 0.5).add(carryOffset);
        Vec3 now = floorCenter(d);
        int dx = (int) Math.round(target.x - now.x), dy = (int) Math.round(target.y - now.y), dz = (int) Math.round(target.z - now.z);
        if (dx == 0 && dy == 0 && dz == 0) return;
        d.x1 += dx;
        d.x2 += dx;
        d.y1 += dy;
        d.y2 += dy;
        d.z1 += dz;
        d.z2 += dz;
        d.cx += dx;
        d.cy += dy;
        d.cz += dz;
        tab.edited(false);
    }

    private static Vec3 floorCenter(ZoneDef d) {
        return new Vec3((d.minX() + d.maxX()) / 2, d.shape.equals("sphere") ? d.cy - d.radius : d.minY(), (d.minZ() + d.maxZ()) / 2);
    }

    // ================================================================== ticking and input

    public static void onTick(ClientHooks.ClientTick e) {
        if (e.phase != TickPhase.END || tab == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen instanceof SprauteScreen || !CameraDirector.flying()) {
            stop();
            return;
        }
        ZoneDef d = tab.doc();
        if (grabbed != null) drag(d);
        else if (carry) carryTo(d);
        hover = grabbed != null ? grabbed : aimedHandle(d);
    }

    public static void onMouse(ClientHooks.MouseButton e) {
        if (tab == null || Minecraft.getInstance().screen != null) return;
        ZoneDef d = tab.doc();
        if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            e.setCanceled(true);
            if (e.getAction() == GLFW.GLFW_PRESS) {
                if (grabbed != null) {
                    grabbed = null;
                    tab.edited(true);
                    say("Отпущено");
                } else if (hover != null) {
                    tab.snapshot();
                    if (d.shape.equals("box")) order(d);
                    carry = false;
                    grabbed = hover;
                    click();
                    say("Тяните прицелом, щёлкните ещё раз — положить");
                }
            }
        } else if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            e.setCanceled(true);
            if (e.getAction() != GLFW.GLFW_PRESS) return;
            BlockHitResult h = aimBlock();
            if (h == null) {
                say("Наведите прицел на блок");
                return;
            }
            tab.snapshot();
            BlockPos b = h.getBlockPos();
            if (d.shape.equals("box")) {
                if (nextCorner == 1) {
                    d.x1 = b.getX();
                    d.y1 = b.getY();
                    d.z1 = b.getZ();
                    // a fresh corner: the box is one block until the second one
                    d.x2 = d.x1;
                    d.y2 = d.y1 + 3;
                    d.z2 = d.z1;
                    say("Угол 1 — теперь ПКМ по второму углу");
                } else {
                    d.x2 = b.getX();
                    d.y2 = Math.max(b.getY(), d.y1 + 1);
                    d.z2 = b.getZ();
                    order(d);
                    say("Угол 2 — зона " + d.size());
                }
                nextCorner = 3 - nextCorner;
            } else {
                BlockPos up = b.relative(h.getDirection());
                d.cx = up.getX() + 0.5;
                d.cy = d.shape.equals("sphere") ? up.getY() + 0.5 : up.getY();
                d.cz = up.getZ() + 0.5;
                say("Центр здесь");
            }
            click();
            tab.edited(true);
        }
    }

    public static void onKey(ClientHooks.Key e) {
        if (tab == null || e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        boolean shift = (e.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0, ctrl = (e.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        ZoneDef d = tab.doc();
        int step = shift ? 5 : 1;
        switch (e.getKey()) {
            case GLFW.GLFW_KEY_G -> {
                grabbed = null;
                carry = !carry;
                if (carry) {
                    tab.snapshot();
                    BlockHitResult h = aimBlock();
                    Vec3 fc = floorCenter(d);
                    if (h != null) {
                        BlockPos b = h.getBlockPos().relative(h.getDirection());
                        carryOffset = new Vec3(0, fc.y - b.getY(), 0);
                        // keep the height above the ground it had only if it was floating
                        if (Math.abs(carryOffset.y) > 8) carryOffset = Vec3.ZERO;
                    } else carryOffset = Vec3.ZERO;
                } else tab.edited(true);
                say(carry ? "Зона едет за прицелом — G, чтобы поставить" : "Зона поставлена");
            }
            case GLFW.GLFW_KEY_B -> {
                if (ClientEvents.isEditorKey(e.getKey())) return;
                tab.newPiece();
                grabbed = null;
                carry = false;
                say("Новая часть " + (tab.doc().editIndex + 1) + " — всего " + tab.doc().pieces());
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (ClientEvents.isEditorKey(e.getKey())) return;
                tab.nextPiece();
                grabbed = null;
                carry = false;
                say("Правится часть " + (tab.doc().editIndex + 1) + " из " + tab.doc().pieces());
            }
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                grabbed = null;
                carry = false;
                say(tab.removePiece() ? "Часть убрана — осталось " + tab.doc().pieces() : "Последнюю часть не убрать");
            }
            case GLFW.GLFW_KEY_T -> {
                tab.snapshot();
                tab.cycleShape();
                grabbed = null;
                say("Форма: " + switch (tab.doc().shape) {
                    case "sphere" -> "шар";
                    case "cylinder" -> "цилиндр";
                    default -> "коробка";
                });
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                tab.snapshot();
                int dy = e.getKey() == GLFW.GLFW_KEY_UP ? step : -step;
                d.y1 += dy;
                d.y2 += dy;
                d.cy += dy;
                tab.edited(true);
                say("Высота: " + (int) Math.floor(d.minY()));
            }
            case GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET -> {
                tab.snapshot();
                int s = e.getKey() == GLFW.GLFW_KEY_RIGHT_BRACKET ? step : -step;
                if (d.shape.equals("box")) {
                    // grow / shrink the box on every side
                    order(d);
                    if (d.x2 - d.x1 + 2 * s >= 0 && d.z2 - d.z1 + 2 * s >= 0) {
                        d.x1 -= s;
                        d.x2 += s;
                        d.z1 -= s;
                        d.z2 += s;
                    }
                } else d.radius = Math.max(0.5, d.radius + s);
                tab.edited(true);
                say(d.size());
            }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL -> {
                if (!d.shape.equals("cylinder") && !d.shape.equals("box")) return;
                tab.snapshot();
                int s = e.getKey() == GLFW.GLFW_KEY_EQUAL ? step : -step;
                if (d.shape.equals("box")) d.y2 = Math.max(d.y1, d.y2 + s);
                else d.height = Math.max(0.5, d.height + s);
                tab.edited(true);
                say(d.size());
            }
            case GLFW.GLFW_KEY_Z -> {
                if (ctrl && tab.undoZone()) say("Отменено");
            }
            case GLFW.GLFW_KEY_C -> {
                // bring the camera in front of the zone
                Vec3 c = d.center();
                double size = Math.max(d.maxX() - d.minX(), d.maxZ() - d.minZ());
                CutsceneDoc.Pose p = new CutsceneDoc.Pose();
                p.x = c.x;
                p.y = d.maxY() + size * 0.5 + 3;
                p.z = d.maxZ() + size * 0.6 + 3;
                Vec3 to = c.subtract(p.x, p.y, p.z);
                p.yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                p.pitch = (float) Math.toDegrees(-Math.atan2(to.y, Math.hypot(to.x, to.z)));
                CameraDirector.flyTo(p);
                say("К зоне");
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                tab.saveNow();
                say("Сохранено");
            }
            default -> {
            }
        }
    }

    public static void onScroll(ClientHooks.MouseScroll e) {
        if (tab == null || Minecraft.getInstance().screen != null) return;
        CameraDirector.flySpeed = Math.max(0.5, Math.min(64, CameraDirector.flySpeed * (e.getScrollDelta() > 0 ? 1.25 : 0.8)));
        say("Скорость полёта: " + Math.round(CameraDirector.flySpeed * 10) / 10.0);
        e.setCanceled(true);
    }

    // ================================================================== drawing

    public static void onRenderLevel(ClientHooks.RenderLevel e) {
        if (tab == null || e.getStage() != ClientHooks.LevelStage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ZoneDef d = tab.doc();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose last = ps.last();
        for (Handle h : handles(d)) {
            boolean on = h.id().equals(hover);
            double s = Math.max(0.15, h.at().distanceTo(cam) * 0.012) * (on ? 1.6 : 1);
            float r = on ? 1 : 0.95f, g = on ? 0.88f : 0.95f, b = on ? 0.3f : 0.95f;
            cube(vc, last, h.at(), s, r, g, b);
            // the axis it moves along while held
            if (h.id().equals(grabbed)) {
                Vec3 a = h.at().subtract(h.axis().scale(64)), z = h.at().add(h.axis().scale(64));
                line(vc, last, a.x, a.y, a.z, z.x, z.y, z.z, 1, 0.85f, 0.3f, 0.6f);
            }
        }
        // the block the right click would use
        BlockHitResult hit = grabbed == null && !carry ? aimBlock() : null;
        if (hit != null) {
            BlockPos b = hit.getBlockPos();
            boolean box = d.shape.equals("box");
            BlockPos at = box ? b : b.relative(hit.getDirection());
            float r = nextCorner == 1 ? 0.3f : 1f, g = 1f, bl = nextCorner == 1 ? 0.6f : 0.4f;
            blockBox(vc, last, at.getX(), at.getY(), at.getZ(), r, g, bl);
            // the box being drawn from corner 1 to the aim
            if (box && nextCorner == 2) {
                double x0 = Math.min(d.x1, b.getX()), y0 = Math.min(d.y1, b.getY()), z0 = Math.min(d.z1, b.getZ());
                double x1 = Math.max(d.x1, b.getX()) + 1, y1 = Math.max(Math.max(d.y1, b.getY()), d.y1 + 1) + 1, z1 = Math.max(d.z1, b.getZ()) + 1;
                box(vc, last, x0, y0, z0, x1, y1, z1, 1, 1, 0.4f, 0.8f);
            }
        }
        ps.popPose();
        buf.endBatch(RenderType.lines());
    }

    private static void cube(VertexConsumer vc, PoseStack.Pose p, Vec3 c, double s, float r, float g, float b) {
        box(vc, p, c.x - s, c.y - s, c.z - s, c.x + s, c.y + s, c.z + s, r, g, b, 1);
    }

    private static void blockBox(VertexConsumer vc, PoseStack.Pose p, int x, int y, int z, float r, float g, float b) {
        box(vc, p, x - 0.005, y - 0.005, z - 0.005, x + 1.005, y + 1.005, z + 1.005, r, g, b, 1);
    }

    private static void box(VertexConsumer vc, PoseStack.Pose p, double x0, double y0, double z0, double x1, double y1, double z1,
                            float r, float g, float b, float a) {
        double[][] e = {
                {x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        for (double[] l : e) line(vc, p, l[0], l[1], l[2], l[3], l[4], l[5], r, g, b, a);
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

    public static void onGui(ClientHooks.RenderGui e) {
        if (tab == null) return;
        Minecraft mc = Minecraft.getInstance();
        var g = e.getGuiGraphics();
        ZoneDef d = tab.doc();
        String back = ClientEvents.OPEN_EDITOR.getTranslatedKeyMessage().getString();
        String shapeTip = d.shape.equals("box") ? "ПКМ по блоку — угол " + nextCorner + " (по очереди 1 и 2)" : "ПКМ по блоку — центр";
        String[] lines = {
                "ЗОНА «" + tab.title() + "» — " + d.size() + (tab.isDirty() ? "  •  не сохранено" : ""),
                "WASD, пробел, Shift — полёт · Ctrl — быстрее · колёсико — скорость (" + Math.round(CameraDirector.flySpeed * 10) / 10.0 + ")",
                shapeTip + " · ЛКМ по метке — тянуть (ещё ЛКМ — положить) · G — перенести часть",
                "T — форма · [ ] — шире/уже (Shift — по 5) · − = — ниже/выше" + " · ↑ ↓ — поднять/опустить",
                "ЧАСТЬ " + (d.editIndex + 1) + " ИЗ " + d.pieces() + " · B — новая часть · Tab — следующая · Delete — убрать эту",
                "C — к зоне · Ctrl+Z — отменить · Enter — сохранить · [" + back + "] — в редактор"};
        int y = 6;
        for (int i = 0; i < lines.length; i++) {
            int w = mc.font.width(lines[i]);
            g.fill(4, y - 2, 10 + w, y + 9, 0x99000000);
            g.drawString(mc.font, lines[i], 7, y, i == 0 ? (d.color | 0xFF000000) : 0xFFE0E0E0, false);
            y += 12;
        }
        int sw = e.getWindow().getGuiScaledWidth(), sh = e.getWindow().getGuiScaledHeight();
        // crosshair
        g.fill(sw / 2 - 4, sh / 2, sw / 2 + 5, sh / 2 + 1, 0xCCFFFFFF);
        g.fill(sw / 2, sh / 2 - 4, sw / 2 + 1, sh / 2 + 5, 0xCCFFFFFF);
        String info = d.shape.equals("box")
                ? "от " + Math.min(d.x1, d.x2) + " " + Math.min(d.y1, d.y2) + " " + Math.min(d.z1, d.z2) + "  до  " + Math.max(d.x1, d.x2) + " " + Math.max(d.y1, d.y2) + " " + Math.max(d.z1, d.z2)
                : "центр " + fmt(d.cx) + " " + fmt(d.cy) + " " + fmt(d.cz);
        if (hover != null) info += "   ·   метка " + hoverName(hover);
        if (carry) info += "   ·   НЕСЁТСЯ";
        int w = mc.font.width(info);
        g.fill((sw - w) / 2 - 4, sh - 30, (sw + w) / 2 + 4, sh - 18, 0x99000000);
        g.drawString(mc.font, info, (sw - w) / 2, sh - 28, grabbed != null || carry ? 0xFF66FF99 : 0xFFFFFFFF, false);
        if (!toast.isEmpty() && System.currentTimeMillis() - toastAt < 2000) {
            int tw = mc.font.width(toast);
            g.fill((sw - tw) / 2 - 6, 86, (sw + tw) / 2 + 6, 100, 0xAA000000);
            g.drawString(mc.font, toast, (sw - tw) / 2, 89, 0xFFFFFFFF, false);
        }
    }

    private static String hoverName(String id) {
        return switch (id) {
            case "-x" -> "запад";
            case "+x" -> "восток";
            case "-y" -> "пол";
            case "+y" -> "потолок";
            case "-z" -> "север";
            case "+z" -> "юг";
            case "r", "ry" -> "радиус";
            case "h" -> "высота";
            default -> "центр по высоте";
        };
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }
}
