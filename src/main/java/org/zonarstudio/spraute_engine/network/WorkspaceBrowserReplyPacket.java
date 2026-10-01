package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules;

import java.util.function.Supplier;

/** S2C: result of a workspace op, or one READ chunk. */
public final class WorkspaceBrowserReplyPacket {
    public final byte op;
    public final boolean ok;
    public final String message;
    public final String path;
    public final long fileSize;
    public final int offset;
    public final boolean last;
    public final byte[] data;

    public WorkspaceBrowserReplyPacket(byte op, boolean ok, String message, String path,
                                       long fileSize, int offset, boolean last, byte[] data) {
        this.op = op;
        this.ok = ok;
        this.message = message == null ? "" : message;
        this.path = path == null ? "" : path;
        this.fileSize = fileSize;
        this.offset = offset;
        this.last = last;
        this.data = data == null ? new byte[0] : data;
    }

    public static WorkspaceBrowserReplyPacket status(byte op, boolean ok, String path, String message) {
        return new WorkspaceBrowserReplyPacket(op, ok, message, path, 0L, 0, true, new byte[0]);
    }

    public static WorkspaceBrowserReplyPacket readChunk(String path, long fileSize, int offset,
                                                       boolean last, byte[] data) {
        return new WorkspaceBrowserReplyPacket(WorkspaceBrowserRules.OP_READ, true, "", path,
                fileSize, offset, last, data);
    }

    public static void encode(WorkspaceBrowserReplyPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.op);
        buf.writeBoolean(msg.ok);
        buf.writeUtf(msg.message, 256);
        buf.writeUtf(msg.path, WorkspaceBrowserRules.MAX_PATH_LEN);
        buf.writeLong(msg.fileSize);
        buf.writeVarInt(msg.offset);
        buf.writeBoolean(msg.last);
        buf.writeByteArray(msg.data);
    }

    public static WorkspaceBrowserReplyPacket decode(FriendlyByteBuf buf) {
        byte op = buf.readByte();
        boolean ok = buf.readBoolean();
        String message = buf.readUtf(256);
        String path = buf.readUtf(WorkspaceBrowserRules.MAX_PATH_LEN);
        long fileSize = buf.readLong();
        int offset = buf.readVarInt();
        boolean last = buf.readBoolean();
        byte[] data = buf.readByteArray(WorkspaceBrowserRules.CHUNK_BYTES);
        return new WorkspaceBrowserReplyPacket(op, ok, message, path, fileSize, offset, last, data);
    }

    public static void handle(WorkspaceBrowserReplyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.onReply(msg)));
        ctx.get().setPacketHandled(true);
    }
}
