package org.zonarstudio.spraute_engine.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks script billboard ids → entity UUID (like {@link NpcManager}). */
public final class BillboardManager {

    private static final Map<String, UUID> BILLBOARDS = new HashMap<>();

    private BillboardManager() {}

    public static void track(String id, UUID uuid) {
        if (id == null || id.isBlank() || uuid == null) return;
        BILLBOARDS.put(id.toLowerCase(), uuid);
    }

    public static UUID get(String id) {
        if (id == null) return null;
        return BILLBOARDS.get(id.toLowerCase());
    }

    public static net.minecraft.world.entity.Entity getEntity(String id, net.minecraft.world.level.Level level) {
        UUID uuid = get(id);
        if (uuid == null || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return null;
        return serverLevel.getEntity(uuid);
    }

    public static void remove(String id) {
        if (id == null) return;
        BILLBOARDS.remove(id.toLowerCase());
    }

    public static void removeByUuid(UUID uuid) {
        if (uuid == null) return;
        BILLBOARDS.entrySet().removeIf(e -> uuid.equals(e.getValue()));
    }

    public static void clear() {
        BILLBOARDS.clear();
    }
}
