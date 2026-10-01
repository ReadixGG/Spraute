package org.zonarstudio.spraute_engine.script;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Canonical vanilla HUD part ids (Forge {@code VanillaGuiOverlay} path) plus aliases.
 * Special tokens: {@code hand}, {@code vanilla}.
 */
public final class HudHideParts {
    public static final String HAND = "hand";
    public static final String VANILLA = "vanilla";

    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        alias("player_health", "health", "hearts", "hp", "здоровье", "сердца", "сердечки", "сердечко");
        alias("food_level", "hunger", "food", "foodlevel", "голод", "еда");
        alias("armor_level", "armor", "броня");
        alias("air_level", "air", "oxygen", "воздух", "кислород");
        alias("hotbar", "hot_bar", "хотбар", "слоты", "инвентарь_хотбар");
        alias("experience_bar", "xp", "exp", "experience", "опыт", "полоска_опыта", "уровень", "level");
        alias("crosshair", "прицел", "cross");
        alias("mount_health", "mount", "horse", "лошадь", "ездовое");
        alias("jump_bar", "jump", "прыжок");
        alias("potion_icons", "effects", "potions", "зелья", "эффекты");
        alias("boss_event_progress", "boss", "босс", "боссбар");
        alias("chat_panel", "chat", "чат");
        alias("item_name", "itemname", "название", "предмет");
        alias("player_list", "tab", "playerlist", "таб", "список");
        alias("title_text", "title", "титр", "заголовок");
        alias("subtitles", "субтитры");
        alias("vignette", "виньетка");
        alias("helmet", "шлем");
        alias("portal", "портал");
        alias(HAND, "рука", "handitem");
        alias(VANILLA, "all", "всё", "все", "minecraft", "hud", "ваниль", "классический");
    }

    private HudHideParts() {}

    private static void alias(String canonical, String... names) {
        ALIASES.put(canonical, canonical);
        for (String name : names) {
            ALIASES.put(normalize(name), canonical);
        }
    }

    public static String canonicalize(String raw) {
        if (raw == null) return null;
        String key = normalize(raw);
        if (key.isEmpty()) return null;
        String aliased = ALIASES.get(key);
        if (aliased != null) return aliased;
        int colon = key.lastIndexOf(':');
        if (colon >= 0 && colon < key.length() - 1) {
            return key.substring(colon + 1);
        }
        return key;
    }

    public static List<String> expand(Object arg) {
        List<String> out = new ArrayList<>();
        if (arg == null) return out;
        if (arg instanceof Collection<?> col) {
            for (Object item : col) addOne(out, item);
            return out;
        }
        addOne(out, arg);
        return out;
    }

    private static void addOne(List<String> out, Object item) {
        if (item == null) return;
        String raw = String.valueOf(item);
        for (String piece : raw.split("[,;]")) {
            String id = canonicalize(piece);
            if (id != null && !out.contains(id)) out.add(id);
        }
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
