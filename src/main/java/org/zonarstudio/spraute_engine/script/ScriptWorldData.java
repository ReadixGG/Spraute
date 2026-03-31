package org.zonarstudio.spraute_engine.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent storage for world-scoped script variables.
 * Survives server restarts.
 * <p>
 * List/Map values are stored as JSON ({@code j:...}). Live objects are cached so in-place mutations
 * (e.g. {@code list_add}) stay consistent until {@link #save} flushes them to NBT.
 */
public class ScriptWorldData extends SavedData {

    private static final String DATA_NAME = "spraute_engine_vars";
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Map<String, String> storage = new HashMap<>();
    /** Deserialized / mutated values; kept in sync with storage on save. */
    private final Map<String, Object> liveCache = new HashMap<>();

    public ScriptWorldData() {
        super();
    }

    public static ScriptWorldData load(CompoundTag tag) {
        ScriptWorldData data = new ScriptWorldData();
        CompoundTag vars = tag.getCompound("vars");
        for (String key : vars.getAllKeys()) {
            data.storage.put(key, vars.getString(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        flushLiveToStorage();
        CompoundTag vars = new CompoundTag();
        for (Map.Entry<String, String> e : storage.entrySet()) {
            vars.putString(e.getKey(), e.getValue());
        }
        tag.put("vars", vars);
        return tag;
    }

    private void flushLiveToStorage() {
        for (Map.Entry<String, Object> e : liveCache.entrySet()) {
            storage.put(e.getKey(), serialize(e.getValue()));
        }
    }

    public static ScriptWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ScriptWorldData::load,
                ScriptWorldData::new,
                DATA_NAME
        );
    }

    public void put(String key, Object value) {
        liveCache.put(key, value);
        storage.put(key, serialize(value));
        setDirty();
    }

    public Object get(String key, MinecraftServer server, ServerLevel level) {
        if (liveCache.containsKey(key)) {
            return liveCache.get(key);
        }
        String s = storage.get(key);
        if (s == null) {
            return null;
        }
        Object o = deserialize(s, server, level);
        if (o != null) {
            liveCache.put(key, o);
        }
        return o;
    }

    public boolean has(String key) {
        return storage.containsKey(key) || liveCache.containsKey(key);
    }

    /** Удалить переменную мира из кэша и сохранённых данных. */
    public void remove(String key) {
        liveCache.remove(key);
        storage.remove(key);
        setDirty();
    }

    /** Очистить все переменные этого измерения. */
    public void clearAll() {
        liveCache.clear();
        storage.clear();
        setDirty();
    }

    public java.util.Set<String> allKeys() {
        java.util.HashSet<String> keys = new java.util.HashSet<>(storage.keySet());
        keys.addAll(liveCache.keySet());
        return keys;
    }

    private static String serialize(Object value) {
        if (value == null) return "n:";
        if (value instanceof Boolean b) return "b:" + b;
        if (value instanceof Number n) return "n:" + n.toString();
        if (value instanceof net.minecraft.world.entity.player.Player p) return "p:" + p.getUUID().toString();
        if (value instanceof net.minecraft.world.entity.Entity e) return "e:" + e.getUUID().toString();
        if (value instanceof String id && org.zonarstudio.spraute_engine.entity.NpcManager.get(id) != null) {
            return "npc:" + id;
        }
        if (value instanceof java.util.List<?> || value instanceof java.util.Map<?, ?>) {
            return "j:" + JSON.toJson(value);
        }
        return "s:" + value.toString();
    }

    private static Object deserialize(String s, MinecraftServer server, ServerLevel level) {
        if (s == null || s.isEmpty()) return null;
        if (s.startsWith("j:")) {
            try {
                return JSON.fromJson(s.substring(2), Object.class);
            } catch (Exception e) {
                return null;
            }
        }
        if (s.startsWith("b:")) return Boolean.parseBoolean(s.substring(2));
        if (s.startsWith("n:")) {
            String num = s.substring(2);
            if (num.isEmpty()) return null;
            if (num.contains(".")) return Double.parseDouble(num);
            return Integer.parseInt(num);
        }
        if (s.startsWith("p:")) {
            try {
                UUID uuid = UUID.fromString(s.substring(2));
                return server.getPlayerList().getPlayer(uuid);
            } catch (Exception e) {
                return null;
            }
        }
        if (s.startsWith("e:")) {
            try {
                UUID uuid = UUID.fromString(s.substring(2));
                return level.getEntity(uuid);
            } catch (Exception e) {
                return null;
            }
        }
        if (s.startsWith("npc:")) {
            String id = s.substring(4);
            UUID uuid = org.zonarstudio.spraute_engine.entity.NpcManager.get(id);
            return uuid != null ? level.getEntity(uuid) : id;
        }
        if (s.startsWith("s:")) return s.substring(2);
        return s;
    }
}
