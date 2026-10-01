package org.zonarstudio.spraute_engine.resource;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.network.AssetChunkPacket;
import org.zonarstudio.spraute_engine.network.AssetManifestPacket;
import org.zonarstudio.spraute_engine.network.AssetSyncFinishedPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side scan, manifest send, and paced file transfer. */
public final class AssetSyncServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object LOCK = new Object();
    private static final long CACHE_MS = 30_000L;
    private static final int CHUNKS_PER_TICK = 4;
    private static final long LIVE_PUSH_DELAY_MS = 500L;

    private static AssetSyncRules.Manifest cached = AssetSyncRules.Manifest.empty();
    private static long cachedAt;
    private static final Map<UUID, Transfer> TRANSFERS = new HashMap<>();
    private static long livePushAfterMs;

    private AssetSyncServer() {}

    public static void invalidate() {
        synchronized (LOCK) {
            cached = AssetSyncRules.Manifest.empty();
            cachedAt = 0L;
        }
    }

    /** After an OP writes sandbox assets, push a new manifest to online clients. */
    public static void requestLivePush() {
        invalidate();
        livePushAfterMs = System.currentTimeMillis() + LIVE_PUSH_DELAY_MS;
    }

    public static void cancel(UUID playerId) {
        TRANSFERS.remove(playerId);
        AssetSyncTracker.clear(playerId);
    }

    public static void sendManifest(ServerPlayer player) {
        if (player == null) return;
        if (isLocalIntegratedPlayer(player)) {
            AssetSyncTracker.markSkipped(player);
            return;
        }
        AssetSyncRules.Manifest manifest = getOrScan(player.getServer());
        if (manifest.entries.isEmpty()) {
            AssetSyncTracker.markSkipped(player);
            return;
        }
        AssetSyncTracker.markPending(player);
        int pageSize = AssetSyncRules.MANIFEST_PAGE_SIZE;
        int pageCount = Math.max(1, (manifest.entries.size() + pageSize - 1) / pageSize);
        for (int page = 0; page < pageCount; page++) {
            int from = page * pageSize;
            int to = Math.min(from + pageSize, manifest.entries.size());
            List<AssetSyncRules.FileEntry> slice = new ArrayList<>(manifest.entries.subList(from, to));
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new AssetManifestPacket(
                            manifest.fingerprint, page, pageCount,
                            manifest.entries.size(), manifest.totalBytes, slice));
        }
        LOGGER.info("[Spraute AssetSync] manifest {} files ({}) -> {}",
                manifest.entries.size(), AssetSyncRules.formatBytes(manifest.totalBytes),
                player.getGameProfile().getName());
    }

    public static void beginTransfer(ServerPlayer player, List<String> requested) {
        if (player == null || isLocalIntegratedPlayer(player)) return;
        if (TRANSFERS.containsKey(player.getUUID())) {
            LOGGER.info("[Spraute AssetSync] ignore duplicate request from {}",
                    player.getGameProfile().getName());
            return;
        }
        AssetSyncRules.Manifest manifest = getOrScan(player.getServer());
        Map<String, AssetSyncRules.FileEntry> byPath = new HashMap<>();
        for (AssetSyncRules.FileEntry e : manifest.entries) {
            byPath.put(e.path, e);
        }
        ArrayDeque<AssetSyncRules.FileEntry> queue = new ArrayDeque<>();
        long total = 0L;
        for (String raw : requested) {
            String path = AssetSyncRules.sanitizeRelative(raw);
            if (path == null) continue;
            AssetSyncRules.FileEntry entry = byPath.get(path);
            if (entry == null) continue;
            if (total + entry.size > AssetSyncRules.maxTotalBytes()) break;
            queue.add(entry);
            total += entry.size;
            if (queue.size() >= AssetSyncRules.maxFiles()) break;
        }
        if (queue.isEmpty()) {
            AssetSyncTracker.markSkipped(player);
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new AssetSyncFinishedPacket(true, 0, ""));
            return;
        }
        AssetSyncTracker.markAllowed(player);
        TRANSFERS.put(player.getUUID(), new Transfer(queue));
        LOGGER.info("[Spraute AssetSync] sending {} files ({}) to {}",
                queue.size(), AssetSyncRules.formatBytes(total), player.getGameProfile().getName());
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (!TRANSFERS.isEmpty()) {
            Iterator<Map.Entry<UUID, Transfer>> it = TRANSFERS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Transfer> e = it.next();
                ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
                if (player == null) {
                    it.remove();
                    continue;
                }
                Transfer transfer = e.getValue();
                boolean still = true;
                for (int i = 0; i < CHUNKS_PER_TICK && still; i++) {
                    still = sendNextChunk(player, transfer);
                }
                if (!still) {
                    ModNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new AssetSyncFinishedPacket(transfer.failed == 0, transfer.filesSent,
                                    transfer.failed == 0 ? "" : "some files skipped"));
                    it.remove();
                    if (livePushAfterMs > 0L) {
                        livePushAfterMs = Math.max(livePushAfterMs, System.currentTimeMillis() + 300L);
                    }
                }
            }
        }
        maybePushLive(server);
    }

    private static void maybePushLive(MinecraftServer server) {
        if (livePushAfterMs <= 0L) return;
        if (System.currentTimeMillis() < livePushAfterMs) return;
        if (!TRANSFERS.isEmpty()) return;
        livePushAfterMs = 0L;
        int pushed = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.connection.isMemoryConnection()) continue;
            if (AssetSyncTracker.isDenied(player.getUUID())) continue;
            sendManifest(player);
            pushed++;
        }
        if (pushed > 0) {
            LOGGER.info("[Spraute AssetSync] live push to {} player(s)", pushed);
        }
    }

    private static boolean sendNextChunk(ServerPlayer player, Transfer transfer) {
        if (transfer.current == null) {
            if (transfer.queue.isEmpty()) return false;
            AssetSyncRules.FileEntry next = transfer.queue.poll();
            Path file = AssetSyncRules.resolveInWorkspace(SprautePaths.workspace(player.getServer()), next.path);
            if (file == null || !Files.isRegularFile(file)) {
                transfer.failed++;
                return !transfer.queue.isEmpty() || transfer.current != null;
            }
            try {
                byte[] data = Files.readAllBytes(file);
                if (data.length > AssetSyncRules.maxFileBytes()
                        || !AssetSyncRules.hashesEqual(AssetSyncRules.sha256(data), next.sha256)) {
                    LOGGER.warn("[Spraute AssetSync] skip changed/oversized {}", next.path);
                    transfer.failed++;
                    return !transfer.queue.isEmpty();
                }
                transfer.current = next;
                transfer.data = data;
                transfer.offset = 0;
            } catch (IOException ex) {
                LOGGER.warn("[Spraute AssetSync] read failed {}: {}", next.path, ex.getMessage());
                transfer.failed++;
                return !transfer.queue.isEmpty();
            }
        }
        int remaining = transfer.data.length - transfer.offset;
        int n = Math.min(AssetSyncRules.CHUNK_BYTES, remaining);
        byte[] slice = new byte[n];
        System.arraycopy(transfer.data, transfer.offset, slice, 0, n);
        int offset = transfer.offset;
        transfer.offset += n;
        boolean last = transfer.offset >= transfer.data.length;
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new AssetChunkPacket(transfer.current.path, transfer.data.length, offset, last, slice));
        if (last) {
            transfer.filesSent++;
            transfer.current = null;
            transfer.data = null;
            transfer.offset = 0;
            return !transfer.queue.isEmpty();
        }
        return true;
    }

    public static boolean isLocalIntegratedPlayer(ServerPlayer player) {
        if (player == null || player.connection == null) return true;
        return player.connection.connection.isMemoryConnection();
    }

    private static AssetSyncRules.Manifest getOrScan(MinecraftServer server) {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (cachedAt > 0L && now - cachedAt < CACHE_MS) {
                return cached;
            }
            cached = AssetSyncRules.scan(SprautePaths.workspace(server));
            cachedAt = now;
            return cached;
        }
    }

    private static final class Transfer {
        final ArrayDeque<AssetSyncRules.FileEntry> queue;
        AssetSyncRules.FileEntry current;
        byte[] data;
        int offset;
        int filesSent;
        int failed;

        Transfer(ArrayDeque<AssetSyncRules.FileEntry> queue) {
            this.queue = queue;
        }
    }
}
