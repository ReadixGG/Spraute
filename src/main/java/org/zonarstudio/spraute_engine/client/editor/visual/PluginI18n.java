package org.zonarstudio.spraute_engine.client.editor.visual;

import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-plugin UI translations. Authors ship {@code lang/en.json} (and later other locales)
 * next to {@code plugin.json}. Keys are the original Russian (or source) strings from
 * {@code .spr} / {@code categories.json} / {@code plugin.json}; values are English.
 *
 * <p>Special keys (optional):
 * <ul>
 *   <li>{@code plugin.name} — display name override</li>
 *   <li>{@code plugin.description} — description override</li>
 * </ul>
 *
 * <p>Active when Minecraft language is English ({@link EditorI18n#english()}).
 */
public final class PluginI18n {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** folder name and plugin namespace → RU→EN map */
    private static final Map<String, Map<String, String>> catalogs = new HashMap<>();
    /** merged lookup for shared category names across plugins */
    private static Map<String, String> merged = Map.of();

    private PluginI18n() {}

    public static void clear() {
        catalogs.clear();
        merged = Map.of();
    }

    /**
     * Load {@code lang/en.json} or {@code lang/en_us.json} from a plugin folder.
     * Registers under both folder name and namespace.
     */
    public static void load(Path pluginRoot, String folder, String namespace) {
        Path langDir = pluginRoot.resolve("lang");
        Path file = firstExisting(langDir.resolve("en.json"), langDir.resolve("en_us.json"));
        if (file == null) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, String> map = parseCatalog(json);
            if (map == null || map.isEmpty()) return;
            Map<String, String> copy = Collections.unmodifiableMap(new HashMap<>(map));
            if (folder != null && !folder.isBlank()) catalogs.put(folder, copy);
            if (namespace != null && !namespace.isBlank()) catalogs.put(namespace, copy);
            rebuildMerged();
            LOGGER.info("[Spraute Engine] Plugin EN catalog loaded for '{}' ({} strings)", folder, map.size());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[Spraute Engine] Failed to load plugin lang for '{}': {}", folder, e.toString());
        }
    }

    /** Last duplicate key wins — Gson throws on duplicates and would leave blocks Russian. */
    private static Map<String, String> parseCatalog(String json) throws IOException {
        Map<String, String> result = new HashMap<>();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.beginObject();
            while (reader.hasNext()) {
                result.put(reader.nextName(), reader.nextString());
            }
            reader.endObject();
        }
        return result;
    }

    private static Path firstExisting(Path a, Path b) {
        if (Files.isRegularFile(a)) return a;
        if (Files.isRegularFile(b)) return b;
        return null;
    }

    private static void rebuildMerged() {
        Map<String, String> m = new HashMap<>();
        for (Map<String, String> cat : catalogs.values()) {
            m.putAll(cat);
        }
        merged = Collections.unmodifiableMap(m);
    }

    /** Translate a plugin UI string for the given plugin folder or namespace. */
    public static String t(String pluginKey, String source) {
        if (source == null || source.isEmpty()) return source;
        if (!EditorI18n.english()) return source;
        if (pluginKey != null && !pluginKey.isBlank()) {
            Map<String, String> cat = catalogs.get(pluginKey);
            if (cat != null) {
                String en = cat.get(source);
                if (en != null) return en;
            }
        }
        String en = merged.get(source);
        return en != null ? en : source;
    }

    /** Translate without a plugin key (categories shared across plugins). */
    public static String t(String source) {
        return t(null, source);
    }

    public static String pluginName(VisualBlockRegistry.PluginInfo p) {
        if (p == null) return "";
        if (!EditorI18n.english()) return p.name == null ? "" : p.name;
        Map<String, String> cat = catalogs.get(p.folder);
        if (cat != null) {
            String override = cat.get("plugin.name");
            if (override != null && !override.isBlank()) return override;
            if (p.name != null) {
                String en = cat.get(p.name);
                if (en != null) return en;
            }
        }
        return p.name == null ? "" : p.name;
    }

    public static String pluginDescription(VisualBlockRegistry.PluginInfo p) {
        if (p == null) return "";
        if (!EditorI18n.english()) return p.description == null ? "" : p.description;
        Map<String, String> cat = catalogs.get(p.folder);
        if (cat != null) {
            String override = cat.get("plugin.description");
            if (override != null) return override;
            if (p.description != null) {
                String en = cat.get(p.description);
                if (en != null) return en;
            }
        }
        return p.description == null ? "" : p.description;
    }
}
