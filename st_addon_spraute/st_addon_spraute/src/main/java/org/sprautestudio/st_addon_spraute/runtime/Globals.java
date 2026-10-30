package org.sprautestudio.st_addon_spraute.runtime;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Values every story of the world shares and keeps across restarts: {@code global set "gold" 10} in
 * one script, {@code global("gold")} in another. Kept in {@code <world>/spraute/globals.json}.
 */
public final class Globals {
    private Globals() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Map<String, Object> values;

    private static Path file() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : s.getWorldPath(LevelResource.ROOT).resolve("spraute").resolve("globals.json");
    }

    private static Map<String, Object> values() {
        if (values != null) return values;
        values = new LinkedHashMap<>();
        Path f = file();
        if (f != null && Files.exists(f)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    JsonElement v = e.getValue();
                    if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) values.put(e.getKey(), v.getAsDouble());
                    else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) values.put(e.getKey(), v.getAsBoolean());
                    else if (v.isJsonPrimitive()) values.put(e.getKey(), v.getAsString());
                }
            } catch (Exception ex) {
                St_addon_spraute.LOGGER.warn("Spraute: cannot read globals", ex);
            }
        }
        return values;
    }

    private static void save() {
        Path f = file();
        if (f == null || values == null) return;
        JsonObject o = new JsonObject();
        values.forEach((k, v) -> {
            if (v instanceof Number n) o.addProperty(k, n.doubleValue());
            else if (v instanceof Boolean b) o.addProperty(k, b);
            else o.addProperty(k, String.valueOf(v));
        });
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot save globals", e);
        }
    }

    public static Object get(String name, Object fallback) {
        Object v = values().get(name);
        return v != null ? v : fallback;
    }

    public static boolean has(String name) {
        return values().containsKey(name);
    }

    public static void set(String name, Object value) {
        if (value instanceof Number n) value = n.doubleValue();
        else if (!(value instanceof Boolean) && !(value instanceof String)) value = Eval.str(value);
        values().put(name, value);
        save();
    }

    public static void add(String name, double by) {
        Object v = values().get(name);
        set(name, (v instanceof Number n ? n.doubleValue() : 0) + by);
    }

    public static void clear(String name) {
        if (values().remove(name) != null) save();
    }

    public static Map<String, Object> all() {
        return values();
    }

    public static void unload() {
        values = null;
    }
}
