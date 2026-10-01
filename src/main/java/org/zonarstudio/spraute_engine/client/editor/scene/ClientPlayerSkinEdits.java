package org.zonarstudio.spraute_engine.client.editor.scene;

import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.PlayerSkinSyncPacket;
import org.zonarstudio.spraute_engine.scene.PlayerSkinState;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;

/** Sends the live PLAYER skin stack from the editor to the server. */
final class ClientPlayerSkinEdits {
    private ClientPlayerSkinEdits() {}

    static void pushPlayer(SceneDocument document, SceneNode node) {
        SceneNode player = playerOf(document, node);
        if (player == null || player.playerUuid == null || player.playerUuid.isBlank()) return;
        ModNetwork.CHANNEL.sendToServer(PlayerSkinSyncPacket.editor(
                player.playerUuid, PlayerSkinState.fromPlayerNode(player)));
    }

    static void pushIfSkinRelated(SceneDocument document, SceneNode node) {
        if (node == null) return;
        if (node.type == SceneNodeType.PLAYER || node.type == SceneNodeType.SKIN_OVERLAY) {
            pushPlayer(document, node);
        }
    }

    private static SceneNode playerOf(SceneDocument document, SceneNode node) {
        if (node == null) return null;
        if (node.type == SceneNodeType.PLAYER) return node;
        if (node.type == SceneNodeType.SKIN_OVERLAY && document != null) {
            SceneNode parent = document.findParent(node.id);
            return parent != null && parent.type == SceneNodeType.PLAYER ? parent : null;
        }
        return null;
    }
}
