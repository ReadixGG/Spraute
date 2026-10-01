package org.zonarstudio.spraute_engine.client;

import net.minecraft.world.entity.player.PlayerModelPart;
import org.zonarstudio.spraute_engine.scene.PlayerSkinLayers;
import org.zonarstudio.spraute_engine.scene.PlayerSkinOps;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client copy of {@link PlayerSkinLayers}: hide hat/jacket/sleeves/pants/cape when rendering. */
public final class PlayerSkinLayersClient {
    private static final Map<UUID, Integer> HIDDEN = new ConcurrentHashMap<>();

    private PlayerSkinLayersClient() {}

    public static void apply(String playerUuid, int mask) {
        UUID uuid = PlayerSkinOps.parseUuid(playerUuid);
        if (uuid == null) return;
        int next = mask & PlayerSkinLayers.ALL;
        if (next == 0) HIDDEN.remove(uuid);
        else HIDDEN.put(uuid, next);
    }

    public static java.util.Set<UUID> hiddenPlayers() {
        return java.util.Set.copyOf(HIDDEN.keySet());
    }

    public static int mask(UUID uuid) {
        if (uuid == null) return 0;
        return HIDDEN.getOrDefault(uuid, 0);
    }

    public static boolean hidden(UUID uuid, PlayerModelPart part) {
        if (uuid == null || part == null) return false;
        return (mask(uuid) & part.getMask()) != 0;
    }

    public static boolean hidden(UUID uuid, int partMask) {
        if (uuid == null || partMask == 0) return false;
        return (mask(uuid) & partMask) == partMask;
    }

    public static void clear() {
        HIDDEN.clear();
    }
}
