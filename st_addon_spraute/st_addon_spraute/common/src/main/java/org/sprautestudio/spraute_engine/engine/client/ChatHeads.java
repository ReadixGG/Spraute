package org.sprautestudio.spraute_engine.engine.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.runtime.Images;
import org.sprautestudio.spraute_engine.platform.ClientHooks;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Speech icons showing the speaker's head: a still copy of the character's model looking straight
 * ahead with only one bone (the head, with what hangs on it) drawn, framed on that bone and clipped
 * to the icon; the player gets the face of their skin.
 * Icons: {@code spraute/head/s<script>/<character>/<scale%>/<dx%>/<dy%>/<yaw>/<bone>} and
 * {@code spraute/head/player/<scale%>/<dx%>/<dy%>/<yaw>}.
 */
public final class ChatHeads {
    private ChatHeads() {}

    /** How the head sits in its frame. dx/dy shift it by percent of the frame, yaw turns it. */
    public record Params(float scale, int dx, int dy, int yaw, String bone) {
        public static final Params DEFAULT = new Params(1, 0, 0, 0, "auto");
    }

    private static final String PREFIX = "textures/gui/chat/spraute/head/";
    private static final ResourceLocation FALLBACK = new ResourceLocation(org.sprautestudio.spraute_engine.SprauteEngine.MODID, "textures/gui/chat/generic_man.png");
    /** Who a head icon shows: the entity (-1 — not in the world) and its Spraute model ("" — none). */
    private record Bind(int entity, String model) {
    }

    /** "s<script>/<character>" -> the body, sent by the server when the character speaks. */
    private static final Map<String, Bind> ENTITIES = new HashMap<>();
    /**
     * "s<script>/<character>" -> the still copy last drawn for it: the head stays when the character is
     * gone, far away (not loaded here) or not spawned yet.
     */
    private static final Map<String, LivingEntity> LAST = new HashMap<>();

    public static void bind(int script, String character, int entityId, String model) {
        ENTITIES.put("s" + script + "/" + character, new Bind(entityId, model == null ? "" : model));
    }

    /** The copy to draw for a character whose body is not here: the last one seen, else one built from its model. */
    private static LivingEntity remembered(Minecraft mc, String key) {
        LivingEntity c = LAST.get(key);
        if (c != null && c.level() == mc.level) return c;
        Bind b = ENTITIES.get(key);
        if (b == null || b.model().isEmpty() || mc.level == null) return null;
        try {
            var made = org.sprautestudio.spraute_engine.runtime.orb.Orbs.MODEL.get().create(mc.level);
            if (made == null) return null;
            made.setModel(b.model());
            still(made);
            LAST.put(key, made);
            return made;
        } catch (Throwable t) {
            return null;
        }
    }

    public static final class Setup {
        public static void registerHooks() {
            ClientHooks.CLIENT_SETUP.add(Setup::clientSetup);
        }

        public static void clientSetup(ClientHooks.ClientSetup e) {
            var ws = org.sprautestudio.spraute_engine.editor.project.Workspace.settings();
            if (ws.mcp) org.sprautestudio.spraute_engine.mcp.McpServer.start(ws.mcpPort);
        }
    }

