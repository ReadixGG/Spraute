package org.sprautestudio.st_addon_spraute.editor.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.sprautestudio.st_addon_spraute.engine.EngineConfig;
import org.sprautestudio.st_addon_spraute.engine.client.ChoiceLayout;
import org.sprautestudio.st_addon_spraute.engine.client.HudPainter;
import org.sprautestudio.st_addon_spraute.gui.GuiDoc;

import java.util.List;

/**
 * The engine's windows as interfaces of the editor: each window's standard layout, built from the settings as they
 * are (places, colours, textures), so the studio starts from exactly what the game shows.
 */
public final class EngineWindows {
    private EngineWindows() {
    }

    /** The windows, in the studio's order. */
    public static final List<String> ROLES = EngineConfig.WINDOWS;

    /** What a window's page tells about its live parts. */
    public static String help(String role) {
        return switch (role) {
            case "hud" -> "Части игры — элементы «HUD ·»: здоровье, еда, броня, воздух, опыт, хотбар, монеты, цели квеста. "
                    + "Чего нет на холсте — того нет в игре. Тексты: {hp} {maxhp} {food} {armor} {air} {level} {exp} {coins} {x} {y} {z} {player} {time}";
            case "choice" -> "{name} — кто спрашивает, {question} — вопрос (печатается по буквам). «Варианты ответа» — кнопки: мышь, 1–9, стрелки. "
                    + "«Говорящий» — тот, кто спрашивает (кого показывать — на странице «Речь»)";
            case "toast" -> "Плашка сверху при событиях квеста. {title} — «Задача выполнена»…, {text} — задача или квест. "
                    + "«Значок квеста» сам меняется: новый, выполнен, провален. Когда и какие события показывать — страница «Журнал квестов»";
            case "zone" -> "Название зоны, когда игрок в неё входит. {zone} — название, {subtitle} — подпись. "
                    + "Сколько секунд держится и звук — в настройках зоны";
            case "quest" -> "Журнал квестов (J). Живые части — «Журнал ·»: вкладки, список квестов, заголовок, страница, итог. "
                    + "Тексты: {tab} — вкладка, {count} — сколько квестов, {quest}, {giver}, {state}, {close} — подсказка закрытия";
            case "shop" -> "Окно торговли. Живые части — «Магазин ·»: товары, ползунок, выбранный товар, цена и итог, стрелки, кнопка, "
                    + "описание, деньги. Тексты: {title} — название магазина, {balance} — деньги игрока";
            case "chatlog" -> "Окно истории сообщений (клавиша истории Spraute): фон и рамка. Сам текст Spraute рисует поверх, на своём месте";
            default -> "";
        };
    }

    public static String icon(String role) {
        return role;
    }

    // ================================================================== the standard layouts

