package org.zonarstudio.spraute_engine.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Project translations for scripts / UI / dialogs.
 *
 * <pre>
 * spraute_engine/lang/en.json
 * spraute_engine/lang/ru.json
 * spraute_engine/lang/en/quest.json     → keys prefixed {@code quest.}
 * spraute_engine/plugins/&lt;id&gt;/lang/en.json
 * spraute_engine/lang/_meta.json        → { "fallback": "en" }
 * </pre>
 *
 * Nested JSON objects become dotted keys ({@code quest.start}).
 * Values may use {@code {0}}, {@code {name}} or {@code %s} placeholders.
 */
public final class ScriptLangCatalog {
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, Map<String, String>> BY_LANG = new HashMap<>();
    private static String fallbackLang = "en";
    private static Path loadedRoot;
    private static long loadedAtMs;

    private ScriptLangCatalog() {}

    public static String languageOf(Player player) {
        if (player instanceof ServerPlayer sp) {
            try {
                String lang = sp.getLanguage();
                if (lang != null && !lang.isBlank()) {
                    return lang.toLowerCase(Locale.ROOT);
                }
            } catch (Throwable ignored) {
            }
        }
        return "en_us";
    }

    /** Short code: {@code en_us} → {@code en}. */
    public static String shortLang(String language) {
        if (language == null || language.isBlank()) return "en";
        String s = language.toLowerCase(Locale.ROOT).trim();
        int us = s.indexOf('_');
        return us > 0 ? s.substring(0, us) : s;
    }

    public static boolean isEnglish(Player player) {
        return shortLang(languageOf(player)).equals("en");
    }

    public static boolean isEnglish(String language) {
        return shortLang(language).equals("en");
    }

    public static String tr(MinecraftServer server, Player player, String key) {
        return tr(server, player, key, List.of());
    }

    public static String tr(MinecraftServer server, Player player, String key, List<Object> formatArgs) {
        if (key == null || key.isBlank()) return "";
        ensureLoaded(server);
        String raw = lookup(key, languageOf(player));
        if (raw == null) return key;
        return applyFormat(raw, formatArgs);
    }

    public static boolean has(MinecraftServer server, Player player, String key) {
        if (key == null || key.isBlank()) return false;
        ensureLoaded(server);
        return lookup(key, languageOf(player)) != null;
    }

    public static List<String> languages(MinecraftServer server) {
        ensureLoaded(server);
        List<String> out = new ArrayList<>(BY_LANG.keySet());
        Collections.sort(out);
        return out;
    }

    public static String applyFormat(String template, List<Object> args) {
        if (template == null) return "";
        if (args == null || args.isEmpty()) return template;
        if (args.size() == 1 && args.get(0) instanceof Map<?, ?> named) {
            return applyNamed(template, named);
        }
        String out = template;
        for (int i = 0; i < args.size(); i++) {
            out = out.replace("{" + i + "}", stringify(args.get(i)));
        }
        for (Object arg : args) {
            int at = out.indexOf("%s");
            if (at < 0) break;
            out = out.substring(0, at) + stringify(arg) + out.substring(at + 2);
        }
        return out;
    }

    public static void invalidate() {
        BY_LANG.clear();
        loadedRoot = null;
        loadedAtMs = 0;
        fallbackLang = "en";
    }

    /** Editable project table ({@code spraute_engine/lang}), without plugin overlays. */
    public static final class Table {
        public String fallback = "en";
        public final List<String> languages = new ArrayList<>();
        /** key → language → text */
        public final LinkedHashMap<String, LinkedHashMap<String, String>> rows = new LinkedHashMap<>();

        public String get(String key, String lang) {
            LinkedHashMap<String, String> row = rows.get(key);
            if (row == null || lang == null) return "";
            String v = row.get(lang);
            return v == null ? "" : v;
        }

        public void set(String key, String lang, String value) {
            if (key == null || key.isBlank() || lang == null || lang.isBlank()) return;
            rows.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(lang, value == null ? "" : value);
        }

        public void renameKey(String from, String to) {
            if (from == null || to == null || from.equals(to) || !rows.containsKey(from)) return;
            LinkedHashMap<String, String> row = rows.remove(from);
            rows.put(to, row);
        }

        public void removeKey(String key) {
            rows.remove(key);
        }
    }

