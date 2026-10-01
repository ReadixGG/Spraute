package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.update.ModUpdateServer;

import java.util.function.Supplier;

/** C2S: this client understands mod auto-update and reports its version. */
public final class ModUpdateHelloPacket {
    public final String modVersion;
    public final String mcVersion;

    public ModUpdateHelloPacket(String modVersion, String mcVersion) {
        this.modVersion = modVersion == null ? "" : modVersion;
        this.mcVersion = mcVersion == null ? "" : mcVersion;
    }

    public static void encode(ModUpdateHelloPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.modVersion, 32);
        buf.writeUtf(msg.mcVersion, 16);
    }

    public static ModUpdateHelloPacket decode(FriendlyByteBuf buf) {
        return new ModUpdateHelloPacket(buf.readUtf(32), buf.readUtf(16));
    }

    public static void handle(ModUpdateHelloPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ModUpdateServer.onHello(player, msg.modVersion, msg.mcVersion);
        });
        ctx.get().setPacketHandled(true);
    }
}
