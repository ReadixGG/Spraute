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
        var ab = player.getAbilities();
        if (enabled) {
            FLIGHT.put(player.getUUID(), true);
            ab.mayfly = true;
            ab.flying = true;
            player.onUpdateAbilities();
        } else {
            FLIGHT.remove(player.getUUID());
            restoreVanilla(player);
        }
    }

    public static boolean get(ServerPlayer player) {
        if (player == null) return false;
        return Boolean.TRUE.equals(FLIGHT.get(player.getUUID()));
    }

    /**
     * Keep {@code mayfly} in survival. Do not force {@code flying}: the player
     * must be able to land (double-jump toggle), same as creative.
     */
    public static void apply(ServerPlayer player) {
        if (player == null) return;
        if (!Boolean.TRUE.equals(FLIGHT.get(player.getUUID()))) return;
        var ab = player.getAbilities();
        if (ab.mayfly) return;
        ab.mayfly = true;
        player.onUpdateAbilities();
    }

    public static void clear(UUID playerId) {
        if (playerId != null) FLIGHT.remove(playerId);
    }

    private static void restoreVanilla(ServerPlayer player) {
        var ab = player.getAbilities();
        GameType mode = player.gameMode.getGameModeForPlayer();
        boolean creative = mode == GameType.CREATIVE;
        boolean spectator = mode == GameType.SPECTATOR;
        ab.mayfly = creative || spectator;
        if (spectator) {
            ab.flying = true;
        } else if (!creative) {
            ab.flying = false;
        }
        player.onUpdateAbilities();
    }
}
