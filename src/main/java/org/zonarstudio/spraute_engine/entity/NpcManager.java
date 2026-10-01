package org.zonarstudio.spraute_engine.entity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class NpcManager {
    private static final Map<String, UUID> NPCS = new HashMap<>();

    public static void track(String name, UUID uuid) {
        if (name == null || name.isBlank() || uuid == null) return;
        NPCS.put(name.toLowerCase(), uuid);
    }

    public static UUID get(String name) {
        if (name == null) return null;
        return NPCS.get(name.toLowerCase());
    }

    /** Tracking id used by {@code create npc} / scene spawn, or null. */
    public static String findId(UUID uuid) {
        if (uuid == null) return null;
        for (var e : NPCS.entrySet()) {
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
        NPCS.remove(name.toLowerCase());
    }

    /** Removes every tracking key that points at the given UUID. */
    public static void removeUuid(UUID uuid) {
        if (uuid == null) return;
        Iterator<Map.Entry<String, UUID>> it = NPCS.entrySet().iterator();
        while (it.hasNext()) {
            if (uuid.equals(it.next().getValue())) it.remove();
        }
    }

    public static void clear() {
        NPCS.clear();
    }

    private static Entity findOnServer(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }
}
