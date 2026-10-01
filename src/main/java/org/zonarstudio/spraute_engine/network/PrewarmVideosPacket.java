package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S2C: videos from auto-start / active scenes to decode before the cutscene plays. */
public final class PrewarmVideosPacket {
    private final List<String> sources;

    public PrewarmVideosPacket(List<String> sources) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public static void encode(PrewarmVideosPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.sources.size());
        for (String source : msg.sources) {
            buf.writeUtf(source == null ? "" : source, 256);
        }
    }

    public static PrewarmVideosPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(64, buf.readVarInt());
        List<String> sources = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sources.add(buf.readUtf(256));
        }
        return new PrewarmVideosPacket(sources);
    }

    public static void handle(PrewarmVideosPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                org.zonarstudio.spraute_engine.client.ui.VideoPrewarm.enqueueAll(msg.sources)));
        ctx.get().setPacketHandled(true);
    }
}
