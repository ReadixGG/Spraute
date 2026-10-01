package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S2C: one page of the server asset manifest. */
public final class AssetManifestPacket {
    public final String fingerprint;
    public final int pageIndex;
    public final int pageCount;
    public final int totalFiles;
    public final long totalBytes;
    public final List<AssetSyncRules.FileEntry> entries;

    public AssetManifestPacket(String fingerprint, int pageIndex, int pageCount,
                               int totalFiles, long totalBytes,
                               List<AssetSyncRules.FileEntry> entries) {
        this.fingerprint = fingerprint == null ? "" : fingerprint;
        this.pageIndex = pageIndex;
        this.pageCount = pageCount;
        this.totalFiles = totalFiles;
        this.totalBytes = totalBytes;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static void encode(AssetManifestPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.fingerprint, 64);
        buf.writeVarInt(msg.pageIndex);
        buf.writeVarInt(msg.pageCount);
        buf.writeVarInt(msg.totalFiles);
        buf.writeLong(msg.totalBytes);
        buf.writeVarInt(msg.entries.size());
        for (AssetSyncRules.FileEntry e : msg.entries) {
            buf.writeUtf(e.path, AssetSyncRules.MAX_PATH_LEN);
            buf.writeLong(e.size);
            byte[] hash = e.sha256;
            if (hash.length != AssetSyncRules.HASH_BYTES) {
                byte[] padded = new byte[AssetSyncRules.HASH_BYTES];
                System.arraycopy(hash, 0, padded, 0, Math.min(hash.length, padded.length));
                hash = padded;
            }
            buf.writeBytes(hash);
        }
    }

    public static AssetManifestPacket decode(FriendlyByteBuf buf) {
        String fingerprint = buf.readUtf(64);
        int pageIndex = buf.readVarInt();
        int pageCount = buf.readVarInt();
        int totalFiles = buf.readVarInt();
        long totalBytes = buf.readLong();
        int n = Math.min(Math.max(0, buf.readVarInt()), AssetSyncRules.MANIFEST_PAGE_SIZE);
        List<AssetSyncRules.FileEntry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String path = buf.readUtf(AssetSyncRules.MAX_PATH_LEN);
            long size = buf.readLong();
            byte[] hash = new byte[AssetSyncRules.HASH_BYTES];
            buf.readBytes(hash);
            String sane = AssetSyncRules.sanitizeRelative(path);
            if (sane == null) continue;
            entries.add(new AssetSyncRules.FileEntry(sane, size, hash));
        }
        return new AssetManifestPacket(fingerprint, pageIndex, pageCount, totalFiles, totalBytes, entries);
    }

    public static void handle(AssetManifestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                org.zonarstudio.spraute_engine.client.assetsync.AssetSyncClient.onManifest(msg)));
        ctx.get().setPacketHandled(true);
    }
}
