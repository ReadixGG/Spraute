package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;

import java.util.function.Supplier;

/** S2C: one chunk of a sandbox asset file. */
public final class AssetChunkPacket {
    public final String path;
    public final long fileSize;
    public final int offset;
    public final boolean last;
    public final byte[] data;

    public AssetChunkPacket(String path, long fileSize, int offset, boolean last, byte[] data) {
        this.path = path == null ? "" : path;
        this.fileSize = fileSize;
        this.offset = offset;
        this.last = last;
        this.data = data == null ? new byte[0] : data;
    }

    public static void encode(AssetChunkPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.path, AssetSyncRules.MAX_PATH_LEN);
        buf.writeLong(msg.fileSize);
        buf.writeVarInt(msg.offset);
        buf.writeBoolean(msg.last);
        buf.writeByteArray(msg.data);
    }

    public static AssetChunkPacket decode(FriendlyByteBuf buf) {
        String path = buf.readUtf(AssetSyncRules.MAX_PATH_LEN);
        long fileSize = buf.readLong();
        int offset = buf.readVarInt();
        boolean last = buf.readBoolean();
        byte[] data = buf.readByteArray(AssetSyncRules.CHUNK_BYTES);
        return new AssetChunkPacket(path, fileSize, offset, last, data);
    }

    public static void handle(AssetChunkPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                org.zonarstudio.spraute_engine.client.assetsync.AssetSyncClient.onChunk(msg)));
        ctx.get().setPacketHandled(true);
    }
}
