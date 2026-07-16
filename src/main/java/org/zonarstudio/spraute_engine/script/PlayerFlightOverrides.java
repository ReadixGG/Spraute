package org.zonarstudio.spraute_engine.script;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Скриптовый полёт в выживании (abilities.mayfly / flying). */
public final class PlayerFlightOverrides {
    private static final Map<UUID, Boolean> FLIGHT = new ConcurrentHashMap<>();

    private PlayerFlightOverrides() {}

    public static void set(ServerPlayer player, boolean enabled) {
        if (player == null) return;
        if (enabled) {
            FLIGHT.put(player.getUUID(), true);
            applyFlight(player, true);
        } else {
            FLIGHT.remove(player.getUUID());
            applyFlight(player, false);
        }
    }

    public static boolean get(ServerPlayer player) {
        if (player == null) return false;
        return Boolean.TRUE.equals(FLIGHT.get(player.getUUID()));
    }

    public static void apply(ServerPlayer player) {
        if (player == null) return;
        if (Boolean.TRUE.equals(FLIGHT.get(player.getUUID()))) {
            applyFlight(player, true);
        }
    }

    public static void clear(UUID playerId) {
        if (playerId != null) FLIGHT.remove(playerId);
    }

    private static void applyFlight(ServerPlayer player, boolean scriptFly) {
        var ab = player.getAbilities();
        if (scriptFly) {
            ab.mayfly = true;
            ab.flying = true;
        } else {
            GameType mode = player.gameMode.getGameModeForPlayer();
            boolean creative = mode == GameType.CREATIVE;
            boolean spectator = mode == GameType.SPECTATOR;
            ab.mayfly = creative || spectator;
            if (spectator) {
                ab.flying = true;
            } else if (!creative) {
                ab.flying = false;
            }
        }
        player.onUpdateAbilities();
    }
}
