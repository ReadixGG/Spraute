package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.resource.AssetSyncTracker;

import java.util.function.Supplier;

/** C2S: player decision / completion of sandbox asset sync. */
public final class AssetSyncStatusPacket {
    public static final byte ALLOW = 1;
    public static final byte DENY = 2;
    public static final byte COMPLETE = 3;
    public static final byte SKIPPED = 4;

    public final byte kind;
    public final int files;

    public AssetSyncStatusPacket(byte kind, int files) {
        this.kind = kind;
        this.files = Math.max(0, files);
    }

    public static AssetSyncStatusPacket allow() {
        return new AssetSyncStatusPacket(ALLOW, 0);
    }

    public static AssetSyncStatusPacket deny() {
        return new AssetSyncStatusPacket(DENY, 0);
    }

    public static AssetSyncStatusPacket complete(int files) {
        return new AssetSyncStatusPacket(COMPLETE, files);
    }

    public static AssetSyncStatusPacket skipped() {
        return new AssetSyncStatusPacket(SKIPPED, 0);
    }

    public static void encode(AssetSyncStatusPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.kind);
        buf.writeVarInt(msg.files);
    }

    public static AssetSyncStatusPacket decode(FriendlyByteBuf buf) {
        return new AssetSyncStatusPacket(buf.readByte(), buf.readVarInt());
    }

    public static void handle(AssetSyncStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            switch (msg.kind) {
                case ALLOW -> AssetSyncTracker.markAllowed(player);
                case DENY -> AssetSyncTracker.markDenied(player);
                case COMPLETE -> AssetSyncTracker.markReady(player, msg.files, true);
                case SKIPPED -> AssetSyncTracker.markSkipped(player);
                default -> {
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
