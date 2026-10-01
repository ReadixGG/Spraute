package org.zonarstudio.spraute_engine.script;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.network.CameraPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;

import java.util.Collection;
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
        CameraControls.Settings c = controls != null ? controls : CameraControls.Settings.defaults();
        long gen = CinematicViewTracker.activate(player, x, y, z, ScriptExecutor.currentInstanceId(), c.lockBlocks());
        CinematicViewTracker.scheduleClear(player, autoReleaseSeconds(holdTime, smooth, smoothTime), gen);
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                CameraPacket.start(x, y, z, yaw, pitch, holdTime, smooth, smoothTime,
                        dimensionFor(player, dimension),
                        lookAt.mode(), lookAt.entityId(), lookAt.x(), lookAt.y(), lookAt.z(),
                        c.lockMovement(), c.hideGui(), c.lockBlocks())
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
        CameraControls.Settings c = controls != null ? controls : CameraControls.Settings.defaults();
        CinematicViewTracker.update(player, x, y, z, ScriptExecutor.currentInstanceId(), c.lockBlocks());
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                CameraPacket.move(x, y, z, yaw, pitch, smoothTime,
                        lookAt.mode(), lookAt.entityId(), lookAt.x(), lookAt.y(), lookAt.z(),
                        c.lockMovement(), c.hideGui(), c.lockBlocks())
        );
    }

    public static void sendStop(ServerPlayer player) {
        sendStop(player, 0.5f);
    }

    public static void sendStop(ServerPlayer player, float returnSmoothTime) {
        CinematicViewTracker.clear(player);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), CameraPacket.stop(returnSmoothTime));
    }

    public static void sendReset(ServerPlayer player) {
        CinematicViewTracker.clear(player);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), CameraPacket.reset());
    }

    /**
     * Point the cinematic camera at a target. {@code smoothTime <= 0} snaps;
     * otherwise yaw/pitch lerp in place. If no cinematic is running, starts one
     * at the player's current eyes.
     */
    public static float sendLook(ServerPlayer player, LookAt lookAt, float smoothTime,
                                 CameraControls.Settings controls) {
        if (player == null || lookAt == null || lookAt.mode() == CameraPacket.LOOK_NONE) return 0f;
        CameraControls.Settings c = controls != null ? controls : CameraControls.Settings.defaults();
        float time = Math.max(0f, smoothTime);
        if (!CinematicViewTracker.isActive(player)) {
            sendStart(player, player.getX(), player.getEyeY(), player.getZ(),
                    player.getYRot(), player.getXRot(),
                    0f, time > 0f, time, null, lookAt, c);
            return time;
        }
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                CameraPacket.look(lookAt.mode(), lookAt.entityId(), lookAt.x(), lookAt.y(), lookAt.z(), time));
        return time;
    }

    /** True when {@code v} is an explicit boolean token, not a duration number. */
    public static boolean isExplicitBool(Object v) {
        if (v instanceof Boolean) return true;
        if (v == null || v instanceof Number) return false;
        String s = String.valueOf(v).trim().toLowerCase();
        return s.equals("true") || s.equals("false") || s.equals("yes") || s.equals("no")
                || s.equals("on") || s.equals("off") || s.equals("да") || s.equals("нет");
    }

    /**
     * Return the camera after {@code delaySec} only if this cinematic generation is still active.
     * {@code returnSmooth <= 0} snaps back instantly.
     */
    public static void scheduleStop(ServerPlayer player, float delaySec, float returnSmooth, long generation) {
        if (player == null || delaySec < 0f) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UUID id = player.getUUID();
        int ticks = Math.max(1, Math.round(Math.max(0f, delaySec) * 20f));
        server.tell(new TickTask(server.getTickCount() + ticks, () -> {
            CinematicViewTracker.View view = CinematicViewTracker.view(id);
            if (view == null || (generation > 0L && view.generation() != generation)) return;
            ServerPlayer live = server.getPlayerList().getPlayer(id);
            if (live == null) return;
            if (returnSmooth > 0f) sendStop(live, returnSmooth);
            else sendReset(live);
        }));
    }

    /**
     * Return cinematic cameras started by the given script instances to the players.
     * Used when a scene/script is force-stopped (including already-finished scripts that left the camera up).
     */
    public static void resetOwnedBy(MinecraftServer server, Collection<String> instanceIds) {
        if (server == null || instanceIds == null || instanceIds.isEmpty()) return;
        for (UUID playerId : CinematicViewTracker.playersOwnedBy(instanceIds)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                sendReset(player);
            } else {
                CinematicViewTracker.clear(playerId);
            }
        }
        CinematicViewTracker.clearOwnedBy(instanceIds);
    }

    /**
     * Seconds until the client auto-returns after {@code setCamera}/{@code create camera}
     * with a positive hold time. {@code 0} means the camera stays until {@code stopCamera}/{@code resetCamera}.
     */
    public static float autoReleaseSeconds(float holdTime, boolean smooth, float smoothTime) {
        if (holdTime <= 0f) return 0f;
        float total = holdTime;
        if (smooth && smoothTime > 0f) {
            total += smoothTime * 2f;
        }
        return total;
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
        String s = String.valueOf(v).trim();
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "off".equalsIgnoreCase(s) || "no".equalsIgnoreCase(s)) {
            return false;
        }
        return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "1".equals(s);
    }
}
