package org.sprautestudio.spraute_engine.shop;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * What the shop window shows: the offers as the server sees them for this player right now (final
 * prices, what is left, what is locked and why) and the player's money.
 */
public class ShopView {
    private static final Gson GSON = new Gson();

    /** "project/name" of the shop. */
    public String key = "";
    public int scriptId = -1;
    public String title = "";
    public String currency = "coins";
    public String currencyName = "";
    public int balance;
    /** The trader standing in the window, or -1. */
    public int entityId = -1;
    public List<ShopDef.Tab> tabs = new ArrayList<>();
    public List<Line> lines = new ArrayList<>();
    /** The result of the last deal ("" — none) and whether it went through. */
    public String message = "";
    public boolean ok = true;

    public static class Line {
        public String id, tab, type, item, name, description;
        public int count, price;
        public List<ShopDef.Stack> costs = new ArrayList<>();
        /** Left in stock (-1 — unlimited) and left for this player (-1 — no limit). */
        public int stock = -1, left = -1;
        /** ok, locked, soldout, limit. */
        public String state = "ok";
        public String lockText = "";
        /** How many of the item the player has (for selling). */
        public int have;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ShopView fromJson(String json) {
        ShopView v = null;
        try {
            v = GSON.fromJson(json, ShopView.class);
        } catch (Exception ignored) {
        }
        return v != null ? v : new ShopView();
    }

    /** Sample contents for the settings preview. */
    public static ShopView sample() {
        ShopView v = preview(ShopDef.example("Лавка Алисы"));
        v.balance = 57;
        return v;
    }

    /** How a shop file looks before it is in the game: every offer, prices at the start. */
    public static ShopView preview(ShopDef d) {
        ShopView v = new ShopView();
        v.title = d.title;
        v.currency = d.currency;
        v.currencyName = d.currencyName;
        v.balance = 100;
        v.tabs = d.tabs;
        for (ShopDef.Offer o : d.offers) {
            Line l = new Line();
            l.id = o.id;
            l.tab = o.tab;
            l.type = o.type;
            l.item = o.item;
            l.name = o.name;
            l.description = o.description;
            l.count = o.count;
            l.price = ShopDef.finalPrice(o, 0);
            l.costs = o.costs;
            l.stock = o.stock;
            l.left = o.limit;
            l.have = o.count * 3;
            if (!o.unlock.isEmpty()) {
                l.state = "locked";
                l.lockText = o.lockText;
            }
            v.lines.add(l);
        }
        return v;
    }
}
