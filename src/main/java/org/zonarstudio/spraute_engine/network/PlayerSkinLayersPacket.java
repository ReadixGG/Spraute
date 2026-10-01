package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: which vanilla outer-skin parts are hidden on a player. */
public final class PlayerSkinLayersPacket {
    private final String playerUuid;
    private final int hiddenMask;

    public PlayerSkinLayersPacket(String playerUuid, int hiddenMask) {
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.hiddenMask = hiddenMask;
    }

    public static void encode(PlayerSkinLayersPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.playerUuid, 64);
        buffer.writeVarInt(message.hiddenMask);
    }

    public static PlayerSkinLayersPacket decode(FriendlyByteBuf buffer) {
        return new PlayerSkinLayersPacket(buffer.readUtf(64), buffer.readVarInt());
    }

    public static void handle(PlayerSkinLayersPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            org.zonarstudio.spraute_engine.client.PlayerSkinLayersClient.apply(
                    message.playerUuid, message.hiddenMask);
            org.zonarstudio.spraute_engine.client.PlayerSkinComposer.onLayersChanged(
                    message.playerUuid);
        }));
        context.setPacketHandled(true);
    }
}
