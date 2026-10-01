package org.zonarstudio.spraute_engine.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

/** {@code META-INF/spraute-pack.json} inside a story JAR. */
public final class StoryPackManifest {
    public static final int SCHEMA = 1;
    public static final String JAR_PATH = "META-INF/spraute-pack.json";
    public static final String CONTENT_ROOT = "spraute_pack";
    public static final String WORLD_ROOT = "spraute_pack/_world";

    public int schema = SCHEMA;
    public String id = "";
    public String displayName = "";
    public String version = "1.0.0";
    public String scene = "";
    public String engine = "1.7";
    public String worldFolder = "";
    public String worldInstallName = "";

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("schema", schema);
        o.addProperty("id", id == null ? "" : id);
        o.addProperty("displayName", displayName == null ? "" : displayName);
        o.addProperty("version", version == null ? "" : version);
        o.addProperty("scene", scene == null ? "" : scene);
        o.addProperty("engine", engine == null ? "" : engine);
        if (worldFolder != null && !worldFolder.isBlank()) {
            JsonObject w = new JsonObject();
            w.addProperty("folder", worldFolder);
            w.addProperty("installName", worldInstallName == null || worldInstallName.isBlank()
                    ? worldFolder : worldInstallName);
            o.add("world", w);
        }
        return o;
    }

    public static StoryPackManifest fromJson(String text) {
        StoryPackManifest m = new StoryPackManifest();
        if (text == null || text.isBlank()) return m;
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        if (o.has("schema")) m.schema = o.get("schema").getAsInt();
        m.id = str(o, "id");
        m.displayName = str(o, "displayName");
        m.version = str(o, "version");
        if (m.version.isBlank()) m.version = "1.0.0";
        m.scene = str(o, "scene");
        m.engine = str(o, "engine");
        if (o.has("world") && o.get("world").isJsonObject()) {
            JsonObject w = o.getAsJsonObject("world");
            m.worldFolder = str(w, "folder");
            m.worldInstallName = str(w, "installName");
            if (m.worldInstallName.isBlank()) m.worldInstallName = m.worldFolder;
        }
        return m;
    }

    public static String sanitizeModId(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_]", "");
        if (s.isEmpty()) return "";
        if (s.charAt(0) < 'a' || s.charAt(0) > 'z') s = "s_" + s;
        if (s.length() > 64) s = s.substring(0, 64);
        if ("minecraft".equals(s) || "forge".equals(s) || "spraute_engine".equals(s)) {
            s = "story_" + s;
        }
        return s;
    }

    public static String sanitizeFileToken(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", "_");
        return s.replaceAll("\\s+", "_");
    }

    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "";
        try {
            return o.get(key).getAsString().trim();
        } catch (RuntimeException e) {
            return "";
        }
    }
}
