package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.SyncPostProcessPacket;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side POST_PROCESS under a player CAMERA: syncs only to that player
 * (full-screen for their view, not a world volume).
 */
public final class ScenePostProcessRuntime {
    private final String sceneId;
    private final String nodeId;
    private int syncCooldown;
    private String lastFingerprint = "";
    private final Set<UUID> syncedPlayers = new HashSet<>();

    ScenePostProcessRuntime(String sceneId, SceneNode node) {
        this.sceneId = sceneId == null ? "" : sceneId;
        this.nodeId = node == null ? "" : node.id;
    }

    String nodeId() {
        return nodeId;
    }

    /** POST_PROCESS is active only as a direct child of CAMERA. */
    public static boolean isCameraBound(SceneDocument document, SceneNode node) {
        return cameraParent(document, node) != null;
    }

    public static SceneNode cameraParent(SceneDocument document, SceneNode node) {
        if (document == null || node == null || node.type != SceneNodeType.POST_PROCESS) return null;
        SceneNode parent = document.findParent(node.id);
        return parent != null && parent.type == SceneNodeType.CAMERA ? parent : null;
    }

    /** Owning player of the CAMERA this post is attached to. */
    public static ServerPlayer resolveOwner(SceneActiveState state, SceneNode node) {
        if (state == null || state.document == null || node == null) return null;
        SceneNode camera = cameraParent(state.document, node);
        if (camera == null) return null;
        SceneNode playerNode = state.document.findParent(camera.id);
        if (playerNode != null && playerNode.type == SceneNodeType.PLAYER) {
            Object obj = state.getRuntimeObject(playerNode.id);
            if (obj instanceof ServerPlayer sp) return sp;
        }
        var entity = PlayerCameraPose.findPlayer(state.document, camera);
        return entity instanceof ServerPlayer sp ? sp : null;
    }

    void tick(SceneActiveState state, ServerLevel level, SceneNode node) {
        if (state == null || level == null || node == null) return;
        if (syncCooldown > 0) {
            syncCooldown--;
            return;
        }
        syncCooldown = 10; // ~0.5s

        if (!isCameraBound(state.document, node)) {
            stopSynced(level);
            syncedPlayers.clear();
            lastFingerprint = "unbound";
            return;
        }

        ServerPlayer target = resolveOwner(state, node);
        if (target == null) {
            stopSynced(level);
            syncedPlayers.clear();
            lastFingerprint = "no-owner";
            return;
        }

        String fp = fingerprint(node) + "|" + target.getUUID();
        UUID targetId = target.getUUID();
        if (fp.equals(lastFingerprint) && syncedPlayers.size() == 1 && syncedPlayers.contains(targetId)) {
            return;
        }
        lastFingerprint = fp;

        // Drop previous recipients if the owner changed.
        for (UUID prev : new HashSet<>(syncedPlayers)) {
            if (prev.equals(targetId)) continue;
            ServerPlayer other = level.getServer().getPlayerList().getPlayer(prev);
            if (other != null) sendStop(other);
        }

        boolean on = node.active && node.post().enabled && node.post().hasAnyOverride();
        if (on) {
            sendUpsert(target, node);
        } else {
            sendStop(target);
        }
        syncedPlayers.clear();
        syncedPlayers.add(targetId);
    }

    void syncNow(SceneActiveState state, ServerLevel level, SceneNode node) {
        syncCooldown = 0;
        lastFingerprint = "";
        tick(state, level, node);
    }

    void clear(ServerLevel level) {
        lastFingerprint = "";
        stopSynced(level);
        syncedPlayers.clear();
    }

    static void clearScene(ServerLevel level, String sceneId) {
        if (level == null) return;
        SyncPostProcessPacket pkt = SyncPostProcessPacket.clearScene(sceneId);
        for (ServerPlayer player : level.players()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
        }
    }

    private void sendUpsert(ServerPlayer player, SceneNode node) {
        PostProcessSettings settings = node.post().copy();
        settings.global = true; // per-player camera stack, never a world box
        SceneTransform identity = new SceneTransform();
        SyncPostProcessPacket pkt = SyncPostProcessPacket.upsert(
                sceneId, node.id, settings, identity);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }

    private void sendStop(ServerPlayer player) {
        if (player == null) return;
        SyncPostProcessPacket pkt = SyncPostProcessPacket.stop(sceneId, nodeId);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }

    private void stopSynced(ServerLevel level) {
        if (level == null || syncedPlayers.isEmpty()) return;
        for (UUID id : syncedPlayers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) sendStop(player);
        }
    }

    private String fingerprint(SceneNode node) {
        return node.active + "|" + node.post().fingerprint();
    }
}
