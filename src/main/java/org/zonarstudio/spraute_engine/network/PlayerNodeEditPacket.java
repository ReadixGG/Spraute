package org.zonarstudio.spraute_engine.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.script.ScriptPropertyAccess;

import java.util.UUID;
import java.util.function.Supplier;

/** Applies an inspector edit to the authoritative ServerPlayer. */
public final class PlayerNodeEditPacket {
    private final String playerUuid;
    private final String property;
    private final String valueJson;

    public PlayerNodeEditPacket(String playerUuid, String property, String valueJson) {
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.property = property == null ? "" : property;
        this.valueJson = valueJson == null ? "null" : valueJson;
    }

    public static void encode(PlayerNodeEditPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.playerUuid);
        buffer.writeUtf(message.property);
        buffer.writeUtf(message.valueJson);
    }

    public static PlayerNodeEditPacket decode(FriendlyByteBuf buffer) {
        return new PlayerNodeEditPacket(buffer.readUtf(64), buffer.readUtf(128), buffer.readUtf(32767));
    }

    public static void handle(PlayerNodeEditPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer editor = context.getSender();
            if (editor == null || !editor.hasPermissions(2) || editor.getServer() == null) return;
            try {
                ServerPlayer target = editor.getServer().getPlayerList().getPlayer(UUID.fromString(message.playerUuid));
                if (target == null || message.property.isBlank()) return;
                ScriptPropertyAccess.set(target, message.property, javaValue(JsonParser.parseString(message.valueJson)),
                        editor.createCommandSourceStack());
            } catch (RuntimeException ignored) {
                // Malformed or stale editor packet: leave the player unchanged.
            }
        });
        context.setPacketHandled(true);
    }

    private static Object javaValue(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonPrimitive()) {
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) return primitive.getAsDouble();
            return primitive.getAsString();
        }
        return value.toString();
    }
}
