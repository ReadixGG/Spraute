package org.zonarstudio.spraute_engine.script;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Множитель скорости копания (1.0 = ванильная скорость). */
public final class PlayerDigSpeedOverrides {
    private static final Map<UUID, Double> MULTIPLIERS = new ConcurrentHashMap<>();

    private PlayerDigSpeedOverrides() {}

    public static void set(ServerPlayer player, double multiplier) {
        if (player == null) return;
        MULTIPLIERS.put(player.getUUID(), multiplier);
    }

    public static double get(ServerPlayer player) {
        if (player == null) return 1.0;
        return MULTIPLIERS.getOrDefault(player.getUUID(), 1.0);
    }

    public static void clear(UUID playerId) {
        if (playerId != null) MULTIPLIERS.remove(playerId);
    }

    public static float apply(ServerPlayer player, float speed) {
        if (player == null) return speed;
        return (float) (speed * get(player));
    }
}
