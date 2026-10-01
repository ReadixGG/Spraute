package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptLangCatalog;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Script language / translation helpers.
 *
 * <pre>
 * playerLanguage([player])     → "en_us"
 * playerLang([player])         → "en"
 * isEnglish([player])          → true/false
 * t(ru, en [, player])         → pick by player language
 * translate(player, {en, ru})  → pick from map
 * tr(key [, player] [, args])  → spraute_engine/lang (placeholders {0} / named)
 * hasTr(key [, player])
 * languages() / format(text, ...) / reloadLang()
 * </pre>
 */
public final class LanguageFunctions {
    private LanguageFunctions() {}

    public static final class PlayerLanguage implements ScriptFunction {
        @Override public String getName() { return "playerLanguage"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Player p = resolveOptionalPlayer(args, 0, source);
            return ScriptLangCatalog.languageOf(p);
        }
    }

    public static final class PlayerLang implements ScriptFunction {
        @Override public String getName() { return "playerLang"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Player p = resolveOptionalPlayer(args, 0, source);
            return ScriptLangCatalog.shortLang(ScriptLangCatalog.languageOf(p));
        }
    }

    public static final class IsEnglish implements ScriptFunction {
        @Override public String getName() { return "isEnglish"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Player p = resolveOptionalPlayer(args, 0, source);
            return ScriptLangCatalog.isEnglish(p);
        }
    }

    /**
     * {@code t(ru, en)} or {@code t(ru, en, player)} —
     * English client gets {@code en}, otherwise {@code ru}.
     */
    public static final class T implements ScriptFunction {
        @Override public String getName() { return "t"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return args.isEmpty() ? "" : String.valueOf(args.get(0));
            String ru = String.valueOf(args.get(0));
            String en = String.valueOf(args.get(1));
            Player p = args.size() >= 3
                    ? resolvePlayer(args.get(2), source)
                    : resolveSourcePlayer(source);
            return ScriptLangCatalog.isEnglish(p) ? en : ru;
        }
    }

    /**
     * {@code translate(player, map)} or {@code translate(map)} —
     * map keys: full code ({@code en_us}), short ({@code en}), or {@code default}.
     */
    public static final class Translate implements ScriptFunction {
        @Override public String getName() { return "translate"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return "";
            Player player;
            Map<?, ?> map;
            if (args.size() >= 2 && args.get(1) instanceof Map<?, ?> m) {
                player = resolvePlayer(args.get(0), source);
                map = m;
            } else if (args.get(0) instanceof Map<?, ?> m) {
                player = resolveSourcePlayer(source);
                map = m;
            } else if (args.size() >= 3) {
                // translate(player, ru, en) alias of t(ru, en, player)
                player = resolvePlayer(args.get(0), source);
                String a = String.valueOf(args.get(1));
                String b = String.valueOf(args.get(2));
                return ScriptLangCatalog.isEnglish(player) ? b : a;
            } else {
                return String.valueOf(args.get(0));
            }
            return pickFromMap(map, ScriptLangCatalog.languageOf(player));
        }
    }

    /** {@code tr(key [, player] [, ...args|dict])} — workspace / plugin lang files. */
    public static final class Tr implements ScriptFunction {
        @Override public String getName() { return "tr"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return "";
            String key = String.valueOf(args.get(0));
            int i = 1;
            Player p = resolveSourcePlayer(source);
            if (args.size() > 1) {
                Object raw = args.get(1);
                if (!(raw instanceof Map<?, ?>) && !(raw instanceof List<?>)) {
                    if (raw instanceof Player pl) {
                        p = pl;
                        i = 2;
                    } else {
                        Player found = ItemStackScriptUtil.resolvePlayer(raw, source);
                        if (found != null) {
                            p = found;
                            i = 2;
                        }
                    }
                }
            }
            List<Object> fmt = i < args.size() ? args.subList(i, args.size()) : List.of();
            return ScriptLangCatalog.tr(source.getServer(), p, key, fmt);
        }
    }

    public static final class HasTr implements ScriptFunction {
        @Override public String getName() { return "hasTr"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            String key = String.valueOf(args.get(0));
            Player p = args.size() >= 2
                    ? resolvePlayer(args.get(1), source)
                    : resolveSourcePlayer(source);
            return ScriptLangCatalog.has(source.getServer(), p, key);
        }
    }

    public static final class Languages implements ScriptFunction {
        @Override public String getName() { return "languages"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return ScriptLangCatalog.languages(source.getServer());
        }
    }

    public static final class Format implements ScriptFunction {
        @Override public String getName() { return "format"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return "";
            String template = String.valueOf(args.get(0));
            List<Object> rest = args.size() > 1 ? args.subList(1, args.size()) : List.of();
            return ScriptLangCatalog.applyFormat(template, rest);
        }
    }

    public static final class ReloadLang implements ScriptFunction {
        @Override public String getName() { return "reloadLang"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            ScriptLangCatalog.invalidate();
            ScriptLangCatalog.languages(source.getServer());
            return true;
        }
    }

    static String pickFromMap(Map<?, ?> map, String language) {
        if (map == null || map.isEmpty()) return "";
        String full = language == null ? "en_us" : language.toLowerCase(Locale.ROOT);
        String shortCode = ScriptLangCatalog.shortLang(full);
        Object v = getIgnoreCase(map, full);
        if (v != null) return String.valueOf(v);
        v = getIgnoreCase(map, shortCode);
        if (v != null) return String.valueOf(v);
        v = getIgnoreCase(map, "default");
        if (v != null) return String.valueOf(v);
        v = getIgnoreCase(map, "fallback");
        if (v != null) return String.valueOf(v);
        // Prefer English then Russian then first entry
        v = getIgnoreCase(map, "en");
        if (v != null && ScriptLangCatalog.isEnglish(full)) return String.valueOf(v);
        v = getIgnoreCase(map, "ru");
        if (v != null && !ScriptLangCatalog.isEnglish(full)) return String.valueOf(v);
        v = getIgnoreCase(map, "en");
        if (v != null) return String.valueOf(v);
        v = getIgnoreCase(map, "ru");
        if (v != null) return String.valueOf(v);
        for (Object val : map.values()) {
            if (val != null) return String.valueOf(val);
        }
        return "";
    }

    private static Object getIgnoreCase(Map<?, ?> map, String key) {
        if (key == null) return null;
        if (map.containsKey(key)) return map.get(key);
        for (var e : map.entrySet()) {
            if (e.getKey() != null && key.equalsIgnoreCase(String.valueOf(e.getKey()))) {
                return e.getValue();
            }
        }
        return null;
    }

    private static Player resolveOptionalPlayer(List<Object> args, int index, CommandSourceStack source) {
        if (args != null && args.size() > index) {
            Player p = resolvePlayer(args.get(index), source);
            if (p != null) return p;
        }
        return resolveSourcePlayer(source);
    }

    private static Player resolveSourcePlayer(CommandSourceStack source) {
        try {
            if (source.getEntity() instanceof Player p) return p;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Player resolvePlayer(Object target, CommandSourceStack source) {
        if (target instanceof Player p) return p;
        if (target instanceof String || target instanceof Number) {
            return ItemStackScriptUtil.resolvePlayer(target, source);
        }
        Player via = ItemStackScriptUtil.resolvePlayer(target, source);
        if (via != null) return via;
        if (target instanceof ServerPlayer sp) return sp;
        return resolveSourcePlayer(source);
    }
}
