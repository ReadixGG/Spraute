package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: transfer queue drained. */
public final class AssetSyncFinishedPacket {
    public final boolean success;
    public final int filesSent;
    public final String message;

    public AssetSyncFinishedPacket(boolean success, int filesSent, String message) {
        this.success = success;
        this.filesSent = filesSent;
        this.message = message == null ? "" : message;
    }

    public static void encode(AssetSyncFinishedPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.success);
        buf.writeVarInt(msg.filesSent);
        buf.writeUtf(msg.message, 256);
    }

    public static AssetSyncFinishedPacket decode(FriendlyByteBuf buf) {
        return new AssetSyncFinishedPacket(buf.readBoolean(), buf.readVarInt(), buf.readUtf(256));
    }

    public static void handle(AssetSyncFinishedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                org.zonarstudio.spraute_engine.client.assetsync.AssetSyncClient.onFinished(msg)));
        ctx.get().setPacketHandled(true);
    }
}
