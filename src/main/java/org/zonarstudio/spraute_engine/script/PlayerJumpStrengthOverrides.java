package org.zonarstudio.spraute_engine.script;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сила прыжка игрока (0.42 = ваниль). На 1.19.2 / 1.20.1 у игрока нет {@code JUMP_STRENGTH}.
 */
public final class PlayerJumpStrengthOverrides {
    public static final float VANILLA = 0.42f;

    @FunctionalInterface
    public interface ClientJump {
        /** {@code null} — ванильный прыжок. */
        Float overrideFor(Player player);
    }

    public static volatile ClientJump clientJump;

    private static final Map<UUID, Float> POWERS = new ConcurrentHashMap<>();

    private PlayerJumpStrengthOverrides() {}

    public static void set(ServerPlayer player, float power) {
        if (player == null) return;
        float clamped = Math.max(0f, Math.min(32f, power));
        POWERS.put(player.getUUID(), clamped);
        PlayerExtrasSync.send(player);
    }

    public static float get(ServerPlayer player) {
        if (player == null) return VANILLA;
        return POWERS.getOrDefault(player.getUUID(), VANILLA);
    }

    /** Для mixin {@code getJumpPower}: {@code null} если не переопределяли. */
    public static Float resolve(Player player) {
        if (player == null) return null;
        if (SprauteEntityCompat.level(player).isClientSide) {
            ClientJump hook = clientJump;
            return hook == null ? null : hook.overrideFor(player);
        }
        return POWERS.get(player.getUUID());
    }

    public static void clear(UUID playerId) {
        if (playerId != null) POWERS.remove(playerId);
    }
}