    public static Table loadProject(Path workspaceRoot) {
        Table table = new Table();
        if (workspaceRoot == null) return table;
        Path langRoot = workspaceRoot.resolve("lang");
        table.fallback = "en";
        Path meta = langRoot.resolve("_meta.json");
        if (Files.isRegularFile(meta)) {
            try {
                JsonObject obj = JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8)).getAsJsonObject();
                if (obj.has("fallback") && obj.get("fallback").isJsonPrimitive()) {
                    String fb = obj.get("fallback").getAsString();
                    if (fb != null && !fb.isBlank()) table.fallback = fb.toLowerCase(Locale.ROOT).trim();
                }
            } catch (Exception ignored) {
            }
        }
        Map<String, Map<String, String>> byLang = new LinkedHashMap<>();
        loadLangTreeInto(langRoot, byLang);
        table.languages.addAll(byLang.keySet());
        Collections.sort(table.languages);
        if (!table.languages.contains("en")) table.languages.add(0, "en");
        if (!table.languages.contains("ru") && Files.isRegularFile(langRoot.resolve("ru.json"))) {
            table.languages.add("ru");
            Collections.sort(table.languages);
        }
        LinkedHashMap<String, Boolean> keyOrder = new LinkedHashMap<>();
        for (String lang : table.languages) {
            Map<String, String> map = byLang.get(lang);
            if (map == null) continue;
            for (String key : map.keySet()) keyOrder.putIfAbsent(key, Boolean.TRUE);
        }
        List<String> keys = new ArrayList<>(keyOrder.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            for (String lang : table.languages) {
                Map<String, String> map = byLang.get(lang);
                String v = map == null ? null : map.get(key);
                row.put(lang, v == null ? "" : v);
            }
            table.rows.put(key, row);
        }
        return table;
    }

    public static void saveProject(Path workspaceRoot, Table table) throws IOException {
        if (workspaceRoot == null || table == null) return;
        Path langRoot = workspaceRoot.resolve("lang");
        Files.createDirectories(langRoot);
        String fb = table.fallback == null || table.fallback.isBlank() ? "en" : table.fallback.trim().toLowerCase(Locale.ROOT);
        JsonObject meta = new JsonObject();
        meta.addProperty("fallback", fb);
        Files.writeString(langRoot.resolve("_meta.json"), PRETTY.toJson(meta) + "\n", StandardCharsets.UTF_8);
        List<String> langs = new ArrayList<>(table.languages);
        if (langs.isEmpty()) langs.add("en");
        for (String lang : langs) {
            if (lang == null || lang.isBlank()) continue;
            String code = lang.trim().toLowerCase(Locale.ROOT);
            JsonObject root = new JsonObject();
            for (var e : table.rows.entrySet()) {
                String key = e.getKey();
                if (key == null || key.isBlank()) continue;
                String value = e.getValue() == null ? "" : e.getValue().getOrDefault(code, "");
                if (value == null) value = "";
                putDotted(root, key, value);
            }
            Files.writeString(langRoot.resolve(code + ".json"), PRETTY.toJson(root) + "\n", StandardCharsets.UTF_8);
        }
    }

    private static String lookup(String key, String language) {
        String full = language == null ? "en_us" : language.toLowerCase(Locale.ROOT);
        String shortCode = shortLang(full);
        String v = get(full, key);
        if (v != null) return v;
        v = get(shortCode, key);
        if (v != null) return v;
        if (!fallbackLang.isBlank() && !fallbackLang.equals(full) && !fallbackLang.equals(shortCode)) {
            v = get(fallbackLang, key);
            if (v != null) return v;
            v = get(shortLang(fallbackLang), key);
            if (v != null) return v;
        }
        if (!"en".equals(shortCode) && !"en".equals(fallbackLang)) {
            v = get("en", key);
            if (v != null) return v;
            v = get("en_us", key);
            if (v != null) return v;
        }
        if (!"ru".equals(shortCode) && !"ru".equals(fallbackLang)) {
            v = get("ru", key);
            if (v != null) return v;
            v = get("ru_ru", key);
            if (v != null) return v;
        }
        return null;
    }

    private static String get(String lang, String key) {
        Map<String, String> map = BY_LANG.get(lang);
        return map == null ? null : map.get(key);
    }

    private static String applyNamed(String template, Map<?, ?> named) {
        if (named == null || named.isEmpty()) return template;
        List<String> keys = new ArrayList<>();
        for (Object k : named.keySet()) {
            if (k != null) keys.add(String.valueOf(k));
        }
        keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
        String out = template;
        for (String k : keys) {
            out = out.replace("{" + k + "}", stringify(named.get(k)));
        }
        return out;
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (server == null) return;
        Path root = server.getServerDirectory().toPath().resolve("spraute_engine");
        long now = System.currentTimeMillis();
        if (root.equals(loadedRoot) && now - loadedAtMs < 2000L && !BY_LANG.isEmpty()) return;
        loadedRoot = root;
        loadedAtMs = now;
        BY_LANG.clear();
        fallbackLang = "en";
        loadMeta(root.resolve("lang").resolve("_meta.json"));
        for (org.zonarstudio.spraute_engine.resource.StoryPackRegistry.Mount mount
                : org.zonarstudio.spraute_engine.resource.StoryPackRegistry.packs()) {
            Path packLang = mount.packRoot().resolve("lang");
            if (Files.isRegularFile(packLang.resolve("_meta.json"))) loadMeta(packLang.resolve("_meta.json"));
            if (Files.isDirectory(packLang)) loadLangTree(packLang);
        }
        Path plugins = root.resolve("plugins");
        if (Files.isDirectory(plugins)) {
            try (Stream<Path> stream = Files.list(plugins)) {
                stream.filter(Files::isDirectory).sorted().forEach(plugin ->
                        loadLangTree(plugin.resolve("lang")));
            } catch (IOException ignored) {
            }
        }
        loadLangTree(root.resolve("lang"));
    }

    private static void loadMeta(Path meta) {
        if (!Files.isRegularFile(meta)) return;
        try {
            JsonObject obj = JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8)).getAsJsonObject();
            if (obj.has("fallback") && obj.get("fallback").isJsonPrimitive()) {
                String fb = obj.get("fallback").getAsString();
                if (fb != null && !fb.isBlank()) fallbackLang = fb.toLowerCase(Locale.ROOT).trim();
            }
        } catch (Exception ignored) {
        }
    }

    private static void loadLangTree(Path langRoot) {
        loadLangTreeInto(langRoot, BY_LANG);
    }

    private static void loadLangTreeInto(Path langRoot, Map<String, Map<String, String>> target) {
        if (!Files.isDirectory(langRoot) || target == null) return;
        try (Stream<Path> stream = Files.walk(langRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().startsWith("_"))
                    .sorted(Comparator
                            .comparingInt((Path p) -> langFileOrder(langRoot, p))
                            .thenComparing(p -> p.toString().toLowerCase(Locale.ROOT)))
                    .forEach(p -> loadLangFile(langRoot, p, target));
        } catch (IOException ignored) {
        }
    }

    /** Nested files first ({@code en/quest.json}), root {@code en.json} last so the editor table wins. */
    private static int langFileOrder(Path langRoot, Path file) {
        try {
            return langRoot.relativize(file).getNameCount() == 1 ? 1 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void loadLangFile(Path langRoot, Path file, Map<String, Map<String, String>> target) {
        Path rel;
        try {
            rel = langRoot.relativize(file);
        } catch (Exception e) {
            return;
        }
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = (dot > 0 ? fileName.substring(0, dot) : fileName).toLowerCase(Locale.ROOT);
        String lang;
        String keyPrefix = "";
        if (rel.getNameCount() == 1) {
            lang = stem;
        } else {
            lang = rel.getName(0).toString().toLowerCase(Locale.ROOT);
            keyPrefix = stem + ".";
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            Map<String, String> flat = new HashMap<>();
            flatten(keyPrefix, root, flat);
            if (flat.isEmpty()) return;
            target.computeIfAbsent(lang, k -> new HashMap<>()).putAll(flat);
        } catch (Exception ignored) {
        }
    }

    private static void putDotted(JsonObject root, String key, String value) {
        String[] parts = key.split("\\.");
        JsonObject cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            JsonElement next = cur.get(part);
            if (next == null || !next.isJsonObject()) {
                JsonObject child = new JsonObject();
                cur.add(part, child);
                cur = child;
            } else {
                cur = next.getAsJsonObject();
            }
        }
        String last = parts[parts.length - 1];
        if (!last.isEmpty()) cur.addProperty(last, value);
    }

    private static void flatten(String prefix, JsonElement el, Map<String, String> out) {
        if (el == null || el.isJsonNull()) return;
        if (el.isJsonPrimitive()) {
            if (!prefix.isEmpty()) out.put(trimDot(prefix), el.getAsString());
            return;
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (prefix.isEmpty() && looksLikeFlatStringMap(obj)) {
                for (var e : obj.entrySet()) {
                    if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                        out.put(e.getKey(), e.getValue().getAsString());
                    } else {
                        flatten(e.getKey() + ".", e.getValue(), out);
                    }
                }
                return;
            }
            for (var e : obj.entrySet()) {
                String next = prefix.isEmpty() ? e.getKey() + "." : prefix + e.getKey() + ".";
                flatten(next, e.getValue(), out);
            }
            return;
        }
        if (el.isJsonArray() && !prefix.isEmpty()) {
            out.put(trimDot(prefix), GSON.toJson(el));
        }
    }

    private static boolean looksLikeFlatStringMap(JsonObject obj) {
        for (var e : obj.entrySet()) {
            if (e.getValue() != null && e.getValue().isJsonObject()) return false;
        }
        return true;
    }

    private static String trimDot(String prefix) {
        if (prefix.endsWith(".")) return prefix.substring(0, prefix.length() - 1);
        return prefix;
    }
}
