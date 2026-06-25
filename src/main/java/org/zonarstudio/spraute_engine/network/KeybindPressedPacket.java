package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.script.PlayerMotionContext;

import java.util.function.Supplier;

/**
 * Sent from client to server when a script-registered keybind is pressed.
 * Optionally carries client horizontal velocity (for air jump without sprint reset).
 */
public class KeybindPressedPacket {

    private final String key;
    private final boolean hasMotion;
    private final double vx;
    private final double vz;

    public KeybindPressedPacket(String key) {
        this.key = key;
        this.hasMotion = false;
        this.vx = 0;
        this.vz = 0;
    }

    public KeybindPressedPacket(String key, double vx, double vz) {
        this.key = key;
        this.hasMotion = true;
        this.vx = vx;
        this.vz = vz;
    }

    public static void encode(KeybindPressedPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.key);
        buf.writeBoolean(msg.hasMotion);
        if (msg.hasMotion) {
            buf.writeDouble(msg.vx);
            buf.writeDouble(msg.vz);
        }
    }

    public static KeybindPressedPacket decode(FriendlyByteBuf buf) {
        String key = buf.readUtf(64);
        if (buf.readBoolean()) {
            return new KeybindPressedPacket(key, buf.readDouble(), buf.readDouble());
        }
        return new KeybindPressedPacket(key);
    }

    public static void handle(KeybindPressedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                try {
                    if (msg.hasMotion) {
                        PlayerMotionContext.setPreservedHorizontal(player.getUUID(), msg.vx, msg.vz);
                    }
                    org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onKeybind(msg.key, player);
                } finally {
                    PlayerMotionContext.clear(player.getUUID());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
