package org.zonarstudio.spraute_engine.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RenderManager {
    private static final Map<String, UUID> RENDERS = new HashMap<>();

    public static void track(String name, UUID uuid) {
        RENDERS.put(name.toLowerCase(), uuid);
    }

    public static UUID get(String name) {
        return RENDERS.get(name.toLowerCase());
    }

    public static net.minecraft.world.entity.Entity getEntity(String name, net.minecraft.world.level.Level level) {
        UUID uuid = get(name);
        if (uuid == null) return null;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            return serverLevel.getEntity(uuid);
        }
        return null;
    }

    public static void remove(String name) {
        RENDERS.remove(name.toLowerCase());
    }

    public static void clear() {
        RENDERS.clear();
    }
}
