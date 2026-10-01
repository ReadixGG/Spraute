package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S2C: one page of the dedicated-server workspace listing (OP explorer). */
public final class WorkspaceBrowserListPacket {
    public final int pageIndex;
    public final int pageCount;
    public final int total;
    public final List<WorkspaceBrowserRules.Entry> entries;

    public WorkspaceBrowserListPacket(int pageIndex, int pageCount, int total,
                                      List<WorkspaceBrowserRules.Entry> entries) {
        this.pageIndex = pageIndex;
        this.pageCount = pageCount;
        this.total = total;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static void encode(WorkspaceBrowserListPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.pageIndex);
        buf.writeVarInt(msg.pageCount);
        buf.writeVarInt(msg.total);
        buf.writeVarInt(msg.entries.size());
        for (WorkspaceBrowserRules.Entry e : msg.entries) {
            buf.writeUtf(e.path, WorkspaceBrowserRules.MAX_PATH_LEN);
            buf.writeBoolean(e.dir);
            buf.writeLong(e.size);
        }
    }

    public static WorkspaceBrowserListPacket decode(FriendlyByteBuf buf) {
        int pageIndex = buf.readVarInt();
        int pageCount = buf.readVarInt();
        int total = buf.readVarInt();
        int n = Math.min(Math.max(0, buf.readVarInt()), WorkspaceBrowserRules.LIST_PAGE_SIZE);
        List<WorkspaceBrowserRules.Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String path = buf.readUtf(WorkspaceBrowserRules.MAX_PATH_LEN);
            boolean dir = buf.readBoolean();
            long size = buf.readLong();
            String sane = WorkspaceBrowserRules.sanitize(path, dir);
            if (sane == null) continue;
            entries.add(new WorkspaceBrowserRules.Entry(sane, dir, size));
        }
        return new WorkspaceBrowserListPacket(pageIndex, pageCount, total, entries);
    }

    public static void handle(WorkspaceBrowserListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.onListPage(msg)));
        ctx.get().setPacketHandled(true);
    }
}
