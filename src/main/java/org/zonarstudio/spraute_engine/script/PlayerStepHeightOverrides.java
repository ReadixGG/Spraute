package org.zonarstudio.spraute_engine.script;



import net.minecraft.server.level.ServerPlayer;

import org.zonarstudio.spraute_engine.compat.SprauteStepHeightCompat;



import java.util.Map;

import java.util.UUID;

import java.util.concurrent.ConcurrentHashMap;



/** Высота шага в блоках (0.6 = ванильная). */

public final class PlayerStepHeightOverrides {

    private static final Map<UUID, Float> HEIGHTS = new ConcurrentHashMap<>();



    private PlayerStepHeightOverrides() {}



    public static void set(ServerPlayer player, float blocks) {

        if (player == null) return;

        HEIGHTS.put(player.getUUID(), blocks);

        SprauteStepHeightCompat.set(player, blocks);

    }



    public static float get(ServerPlayer player) {

        if (player == null) return 0.6f;

        return HEIGHTS.getOrDefault(player.getUUID(), 0.6f);

    }



    public static void apply(ServerPlayer player) {

        if (player == null) return;

        Float h = HEIGHTS.get(player.getUUID());

        if (h != null) {

            SprauteStepHeightCompat.applyTick(player, h);

        }

    }



    public static void clear(UUID playerId) {

        if (playerId != null) HEIGHTS.remove(playerId);

    }

}

