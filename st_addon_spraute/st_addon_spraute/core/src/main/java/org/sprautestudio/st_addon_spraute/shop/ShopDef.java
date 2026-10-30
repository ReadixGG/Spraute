package org.sprautestudio.st_addon_spraute.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A shop of a project: tabs and offers. The player buys for coins (or another item as money), sells
 * items for them, or trades items for items. Offers can run out and come back, have a limit, show only
 * when a story condition holds, stay locked until another one does, and grow in price.
 */
public class ShopDef {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String title = "Лавка";
    /** coins — the engine's coins; or an item id used as money (minecraft:emerald). */
    public String currency = "coins";
    /** How the money is called in the window ("" — монеты / the item's name). */
    public String currencyName = "";
    public List<Tab> tabs = new ArrayList<>();
    public List<Offer> offers = new ArrayList<>();

    public static class Tab {
        public String id = "main";
        public String name = "Товары";
        public String icon = "minecraft:chest";

        public Tab() {
        }

        public Tab(String id, String name, String icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
        }
    }

    /** An item the trade gives or takes. */
    public static class Stack {
        public String item = "minecraft:emerald";
        public int count = 1;

        public Stack() {
        }

        public Stack(String item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    public static class Offer {
        public String id = "offer";
        public String tab = "main";
        /** buy — the player buys; sell — the player sells; trade — items for items. */
        public String type = "buy";
        public String item = "minecraft:bread";
        public int count = 1;
        /** Name and description shown in the window ("" — the item's own name). */
        public String name = "";
        public String description = "";
        /** Coins (or currency items) for one purchase / sale. */
        public int price = 5;
        /** What a trade asks for (type trade), or extra items a purchase also takes. */
        public List<Stack> costs = new ArrayList<>();
        /** How many times it can be bought before it runs out; -1 — always in stock. */
        public int stock = -1;
        /** never, daily (back every new day) — how the stock comes back. */
        public String restock = "never";
        /** Most times one player can do it in all; -1 — no limit. */
        public int limit = -1;
        /** Shown only when this story expression is true ("" — always): flag("met_trader"). */
        public String condition = "";
        /** Seen but locked until this is true: level >= 3. */
        public String unlock = "";
        public String lockText = "";
        /** Each purchase makes the next one dearer by this share (0.1 — +10%). */
        public float priceGrowth;
        /** Sold at this share of the price (0.8 — 20% off); for sales — paid at this share. */
        public float discount = 1;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ShopDef fromJson(String json) {
        ShopDef d = null;
        try {
            d = GSON.fromJson(json, ShopDef.class);
        } catch (Exception ignored) {
        }
        if (d == null) d = new ShopDef();
        d.fix();
        return d;
    }

    public void fix() {
        if (title == null) title = "Лавка";
        if (currency == null || currency.isBlank()) currency = "coins";
        if (currencyName == null) currencyName = "";
        if (tabs == null) tabs = new ArrayList<>();
        if (offers == null) offers = new ArrayList<>();
        if (tabs.isEmpty()) tabs.add(new Tab());
        for (Offer o : offers) {
            if (o.id == null || o.id.isBlank()) o.id = "offer";
            if (o.tab == null || o.tab.isBlank()) o.tab = tabs.get(0).id;
            if (o.type == null) o.type = "buy";
            if (o.item == null) o.item = "minecraft:air";
            if (o.name == null) o.name = "";
            if (o.description == null) o.description = "";
            if (o.costs == null) o.costs = new ArrayList<>();
            if (o.restock == null) o.restock = "never";
            if (o.condition == null) o.condition = "";
            if (o.unlock == null) o.unlock = "";
            if (o.lockText == null) o.lockText = "";
            if (o.discount <= 0) o.discount = 1;
            o.count = Math.max(1, o.count);
            o.price = Math.max(0, o.price);
        }
    }

    public Offer offer(String id) {
        for (Offer o : offers) if (o.id.equals(id)) return o;
        return null;
    }

    public boolean coins() {
        return currency.equals("coins");
    }

    /** Price of the next deal after {@code bought} earlier ones. */
    public static int finalPrice(Offer o, int bought) {
        double p = o.price * o.discount * Math.pow(1 + Math.max(0, o.priceGrowth), Math.max(0, bought));
        return (int) Math.max(0, Math.round(p));
    }

    /** A sample shop for new files: food, a weapon, selling and a trade. */
    public static ShopDef example(String title) {
        ShopDef d = new ShopDef();
        d.title = title;
        d.tabs.add(new Tab("main", "Еда", "minecraft:bread"));
        d.tabs.add(new Tab("gear", "Снаряжение", "minecraft:iron_sword"));
        d.tabs.add(new Tab("buyout", "Скупка", "minecraft:gold_ingot"));
        Offer bread = offer("bread", "main", "buy", "minecraft:bread", 3, 4);
        bread.description = "Свежий хлеб из печи";
        Offer apple = offer("apple", "main", "buy", "minecraft:golden_apple", 1, 40);
        apple.stock = 2;
        apple.restock = "daily";
        apple.description = "Всего две штуки в день";
        Offer sword = offer("sword", "gear", "buy", "minecraft:iron_sword", 1, 25);
        sword.priceGrowth = 0.25f;
        sword.description = "Каждый следующий дороже на 25%";
        Offer armor = offer("chest", "gear", "buy", "minecraft:iron_chestplate", 1, 60);
        armor.unlock = "flag(\"trusted\")";
        armor.lockText = "Торговец вам ещё не доверяет";
        Offer wheat = offer("wheat", "buyout", "sell", "minecraft:wheat", 8, 3);
        wheat.description = "Принимаю пшеницу пачками по 8";
        Offer trade = offer("emeralds", "buyout", "trade", "minecraft:emerald", 1, 0);
        trade.costs.add(new Stack("minecraft:diamond", 1));
        trade.description = "Алмаз на изумруд";
        d.offers.addAll(List.of(bread, apple, sword, armor, wheat, trade));
        d.fix();
        return d;
    }

    private static Offer offer(String id, String tab, String type, String item, int count, int price) {
        Offer o = new Offer();
        o.id = id;
        o.tab = tab;
        o.type = type;
        o.item = item;
        o.count = count;
        o.price = price;
        return o;
    }
}
