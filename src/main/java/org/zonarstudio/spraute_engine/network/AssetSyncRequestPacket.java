package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;
import org.zonarstudio.spraute_engine.resource.AssetSyncServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** C2S: player accepted the prompt and wants these sandbox paths. */
public final class AssetSyncRequestPacket {
    public final List<String> paths;

    public AssetSyncRequestPacket(List<String> paths) {
        this.paths = paths == null ? List.of() : List.copyOf(paths);
    }

    public static void encode(AssetSyncRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.paths.size());
        for (String path : msg.paths) {
            buf.writeUtf(path == null ? "" : path, AssetSyncRules.MAX_PATH_LEN);
        }
    }

    public static AssetSyncRequestPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(Math.max(0, buf.readVarInt()), AssetSyncRules.HARD_MAX_FILES);
        List<String> paths = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String path = AssetSyncRules.sanitizeRelative(buf.readUtf(AssetSyncRules.MAX_PATH_LEN));
            if (path != null) paths.add(path);
        }
        return new AssetSyncRequestPacket(paths);
    }

    public static void handle(AssetSyncRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            AssetSyncServer.beginTransfer(player, msg.paths);
        });
        ctx.get().setPacketHandled(true);
    }
}
