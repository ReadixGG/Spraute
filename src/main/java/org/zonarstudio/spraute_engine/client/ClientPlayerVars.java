package org.zonarstudio.spraute_engine.client;

import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.PlayerVarsPacket;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Editor cache of {@code player.data} / {@code player.savedData}. */
public final class ClientPlayerVars {
    public record Snapshot(Map<String, Object> data, Map<String, Object> saved, long time) {}

    private static final Map<String, Snapshot> CACHE = new ConcurrentHashMap<>();
    private static long lastRequestAt;
    private static String lastRequestUuid = "";

    private ClientPlayerVars() {}

    public static Snapshot get(String uuid) {
        return uuid == null ? null : CACHE.get(uuid);
    }

    public static void apply(String uuid, Map<String, Object> data, Map<String, Object> saved) {
        if (uuid == null || uuid.isBlank()) return;
        CACHE.put(uuid, new Snapshot(
                data == null ? Map.of() : new LinkedHashMap<>(data),
                saved == null ? Map.of() : new LinkedHashMap<>(saved),
                System.currentTimeMillis()));
    }

    public static void requestIfStale(String uuid) {
        if (uuid == null || uuid.isBlank()) return;
        long now = System.currentTimeMillis();
        if (uuid.equals(lastRequestUuid) && now - lastRequestAt < 400L) return;
        lastRequestUuid = uuid;
        lastRequestAt = now;
        ModNetwork.CHANNEL.sendToServer(PlayerVarsPacket.request(uuid));
    }

    public static void clear() {
        CACHE.clear();
    }
}
