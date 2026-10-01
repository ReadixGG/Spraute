package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.update.ModSelfUpdate;

import java.util.function.Supplier;

/** S2C: one slice of the server mod jar. */
public final class ModUpdateChunkPacket {
    public final long fileSize;
    public final int offset;
    public final boolean last;
    public final byte[] data;

    public ModUpdateChunkPacket(long fileSize, int offset, boolean last, byte[] data) {
        this.fileSize = fileSize;
        this.offset = offset;
        this.last = last;
        this.data = data == null ? new byte[0] : data;
    }

    public static void encode(ModUpdateChunkPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.fileSize);
        buf.writeVarInt(msg.offset);
        buf.writeBoolean(msg.last);
        buf.writeByteArray(msg.data);
    }

    public static ModUpdateChunkPacket decode(FriendlyByteBuf buf) {
        long fileSize = buf.readLong();
        int offset = buf.readVarInt();
        boolean last = buf.readBoolean();
        byte[] data = buf.readByteArray(ModSelfUpdate.CHUNK_BYTES);
        return new ModUpdateChunkPacket(fileSize, offset, last, data);
    }

    public static void handle(ModUpdateChunkPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                org.zonarstudio.spraute_engine.client.update.ModUpdateClient.onChunk(msg)));
        ctx.get().setPacketHandled(true);
    }
}
