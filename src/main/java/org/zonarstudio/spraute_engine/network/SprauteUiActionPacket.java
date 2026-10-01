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
    /** C2S: started dragging a UI_DRAG widget. widgetId = {@code dragId} or {@code dragId|payload}. */
    public static final byte ACTION_DRAG_START = 5;
    /**
     * C2S: released a drag over a drop zone (or empty drop).
     * widgetId = {@code dragId>dropId@x,y|payload} ({@code dropId} empty if miss).
     */
    public static final byte ACTION_DROP = 6;
    /** C2S: drag gesture ended. widgetId = {@code dragId>dropId} (dropId empty if miss). */
    public static final byte ACTION_DRAG_END = 7;

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

    /** {@code dragId} or {@code dragId|payload} for {@link #ACTION_DRAG_START}. */
    public static String encodeDragStart(String dragId, String payload) {
        String id = dragId == null ? "" : dragId;
        String p = payload == null ? "" : payload;
        if (p.isEmpty()) return clipUtf(id);
        return clipUtf(id + "|" + p.replace("|", " "));
    }

    /** {@code dragId>dropId@x,y|payload} for {@link #ACTION_DROP}. */
    public static String encodeDrop(String dragId, String dropId, int x, int y, String payload) {
        String d = sanitizeToken(dragId);
        String t = sanitizeToken(dropId);
        String p = payload == null ? "" : payload.replace("|", " ");
        String s = d + ">" + t + "@" + x + "," + y;
        if (!p.isEmpty()) s += "|" + p;
        return clipUtf(s);
    }

    /** {@code dragId>dropId} for {@link #ACTION_DRAG_END}. */
    public static String encodeDragEnd(String dragId, String dropId) {
        return clipUtf(sanitizeToken(dragId) + ">" + sanitizeToken(dropId));
    }

    public static final class UiDragMsg {
        public final String dragId;
        public final String dropId;
        public final String payload;
        public final int x;
        public final int y;

        public UiDragMsg(String dragId, String dropId, String payload, int x, int y) {
            this.dragId = dragId != null ? dragId : "";
            this.dropId = dropId != null ? dropId : "";
            this.payload = payload != null ? payload : "";
            this.x = x;
            this.y = y;
        }
    }

    public static UiDragMsg parseDragStart(String widgetId) {
        String wid = widgetId != null ? widgetId : "";
        int bar = wid.indexOf('|');
        if (bar < 0) return new UiDragMsg(wid, "", "", 0, 0);
        return new UiDragMsg(wid.substring(0, bar), "", wid.substring(bar + 1), 0, 0);
    }

    public static UiDragMsg parseDrop(String widgetId) {
        String wid = widgetId != null ? widgetId : "";
        int gt = wid.indexOf('>');
        if (gt < 0) return parseDragStart(wid);
        String drag = wid.substring(0, gt);
        String rest = wid.substring(gt + 1);
        String payload = "";
        int bar = rest.lastIndexOf('|');
        if (bar >= 0) {
            payload = rest.substring(bar + 1);
            rest = rest.substring(0, bar);
        }
        int at = rest.lastIndexOf('@');
        String dropId = rest;
        int x = 0, y = 0;
        if (at >= 0) {
            dropId = rest.substring(0, at);
            String xy = rest.substring(at + 1);
            int c = xy.indexOf(',');
            if (c >= 0) {
                try {
                    x = Integer.parseInt(xy.substring(0, c).trim());
                    y = Integer.parseInt(xy.substring(c + 1).trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return new UiDragMsg(drag, dropId, payload, x, y);
    }

    private static String sanitizeToken(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace(">", "").replace("@", "").replace("|", "");
    }

    private static String clipUtf(String s) {
        if (s == null) return "";
        return s.length() <= 256 ? s : s.substring(0, 256);
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
