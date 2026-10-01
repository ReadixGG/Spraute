package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.PlayerCameraRestPacket;

import java.util.UUID;

/** Pushes a CAMERA rest pose to the owning client. */
public final class PlayerCameraSync {
    private PlayerCameraSync() {}

    public static void send(ServerPlayer player, SceneNode camera) {
        if (player == null) return;
        PlayerCameraPose.Rest rest = camera == null
                ? PlayerCameraPose.VANILLA
                : PlayerCameraPose.Rest.from(camera);
        send(player, rest);
    }

    public static void send(ServerPlayer player, PlayerCameraPose.Rest rest) {
        if (player == null) return;
        PlayerCameraPose.Rest value = rest == null ? PlayerCameraPose.VANILLA : rest;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                PlayerCameraRestPacket.clientApply(player.getUUID().toString(), value));
    }

    public static void sendFromScene(SceneActiveState state, ServerPlayer player) {
        if (state == null || player == null) return;
        SceneNode players = state.document.ensurePlayersNode();
        for (SceneNode child : players.children) {
            if (child.type != SceneNodeType.PLAYER || !player.getUUID().toString().equals(child.playerUuid)) {
                continue;
            }
            send(player, PlayerCameraNodes.ensureCamera(child, players, state.document));
            return;
        }
    }

    public static void sendAllIn(SceneActiveState state) {
        if (state == null || state.document == null) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        SceneNode players = state.document.ensurePlayersNode();
        for (SceneNode child : players.children) {
            if (child.type != SceneNodeType.PLAYER || child.playerUuid == null || child.playerUuid.isBlank()) {
                continue;
            }
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(child.playerUuid));
                if (player != null) send(player, PlayerCameraNodes.ensureCamera(child, players, state.document));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /**
     * After a scene stops: each player falls back to another loaded scene's CAMERA,
     * or to vanilla if none remain. Camera rest must not stick around with no scene.
     */
    public static void clearAfterUnload(SceneActiveState unloaded, MinecraftServer server) {
        if (unloaded == null || unloaded.document == null || server == null) return;
        SceneNode players = unloaded.document.ensurePlayersNode();
        if (players == null) return;
        for (SceneNode child : players.children) {
            if (child.type != SceneNodeType.PLAYER || child.playerUuid == null || child.playerUuid.isBlank()) {
                continue;
            }
            ServerPlayer player;
            try {
                player = server.getPlayerList().getPlayer(UUID.fromString(child.playerUuid));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (player == null) continue;
            PlayerCameraPose.Rest next = restFromAnyLoaded(player.getUUID().toString());
            send(player, next == null ? PlayerCameraPose.VANILLA : next);
        }
    }

    /** Restores every online player to vanilla when no scenes are left. */
    public static void clearAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, PlayerCameraPose.VANILLA);
        }
    }

    /** Rest pose of a live player's CAMERA, or {@code null} if no scene owns them. */
    public static PlayerCameraPose.Rest restOf(Player player) {
        if (player == null) return null;
        return restFromAnyLoaded(player.getUUID().toString());
    }

    private static PlayerCameraPose.Rest restFromAnyLoaded(String playerUuid) {
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null || state.document == null) continue;
            SceneNode players = state.document.ensurePlayersNode();
            for (SceneNode child : players.children) {
                if (child.type != SceneNodeType.PLAYER || !playerUuid.equals(child.playerUuid)) continue;
                return PlayerCameraPose.Rest.from(PlayerCameraNodes.ensureCamera(child, players, state.document));
            }
        }
        return null;
    }

    public static void applyAuthoring(PlayerCameraPose.Rest rest) {
        if (rest == null) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null || state.document == null) continue;
            SceneNode players = state.document.ensurePlayersNode();
            players.cameraRestX = (float) rest.x();
            players.cameraRestY = (float) rest.y();
            players.cameraRestZ = (float) rest.z();
            players.cameraRestYaw = rest.yaw();
            players.cameraRestPitch = rest.pitch();
            players.cameraFov = rest.fov();
            players.cameraDistance = rest.distance();
            PlayerCameraNodes.applyRig(players, rest.rig());
            PlayerCameraNodes.applyDefaultsToAll(players);
            sendAllIn(state);
        }
    }

    public static void applyPlayer(String playerUuid, PlayerCameraPose.Rest rest) {
        if (playerUuid == null || playerUuid.isBlank() || rest == null) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer target;
        try {
            target = server.getPlayerList().getPlayer(UUID.fromString(playerUuid));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (target == null) return;
        boolean live = false;
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null || state.document == null) continue;
            SceneNode players = state.document.ensurePlayersNode();
            for (SceneNode child : players.children) {
                if (child.type != SceneNodeType.PLAYER || !playerUuid.equals(child.playerUuid)) continue;
                PlayerCameraNodes.applyRest(PlayerCameraNodes.ensureCamera(child, players, state.document), rest);
                live = true;
            }
        }
        if (live) send(target, rest);
    }
}
