package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.scene.PlayerSkinState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S2C (and editor C2S) full player skin + overlay stack. */
public final class PlayerSkinSyncPacket {
    private final String playerUuid;
    private final String baseSkin;
    private final List<PlayerSkinState.Overlay> overlays;
    private final boolean editor;

    public PlayerSkinSyncPacket(String playerUuid, PlayerSkinState.Appearance appearance) {
        this(playerUuid, appearance, false);
    }

    public PlayerSkinSyncPacket(String playerUuid, PlayerSkinState.Appearance appearance, boolean editor) {
        PlayerSkinState.Appearance value = appearance == null ? PlayerSkinState.VANILLA : appearance;
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.baseSkin = value.baseSkin() == null ? "" : value.baseSkin();
        this.overlays = value.overlays();
        this.editor = editor;
    }

    private PlayerSkinSyncPacket(String playerUuid, String baseSkin, List<PlayerSkinState.Overlay> overlays,
                                 boolean editor) {
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.baseSkin = baseSkin == null ? "" : baseSkin;
        this.overlays = overlays == null ? List.of() : overlays;
        this.editor = editor;
    }

    public static PlayerSkinSyncPacket editor(String playerUuid, PlayerSkinState.Appearance appearance) {
        return new PlayerSkinSyncPacket(playerUuid, appearance, true);
    }

    public PlayerSkinState.Appearance appearance() {
        return new PlayerSkinState.Appearance(baseSkin, overlays);
    }

    public static void encode(PlayerSkinSyncPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.playerUuid, 64);
        buffer.writeUtf(message.baseSkin, 512);
        buffer.writeVarInt(message.overlays.size());
        for (PlayerSkinState.Overlay overlay : message.overlays) {
            buffer.writeUtf(overlay.id(), 128);
            buffer.writeUtf(overlay.name(), 128);
            buffer.writeUtf(overlay.texture(), 512);
            buffer.writeBoolean(overlay.active());
            buffer.writeVarInt(overlay.hiddenSkinLayers());
        }
        buffer.writeBoolean(message.editor);
    }

    public static PlayerSkinSyncPacket decode(FriendlyByteBuf buffer) {
        String uuid = buffer.readUtf(64);
        String skin = buffer.readUtf(512);
        int count = buffer.readVarInt();
        List<PlayerSkinState.Overlay> overlays = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            overlays.add(new PlayerSkinState.Overlay(
                    buffer.readUtf(128), buffer.readUtf(128), buffer.readUtf(512),
                    buffer.readBoolean(), buffer.readVarInt()));
        }
        return new PlayerSkinSyncPacket(uuid, skin, overlays, buffer.readBoolean());
    }

    public static void handle(PlayerSkinSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        var sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) {
                if (!sender.hasPermissions(2) || !message.editor) return;
                java.util.UUID uuid = org.zonarstudio.spraute_engine.scene.PlayerSkinOps.parseUuid(message.playerUuid);
                if (uuid == null) uuid = sender.getUUID();
                org.zonarstudio.spraute_engine.scene.PlayerSkinOps.replace(uuid, message.appearance());
            } else {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.PlayerSkinComposer.apply(
                                message.playerUuid, message.appearance()));
            }
        });
        context.setPacketHandled(true);
    }
}
