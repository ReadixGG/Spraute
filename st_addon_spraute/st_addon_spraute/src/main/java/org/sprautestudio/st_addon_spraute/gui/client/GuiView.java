package org.sprautestudio.st_addon_spraute.gui.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.gui.GuiDoc;
import org.sprautestudio.st_addon_spraute.gui.GuiDoc.Element;
import org.sprautestudio.st_addon_spraute.gui.GuiSchema;
import org.sprautestudio.st_addon_spraute.lang.Specs;
import org.sprautestudio.st_addon_spraute.runtime.Images;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a {@link GuiDoc} and (when interactive) lets the player use it: hover, buttons with
 * hotkeys, text inputs, tooltips. The editor uses the same class for its canvas.
 */
public class GuiView extends Widget implements UI.CursorProvider {
    public interface Listener {
        /** kind: "click" for buttons, "close" when the GUI asks to be closed. */
        void event(String elementId, String kind);
    }

    public GuiDoc doc;
    /** Project the pictures belong to. */
    public String project;
    /** Local project (editor) used to load pictures on demand; may be null at runtime. */
    public Project localProject;
    public Listener listener;
    public boolean interactive = true;
    /** Seconds since opening; the editor keeps it large so everything is fully shown. */
    protected long openedAt = System.nanoTime();
    public boolean playAppear = true;
    /** Fill the screen with the interface's background colour (off for overlays on top of the game). */
    public boolean drawBackground = true;

    // area: design units -> widget pixels
    protected float areaX, areaY, areaScale = 1, areaW, areaH;

    private final Map<String, EntityPreview> entities = new HashMap<>();
    private final Map<String, Float> hoverAnim = new HashMap<>();
    private final Map<String, Float> shownValue = new HashMap<>();
    private final Map<String, Long> textChangedAt = new HashMap<>();
    private final Map<String, String> inputs = new LinkedHashMap<>();
    private String focusedInput, pressedId, hoveredId;
    private int mouseX, mouseY;

    // tree layout (design units): element rect, the area it is anchored in, content size of scroll panels
    private final Map<Element, int[]> rects = new java.util.IdentityHashMap<>();
    private final Map<Element, float[]> areas = new java.util.IdentityHashMap<>();
    private final Map<Element, int[]> contents = new java.util.IdentityHashMap<>();
    /** Scroll state per scroll panel: {x, y, targetX, targetY}. */
    private final Map<String, float[]> scrolls = new HashMap<>();
    private String draggingBar;
    private boolean barVertical;
    /** Visible part of the design area, for culling. */
    private float viewX0, viewY0, viewX1, viewY1;

    /**
     * The player's HUD this view draws with (null — a sample, as in the editor). The engine's HUD layout
     * gives the live state; its parts then also hide when the game hides them (food in creative…).
     */
    public java.util.function.Supplier<org.sprautestudio.st_addon_spraute.engine.client.HudPainter.State> hudState;

    private static org.sprautestudio.st_addon_spraute.engine.client.HudPainter.State sample;

    /** {name}, {question}, {title}… of the engine window this view draws (a sample in the editor). */
    public final Map<String, String> vars = new java.util.HashMap<>();
    /** The answers of a choice menu (null — the editor's sample), the one under the mouse, and where they were drawn. */
    public List<String> answers;
    public int answerHover = -1;
    private final List<int[]> answerRects = new ArrayList<>();
    /** Who asks (choice menu): drawn by "speaker" elements. */
    public net.minecraft.world.entity.LivingEntity speaker;
    /** Whole view's opacity (a window fading in and out). */
    public float opacity = 1;
    /** What the live parts of an engine screen draw (the journal's or the shop's state); null — the editor's sample. */
    public Object liveState;

    /** The mouse in design units (for live parts' hover). */
    public float mouseDX() {
        return toDesignX(mouseX);
    }

    public float mouseDY() {
        return toDesignY(mouseY);
    }

    private static final Map<String, String> SAMPLE_VARS = Map.of("name", "Слепая Агата", "question", "Ты ведь слышал все тринадцать ударов?",
            "title", "Задача выполнена", "text", "Найти того, кто напевает на северной улице", "kind", "task",
            "zone", "Деревня Ольховка", "subtitle", "Тихое место у реки");

    /** Index of the answer drawn under a screen point, or -1. */
    public int answerAt(double sx, double sy) {
        float dx = toDesignX(sx), dy = toDesignY(sy);
        for (int i = 0; i < answerRects.size(); i++) {
            int[] r = answerRects.get(i);
            if (dx >= r[0] && dx < r[0] + r[2] && dy >= r[1] && dy < r[1] + r[3]) return i;
        }
        return -1;
    }

