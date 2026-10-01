package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.script.ScriptConsole;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.util.function.Supplier;

/** Run arbitrary Spraute source from the editor console (op only). */
public final class ConsoleEvalPacket {
    private static final int MAX_CODE_CHARS = 200_000;
    private final String code;

    public ConsoleEvalPacket(String code) {
        this.code = code == null ? "" : code;
    }

    public static void encode(ConsoleEvalPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.code, MAX_CODE_CHARS);
    }

    public static ConsoleEvalPacket decode(FriendlyByteBuf buffer) {
        return new ConsoleEvalPacket(buffer.readUtf(MAX_CODE_CHARS));
    }

    public static void handle(ConsoleEvalPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) return;
            ScriptManager manager = ScriptManager.getInstance();
            if (manager == null) {
                ScriptConsole.error("console", "ScriptManager не инициализирован");
                return;
            }
            manager.eval(message.code, player.createCommandSourceStack());
        });
        context.setPacketHandled(true);
    }
}
