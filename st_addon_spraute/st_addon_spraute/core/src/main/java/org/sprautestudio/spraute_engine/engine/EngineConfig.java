package org.sprautestudio.spraute_engine.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which Spraute systems are on and how the HUD looks. Kept per world on the server, synced
 * to every client; edited in the IDE ("Настройки движка") and saved in the project as engine.json.
 * Every system is off by default, so a fresh world plays like vanilla Minecraft.
 */
public final class EngineConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean health;
    public boolean currency;
    public boolean time;
    public boolean inventory;
    public boolean slots;
    /** Open hotbar slots (1–9) while slots are limited; the second hand: auto, off or on. */
    public int hotbarSize = 5;
    public String offhand = "auto";
    /** Hide what players write in chat (Spraute's story mode). */
    public boolean chat;

    /** Project whose pictures the skins name. */
    public String project = "";

    /** HUD parts by {@link #ELEMENTS} key. */
    public Map<String, Element> hud = new LinkedHashMap<>();
    public ChatLog chatLog = new ChatLog();
    public Choice choice = new Choice();
    public Clock clock = new Clock();
    public Shop shop = new Shop();
    public ZoneTitle zoneTitle = new ZoneTitle();
    public Quests quests = new Quests();
    /**
     * The player's HUD laid out in the interface editor: the name of one of the project's interfaces ("" — the
     * game's HUD with the settings above) and its text, carried here so every player gets it with the settings.
     */
    public String hudLayout = "";
    public String hudJson = "";

    /** Engine windows that can be laid out in the interface editor, in the order the settings show them. */
    public static final java.util.List<String> WINDOWS = java.util.List.of("hud", "choice", "toast", "zone", "chatlog", "quest", "shop");

    public static String windowTitle(String role) {
        return switch (role) {
            case "hud" -> "HUD игрока";
            case "choice" -> "Меню выбора";
            case "toast" -> "Уведомление квеста";
            case "zone" -> "Название зоны";
            case "chatlog" -> "История чата";
            case "quest" -> "Журнал квестов";
            case "shop" -> "Магазин";
            default -> role;
        };
    }

    /**
     * Engine windows laid out in the interface editor: role → the project's interface ("" or none — the engine's own
     * look with the settings below) and that interface's text, carried here so every player gets it.
     */
    public java.util.Map<String, String> layouts = new java.util.LinkedHashMap<>(), layoutJson = new java.util.LinkedHashMap<>();

    /** The interface a window is drawn with, or null — the engine's own look. */
    public String layout(String role) {
        String json = layoutJson.get(role);
        return json == null || json.isBlank() ? null : json;
    }

    /** The quest journal's look. */
    public static final class Quests {
        /** 1 — the journal fills the screen (keeping its shape), 0.8 — smaller. */
        public float scale = 1;
        /** Textures ("" — Spraute's): the list, the quest card, list entries (usual / hovered / chosen), a task's strip. */
        public Skin list = new Skin(), card = new Skin(), button = new Skin(), buttonHover = new Skin(), buttonOn = new Skin(), taskLine = new Skin();
        /** Sheets like Spraute's: a tab (23×69 — usual, hovered, chosen), the icons (63×46), the divider line (129×2). */
        public Skin tabCurrent = new Skin(), tabDone = new Skin(), tabFailed = new Skin(), icons = new Skin(), divider = new Skin();
        public int textColor = 0xFFFFFFFF, mutedColor = 0xFF9AA4B4, accentColor = 0xFFFFD25A, doneColor = 0xFF7BE08A, failColor = 0xFFFF6B6B,
                headerColor = 0xFF6EC3FF, shadeColor = 0xA6191513;
        /** Shift of the list and the card from their places. */
        public java.util.Map<String, int[]> offsets = new java.util.LinkedHashMap<>();
        /** The followed quest's goals at the right edge of the screen (off — the goals are only in the journal, J). */
        public boolean tracker = true;
        /** The notice at the top of the screen when a quest starts, a goal is done, a quest ends. */
        public Toast toast = new Toast();

        /** The quest notice: which events show it, its words, size, place, time and look. */
        public static final class Toast {
            public boolean enabled = true;
            public boolean onStart = true, onTask = true, onReveal = true, onComplete = true, onFail = true;
            /** Its upper line for each event ("" — the usual words). */
            public String startTitle = "Новый квест", taskTitle = "Задача выполнена", revealTitle = "Новая задача",
                    completeTitle = "Квест выполнен", failTitle = "Квест провален";
            /** Seconds it stays; its top (pixels from the screen's top), width and size. */
            public float seconds = 4, scale = 1;
            public int y = 24, width = 225;
            public int background = 0xB00B0F16;
            public boolean icon = true, sound = true;
        }

        void fix() {
            if (scale <= 0) scale = 1;
            if (list == null) list = new Skin();
            if (card == null) card = new Skin();
            if (button == null) button = new Skin();
            if (buttonHover == null) buttonHover = new Skin();
            if (buttonOn == null) buttonOn = new Skin();
            if (taskLine == null) taskLine = new Skin();
            if (tabCurrent == null) tabCurrent = new Skin();
            if (tabDone == null) tabDone = new Skin();
            if (tabFailed == null) tabFailed = new Skin();
            if (icons == null) icons = new Skin();
            if (divider == null) divider = new Skin();
            if (offsets == null) offsets = new java.util.LinkedHashMap<>();
            if (toast == null) toast = new Toast();
            if (toast.seconds <= 0) toast.seconds = 4;
            if (toast.scale <= 0) toast.scale = 1;
            if (toast.width < 80) toast.width = 225;
            if (toast.startTitle == null) toast.startTitle = "";
            if (toast.taskTitle == null) toast.taskTitle = "";
            if (toast.revealTitle == null) toast.revealTitle = "";
            if (toast.completeTitle == null) toast.completeTitle = "";
            if (toast.failTitle == null) toast.failTitle = "";
        }

        public int[] off(String part) {
            int[] o = offsets.get(part);
            return o != null && o.length == 2 ? o : new int[]{0, 0};
        }
    }

    /** The zone's name shown when the player comes in. */
    public static final class ZoneTitle {
        /** Centre of the title, as a share of the screen. */
        public float x = 0.5f, y = 0.27f;
        public float scale = 2.4f, subScale = 1.1f;
        public float seconds = 4.5f;
        public int titleColor = 0xFFFFF1D9, subColor = 0xFFD8DEE8, lineColor = 0xB0FFD9A0, plateColor = 0x99000000;
        public boolean bold = true, shadow = true, lines = true, spread = true, plate = false;
        public int padding = 8;
        public Skin plateSkin = new Skin();
        /** Played as it appears ("" — none). */
        public String sound = "";

        void fix() {
            if (plateSkin == null) plateSkin = new Skin();
            if (sound == null) sound = "";
            seconds = Math.max(0.5f, seconds);
            scale = Math.max(0.3f, scale);
            subScale = Math.max(0.3f, subScale);
        }
    }

    /** The shop window's look: textures ("" — built in) and colours. */
    public static final class Shop {
        public Skin panel = new Skin(), tab = new Skin(), tabOn = new Skin(), cell = new Skin(), cellOn = new Skin(), detail = new Skin(),
                button = new Skin(), buttonHover = new Skin(), dealButton = new Skin();
        /** A sold-out and a locked cell, the scroll slider, the amount arrows (−5 −1 +1 +5) and the same hovered. */
        public Skin locked = new Skin(), sold = new Skin(), slider = new Skin(),
                arrowMinus5 = new Skin(), arrowMinus1 = new Skin(), arrowPlus1 = new Skin(), arrowPlus5 = new Skin(),
                arrowMinus5Hover = new Skin(), arrowMinus1Hover = new Skin(), arrowPlus1Hover = new Skin(), arrowPlus5Hover = new Skin();

        /** The arrow of this step (−5, −1, 1, 5), hovered or not. */
        public Skin arrow(int step, boolean hover) {
            return switch (step) {
                case -5 -> hover ? arrowMinus5Hover : arrowMinus5;
                case -1 -> hover ? arrowMinus1Hover : arrowMinus1;
                case 1 -> hover ? arrowPlus1Hover : arrowPlus1;
                default -> hover ? arrowPlus5Hover : arrowPlus5;
            };
        }
        public int titleColor = 0xFFFFE7A3, textColor = 0xFFE6E8F0, mutedColor = 0xFF8C91A8, priceColor = 0xFFFFD25A, sellColor = 0xFF7BE08A,
                accentColor = 0xFF6E86FF;
        /** Shift of the parts (window, list, card, title, money) from Spraute's places. */
        public java.util.Map<String, int[]> offsets = new java.util.LinkedHashMap<>();

        void fix() {
            if (panel == null) panel = new Skin();
            if (tab == null) tab = new Skin();
            if (tabOn == null) tabOn = new Skin();
            if (cell == null) cell = new Skin();
            if (cellOn == null) cellOn = new Skin();
            if (detail == null) detail = new Skin();
            if (button == null) button = new Skin();
            if (buttonHover == null) buttonHover = new Skin();
            if (dealButton == null) dealButton = new Skin();
            if (locked == null) locked = new Skin();
            if (sold == null) sold = new Skin();
            if (slider == null) slider = new Skin();
            if (arrowMinus5 == null) arrowMinus5 = new Skin();
            if (arrowMinus1 == null) arrowMinus1 = new Skin();
            if (arrowPlus1 == null) arrowPlus1 = new Skin();
            if (arrowPlus5 == null) arrowPlus5 = new Skin();
            if (arrowMinus5Hover == null) arrowMinus5Hover = new Skin();
            if (arrowMinus1Hover == null) arrowMinus1Hover = new Skin();
            if (arrowPlus1Hover == null) arrowPlus1Hover = new Skin();
            if (arrowPlus5Hover == null) arrowPlus5Hover = new Skin();
            if (offsets == null) offsets = new java.util.LinkedHashMap<>();
        }
    }

    /** Time of day, run by the engine when Spraute's time system is off. */
    public static final class Clock {
        /** The engine sets the day cycle and time itself (off — Minecraft's /gamerule and /time decide). */
        public boolean manage = true;
        /** Day and night follow each other (the doDaylightCycle rule). */
        public boolean cycle = true;
        /** How fast the day goes: 1 — as usual (20 minutes), 2 — twice as fast, 0.5 — twice as slow. */
        public float speed = 1;
        /** Time of day the world stays at (0..23999), or -1. */
        public int fixed = -1;
    }

    /** HUD parts in drawing order. */
    public static final List<String> ELEMENTS = List.of("hotbar", "exp", "health", "armor", "food", "air", "coins");

    public static String title(String key) {
        return switch (key) {
            case "health" -> "Здоровье";
            case "armor" -> "Броня";
            case "food" -> "Голод";
            case "air" -> "Воздух";
            case "exp" -> "Опыт";
            case "hotbar" -> "Хотбар (9 слотов)";
            case "coins" -> "Монеты";
            default -> key;
        };
    }

    /** Styles a part can be drawn in; "default" is the game's own look. */
    public static List<String> styles(String key) {
        return switch (key) {
            case "hotbar" -> List.of("default", "classic", "slots");
            case "exp" -> List.of("default", "bar");
            case "coins" -> List.of("default", "icon");
            default -> List.of("default", "bar", "icons");
        };
    }

    public static String styleTitle(String style) {
        return switch (style) {
            case "bar" -> "Полоска (фон, заливка, рамка)";
            case "icons" -> "Ряд иконок (полная, половина, пустая)";
            case "slots" -> "Свои слоты (свои текстуры)";
            case "classic" -> "Хотбар Spraute";
            case "icon" -> "Картинка и число";
            default -> "Стандартный";
        };
    }

    /** Styles drawn by the game itself (only moved, never redrawn). */
    public static boolean gameDrawn(String style) {
        return style.equals("default") || style.equals("classic");
    }

    /** A texture drawn stretched or as a 9-slice (corners kept, edges and middle stretched). */
    public static final class Skin {
        public String image = "";
        public boolean nine;
        /** 9-slice borders in texture pixels. */
        public int left = 4, top = 4, right = 4, bottom = 4;
        /** Size of a texture pixel on screen when drawing the 9-slice borders. */
        public float scale = 1;

        public boolean isSet() {
            return image != null && !image.isBlank();
        }
    }

    /** One HUD part: shown or hidden, its place and look. */
    public static final class Element {
        public boolean visible = true;
        /** Off: the game's own place. On: {@link #anchor} plus {@link #x}, {@link #y}. */
        public boolean customPos;
        /** Screen point the offset is measured from: TL, T, TR, L, C, R, BL, B, BR. */
        public String anchor = "BL";
        /**
         * Offset of the part's matching point from the anchor: with anchor "B" the part's bottom
         * centre sits at the screen's bottom centre plus the offset. Pixels, or percent of the screen.
         */
        public int x, y;
        public boolean percent;
        public float px, py;
        /** Size of a custom-style part (bar size, icon row size, hotbar size). */
        public int width = 81, height = 9;
        public String style = "default";
        /** bar: back/fill/frame. slots: back = slot, frame = selected slot, fill = locked slot. icon: fill = icon, back = plate. */
        public Skin back = new Skin(), fill = new Skin(), frame = new Skin();
        /** icons: full, half and empty icon. */
        public Skin full = new Skin(), half = new Skin(), empty = new Skin();
        public int count = 10;
        /** Padding of a bar's fill; gap between icons or slots. */
        public int pad = 1, gap = 0;
        /** Which way a bar fills: right, left, up, down. */
        public String fillDir = "right";
        /** Icons fill from the right (like vanilla food). */
        public boolean reverse;
        public boolean text;
        public int textColor = 0xFFFFFFFF;

        void fix() {
            if (back == null) back = new Skin();
            if (fill == null) fill = new Skin();
            if (frame == null) frame = new Skin();
            if (full == null) full = new Skin();
            if (half == null) half = new Skin();
            if (empty == null) empty = new Skin();
            if (anchor == null) anchor = "BL";
            if (style == null) style = "default";
        }
    }

    /** Background and frame of the chat history screen. */
    public static final class ChatLog {
        public Skin back = new Skin(), frame = new Skin();
        /**
         * An own frame drawn over the messages (it has a see-through middle, like Spraute's); off — under them,
         * right over the background. Spraute's own frame is always on top.
         */
        public boolean frameOver = false;

        /** The frame goes on top of the messages. */
        public boolean frameOnTop() {
            return !frame.isSet() || frameOver;
        }
        /** One colour for every speaker's name (0xRRGGBB) unless a character has its own; -1 — Spraute's usual. */
        public int nameColor = -1;
        /** "▸ F8" by the speaker's name while the story waits for F8. */
        public boolean f8Hint = true;
        /** Where the speech plate stands: bottom, center or top of the screen; and its shift up (+) or down, in pixels. */
        public String speechPlace = "bottom";
        public int speechShift = 0;
        /** The player's head in speech: size (1 — fills the frame) and shift (percent of the frame). */
        public float playerHeadScale = 1;
        public int playerHeadX = 0, playerHeadY = 0;

        public boolean custom() {
            return back.isSet() || frame.isSet();
        }
    }

    /** The story's choice menu. */
    public static final class Choice {
        /** spraute — the engine's own menu; classic — Spraute's dialog window. */
        public String style = "spraute";
        /** Who is drawn beside the question: speaker, player, mob or none. */
        public String show = "speaker";
        /** Entity type shown with show = mob. */
        public String mob = "minecraft:villager";
        /** body — the whole figure; head — only the head, like in the chat. */
        public String framing = "body";
        /** The camera flies in front of the speaker while the menu is open (as in Spraute's dialogs). */
        public boolean camera = true;
        public Skin panel = new Skin(), portrait = new Skin(), button = new Skin(), buttonHover = new Skin();
        /** The strip under the asker's name (80x3); off - no strip at all. */
        public Skin nameplate = new Skin();
        public boolean nameLine = true;
        /** Spraute's dialog colour by default. */
        public int textColor = 0xFF42C7FF, nameColor = 0xFF42C7FF, answerColor = 0xFF42C7FF, answerHoverColor = 0xFF42C7FF;
        /** Shift of each part (answers, panel, name, text, portrait) from its place in Spraute's window. */
        public java.util.Map<String, int[]> offsets = new java.util.LinkedHashMap<>();
        /** The one in the frame: zoom, shift (pixels) and how much is cut off each side of the frame (pixels). */
        public float entityScale = 1;
        public int entityX = 0, entityY = 0, cropLeft = 4, cropRight = 4, cropTop = 4, cropBottom = 4;
        /** Size of the whole menu: 1 — as Spraute's window on any screen (the same share of it), 1.2 — bigger. */
        public float uiScale = 1;
        /** Answer buttons: "fixed" — Spraute's 166 wide, "fit" — as wide as their text (between min and max). */
        public String answerWidth = "fixed";
        public int answerMinW = 90, answerMaxW = 260;
        public float answerTextScale = 1;

        void fix() {
            if (style == null) style = "spraute";
            if (entityScale <= 0) entityScale = 1;
            if (uiScale <= 0) uiScale = 1;
            if (answerWidth == null) answerWidth = "fixed";
            if (answerTextScale <= 0) answerTextScale = 1;
            answerMinW = Math.max(40, answerMinW);
            answerMaxW = Math.max(answerMinW, answerMaxW);
            if (show == null) show = "speaker";
            if (mob == null) mob = "minecraft:villager";
            if (framing == null) framing = "body";
            if (panel == null) panel = new Skin();
            if (portrait == null) portrait = new Skin();
            if (nameplate == null) nameplate = new Skin();
            if (button == null) button = new Skin();
            if (buttonHover == null) buttonHover = new Skin();
            if (offsets == null) offsets = new java.util.LinkedHashMap<>();
            // the first version's colours (white, yellow) become Spraute's blue
            if (textColor == 0xFFFFFFFF && nameColor == 0xFFFFD27A && answerColor == 0xFFFFFFFF && answerHoverColor == 0xFFFFE08A) {
                textColor = nameColor = answerColor = answerHoverColor = 0xFF42C7FF;
            }
        }
    }

    public Element element(String key) {
        Element e = hud.get(key);
        if (e == null) {
            e = defaults(key);
            hud.put(key, e);
        }
        return e;
    }

    /** Fresh settings of a part: sizes that suit its custom styles. */
    public static Element defaults(String key) {
        Element e = new Element();
        switch (key) {
            case "health" -> {
                e.width = 81;
                e.height = 9;
                e.text = false;
            }
            case "food", "air" -> e.reverse = true;
            case "exp" -> {
                e.width = 182;
                e.height = 5;
                e.text = true;
                e.textColor = 0xFF80FF20;
            }
            case "hotbar" -> {
                e.width = 182;
                e.height = 22;
                e.count = 9;
                e.anchor = "B";
            }
            case "coins" -> {
                e.width = 16;
                e.height = 16;
                e.anchor = "TR";
            }
            default -> {
            }
        }
        return e;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static EngineConfig fromJson(String json) {
        EngineConfig c = null;
        try {
            c = GSON.fromJson(json, EngineConfig.class);
        } catch (Exception ignored) {
        }
        if (c == null) c = new EngineConfig();
        c.fix();
        return c;
    }

    public EngineConfig copy() {
        return fromJson(toJson());
    }

    /** Fills parts missing in older or hand-written files. */
    private void fix() {
        if (project == null) project = "";
        if (hud == null) hud = new LinkedHashMap<>();
        for (String k : ELEMENTS) {
            Element e = hud.get(k);
            if (e == null) hud.put(k, defaults(k));
            else e.fix();
        }
        if (chatLog == null) chatLog = new ChatLog();
        if (chatLog.back == null) chatLog.back = new Skin();
        if (chatLog.frame == null) chatLog.frame = new Skin();
        if (choice == null) choice = new Choice();
        if (clock == null) clock = new Clock();
        if (shop == null) shop = new Shop();
        if (zoneTitle == null) zoneTitle = new ZoneTitle();
        if (quests == null) quests = new Quests();
        if (hudLayout == null) hudLayout = "";
        if (hudJson == null) hudJson = "";
        if (layouts == null) layouts = new java.util.LinkedHashMap<>();
        if (layoutJson == null) layoutJson = new java.util.LinkedHashMap<>();
        // the first HUD layouts were kept in their own fields
        if (!hudLayout.isEmpty() && !layouts.containsKey("hud")) {
            layouts.put("hud", hudLayout);
            layoutJson.put("hud", hudJson);
        }
        hudLayout = "";
        hudJson = "";
        quests.fix();
        zoneTitle.fix();
        hotbarSize = Math.max(1, Math.min(9, hotbarSize));
        if (offhand == null) offhand = "auto";
        shop.fix();
        clock.speed = Math.max(0.05f, Math.min(50, clock.speed));
        choice.fix();
    }

}
