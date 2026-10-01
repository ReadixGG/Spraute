package org.zonarstudio.spraute_engine.script;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side mirror of scripted / route camera activity.
 * <p>
 * Cutscenes move the <em>client</em> camera far from the player body.
 * Network helpers that use {@code PacketDistributor.NEAR} around the player
 * (or only the emitter while the body is elsewhere) drop VFX during cinematics.
 * While a player is tracked here, scene particle bursts are always delivered
 * to them in the same dimension.
 * <p>
 * {@link View#ownerInstanceId()} ties the cinematic to the script instance that
 * started it so scene/script stop can return the camera to the player.
 * {@link View#lockBlocks()} blocks break/place/use of world blocks while the camera is active.
 */
public final class CinematicViewTracker {
    private static final Map<UUID, View> ACTIVE = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();

    private CinematicViewTracker() {}

    public record View(double x, double y, double z, String ownerInstanceId, boolean lockBlocks, long generation) {
        public View(double x, double y, double z, String ownerInstanceId) {
            this(x, y, z, ownerInstanceId, CameraControls.DEFAULT_LOCK_BLOCKS, 0L);
        }

        public View(double x, double y, double z, String ownerInstanceId, boolean lockBlocks) {
            this(x, y, z, ownerInstanceId, lockBlocks, 0L);
        }
    }

    public static long activate(ServerPlayer player, double x, double y, double z) {
        return activate(player, x, y, z, null, CameraControls.DEFAULT_LOCK_BLOCKS);
    }

    public static long activate(ServerPlayer player, double x, double y, double z, String ownerInstanceId) {
        return activate(player, x, y, z, ownerInstanceId, CameraControls.DEFAULT_LOCK_BLOCKS);
    }

    public static long activate(ServerPlayer player, double x, double y, double z,
                                String ownerInstanceId, boolean lockBlocks) {
        if (player == null) return 0L;
        long gen = NEXT_GENERATION.incrementAndGet();
        ACTIVE.put(player.getUUID(), new View(x, y, z, ownerInstanceId, lockBlocks, gen));
        return gen;
    }

    public static void update(ServerPlayer player, double x, double y, double z) {
        update(player, x, y, z, null, null);
    }

    public static void update(ServerPlayer player, double x, double y, double z, String ownerInstanceId) {
        update(player, x, y, z, ownerInstanceId, null);
    }

    public static void update(ServerPlayer player, double x, double y, double z,
                              String ownerInstanceId, Boolean lockBlocks) {
        if (player == null) return;
        ACTIVE.compute(player.getUUID(), (id, prev) -> {
            String owner = prev != null && prev.ownerInstanceId() != null
                    ? prev.ownerInstanceId()
                    : ownerInstanceId;
            boolean blocks = lockBlocks != null
                    ? lockBlocks
                    : (prev != null ? prev.lockBlocks() : CameraControls.DEFAULT_LOCK_BLOCKS);
            long gen = prev != null ? prev.generation() : NEXT_GENERATION.incrementAndGet();
            return new View(x, y, z, owner, blocks, gen);
        });
    }

    /**
     * Clear the cinematic after {@code seconds} only if this generation is still active.
     * Used when the client auto-returns (holdTime / route end) and never sends {@code resetCamera}.
     */
    public static void scheduleClear(ServerPlayer player, float seconds, long generation) {
        if (player == null || seconds <= 0f || generation <= 0L) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UUID id = player.getUUID();
        int ticks = Math.max(1, (int) Math.ceil(seconds * 20.0) + 5);
        server.tell(new TickTask(server.getTickCount() + ticks, () -> clearIfGeneration(id, generation)));
    }

    public static void clearIfGeneration(UUID playerId, long generation) {
        if (playerId == null) return;
        ACTIVE.computeIfPresent(playerId, (id, prev) -> prev.generation() == generation ? null : prev);
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        ACTIVE.remove(player.getUUID());
    }

    public static void clear(UUID playerId) {
        if (playerId == null) return;
        ACTIVE.remove(playerId);
    }

    /** Player ids whose cinematic was started by one of the given script instances. */
    public static List<UUID> playersOwnedBy(Collection<String> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) return List.of();
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, View> e : ACTIVE.entrySet()) {
            String owner = e.getValue().ownerInstanceId();
            if (owner != null && instanceIds.contains(owner)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public static void clearOwnedBy(Collection<String> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) return;
        ACTIVE.entrySet().removeIf(e -> {
            String owner = e.getValue().ownerInstanceId();
            return owner != null && instanceIds.contains(owner);
        });
    }

    public static boolean isActive(UUID playerId) {
        return playerId != null && ACTIVE.containsKey(playerId);
    }

    public static boolean isActive(ServerPlayer player) {
        return player != null && ACTIVE.containsKey(player.getUUID());
    }

    public static View view(UUID playerId) {
        return playerId == null ? null : ACTIVE.get(playerId);
    }

    /** True when a cinematic is active for this player and block break/use is locked. */
    public static boolean locksBlocks(ServerPlayer player) {
        if (player == null) return false;
        View v = ACTIVE.get(player.getUUID());
        return v != null && v.lockBlocks();
    }

    public static boolean locksBlocks(UUID playerId) {
        View v = view(playerId);
        return v != null && v.lockBlocks();
    }
}
