package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Maintains the dynamic PLAYER children of the system PLAYERS node. */
public final class ScenePlayerNodes {
    private ScenePlayerNodes() {}

    public static SceneNode find(SceneActiveState state, String playerUuid) {
        if (state == null || state.document == null || playerUuid == null || playerUuid.isBlank()) {
            return null;
        }
        SceneNode players = state.document.ensurePlayersNode();
        for (SceneNode child : players.children) {
            if (child.type == SceneNodeType.PLAYER && playerUuid.equals(child.playerUuid)) return child;
        }
        return null;
    }

    public static SceneNode find(String playerUuid) {
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneNode node = find(SceneRuntime.getActiveState(sceneId), playerUuid);
            if (node != null) return node;
        }
        return null;
    }

    public static void syncServer(SceneActiveState state, MinecraftServer server) {
        syncServer(state, server, false);
    }

    /**
     * @param forcePush always send camera / post / audio (scene Play, late join).
     *                  Tick sync only pushes when a PLAYER or CAMERA is first created.
     */
    public static void syncServer(SceneActiveState state, MinecraftServer server, boolean forcePush) {
        if (state == null || server == null) return;
        SceneNode players = state.document.ensurePlayersNode();
        Map<String, SceneNode> byUuid = new HashMap<>();
        for (SceneNode child : players.children) {
            if (child.type == SceneNodeType.PLAYER && child.playerUuid != null
                    && !child.playerUuid.isBlank()) {
                byUuid.put(child.playerUuid, child);
            }
        }

        Set<String> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String uuid = player.getUUID().toString();
            online.add(uuid);
            SceneNode node = byUuid.get(uuid);
            boolean created = node == null;
            if (created) {
                node = SceneNode.livePlayer(uuid, player.getGameProfile().getName(), state.document);
                players.children.add(node);
            } else {
                node.name = player.getGameProfile().getName();
                node.dynamicPlayer = true;
            }
            boolean newCamera = PlayerCameraNodes.findCamera(node) == null;
            SceneNode camera = PlayerCameraNodes.ensureCamera(node, players, state.document);
            PlayerCameraNodes.ensurePostProcess(state, camera);
            boolean newAudio = PlayerAudioNodes.findAudio(node) == null;
            SceneNode audio = PlayerAudioNodes.ensureAudio(node, state.document);
            // Overlay children come from PlayerSkinOps.replace/syncNodes — do not rebuild
            // every tick or a just-added SKIN_OVERLAY (empty texture) is wiped as VANILLA.
            if (created) {
                PlayerSkinOps.applyToPlayerNode(node);
                PlayerSkinLayers.applyToPlayerNode(node);
            } else if (node.hiddenSkinLayers != 0 && !PlayerSkinLayers.has(player.getUUID())) {
                PlayerSkinLayers.applyFromPlayerNode(node);
            }
            state.putRuntimeObject(node.id, player);
            if (created) {
                ServerLevel localWorldLevel = SceneRuntime.resolveLevelForSync(server, state.runtimeDimension);
                if (localWorldLevel != null) {
                    LocalWorldInstancing.spawnFor(state, localWorldLevel, player, node);
                }
            }
            if (created || newCamera || forcePush) {
                PlayerCameraSync.send(player, camera);
                SceneNode post = PlayerCameraNodes.findPostProcess(camera);
                ScenePostProcessRuntime runtime = post == null ? null : state.postProcessRuntime(post.id);
                if (runtime != null) {
                    runtime.syncNow(state,
                            org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player),
                            post);
                }
            }
            if (created || newAudio || forcePush) PlayerAudioOps.syncTo(player, audio);
            if (created || forcePush) PlayerVarsOps.applyDefaults(player, players);
        }

        players.children.removeIf(node -> {
            if (node.type != SceneNodeType.PLAYER || online.contains(node.playerUuid)) return false;
            LocalWorldInstancing.despawnFor(state, node);
            state.removeRuntimeObject(node.id);
            return true;
        });
        players.children.sort(Comparator.comparing(node -> node.name, String.CASE_INSENSITIVE_ORDER));
    }

    /** Force camera / post / audio onto one client (login). Creates the PLAYER node if needed. */
    public static void pushPresentation(SceneActiveState state, ServerPlayer player) {
        if (state == null || player == null || player.getServer() == null) return;
        syncServer(state, player.getServer(), false);
        SceneNode players = state.document.ensurePlayersNode();
        String uuid = player.getUUID().toString();
        for (SceneNode child : players.children) {
            if (child.type != SceneNodeType.PLAYER || !uuid.equals(child.playerUuid)) continue;
            SceneNode camera = PlayerCameraNodes.ensureCamera(child, players, state.document);
            PlayerCameraSync.send(player, camera);
            SceneNode post = PlayerCameraNodes.findPostProcess(camera);
            ScenePostProcessRuntime runtime = post == null ? null : state.postProcessRuntime(post.id);
            if (runtime != null) {
                runtime.syncNow(state,
                        org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player),
                        post);
            }
            SceneNode audio = PlayerAudioNodes.findAudio(child);
            if (audio != null) PlayerAudioOps.syncTo(player, audio);
            PlayerVarsOps.applyDefaults(player, players);
            return;
        }
    }
}
