package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.scene.PlayerAudioNodes;
import org.zonarstudio.spraute_engine.scene.PlayerAudioOps;
import org.zonarstudio.spraute_engine.scene.SceneActiveState;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;

import java.util.function.Supplier;

/**
 * Personal AUDIO bus master. S2C applies on the owning client; C2S from the editor
 * updates the live player AUDIO node and re-syncs.
 */
public final class PlayerAudioSettingsPacket {
    public final String playerUuid;
    public final float volume;
    public final float pitch;
    public final boolean enabled;
    public final boolean fromEditor;

    public PlayerAudioSettingsPacket(String playerUuid, float volume, float pitch, boolean enabled,
                                     boolean fromEditor) {
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.volume = volume;
        this.pitch = pitch;
        this.enabled = enabled;
        this.fromEditor = fromEditor;
    }

    /** S2C: apply bus settings on the client. */
    public static PlayerAudioSettingsPacket sync(float volume, float pitch, boolean enabled) {
        return new PlayerAudioSettingsPacket("", volume, pitch, enabled, false);
    }

    /** C2S: inspector / authoring push for one player's AUDIO node. */
    public static PlayerAudioSettingsPacket editor(String playerUuid, float volume, float pitch, boolean enabled) {
        return new PlayerAudioSettingsPacket(playerUuid, volume, pitch, enabled, true);
    }

    public static void encode(PlayerAudioSettingsPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.playerUuid);
        buf.writeFloat(msg.volume);
        buf.writeFloat(msg.pitch);
        buf.writeBoolean(msg.enabled);
        buf.writeBoolean(msg.fromEditor);
    }

    public static PlayerAudioSettingsPacket decode(FriendlyByteBuf buf) {
        return new PlayerAudioSettingsPacket(
                buf.readUtf(64), buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(PlayerAudioSettingsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isServer()) {
                if (!msg.fromEditor) return;
                ServerPlayer sender = context.getSender();
                if (sender == null) return;
                applyEditor(sender, msg);
                return;
            }
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    org.zonarstudio.spraute_engine.client.audio.ClientScriptSoundManager.applySettings(msg));
        });
        context.setPacketHandled(true);
    }

    private static void applyEditor(ServerPlayer sender, PlayerAudioSettingsPacket msg) {
        String uuid = msg.playerUuid == null || msg.playerUuid.isBlank()
                ? sender.getUUID().toString() : msg.playerUuid;
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null || state.document == null) continue;
            SceneNode players = state.document.ensurePlayersNode();
            for (SceneNode child : players.children) {
                if (child.type != SceneNodeType.PLAYER || !uuid.equals(child.playerUuid)) continue;
                SceneNode audio = PlayerAudioNodes.ensureAudio(child, state.document);
                if (audio == null) return;
                audio.audioVolume = Math.max(0f, msg.volume);
                audio.audioPitch = Math.max(0.01f, msg.pitch);
                audio.audioEnabled = msg.enabled;
                ServerPlayer target = null;
                try {
                    target = sender.getServer().getPlayerList()
                            .getPlayer(java.util.UUID.fromString(uuid));
                } catch (IllegalArgumentException ignored) {
                }
                if (target != null) PlayerAudioOps.syncTo(target, audio);
                return;
            }
        }
    }
}
