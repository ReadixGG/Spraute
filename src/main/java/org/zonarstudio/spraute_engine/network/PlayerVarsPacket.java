package org.zonarstudio.spraute_engine.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.scene.PlayerVarsOps;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Editor: request / edit {@code player.data} and {@code player.savedData}. */
public final class PlayerVarsPacket {
    public static final int REQUEST = 0;
    public static final int SNAPSHOT = 1;
    public static final int SET = 2;
    public static final int REMOVE = 3;

    private final int op;
    private final String playerUuid;
    private final boolean saved;
    private final String key;
    private final String valueJson;
    private final String dataJson;
    private final String savedJson;

    public PlayerVarsPacket(int op, String playerUuid, boolean saved, String key, String valueJson,
                            String dataJson, String savedJson) {
        this.op = op;
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.saved = saved;
        this.key = key == null ? "" : key;
        this.valueJson = valueJson == null ? "null" : valueJson;
        this.dataJson = dataJson == null ? "{}" : dataJson;
        this.savedJson = savedJson == null ? "{}" : savedJson;
    }

    public static PlayerVarsPacket request(String playerUuid) {
        return new PlayerVarsPacket(REQUEST, playerUuid, false, "", "null", "{}", "{}");
    }

    public static PlayerVarsPacket set(String playerUuid, boolean saved, String key, Object value) {
        return new PlayerVarsPacket(SET, playerUuid, saved, key, toJson(value).toString(), "{}", "{}");
    }

    public static PlayerVarsPacket remove(String playerUuid, boolean saved, String key) {
        return new PlayerVarsPacket(REMOVE, playerUuid, saved, key, "null", "{}", "{}");
    }

    public static void encode(PlayerVarsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.op);
        buf.writeUtf(msg.playerUuid, 64);
        buf.writeBoolean(msg.saved);
        buf.writeUtf(msg.key, 256);
        buf.writeUtf(msg.valueJson, 32767);
        buf.writeUtf(msg.dataJson, 32767);
        buf.writeUtf(msg.savedJson, 32767);
    }

    public static PlayerVarsPacket decode(FriendlyByteBuf buf) {
        return new PlayerVarsPacket(buf.readVarInt(), buf.readUtf(64), buf.readBoolean(),
                buf.readUtf(256), buf.readUtf(32767), buf.readUtf(32767), buf.readUtf(32767));
    }

    public static void handle(PlayerVarsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.ClientPlayerVars.apply(
                                msg.playerUuid, parseMap(msg.dataJson), parseMap(msg.savedJson)));
                return;
            }
            ServerPlayer editor = context.getSender();
            if (editor == null || !editor.hasPermissions(2) || editor.getServer() == null) return;
            ServerPlayer target;
            try {
                target = editor.getServer().getPlayerList().getPlayer(UUID.fromString(msg.playerUuid));
            } catch (RuntimeException e) {
                return;
            }
            if (target == null) return;
            if (msg.op == SET && !msg.key.isBlank()) {
                PlayerVarsOps.set(target, msg.saved, msg.key, javaValue(parseJson(msg.valueJson)));
            } else if (msg.op == REMOVE && !msg.key.isBlank()) {
                PlayerVarsOps.remove(target, msg.saved, msg.key);
            }
            replySnapshot(editor, target);
        });
        context.setPacketHandled(true);
    }

    private static void replySnapshot(ServerPlayer editor, ServerPlayer target) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> editor), snapshotOf(target));
    }

    public static PlayerVarsPacket snapshotOf(ServerPlayer player) {
        return new PlayerVarsPacket(SNAPSHOT, player.getUUID().toString(), false, "", "null",
                encodeMap(PlayerVarsOps.snapshotData(player.getUUID())),
                encodeMap(PlayerVarsOps.snapshotSaved(player)));
    }

    private static JsonElement parseJson(String raw) {
        try {
            return JsonParser.parseString(raw == null || raw.isBlank() ? "null" : raw);
        } catch (RuntimeException e) {
            return new JsonPrimitive(raw == null ? "" : raw);
        }
    }

    private static Object javaValue(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonPrimitive()) {
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) {
                double n = primitive.getAsDouble();
                if (n == Math.rint(n) && n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE) {
                    return (int) n;
                }
                return n;
            }
            return primitive.getAsString();
        }
        return value.toString();
    }

    private static JsonElement toJson(Object value) {
        if (value == null) return com.google.gson.JsonNull.INSTANCE;
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return new JsonPrimitive(n);
        return new JsonPrimitive(String.valueOf(value));
    }

    private static String encodeMap(Map<String, Object> map) {
        JsonObject o = new JsonObject();
        if (map != null) {
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) continue;
                o.add(e.getKey(), toJson(e.getValue()));
            }
        }
        return o.toString();
    }

    private static Map<String, Object> parseMap(String json) {
        Map<String, Object> out = new LinkedHashMap<>();
        JsonElement el = parseJson(json);
        if (!el.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            out.put(e.getKey(), javaValue(e.getValue()));
        }
        return out;
    }
}
