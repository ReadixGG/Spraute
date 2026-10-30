package org.sprautestudio.st_addon_spraute.shop.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.sprautestudio.st_addon_spraute.engine.EngineConfig;
import org.sprautestudio.st_addon_spraute.engine.client.EngineClient;
import org.sprautestudio.st_addon_spraute.engine.client.LiveDraw;
import org.sprautestudio.st_addon_spraute.gui.GuiDoc;
import org.sprautestudio.st_addon_spraute.gui.client.GuiView;
import org.sprautestudio.st_addon_spraute.shop.ShopDef;
import org.sprautestudio.st_addon_spraute.shop.ShopView;
import org.sprautestudio.st_addon_spraute.ui.Canvas;

import java.util.ArrayList;
import java.util.List;

/**
 * The shop's live parts for the engine studio's layout: the goods (cards in rows), the scroll slider, the chosen
 * item, its prices and state, the amount arrows, the deal button, the description and the player's money. Each part
 * is drawn into its element's rectangle and remembers where it put what, for the shop screen's clicks.
 */
public final class ShopParts {
    private ShopParts() {
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "textures/gui/store/" + name + ".png");
    }

    private static final ResourceLocation CELL = tex("cell"), CELL_GLOW = tex("cell_hot"), LOCKED = tex("cell_locked"),
            SOLD = tex("cell_sold"), BUY = tex("buy"), BUY_LIGHT = tex("buy_hot"), SLIDER = tex("slider");
    private static final int[] STEPS = {-5, -1, 1, 5};

    /** The open shop (its data and choices are the panel's) and where the parts were drawn. */
    public static final class State {
        public final ShopPanel panel;
        public double maxScroll;
        final List<int[]> cellRects = new ArrayList<>();
        final List<String> cellIds = new ArrayList<>();
        final int[][] arrowRects = new int[4][];
        int[] goodsRect, dealRect, sliderRect;
        /** The item under the mouse after drawing (for the tooltip). */
        public ItemStack hovered;

        public State(ShopPanel panel) {
            this.panel = panel;
        }
    }

    private static State sample;

    public static State sample() {
        if (sample == null) sample = new State(new ShopPanel(ShopView.sample()));
        return sample;
    }

    static State state(GuiView v) {
        return v.liveState instanceof State s ? s : sample();
    }

    /** {title}, {balance}, {currency} for the window's texts. */
    public static void vars(GuiView v) {
        State s = state(v);
        v.vars.put("title", s.panel.view.title);
        v.vars.put("balance", String.valueOf(s.panel.view.balance));
        v.vars.put("currency", s.panel.view.currencyName);
    }

    private static EngineConfig.Shop st() {
        return EngineClient.config().shop;
    }

    private static ItemStack coin(ShopView view) {
        if (!view.currency.equals("coins")) return ShopPanel.stack(view.currency, 1);
        ItemStack s = ShopPanel.stack("minecraft:gold_nugget", 1);
        return s.isEmpty() ? new ItemStack(Items.GOLD_NUGGET) : s;
    }

    /** Draws a "shop_*" element. */
    public static void draw(GuiView v, Canvas c, GuiDoc.Element e, int[] r, float op) {
        State s = state(v);
        c.flush();
        float mx = v.mouseDX(), my = v.mouseDY();
        if (e.type.equals("shop_goods")) s.hovered = null;
        switch (e.type) {
            case "shop_goods" -> goods(v, c.g, c.font, s, e, r, op, mx, my);
            case "shop_slider" -> slider(v, c.g, s, e, r, op);
            case "shop_item" -> item(c.g, c.font, s, e, r, op, mx, my);
            case "shop_info" -> info(c.g, c.font, s, e, r, op);
            case "shop_arrows" -> arrows(v, c.g, s, e, r, op, mx, my);
            case "shop_deal" -> deal(v, c.g, c.font, s, e, r, op, mx, my);
            case "shop_desc" -> desc(c.g, c.font, s, e, r, op);
            case "shop_money" -> money(c.g, c.font, s, e, r, op);
            default -> {
            }
        }
        c.flush();
    }

    private static void goods(GuiView v, GuiGraphics g, Font font, State s, GuiDoc.Element e, int[] r, float op, float mx, float my) {
        s.goodsRect = r.clone();
        s.cellRects.clear();
        s.cellIds.clear();
        var st = st();
        ShopPanel p = s.panel;
        List<ShopView.Line> ls = p.lines();
        int cols = Math.max(1, e.i("columns")), cw = Math.max(8, e.i("cellWidth")), ch = Math.max(8, e.i("cellHeight"));
        int sx = cw + e.i("gapX"), sy = ch + e.i("gapY"), px = e.i("padX"), py = e.i("padY");
        int rows = (ls.size() + cols - 1) / cols;
        s.maxScroll = Math.max(0, rows * sy + py - r[3]);
        p.scroll = Math.max(0, Math.min(s.maxScroll, p.scroll));
        ItemStack coin = coin(p.view);
        LiveDraw.clip(g, r[0], r[1], r[2], r[3]);
        for (int i = 0; i < ls.size(); i++) {
            ShopView.Line l = ls.get(i);
            int[] cr = {r[0] + px + (i % cols) * sx, (int) Math.round(r[1] + py + (i / cols) * sy - p.scroll), cw, ch};
            s.cellRects.add(cr);
            s.cellIds.add(l.id);
            if (cr[1] + ch < r[1] || cr[1] > r[1] + r[3]) continue;
            boolean sel = l.id.equals(p.selected);
            boolean ok = l.state.equals("ok") || l.state.equals("limit");
            ResourceLocation def = l.state.equals("locked") ? LOCKED : l.state.equals("soldout") ? SOLD : sel ? CELL_GLOW : CELL;
            String prop = ok ? (sel ? "cellOn" : "cell") : l.state.equals("locked") ? "locked" : "sold";
            EngineConfig.Skin skin = ok ? (sel ? st.cellOn : st.cell) : l.state.equals("locked") ? st.locked : st.sold;
            LiveDraw.image(g, LiveDraw.tex(v.project, e, prop, skin, def), cr[0], cr[1], cw, ch, op);
            ItemStack it = ShopPanel.stack(l.item, l.count);
            float isz = cw * 16 / 30f;
            LiveDraw.item(g, it, cr[0] + (cw - isz) / 2f, cr[1] + ch * 8 / 43f, isz / 16f);
            if (l.count > 1) {
                g.pose().pushPose();
                g.pose().translate(cr[0] + (cw - isz) / 2f, cr[1] + ch * 8 / 43f, 0);
                g.pose().scale(isz / 16f, isz / 16f, 1);
                g.renderItemDecorations(font, it, 0, 0);
                g.pose().popPose();
            }
            boolean trade = l.type.equals("trade") && l.price == 0;
            String pr = trade ? "ОБМЕН" : String.valueOf(l.price);
            int col = l.type.equals("sell") ? e.color("sellColor") : e.color("priceColor");
            float ty = cr[1] + ch - 7;
            if (trade) LiveDraw.text(g, font, pr, cr[0] + cw / 2f - font.width(pr) * 0.25f, ty, 0.5f, LiveDraw.a(col, op), true);
            else {
                float pw = font.width(pr) * 0.5f + 6;
                LiveDraw.item(g, coin, cr[0] + cw / 2f - pw / 2 - 1, ty - 1.5f, 0.375f);
                LiveDraw.text(g, font, pr, cr[0] + cw / 2f - pw / 2 + 6, ty, 0.5f, LiveDraw.a(col, op), true);
            }
            if (LiveDraw.in(cr, mx, my) && LiveDraw.in(r, mx, my)) s.hovered = it;
        }
        g.disableScissor();
        if (ls.isEmpty()) LiveDraw.text(g, font, "Здесь пока пусто", r[0] + px, r[1] + py + 4, 0.75f, LiveDraw.a(e.color("mutedColor"), op), false);
    }

    private static void slider(GuiView v, GuiGraphics g, State s, GuiDoc.Element e, int[] r, float op) {
        s.sliderRect = r.clone();
        if (s.maxScroll <= 0 && !e.b("always")) return;
        int th = Math.max(2, e.i("thumbHeight"));
        double share = s.maxScroll <= 0 ? 0 : s.panel.scroll / s.maxScroll;
        LiveDraw.image(g, LiveDraw.tex(v.project, e, "image", st().slider, SLIDER), r[0], (int) Math.round(r[1] + share * (r[3] - th)), r[2], th, op);
    }

    private static void item(GuiGraphics g, Font font, State s, GuiDoc.Element e, int[] r, float op, float mx, float my) {
        ShopPanel p = s.panel;
        ShopView.Line l = p.line(p.selected);
        if (l == null) return;
        int size = Math.min(r[2], r[3]);
        LiveDraw.item(g, ShopPanel.stack(l.item, 1), r[0], r[1], size / 16f);
        if (LiveDraw.in(new int[]{r[0], r[1], size, size}, mx, my)) s.hovered = ShopPanel.stack(l.item, l.count);
        if (e.b("amount")) LiveDraw.text(g, font, "x" + p.qty, r[0] + size * 0.6f, r[1] + size * 0.62f, size / 21.3f, LiveDraw.a(e.color("textColor"), op), true);
    }

    private static void info(GuiGraphics g, Font font, State s, GuiDoc.Element e, int[] r, float op) {
        ShopPanel p = s.panel;
        ShopView.Line l = p.line(p.selected);
        if (l == null) return;
        int white = LiveDraw.a(e.color("textColor"), op);
        ItemStack coin = coin(p.view);
        int n = Math.max(1, p.qty);
        boolean sell = l.type.equals("sell");
        int priceCol = LiveDraw.a(sell ? e.color("sellColor") : e.color("priceColor"), op);
        int x = r[0], y = r[1];
        String name = ShopPanel.title(l);
        float ts = Math.max(0.5f, Math.min(1, (r[2] / 0.75f) / Math.max(1, font.width(name)))) * 0.75f;
        LiveDraw.text(g, font, name, x, y, ts, white, false);
        LiveDraw.text(g, font, sell ? "Торговец заплатит: " : "Стоимость товара: ", x, y + 9, 0.75f, white, false);
        price(g, font, coin, l, x, y + 19, 1, priceCol, white);
        LiveDraw.text(g, font, "Количество: " + n + " по " + l.count + " (" + n * l.count + " шт.)", x, y + 34, 0.75f, white, false);
        LiveDraw.text(g, font, sell ? "Итого получите: " : "Итоговая стоимость: ", x, y + 43, 0.75f, white, false);
        price(g, font, coin, l, x, y + 53, n, priceCol, white);
        String status;
        boolean good;
        if (!p.view.message.isEmpty() && System.currentTimeMillis() - p.messageAt < 3000) {
            status = p.view.message;
            good = p.view.ok;
        } else if (l.state.equals("locked")) {
            status = l.lockText.isEmpty() ? "Товар заблокирован!" : l.lockText;
            good = false;
        } else if (l.state.equals("soldout")) {
            status = sell ? "Товар не принимается!" : "Товар закончился!";
            good = false;
        } else if (l.state.equals("limit")) {
            status = "Больше нельзя!";
            good = false;
        } else {
            good = p.affordable(l, n);
            status = sell ? (good ? "Готово к продаже!" : "Недостаточно товара!")
                    : good ? (l.type.equals("trade") ? "Готово к обмену!" : "Готово к покупке!") : "Недостаточно средств!";
        }
        LiveDraw.text(g, font, font.plainSubstrByWidth(status, (int) (r[2] / 0.75f)), x, y + 67, 0.75f,
                LiveDraw.a(good ? e.color("okColor") : e.color("badColor"), op), false);
    }

    private static void price(GuiGraphics g, Font font, ItemStack coin, ShopView.Line l, float x, float y, int n, int priceCol, int white) {
        float end = x;
        if (l.price > 0 || l.costs.isEmpty()) {
            LiveDraw.item(g, coin, x, y - 3, 0.6f);
            String t = "x" + l.price * n;
            LiveDraw.text(g, font, t, x + 11, y, 0.75f, priceCol, false);
            end = x + 11 + font.width(t) * 0.75f;
        }
        for (ShopDef.Stack cs : l.costs) {
            LiveDraw.item(g, ShopPanel.stack(cs.item, 1), end + 3, y - 3, 0.6f);
            String t = "x" + cs.count * n;
            LiveDraw.text(g, font, t, end + 14, y, 0.75f, white, true);
            end += 14 + font.width(t) * 0.75f;
        }
    }

    private static void arrows(GuiView v, GuiGraphics g, State s, GuiDoc.Element e, int[] r, float op, float mx, float my) {
        var st = st();
        // Spraute's arrows over 82×15: big ones at the ends, small ones inside
        int[][] at = {{0, 0, 12, 15}, {25, 1, 9, 13}, {48, 1, 9, 13}, {70, 0, 12, 15}};
        float kx = r[2] / 82f, ky = r[3] / 15f;
        for (int i = 0; i < 4; i++) {
            int[] a = at[i];
            int[] ar = {r[0] + Math.round(a[0] * kx), r[1] + Math.round(a[1] * ky), Math.round(a[2] * kx), Math.round(a[3] * ky)};
            s.arrowRects[i] = ar;
            int step = STEPS[i];
            boolean hov = LiveDraw.in(ar, mx, my);
            String name = (step > 0 ? "plus" : "minus") + Math.abs(step) + (hov ? "Hover" : "");
            LiveDraw.image(g, LiveDraw.tex(v.project, e, name, st.arrow(step, hov), tex("arrow_" + step + (hov ? "_glow" : ""))), ar[0], ar[1], ar[2], ar[3], op);
        }
    }

    private static void deal(GuiView v, GuiGraphics g, Font font, State s, GuiDoc.Element e, int[] r, float op, float mx, float my) {
        s.dealRect = r.clone();
        var st = st();
        ShopView.Line l = s.panel.line(s.panel.selected);
        boolean hov = l != null && LiveDraw.in(r, mx, my);
        EngineConfig.Skin skin = hov && st.buttonHover.isSet() ? st.buttonHover : st.dealButton.isSet() ? st.dealButton : st.button;
        String own = hov && !e.get("hoverImage").isEmpty() ? e.get("hoverImage") : e.get("image");
        LiveDraw.image(g, LiveDraw.tex(v.project, own, skin, hov ? BUY_LIGHT : BUY), r[0], r[1], r[2], r[3], op);
        String action = l == null ? "ВЫБЕРИТЕ ТОВАР" : switch (l.type) {
            case "sell" -> "ПРОДАТЬ";
            case "trade" -> "ОБМЕНЯТЬ";
            default -> "КУПИТЬ";
        };
        Component act = Component.literal(action).withStyle(ChatFormatting.BOLD);
        float sc = Math.max(0.3f, e.f("textScale"));
        LiveDraw.text(g, font, act, r[0] + r[2] / 2f - font.width(act) * sc / 2f, r[1] + (r[3] - 8 * sc) / 2f + 0.5f, sc,
                LiveDraw.a(e.color("textColor"), op), false);
    }

    private static void desc(GuiGraphics g, Font font, State s, GuiDoc.Element e, int[] r, float op) {
        ShopView.Line l = s.panel.line(s.panel.selected);
        if (l == null || l.description.isEmpty()) return;
        float sc = Math.max(0.3f, e.f("textScale"));
        List<FormattedCharSequence> lines = font.split(Component.literal(l.description), (int) (r[2] / sc));
        float dy = r[1];
        int max = Math.max(1, (int) (r[3] / (10.8f * sc)));
        for (int i = 0; i < Math.min(max, lines.size()); i++) {
            LiveDraw.text(g, font, lines.get(i), r[0], dy, sc, LiveDraw.a(e.color("textColor"), op), false);
            dy += 10.8f * sc;
        }
    }

    private static void money(GuiGraphics g, Font font, State s, GuiDoc.Element e, int[] r, float op) {
        String bal = String.valueOf(s.panel.view.balance);
        float bw = font.width(bal);
        float x = r[0] + r[2] - bw;
        LiveDraw.item(g, coin(s.panel.view), x - 13, r[1] + (r[3] - 12) / 2f, 0.75f);
        LiveDraw.text(g, font, bal, x, r[1] + (r[3] - 8) / 2f, 1f, LiveDraw.a(e.color("textColor"), op), true);
    }

    // ================================================================== input (design units)

    /** What is under the point: "cell:ID", "arrow:N", "deal" or null. */
    public static String hit(State s, float mx, float my) {
        if (LiveDraw.in(s.goodsRect, mx, my))
            for (int i = 0; i < s.cellRects.size(); i++) if (LiveDraw.in(s.cellRects.get(i), mx, my)) return "cell:" + s.cellIds.get(i);
        for (int i = 0; i < 4; i++) if (LiveDraw.in(s.arrowRects[i], mx, my)) return "arrow:" + STEPS[i];
        if (LiveDraw.in(s.dealRect, mx, my)) return "deal";
        return null;
    }

    /** A click; true when the deal button was pressed and the deal can go. */
    public static boolean click(State s, float mx, float my) {
        String h = hit(s, mx, my);
        if (h == null) return false;
        ShopPanel p = s.panel;
        ShopView.Line l = p.line(p.selected);
        if (h.startsWith("cell:")) {
            String id = h.substring(5);
            if (!id.equals(p.selected)) p.qty = 1;
            p.selected = id;
        } else if (h.startsWith("arrow:") && l != null) {
            p.qty = Math.max(1, Math.min(p.maxQty(l), p.qty + Integer.parseInt(h.substring(6))));
        } else if (h.equals("deal") && l != null) {
            return l.state.equals("ok") && p.affordable(l, p.qty);
        }
        return false;
    }

    public static void scroll(State s, double delta) {
        s.panel.scroll = Math.max(0, Math.min(s.maxScroll, s.panel.scroll - delta * 20));
    }
}