    private org.sprautestudio.st_addon_spraute.engine.client.HudPainter.State hud() {
        if (hudState != null) {
            var s = hudState.get();
            if (s != null) return s;
        }
        if (sample == null) {
            var s = new org.sprautestudio.st_addon_spraute.engine.client.HudPainter.State();
            s.hp = 14;
            s.armor = 8;
            s.food = 17;
            s.air = 180;
            s.exp = 0.45f;
            s.level = 7;
            s.coins = 1250;
            s.selected = 1;
            net.minecraft.world.item.ItemStack[] items = {new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 12),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH, 48),
                    net.minecraft.world.item.ItemStack.EMPTY, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MAP),
                    net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 3)};
            System.arraycopy(items, 0, s.hotbar, 0, 9);
            sample = s;
        }
        sample.stHealth = org.sprautestudio.st_addon_spraute.engine.client.EngineClient.config().health;
        return sample;
    }

    /** {hp}, {food}, {coins}… in texts: the player's values (a sample in the editor). */
    private String fillHud(String text) {
        if (text.indexOf('{') < 0) return text;
        var s = hud();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        var p = hudState != null ? mc.player : null;
        long day = mc.level != null ? mc.level.getDayTime() % 24000 : 6000;
        int hh = (int) ((day / 1000 + 6) % 24), mm = (int) (day % 1000 * 60 / 1000);
        for (String k : SAMPLE_VARS.keySet()) {
            String v = vars.get(k);
            if (v == null && hudState == null) v = SAMPLE_VARS.get(k);
            if (v != null) text = text.replace("{" + k + "}", v);
        }
        for (var en : vars.entrySet()) if (!SAMPLE_VARS.containsKey(en.getKey())) text = text.replace("{" + en.getKey() + "}", en.getValue());
        return text.replace("{hp}", String.valueOf(Math.round(s.hp))).replace("{maxhp}", String.valueOf(Math.round(s.maxHp)))
                .replace("{food}", String.valueOf(s.food)).replace("{armor}", String.valueOf(s.armor))
                .replace("{air}", String.valueOf(Math.max(0, s.air) * 10 / Math.max(1, s.maxAir)))
                .replace("{level}", String.valueOf(s.level)).replace("{exp}", String.valueOf(Math.round(s.exp * 100)))
                .replace("{coins}", String.valueOf(s.coins))
                .replace("{x}", p != null ? String.valueOf(p.getBlockX()) : "120").replace("{y}", p != null ? String.valueOf(p.getBlockY()) : "64")
                .replace("{z}", p != null ? String.valueOf(p.getBlockZ()) : "-35")
                .replace("{player}", p != null ? p.getGameProfile().getName() : "Игрок")
                .replace("{time}", String.format("%02d:%02d", hh, mm));
    }

    /** Share 0..1 of a player's value for a bar that shows it. */
    private float hudFraction(String bind) {
        var s = hud();
        return switch (bind) {
            case "health" -> s.maxHp <= 0 ? 0 : s.hp / s.maxHp;
            case "food" -> s.food / 20f;
            case "armor" -> s.armor / 20f;
            case "air" -> s.maxAir <= 0 ? 0 : Math.max(0, s.air) / (float) s.maxAir;
            case "exp" -> s.exp;
            default -> 0;
        };
    }

    // ================================================================ the engine windows' live parts

    private static final List<String> SAMPLE_ANSWERS = List.of("Слышал.", "Их было двенадцать.", "Расскажи подробнее…");

    /** The followed quest and its goals (in the editor — an example). */
    private void drawQuests(Canvas c, Element e, int[] r, float op) {
        String title;
        List<String[]> rows = new ArrayList<>();
        if (hudState != null) {
            var j = org.sprautestudio.st_addon_spraute.quest.client.QuestClient.journal();
            var q = j.tracked.isEmpty() ? null : j.entry(j.tracked);
            if (q == null || !q.state.equals("active")) return;
            title = q.title;
            for (var t : q.tasks)
                rows.add(new String[]{(t.done() ? "✔ " : "◇ ") + t.text + (t.count > 1 ? "  " + t.progress + "/" + t.count : ""),
                        t.done() ? "done" : t.optional ? "opt" : ""});
        } else {
            title = "Тринадцатый удар";
            rows.add(new String[]{"✔ Найти хоть кого-нибудь живого", "done"});
            rows.add(new String[]{"◇ Собрать души-огоньки  1/3", ""});
            rows.add(new String[]{"◇ Заметить, что у монаха нет тени", "opt"});
        }
        c.flush();
        String al = e.get("align");
        boolean shadow = e.b("shadow");
        net.minecraft.network.chat.Component tc = net.minecraft.network.chat.Component.literal(c.font.plainSubstrByWidth(title, r[2]))
                .withStyle(net.minecraft.ChatFormatting.BOLD);
        int tw = c.font.width(tc);
        int tx = al.equals("right") ? r[0] + r[2] - tw : al.equals("center") ? r[0] + (r[2] - tw) / 2 : r[0];
        c.g.drawString(c.font, tc, tx, r[1], a(e.color("titleColor"), op), shadow);
        float ts = Math.max(0.3f, e.f("textScale"));
        float y = r[1] + 11;
        int shown = 0;
        for (String[] row : rows) {
            if (shown++ >= Math.max(1, e.i("maxTasks")) || y > r[1] + r[3]) break;
            String s = c.font.plainSubstrByWidth(row[0], (int) (r[2] / ts));
            int col = row[1].equals("done") ? e.color("doneColor") : row[1].equals("opt") ? e.color("optionalColor") : e.color("textColor");
            float w = c.font.width(s) * ts;
            float x = al.equals("right") ? r[0] + r[2] - w : al.equals("center") ? r[0] + (r[2] - w) / 2 : r[0];
            c.g.pose().pushPose();
            c.g.pose().translate(x, y, 0);
            c.g.pose().scale(ts, ts, 1);
            c.g.drawString(c.font, s, 0, 0, a(col, op), shadow);
            c.g.pose().popPose();
            y += 10 * ts + 1;
        }
    }

    /** The answers of a choice menu, one under another; remembers where each is for the mouse. */
    private void drawAnswers(Canvas c, Element e, int[] r, float op) {
        List<String> list = answers != null ? answers : SAMPLE_ANSWERS;
        String style = e.get("style");
        float ts = Math.max(0.3f, e.f("textScale"));
        int pad = e.i("padding"), gap = e.i("gap"), minH = Math.max(6, e.i("minHeight"));
        int innerW = Math.max(10, Math.round((r[2] - 2 * pad) / ts));
        int y = r[1];
        c.flush();
        for (int i = 0; i < list.size(); i++) {
            String label = (e.b("numbers") ? (i + 1) + ". " : "") + list.get(i);
            List<FormattedCharSequence> lines = c.font.split(net.minecraft.network.chat.Component.literal(label), innerW);
            int h = Math.max(minH, Math.round(lines.size() * 9 * ts) + 2 * pad);
            boolean hot = answers != null ? i == answerHover : i == 0;
            switch (style) {
                case "flat" -> {
                    box(c, r[0], y, r[2], h, e.i("radius"), a(hot ? e.color("hoverColor") : e.color("color"), op),
                            a(hot ? e.color("hoverBorderColor") : e.color("borderColor"), op), 1);
                    c.flush();
                }
                case "image" -> {
                    String img = hot && !e.get("hoverImage").isEmpty() ? e.get("hoverImage") : e.get("image");
                    if (!img.isEmpty()) {
                        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, op);
                        c.g.blit(org.sprautestudio.st_addon_spraute.runtime.Images.texture(project, img), r[0], y, r[2], h, 0, 0, 1, 1, 1, 1);
                        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
                    }
                }
                default -> {
                    com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, op);
                    org.sprautestudio.st_addon_spraute.engine.client.ChoiceLayout.drawButton(c.g, hot, r[0], y, r[2], h);
                    com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
                }
            }
            int col = a(hot ? e.color("hoverTextColor") : e.color("textColor"), op);
            float ty = y + (h - lines.size() * 9 * ts) / 2f;
            for (int k = 0; k < lines.size(); k++) {
                c.g.pose().pushPose();
                c.g.pose().translate(r[0] + pad, ty + 9 * ts * k, 0);
                c.g.pose().scale(ts, ts, 1);
                c.g.drawString(c.font, lines.get(k), 0, 0, col, false);
                c.g.pose().popPose();
            }
            answerRects.add(new int[]{r[0], y, r[2], h});
            y += h + gap;
        }
    }

    /** The one asking: the whole figure following the mouse, or the head. In the editor — the player. */
    private void drawSpeaker(Canvas c, Element e, int[] r, float op) {
        int bg = e.color("background");
        if ((bg >>> 24) != 0) c.fill(r[0], r[1], r[2], r[3], a(bg, op));
        net.minecraft.world.entity.LivingEntity who = speaker != null ? speaker : hudState == null ? net.minecraft.client.Minecraft.getInstance().player : null;
        if (who == null) return;
        c.flush();
        int[] clip = org.sprautestudio.st_addon_spraute.engine.client.ChatHeads.screenRect(c.g, r[0], r[1], r[2], r[3]);
        c.g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
        c.g.pose().pushPose();
        c.g.pose().translate(0, 0, 200);
        try {
            float sc = Math.max(0.1f, e.f("scale"));
            if (e.get("framing").equals("head")) {
                org.sprautestudio.st_addon_spraute.engine.client.ChatHeads.render(c.g, who, r[0], r[1], r[2], r[3],
                        new org.sprautestudio.st_addon_spraute.engine.client.ChatHeads.Params(sc, 0, 0, 0, "auto"));
            } else {
                float eh = Math.max(0.5f, who.getBbHeight()), ew = Math.max(0.5f, who.getBbWidth());
                int size = (int) Math.max(4, Math.min((r[3] - 8) / eh, (r[2] - 8) / (ew * 1.4f)) * sc);
                float mx = toDesignX(mouseX), my = toDesignY(mouseY);
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(c.g, r[0] + r[2] / 2, r[1] + r[3] - 4,
                        size, r[0] + r[2] / 2f - mx, r[1] + r[3] / 3f - my, who);
            }
        } catch (Throwable ignored) {
        } finally {
            c.g.pose().popPose();
            c.g.disableScissor();
        }
    }

    private static final ResourceLocation QUEST_ICONS = new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "textures/gui/journal/icons.png");

    /** The quest notice's icon for its event: new / done / failed. */
    private void drawQuestIcon(Canvas c, Element e, int[] r, float op) {
        String kind = vars.getOrDefault("kind", hudState == null ? "task" : "");
        int i = kind.equals("complete") ? 1 : kind.equals("fail") ? 2 : 0;
        c.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, op);
        String own = e.get("image");
        if (own.isEmpty()) c.g.blit(QUEST_ICONS, r[0], r[1], r[2], r[3], i * 21, 0, 21, 23, 63, 46);
        else {
            var sk = new org.sprautestudio.st_addon_spraute.engine.EngineConfig.Skin();
            sk.image = own;
            org.sprautestudio.st_addon_spraute.engine.client.Skins.drawSheet(c.g, project, sk, QUEST_ICONS, r[0], r[1], r[2], r[3], i * 21, 0, 63, 46);
        }
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /** The game's look of a part gives way when the same layout shows that part another way: it must not peek from under it. */
    private boolean replaced(Element e, String key) {
        if (!org.sprautestudio.st_addon_spraute.engine.EngineConfig.gameDrawn(e.get("style"))) return false;
        for (Element o : doc.all()) {
            if (o == e || !o.b("visible")) continue;
            if (o.type.equals(e.type) && !org.sprautestudio.st_addon_spraute.engine.EngineConfig.gameDrawn(o.get("style"))) return true;
            if (o.type.equals("progress") && o.get("bind").equals(key)) return true;
        }
        return false;
    }

    /** A part of the player's HUD, drawn like the engine draws it, fitted into the element. */
    private void drawHud(Canvas c, Element e, int[] r, float op) {
        String key = e.type.substring(4);
        if (replaced(e, key)) return;
        var s = hud();
        var cfg = org.sprautestudio.st_addon_spraute.engine.client.EngineClient.config();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        // in the game: the game's rules for when a part shows at all
        if (hudState != null && (!org.sprautestudio.st_addon_spraute.engine.client.EngineClient.shown(mc, key, s)
                || key.equals("coins") && !cfg.currency)) return;
        org.sprautestudio.st_addon_spraute.engine.EngineConfig.Element el = new org.sprautestudio.st_addon_spraute.engine.EngineConfig.Element();
        el.style = e.get("style");
        el.count = Math.max(1, e.i("count"));
        el.pad = e.i("pad");
        el.fillDir = e.get("direction");
        el.gap = e.i("gap");
        el.reverse = e.b("reverse");
        el.text = e.b("showText");
        el.textColor = e.color("textColor");
        el.width = r[2];
        el.height = r[3];
        el.back.image = e.get("back");
        el.fill.image = e.get("fill");
        el.frame.image = e.get("frame");
        el.full.image = e.get("full");
        el.half.image = e.get("half");
        el.empty.image = e.get("empty");
        c.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, op);
        try {
            if (org.sprautestudio.st_addon_spraute.engine.EngineConfig.gameDrawn(el.style)) {
                // the game's look at its own size, fitted (keeping its shape) and centred in the element
                int nw, nh;
                switch (key) {
                    case "health" -> {
                        nw = s.stHealth ? 68 : 81;
                        nh = s.stHealth ? 67 : 9;
                    }
                    case "exp" -> {
                        nw = 182;
                        nh = 5;
                    }
                    case "hotbar" -> {
                        nw = 182;
                        nh = 22;
                    }
                    case "coins" -> {
                        nw = 56;
                        nh = 18;
                    }
                    default -> {
                        nw = 81;
                        nh = 9;
                    }
                }
                float k = Math.min(r[2] / (float) nw, r[3] / (float) nh);
                c.g.pose().pushPose();
                c.g.pose().translate(r[0] + (r[2] - nw * k) / 2f, r[1] + (r[3] - nh * k) / 2f, 0);
                c.g.pose().scale(k, k, 1);
                org.sprautestudio.st_addon_spraute.engine.client.HudPainter.standard(c.g, c.font, key, el.style, new int[]{0, 0, nw, nh}, s);
                c.g.pose().popPose();
            } else {
                var tmp = new org.sprautestudio.st_addon_spraute.engine.EngineConfig();
                tmp.project = project != null && !project.isEmpty() ? project : cfg.project;
                tmp.hud.put(key, el);
                org.sprautestudio.st_addon_spraute.engine.client.HudPainter.custom(c.g, c.font, tmp, key, r, s);
            }
        } finally {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    public GuiView(GuiDoc doc, String project) {
        this.doc = doc;
        this.project = project;
        focusable = true;
        for (Element e : doc.all()) if (e.type.equals("input")) inputs.put(e.id, e.get("text"));
    }

    public void restartAppear() {
        openedAt = System.nanoTime();
        textChangedAt.clear();
    }

    protected float time() {
        return playAppear ? (System.nanoTime() - openedAt) / 1e9f : 1e6f;
    }

    public Map<String, String> inputs() {
        return inputs;
    }

    /** Running animations of numeric properties: key "id\0prop" -> {from, to, startNanos, seconds}. */
    private final Map<String, double[]> tweens = new LinkedHashMap<>();
    private final Map<String, String> tweenEnd = new HashMap<>();

    /** Applies a runtime change from the story ("gui set"); "prop@seconds" animates numbers smoothly. */
    public void update(String id, String prop, String value) {
        Element e = doc.element(id);
        if (e == null) return;
        int at = prop.indexOf('@');
        if (at > 0) {
            float secs = GuiDoc.toFloat(prop.substring(at + 1));
            prop = prop.substring(0, at);
            String cur = e.get(prop);
            boolean numeric = isNumber(cur) && isNumber(value);
            if (secs > 0 && numeric) {
                tweens.put(id + "\0" + prop, new double[]{Double.parseDouble(cur.trim()), Double.parseDouble(value.trim()), System.nanoTime(), secs});
                tweenEnd.put(id + "\0" + prop, value);
                return;
            }
        }
        tweens.remove(id + "\0" + prop);
        e.set(prop, value);
        if (prop.equals("text")) {
            textChangedAt.put(id, System.nanoTime());
            if (e.type.equals("input")) inputs.put(id, value);
        }
        if (prop.equals("scrollY") || prop.equals("scrollX")) {
            float[] s = scrolls.computeIfAbsent(id, k -> new float[4]);
            float v = GuiDoc.toFloat(value);
            if (prop.equals("scrollY")) s[1] = s[3] = v;
            else s[0] = s[2] = v;
        }
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Advances property animations (ease in-out). */
    private void stepTweens() {
        if (tweens.isEmpty()) return;
        long now = System.nanoTime();
        var it = tweens.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            String[] k = en.getKey().split("\0", 2);
            Element e = doc.element(k[0]);
            double[] t = en.getValue();
            if (e == null) {
                it.remove();
                continue;
            }
            double p = (now - t[2]) / 1e9 / t[3];
            if (p >= 1) {
                e.set(k[1], tweenEnd.getOrDefault(en.getKey(), String.valueOf(t[1])));
                it.remove();
                continue;
            }
            double q = p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2;
            double v = t[0] + (t[1] - t[0]) * q;
            e.set(k[1], String.valueOf(Math.round(v * 100) / 100.0));
        }
    }

    // ================================================================ geometry

    /** Places the design area and lays out the element tree. */
    protected final void computeArea() {
        stepTweens();
        placeArea();
        layoutTree();
    }

    /** Places the design area inside the widget. The editor overrides this for zoom and pan. */
    protected void placeArea() {
        if (doc.get("layout").equals("screen")) {
            areaScale = 1;
            areaX = x;
            areaY = y;
            areaW = w;
            areaH = h;
        } else {
            areaW = doc.width();
            areaH = doc.height();
            areaScale = Math.min(w / areaW, h / areaH);
            areaX = x + (w - areaW * areaScale) / 2;
            areaY = y + (h - areaH * areaScale) / 2;
        }
    }

    private static float ax(String anchor) {
        return anchor.endsWith("left") ? 0 : anchor.endsWith("right") ? 1 : 0.5f;
    }

    private static float ay(String anchor) {
        return anchor.startsWith("top") ? 0 : anchor.startsWith("bottom") ? 1 : 0.5f;
    }

    /** Computes absolute design rectangles of the whole tree. Children are anchored inside their container. */
    public void layoutTree() {
        rects.clear();
        areas.clear();
        contents.clear();
        place(doc.elements, 0, 0, areaW, areaH);
    }

    private void place(List<Element> list, float ax, float ay, float aw, float ah) {
        for (Element e : list) {
            String a = e.get("anchor");
            float fx = ax(a), fy = ay(a);
            int ew = Math.max(1, Math.round(dim(e.get("w"), aw))), eh = Math.max(1, Math.round(dim(e.get("h"), ah)));
            int ex = Math.round(ax + fx * aw + dim(e.get("x"), aw) - fx * ew);
            int ey = Math.round(ay + fy * ah + dim(e.get("y"), ah) - fy * eh);
            String stretch = e.get("stretch");
            if (areaScale > 0 && (stretch.equals("width") || stretch.equals("both"))) {
                ex = (int) Math.floor((x - areaX) / areaScale);
                ew = Math.max(1, (int) Math.ceil(w / areaScale) + 1);
            }
            if (areaScale > 0 && (stretch.equals("height") || stretch.equals("both"))) {
                ey = (int) Math.floor((y - areaY) / areaScale);
                eh = Math.max(1, (int) Math.ceil(h / areaScale) + 1);
            }
            rects.put(e, new int[]{ex, ey, ew, eh});
            areas.put(e, new float[]{ax, ay, aw, ah});
            if (e.children.isEmpty() || !e.isContainer()) continue;
            if (!e.type.equals("scroll")) {
                place(e.children, ex, ey, ew, eh);
                continue;
            }
            String dir = e.get("direction");
            boolean v = !dir.equals("horizontal"), hz = !dir.equals("vertical");
            int cw = e.i("contentWidth"), ch = e.i("contentHeight");
            if (cw <= 0 || ch <= 0) {
                // measure the children laid out in the viewport
                place(e.children, ex, ey, ew, eh);
                int maxR = ew, maxB = eh, pad = e.i("padding");
                for (Element c : e.children) {
                    int[] r = rects.get(c);
                    maxR = Math.max(maxR, r[0] + r[2] - ex + pad);
                    maxB = Math.max(maxB, r[1] + r[3] - ey + pad);
                }
                if (cw <= 0) cw = maxR;
                if (ch <= 0) ch = maxB;
            }
            if (!hz) cw = ew;
            if (!v) ch = eh;
            cw = Math.max(cw, ew);
            ch = Math.max(ch, eh);
            contents.put(e, new int[]{cw, ch});
            float[] s = scrolls.computeIfAbsent(e.id, k -> new float[4]);
            s[2] = Math.max(0, Math.min(cw - ew, s[2]));
            s[3] = Math.max(0, Math.min(ch - eh, s[3]));
            s[0] = Math.max(0, Math.min(cw - ew, s[0]));
            s[1] = Math.max(0, Math.min(ch - eh, s[1]));
            place(e.children, ex - Math.round(s[0]), ey - Math.round(s[1]), cw, ch);
        }
    }

    /** A size or offset: pixels, or "50%" of the parent's size. */
    public static float dim(String v, float base) {
        String s = v == null ? "" : v.trim();
        if (s.endsWith("%")) return GuiDoc.toFloat(s.substring(0, s.length() - 1)) * base / 100f;
        return GuiDoc.toFloat(s);
    }

    /** Element rectangle in absolute design units: {x, y, w, h}. */
    public int[] rect(Element e) {
        int[] r = rects.get(e);
        if (r == null) {
            layoutTree();
            r = rects.get(e);
        }
        return r != null ? r : new int[]{0, 0, 1, 1};
    }

    /** Inverse of {@link #rect}: x/y props that put e's rectangle at design position (rx, ry). */
    public int[] offsetFor(Element e, int rx, int ry, int rw, int rh) {
        String a = e.get("anchor");
        float fx = ax(a), fy = ay(a);
        float[] ar = areas.get(e);
        if (ar == null) ar = new float[]{0, 0, areaW, areaH};
        return new int[]{Math.round(rx - ar[0] - fx * ar[2] + fx * rw), Math.round(ry - ar[1] - fy * ar[3] + fy * rh)};
    }

    /** The area e is anchored in (its parent's rectangle or the design area): {x, y, w, h}. */
    public float[] areaOf(Element e) {
        float[] ar = areas.get(e);
        return ar != null ? ar : new float[]{0, 0, areaW, areaH};
    }

    public float toDesignX(double px) {
        return (float) ((px - areaX) / areaScale);
    }

    public float toDesignY(double py) {
        return (float) ((py - areaY) / areaScale);
    }

    public float areaX() {
        return areaX;
    }

    public float areaY() {
        return areaY;
    }

    public float areaScale() {
        return areaScale;
    }

    public float areaW() {
        return areaW;
    }

    public float areaH() {
        return areaH;
    }

    /** Topmost visible element under a design point, optionally only interactive ones. */
    public Element elementAt(float dx, float dy, boolean interactiveOnly) {
        return pick(dx, dy, e -> !interactiveOnly || e.type.equals("button") || e.type.equals("input") || e.type.equals("slot")
                || e.type.equals("inventory") || !e.get("tooltip").isEmpty());
    }

    /** Deepest visible element under a design point accepted by the filter; clip and scroll containers cut their children. */
    public Element pick(float dx, float dy, java.util.function.Predicate<Element> accept) {
        return pickIn(doc.elements, dx, dy, accept);
    }

    private Element pickIn(List<Element> list, float dx, float dy, java.util.function.Predicate<Element> accept) {
        for (int i = list.size() - 1; i >= 0; i--) {
            Element e = list.get(i);
            if (!e.b("visible") && !showHidden()) continue;
            int[] r = rect(e);
            boolean inside = dx >= r[0] && dy >= r[1] && dx < r[0] + r[2] && dy < r[1] + r[3];
            if (e.isContainer() && !e.children.isEmpty() && (inside || e.type.equals("group"))) {
                Element c = pickIn(e.children, dx, dy, accept);
                if (c != null) return c;
            }
            if (inside && accept.test(e)) return e;
        }
        return null;
    }

    // ================================================================ painting

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        mouseX = mx;
        mouseY = my;
        computeArea();
        int bg = GuiDoc.toColor(doc.get("background"));
        if ((bg >>> 24) != 0 && interactive && drawBackground) c.fill(x, y, w, h, bg);
        paintBackdrop(c);
        if (interactive) {
            Element hov = elementAt(toDesignX(mx), toDesignY(my), true);
            hoveredId = hov != null && isHoveredDeep() ? hov.id : null;
        }
        paintAfter = interactive;
        viewX0 = toDesignX(x);
        viewY0 = toDesignY(y);
        viewX1 = toDesignX(x + w);
        viewY1 = toDesignY(y + h);
        c.pushTransform(areaX, areaY, areaScale);
        answerRects.clear();
        drawList(c, doc.elements, Math.max(0, Math.min(1, opacity)), dt);
        c.popTransform();
        if (interactive && hoveredId != null) {
            Element e = doc.element(hoveredId);
            if (e != null && !e.get("tooltip").isEmpty()) paintTooltip(c, unescape(e.get("tooltip")), mx, my);
        }
    }

    /** Hook for the editor (design frame, grid). */
    protected void paintBackdrop(Canvas c) {
    }

    /** The editor draws hidden elements faintly. */
    protected boolean showHidden() {
        return false;
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n");
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private static float easeBack(float t) {
        t = Math.max(0, Math.min(1, t));
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    private void drawList(Canvas c, List<Element> list, float parentOpacity, float dt) {
        for (Element e : list) {
            if (!e.b("visible") && !showHidden()) continue;
            int[] r = rect(e);
            // culling: skip what is outside the view (groups may hold children outside their own box)
            boolean plain = e.f("rotation") == 0 && e.get("appear").equals("none");
            if (plain && !e.type.equals("group") && (r[0] > viewX1 || r[1] > viewY1 || r[0] + r[2] < viewX0 || r[1] + r[3] < viewY0)) continue;
            float target = e.id.equals(hoveredId) ? 1 : 0;
            float hv = hoverAnim.getOrDefault(e.id, 0f);
            if (hv != target) {
                hv += (target - hv) * Math.min(1, dt * 14);
                if (Math.abs(hv - target) < 0.01f) hv = target;
                hoverAnim.put(e.id, hv);
            }
            try {
                drawElement(c, e, hv, dt, parentOpacity);
            } catch (RuntimeException ex) {
                c.outline(r[0], r[1], r[2], r[3], Theme.ERROR);
            }
        }
    }

    /** Draw the carried item once the tree is painted. */
    private boolean paintAfter;

    @Override
    protected void paintOver(Canvas c, int mx, int my, float dt) {
        super.paintOver(c, mx, my, dt);
        if (paintAfter) drawCarried(c);
    }

    private void drawElement(Canvas c, Element e, float hover, float dt, float parentOpacity) {
        int[] r = rect(e);
        float opacity = Math.max(0, Math.min(1, e.f("opacity"))) * parentOpacity;
        if (!e.b("visible")) opacity *= 0.3f;
        // appearance animation
        String appear = e.get("appear");
        float dx = 0, dy = 0, scale = 1;
        if (!appear.equals("none")) {
            float dur = Math.max(0.01f, e.f("appearTime"));
            float t = (time() - e.f("appearDelay")) / dur;
            if (t <= 0 && playAppear) return;
            float k = ease(t);
            switch (appear) {
                case "fade" -> opacity *= k;
                case "slide_up" -> {
                    dy = (1 - k) * 24;
                    opacity *= k;
                }
                case "slide_down" -> {
                    dy = -(1 - k) * 24;
                    opacity *= k;
                }
                case "slide_left" -> {
                    dx = (1 - k) * 32;
                    opacity *= k;
                }
                case "slide_right" -> {
                    dx = -(1 - k) * 32;
                    opacity *= k;
                }
                case "zoom" -> {
                    scale = 0.6f + 0.4f * k;
                    opacity *= k;
                }
                case "pop" -> {
                    scale = 0.5f + 0.5f * easeBack(t);
                    opacity *= Math.min(1, t * 3);
                }
                default -> {
                }
            }
        }
        if (opacity <= 0.01f) return;
        float hs = e.f("hoverScale");
        if (hs > 0 && hs != 1) scale *= 1 + (hs - 1) * hover;
        float rot = e.f("rotation");
        boolean transformed = dx != 0 || dy != 0 || scale != 1 || rot != 0;
        if (transformed) {
            float cx = r[0] + r[2] / 2f, cy = r[1] + r[3] / 2f;
            c.g.pose().pushPose();
            c.g.pose().translate(cx + dx, cy + dy, 0);
            if (rot != 0) c.g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
            c.g.pose().scale(scale, scale, 1);
            c.g.pose().translate(-cx, -cy, 0);
        }
        switch (e.type) {
            case "panel" -> drawPanel(c, e, r, opacity);
            case "image" -> drawImage(c, e, r, opacity);
            case "text" -> drawText(c, e, r, opacity);
            case "button" -> drawButton(c, e, r, opacity, hover);
            case "entity" -> drawEntity(c, e, r, opacity);
            case "item" -> drawItem(c, e, r, opacity);
            case "slot" -> drawSlot(c, e, r, opacity, hover);
            case "inventory" -> drawInventory(c, e, r, opacity);
            case "progress" -> drawProgress(c, e, r, opacity, dt);
            case "input" -> drawInput(c, e, r, opacity);
            case "clip", "scroll" -> drawZone(c, e, r, opacity);
            case "hud_quests" -> drawQuests(c, e, r, opacity);
            case "choice_answers" -> drawAnswers(c, e, r, opacity);
            case "speaker" -> drawSpeaker(c, e, r, opacity);
            case "quest_icon" -> drawQuestIcon(c, e, r, opacity);
            default -> {
                if (org.sprautestudio.st_addon_spraute.gui.GuiSchema.isHud(e.type)) drawHud(c, e, r, opacity);
                else if (e.type.startsWith("journal_")) org.sprautestudio.st_addon_spraute.quest.client.JournalParts.draw(this, c, e, r, opacity);
                else if (e.type.startsWith("shop_")) org.sprautestudio.st_addon_spraute.shop.client.ShopParts.draw(this, c, e, r, opacity);
            }
        }
        if (e.isContainer() && !e.children.isEmpty()) {
            boolean clips = !e.type.equals("group");
            if (clips) c.pushClip(r[0], r[1], r[2], r[3]);
            if (e.type.equals("scroll")) animateScroll(e, dt);
            drawList(c, e.children, opacity, dt);
            if (clips) c.popClip();
        }
        if (e.type.equals("scroll")) drawScrollbars(c, e, r, opacity);
        if (transformed) c.g.pose().popPose();
    }

    private void drawZone(Canvas c, Element e, int[] r, float op) {
        int bg = e.color("background");
        int bw = e.i("borderWidth");
        if ((bg >>> 24) != 0 || bw > 0) box(c, r[0], r[1], r[2], r[3], e.i("radius"), a(bg, op), a(e.color("borderColor"), op), bw);
        if (!interactive && e.type.equals("clip")) c.outline(r[0], r[1], r[2], r[3], 0x33FFCB6B);
    }

    private void animateScroll(Element e, float dt) {
        float[] s = scrolls.get(e.id);
        if (s == null) return;
        if (!e.b("smooth")) {
            s[0] = s[2];
            s[1] = s[3];
            return;
        }
        float k = Math.min(1, dt * 16);
        s[0] += (s[2] - s[0]) * k;
        s[1] += (s[3] - s[1]) * k;
        if (Math.abs(s[2] - s[0]) < 0.3f) s[0] = s[2];
        if (Math.abs(s[3] - s[1]) < 0.3f) s[1] = s[3];
    }

    /** Thumb rectangles of a scroll panel: {vx, vy, vw, vh, hx, hy, hw, hh}; w = 0 when not needed. */
    private int[] bars(Element e, int[] r) {
        int[] content = contents.get(e);
        int[] out = new int[8];
        if (content == null || !e.b("scrollbar")) return out;
        float[] s = scrolls.getOrDefault(e.id, new float[4]);
        int bw = Math.max(2, e.i("barWidth"));
        if (content[1] > r[3]) {
            int track = r[3] - 4, thumb = Math.max(12, track * r[3] / content[1]);
            int pos = Math.round((track - thumb) * s[1] / (content[1] - r[3]));
            out[0] = r[0] + r[2] - bw - 2;
            out[1] = r[1] + 2 + pos;
            out[2] = bw;
            out[3] = thumb;
        }
        if (content[0] > r[2]) {
            int track = r[2] - 4, thumb = Math.max(12, track * r[2] / content[0]);
            int pos = Math.round((track - thumb) * s[0] / (content[0] - r[2]));
            out[4] = r[0] + 2 + pos;
            out[5] = r[1] + r[3] - bw - 2;
            out[6] = thumb;
            out[7] = bw;
        }
        return out;
    }

    private void drawScrollbars(Canvas c, Element e, int[] r, float op) {
        int[] b = bars(e, r);
        int col = a(e.color("barColor"), op);
        if (b[2] > 0) c.round(b[0], b[1], b[2], b[3], b[2] / 2, col);
        if (b[6] > 0) c.round(b[4], b[5], b[6], b[7], b[7] / 2, col);
    }

    /** Scrolls the panel under the point; returns false when there is none. */
    public boolean scrollAt(float dx, float dy, double delta, boolean horizontal) {
        Element e = pick(dx, dy, el -> el.type.equals("scroll") && contents.containsKey(el));
        if (e == null) return false;
        int[] content = contents.get(e), r = rect(e);
        float[] s = scrolls.computeIfAbsent(e.id, k -> new float[4]);
        float step = (float) (delta * Math.max(4, e.i("speed")));
        boolean canV = content[1] > r[3], canH = content[0] > r[2];
        if ((horizontal || !canV) && canH) s[2] = Math.max(0, Math.min(content[0] - r[2], s[2] - step));
        else if (canV) s[3] = Math.max(0, Math.min(content[1] - r[3], s[3] - step));
        else return false;
        return true;
    }

    private static int a(int color, float opacity) {
        return Theme.alpha(color, opacity);
    }

    private static void box(Canvas c, int x, int y, int w, int h, int radius, int fill, int border, int bw) {
        if (bw > 0 && (border >>> 24) != 0) {
            c.round(x, y, w, h, radius, border);
            c.round(x + bw, y + bw, w - 2 * bw, h - 2 * bw, Math.max(0, radius - bw), fill);
        } else {
            c.round(x, y, w, h, radius, fill);
        }
    }

    private void drawPanel(Canvas c, Element e, int[] r, float op) {
        int radius = e.i("radius"), bw = e.i("borderWidth");
        int shadow = e.i("shadow");
        if (shadow > 0 && op > 0.4f) c.shadow(r[0], r[1], r[2], r[3], radius, shadow);
        int col = a(e.color("color"), op), col2 = a(e.color("color2"), op);
        String grad = e.get("gradient");
        box(c, r[0], r[1], r[2], r[3], radius, col, a(e.color("borderColor"), op), bw);
        if (!grad.equals("none")) {
            int inset = Math.max(bw, 0);
            int gx = r[0] + inset, gy = r[1] + inset, gw = r[2] - 2 * inset, gh = r[3] - 2 * inset;
            if (grad.equals("vertical")) {
                int ry = Math.min(radius, gh / 2);
                c.gradient(gx, gy + ry, gw, gh - 2 * ry, col, col2);
                c.round(gx, gy + gh - 2 * ry, gw, 2 * ry, ry, col2);
                c.fill(gx, gy + gh - 2 * ry, gw, ry, col2);
            } else {
                int steps = Math.max(1, Math.min(64, gw));
                int rx = Math.min(radius, gw / 2);
                for (int i = 0; i < steps; i++) {
                    int sx = gx + rx + (gw - 2 * rx) * i / steps, ex = gx + rx + (gw - 2 * rx) * (i + 1) / steps;
                    c.fill(sx, gy, ex - sx, gh, Theme.lerp(col, col2, i / (float) steps));
                }
                c.round(gx + gw - 2 * rx, gy, 2 * rx, gh, rx, col2);
                c.fill(gx + gw - 2 * rx, gy, rx, gh, col2);
            }
        }
    }

    /** Texture and its size for an image value: a project picture or a resource path. */
    private record Tex(ResourceLocation rl, int w, int h) {}

    private Tex texture(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.contains(":")) {
            ResourceLocation rl = ResourceLocation.tryParse(v);
            return rl == null ? null : new Tex(rl, 0, 0);
        }
        if (localProject != null) {
            Project.ImageRef ref = localProject.image(v);
            if (ref != null) {
                ProjectImages.Loaded l = ProjectImages.load(localProject, ref);
                if (l != null) return new Tex(l.texture(), l.width(), l.height());
            }
        }
        int[] size = ProjectImages.size(Images.texture(project, v));
        return new Tex(Images.texture(project, v), size[0], size[1]);
    }

    private static void blit(Canvas c, ResourceLocation rl, int x, int y, int w, int h, float u0, float v0, float u1, float v1, int tint, float op) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, ((tint >>> 24) / 255f) * op);
        final int T = 4096;
        int uu = Math.round(u0 * T), vv = Math.round(v0 * T), uw = Math.round((u1 - u0) * T), vh = Math.round((v1 - v0) * T);
        c.g.blit(rl, x, y, w, h, uu, vv, uw, vh, T, T);
        c.flush();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private void drawImage(Canvas c, Element e, int[] r, float op) {
        Tex t = texture(e.get("image"));
        if (t == null) {
            if (!interactive) {
                c.outline(r[0], r[1], r[2], r[3], 0x55FFFFFF);
                c.textCentered("картинка", r[0] + r[2] / 2, r[1] + r[3] / 2 - 4, 0x88FFFFFF);
            }
            return;
        }
        float u0 = e.f("u0"), v0 = e.f("v0"), u1 = e.f("u1"), v1 = e.f("v1");
        if (e.b("flipX")) {
            float tmp = u0;
            u0 = u1;
            u1 = tmp;
        }
        if (e.b("flipY")) {
            float tmp = v0;
            v0 = v1;
            v1 = tmp;
        }
        int tint = e.color("tint");
        String fit = e.get("fit");
        int x = r[0], y = r[1], w = r[2], h = r[3];
        if (t.w > 0 && t.h > 0 && (fit.equals("contain") || fit.equals("cover"))) {
            float iw = t.w * Math.abs(u1 - u0), ih = t.h * Math.abs(v1 - v0);
            float k = fit.equals("contain") ? Math.min(w / iw, h / ih) : Math.max(w / iw, h / ih);
            int dw = Math.round(iw * k), dh = Math.round(ih * k);
            if (fit.equals("cover")) c.pushClip(x, y, w, h);
            blit(c, t.rl, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, u0, v0, u1, v1, tint, op);
            if (fit.equals("cover")) c.popClip();
        } else if (fit.equals("tile")) {
            int tw = t.w > 0 ? t.w : 16, th = t.h > 0 ? t.h : 16;
            c.pushClip(x, y, w, h);
            for (int yy = y; yy < y + h; yy += th) for (int xx = x; xx < x + w; xx += tw) blit(c, t.rl, xx, yy, tw, th, u0, v0, u1, v1, tint, op);
            c.popClip();
        } else {
            blit(c, t.rl, x, y, w, h, u0, v0, u1, v1, tint, op);
        }
    }

    private MutableComponent styled(Element e, String text) {
        MutableComponent comp = Component.literal(text);
        Style s = Style.EMPTY.withBold(e.b("bold")).withItalic(e.b("italic")).withUnderlined(e.b("underline")).withStrikethrough(e.b("strike"));
        return comp.withStyle(s);
    }

    private String visibleText(Element e, String full) {
        full = fillHud(full);
        float speed = e.f("typewriter");
        if (speed <= 0 || !playAppear) return full;
        long from = textChangedAt.getOrDefault(e.id, openedAt);
        float secs = (System.nanoTime() - from) / 1e9f - (textChangedAt.containsKey(e.id) ? 0 : e.f("appearDelay"));
        int n = (int) Math.max(0, secs * speed);
        return n >= full.length() ? full : full.substring(0, n);
    }

    private void drawText(Canvas c, Element e, int[] r, float op) {
        int bgc = e.color("background");
        if ((bgc >>> 24) != 0) c.round(r[0], r[1], r[2], r[3], 3, a(bgc, op));
        int pad = e.i("padding");
        float scale = Math.max(0.1f, e.f("scale"));
        String text = visibleText(e, unescape(e.get("text")));
        int innerW = Math.max(1, Math.round((r[2] - 2 * pad) / scale));
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (e.b("wrap")) {
                List<FormattedCharSequence> split = c.font.split(styled(e, para), innerW);
                if (split.isEmpty()) lines.add(FormattedCharSequence.EMPTY);
                else lines.addAll(split);
            } else {
                lines.add(styled(e, para).getVisualOrderText());
            }
        }
        int lh = c.font.lineHeight + e.i("lineSpacing");
        float innerH = (r[3] - 2 * pad) / scale;
        String overflow = e.get("overflow");
        if (overflow.equals("ellipsis")) {
            int fit = Math.max(1, (int) Math.floor((innerH + e.i("lineSpacing")) / lh));
            boolean cut = lines.size() > fit;
            while (lines.size() > fit) lines.remove(lines.size() - 1);
            for (int i = 0; i < lines.size(); i++) {
                boolean last = i == lines.size() - 1;
                if (c.font.width(lines.get(i)) <= innerW && !(last && cut)) continue;
                String plain = plain(lines.get(i));
                String shortened = c.font.plainSubstrByWidth(plain, Math.max(0, innerW - c.font.width("…")));
                lines.set(i, styled(e, shortened.stripTrailing() + "…").getVisualOrderText());
            }
        }
        int total = lines.size() * lh;
        String va = e.get("valign"), al = e.get("align");
        float top = va.equals("middle") ? (innerH - total) / 2f : va.equals("bottom") ? innerH - total : 0;
        int color = a(e.color("color"), op);
        if ((color >>> 24) < 5) return;
        boolean clip = !overflow.equals("visible");
        if (clip) c.pushClip(r[0], r[1], r[2], r[3]);
        c.g.pose().pushPose();
        c.g.pose().translate(r[0] + pad, r[1] + pad, 0);
        c.g.pose().scale(scale, scale, 1);
        int yy = Math.round(top);
        for (FormattedCharSequence line : lines) {
            int lw = c.font.width(line);
            int lx = al.equals("center") ? (innerW - lw) / 2 : al.equals("right") ? innerW - lw : 0;
            c.g.drawString(c.font, line, lx, yy, color, e.b("shadow"));
            yy += lh;
        }
        c.g.pose().popPose();
        if (clip) {
            c.flush();
            c.popClip();
        }
    }

    /** The characters of a laid-out line. */
    private static String plain(FormattedCharSequence line) {
        StringBuilder sb = new StringBuilder();
        line.accept((i, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }

    private void drawButton(Canvas c, Element e, int[] r, float op, float hover) {
        boolean enabled = e.b("enabled");
        boolean pressed = interactive && e.id.equals(pressedId) && e.id.equals(hoveredId);
        int fill = !enabled ? e.color("disabledColor") : pressed ? e.color("pressColor") : Theme.lerp(e.color("color"), e.color("hoverColor"), hover);
        int border = enabled ? Theme.lerp(e.color("borderColor"), e.color("hoverBorderColor"), hover) : e.color("borderColor");
        box(c, r[0], r[1], r[2], r[3], e.i("radius"), a(fill, op), a(border, op), e.i("borderWidth"));
        float ts = Math.max(0.1f, e.f("textScale"));
        String text = unescape(e.get("text"));
        Tex icon = texture(e.get("icon"));
        int is = icon != null ? e.i("iconSize") : 0;
        int tw = Math.round(c.font.width(text) * ts);
        int gap = is > 0 && !text.isEmpty() ? 4 : 0;
        int total = is + gap + tw;
        int sx = r[0] + (r[2] - total) / 2;
        if (icon != null) blit(c, icon.rl, sx, r[1] + (r[3] - is) / 2, is, is, 0, 0, 1, 1, 0xFFFFFFFF, enabled ? op : op * 0.5f);
        int tc = a(e.color("textColor"), enabled ? op : op * 0.5f);
        if ((tc >>> 24) >= 5 && !text.isEmpty()) {
            c.g.pose().pushPose();
            c.g.pose().translate(sx + is + gap, r[1] + (r[3] - c.font.lineHeight * ts) / 2f + (pressed ? 1 : 0), 0);
            c.g.pose().scale(ts, ts, 1);
            c.g.drawString(c.font, text, 0, 0, tc, true);
            c.g.pose().popPose();
        }
    }

    private void drawEntity(Canvas c, Element e, int[] r, float op) {
        int bgc = e.color("background");
        if ((bgc >>> 24) != 0) c.round(r[0], r[1], r[2], r[3], 4, a(bgc, op));
        String type = e.get("entity");
        EntityPreview p = entities.get(e.id);
        if (p == null || !p.typeId().equals(type)) {
            p = new EntityPreview(type);
            entities.put(e.id, p);
        }
        p.animate(e.get("anim"), e.get("animLayer"), e.b("animLoop"), e.f("animSpeed"));
        float lookX = 0, lookY = 0;
        if (e.b("followMouse")) {
            float mxD = toDesignX(mouseX), myD = toDesignY(mouseY);
            lookX = (float) Math.atan((r[0] + r[2] / 2f - mxD) / 40f);
            lookY = (float) Math.atan((r[1] + r[3] * 0.3f - myD) / 40f);
        }
        float yaw = e.f("yaw") + e.f("spin") * time();
        c.pushClip(r[0], r[1], r[2], r[3]);
        p.render(c, r[0] + r[2] / 2f, r[1] + r[3] - 2, r[2], r[3], Math.max(0.05f, e.f("scale")), yaw, e.f("pitch"), lookX, lookY);
        c.popClip();
        if (p.entity() == null && !interactive) {
            c.outline(r[0], r[1], r[2], r[3], 0x55FFFFFF);
            c.textCentered(p.error != null ? p.error : "войдите в мир", r[0] + r[2] / 2, r[1] + r[3] / 2 - 4, 0x88FFFFFF);
        }
    }

    private void drawItem(Canvas c, Element e, int[] r, float op) {
        int bgc = e.color("background");
        if ((bgc >>> 24) != 0) c.round(r[0], r[1], r[2], r[3], 3, a(bgc, op));
        ResourceLocation rl = ResourceLocation.tryParse(e.get("item").contains(":") ? e.get("item") : "minecraft:" + e.get("item"));
        Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        if (item == null) return;
        ItemStack stack = new ItemStack(item, Math.max(1, e.i("count")));
        c.g.pose().pushPose();
        c.g.pose().translate(r[0], r[1], 0);
        c.g.pose().scale(r[2] / 16f, r[3] / 16f, 1);
        c.g.renderItem(stack, 0, 0);
        if (e.b("showCount") && stack.getCount() > 1) c.g.renderItemDecorations(c.font, stack, 0, 0);
        c.g.pose().popPose();
    }

    // ================================================================ slots

    /** Inventory index the player has picked up to put into a slot, or -1. */
    private int carried = -1;

    private static ItemStack stackOf(String id, int count) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id.trim() : "minecraft:" + id.trim());
        Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
    }

    /** Cell background: a picture, the vanilla bevel, a flat colour or nothing. */
    private void cell(Canvas c, Element e, int x, int y, int s, float op, boolean hover) {
        Tex t = texture(e.get("texture"));
        if (t != null) {
            c.flush();
            int tw = t.w > 0 ? t.w : s, th = t.h > 0 ? t.h : s;
            c.g.blit(t.rl, x, y, s, s, 0, 0, tw, th, tw, th);
        } else {
            switch (e.get("style")) {
                case "flat" -> c.fill(x, y, s, s, a(e.color("background"), op));
                case "none" -> {
                }
                default -> {
                    c.fill(x, y, s, s, a(0xFF8B8B8B, op));
                    c.fill(x, y, s - 1, 1, a(0xFF373737, op));
                    c.fill(x, y, 1, s - 1, a(0xFF373737, op));
                    c.fill(x + 1, y + s - 1, s - 1, 1, a(0xFFFFFFFF, op));
                    c.fill(x + s - 1, y + 1, 1, s - 1, a(0xFFFFFFFF, op));
                }
            }
        }
        if (hover) c.fill(x + 1, y + 1, s - 2, s - 2, e.color("hoverColor"));
    }

    private void itemIn(Canvas c, ItemStack st, int x, int y, int s, boolean decorations) {
        if (st.isEmpty()) return;
        c.flush();
        float k = Math.max(0.25f, (s - 2) / 16f);
        c.g.pose().pushPose();
        c.g.pose().translate(x + (s - 16 * k) / 2f, y + (s - 16 * k) / 2f, 0);
        c.g.pose().scale(k, k, 1);
        c.g.renderItem(st, 0, 0);
        if (decorations) c.g.renderItemDecorations(c.font, st, 0, 0);
        c.g.pose().popPose();
    }

    private void drawSlot(Canvas c, Element e, int[] r, float op, float hover) {
        int s = Math.min(r[2], r[3]);
        int x = r[0] + (r[2] - s) / 2, y = r[1] + (r[3] - s) / 2;
        cell(c, e, x, y, s, op, interactive && hover > 0.5f && !e.get("mode").equals("display"));
        ItemStack st = stackOf(e.get("item"), e.i("count"));
        if (st.isEmpty()) {
            ItemStack ghost = stackOf(e.get("ghost"), 1);
            if (!ghost.isEmpty()) {
                itemIn(c, ghost, x, y, s, false);
                c.fill(x + 1, y + 1, s - 2, s - 2, 0x998B8B8B);
            }
        } else {
            itemIn(c, st, x, y, s, true);
        }
    }

    /** Inventory indices shown by an inventory element, row by row; -1 is the gap before the hotbar. */
    private static int[][] inventoryRows(Element e) {
        String rows = e.get("rows");
        List<int[]> out = new ArrayList<>();
        if (!rows.equals("hotbar")) for (int r = 0; r < 3; r++) {
            int[] row = new int[9];
            for (int i = 0; i < 9; i++) row[i] = 9 + r * 9 + i;
            out.add(row);
        }
        if (!rows.equals("main")) {
            int[] row = new int[9];
            for (int i = 0; i < 9; i++) row[i] = i;
            out.add(row);
        }
        return out.toArray(new int[0][]);
    }

    /** Inventory index of a cell under a design point, or -1. */
    private int inventoryCell(Element e, float dx, float dy) {
        int[] r = rect(e);
        int s = Math.max(4, e.i("cell"));
        int[][] rows = inventoryRows(e);
        for (int ri = 0; ri < rows.length; ri++) {
            int cy = r[1] + ri * s + (ri == 3 || (rows.length == 4 && ri == 3) ? 4 : 0);
            for (int ci = 0; ci < 9; ci++) {
                int cx = r[0] + ci * s;
                if (dx >= cx && dx < cx + s && dy >= cy && dy < cy + s) return rows[ri][ci];
            }
        }
        return -1;
    }

    private static int hotbarSize() {
        return 9;
    }

    private void drawInventory(Canvas c, Element e, int[] r, float op) {
        var player = Minecraft.getInstance().player;
        int s = Math.max(4, e.i("cell"));
        int[][] rows = inventoryRows(e);
        int hover = interactive && isHoveredDeep() ? inventoryCell(e, toDesignX(mouseX), toDesignY(mouseY)) : -1;
        int open = hotbarSize();
        for (int ri = 0; ri < rows.length; ri++) {
            int cy = r[1] + ri * s + (ri == 3 ? 4 : 0);
            for (int ci = 0; ci < 9; ci++) {
                int idx = rows[ri][ci], cx = r[0] + ci * s;
                boolean locked = idx < 9 && idx >= open;
                cell(c, e, cx, cy, s, op, idx == hover && !locked);
                if (locked) {
                    c.fill(cx + 1, cy + 1, s - 2, s - 2, 0xAA202020);
                    continue;
                }
                if (player == null || idx == carried) continue;
                itemIn(c, player.getInventory().getItem(idx), cx, cy, s, true);
            }
        }
    }

    /** The picked-up item follows the mouse. */
    private void drawCarried(Canvas c) {
        var player = Minecraft.getInstance().player;
        if (carried < 0 || player == null) return;
        ItemStack st = player.getInventory().getItem(carried);
        if (st.isEmpty()) {
            carried = -1;
            return;
        }
        c.pushLayer(400);
        itemIn(c, st, mouseX - 9, mouseY - 9, 18, true);
        c.popLayer();
    }

    /** Clicks on slots and inventory cells; true when handled. */
    private boolean clickSlots(float dx, float dy, int button) {
        Element e = pick(dx, dy, el -> el.type.equals("slot") || el.type.equals("inventory"));
        if (e == null) {
            if (carried >= 0) {
                carried = -1;
                return true;
            }
            return false;
        }
        var player = Minecraft.getInstance().player;
        if (e.type.equals("inventory")) {
            int idx = inventoryCell(e, dx, dy);
            if (idx < 0 || (idx < 9 && idx >= hotbarSize())) return true;
            if (player != null && !player.getInventory().getItem(idx).isEmpty()) carried = carried == idx ? -1 : idx;
            else carried = -1;
            return true;
        }
        String mode = e.get("mode");
        if (mode.equals("display") || listener == null) return true;
        int one = button == 1 ? 1 : 64;
        if (carried >= 0) {
            if (mode.equals("storage") || mode.equals("put")) listener.event(e.id, "slot_put:" + carried + ":" + one);
            carried = -1;
        } else if (!e.get("item").isBlank() && (mode.equals("storage") || mode.equals("take"))) {
            listener.event(e.id, "slot_take:" + one);
        }
        return true;
    }

    private void drawProgress(Canvas c, Element e, int[] r, float op, float dt) {
        String bind = e.get("bind");
        float target = Math.max(0, Math.min(1, bind.isEmpty() || bind.equals("none") ? e.f("value") : hudFraction(bind)));
        float v = shownValue.getOrDefault(e.id, target);
        float smooth = e.f("smooth");
        v = smooth <= 0 || !interactive ? target : v + (target - v) * Math.min(1, dt * smooth);
        shownValue.put(e.id, v);
        int radius = e.i("radius"), bw = e.i("borderWidth");
        box(c, r[0], r[1], r[2], r[3], radius, a(e.color("background"), op), a(e.color("borderColor"), op), bw);
        int ix = r[0] + bw, iy = r[1] + bw, iw = r[2] - 2 * bw, ih = r[3] - 2 * bw;
        String dir = e.get("direction");
        int fx = ix, fy = iy, fw = iw, fh = ih;
        switch (dir) {
            case "left" -> {
                fw = Math.round(iw * v);
                fx = ix + iw - fw;
            }
            case "up" -> {
                fh = Math.round(ih * v);
                fy = iy + ih - fh;
            }
            case "down" -> fh = Math.round(ih * v);
            default -> fw = Math.round(iw * v);
        }
        if (fw > 0 && fh > 0) {
            int col = a(e.color("color"), op);
            String c2 = e.get("color2");
            c.pushClip(fx, fy, fw, fh);
            if (c2.isBlank()) {
                c.round(ix, iy, iw, ih, Math.max(0, radius - bw), col);
            } else {
                int col2 = a(GuiDoc.toColor(c2), op);
                boolean vertical = dir.equals("up") || dir.equals("down");
                if (vertical) c.gradient(ix, iy, iw, ih, col2, col);
                else {
                    int steps = Math.max(1, Math.min(48, iw));
                    for (int i = 0; i < steps; i++) {
                        int sx = ix + iw * i / steps, ex = ix + iw * (i + 1) / steps;
                        c.fill(sx, iy, ex - sx, ih, Theme.lerp(col, col2, i / (float) steps));
                    }
                }
            }
            c.popClip();
        }
        String label = e.get("label");
        if (!label.isBlank()) {
            String s = unescape(label).replace("{p}", String.valueOf(Math.round(v * 100)));
            c.g.drawString(c.font, s, r[0] + (r[2] - c.font.width(s)) / 2, r[1] + (r[3] - 8) / 2, a(e.color("textColor"), op), true);
        }
    }

    private void drawInput(Canvas c, Element e, int[] r, float op) {
        boolean focused = interactive && e.id.equals(focusedInput);
        box(c, r[0], r[1], r[2], r[3], e.i("radius"), a(e.color("background"), op), a(focused ? e.color("focusColor") : e.color("borderColor"), op), 1);
        String value = interactive ? inputs.getOrDefault(e.id, "") : e.get("text");
        c.pushClip(r[0] + 2, r[1], r[2] - 4, r[3]);
        int ty = r[1] + (r[3] - 8) / 2;
        if (value.isEmpty() && !focused) c.text(e.get("placeholder"), r[0] + 5, ty, a(0xFF686D85, op));
        else {
            int tw = c.width(value);
            int shift = Math.max(0, tw - (r[2] - 12));
            c.text(value, r[0] + 5 - shift, ty, a(e.color("textColor"), op));
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) c.fill(r[0] + 5 - shift + tw + 1, ty - 1, 1, 10, a(0xFFFFFFFF, op));
        }
        c.popClip();
    }

    private void paintTooltip(Canvas c, String text, int mx, int my) {
        int w = 0;
        String[] lines = text.split("\n");
        for (String l : lines) w = Math.max(w, c.width(l));
        int h = lines.length * 10 + 6;
        int tx = Math.min(mx + 10, x + this.w - w - 12), ty = Math.min(my + 12, y + this.h - h - 4);
        c.pushLayer(300);
        c.roundBorder(tx, ty, w + 10, h, 3, 0xF0101116, 0xFF5A4FCF);
        for (int i = 0; i < lines.length; i++) c.text(lines[i], tx + 5, ty + 4 + i * 10, 0xFFE3E5EE);
        c.popLayer();
    }

    // ================================================================ input

    @Override
    public void tick() {
        super.tick();
        for (EntityPreview p : entities.values()) p.tick();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!interactive) return false;
        computeArea();
        return scrollAt(toDesignX(mx), toDesignY(my), delta, net.minecraft.client.gui.screens.Screen.hasShiftDown());
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
        if (!interactive || draggingBar == null) return interactive;
        Element e = doc.element(draggingBar);
        int[] content = e != null ? contents.get(e) : null;
        if (content == null) return true;
        int[] r = rect(e);
        float[] s = scrolls.computeIfAbsent(e.id, k -> new float[4]);
        float d = (float) ((barVertical ? ddy : ddx) / areaScale);
        if (barVertical) s[3] = s[1] = Math.max(0, Math.min(content[1] - r[3], s[3] + d * content[1] / r[3]));
        else s[2] = s[0] = Math.max(0, Math.min(content[0] - r[2], s[2] + d * content[0] / r[2]));
        return true;
    }

    private boolean pressBar(float dx, float dy) {
        for (Element e : doc.all()) {
            if (!e.type.equals("scroll") || !contents.containsKey(e) || !e.b("visible")) continue;
            int[] b = bars(e, rect(e));
            if (b[2] > 0 && dx >= b[0] - 2 && dx < b[0] + b[2] + 2 && dy >= b[1] && dy < b[1] + b[3]) {
                draggingBar = e.id;
                barVertical = true;
                return true;
            }
            if (b[6] > 0 && dx >= b[4] && dx < b[4] + b[6] && dy >= b[5] - 2 && dy < b[5] + b[7] + 2) {
                draggingBar = e.id;
                barVertical = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        if (!interactive) return false;
        computeArea();
        if (clickSlots(toDesignX(mx), toDesignY(my), button)) return true;
        if (button != 0) return true;
        draggingBar = null;
        if (pressBar(toDesignX(mx), toDesignY(my))) return true;
        Element e = elementAt(toDesignX(mx), toDesignY(my), true);
        focusedInput = null;
        pressedId = null;
        if (e == null) return true;
        if (e.type.equals("input")) {
            focusedInput = e.id;
            requestFocus();
        } else if (e.type.equals("button") && e.b("enabled")) {
            pressedId = e.id;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!interactive) return false;
        draggingBar = null;
        computeArea();
        String p = pressedId;
        pressedId = null;
        if (p == null) return true;
        Element e = elementAt(toDesignX(mx), toDesignY(my), true);
        if (e != null && e.id.equals(p)) click(e);
        return true;
    }

    public void click(Element e) {
        if (!e.b("enabled")) return;
        String sound = e.get("sound");
        if (!sound.isBlank()) org.sprautestudio.st_addon_spraute.engine.client.ProjectSounds.ui(project, sound);
        String action = e.get("action");
        if (listener == null) return;
        if (!action.equals("close")) listener.event(e.id, "click");
        if (action.equals("close") || action.equals("event_close")) listener.event(e.id, "close");
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!interactive) return false;
        if (focusedInput != null) {
            String v = inputs.getOrDefault(focusedInput, "");
            switch (key) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!v.isEmpty()) inputs.put(focusedInput, v.substring(0, v.length() - 1));
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ESCAPE -> {
                    focusedInput = null;
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                        Element e = doc.element(focusedInput);
                        int max = e != null ? e.i("maxLength") : 64;
                        String add = Minecraft.getInstance().keyboardHandler.getClipboard().replace("\n", " ");
                        inputs.put(focusedInput, (v + add).substring(0, Math.min(max, v.length() + add.length())));
                        return true;
                    }
                    return true;
                }
                default -> {
                    return true; // printable keys arrive through charTyped
                }
            }
        }
        for (Element e : doc.all()) {
            if (!e.type.equals("button") || !e.b("visible") || e.get("key").isBlank()) continue;
            int k = Specs.glfwKey(e.get("key").trim());
            if (k == key || (k >= '0' && k <= '9' && key == GLFW.GLFW_KEY_KP_0 + (k - '0'))) {
                click(e);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (!interactive || focusedInput == null || ch < ' ') return false;
        Element e = doc.element(focusedInput);
        int max = e != null ? Math.max(1, e.i("maxLength")) : 64;
        String v = inputs.getOrDefault(focusedInput, "");
        if (v.length() < max) inputs.put(focusedInput, v + ch);
        return true;
    }

    @Override
    public int cursor(int mx, int my) {
        if (!interactive) return UI.CURSOR_ARROW;
        Element e = elementAt(toDesignX(mx), toDesignY(my), true);
        if (e == null) return UI.CURSOR_ARROW;
        if (e.type.equals("slot") || e.type.equals("inventory")) return UI.CURSOR_HAND;
        return e.type.equals("input") ? UI.CURSOR_IBEAM : e.type.equals("button") && e.b("enabled") ? UI.CURSOR_HAND : UI.CURSOR_ARROW;
    }

    public boolean hasFocusedInput() {
        return focusedInput != null;
    }

    /** All element types the editor palette offers, in order. */
    public static List<GuiSchema.Type> palette() {
        return GuiSchema.types();
    }
}
