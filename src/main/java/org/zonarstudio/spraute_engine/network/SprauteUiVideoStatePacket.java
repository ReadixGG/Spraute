package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.ui.UiTracker;

import java.util.function.Supplier;

/** C2S: current UI video playback state for script queries. */
public final class SprauteUiVideoStatePacket {
    private final String widgetId;
    private final float time;
    private final float duration;
    private final boolean playing;
    private final boolean ended;

    public SprauteUiVideoStatePacket(String widgetId, double time, double duration, boolean playing, boolean ended) {
        this.widgetId = widgetId != null ? widgetId : "";
        this.time = (float) Math.max(0.0, time);
        this.duration = (float) Math.max(0.0, duration);
        this.playing = playing;
        this.ended = ended;
    }

    public static void encode(SprauteUiVideoStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.widgetId, 256);
        buf.writeFloat(msg.time);
        buf.writeFloat(msg.duration);
        buf.writeBoolean(msg.playing);
        buf.writeBoolean(msg.ended);
    }

    public static SprauteUiVideoStatePacket decode(FriendlyByteBuf buf) {
        return new SprauteUiVideoStatePacket(
                buf.readUtf(256), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(SprauteUiVideoStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || msg.widgetId.isEmpty()) return;
            UiTracker.setVideo(player.getUUID(), msg.widgetId, msg.time, msg.duration, msg.playing, msg.ended);
        });
        ctx.get().setPacketHandled(true);
    }
}
