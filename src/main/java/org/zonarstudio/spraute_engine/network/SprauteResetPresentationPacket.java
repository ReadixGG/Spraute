package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.presentation.SprautePresentationReset;

import java.util.function.Supplier;

/** C2S: player requests closing all Spraute UI and resetting cinematic camera. */
public class SprauteResetPresentationPacket {

    public SprauteResetPresentationPacket() {}

    public static void encode(SprauteResetPresentationPacket msg, FriendlyByteBuf buf) {}

    public static SprauteResetPresentationPacket decode(FriendlyByteBuf buf) {
        return new SprauteResetPresentationPacket();
    }

    public static void handle(SprauteResetPresentationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                SprautePresentationReset.resetForPlayer(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
