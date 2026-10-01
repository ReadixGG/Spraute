package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Ensures every live PLAYER has exactly one personal AUDIO bus child
 * (non-positional script / ProCode sound).
 */
public final class PlayerAudioNodes {
    private PlayerAudioNodes() {}

    public static SceneNode ensureAudio(SceneNode player) {
        return ensureAudio(player, null);
    }

    public static SceneNode ensureAudio(SceneNode player, SceneDocument document) {
        if (player == null || player.type != SceneNodeType.PLAYER) return null;
        SceneNode audio = findAudio(player);
        if (audio == null) {
            audio = SceneNode.liveAudio(player.playerUuid, player.name, document);
            player.children.add(audio);
        }
        return audio;
    }

    public static void ensureAll(SceneNode playersRoot) {
        if (playersRoot == null || playersRoot.type != SceneNodeType.PLAYERS) return;
        for (SceneNode child : playersRoot.children) {
            if (child.type == SceneNodeType.PLAYER) ensureAudio(child);
        }
    }

    public static SceneNode findAudio(SceneNode player) {
        if (player == null) return null;
        for (SceneNode child : player.children) {
            if (child.type == SceneNodeType.AUDIO) return child;
        }
        return null;
    }

    public static SceneNodeView viewFor(Player player, SceneActiveState preferred) {
        if (player == null) return null;
        String uuid = player.getUUID().toString();
        if (preferred != null) {
            SceneNodeView view = viewIn(preferred, uuid);
            if (view != null) return view;
        }
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneNodeView view = viewIn(SceneRuntime.getActiveState(sceneId), uuid);
            if (view != null) return view;
        }
        return null;
    }

    public static SceneNodeView viewFor(ServerPlayer player) {
        return viewFor(player, null);
    }

    private static SceneNodeView viewIn(SceneActiveState state, String uuid) {
        if (state == null || state.document == null || uuid == null) return null;
        SceneNode players = state.document.ensurePlayersNode();
        for (SceneNode child : players.children) {
            if (child.type != SceneNodeType.PLAYER || !uuid.equals(child.playerUuid)) continue;
            SceneNode audio = ensureAudio(child, state.document);
            return audio == null ? null : state.nodeView(audio);
        }
        return null;
    }
}
