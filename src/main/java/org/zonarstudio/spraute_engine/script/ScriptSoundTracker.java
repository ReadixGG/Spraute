package org.zonarstudio.spraute_engine.script;

import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.PlayScriptSoundPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks {@code playSound} calls per script instance so they can be stopped
 * when the script (or its scene) is stopped.
 */
public final class ScriptSoundTracker {
    private static final Map<String, List<Entry>> BY_INSTANCE = new ConcurrentHashMap<>();

    private ScriptSoundTracker() {}

    public static void register(String instanceId, ServerPlayer player, String soundId, boolean loop) {
        if (player == null || soundId == null || soundId.isBlank()) return;
        String key = instanceId == null || instanceId.isBlank() ? "_" : instanceId;
        List<Entry> list = BY_INSTANCE.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(new Entry(player.getUUID(), soundId, loop));
    }

    public static void stopForInstance(MinecraftServer server, String instanceId) {
        if (server == null) return;
        String key = instanceId == null || instanceId.isBlank() ? "_" : instanceId;
        List<Entry> list = BY_INSTANCE.remove(key);
        if (list == null || list.isEmpty()) return;
        List<Entry> snapshot;
        synchronized (list) {
            snapshot = new ArrayList<>(list);
        }
        for (Entry e : snapshot) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.playerId);
            if (player == null) continue;
            stopEntry(player, e);
        }
    }

    public static void stopAll(MinecraftServer server) {
        if (server == null) return;
        for (String key : new ArrayList<>(BY_INSTANCE.keySet())) {
            stopForInstance(server, key);
        }
    }

    private static void stopEntry(ServerPlayer player, Entry e) {
        ResourceLocation rl = e.soundId.contains(":")
                ? new ResourceLocation(e.soundId)
                : new ResourceLocation("minecraft", e.soundId);
        player.connection.send(new ClientboundStopSoundPacket(rl, SoundSource.MASTER));
        if (e.loop) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    PlayScriptSoundPacket.stopOne(e.soundId));
        }
    }

    private record Entry(UUID playerId, String soundId, boolean loop) {}
}
