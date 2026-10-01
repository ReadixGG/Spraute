package org.zonarstudio.spraute_engine.entity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Tracks spawned geo-model entities by script/scene id. */
public final class ModelManager {
    private static final Map<String, UUID> MODELS = new HashMap<>();

    private ModelManager() {}

    public static void track(String name, UUID uuid) {
        if (name == null || name.isBlank() || uuid == null) return;
        MODELS.put(name.toLowerCase(), uuid);
    }

    public static UUID get(String name) {
        if (name == null) return null;
        return MODELS.get(name.toLowerCase());
    }

    /** Tracking id used by {@code create model} / scene spawn, or null. */
    public static String findId(UUID uuid) {
        if (uuid == null) return null;
        for (var e : MODELS.entrySet()) {
            if (uuid.equals(e.getValue())) return e.getKey();
        }
        return null;
    }

    public static Entity getEntity(String name, Level level) {
        UUID uuid = get(name);
        if (uuid == null) return null;
        if (level instanceof ServerLevel serverLevel) {
            Entity local = serverLevel.getEntity(uuid);
            if (local != null) return local;
            MinecraftServer server = serverLevel.getServer();
            if (server != null) return findOnServer(server, uuid);
        }
        return null;
    }

    public static Entity getEntity(String name, MinecraftServer server) {
        UUID uuid = get(name);
        if (uuid == null || server == null) return null;
        return findOnServer(server, uuid);
    }

    public static void remove(String name) {
        if (name == null) return;
        MODELS.remove(name.toLowerCase());
    }

    /** Removes every tracking key that points at the given UUID. */
    public static void removeUuid(UUID uuid) {
        if (uuid == null) return;
        Iterator<Map.Entry<String, UUID>> it = MODELS.entrySet().iterator();
        while (it.hasNext()) {
            if (uuid.equals(it.next().getValue())) it.remove();
        }
    }

    public static void clear() {
        MODELS.clear();
    }

    private static Entity findOnServer(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }
}