    private static int part(String[] p, int i, int def) {
        try {
            return i < p.length ? Integer.parseInt(p[i]) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** A speech icon: a head drawn from the speaker's model, or a picture. */
    public static void drawIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        if (!draw(g, tex, x, y, w, h)) {
            RenderSystem.enableBlend();
            g.blit(tex, x, y, 0, 0, w, h, w, h);
        }
    }

    private static boolean draw(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        if (!tex.getNamespace().equals(SprauteEngine.MODID) || !tex.getPath().startsWith(PREFIX)) return false;
        String[] p = tex.getPath().substring(PREFIX.length()).replace(".png", "").split("/");
        boolean player = p[0].equals("player");
        int base = player ? 1 : 2;
        Params params = new Params(Math.max(0.2f, Math.min(8, part(p, base, 100) / 100f)), part(p, base + 1, 0), part(p, base + 2, 0),
                part(p, base + 3, 0), base + 4 < p.length ? p[base + 4] : "auto");
        float alpha = RenderSystem.getShaderColor()[3];
        if (alpha < 0.05f) return true;
        Minecraft mc = Minecraft.getInstance();
        Entity en = null;
        String key = p.length >= 2 ? p[0] + "/" + p[1] : "";
        if (player) en = mc.player;
        else if (p.length >= 2 && mc.level != null) {
            Bind b = ENTITIES.get(key);
            if (b != null && b.entity() >= 0) en = mc.level.getEntity(b.entity());
        }
        int[] clip = screenRect(g, x, y, w, h);
        g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
        try {
            if (en instanceof AbstractClientPlayer pl) face(g, pl.getSkinTextureLocation(), x, y, w, h, params);
            else if (en instanceof LivingEntity le) {
                LivingEntity copy = copyOf(le);
                still(copy);
                if (!key.isEmpty()) LAST.put(key, copy);
                render(g, copy, x, y, w, h, params);
            } else {
                LivingEntity copy = player ? null : remembered(mc, key);
                if (copy != null) {
                    still(copy);
                    render(g, copy, x, y, w, h, params);
                } else g.blit(FALLBACK, x, y, 0, 0, w, h, w, h);
            }
        } finally {
            g.disableScissor();
        }
        return true;
    }

    /** The icon rectangle after the overlay's own transform, for the scissor. */
    public static int[] screenRect(GuiGraphics g, int x, int y, int w, int h) {
        var m = g.pose().last().pose();
        Vector3f a = m.transformPosition(new Vector3f(x, y, 0)), b = m.transformPosition(new Vector3f(x + w, y + h, 0));
        return new int[]{(int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y))};
    }

    /** The 8x8 face of a skin and its hat layer. */
    public static void face(GuiGraphics g, ResourceLocation skin, int x, int y, int w, int h, Params p) {
        int s = Math.round(Math.min(w, h) * p.scale());
        int fx = x + (w - s) / 2 + Math.round(w * p.dx() / 100f), fy = y + (h - s) / 2 + Math.round(h * p.dy() / 100f);
        RenderSystem.enableBlend();
        g.blit(skin, fx, fy, s, s, 8, 8, 8, 8, 64, 64);
        // the second layer stands out of the head (9/8 of it), as on the player's model
        float hs = s * 9 / 8f, off = (hs - s) / 2f;
        g.pose().pushPose();
        g.pose().translate(fx - off, fy - off, 0);
        g.pose().scale(hs / s, hs / s, 1);
        g.blit(skin, 0, 0, s, s, 40, 8, 8, 8, 64, 64);
        g.pose().popPose();
    }

    // ================================================================== still copies of models

    /** Still copies of the speakers' models (no animations), by the id of the live entity. */
    private static final Map<Integer, LivingEntity> COPIES = new HashMap<>();
    private static final Map<EntityType<?>, LivingEntity> BY_TYPE = new HashMap<>();

    private static LivingEntity copyOf(LivingEntity live) {
        LivingEntity c = COPIES.get(live.getId());
        if (c != null && c.getType() == live.getType() && c.level() == live.level()) {
            // a Spraute entity gets its model a moment after it appears: the copy follows it
            if (c instanceof org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity cm
                    && live instanceof org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity lm && !cm.model().equals(lm.model())) {
                cm.setModel(lm.model());
            }
            return c;
        }
        Entity made;
        try {
            made = live.getType().create(live.level());
        } catch (Throwable t) {
            made = null;
        }
        if (!(made instanceof LivingEntity le)) return live;
        try {
            // same look (skin, variant), none of the live state
            net.minecraft.nbt.CompoundTag tag = live.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            tag.remove("UUID");
            tag.remove("Motion");
            tag.remove("Rotation");
            le.load(tag);
        } catch (Throwable ignored) {
        }
        still(le);
        COPIES.put(live.getId(), le);
        return le;
    }

    private static final Map<String, LivingEntity> BY_SPRAUTE = new HashMap<>();

