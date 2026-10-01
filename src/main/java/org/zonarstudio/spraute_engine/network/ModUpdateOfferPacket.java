package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;

import java.util.function.Supplier;

/** S2C: server jar differs; client may request the file. */
public final class ModUpdateOfferPacket {
    public final String modVersion;
    public final String mcVersion;
    public final String fileName;
    public final long size;
    public final byte[] sha256;
    public final boolean requireRestart;

    public ModUpdateOfferPacket(String modVersion, String mcVersion, String fileName, long size,
                                byte[] sha256, boolean requireRestart) {
        this.modVersion = modVersion == null ? "" : modVersion;
        this.mcVersion = mcVersion == null ? "" : mcVersion;
        this.fileName = fileName == null ? "" : fileName;
        this.size = size;
        this.sha256 = sha256 == null ? new byte[AssetSyncRules.HASH_BYTES] : sha256;
        this.requireRestart = requireRestart;
    }

    public static void encode(ModUpdateOfferPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.modVersion, 32);
        buf.writeUtf(msg.mcVersion, 16);
        buf.writeUtf(msg.fileName, 120);
        buf.writeLong(msg.size);
        byte[] hash = msg.sha256;
        if (hash.length != AssetSyncRules.HASH_BYTES) {
            byte[] padded = new byte[AssetSyncRules.HASH_BYTES];
            System.arraycopy(hash, 0, padded, 0, Math.min(hash.length, padded.length));
            hash = padded;
        }
        buf.writeBytes(hash);
        buf.writeBoolean(msg.requireRestart);
    }

    public static ModUpdateOfferPacket decode(FriendlyByteBuf buf) {
        String ver = buf.readUtf(32);
        String mc = buf.readUtf(16);
        String name = buf.readUtf(120);
        long size = buf.readLong();
        byte[] hash = new byte[AssetSyncRules.HASH_BYTES];
        buf.readBytes(hash);
        boolean requireRestart = buf.isReadable() && buf.readBoolean();
        return new ModUpdateOfferPacket(ver, mc, name, size, hash, requireRestart);
    }

    public static void handle(ModUpdateOfferPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                org.zonarstudio.spraute_engine.client.update.ModUpdateClient.onOffer(msg)));
        ctx.get().setPacketHandled(true);
    }
}
