package org.sprautestudio.st_addon_spraute.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.sprautestudio.st_addon_spraute.core.system.Coins;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.lang.Ast;
import org.sprautestudio.st_addon_spraute.lang.Parser;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.runtime.Eval;
import org.sprautestudio.st_addon_spraute.runtime.GameEvents;
import org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime;
import org.sprautestudio.st_addon_spraute.runtime.SprauteScriptInstance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shops of the world on the server: their definitions (from the deployed project), what is left
 * and who bought what (saved in {@code <world>/spraute/shops.json}), the window views and the deals —
 * always checked here, never trusted from the client.
 */
public final class Shops {
    private Shops() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** State of one offer: what is left and how many times each player (uuid) did it. */
    static final class OfferState {
        int stock = Integer.MIN_VALUE;
        int total;
        Map<String, Integer> bought = new HashMap<>();
    }

    static final class ShopState {
        long day = Long.MIN_VALUE;
        Map<String, OfferState> offers = new LinkedHashMap<>();
    }

    private static Map<String, ShopState> states;

    // ------------------------------------------------------------------ definitions and state

    public static String key(String project, String name) {
        return project + "/" + name;
    }

    public static ShopDef def(String project, String name) {
        String json = SprauteRuntime.get().gui(project, "shop:" + name);
        return json == null ? null : ShopDef.fromJson(json);
    }

