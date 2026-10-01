package org.zonarstudio.spraute_engine.script;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.PlayerControlPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side player move/look/F5-perspective locks, synced to the client. */
public final class PlayerControlOverrides {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    public record State(boolean moveLocked, boolean lookLocked, boolean perspectiveLocked,
                        float yaw, float pitch, int moveDirMask) {
        static State unlocked() {
            return new State(false, false, false, 0f, 0f, 0);
        }

        boolean isEmpty() {
            return !moveLocked && !lookLocked && !perspectiveLocked && moveDirMask == 0;
        }
    }

    private PlayerControlOverrides() {}

    public static State get(ServerPlayer player) {
        return STATES.getOrDefault(player.getUUID(), State.unlocked());
    }

    public static boolean isMoveLocked(ServerPlayer player) {
        return get(player).moveLocked();
    }

    public static boolean isLookLocked(ServerPlayer player) {
        return get(player).lookLocked();
    }

    public static boolean isPerspectiveLocked(ServerPlayer player) {
        return get(player).perspectiveLocked();
    }

    public static int moveDirMask(ServerPlayer player) {
        return get(player).moveDirMask();
    }

    public static boolean isMoveDirLocked(ServerPlayer player, int bits) {
        return bits != 0 && (get(player).moveDirMask() & bits) != 0;
    }

    public static void setMoveLocked(ServerPlayer player, boolean locked) {
        State prev = get(player);
        State next = new State(locked, prev.lookLocked(), prev.perspectiveLocked(),
                prev.yaw(), prev.pitch(), prev.moveDirMask());
        putAndSync(player, next, PlayerControlPacket.lockMove(locked));
    }

    public static void setLookLocked(ServerPlayer player, boolean locked) {
        State prev = get(player);
        float yaw = locked ? player.getYRot() : prev.yaw();
        float pitch = locked ? player.getXRot() : prev.pitch();
        State next = new State(prev.moveLocked(), locked, prev.perspectiveLocked(),
                yaw, pitch, prev.moveDirMask());
        putAndSync(player, next, PlayerControlPacket.lockLook(locked, yaw, pitch));
    }

    public static void setPerspectiveLocked(ServerPlayer player, boolean locked) {
        State prev = get(player);
        State next = new State(prev.moveLocked(), prev.lookLocked(), locked,
                prev.yaw(), prev.pitch(), prev.moveDirMask());
        putAndSync(player, next, PlayerControlPacket.lockPerspective(locked));
    }

    public static void setMoveDirMask(ServerPlayer player, int mask) {
        int nextMask = mask & (PlayerMoveDirs.RELATIVE | PlayerMoveDirs.WORLD);
        State prev = get(player);
        State next = new State(prev.moveLocked(), prev.lookLocked(), prev.perspectiveLocked(),
                prev.yaw(), prev.pitch(), nextMask);
        putAndSync(player, next, PlayerControlPacket.lockMoveDirs(nextMask));
    }

    public static void setMoveDir(ServerPlayer player, int bits, boolean locked) {
        int keep = bits & (PlayerMoveDirs.RELATIVE | PlayerMoveDirs.WORLD);
        if (keep == 0) return;
        setMoveDirMask(player, PlayerMoveDirs.with(moveDirMask(player), keep, locked));
    }

    /** Teleport in the current dimension so the client accepts the new coords. */
    public static void setPos(ServerPlayer player, double x, double y, double z) {
        player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
    }

    /** Set facing (degrees). If look is locked, updates the lock target too. */
    public static void setLook(ServerPlayer player, float yaw, float pitch) {
        pitch = Math.max(-90f, Math.min(90f, pitch));
        State prev = get(player);
        State next = new State(prev.moveLocked(), prev.lookLocked(), prev.perspectiveLocked(),
                yaw, pitch, prev.moveDirMask());
        STATES.put(player.getUUID(), next);
        applyLookServer(player, yaw, pitch);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                PlayerControlPacket.setLook(yaw, pitch));
    }

    public static void clear(UUID id) {
        STATES.remove(id);
    }

    public static void clear(ServerPlayer player) {
        STATES.remove(player.getUUID());
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                PlayerControlPacket.clear());
    }

    private static void putAndSync(ServerPlayer player, State next, PlayerControlPacket pkt) {
        if (next.isEmpty()) {
            STATES.remove(player.getUUID());
        } else {
            STATES.put(player.getUUID(), next);
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }

    private static void applyLookServer(ServerPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yHeadRot = yaw;
        player.yRotO = yaw;
        player.xRotO = pitch;
        // Keep client entity facing in sync without moving the player.
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
    }
}
