package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.update.ModUpdateServer;

import java.util.function.Supplier;

/** C2S: send the server jar. */
public final class ModUpdateRequestPacket {
    public ModUpdateRequestPacket() {}

    public static void encode(ModUpdateRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(true);
    }

    public static ModUpdateRequestPacket decode(FriendlyByteBuf buf) {
        buf.readBoolean();
        return new ModUpdateRequestPacket();
    }

    public static void handle(ModUpdateRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ModUpdateServer.beginTransfer(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
