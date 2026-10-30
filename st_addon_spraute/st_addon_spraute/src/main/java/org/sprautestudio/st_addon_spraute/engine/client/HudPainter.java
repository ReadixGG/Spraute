package org.sprautestudio.st_addon_spraute.engine.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.sprautestudio.st_addon_spraute.engine.EngineConfig;

/**
 * Layout and drawing of HUD parts, shared by the game and the IDE's visual HUD editor.
 * Positions are in GUI pixels of a screen {@code W x H}.
 */
public final class HudPainter {
    private HudPainter() {}

    /** Values a HUD shows. */
    public static final class State {
        public float hp = 20, maxHp = 20;
        public int armor, food = 20, air = 300, maxAir = 300;
        public float exp;
        public int level;
        public int coins;
        /** How much is needed, shown as have/need; -1 — nothing. */
        public int coinsNeed = -1;
        public int selected;
        public ItemStack[] hotbar = new ItemStack[9];
        public ItemStack offhand = ItemStack.EMPTY;
        /** Unlocked hotbar slots (Spraute's slot lock). */
        public int hotbarSize = 9;
        /** Spraute's health system is on: its round bar replaces the hearts. */
        public boolean stHealth;
        /** Air is shown only while it runs out, like in vanilla. */
        public boolean showAir = true;

        public State() {
            java.util.Arrays.fill(hotbar, ItemStack.EMPTY);
        }
    }

