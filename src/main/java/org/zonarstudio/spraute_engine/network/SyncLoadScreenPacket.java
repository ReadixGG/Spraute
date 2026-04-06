package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.client.LoadScreenOverlay;

import java.util.function.Supplier;

public class SyncLoadScreenPacket {
    public final boolean show;

    public SyncLoadScreenPacket(boolean show) {
        this.show = show;
    }

    public static void encode(SyncLoadScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.show);
    }

    public static SyncLoadScreenPacket decode(FriendlyByteBuf buf) {
        return new SyncLoadScreenPacket(buf.readBoolean());
    }

    public static void handle(SyncLoadScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.show) {
                LoadScreenOverlay.trigger();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