    /** A still model of a project entity type ("spraute:Пекарь"), for previews. */
    public static LivingEntity ofSprauteType(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var t = org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.find(null, id);
        String key;
        if (id.equals("spraute:npc")) key = org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.BUILTIN;
        else if (t != null) key = t.key();
        else {
            var m = org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.find(null, id.substring(8));
            if (m == null) return null;
            key = m.key();
        }
        LivingEntity c = BY_SPRAUTE.get(key);
        if (c != null && c.level() == mc.level) return c;
        try {
            Entity e = org.sprautestudio.spraute_engine.runtime.orb.Orbs.MODEL.get().create(mc.level);
            if (!(e instanceof org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity me)) return null;
            me.setModel(key);
            still(me);
            BY_SPRAUTE.put(key, me);
            return me;
        } catch (Throwable ex) {
            return null;
        }
    }

    /** A still model of an entity type, for previews. */
    public static LivingEntity ofType(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || type == null) return null;
        LivingEntity c = BY_TYPE.get(type);
        if (c != null && c.level() == mc.level) return c;
        try {
            Entity e = type.create(mc.level);
            if (!(e instanceof LivingEntity le)) return null;
            still(le);
            BY_TYPE.put(type, le);
            return le;
        } catch (Throwable t) {
            return null;
        }
    }

    /** A portrait stands still: model entities are drawn in the rest pose, vanilla ones at rest. */
    private static void still(LivingEntity le) {
        le.tickCount = 0;
        le.walkAnimation.setSpeed(0);
    }

    // ================================================================== bones

    /** The Bedrock model of an entity, or null. */
    private static org.sprautestudio.spraute_engine.core.model.SpGeoModel geometry(LivingEntity e) {
        if (!(e instanceof org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity me)) return null;
        var look = me.look();
        return look != null ? look.geometry() : null;
    }

    /** Named parts of a vanilla model (its ModelPart fields: head, hat, body...). */
    private static Map<String, ModelPart> vanillaParts(LivingEntity e) {
        Map<String, ModelPart> out = new java.util.LinkedHashMap<>();
        try {
            var r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
            if (!(r instanceof LivingEntityRenderer<?, ?> lr)) return out;
            EntityModel<?> m = lr.getModel();
            for (Class<?> c = m.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() != ModelPart.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    ModelPart part = (ModelPart) f.get(m);
                    if (part != null && !out.containsValue(part)) out.put(f.getName(), part);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Bone names that can be shown alone, the head-like ones first. */
    public static List<String> bones(LivingEntity e) {
        List<String> names = new ArrayList<>();
        var gm = geometry(e);
        if (gm != null) names.addAll(gm.boneMap.keySet());
        else names.addAll(vanillaParts(e).keySet());
        names.sort((a, b) -> Boolean.compare(!a.toLowerCase().contains("head"), !b.toLowerCase().contains("head")));
        return names;
    }

    /** The bone to show for a setting: "all", a name (matched loosely), or "auto" — the head. */
    private static String pick(List<String> names, String want) {
        if (want == null || want.isEmpty() || want.equals("auto")) {
            for (String n : names) if (n.equalsIgnoreCase("head")) return n;
            for (String n : names) if (n.toLowerCase().contains("head") && !n.toLowerCase().contains("wear")) return n;
            return null;
        }
        if (want.equals("all")) return null;
        for (String n : names) if (n.equals(want) || Images.slug(n).equals(want)) return n;
        return null;
    }

    // ================================================================== drawing a model

    /**
     * Draws a still model in a frame: framed on the chosen bone (shown alone) or the eyes, turned by
     * {@code yaw}, zoomed and shifted by the params. The caller clips to the frame.
     */
    public static void render(GuiGraphics g, LivingEntity e, int x, int y, int w, int h, Params p) {
        if (e == null) return;
        int size = Math.min(w, h);
        // pixels per block: a head (half a block) fills most of the frame at scale 1
        float px = size * 1.6f * p.scale();
        float focus = e.getEyeHeight();
        // how far the framed part is from the middle of the model, sideways (blocks)
        float sideShift = 0;
        var gm = geometry(e);
        Map<Object, boolean[]> saved = new IdentityHashMap<>();
        String bone = null;
        // vanilla models: legs and arms at rest
        float walk = e.walkAnimation.speed();
        e.walkAnimation.setSpeed(0);
        if (gm != null) {
            bone = pick(new ArrayList<>(gm.boneMap.keySet()), p.bone());
            if (bone != null) {
                // frame what the bone really is: the middle of its cubes, and zoom so it fills the frame
                float[] bb = bounds(gm.boneMap.get(bone));
                float k = e instanceof org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity sme && sme.look() != null ? sme.look().scale() : 1;
                if (bb != null) {
                    focus = (bb[2] + bb[3]) / 2 * k;
                    sideShift = (bb[0] + bb[1]) / 2 * k;
                    float extent = Math.max(bb[1] - bb[0], bb[3] - bb[2]) * k;
                    if (extent > 0.05f) px = size * 0.8f / extent * p.scale();
                }
            }
        } else {
            // vanilla models: hide every other part
            Map<String, ModelPart> parts = vanillaParts(e);
            bone = pick(new ArrayList<>(parts.keySet()), p.bone());
            if (bone != null) {
                ModelPart keep = parts.get(bone);
                for (Map.Entry<String, ModelPart> en : parts.entrySet()) {
                    ModelPart mp = en.getValue();
                    saved.put(mp, new boolean[]{mp.visible});
                    boolean show = mp == keep || en.getKey().equals("hat") && bone.equals("head");
                    mp.visible = show;
                }
                focus = (24 - keep.y) / 16f + 0.25f;
            }
        }
        float cx = x + w / 2f + w * p.dx() / 100f - sideShift * px;
        float cy = y + h * 0.5f + h * p.dy() / 100f;
        float feetY = cy + focus * px;
        var ps = g.pose();
        g.flush();
        var outer = ps.last();
        ps.pushPose();
        try {
            ps.translate(cx, feetY, 200);
            ps.mulPoseMatrix(new Matrix4f().scaling(px, px, -px));
            ps.mulPose(new Quaternionf().rotateZ((float) Math.PI));
            ps.mulPose(Axis.YP.rotationDegrees(180 + p.yaw()));
            e.setYRot(0);
            e.yRotO = 0;
            e.setXRot(0);
            e.xRotO = 0;
            e.yBodyRot = e.yBodyRotO = 0;
            e.yHeadRot = e.yHeadRotO = 0;
            Lighting.setupForEntityInInventory();
            var d = Minecraft.getInstance().getEntityRenderDispatcher();
            d.setRenderShadow(false);
            String shown = bone;
            org.sprautestudio.spraute_engine.runtime.models.SprauteModelRenderer.withPortrait(shown,
                    () -> RenderSystem.runAsFancy(() -> d.render(e, 0, 0, 0, 0, 0, ps, g.bufferSource(), LightTexture.FULL_BRIGHT)));
            g.flush();
            d.setRenderShadow(true);
        } catch (Throwable t) {
            try {
                g.flush();
            } catch (Throwable ignored) {
            }
        } finally {
            for (int i = 0; i < 64 && ps.last() != outer; i++) ps.popPose();
            Lighting.setupFor3DItems();
            e.walkAnimation.setSpeed(walk);
            saved.forEach((k, v) -> {
                if (k instanceof ModelPart mp) mp.visible = v[0];
            });
        }
    }

    /**
     * The box of a bone's cubes and all under it, in the rest pose: {minX, maxX, minY, maxY} in blocks from the
     * feet; null when it has no cubes.
     */
    private static float[] bounds(org.sprautestudio.spraute_engine.core.model.SpBone top) {
        float[] b = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        java.util.ArrayDeque<org.sprautestudio.spraute_engine.core.model.SpBone> todo = new java.util.ArrayDeque<>();
        todo.push(top);
        boolean any = false;
        while (!todo.isEmpty()) {
            var bone = todo.pop();
            todo.addAll(bone.children);
            for (var c : bone.cubes) {
                any = true;
                b[0] = Math.min(b[0], -(c.origin.x + c.size.x) / 16f);
                b[1] = Math.max(b[1], -c.origin.x / 16f);
                b[2] = Math.min(b[2], c.origin.y / 16f);
                b[3] = Math.max(b[3], (c.origin.y + c.size.y) / 16f);
            }
        }
        return any ? b : null;
    }
}