    public static final ResourceLocation CHAT_BACK = new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "textures/gui/log/back.png");
    public static final ResourceLocation CHAT_FRAME = new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "textures/gui/log/frame.png");
    private static final ResourceLocation COIN = new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "textures/gui/coin.png");
    private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");

    // ================================================================== layout

    public static int[] anchorPoint(String anchor, int sw, int sh) {
        int ax = switch (anchor) {
            case "T", "C", "B" -> sw / 2;
            case "TR", "R", "BR" -> sw;
            default -> 0;
        };
        int ay = switch (anchor) {
            case "L", "C", "R" -> sh / 2;
            case "BL", "B", "BR" -> sh;
            default -> 0;
        };
        return new int[]{ax, ay};
    }

    private static boolean vanillaDrawn(EngineConfig cfg, String key, State s) {
        EngineConfig.Element e = cfg.element(key);
        if (key.equals("health") && s.stHealth) return false;
        return e.visible && EngineConfig.gameDrawn(e.style);
    }

    /** The game's own place of a part. */
    public static int[] defaultRect(EngineConfig cfg, String key, int sw, int sh, State s) {
        int cx = sw / 2;
        return switch (key) {
            case "health" -> s.stHealth ? new int[]{10, sh - 90, 68, 67} : new int[]{cx - 91, sh - 39, 81, 9};
            case "armor" -> new int[]{cx - 91, sh - 39 - (vanillaDrawn(cfg, "health", s) ? 10 : 0), 81, 9};
            case "food" -> new int[]{cx + 10, sh - 39, 81, 9};
            case "air" -> new int[]{cx + 10, sh - 39 - (vanillaDrawn(cfg, "food", s) ? 10 : 0), 81, 9};
            case "exp" -> new int[]{cx - 91, sh - 29, 182, 5};
            case "hotbar" -> new int[]{cx - 91, sh - 22, 182, 22};
            case "coins" -> new int[]{sw - 62, sh - 24, 56, 18};
            default -> new int[]{0, 0, 10, 10};
        };
    }

    /** Where a part is drawn: its own size for custom styles, its custom place if set. */
    public static int[] rect(EngineConfig cfg, String key, int sw, int sh, State s) {
        EngineConfig.Element e = cfg.element(key);
        int[] d = defaultRect(cfg, key, sw, sh, s);
        int w = d[2], h = d[3];
        if (!EngineConfig.gameDrawn(e.style)) {
            int[] cs = customSize(e, key);
            w = cs[0];
            h = cs[1];
        }
        if (!e.customPos) {
            // custom sizes grow from the game's place: bottom-aligned at the screen bottom
            int y = d[1] + (d[1] + d[3] > sh * 2 / 3 ? d[3] - h : 0);
            return new int[]{d[0], y, w, h};
        }
        int[] a = anchorPoint(e.anchor, sw, sh);
        int[] pv = anchorPoint(e.anchor, w, h);
        float ox = e.percent ? e.px * sw / 100f : e.x, oy = e.percent ? e.py * sh / 100f : e.y;
        return new int[]{Math.round(a[0] + ox - pv[0]), Math.round(a[1] + oy - pv[1]), w, h};
    }

    /** Stores an absolute rectangle as the part's offset (inverse of {@link #rect}). */
    public static void place(EngineConfig.Element e, int[] r, int sw, int sh) {
        int[] a = anchorPoint(e.anchor, sw, sh);
        int[] pv = anchorPoint(e.anchor, r[2], r[3]);
        int ox = r[0] + pv[0] - a[0], oy = r[1] + pv[1] - a[1];
        e.x = ox;
        e.y = oy;
        e.px = Math.round(ox * 1000f / sw) / 10f;
        e.py = Math.round(oy * 1000f / sh) / 10f;
    }

    /** Size of a part in a custom style. */
    public static int[] customSize(EngineConfig.Element e, String key) {
        return switch (e.style) {
            case "icons" -> {
                int n = Math.max(1, e.count), s = Math.max(2, e.height);
                yield new int[]{n * s + (n - 1) * e.gap, s};
            }
            case "slots" -> {
                int n = 9, s = Math.max(4, e.height);
                yield new int[]{n * s + (n - 1) * e.gap, s};
            }
            case "icon" -> new int[]{Math.max(4, e.height) + 4 + 30, Math.max(4, e.height)};
            default -> new int[]{Math.max(2, e.width), Math.max(2, e.height)};
        };
    }

    /** Offset that moves the game's own drawing of a part to its custom place. */
    public static int[] shift(EngineConfig cfg, String key, int sw, int sh, State s) {
        int[] d = defaultRect(cfg, key, sw, sh, s), r = rect(cfg, key, sw, sh, s);
        return new int[]{r[0] - d[0], r[1] - d[1]};
    }

    // ================================================================== drawing

    /** Fraction a bar shows and the text it may print. */
    private static float fraction(String key, State s) {
        return switch (key) {
            case "health" -> s.maxHp <= 0 ? 0 : s.hp / s.maxHp;
            case "armor" -> s.armor / 20f;
            case "food" -> s.food / 20f;
            case "air" -> s.maxAir <= 0 ? 0 : Math.max(0, s.air) / (float) s.maxAir;
            case "exp" -> s.exp;
            default -> 0;
        };
    }

    private static String label(String key, State s) {
        return switch (key) {
            case "health" -> Math.round(s.hp) + " / " + Math.round(s.maxHp);
            case "armor" -> String.valueOf(s.armor);
            case "food" -> String.valueOf(s.food);
            case "air" -> String.valueOf(Math.max(0, s.air / 15));
            case "exp" -> String.valueOf(s.level);
            default -> "";
        };
    }

    /** Draws a part in its custom style at {@code r}. */
    public static void custom(GuiGraphics g, Font font, EngineConfig cfg, String key, int[] r, State s) {
        EngineConfig.Element e = cfg.element(key);
        String p = cfg.project;
        int x = r[0], y = r[1], w = r[2], h = r[3];
        switch (e.style) {
            case "bar" -> {
                float frac = Math.max(0, Math.min(1, fraction(key, s)));
                Skins.draw(g, p, e.back, x, y, w, h);
                if (!e.back.isSet() && !e.frame.isSet()) g.fill(x, y, x + w, y + h, 0x88000000);
                int pd = Math.max(0, Math.min(e.pad, Math.min(w, h) / 2));
                if (e.fill.isSet()) Skins.drawFill(g, p, e.fill, x + pd, y + pd, w - 2 * pd, h - 2 * pd, frac, e.fillDir);
                else {
                    int[] part = Skins.filledPart(x + pd, y + pd, w - 2 * pd, h - 2 * pd, frac, e.fillDir);
                    g.fill(part[0], part[1], part[0] + part[2], part[1] + part[3], barColor(key));
                }
                Skins.draw(g, p, e.frame, x, y, w, h);
                if (e.text) {
                    String t = label(key, s);
                    if (key.equals("exp")) g.drawString(font, t, x + (w - font.width(t)) / 2, y - 9, e.textColor, true);
                    else if (h >= 8) g.drawString(font, t, x + (w - font.width(t)) / 2, y + (h - 8) / 2 + 1, e.textColor, true);
                }
            }
            case "icons" -> {
                int n = Math.max(1, e.count), size = h, step = size + e.gap;
                int halves = Math.round(Math.max(0, Math.min(1, fraction(key, s))) * n * 2);
                for (int i = 0; i < n; i++) {
                    int idx = e.reverse ? n - 1 - i : i;
                    int ix = x + idx * step;
                    int v = halves - i * 2;
                    boolean skinned = e.full.isSet() || e.half.isSet() || e.empty.isSet();
                    if (!skinned) {
                        g.fill(ix, y, ix + size, y + size, 0x66000000);
                        if (v >= 1) g.fill(ix + 1, y + 1, ix + (v >= 2 ? size - 1 : size / 2), y + size - 1, barColor(key));
                        continue;
                    }
                    Skins.draw(g, p, e.empty, ix, y, size, size);
                    if (v >= 2) Skins.draw(g, p, e.full, ix, y, size, size);
                    else if (v == 1) {
                        if (e.half.isSet()) Skins.draw(g, p, e.half, ix, y, size, size);
                        else Skins.drawFill(g, p, e.full, ix, y, size, size, 0.5f);
                    }
                }
                if (e.text) g.drawString(font, label(key, s), x + w + 3, y + (h - 8) / 2 + 1, e.textColor, true);
            }
            case "slots" -> {
                int size = h, step = size + e.gap;
                for (int i = 0; i < 9; i++) {
                    int sx = x + i * step;
                    if (i >= s.hotbarSize) continue;
                    boolean locked = false;
                    EngineConfig.Skin sk = e.back;
                    if (sk.isSet()) Skins.draw(g, p, sk, sx, y, size, size);
                    else {
                        g.fill(sx, y, sx + size, y + size, 0xAA000000);
                        g.renderOutline(sx, y, size, size, 0x66FFFFFF);
                    }
                    if (locked) {
                        if (!e.fill.isSet()) g.fill(sx + 1, y + 1, sx + size - 1, y + size - 1, 0x99202020);
                        continue;
                    }
                    ItemStack st = s.hotbar[i];
                    if (st != null && !st.isEmpty()) {
                        int ix = sx + (size - 16) / 2, iy = y + (size - 16) / 2;
                        g.renderItem(st, ix, iy);
                        g.renderItemDecorations(font, st, ix, iy);
                    }
                }
                int sel = Math.max(0, Math.min(8, s.selected));
                int sx = x + sel * step;
                if (e.frame.isSet()) Skins.draw(g, p, e.frame, sx - 1, y - 1, size + 2, size + 2);
                else g.renderOutline(sx - 1, y - 1, size + 2, size + 2, 0xFFFFFFFF);
                if (s.offhand != null && !s.offhand.isEmpty()) {
                    int ox = x - size - 6;
                    if (e.back.isSet()) Skins.draw(g, p, e.back, ox, y, size, size);
                    else g.fill(ox, y, ox + size, y + size, 0xAA000000);
                    g.renderItem(s.offhand, ox + (size - 16) / 2, y + (size - 16) / 2);
                    g.renderItemDecorations(font, s.offhand, ox + (size - 16) / 2, y + (size - 16) / 2);
                }
            }
            case "icon" -> {
                String t = String.valueOf(s.coins);
                int size = h, tw = font.width(t);
                int bw = size + 4 + tw + (e.back.isSet() ? 8 : 0);
                // right-aligned inside the rectangle so the number grows to the left
                int bx = x + w - bw;
                if (e.back.isSet()) Skins.draw(g, p, e.back, bx, y - 3, bw, size + 6);
                int pd = e.back.isSet() ? 4 : 0;
                if (e.fill.isSet()) Skins.draw(g, p, e.fill, bx + pd, y, size, size);
                else g.fill(bx + pd + 2, y + 2, bx + pd + size - 2, y + size - 2, 0xFF7FE0FF);
                g.drawString(font, t, bx + pd + size + 4, y + (size - 8) / 2 + 1, e.textColor, true);
            }
            default -> {
            }
        }
    }

    private static int barColor(String key) {
        return switch (key) {
            case "armor" -> 0xFFB8C4D0;
            case "food" -> 0xFFC88A3C;
            case "air" -> 0xFF5DA9F6;
            case "exp" -> 0xFF80FF20;
            default -> 0xFFD94848;
        };
    }

    private static final ResourceLocation WIDE_HOTBAR = new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "textures/gui/slots/hotbar.png");
    private static final ResourceLocation WIDE_SELECTION = new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "textures/gui/slots/selection.png");

    /** The game's own look of a part (for the editor preview; in the game the game draws it). */
    public static void standard(GuiGraphics g, Font font, String key, String style, int[] r, State s) {
        if (key.equals("hotbar") && style.equals("classic")) {
            int x = r[0], y = r[1];
            RenderSystem.enableBlend();
            g.blit(WIDE_HOTBAR, x - 2, y - 4, 0, 0, 186, 26, 186, 26);
            g.blit(WIDE_SELECTION, x - 1 + s.selected * 20 + 2, y - 1, 0, 0, 24, 23, 24, 23);
            for (int i = 0; i < 9; i++) {
                ItemStack st = s.hotbar[i];
                if (i >= s.hotbarSize || st == null || st.isEmpty()) continue;
                g.renderItem(st, x + 5 + i * 20, y + 3);
                g.renderItemDecorations(font, st, x + 5 + i * 20, y + 3);
            }
            return;
        }
        standard(g, font, key, r, s);
    }

    public static void standard(GuiGraphics g, Font font, String key, int[] r, State s) {
        RenderSystem.enableBlend();
        int x = r[0], y = r[1];
        switch (key) {
            case "health" -> {
                if (s.stHealth) vessel(g, font, x, y, s);
                else icons(g, x, y, Math.round(s.hp), 16, 52, 61, 0, false);
            }
            case "armor" -> icons(g, x, y, s.armor, 16, 34, 25, 9, false);
            case "food" -> icons(g, x, y, s.food, 16, 52, 61, 27, true);
            case "air" -> {
                int bubbles = (int) Math.ceil(Math.max(0, s.air) * 10.0 / Math.max(1, s.maxAir));
                for (int i = 0; i < 10; i++) if (i < bubbles) g.blit(ICONS, x + 72 - i * 8, y, 16, 18, 9, 9);
            }
            case "exp" -> {
                g.blit(ICONS, x, y, 0, 64, 182, 5);
                int fw = Math.round(182 * s.exp);
                if (fw > 0) g.blit(ICONS, x, y, 0, 69, fw, 5);
                String t = String.valueOf(s.level);
                int tx = x + (182 - font.width(t)) / 2, ty = y - 6;
                g.drawString(font, t, tx + 1, ty, 0xFF000000, false);
                g.drawString(font, t, tx - 1, ty, 0xFF000000, false);
                g.drawString(font, t, tx, ty + 1, 0xFF000000, false);
                g.drawString(font, t, tx, ty - 1, 0xFF000000, false);
                g.drawString(font, t, tx, ty, 0xFF80FF20, false);
            }
            case "hotbar" -> {
                // only the open slots: the bar is cut to them, with its right end
                int barW = s.hotbarSize * 20 + 1;
                g.blit(WIDGETS, x, y, 0, 0, barW, 22);
                g.blit(WIDGETS, x + barW, y, 181, 0, 1, 22);
                g.blit(WIDGETS, x - 1 + s.selected * 20, y - 1, 0, 22, 24, 24);
                for (int i = 0; i < 9; i++) {
                    ItemStack st = s.hotbar[i];
                    if (i >= s.hotbarSize) continue;
                    if (st != null && !st.isEmpty()) {
                        g.renderItem(st, x + 3 + i * 20, y + 3);
                        g.renderItemDecorations(font, st, x + 3 + i * 20, y + 3);
                    }
                }
            }
            case "coins" -> {
                String have = shortNumber(s.coins);
                g.blit(COIN, x + r[2] - 18, y + 1, 0, 0, 16, 16, 16, 16);
                int tx = x + r[2] - 22;
                if (s.coinsNeed >= 0) {
                    String need = "/" + shortNumber(s.coinsNeed);
                    tx -= font.width(need);
                    g.drawString(font, need, tx, y + 5, 0xFFFFFFFF, true);
                }
                g.drawString(font, have, tx - font.width(have), y + 5, s.coinsNeed >= 0 && s.coins < s.coinsNeed ? 0xFFFF5555 : 0xFFFFFFFF, true);
            }
            default -> {
            }
        }
    }

    /** 12500 — 12.5k, 3000000 — 3M. */
    private static String shortNumber(int n) {
        if (n < 10000) return String.valueOf(n);
        if (n < 1000000) return Math.round(n / 100f) / 10f + "k";
        return Math.round(n / 100000f) / 10f + "M";
    }

    /** The health vessel: filled from below, two waves running opposite ways at the level; the maximum written under it. */
    private static void vessel(GuiGraphics g, Font font, int x, int y, State s) {
        int size = 40;
        int ox = x + 14, oy = y + 6;
        float share = s.maxHp <= 0 ? 0 : Math.max(0, Math.min(1, s.hp / s.maxHp));
        float t = System.nanoTime() / 1e9f;
        float r = size / 2f;
        for (int cx = 0; cx < size; cx++) {
            float dx = cx + 0.5f - r;
            int half = (int) Math.sqrt(Math.max(0, r * r - dx * dx));
            int top = oy + Math.round(r) - half, bottom = oy + Math.round(r) + half;
            g.fill(ox + cx, top, ox + cx + 1, bottom, 0xC0101218);
            float level = oy + size * (1 - share);
            int front = Math.round(level + (float) Math.sin(cx * 0.35f + t * 3) * 1.5f);
            int back = Math.round(level + (float) Math.sin(-cx * 0.28f + t * 2.2f) * 1.5f);
            if (share > 0 && Math.max(back, top) < bottom) g.fill(ox + cx, Math.max(back, top), ox + cx + 1, bottom, 0x80E05555);
            if (share > 0 && Math.max(front, top) < bottom) g.fill(ox + cx, Math.max(front, top), ox + cx + 1, bottom, 0xFFC02F2F);
        }
        String max = String.valueOf(Math.round(s.maxHp));
        g.drawString(font, max, ox + (size - font.width(max)) / 2, oy + size + 2, 0xFFFFFFFF, true);
    }

    /** A vanilla row of 10 icons out of icons.png: container, full and half sprite columns. */
    private static void icons(GuiGraphics g, int x, int y, int halves, int uBack, int uFull, int uHalf, int v, boolean fromRight) {
        for (int i = 0; i < 10; i++) {
            int ix = fromRight ? x + 72 - i * 8 : x + i * 8;
            g.blit(ICONS, ix, y, uBack, v, 9, 9);
            if (halves >= i * 2 + 2) g.blit(ICONS, ix, y, uFull, v, 9, 9);
            else if (halves == i * 2 + 1) g.blit(ICONS, ix, y, uHalf, v, 9, 9);
        }
    }

    // ================================================================== chat log

    /** Where Spraute draws the chat history panel on a screen of the given size. */
    public static int[] chatRect(int sw, int sh) {
        float k = 1.1043478f;
        int w = Math.round(sh * k);
        return new int[]{Math.round(sw / 2f - sh / 2f * k), 0, w, sh};
    }

    public static void chatBack(GuiGraphics g, EngineConfig cfg, int x, int y, int w, int h) {
        if (cfg.chatLog.back.isSet()) Skins.draw(g, cfg.project, cfg.chatLog.back, x, y, w, h);
        else Skins.draw(g, CHAT_BACK, null, x, y, w, h);
    }

    public static void chatFrame(GuiGraphics g, EngineConfig cfg, int x, int y, int w, int h) {
        if (cfg.chatLog.frame.isSet()) Skins.draw(g, cfg.project, cfg.chatLog.frame, x, y, w, h);
        else Skins.draw(g, CHAT_FRAME, null, x, y, w, h);
    }
}
