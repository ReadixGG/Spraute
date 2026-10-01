package org.zonarstudio.spraute_engine.client.editor;

import com.google.gson.stream.JsonReader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Russian → English catalog for editor UI ({@code assets/.../lang/editor_en.json}). */
public final class EditorI18nCatalog {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static Map<String, String> map = Collections.emptyMap();
    private static Map<String, String> reverse = Collections.emptyMap();
    private static boolean loaded;

    private EditorI18nCatalog() {}

    public static String translate(String russian) {
        ensureLoaded();
        return map.get(russian);
    }

    /** English → Russian (for undoing strings baked while the client was on English). */
    public static String reverse(String english) {
        ensureLoaded();
        return reverse.get(english);
    }

    /** All Russian keys that are used as dynamic error prefixes (longest first). */
    public static List<String> prefixesLongestFirst() {
        ensureLoaded();
        return map.keySet().stream()
                .filter(k -> k.endsWith(": ") || k.endsWith(": ") || k.endsWith("«") || k.startsWith("⚠"))
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = openCatalogStream()) {
            if (in == null) {
                LOGGER.error("[Spraute Engine] editor_en.json not found — editor stays Russian");
                return;
            }
            Map<String, String> parsed = parseCatalog(in);
            if (parsed != null && !parsed.isEmpty()) {
                map = Collections.unmodifiableMap(new HashMap<>(parsed));
                Map<String, String> rev = new HashMap<>();
                for (var e : parsed.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null && !e.getValue().isBlank()) {
                        rev.putIfAbsent(e.getValue(), e.getKey());
                    }
                }
                reverse = Collections.unmodifiableMap(rev);
                LOGGER.info("[Spraute Engine] Editor EN catalog loaded ({} strings)", map.size());
            } else {
                LOGGER.error("[Spraute Engine] editor_en.json parsed empty");
            }
        } catch (Throwable t) {
            LOGGER.error("[Spraute Engine] Failed to load editor_en.json: {}", t.toString());
            map = Collections.emptyMap();
            reverse = Collections.emptyMap();
        }
    }

    /** Last duplicate key wins — Gson throws on duplicates and would leave the editor Russian. */
    private static Map<String, String> parseCatalog(InputStream in) throws java.io.IOException {
        Map<String, String> result = new HashMap<>();
        try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                result.put(reader.nextName(), reader.nextString());
            }
            reader.endObject();
        }
        return result;
    }

    private static InputStream openCatalogStream() {
        InputStream classpath = EditorI18nCatalog.class.getResourceAsStream(
                "/assets/spraute_engine/lang/editor_en.json");
        if (classpath != null) return classpath;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getResourceManager() == null) return null;
            //? if >=1.20.1 {
            ResourceLocation loc = new ResourceLocation("spraute_engine", "lang/editor_en.json");
            //?} else {
            /*ResourceLocation loc = new ResourceLocation("spraute_engine", "lang/editor_en.json");
            *///?}
            Optional<Resource> res = mc.getResourceManager().getResource(loc);
            if (res.isPresent()) return res.get().open();
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Force reload after resource pack refresh. */
    public static void invalidate() {
        loaded = false;
        map = Collections.emptyMap();
        reverse = Collections.emptyMap();
    }
}
