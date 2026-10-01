package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.SyncAudioSourcePacket;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side AUDIO_SOURCE: keeps clients in sync with looping positional sources.
 * Under a PLAYER it is personal; in the world it is only sent to listeners in range.
 */
public final class SceneAudioRuntime {
    private final String nodeId;
    private int syncCooldown;
    private String lastFingerprint = "";
    /** Incremented by {@link #requestPlay} so clients force-restart the instance. */
    private int playGeneration;
    private final Set<UUID> syncedPlayers = new HashSet<>();

    SceneAudioRuntime(SceneNode node) {
        this.nodeId = node == null ? "" : node.id;
    }

    String nodeId() {
        return nodeId;
    }

    /** Script {@code node.play()} / {@code startPlay()} — restart from the beginning. */
    public void requestPlay(SceneActiveState state, ServerLevel level, SceneNode node) {
        if (node != null) {
            node.audioEnabled = true;
            node.active = true;
        }
        playGeneration++;
        syncNow(state, level, node);
    }

    void tick(SceneActiveState state, ServerLevel level, SceneNode node) {
        if (state == null || level == null || node == null) return;
        if (syncCooldown > 0) {
            syncCooldown--;
            return;
        }
        syncCooldown = 10; // ~0.5s
        boolean on = node.active && node.audioEnabled
                && node.audioSound != null && !node.audioSound.isBlank();
        if (!on) {
            lastFingerprint = fingerprint(state, node);
            stopSynced(level);
            return;
        }
        String fp = fingerprint(state, node);
        boolean changed = !fp.equals(lastFingerprint);
        lastFingerprint = fp;
        ServerPlayer owner = resolveOwner(state, node);
        if (owner != null) {
            UUID id = owner.getUUID();
            for (UUID prev : new HashSet<>(syncedPlayers)) {
                if (prev.equals(id)) continue;
                ServerPlayer other = level.getServer().getPlayerList().getPlayer(prev);
                if (other != null) sendStop(other);
            }
            if (changed || !syncedPlayers.contains(id)) {
                sendUpsert(state, owner, node);
            }
            syncedPlayers.clear();
            syncedPlayers.add(id);
            return;
        }
        SceneTransform world = state.document.worldTransform(node.id);
        Set<UUID> next = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (!canHear(node, world, player)) continue;
            UUID id = player.getUUID();
            next.add(id);
            if (changed || !syncedPlayers.contains(id)) {
                sendUpsert(state, player, node);
            }
        }
        for (UUID prev : syncedPlayers) {
            if (next.contains(prev)) continue;
            ServerPlayer other = level.getServer().getPlayerList().getPlayer(prev);
            if (other != null) sendStop(other);
        }
        syncedPlayers.clear();
        syncedPlayers.addAll(next);
    }

    void syncNow(SceneActiveState state, ServerLevel level, SceneNode node) {
        syncCooldown = 0;
        lastFingerprint = "";
        tick(state, level, node);
    }

    void clear(ServerLevel level) {
        lastFingerprint = "";
        stopSynced(level);
    }

    private void sendUpsert(SceneActiveState state, ServerPlayer player, SceneNode node) {
        SceneTransform world = state.document.worldTransform(node.id);
        boolean personal = resolveOwner(state, node) != null;
        SyncAudioSourcePacket pkt = SyncAudioSourcePacket.upsert(
                state.sceneId, node.id,
                node.audioSound, SceneAudioAttenuation.normalizeShape(node.audioShape),
                node.audioFullRadius, node.audioMaxDistance,
                node.audioVolume, node.audioPitch, node.audioLoop,
                node.audioReplayOnEnter, playGeneration,
                (float) world.px, (float) world.py, (float) world.pz,
                (float) world.sx, (float) world.sy, (float) world.sz,
                personal);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }

    private void sendStop(ServerPlayer player) {
        if (player == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                SyncAudioSourcePacket.stop("", nodeId));
    }

    private void stopSynced(ServerLevel level) {
        if (level == null || syncedPlayers.isEmpty()) return;
        for (UUID id : syncedPlayers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) sendStop(player);
        }
        syncedPlayers.clear();
    }

    /** AUDIO_SOURCE under a PLAYER is personal; world sources stay positional. */
    static ServerPlayer resolveOwner(SceneActiveState state, SceneNode node) {
        if (state == null || state.document == null || node == null) return null;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        SceneNode cur = node;
        while (cur != null) {
            if (cur.type == SceneNodeType.PLAYER && cur.playerUuid != null && !cur.playerUuid.isBlank()) {
                try {
                    return server.getPlayerList().getPlayer(UUID.fromString(cur.playerUuid));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
            cur = state.document.findParent(cur.id);
        }
        return null;
    }

    private static boolean canHear(SceneNode node, SceneTransform world, ServerPlayer player) {
        if (player == null) return false;
        float t = SceneAudioAttenuation.normalizedDistance(node, world,
                player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
        return t < 1.15f;
    }

    private String fingerprint(SceneActiveState state, SceneNode node) {
        SceneTransform w = state.document.worldTransform(node.id);
        ServerPlayer owner = resolveOwner(state, node);
        return node.active + "|" + node.audioEnabled + "|" + node.audioSound + "|"
                + node.audioShape + "|" + node.audioFullRadius + "|" + node.audioMaxDistance + "|"
                + node.audioVolume + "|" + node.audioPitch + "|" + node.audioLoop + "|"
                + node.audioReplayOnEnter + "|" + playGeneration + "|"
                + (owner == null ? "" : owner.getUUID()) + "|"
                + round(w.px) + "|" + round(w.py) + "|" + round(w.pz) + "|"
                + round(w.sx) + "|" + round(w.sy) + "|" + round(w.sz);
    }

    private static String round(double v) {
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }
}
