package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.client.LoadScreenOverlay;

import java.util.Map;
import java.util.HashMap;
import java.util.function.Supplier;

public class SyncLoadScreenPacket {
    public final boolean isLoginScreen;
    public final boolean show;
    public final Map<String, Object> props;

    public SyncLoadScreenPacket(boolean show) {
        this.isLoginScreen = true;
        this.show = show;
        this.props = new HashMap<>();
    }

    public SyncLoadScreenPacket(Map<String, Object> props) {
        this.isLoginScreen = false;
        this.show = true;
        this.props = props;
    }

    private SyncLoadScreenPacket(boolean isLoginScreen, boolean show, Map<String, Object> props) {
        this.isLoginScreen = isLoginScreen;
        this.show = show;
        this.props = props;
    }

    public static void encode(SyncLoadScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isLoginScreen);
        buf.writeBoolean(msg.show);
        
        buf.writeInt(msg.props.size());
        for (Map.Entry<String, Object> entry : msg.props.entrySet()) {
            buf.writeUtf(entry.getKey());
            Object val = entry.getValue();
            if (val instanceof String s) {
                buf.writeByte(1);
                buf.writeUtf(s);
            } else if (val instanceof Number n) {
                buf.writeByte(2);
                buf.writeDouble(n.doubleValue());
            } else if (val instanceof Boolean b) {
                buf.writeByte(3);
                buf.writeBoolean(b);
            } else {
                buf.writeByte(0);
            }
        }
    }

    public static SyncLoadScreenPacket decode(FriendlyByteBuf buf) {
        boolean isLoginScreen = buf.readBoolean();
        boolean show = buf.readBoolean();
        
        Map<String, Object> props = new HashMap<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf();
            byte type = buf.readByte();
            if (type == 1) {
                props.put(key, buf.readUtf());
            } else if (type == 2) {
                props.put(key, buf.readDouble());
            } else if (type == 3) {
                props.put(key, buf.readBoolean());
            }
        }
        return new SyncLoadScreenPacket(isLoginScreen, show, props);
    }

    public static void handle(SyncLoadScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.isLoginScreen) {
                if (msg.show) {
                    LoadScreenOverlay.triggerLogin();
                }
            } else {
                LoadScreenOverlay.triggerFadeIn(msg.props);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
