package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from client to server when a script-registered keybind is pressed.
 */
public class KeybindPressedPacket {

    private final String key;

    public KeybindPressedPacket(String key) {
        this.key = key;
    }

    public static void encode(KeybindPressedPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.key);
    }

    public static KeybindPressedPacket decode(FriendlyByteBuf buf) {
        return new KeybindPressedPacket(buf.readUtf(64));
    }

    public static void handle(KeybindPressedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onKeybind(msg.key, player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
