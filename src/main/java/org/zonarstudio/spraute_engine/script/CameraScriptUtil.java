package org.zonarstudio.spraute_engine.script;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.network.CameraPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;

import java.util.List;
import java.util.UUID;

/** Shared helpers for script camera functions and the {@code camera} block. */
public final class CameraScriptUtil {
    private CameraScriptUtil() {}

    public record LookAt(byte mode, int entityId, double x, double y, double z) {
        public static LookAt none() {
            return new LookAt(CameraPacket.LOOK_NONE, -1, 0, 0, 0);
        }
    }

    public static ServerPlayer resolvePlayer(Object arg, CommandSourceStack source) {
        if (arg != null) {
            if (arg instanceof ServerPlayer sp) return sp;
            if (arg instanceof net.minecraft.world.entity.player.Player p && source.getServer() != null) {
                ServerPlayer byUuid = source.getServer().getPlayerList().getPlayer(p.getUUID());
                if (byUuid != null) return byUuid;
            }
            if (source.getServer() != null) {
                ServerPlayer byName = source.getServer().getPlayerList().getPlayerByName(String.valueOf(arg));
                if (byName != null) return byName;
            }
        }
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        return null;
    }

    public static Entity resolveEntity(Object arg, CommandSourceStack source) {
        if (arg instanceof Entity e) return e;
        ServerLevel level = source.getLevel();
        if (level == null) return null;
        if (arg instanceof String idOrKeyword) {
            try {
                UUID uuid = UUID.fromString(idOrKeyword);
                Entity byUuid = level.getEntity(uuid);
                if (byUuid != null) return byUuid;
            } catch (IllegalArgumentException ignored) {}
            UUID npcUuid = org.zonarstudio.spraute_engine.entity.NpcManager.get(idOrKeyword);
            if (npcUuid != null) {
                Entity npc = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(idOrKeyword, level);
                if (npc != null) return npc;
            }
            if ("player".equalsIgnoreCase(idOrKeyword)) {
                Entity origin = source.getEntity();
                if (origin != null) return level.getNearestPlayer(origin, 64.0);
                var pos = source.getPosition();
                return level.getNearestPlayer(pos.x, pos.y, pos.z, 64.0, false);
            }
            return level.getServer().getPlayerList().getPlayerByName(idOrKeyword);
        }
        return null;
    }

    public static LookAt parseLookAt(Object arg, boolean track, CommandSourceStack source) {
        if (arg == null) return LookAt.none();
        if (arg instanceof List<?> list && list.size() >= 3
                && list.get(0) instanceof Number
                && list.get(1) instanceof Number
                && list.get(2) instanceof Number) {
            byte mode = track ? CameraPacket.LOOK_TRACK : CameraPacket.LOOK_ONCE;
            return new LookAt(mode, -1,
                    ((Number) list.get(0)).doubleValue(),
                    ((Number) list.get(1)).doubleValue(),
                    ((Number) list.get(2)).doubleValue());
        }
        Entity entity = resolveEntity(arg, source);
        if (entity != null) {
            byte mode = track ? CameraPacket.LOOK_TRACK : CameraPacket.LOOK_ONCE;
            return new LookAt(mode, entity.getId(), entity.getX(), entity.getEyeY(), entity.getZ());
        }
        return LookAt.none();
    }

    public static LookAt parseLookAtCoords(List<Object> args, int xIndex, boolean track) {
        if (args.size() < xIndex + 3) return LookAt.none();
        if (!(args.get(xIndex) instanceof Number)
                || !(args.get(xIndex + 1) instanceof Number)
                || !(args.get(xIndex + 2) instanceof Number)) {
            return LookAt.none();
        }
        byte mode = track ? CameraPacket.LOOK_TRACK : CameraPacket.LOOK_ONCE;
        return new LookAt(mode, -1,
                ((Number) args.get(xIndex)).doubleValue(),
                ((Number) args.get(xIndex + 1)).doubleValue(),
                ((Number) args.get(xIndex + 2)).doubleValue());
    }

    public static String dimensionFor(ServerPlayer player, String override) {
        if (override != null && !override.isBlank()) return override;
        return SprauteEntityCompat.level(player).dimension().location().toString();
    }

    public static void sendStart(ServerPlayer player,
                                 double x, double y, double z,
                                 float yaw, float pitch,
                                 float holdTime, boolean smooth, float smoothTime,
                                 String dimension, LookAt lookAt) {
        sendStart(player, x, y, z, yaw, pitch, holdTime, smooth, smoothTime, dimension, lookAt,
                CameraControls.Settings.defaults());
    }

    public static void sendStart(ServerPlayer player,
                                 double x, double y, double z,
                                 float yaw, float pitch,
                                 float holdTime, boolean smooth, float smoothTime,
                                 String dimension, LookAt lookAt,
                                 CameraControls.Settings controls) {
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                CameraPacket.start(x, y, z, yaw, pitch, holdTime, smooth, smoothTime,
                        dimensionFor(player, dimension),
                        lookAt.mode(), lookAt.entityId(), lookAt.x(), lookAt.y(), lookAt.z(),
                        controls.lockMovement(), controls.hideGui())
        );
    }

    public static void sendMove(ServerPlayer player,
                                double x, double y, double z,
                                float yaw, float pitch, float smoothTime, LookAt lookAt) {
        sendMove(player, x, y, z, yaw, pitch, smoothTime, lookAt, CameraControls.Settings.defaults());
    }

    public static void sendMove(ServerPlayer player,
                                double x, double y, double z,
                                float yaw, float pitch, float smoothTime, LookAt lookAt,
                                CameraControls.Settings controls) {
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                CameraPacket.move(x, y, z, yaw, pitch, smoothTime,
                        lookAt.mode(), lookAt.entityId(), lookAt.x(), lookAt.y(), lookAt.z(),
                        controls.lockMovement(), controls.hideGui())
        );
    }

    public static void sendStop(ServerPlayer player) {
        sendStop(player, 0.5f);
    }

    public static void sendStop(ServerPlayer player, float returnSmoothTime) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), CameraPacket.stop(returnSmoothTime));
    }

    public static void sendReset(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), CameraPacket.reset());
    }

    /** Parse trailing optional hold / smooth / track from the end of arg list. */
    public static float optionalFloat(List<Object> args, int index, float defaultVal) {
        if (args.size() <= index || !(args.get(index) instanceof Number n)) return defaultVal;
        return n.floatValue();
    }

    public static boolean optionalBool(List<Object> args, int index, boolean defaultVal) {
        if (args.size() <= index) return defaultVal;
        Object v = args.get(index);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
