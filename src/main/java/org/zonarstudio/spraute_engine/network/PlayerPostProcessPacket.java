package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.scene.PlayerCameraNodes;
import org.zonarstudio.spraute_engine.scene.PostProcessSettings;

import java.util.function.Supplier;

/** C2S from the editor: camera-bound post for one player (blank uuid = PLAYERS defaults only). */
public final class PlayerPostProcessPacket {
    private final String playerUuid;
    private final PostProcessSettings settings;
    private final boolean editor;

    public PlayerPostProcessPacket(String playerUuid, PostProcessSettings settings, boolean editor) {
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.settings = settings == null ? new PostProcessSettings() : settings;
        this.editor = editor;
    }

    public static PlayerPostProcessPacket editor(String playerUuid, PostProcessSettings settings) {
        return new PlayerPostProcessPacket(playerUuid, settings == null ? null : settings.copy(), true);
    }

    public static void encode(PlayerPostProcessPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.playerUuid);
        message.settings.encode(buffer);
        buffer.writeBoolean(message.editor);
    }

    public static PlayerPostProcessPacket decode(FriendlyByteBuf buffer) {
        String uuid = buffer.readUtf(64);
        return new PlayerPostProcessPacket(uuid, PostProcessSettings.decode(buffer), buffer.readBoolean());
    }

    public static void handle(PlayerPostProcessPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender == null || !sender.hasPermissions(2) || !message.editor) return;
            String uuid = message.playerUuid == null || message.playerUuid.isBlank()
                    ? sender.getUUID().toString() : message.playerUuid;
            PlayerCameraNodes.applyPostAuthoring(uuid, message.settings);
        });
        context.setPacketHandled(true);
    }
}
