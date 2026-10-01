package org.zonarstudio.spraute_engine.script;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.HudHidePacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side hidden vanilla HUD parts, synced to the client. */
public final class HudHideOverrides {
    private static final Map<UUID, Set<String>> HIDDEN = new ConcurrentHashMap<>();

    private HudHideOverrides() {}

    public static void setHidden(ServerPlayer player, Collection<String> parts, boolean hidden) {
        if (player == null || parts == null || parts.isEmpty()) return;
        Set<String> state = HIDDEN.computeIfAbsent(player.getUUID(), id -> ConcurrentHashMap.newKeySet());
        if (hidden) {
            state.addAll(parts);
        } else {
            state.removeAll(parts);
        }
        if (state.isEmpty()) {
            HIDDEN.remove(player.getUUID());
        }
        sync(player);
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        HIDDEN.remove(player.getUUID());
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), HudHidePacket.clear());
    }

    public static void clear(UUID id) {
        if (id == null) return;
        HIDDEN.remove(id);
    }

    public static boolean isHidden(ServerPlayer player, String part) {
        if (player == null || part == null) return false;
        Set<String> state = HIDDEN.get(player.getUUID());
        if (state == null || state.isEmpty()) return false;
        if (state.contains(HudHideParts.VANILLA)) return true;
        return state.contains(part);
    }

    public static Set<String> snapshot(ServerPlayer player) {
        Set<String> state = player == null ? null : HIDDEN.get(player.getUUID());
        return state == null ? Set.of() : Set.copyOf(state);
    }

    private static void sync(ServerPlayer player) {
        Set<String> state = HIDDEN.getOrDefault(player.getUUID(), Set.of());
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                HudHidePacket.of(new LinkedHashSet<>(state)));
    }
}
