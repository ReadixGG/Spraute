package org.sprautestudio.st_addon_spraute.shop.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.sprautestudio.st_addon_spraute.engine.EngineConfig;
import org.sprautestudio.st_addon_spraute.engine.client.Skins;
import org.sprautestudio.st_addon_spraute.shop.ShopDef;
import org.sprautestudio.st_addon_spraute.shop.ShopView;

import java.util.List;

/**
 * The shop window as Spraute draws its own: the goods on the left (cards in rows of five,
 * scrolled with the wheel, the price under each), the chosen one on the right — large, with its
 * price, how many, the total and the buy / sell button. Same textures and places; the engine settings
 * may swap the textures. Coordinates are those of Spraute's 445×262 window.
 */
public class ShopPanel {
    public static final int W = 445, H = 262;
    private static final int COLS = 5, STEP_X = 36, STEP_Y = 50, CELL_W = 30, CELL_H = 43;
    private static final int LOTS_X = 40, LOTS_Y = 71, CLIP_X = 38, CLIP_Y = 66, CLIP_W = 182, CLIP_H = 140;

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "textures/gui/store/" + name + ".png");
    }

    private static final ResourceLocation BASE_1 = tex("list"), BASE_2 = tex("card"), CELL = tex("cell"), CELL_GLOW = tex("cell_hot"),
            LOCKED = tex("cell_locked"), SOLD = tex("cell_sold"), BUY = tex("buy"), BUY_LIGHT = tex("buy_hot"), SLIDER = tex("slider");
    private static final int WHITE = 0xFFFFFFFF, GREEN = 0xFF55FF55, RED = 0xFFFF5555, TYPE = 0xFFE2D7C4;

    public ShopView view;
    public int qty = 1;
    /** How far the goods are scrolled down (pixels of the window). */
    public double scroll;
    public String selected;
    /** Where the window is (its top left) and the zoom it is drawn with. */
    public int x, y;
    /** The window's place before the shifts of its parts; the look whose shifts apply. */
    public int bx, by;
    public EngineConfig.Shop style;

    public static final java.util.List<String> PARTS = java.util.List.of("window", "list", "card", "title", "money");

    public static String partTitle(String part) {
        return switch (part) {
            case "window" -> "всё окно";
            case "list" -> "список товаров";
            case "card" -> "карточка товара";
            case "title" -> "название";
            default -> "деньги";
        };
    }

    public int[] off(String part) {
        int[] o = style != null ? style.offsets.get(part) : null;
        return o != null && o.length == 2 ? o : new int[]{0, 0};
    }

    /** Puts x, y where a part of the window is drawn (its shift and the whole window's). */
    private void at(String part) {
        int[] w = off("window"), o = part.equals("window") ? new int[]{0, 0} : off(part);
        x = bx + w[0] + o[0];
        y = by + w[1] + o[1];
    }

    /** Places the window by its top left corner (the editor's preview). */
    public void place(int px, int py) {
        bx = px;
        by = py;
        at("window");
    }

    /** The rectangle of a part in window coordinates (for dragging it in the settings). */
    public int[] partRect(String part, Font font) {
        at(part);
        int[] r = switch (part) {
            case "list" -> new int[]{x, y + 25, 254, 212};
            case "card" -> new int[]{x + 269, y, 176, 262};
            case "title" -> new int[]{x + 14, y + 8, font.width(view.title) + 4, 12};
            case "money" -> {
                int bw = font.width(String.valueOf(view.balance));
                yield new int[]{x + 238 - bw - 15, y + 6, bw + 17, 13};
            }
            default -> new int[]{x, y, W, H};
        };
        at("window");
        return r;
    }

    /** The part under a point (the small ones first), or null. */
    public String partAt(double mx, double my, Font font) {
        for (String p : java.util.List.of("title", "money", "card", "list")) if (in(partRect(p, font), mx, my)) return p;
        return null;
    }
    public float scale = 1;
    public long messageAt, openedAt = System.currentTimeMillis();
    /** The item under the mouse after render (for the tooltip). */
    public ItemStack hoveredStack;

    public ShopPanel(ShopView view) {
        setView(view);
    }

    public void setView(ShopView v) {
        this.view = v;
        if (selected == null || line(selected) == null) {
            List<ShopView.Line> l = lines();
            selected = l.isEmpty() ? null : l.get(0).id;
        }
        if (!v.message.isEmpty()) messageAt = System.currentTimeMillis();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** Centres the window; drawn as at GUI scale 4 like Spraute's, smaller when it does not fit. */
    public void layout(int sw, int sh, double guiScale) {
        scale = (float) Math.min(4.0 / Math.max(1, guiScale), Math.min((sw - 8) / (double) W, (sh - 8) / (double) H));
        place(Math.round((sw / scale - W) / 2), Math.round((sh / scale - H) / 2) + 5);
    }

    public void layout(int sw, int sh) {
        scale = 1;
        place((sw - W) / 2, (sh - H) / 2 + 5);
    }

    // ------------------------------------------------------------------ data

    public List<ShopView.Line> lines() {
        return view.lines;
    }

    public ShopView.Line line(String id) {
        if (id == null) return null;
        for (ShopView.Line l : view.lines) if (l.id.equals(id)) return l;
        return null;
    }

    static ItemStack stack(String id, int count) {
        return org.sprautestudio.st_addon_spraute.runtime.ItemSpec.stack(id, count);
    }

    private ItemStack money() {
        if (!view.currency.equals("coins")) return stack(view.currency, 1);
        ItemStack s = stack("minecraft:gold_nugget", 1);
        return s.isEmpty() ? new ItemStack(Items.GOLD_NUGGET) : s;
    }

    public static String title(ShopView.Line l) {
        return l.name != null && !l.name.isEmpty() ? l.name : stack(l.item, 1).getHoverName().getString();
    }

    /** Most deals at once, as Spraute's arrows allow (and what the player can do). */
    public int maxQty(ShopView.Line l) {
        int m = 99;
        if (l.stock >= 0) m = Math.min(m, l.stock);
        if (l.left >= 0) m = Math.min(m, l.left);
        return Math.max(1, m);
    }

    public boolean affordable(ShopView.Line l, int n) {
        if (l.type.equals("sell")) return l.have >= l.count * n;
        return view.balance >= l.price * n;
    }

    private double maxScroll() {
        int rows = (lines().size() + COLS - 1) / COLS;
        return Math.max(0, rows * STEP_Y - CLIP_H);
    }

    // ------------------------------------------------------------------ places (window coordinates)

    public int[] cellRect(int i) {
        return new int[]{x + LOTS_X + (i % COLS) * STEP_X, (int) Math.round(y + LOTS_Y + (i / COLS) * STEP_Y - scroll), CELL_W, CELL_H};
    }

    private static final int[][] ARROWS = {{-5, 317, 102, 12, 15}, {-1, 342, 103, 9, 13}, {1, 365, 103, 9, 13}, {5, 387, 102, 12, 15}};

    private int[] arrowRect(int i) {
        int[] a = ARROWS[i];
        return new int[]{x + a[1], y + a[2], a[3], a[4]};
    }

    public int[] dealRect() {
        return new int[]{x + 313, y + 195, 88, 14};
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    /** Screen mouse to window coordinates. */
    public double wx(double mx) {
        return mx / scale;
    }

    public double wy(double my) {
        return my / scale;
    }

    // ------------------------------------------------------------------ input (window coordinates)

    /** What is under the point: "cell:ID", "arrow:N", "deal" or null. */
    public String hit(double mx, double my) {
        try {
            at("list");
            if (mx >= x + CLIP_X && mx < x + CLIP_X + CLIP_W && my >= y + CLIP_Y && my < y + CLIP_Y + CLIP_H) {
                List<ShopView.Line> ls = lines();
                for (int i = 0; i < ls.size(); i++) if (in(cellRect(i), mx, my)) return "cell:" + ls.get(i).id;
            }
            at("card");
            for (int i = 0; i < ARROWS.length; i++) if (in(arrowRect(i), mx, my)) return "arrow:" + ARROWS[i][0];
            if (in(dealRect(), mx, my)) return "deal";
            return null;
        } finally {
            at("window");
        }
    }

    /** Clicks that change the window; true when the deal button was pressed and the deal can go. */
    public boolean click(double mx, double my) {
        String h = hit(mx, my);
        if (h == null) return false;
        ShopView.Line l = line(selected);
        if (h.startsWith("cell:")) {
            String id = h.substring(5);
            if (!id.equals(selected)) qty = 1;
            selected = id;
        } else if (h.startsWith("arrow:") && l != null) {
            qty = Math.max(1, Math.min(maxQty(l), qty + Integer.parseInt(h.substring(6))));
        } else if (h.equals("deal") && l != null) {
            return l.state.equals("ok") && affordable(l, qty);
        }
        return false;
    }

    public void scroll(double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - delta * 20));
    }

    // ------------------------------------------------------------------ drawing

    private static void blit(GuiGraphics g, String project, EngineConfig.Skin skin, ResourceLocation def, int x, int y, int w, int h) {
        if (skin != null && skin.isSet()) Skins.draw(g, project, skin, x, y, w, h);
        else g.blit(def, x, y, 0, 0, w, h, w, h);
    }

    private static void text(GuiGraphics g, Font font, String s, float x, float y, float scale, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, x / scale, y / scale, color, shadow);
        g.pose().popPose();
    }

    private static void item(GuiGraphics g, ItemStack s, float x, float y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(s, 0, 0);
        g.pose().popPose();
    }

    /** A coin and "x5", like Spraute's sparks; returns where it ends. */
    private float price(GuiGraphics g, Font font, ItemStack coin, float px, float py, int amount, int color) {
        item(g, coin, px, py - 3, 0.6f);
        String s = "x" + amount;
        text(g, font, s, px + 11, py, 0.75f, color, false);
        return px + 11 + font.width(s) * 0.75f;
    }

    /** {@code mx}, {@code my} in window coordinates (screen / scale). */
    public void render(GuiGraphics g, Font font, EngineConfig cfg, double mx, double my) {
        EngineConfig.Shop c = cfg.shop;
        style = c;
        String project = cfg.project;
        float k = Math.min(1, (System.currentTimeMillis() - openedAt) / 500f);
        float ease = 1 - (1 - k) * (1 - k);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        // the window slides in like Spraute's
        g.pose().translate(0, -(1 - ease) * 30, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, ease);
        at("list");
        blit(g, project, c.panel, BASE_1, x, y + 25, 254, 212);
        at("card");
        blit(g, project, c.detail, BASE_2, x + 254 + 15, y, 176, 262);
        RenderSystem.setShaderColor(1, 1, 1, 1);

        // the name of the shop and the player's money above the goods
        at("title");
        text(g, font, view.title, x + 16, y + 11, 1f, c.titleColor, true);
        at("money");
        ItemStack coin = money();
        String bal = String.valueOf(view.balance);
        float bw = font.width(bal);
        item(g, coin, x + 238 - bw - 13, y + 7, 0.75f);
        text(g, font, bal, x + 238 - bw, y + 11, 1f, c.priceColor, true);

        // the goods: cards in rows of five
        at("list");
        List<ShopView.Line> ls = lines();
        int[] clip = org.sprautestudio.st_addon_spraute.engine.client.ChatHeads.screenRect(g, x + CLIP_X, y + CLIP_Y, CLIP_W, CLIP_H);
        g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
        hoveredStack = null;
        for (int i = 0; i < ls.size(); i++) {
            ShopView.Line l = ls.get(i);
            int[] r = cellRect(i);
            if (r[1] + r[3] < y + CLIP_Y || r[1] > y + CLIP_Y + CLIP_H) continue;
            boolean sel = l.id.equals(selected);
            ResourceLocation t = l.state.equals("locked") ? LOCKED : l.state.equals("soldout") ? SOLD : sel ? CELL_GLOW : CELL;
            EngineConfig.Skin skin = l.state.equals("ok") || l.state.equals("limit") ? (sel ? c.cellOn : c.cell)
                    : l.state.equals("locked") ? c.locked : c.sold;
            blit(g, project, skin, t, r[0], r[1], CELL_W, CELL_H);
            ItemStack s = stack(l.item, l.count);
            g.renderItem(s, r[0] + 7, r[1] + 8);
            if (l.count > 1) g.renderItemDecorations(font, s, r[0] + 7, r[1] + 8);
            // the price under the card
            String p = l.type.equals("trade") && l.price == 0 ? "ОБМЕН" : String.valueOf(l.price);
            int col = l.type.equals("sell") ? c.sellColor : TYPE;
            if (l.type.equals("trade") && l.price == 0) text(g, font, p, r[0] + 15 - font.width(p) * 0.25f, r[1] + 36, 0.5f, col, true);
            else {
                float pw = font.width(p) * 0.5f + 6;
                item(g, coin, r[0] + 15 - pw / 2 - 1, r[1] + 34.5f, 0.375f);
                text(g, font, p, r[0] + 15 - pw / 2 + 6, r[1] + 36, 0.5f, col, true);
            }
            if (in(r, mx, my) && my >= y + CLIP_Y && my < y + CLIP_Y + CLIP_H) hoveredStack = s;
        }
        g.disableScissor();
        if (ls.isEmpty()) text(g, font, "Здесь пока пусто", x + LOTS_X, y + LOTS_Y + 4, 0.75f, c.mutedColor, false);
        // the scroll bar in the strip on the right of the goods
        if (maxScroll() > 0) {
            double share = scroll / maxScroll();
            blit(g, project, c.slider, SLIDER, x + 239, (int) Math.round(y + 48 + share * (212 - 48 - 12)), 9, 10);
        }

        // the chosen offer
        at("card");
        ShopView.Line l = line(selected);
        if (l != null) {
            ItemStack s = stack(l.item, 1);
            item(g, s, x + 357 - 16, y + 71 - 16, 2f);
            if (in(new int[]{x + 341, y + 55, 32, 32}, mx, my)) hoveredStack = stack(l.item, l.count);
            text(g, font, "x" + qty, x + 360, y + 75, 1.5f, WHITE, true);
            int n = Math.max(1, qty);
            int yOff = 10;
            String name = title(l);
            float ts = Math.max(0.5f, Math.min(1, 155f / Math.max(1, font.width(name)))) * 0.75f;
            text(g, font, name, x + 296, y + yOff + 108, ts, WHITE, false);
            boolean sell = l.type.equals("sell");
            text(g, font, sell ? "Торговец заплатит: " : "Стоимость товара: ", x + 296, y + yOff + 117, 0.75f, WHITE, false);
            float end = l.price > 0 || l.costs.isEmpty() ? price(g, font, coin, x + 296, y + 137, l.price, sell ? c.sellColor : c.priceColor) : x + 296;
            for (ShopDef.Stack cs : l.costs) {
                item(g, stack(cs.item, 1), end + 3, y + 134, 0.6f);
                text(g, font, "x" + cs.count, end + 14, y + 137, 0.75f, WHITE, true);
                end += 14 + font.width("x" + cs.count) * 0.75f;
            }
            text(g, font, "Количество: " + n + " по " + l.count + " (" + n * l.count + " шт.)", x + 296, y + yOff + 126 + 16, 0.75f, WHITE, false);
            text(g, font, sell ? "Итого получите: " : "Итоговая стоимость: ", x + 296, y + yOff + 135 + 16, 0.75f, WHITE, false);
            float end2 = l.price > 0 || l.costs.isEmpty() ? price(g, font, coin, x + 296, y + 171, l.price * n, sell ? c.sellColor : c.priceColor) : x + 296;
            for (ShopDef.Stack cs : l.costs) {
                item(g, stack(cs.item, 1), end2 + 3, y + 168, 0.6f);
                text(g, font, "x" + cs.count * n, end2 + 14, y + 171, 0.75f, WHITE, true);
                end2 += 14 + font.width("x" + cs.count * n) * 0.75f;
            }
            // what will happen, or what just did
            String status;
            int sc;
            if (!view.message.isEmpty() && System.currentTimeMillis() - messageAt < 3000) {
                status = view.message;
                sc = view.ok ? GREEN : RED;
            } else if (l.state.equals("locked")) {
                status = l.lockText.isEmpty() ? "Товар заблокирован!" : l.lockText;
                sc = RED;
            } else if (l.state.equals("soldout")) {
                status = sell ? "Товар не принимается!" : "Товар закончился!";
                sc = RED;
            } else if (l.state.equals("limit")) {
                status = "Больше нельзя!";
                sc = RED;
            } else if (sell) {
                status = affordable(l, n) ? "Готово к продаже!" : "Недостаточно товара!";
                sc = affordable(l, n) ? GREEN : RED;
            } else {
                status = affordable(l, n) ? (l.type.equals("trade") ? "Готово к обмену!" : "Готово к покупке!") : "Недостаточно средств!";
                sc = affordable(l, n) ? GREEN : RED;
            }
            text(g, font, font.plainSubstrByWidth(status, (int) (150 / 0.75f)), x + 296, y + yOff + 175, 0.75f, sc, false);
            // the arrows: -5 -1 +1 +5
            for (int i = 0; i < ARROWS.length; i++) {
                int[] r = arrowRect(i);
                int step = ARROWS[i][0];
                boolean hov = in(r, mx, my);
                blit(g, project, c.arrow(step, hov), tex("arrow_" + step + (hov ? "_glow" : "")), r[0], r[1], r[2], r[3]);
            }
            // the description in the little window at the bottom
            if (!l.description.isEmpty()) {
                List<FormattedCharSequence> lines = font.split(Component.literal(l.description), (int) (106 / 0.6f));
                float dy = y + 220;
                for (int i = 0; i < Math.min(3, lines.size()); i++) {
                    g.pose().pushPose();
                    g.pose().scale(0.6f, 0.6f, 1);
                    g.drawString(font, lines.get(i), (x + 304) / 0.6f, dy / 0.6f, c.mutedColor, false);
                    g.pose().popPose();
                    dy += 6.5f;
                }
            }
        }
        // the buy / sell button
        int[] d = dealRect();
        boolean hov = l != null && in(d, mx, my);
        EngineConfig.Skin bskin = hov && c.buttonHover.isSet() ? c.buttonHover : c.dealButton.isSet() ? c.dealButton : c.button;
        blit(g, project, bskin, hov ? BUY_LIGHT : BUY, d[0], d[1], d[2], d[3]);
        String action = l == null ? "ВЫБЕРИТЕ ТОВАР" : switch (l.type) {
            case "sell" -> "ПРОДАТЬ";
            case "trade" -> "ОБМЕНЯТЬ";
            default -> "КУПИТЬ";
        };
        Component act = Component.literal(action).withStyle(net.minecraft.ChatFormatting.BOLD);
        g.pose().pushPose();
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, act, (int) ((d[0] + d[2] / 2f) / 0.75f - font.width(act) / 2f), (int) ((d[1] + 4) / 0.75f) + 1, WHITE, false);
        g.pose().popPose();
        g.pose().popPose();
        at("window");
    }
}
