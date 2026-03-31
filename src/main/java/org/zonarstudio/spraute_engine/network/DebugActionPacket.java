package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DebugActionPacket {
    public final String action; // "START", "STOP", "RELOAD"
    public final String scriptName;

    public DebugActionPacket(String action, String scriptName) {
        this.action = action;
        this.scriptName = scriptName != null ? scriptName : "";
    }

    public static void encode(DebugActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action);
        buf.writeUtf(msg.scriptName);
    }

    public static DebugActionPacket decode(FriendlyByteBuf buf) {
        return new DebugActionPacket(buf.readUtf(32767), buf.readUtf(32767));
    }

    public static void handle(DebugActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                org.zonarstudio.spraute_engine.script.ScriptManager manager = org.zonarstudio.spraute_engine.script.ScriptManager.getInstance();
                if (manager != null) {
                    if (msg.action.equals("START")) {
                        manager.run(msg.scriptName, player.createCommandSourceStack());
                    } else if (msg.action.equals("STOP")) {
                        manager.stopScript(msg.scriptName);
                    } else if (msg.action.equals("RELOAD")) {
                        manager.reload();
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}