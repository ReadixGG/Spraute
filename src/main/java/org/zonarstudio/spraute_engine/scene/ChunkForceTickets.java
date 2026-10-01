package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference-counted {@link ServerLevel#setChunkForced} so scene tickets and NPC
 * tickets do not unforce each other.
 */
public final class ChunkForceTickets {
    private static final Map<ServerLevel, Map<Long, Integer>> REFS = new ConcurrentHashMap<>();

    private ChunkForceTickets() {}

    public static void acquire(ServerLevel level, ChunkPos pos) {
        if (level == null || pos == null) return;
        Map<Long, Integer> map = REFS.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>());
        int next = map.merge(pos.toLong(), 1, Integer::sum);
        if (next == 1) {
            level.setChunkForced(pos.x, pos.z, true);
        }
    }

    public static void release(ServerLevel level, ChunkPos pos) {
        if (level == null || pos == null) return;
        Map<Long, Integer> map = REFS.get(level);
        if (map == null) return;
        long key = pos.toLong();
        Integer current = map.get(key);
        if (current == null) return;
        if (current <= 1) {
            map.remove(key);
            if (map.isEmpty()) REFS.remove(level);
            level.setChunkForced(pos.x, pos.z, false);
        } else {
            map.put(key, current - 1);
        }
    }

    public static void replace(ServerLevel level, Set<ChunkPos> previous, Set<ChunkPos> next) {
        if (level == null) return;
        Set<ChunkPos> prev = previous == null ? Set.of() : previous;
        Set<ChunkPos> nxt = next == null ? Set.of() : next;
        for (ChunkPos pos : nxt) {
            if (!prev.contains(pos)) acquire(level, pos);
        }
        for (ChunkPos pos : prev) {
            if (!nxt.contains(pos)) release(level, pos);
        }
    }

    public static void releaseAll(ServerLevel level, Collection<ChunkPos> positions) {
        if (level == null || positions == null || positions.isEmpty()) return;
        for (ChunkPos pos : positions) {
            release(level, pos);
        }
    }

    /** Prevent our vanilla forced-chunk entries from being persisted on a clean shutdown. */
    public static void releaseAll() {
        for (Map.Entry<ServerLevel, Map<Long, Integer>> levelEntry : REFS.entrySet()) {
            ServerLevel level = levelEntry.getKey();
            for (long packed : levelEntry.getValue().keySet()) {
                ChunkPos pos = new ChunkPos(packed);
                level.setChunkForced(pos.x, pos.z, false);
            }
        }
        REFS.clear();
    }
}
