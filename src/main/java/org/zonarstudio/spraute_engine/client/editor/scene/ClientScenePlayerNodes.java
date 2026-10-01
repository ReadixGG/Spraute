package org.zonarstudio.spraute_engine.client.editor.scene;

import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Client-side mirror used by the hierarchy and player inspector. */
public final class ClientScenePlayerNodes {
    private ClientScenePlayerNodes() {}

    static void sync(SceneDocument document) {
        if (document == null) return;
        SceneNode players = document.ensurePlayersNode();
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            players.children.removeIf(node -> node.type == SceneNodeType.PLAYER);
            return;
        }

        Map<String, SceneNode> existing = new HashMap<>();
        for (SceneNode node : players.children) {
            if (node.type == SceneNodeType.PLAYER && node.playerUuid != null && !node.playerUuid.isBlank()) {
                existing.put(node.playerUuid, node);
            }
        }
        Set<String> online = new HashSet<>();
        for (var info : connection.getOnlinePlayers()) {
            String uuid = info.getProfile().getId().toString();
            online.add(uuid);
            SceneNode node = existing.get(uuid);
            boolean created = node == null;
            if (created) {
                node = SceneNode.livePlayer(uuid, info.getProfile().getName(), document);
                players.children.add(node);
            } else {
                node.name = info.getProfile().getName();
            }
            org.zonarstudio.spraute_engine.scene.PlayerCameraNodes.ensureCamera(node, players, document);
            org.zonarstudio.spraute_engine.scene.PlayerAudioNodes.ensureAudio(node, document);
            if (created) applySkin(node);
        }
        players.children.removeIf(node -> node.type == SceneNodeType.PLAYER && !online.contains(node.playerUuid));
        players.children.sort(Comparator.comparing(node -> node.name, String.CASE_INSENSITIVE_ORDER));
    }

    /** Apply a skin packet onto the live PLAYER node in the editor document. */
    public static void applySkin(String playerUuid, org.zonarstudio.spraute_engine.scene.PlayerSkinState.Appearance appearance) {
        SceneDocument document = SceneEditorSession.document();
        if (document == null || playerUuid == null || playerUuid.isBlank()) return;
        SceneNode players = document.ensurePlayersNode();
        for (SceneNode child : players.children) {
            if (child.type == SceneNodeType.PLAYER && playerUuid.equals(child.playerUuid)) {
                org.zonarstudio.spraute_engine.scene.PlayerSkinOps.applyToPlayerNode(child, appearance);
                return;
            }
        }
    }

    private static void applySkin(SceneNode player) {
        if (player == null || player.playerUuid == null || player.playerUuid.isBlank()) return;
        try {
            org.zonarstudio.spraute_engine.scene.PlayerSkinOps.applyToPlayerNode(player,
                    org.zonarstudio.spraute_engine.client.PlayerSkinComposer.appearanceOf(
                            java.util.UUID.fromString(player.playerUuid)));
        } catch (IllegalArgumentException ignored) {
        }
    }

    static void pushAuthoring(SceneDocument document, SceneNode camera) {
        pushPlayerCamera(document, camera);
    }

    /**
     * Push one player's CAMERA. Does not copy onto PLAYERS defaults or other players.
     * Live packet only while a scene is running — authoring must not move the view.
     */
    static void pushPlayerCamera(SceneDocument document, SceneNode camera) {
        if (document == null || camera == null || camera.type != SceneNodeType.CAMERA) return;
        SceneNode parent = document.findParent(camera.id);
        String uuid = parent != null && parent.playerUuid != null ? parent.playerUuid : "";
        if (uuid.isBlank()) return;
        org.zonarstudio.spraute_engine.scene.PlayerCameraPose.Rest rest =
                org.zonarstudio.spraute_engine.scene.PlayerCameraPose.Rest.from(camera);
        applyLocalRest(uuid, rest);
        if (!SceneEditorSession.isLiveRuntime()) return;
        org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.sendToServer(
                org.zonarstudio.spraute_engine.network.PlayerCameraRestPacket.editor(uuid, rest));
    }

    /** Sync this player's AUDIO bus only. */
    static void pushPlayerAudio(SceneDocument document, SceneNode audio) {
        if (document == null || audio == null || audio.type != SceneNodeType.AUDIO) return;
        if (!SceneEditorSession.isLiveRuntime()) return;
        SceneNode parent = document.findParent(audio.id);
        String uuid = parent != null && parent.playerUuid != null ? parent.playerUuid : "";
        org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.sendToServer(
                org.zonarstudio.spraute_engine.network.PlayerAudioSettingsPacket.editor(
                        uuid, audio.audioVolume, audio.audioPitch, audio.audioEnabled));
    }

    private static String lastPostFingerprint = "";

    static void pushPostAuthoring(SceneDocument document, SceneNode post) {
        if (document == null || post == null || post.type != SceneNodeType.POST_PROCESS) return;
        org.zonarstudio.spraute_engine.scene.PlayerCameraNodes.onPostAuthoringChanged(document, post);
        String fingerprint = post.post().fingerprint();
        if (fingerprint.equals(lastPostFingerprint)) return;
        lastPostFingerprint = fingerprint;
        if (!SceneEditorSession.isLiveRuntime()) return;
        SceneNode camera = document.findParent(post.id);
        SceneNode player = camera == null ? null : document.findParent(camera.id);
        String uuid = player != null && player.playerUuid != null ? player.playerUuid : "";
        org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.sendToServer(
                org.zonarstudio.spraute_engine.network.PlayerPostProcessPacket.editor(uuid, post.post()));
    }

    static void pushDefaults(SceneDocument document) {
        if (document == null) return;
        SceneNode players = document.ensurePlayersNode();
        org.zonarstudio.spraute_engine.scene.PlayerCameraNodes.applyDefaultsToAll(players);
        if (!SceneEditorSession.isLiveRuntime()) return;
        org.zonarstudio.spraute_engine.scene.PlayerCameraPose.Rest rest =
                org.zonarstudio.spraute_engine.scene.PlayerCameraPose.Rest.fromPlayers(players);
        applyLocalRest("", rest);
        // Blank uuid = PLAYERS defaults (server copies to every live player).
        org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.sendToServer(
                org.zonarstudio.spraute_engine.network.PlayerCameraRestPacket.editor("", rest));
    }

    private static void applyLocalRest(String playerUuid, org.zonarstudio.spraute_engine.scene.PlayerCameraPose.Rest rest) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (playerUuid != null && !playerUuid.isBlank()
                && !org.zonarstudio.spraute_engine.scene.PlayerCameraPose.isOwnedByLocalPlayer(playerUuid)) {
            return;
        }
        org.zonarstudio.spraute_engine.client.CameraHandler.setRestPose(rest);
    }
}
