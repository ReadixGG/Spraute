package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: GUI button click, close, hover enter/leave, input change.
 */
public class SprauteUiActionPacket {
    public static final byte ACTION_CLICK = 0;
    public static final byte ACTION_CLOSE = 1;
    public static final byte ACTION_HOVER_ENTER = 2;
    public static final byte ACTION_HOVER_LEAVE = 3;
    /** C2S: scrollOffset изменился (mouseButton = offset в px). */
    public static final byte ACTION_SCROLL = 4;

    private final byte action;
    private final String widgetId;
    private final int mouseButton;

    /** Legacy: click (button 0) or close. */
    public SprauteUiActionPacket(String widgetId, boolean closed) {
        this(closed ? ACTION_CLOSE : ACTION_CLICK, widgetId, closed ? -1 : 0);
    }

    public SprauteUiActionPacket(byte action, String widgetId, int mouseButton) {
        this.action = action;
        this.widgetId = widgetId != null ? widgetId : "";
        this.mouseButton = mouseButton;
    }

    public byte getAction() { return action; }
    public String getWidgetId() { return widgetId; }
    public int getMouseButton() { return mouseButton; }

    public static String mouseButtonLabel(int button) {
        if (button == 0) return "left";
        if (button == 1) return "right";
        if (button == 2) return "middle";
        if (button < 0) return "";
        return "button" + button;
    }

    public static void encode(SprauteUiActionPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
        buf.writeUtf(msg.widgetId, 256);
        buf.writeVarInt(msg.mouseButton);
    }

    public static SprauteUiActionPacket decode(FriendlyByteBuf buf) {
        return new SprauteUiActionPacket(buf.readByte(), buf.readUtf(256), buf.readVarInt());
    }

    public static void handle(SprauteUiActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                if (msg.action == ACTION_CLOSE) {
                    org.zonarstudio.spraute_engine.ui.UiTracker.markClosed(player.getUUID());
                } else if (msg.action == ACTION_SCROLL) {
                    org.zonarstudio.spraute_engine.ui.UiTracker.setScroll(
                            player.getUUID(), msg.widgetId, msg.mouseButton);
                } else if (msg.action == ACTION_CLICK && msg.widgetId != null && msg.widgetId.contains(":")) {
                    int colon = msg.widgetId.indexOf(':');
                    String inputId = msg.widgetId.substring(0, colon);
                    String text = msg.widgetId.substring(colon + 1);
                    if (!inputId.isEmpty() && !inputId.startsWith("input:")) {
                        org.zonarstudio.spraute_engine.ui.UiTracker.setInput(player.getUUID(), inputId, text);
                    }
                }
                org.zonarstudio.spraute_engine.script.ScriptManager.getInstance()
                        .onUiAction(player, msg.action, msg.widgetId, msg.mouseButton);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