    private static Path file() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : s.getWorldPath(LevelResource.ROOT).resolve("spraute").resolve("shops.json");
    }

    private static Map<String, ShopState> states() {
        if (states != null) return states;
        states = new LinkedHashMap<>();
        Path f = file();
        if (f != null && Files.exists(f)) {
            try {
                Map<String, ShopState> m = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<Map<String, ShopState>>() {}.getType());
                if (m != null) states.putAll(m);
            } catch (Exception e) {
                St_addon_spraute.LOGGER.warn("Spraute: cannot read shops", e);
            }
        }
        return states;
    }

    private static void save() {
        Path f = file();
        if (f == null || states == null) return;
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(states), StandardCharsets.UTF_8);
        } catch (IOException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot save shops", e);
        }
    }

    /** The world closed: the next one reads its own file. */
    public static void unload() {
        states = null;
    }

    private static long today() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? 0 : Math.floorDiv(s.overworld().getDayTime(), 24000L);
    }

    /** The state of a shop, its stock filled in and refilled on a new day. */
    private static ShopState state(String key, ShopDef def) {
        ShopState st = states().computeIfAbsent(key, k -> new ShopState());
        long day = today();
        boolean newDay = st.day != day;
        for (ShopDef.Offer o : def.offers) {
            OfferState os = st.offers.computeIfAbsent(o.id, k -> new OfferState());
            if (os.stock == Integer.MIN_VALUE || (newDay && st.day != Long.MIN_VALUE && o.restock.equals("daily"))) os.stock = o.stock;
        }
        st.day = day;
        return st;
    }

    public static void restock(String project, String name) {
        ShopDef def = def(project, name);
        if (def == null) return;
        ShopState st = state(key(project, name), def);
        for (ShopDef.Offer o : def.offers) st.offers.get(o.id).stock = o.stock;
        save();
    }

    public static int bought(String project, String name, String offer) {
        ShopDef def = def(project, name);
        if (def == null) return 0;
        OfferState os = state(key(project, name), def).offers.get(offer);
        return os == null ? 0 : os.total;
    }

    public static int stock(String project, String name, String offer) {
        ShopDef def = def(project, name);
        if (def == null) return 0;
        OfferState os = state(key(project, name), def).offers.get(offer);
        return os == null ? 0 : os.stock;
    }

    /** Puts what is left of an offer (-1 — unlimited). */
    public static void setStock(String project, String name, String offer, int stock) {
        ShopDef def = def(project, name);
        if (def == null) return;
        OfferState os = state(key(project, name), def).offers.get(offer);
        if (os != null) {
            os.stock = stock;
            save();
        }
    }

    // ------------------------------------------------------------------ conditions

    private static final Map<String, Ast.Expr> PARSED = new HashMap<>();

    /** A story expression of an offer: true when empty; false (and a log line) when it does not work. */
    private static boolean check(SprauteScriptInstance s, String expr) {
        if (expr == null || expr.isBlank()) return true;
        if (s == null) return true;
        Ast.Expr e = PARSED.computeIfAbsent(expr, Parser::parseExpression);
        if (e == null) {
            SprauteRuntime.get().report(s.getId(), "Магазин: не понимаю условие «" + expr + "»");
            return false;
        }
        try {
            return Eval.truthy(s.eval().eval(e));
        } catch (RuntimeException ex) {
            SprauteRuntime.get().report(s.getId(), "Магазин, условие «" + expr + "»: " + ex.getMessage());
            return false;
        }
    }

    private static SprauteScriptInstance script(int id) {
        return SprauteRuntime.env().map(env -> env.scriptInstances.get(id) instanceof SprauteScriptInstance s ? s : null).orElse(null);
    }

    // ------------------------------------------------------------------ items and money

    static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id == null ? "" : (id.contains(":") ? id : "minecraft:" + id));
        Item it = rl == null ? null : BuiltInRegistries.ITEM.get(rl);
        return it == null ? Items.AIR : it;
    }

    private static int count(ServerPlayer p, Item it) {
        return p.getInventory().countItem(it);
    }

    private static void take(ServerPlayer p, Item it, int n) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(it)) {
                int k = Math.min(n, s.getCount());
                s.shrink(k);
                n -= k;
            }
        }
        inv.setChanged();
    }

    private static void give(ServerPlayer p, Item it, int n) {
        while (n > 0) {
            int k = Math.min(n, it.getMaxStackSize());
            ItemStack s = new ItemStack(it, k);
            if (!p.addItem(s)) p.drop(s, false);
            n -= k;
        }
    }

    private static int balance(ServerPlayer p, ShopDef def) {
        return def.coins() ? Coins.get() : count(p, item(def.currency));
    }

    private static void pay(ServerPlayer p, ShopDef def, int n) {
        if (def.coins()) Coins.set(Math.max(0, Coins.get() - n));
        else take(p, item(def.currency), n);
    }

    private static void earn(ServerPlayer p, ShopDef def, int n) {
        if (def.coins()) Coins.add(n);
        else give(p, item(def.currency), n);
    }

    // ------------------------------------------------------------------ the window

    public static ShopView view(ServerPlayer p, int scriptId, String project, String name) {
        ShopDef def = def(project, name);
        if (def == null) return null;
        SprauteScriptInstance s = script(scriptId);
        ShopState st = state(key(project, name), def);
        ShopView v = new ShopView();
        v.key = key(project, name);
        v.scriptId = scriptId;
        v.title = def.title;
        v.currency = def.currency;
        v.currencyName = def.currencyName;
        v.balance = balance(p, def);
        v.tabs = def.tabs;
        String who = p.getStringUUID();
        for (ShopDef.Offer o : def.offers) {
            if (!check(s, o.condition)) continue;
            OfferState os = st.offers.get(o.id);
            ShopView.Line l = new ShopView.Line();
            l.id = o.id;
            l.tab = o.tab;
            l.type = o.type;
            l.item = o.item;
            l.name = o.name;
            l.description = o.description;
            l.count = o.count;
            l.price = ShopDef.finalPrice(o, os.total);
            l.costs = o.costs;
            l.stock = os.stock;
            int mine = os.bought.getOrDefault(who, 0);
            l.left = o.limit < 0 ? -1 : Math.max(0, o.limit - mine);
            l.have = count(p, item(o.item));
            if (!check(s, o.unlock)) {
                l.state = "locked";
                l.lockText = o.lockText;
            } else if (os.stock == 0) l.state = "soldout";
            else if (l.left == 0) l.state = "limit";
            v.lines.add(l);
        }
        return v;
    }

    /** Opens a shop for a player; {@code trader} stands in the window. */
    public static boolean open(ServerPlayer p, int scriptId, String project, String name, Entity trader) {
        ShopView v = view(p, scriptId, project, name);
        if (v == null) return false;
        v.entityId = trader != null ? trader.getId() : -1;
        SprauteNetwork.sendTo(p, new Packets.ShopOpen(v.toJson(), false));
        return true;
    }

    /** A deal asked for by the client: checked again here, then done. */
    public static void deal(ServerPlayer p, Packets.ShopDeal msg) {
        int slash = msg.key().indexOf('/');
        if (slash < 0) return;
        String project = msg.key().substring(0, slash), name = msg.key().substring(slash + 1);
        ShopDef def = def(project, name);
        if (def == null) return;
        ShopDef.Offer o = def.offer(msg.offer());
        String result;
        boolean ok = false;
        int n = Math.max(1, Math.min(999, msg.count()));
        SprauteScriptInstance s = script(msg.scriptId());
        ShopState st = state(key(project, name), def);
        if (o == null || !check(s, o.condition)) result = "Этого больше нет";
        else if (!check(s, o.unlock)) result = o.lockText.isEmpty() ? "Пока недоступно" : o.lockText;
        else {
            OfferState os = st.offers.get(o.id);
            String who = p.getStringUUID();
            int mine = os.bought.getOrDefault(who, 0);
            if (os.stock >= 0) n = Math.min(n, os.stock);
            if (o.limit >= 0) n = Math.min(n, o.limit - mine);
            if (n <= 0) result = os.stock == 0 ? "Закончилось" : "Больше нельзя";
            else result = exchange(p, def, o, os, n);
            if (result == null) {
                ok = true;
                if (os.stock >= 0) os.stock -= n;
                os.total += n;
                os.bought.merge(who, n, Integer::sum);
                save();
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("shop", name);
                data.put("offer", o.id);
                data.put("item", o.item);
                data.put("count", (double) (n * o.count));
                data.put("amount", (double) n);
                data.put("type", o.type);
                GameEvents.post("purchase", p, data);
                String what = (o.name.isEmpty() ? new ItemStack(item(o.item)).getHoverName().getString() : o.name) + " ×" + (n * o.count);
                result = switch (o.type) {
                    case "sell" -> "Продано: " + what;
                    case "trade" -> "Обменяно: " + what;
                    default -> "Куплено: " + what;
                };
            }
        }
        ShopView v = view(p, msg.scriptId(), project, name);
        if (v == null) return;
        v.entityId = msg.entityId();
        v.message = result;
        v.ok = ok;
        SprauteNetwork.sendTo(p, new Packets.ShopOpen(v.toJson(), true));
    }

    /** Does the deal n times; null when done, else why not. */
    private static String exchange(ServerPlayer p, ShopDef def, ShopDef.Offer o, OfferState os, int n) {
        Item it = item(o.item);
        if (it == Items.AIR) return "Неизвестный предмет " + o.item;
        int price = 0;
        for (int i = 0; i < n; i++) price += ShopDef.finalPrice(o, os.total + i);
        for (ShopDef.Stack c : o.costs) {
            if (count(p, item(c.item)) < c.count * n) return "Не хватает: " + new ItemStack(item(c.item)).getHoverName().getString() + " ×" + (c.count * n);
        }
        switch (o.type) {
            case "sell" -> {
                if (count(p, it) < o.count * n) return "У вас нет столько: нужно " + (o.count * n);
                take(p, it, o.count * n);
                for (ShopDef.Stack c : o.costs) take(p, item(c.item), c.count * n);
                earn(p, def, price);
            }
            case "trade" -> {
                if (price > 0 && balance(p, def) < price) return "Не хватает денег: нужно " + price;
                for (ShopDef.Stack c : o.costs) take(p, item(c.item), c.count * n);
                if (price > 0) pay(p, def, price);
                give(p, it, o.count * n);
            }
            default -> {
                if (balance(p, def) < price) return "Не хватает денег: нужно " + price;
                pay(p, def, price);
                for (ShopDef.Stack c : o.costs) take(p, item(c.item), c.count * n);
                give(p, it, o.count * n);
            }
        }
        return null;
    }
}
