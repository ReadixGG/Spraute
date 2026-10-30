package org.sprautestudio.st_addon_spraute.zone.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.engine.EngineConfig;
import org.sprautestudio.st_addon_spraute.zone.ZoneDef;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The player's side of the zones: the zone's name shown big when they come in, and — in the editor
 * mode — the outlines of the project's zones in the world, so the author sees where they are.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class ZoneClient {
    private ZoneClient() {
    }

    // ------------------------------------------------------------------ the title

    private static String title = "", subtitle = "";
    private static long titleAt;

    public static void showTitle(String t, String sub) {
        title = t;
        subtitle = sub;
        titleAt = System.currentTimeMillis();
        var lay = org.sprautestudio.st_addon_spraute.engine.client.EngineClient.window("zone");
        if (lay != null) lay.restartAppear();
        String snd = org.sprautestudio.st_addon_spraute.engine.client.EngineClient.config().zoneTitle.sound;
        org.sprautestudio.st_addon_spraute.engine.client.ProjectSounds.ui(org.sprautestudio.st_addon_spraute.engine.client.EngineClient.config().project, snd);
    }

    // ------------------------------------------------------------------ walls you bump into

    private static Vec3 lastPos;

    /**
     * A zone wall stops the player like a real wall: the step that would cross it is undone on the
     * axis that crosses (so you slide along it), before the frame is drawn. A teleport goes through.
     */
    @SubscribeEvent
    public static void wallTick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || e.player != mc.player || mc.player == null || mc.level == null) return;
        var p = mc.player;
        Vec3 now = p.position();
        if (lastPos == null || lastPos.distanceToSqr(now) > 9 || p.isSpectator()) {
            lastPos = now;
            return;
        }
        String dim = mc.level.dimension().location().toString();
        for (ZoneDef d : shown.values()) {
            if (d.wall.equals("none") || (!d.dimension.isEmpty() && !d.dimension.equals(dim))) continue;
            boolean keepIn = d.wall.equals("in");
            boolean was = d.holds(lastPos.x, lastPos.y, lastPos.z), in = d.holds(now.x, now.y, now.z);
            if (keepIn ? !(was && !in) : !(!was && in)) continue;
            // undo the crossing on as few axes as possible: slide along the wall
            Vec3[] tries = {new Vec3(lastPos.x, now.y, now.z), new Vec3(now.x, now.y, lastPos.z), new Vec3(lastPos.x, now.y, lastPos.z),
                    new Vec3(now.x, lastPos.y, now.z), lastPos};
            Vec3 fix = lastPos;
            for (Vec3 t : tries) {
                if (d.holds(t.x, t.y, t.z) == keepIn) {
                    fix = t;
                    break;
                }
            }
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(fix.x != now.x ? 0 : v.x, fix.y != now.y ? Math.min(0, v.y) : v.y, fix.z != now.z ? 0 : v.z);
            p.setPos(fix.x, fix.y, fix.z);
            now = fix;
        }
        lastPos = now;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        lastPos = null;
        titleAt = 0;
        shown = new LinkedHashMap<>();
        org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.CLIENT.clear();
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post e) {
        if (titleAt == 0) return;
        EngineConfig cfg = org.sprautestudio.st_addon_spraute.engine.client.EngineClient.config();
        long t = System.currentTimeMillis() - titleAt;
        if (t > cfg.zoneTitle.seconds * 1000) {
            titleAt = 0;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        var lay = org.sprautestudio.st_addon_spraute.engine.client.EngineClient.window("zone");
        if (lay != null) {
            // the title from the interface editor: its appear animations, faded out at the end
            float total = Math.max(0.5f, cfg.zoneTitle.seconds) * 1000;
            lay.vars.put("zone", title);
            lay.vars.put("subtitle", subtitle);
            lay.opacity = Math.min(1, (total - t) / Math.min(900, total * 0.25f));
            org.sprautestudio.st_addon_spraute.engine.client.EngineClient.drawWindow(g, lay, -10000, -10000);
            return;
        }
        drawTitle(g, mc.font, g.guiWidth(), g.guiHeight(), cfg.zoneTitle, cfg.project, title, subtitle, t);
    }

    /** The zone's name as the engine settings draw it, t ms after it appeared (also the editor's preview). */
    public static void drawTitle(GuiGraphics g, Font font, int sw, int sh, EngineConfig.ZoneTitle c, String project, String title, String subtitle, long t) {
        float total = Math.max(0.5f, c.seconds) * 1000;
        float fadeIn = Math.min(700, total * 0.2f), fadeOut = Math.min(900, total * 0.25f);
        float a = Math.min(Math.min(1, t / fadeIn), Math.min(1, (total - t) / fadeOut));
        if (a <= 0.02f) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int alpha = Math.max(4, (int) (a * 255)) << 24;
        Component big = c.bold ? Component.literal(title).withStyle(ChatFormatting.BOLD) : Component.literal(title);
        float scale = Math.min(c.scale, (sw - 40) / (float) Math.max(1, font.width(big)));
        float cx = sw * c.x, cy = sh * c.y;
        float grow = Math.min(1, t / 900f);
        float spread = c.spread ? (1 - grow) * 0.15f + 1 : 1;
        float tw = font.width(big) * scale, th = font.lineHeight * scale;
        float subW = subtitle.isEmpty() ? 0 : font.width(subtitle) * c.subScale;
        float bottom = cy + th / 2 + 4 + (subtitle.isEmpty() ? 0 : 6 + font.lineHeight * c.subScale);
        // the plate behind
        if (c.plate) {
            int pw = (int) (Math.max(tw, subW) + c.padding * 2), top = (int) (cy - th / 2 - c.padding), bot = (int) (bottom + c.padding);
            int px = (int) (cx - pw / 2f);
            if (c.plateSkin.isSet()) {
                RenderSystem.setShaderColor(1, 1, 1, a);
                org.sprautestudio.st_addon_spraute.engine.client.Skins.draw(g, project, c.plateSkin, px, top, pw, bot - top);
                RenderSystem.setShaderColor(1, 1, 1, 1);
            } else {
                int pa = (int) (((c.plateColor >>> 24) & 0xFF) * a);
                g.fill(px, top, px + pw, bot, (pa << 24) | (c.plateColor & 0xFFFFFF));
            }
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale * spread, scale, 1);
        g.drawString(font, big, -font.width(big) / 2, -font.lineHeight / 2, alpha | (c.titleColor & 0xFFFFFF), c.shadow);
        g.pose().popPose();
        int lineY = (int) (cy + th / 2 + 4);
        if (c.lines) {
            // thin lines that grow from the middle
            int lw = (int) (grow * Math.min(sw * 0.35f, tw * 0.7f + 30));
            int la = (int) (a * ((c.lineColor >>> 24) & 0xFF)) << 24;
            int lc = c.lineColor & 0xFFFFFF;
            g.fillGradient((int) cx - lw, lineY, (int) cx, lineY + 1, lc, la | lc);
            g.fillGradient((int) cx, lineY, (int) cx + lw, lineY + 1, la | lc, lc);
        }
        if (!subtitle.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(cx, lineY + 6, 0);
            g.pose().scale(c.subScale, c.subScale, 1);
            g.drawString(font, subtitle, -font.width(subtitle) / 2, 0, alpha | (c.subColor & 0xFFFFFF), c.shadow);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ outlines in the world (editor mode)

    /** The open project's zones: name -> JSON (set by the editor). */
    public static Supplier<Map<String, String>> projectZones = Map::of;
    /** Zones being edited now (their unsaved shape wins), and the one selected. */
    public static final Map<String, ZoneDef> LIVE = new LinkedHashMap<>();
    public static String highlighted = "";

    private static Map<String, ZoneDef> cached = new LinkedHashMap<>();
    private static long cachedAt;

    /** Zones the server shows to everybody ("Скрывать в игре" off): project/name -> definition. */
    private static Map<String, ZoneDef> shown = new LinkedHashMap<>();

    public static void receiveShown(String json) {
        Map<String, ZoneDef> m = new LinkedHashMap<>();
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            for (var e : o.entrySet()) m.put(e.getKey(), ZoneDef.fromJson(e.getValue().toString()));
        } catch (Exception ignored) {
        }
        shown = m;
    }

    private static Map<String, ZoneDef> zones() {
        if (!org.sprautestudio.st_addon_spraute.editor.zone.ZoneWorldEditor.active()) {
            Map<String, ZoneDef> vis = new LinkedHashMap<>();
            for (Map.Entry<String, ZoneDef> e : shown.entrySet()) if (!e.getValue().hideInGame) vis.put(e.getKey(), e.getValue());
            return vis;
        }
        long now = System.currentTimeMillis();
        if (now - cachedAt > 1000) {
            cachedAt = now;
            Map<String, ZoneDef> m = new LinkedHashMap<>();
            try {
                for (Map.Entry<String, String> e : projectZones.get().entrySet()) m.put(e.getKey(), ZoneDef.fromJson(e.getValue()));
            } catch (Exception ignored) {
            }
            cached = m;
        }
        Map<String, ZoneDef> out = new LinkedHashMap<>(cached);
        out.putAll(LIVE);
        return out;
    }

    public static boolean visible() {
        if (org.sprautestudio.st_addon_spraute.editor.zone.ZoneWorldEditor.active()) return true;
        for (ZoneDef d : shown.values()) if (!d.hideInGame) return true;
        return false;
    }

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !visible()) return;
        boolean editing = org.sprautestudio.st_addon_spraute.editor.zone.ZoneWorldEditor.active();
        if (!editing && Minecraft.getInstance().options.hideGui) return;
        Map<String, ZoneDef> zones = zones();
        if (zones.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        String dim = mc.level.dimension().location().toString();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose last = ps.last();
        for (Map.Entry<String, ZoneDef> en : zones.entrySet()) {
            ZoneDef d = en.getValue();
            if (!d.dimension.isEmpty() && !d.dimension.equals(dim)) continue;
            if (d.center().distanceTo(cam) > 160 + Math.max(d.uMaxX() - d.uMinX(), d.uMaxZ() - d.uMinZ())) continue;
            boolean hl = en.getKey().equals(highlighted);
            int c = d.color;
            float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f, a = d.enabled ? 1f : 0.4f;
            if (hl) {
                float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 200.0);
                r = Math.min(1, r * pulse + 0.3f);
                g = Math.min(1, g * pulse + 0.3f);
                b = Math.min(1, b * pulse + 0.3f);
            }
            outline(vc, last, d.primary(), r, g, b, a);
            // the other pieces: a bit fainter while editing (the bright one is being edited)
            for (org.sprautestudio.st_addon_spraute.zone.ZoneShape sh : d.more) outline(vc, last, sh, r, g, b, editing ? a * 0.55f : a);
        }
        ps.popPose();
        buf.endBatch(RenderType.lines());
        // names above the zones
        Font font = mc.font;
        for (Map.Entry<String, ZoneDef> en : zones.entrySet()) {
            ZoneDef d = en.getValue();
            if (!d.dimension.isEmpty() && !d.dimension.equals(dim)) continue;
            Vec3 top = new Vec3((d.uMinX() + d.uMaxX()) / 2, d.uMaxY() + 0.4, (d.uMinZ() + d.uMaxZ()) / 2);
            if (top.distanceTo(cam) > 96) continue;
            Vec3 p = top.subtract(cam);
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            float s = 0.03f * (float) Math.max(1, top.distanceTo(cam) / 12);
            ps.scale(-s, -s, s);
            // while playing: the zone's title only (none — no label)
            if (!editing && d.title.isEmpty()) {
                ps.popPose();
                continue;
            }
            String label = !editing ? d.title
                    : "◆ " + en.getKey() + (d.enabled ? "" : "  (выкл)") + (d.wall.equals("none") ? "" : d.wall.equals("in") ? "  [не выйти]" : "  [не войти]")
                    + (d.hideInGame ? "" : "  [видна в игре]");
            Matrix4f m = ps.last().pose();
            int col = en.getKey().equals(highlighted) ? 0xFFFFE07A : (0xFF000000 | d.color);
            font.drawInBatch(label, -font.width(label) / 2f, 0, col, false, m, buf, Font.DisplayMode.SEE_THROUGH, 0x66000000, 0xF000F0);
            ps.popPose();
        }
        buf.endBatch();
    }

    private static void outline(VertexConsumer vc, PoseStack.Pose p, org.sprautestudio.st_addon_spraute.zone.ZoneShape d, float r, float g, float b, float a) {
        switch (d.shape) {
            case "sphere" -> {
                int n = 48;
                for (int ring = 0; ring < 3; ring++) circle(vc, p, d.cx, d.cy, d.cz, d.radius, ring, n, r, g, b, a);
                // two more latitude rings so it reads as a ball
                for (double lat : new double[]{-0.6, 0.6}) {
                    double rr = d.radius * Math.sqrt(1 - lat * lat);
                    circle(vc, p, d.cx, d.cy + d.radius * lat, d.cz, rr, 0, n, r, g, b, a * 0.6f);
                }
            }
            case "cylinder" -> {
                int n = 48;
                circle(vc, p, d.cx, d.cy, d.cz, d.radius, 0, n, r, g, b, a);
                circle(vc, p, d.cx, d.cy + d.height, d.cz, d.radius, 0, n, r, g, b, a);
                for (int i = 0; i < 8; i++) {
                    double ang = Math.PI * 2 * i / 8;
                    double x = d.cx + Math.cos(ang) * d.radius, z = d.cz + Math.sin(ang) * d.radius;
                    line(vc, p, x, d.cy, z, x, d.cy + d.height, z, r, g, b, a);
                }
            }
            default -> {
                double x0 = d.minX(), y0 = d.minY(), z0 = d.minZ(), x1 = d.maxX(), y1 = d.maxY(), z1 = d.maxZ();
                line(vc, p, x0, y0, z0, x1, y0, z0, r, g, b, a);
                line(vc, p, x1, y0, z0, x1, y0, z1, r, g, b, a);
                line(vc, p, x1, y0, z1, x0, y0, z1, r, g, b, a);
                line(vc, p, x0, y0, z1, x0, y0, z0, r, g, b, a);
                line(vc, p, x0, y1, z0, x1, y1, z0, r, g, b, a);
                line(vc, p, x1, y1, z0, x1, y1, z1, r, g, b, a);
                line(vc, p, x1, y1, z1, x0, y1, z1, r, g, b, a);
                line(vc, p, x0, y1, z1, x0, y1, z0, r, g, b, a);
                line(vc, p, x0, y0, z0, x0, y1, z0, r, g, b, a);
                line(vc, p, x1, y0, z0, x1, y1, z0, r, g, b, a);
                line(vc, p, x1, y0, z1, x1, y1, z1, r, g, b, a);
                line(vc, p, x0, y0, z1, x0, y1, z1, r, g, b, a);
                // a faint grid on the floor every 4 blocks
                for (double x = Math.ceil(x0 / 4) * 4; x < x1; x += 4) line(vc, p, x, y0 + 0.02, z0, x, y0 + 0.02, z1, r, g, b, a * 0.25f);
                for (double z = Math.ceil(z0 / 4) * 4; z < z1; z += 4) line(vc, p, x0, y0 + 0.02, z, x1, y0 + 0.02, z, r, g, b, a * 0.25f);
            }
        }
    }

    /** A circle: axis 0 — flat (XZ), 1 — XY, 2 — ZY. */
    private static void circle(VertexConsumer vc, PoseStack.Pose p, double cx, double cy, double cz, double rad, int axis, int n,
                               float r, float g, float b, float a) {
        double px = 0, py = 0, pz = 0;
        for (int i = 0; i <= n; i++) {
            double ang = Math.PI * 2 * i / n, u = Math.cos(ang) * rad, v = Math.sin(ang) * rad;
            double x = cx + (axis == 2 ? 0 : u), y = cy + (axis == 0 ? 0 : v), z = cz + (axis == 0 ? v : axis == 2 ? u : 0);
            if (i > 0) line(vc, p, px, py, pz, x, y, z, r, g, b, a);
            px = x;
            py = y;
            pz = z;
        }
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
}
