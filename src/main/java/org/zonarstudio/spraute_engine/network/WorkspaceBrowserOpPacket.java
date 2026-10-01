package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules;
import org.zonarstudio.spraute_engine.resource.WorkspaceBrowserServer;

import java.util.function.Supplier;

/** C2S (OP): list / read / write / delete / mkdir / rename in the server workspace. */
public final class WorkspaceBrowserOpPacket {
    public final byte op;
    public final String path;
    public final String dest;
    public final long fileSize;
    public final int offset;
    public final boolean last;
    public final byte[] data;

    public WorkspaceBrowserOpPacket(byte op, String path, String dest,
                                    long fileSize, int offset, boolean last, byte[] data) {
        this.op = op;
        this.path = path == null ? "" : path;
        this.dest = dest == null ? "" : dest;
        this.fileSize = fileSize;
        this.offset = offset;
        this.last = last;
        this.data = data == null ? new byte[0] : data;
    }

    public static WorkspaceBrowserOpPacket list() {
        return new WorkspaceBrowserOpPacket(WorkspaceBrowserRules.OP_LIST, "", "", 0L, 0, true, new byte[0]);
    }

    public static WorkspaceBrowserOpPacket read(String path) {
        return new WorkspaceBrowserOpPacket(WorkspaceBrowserRules.OP_READ, path, "", 0L, 0, true, new byte[0]);
    }

    public static WorkspaceBrowserOpPacket delete(String path) {
        return new WorkspaceBrowserOpPacket(WorkspaceBrowserRules.OP_DELETE, path, "", 0L, 0, true, new byte[0]);
    }

    public static WorkspaceBrowserOpPacket mkdir(String path) {
        return new WorkspaceBrowserOpPacket(WorkspaceBrowserRules.OP_MKDIR, path, "", 0L, 0, true, new byte[0]);
    }

    public static WorkspaceBrowserOpPacket rename(String from, String to) {
        return new WorkspaceBrowserOpPacket(WorkspaceBrowserRules.OP_RENAME, from, to, 0L, 0, true, new byte[0]);
    }

    public static WorkspaceBrowserOpPacket writeChunk(String path, long fileSize, int offset,
                                                       boolean last, byte[] data) {
        return new WorkspaceBrowserOpPacket(WorkspaceBrowserRules.OP_WRITE, path, "", fileSize, offset, last, data);
    }

    public static WorkspaceBrowserOpPacket lock(String path) {
        return new WorkspaceBrowserOpPacket(WorkspaceBrowserRules.OP_LOCK, path, "", 0L, 0, true, new byte[0]);
    }

    public static WorkspaceBrowserOpPacket unlock(String path) {
        return new WorkspaceBrowserOpPacket(WorkspaceBrowserRules.OP_UNLOCK, path, "", 0L, 0, true, new byte[0]);
    }

    public static WorkspaceBrowserOpPacket setLimits(int fileMb, int totalMb, int maxFiles) {
        return new WorkspaceBrowserOpPacket(WorkspaceBrowserRules.OP_SET_LIMITS, "",
                Integer.toString(maxFiles), fileMb, totalMb, true, new byte[0]);
    }

    public static void encode(WorkspaceBrowserOpPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.op);
        buf.writeUtf(msg.path, WorkspaceBrowserRules.MAX_PATH_LEN);
        buf.writeUtf(msg.dest, WorkspaceBrowserRules.MAX_PATH_LEN);
        buf.writeLong(msg.fileSize);
        buf.writeVarInt(msg.offset);
        buf.writeBoolean(msg.last);
        buf.writeByteArray(msg.data);
    }

    public static WorkspaceBrowserOpPacket decode(FriendlyByteBuf buf) {
        byte op = buf.readByte();
        String path = buf.readUtf(WorkspaceBrowserRules.MAX_PATH_LEN);
        String dest = buf.readUtf(WorkspaceBrowserRules.MAX_PATH_LEN);
        long fileSize = buf.readLong();
        int offset = buf.readVarInt();
        boolean last = buf.readBoolean();
        byte[] data = buf.readByteArray(WorkspaceBrowserRules.CHUNK_BYTES);
        return new WorkspaceBrowserOpPacket(op, path, dest, fileSize, offset, last, data);
    }

    public static void handle(WorkspaceBrowserOpPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> WorkspaceBrowserServer.handle(msg, context.getSender()));
        context.setPacketHandled(true);
    }
}
