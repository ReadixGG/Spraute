package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S2C: which scenes are currently loaded on the server. */
public final class SyncActiveScenesPacket {
    private final List<String> sceneIds;

    public SyncActiveScenesPacket(List<String> sceneIds) {
        this.sceneIds = sceneIds == null ? List.of() : List.copyOf(sceneIds);
    }

    public static void encode(SyncActiveScenesPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.sceneIds.size());
        for (String id : msg.sceneIds) {
            buf.writeUtf(id == null ? "" : id, 256);
        }
    }

    public static SyncActiveScenesPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> ids = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            ids.add(buf.readUtf(256));
        }
        return new SyncActiveScenesPacket(ids);
    }

    public static void handle(SyncActiveScenesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                org.zonarstudio.spraute_engine.client.ClientSceneRuntime.setActive(msg.sceneIds)));
        ctx.get().setPacketHandled(true);
    }
}