    /** A window's standard layout from the settings, as text. */
    public static String build(String role, EngineConfig cfg) {
        GuiDoc d = switch (role) {
            case "choice" -> choice(cfg);
            case "toast" -> toast(cfg);
            case "zone" -> zone(cfg);
            case "chatlog" -> chatlog(cfg);
            case "quest" -> journal(cfg);
            case "shop" -> shop(cfg);
            default -> hud(cfg);
        };
        return d.toJson();
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    static GuiDoc.Element el(String type, String id, String anchor, int x, int y, int w, int h, String... kv) {
        GuiDoc.Element e = new GuiDoc.Element();
        e.type = type;
        e.id = id;
        e.set("anchor", anchor);
        e.set("x", String.valueOf(x));
        e.set("y", String.valueOf(y));
        e.set("w", String.valueOf(w));
        e.set("h", String.valueOf(h));
        for (int i = 0; i + 1 < kv.length; i += 2) e.set(kv[i], kv[i + 1]);
        return e;
    }

    private static GuiDoc doc(String title, String layout, int w, int h) {
        GuiDoc d = new GuiDoc();
        d.set("title", title);
        d.set("layout", layout);
        d.set("width", String.valueOf(w));
        d.set("height", String.valueOf(h));
        d.set("back", "#00000000");
        return d;
    }

    /** A texture of the settings, else Spraute's. */
    private static String img(EngineConfig.Skin s, String stPath) {
        return s != null && s.isSet() ? s.image : "st_addon_spraute:textures/" + stPath + ".png";
    }

    static HudPainter.State sample(EngineConfig cfg) {
        HudPainter.State s = new HudPainter.State();
        s.stHealth = cfg.health;
        s.hp = 14;
        s.armor = 8;
        s.food = 17;
        s.air = 200;
        s.exp = 0.45f;
        s.level = 7;
        s.coins = 1250;
        s.selected = 1;
        s.hotbarSize = cfg.slots ? cfg.hotbarSize : 9;
        ItemStack[] items = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BREAD, 12), new ItemStack(Items.TORCH, 48),
                new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.MAP), new ItemStack(Items.APPLE, 3),
                ItemStack.EMPTY, new ItemStack(Items.COMPASS), new ItemStack(Items.WATER_BUCKET)};
        System.arraycopy(items, 0, s.hotbar, 0, 9);
        return s;
    }

    /** The HUD: every part where and how the settings have it now. */
    private static GuiDoc hud(EngineConfig cfg) {
        int sw = 480, sh = 270;
        HudPainter.State s = sample(cfg);
        GuiDoc doc = doc("HUD игрока", "screen", sw, sh);
        for (String key : List.of("hotbar", "exp", "health", "armor", "food", "air", "coins")) {
            if (key.equals("coins") && !cfg.currency) continue;
            EngineConfig.Element p = cfg.element(key);
            if (!p.visible) continue;
            int[] r = HudPainter.rect(cfg, key, sw, sh, s);
            // the anchor: the screen third the part sits in, so it stays there on any screen
            float cx = r[0] + r[2] / 2f, cy = r[1] + r[3] / 2f;
            String h = cx < sw / 3f ? "left" : cx > sw * 2 / 3f ? "right" : "";
            String v = cy < sh / 3f ? "top" : cy > sh * 2 / 3f ? "bottom" : "";
            String anchor = v.isEmpty() && h.isEmpty() ? "center" : v.isEmpty() ? h : h.isEmpty() ? v : v + "_" + h;
            float fx = h.equals("left") ? 0 : h.equals("right") ? 1 : 0.5f, fy = v.equals("top") ? 0 : v.equals("bottom") ? 1 : 0.5f;
            doc.elements.add(el("hud_" + key, key, anchor, Math.round(r[0] - fx * sw + fx * r[2]), Math.round(r[1] - fy * sh + fy * r[3]), r[2], r[3],
                    "style", p.style, "count", String.valueOf(p.count), "pad", String.valueOf(p.pad), "direction", p.fillDir, "gap", String.valueOf(p.gap),
                    "reverse", String.valueOf(p.reverse), "showText", String.valueOf(p.text), "textColor", hex(p.textColor),
                    "back", p.back.image, "fill", p.fill.image, "frame", p.frame.image, "full", p.full.image, "half", p.half.image, "empty", p.empty.image));
        }
        if (cfg.quests.tracker) doc.elements.add(el("hud_quests", "quests", "top_right", -6, 46, 150, 60,
                "titleColor", hex(cfg.quests.accentColor), "doneColor", hex(cfg.quests.doneColor)));
        return doc;
    }

    /** The choice menu as the settings draw it, on Spraute's 480×270 window. */
    private static GuiDoc choice(EngineConfig cfg) {
        EngineConfig.Choice c = cfg.choice;
        GuiDoc doc = doc("Меню выбора", "fit", 480, 270);
        var r = ChoiceLayout.rects(c, Minecraft.getInstance().font, 480, 270, List.of("Слышал.", "Их было двенадцать.", "Расскажи подробнее…"));
        int[] p = r.get("panel"), f = r.get("portrait"), n = r.get("name"), t = r.get("text"), a = r.get("answers");
        doc.elements.add(el("image", "panel", "top_left", p[0], p[1], p[2], p[3], "image", img(c.panel, "gui/talk/fade")));
        if (!c.show.equals("none")) doc.elements.add(el("speaker", "speaker", "top_left", f[0], f[1], f[2], f[3],
                "framing", c.framing, "scale", String.valueOf(c.entityScale)));
        if (c.portrait.isSet()) doc.elements.add(el("image", "portrait", "top_left", f[0], f[1], f[2], f[3], "image", c.portrait.image));
        if (c.nameLine) doc.elements.add(el("image", "nameplate", "top_left", n[0], n[1] + 12, 103, 3, "image", img(c.nameplate, "gui/talk/divider")));
        doc.elements.add(el("text", "name", "top_left", n[0], n[1], 104, 12, "text", "{name}", "align", "center", "color", hex(c.nameColor),
                "wrap", "false"));
        doc.elements.add(el("text", "question", "top_left", t[0], t[1], t[2], t[3], "text", "{question}", "color", hex(c.textColor), "shadow", "false"));
        boolean own = c.button.isSet();
        doc.elements.add(el("choice_answers", "answers", "top_left", a[0], a[1], Math.max(a[2], 166), 160,
                "style", own ? "image" : "classic", "image", own ? c.button.image : "", "hoverImage", c.buttonHover.isSet() ? c.buttonHover.image : "",
                "textColor", hex(c.answerColor), "hoverTextColor", hex(c.answerHoverColor), "textScale", String.valueOf(c.answerTextScale),
                "padding", "12", "minHeight", "32", "gap", "5"));
        return doc;
    }

    /** The quest notice at the top: its strip, icon, upper line and text. */
    private static GuiDoc toast(EngineConfig cfg) {
        EngineConfig.Quests q = cfg.quests;
        EngineConfig.Quests.Toast t = q.toast;
        GuiDoc doc = doc("Уведомление квеста", "screen", 480, 270);
        int w = t.width;
        GuiDoc.Element g = el("group", "toast", "top", 0, t.y - 12, w, 32, "appear", "slide_down", "appearTime", "0.3");
        g.children.add(el("panel", "back", "top_left", 0, 0, w, 30, "color", hex(t.background), "borderWidth", "0", "radius", "0", "shadow", "0"));
        g.children.add(el("image", "line", "top_left", 0, 14, w, 16, "image", img(q.taskLine, "gui/journal/task_strip")));
        int tx = 6;
        if (t.icon) {
            g.children.add(el("quest_icon", "icon", "top_left", 3, 2, 21, 23, "image", q.icons.isSet() ? q.icons.image : ""));
            tx = 28;
        }
        g.children.add(el("text", "title", "top_left", tx, 4, w - tx - 4, 10, "text", "{title}", "bold", "true", "color", hex(q.accentColor),
                "wrap", "false"));
        g.children.add(el("text", "text", "top_left", tx, 18, w - tx - 4, 10, "text", "{text}", "color", hex(q.textColor), "wrap", "false"));
        doc.elements.add(g);
        return doc;
    }

    /** The zone's name and subtitle where the settings put them. */
    private static GuiDoc zone(EngineConfig cfg) {
        EngineConfig.ZoneTitle z = cfg.zoneTitle;
        GuiDoc doc = doc("Название зоны", "screen", 480, 270);
        int y = Math.round(z.y * 270) - 18;
        GuiDoc.Element g = el("group", "zone", "top", Math.round((z.x - 0.5f) * 480), y, 360, 44, "appear", "fade", "appearTime", "0.7");
        g.children.add(el("text", "title", "top", 0, 0, 360, Math.round(10 * z.scale), "text", "{zone}", "align", "center", "wrap", "false",
                "scale", String.valueOf(z.scale), "bold", String.valueOf(z.bold), "shadow", String.valueOf(z.shadow), "color", hex(z.titleColor)));
        g.children.add(el("text", "subtitle", "top", 0, Math.round(10 * z.scale) + 4, 360, Math.round(10 * z.subScale), "text", "{subtitle}",
                "align", "center", "wrap", "false", "scale", String.valueOf(z.subScale), "shadow", String.valueOf(z.shadow), "color", hex(z.subColor)));
        doc.elements.add(g);
        return doc;
    }

    private static String own(EngineConfig.Skin s) {
        return s != null && s.isSet() ? s.image : "";
    }

    /** The quest journal as Spraute draws it, on its 392×230 window. */
    private static GuiDoc journal(EngineConfig cfg) {
        EngineConfig.Quests q = cfg.quests;
        GuiDoc doc = doc("Журнал квестов", "fit", 392, 230);
        doc.set("back", hex(q.shadeColor));
        int[] ol = q.off("list"), oc = q.off("card");
        GuiDoc.Element list = el("group", "left", "top_left", ol[0], ol[1], 144, 230, "appear", "slide_down", "appearTime", "0.4");
        list.children.add(el("image", "list_back", "top_left", 0, 0, 144, 230, "image", img(q.list, "gui/journal/list")));
        list.children.add(el("journal_tabs", "tabs", "top_left", 7, 5, 75, 23, "gap", "3",
                "current", own(q.tabCurrent), "done", own(q.tabDone), "failed", own(q.tabFailed)));
        list.children.add(el("text", "tab_name", "top_left", 88, 13, 54, 8, "text", "{tab} · {count}", "scale", "0.75", "wrap", "false",
                "shadow", "false", "color", hex(q.mutedColor)));
        list.children.add(el("journal_list", "list", "top_left", 4, 31, 136, 195, "entryHeight", "35", "gap", "2", "padding", "2",
                "textColor", hex(q.textColor), "mutedColor", hex(q.mutedColor), "accentColor", hex(q.accentColor),
                "button", own(q.button), "buttonHover", own(q.buttonHover), "buttonOn", own(q.buttonOn), "iconSheet", own(q.icons)));
        doc.elements.add(list);
        GuiDoc.Element card = el("group", "right", "top_left", 150 + oc[0], 2 + oc[1], 240, 228, "appear", "slide_down", "appearTime", "0.4");
        card.children.add(el("image", "card_back", "top_left", 0, 0, 240, 225, "image", img(q.card, "gui/journal/card")));
        card.children.add(el("journal_title", "title", "top_left", 10, 7, 222, 30, "textColor", hex(q.textColor), "accentColor", hex(q.accentColor),
                "hoverColor", hex(q.headerColor), "mutedColor", hex(q.mutedColor)));
        card.children.add(el("image", "divider", "top_left", 10, 37, 129, 2, "image", img(q.divider, "gui/journal/divider")));
        card.children.add(el("journal_page", "page", "top_left", 6, 42, 232, 163, "textColor", hex(q.textColor), "headerColor", hex(q.headerColor),
                "accentColor", hex(q.accentColor), "doneColor", hex(q.doneColor), "failColor", hex(q.failColor), "taskLine", own(q.taskLine)));
        card.children.add(el("journal_state", "state", "top_left", 102, 211, 130, 10, "doneColor", hex(q.doneColor), "failColor", hex(q.failColor)));
        card.children.add(el("text", "close", "top_left", 5, 227, 200, 5, "text", "{close}", "scale", "0.5", "wrap", "false", "shadow", "false",
                "color", "#80FFFFFF"));
        doc.elements.add(card);
        return doc;
    }

    /** The shop as Spraute draws it, on its 445×262 window. */
    private static GuiDoc shop(EngineConfig cfg) {
        EngineConfig.Shop s = cfg.shop;
        GuiDoc doc = doc("Магазин", "fit", 445, 262);
        doc.set("back", "#A6191513");
        int[] ow = s.offsets.getOrDefault("window", new int[]{0, 0}), ol = s.offsets.getOrDefault("list", new int[]{0, 0}),
                oc = s.offsets.getOrDefault("card", new int[]{0, 0}), ot = s.offsets.getOrDefault("title", new int[]{0, 0}),
                om = s.offsets.getOrDefault("money", new int[]{0, 0});
        GuiDoc.Element list = el("group", "goods_side", "top_left", ow[0] + ol[0], ow[1] + ol[1] + 25, 254, 212, "appear", "slide_down", "appearTime", "0.5");
        list.children.add(el("image", "list_back", "top_left", 0, 0, 254, 212, "image", img(s.panel, "gui/store/list")));
        list.children.add(el("shop_goods", "goods", "top_left", 38, 41, 182, 140, "priceColor", "#FFE2D7C4", "sellColor", hex(s.sellColor),
                "mutedColor", hex(s.mutedColor), "cell", own(s.cell), "cellOn", own(s.cellOn), "locked", own(s.locked), "sold", own(s.sold)));
        list.children.add(el("shop_slider", "slider", "top_left", 239, 23, 9, 162, "image", own(s.slider)));
        doc.elements.add(list);
        doc.elements.add(el("text", "title", "top_left", ow[0] + ot[0] + 16, ow[1] + ot[1] + 11, 200, 10, "text", "{title}", "wrap", "false",
                "color", hex(s.titleColor), "shadow", "true", "appear", "slide_down", "appearTime", "0.5"));
        doc.elements.add(el("shop_money", "money", "top_left", ow[0] + om[0] + 148, ow[1] + om[1] + 6, 90, 13, "textColor", hex(s.priceColor),
                "appear", "slide_down", "appearTime", "0.5"));
        GuiDoc.Element card = el("group", "card", "top_left", ow[0] + oc[0] + 269, ow[1] + oc[1], 176, 262, "appear", "slide_down", "appearTime", "0.5");
        card.children.add(el("image", "card_back", "top_left", 0, 0, 176, 262, "image", img(s.detail, "gui/store/card")));
        card.children.add(el("shop_item", "item", "top_left", 72, 55, 40, 32));
        card.children.add(el("shop_info", "info", "top_left", 27, 118, 145, 76, "priceColor", hex(s.priceColor), "sellColor", hex(s.sellColor)));
        card.children.add(el("shop_arrows", "arrows", "top_left", 48, 102, 82, 15,
                "minus5", own(s.arrowMinus5), "minus1", own(s.arrowMinus1), "plus1", own(s.arrowPlus1), "plus5", own(s.arrowPlus5),
                "minus5Hover", own(s.arrowMinus5Hover), "minus1Hover", own(s.arrowMinus1Hover), "plus1Hover", own(s.arrowPlus1Hover),
                "plus5Hover", own(s.arrowPlus5Hover)));
        card.children.add(el("shop_deal", "deal", "top_left", 44, 195, 88, 14, "image", own(s.dealButton.isSet() ? s.dealButton : s.button),
                "hoverImage", own(s.buttonHover)));
        card.children.add(el("shop_desc", "description", "top_left", 35, 220, 106, 20, "textColor", hex(s.mutedColor)));
        doc.elements.add(card);
        return doc;
    }

    /** The chat history's background and frame (Spraute draws the text over them, in its place). */
    private static GuiDoc chatlog(EngineConfig cfg) {
        GuiDoc doc = doc("История чата", "fit", 480, 270);
        int w = Math.round(270 * 1.1043478f);
        doc.elements.add(el("image", "back", "center", 0, 0, w, 270, "image", img(cfg.chatLog.back, "gui/log/back")));
        doc.elements.add(el("image", "frame", "center", 0, 0, w, 270, "image", img(cfg.chatLog.frame, "gui/log/frame")));
        return doc;
    }
}
