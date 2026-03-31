package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: button click or screen closed (ESC).
 */
public class SprauteUiActionPacket {
    private final String widgetId;
    private final boolean closed;

    public SprauteUiActionPacket(String widgetId, boolean closed) {
        this.widgetId = widgetId != null ? widgetId : "";
        this.closed = closed;
    }

    public static void encode(SprauteUiActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.widgetId, 256);
        buf.writeBoolean(msg.closed);
    }

    public static SprauteUiActionPacket decode(FriendlyByteBuf buf) {
        return new SprauteUiActionPacket(buf.readUtf(256), buf.readBoolean());
    }

    public static void handle(SprauteUiActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                org.zonarstudio.spraute_engine.script.ScriptManager.getInstance()
                        .onUiAction(player, msg.widgetId, msg.closed);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
