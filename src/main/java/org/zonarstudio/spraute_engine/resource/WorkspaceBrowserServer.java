package org.zonarstudio.spraute_engine.resource;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.WorkspaceBrowserListPacket;
import org.zonarstudio.spraute_engine.network.WorkspaceBrowserOpPacket;
import org.zonarstudio.spraute_engine.network.WorkspaceBrowserReplyPacket;
import org.zonarstudio.spraute_engine.scene.SceneRepository;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** OP-only file ops against the dedicated server {@code spraute_engine/} workspace. */
public final class WorkspaceBrowserServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Incoming> INCOMING = new HashMap<>();
    private static final Map<String, FileLock> LOCKS = new HashMap<>();

    private WorkspaceBrowserServer() {}

    public static void handle(WorkspaceBrowserOpPacket msg, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return;
        try {
            switch (msg.op) {
                case WorkspaceBrowserRules.OP_LIST -> sendList(player);
                case WorkspaceBrowserRules.OP_READ -> handleRead(player, msg.path);
                case WorkspaceBrowserRules.OP_WRITE -> handleWrite(player, msg);
                case WorkspaceBrowserRules.OP_DELETE -> handleDelete(player, msg.path);
                case WorkspaceBrowserRules.OP_MKDIR -> handleMkdir(player, msg.path);
                case WorkspaceBrowserRules.OP_RENAME -> handleRename(player, msg.path, msg.dest);
                case WorkspaceBrowserRules.OP_LOCK -> handleLock(player, msg.path);
                case WorkspaceBrowserRules.OP_UNLOCK -> handleUnlock(player, msg.path);
                case WorkspaceBrowserRules.OP_SET_LIMITS -> handleSetLimits(player, msg);
                default -> reply(player, msg.op, false, msg.path, "unknown op");
            }
        } catch (Exception e) {
            LOGGER.warn("[Spraute Workspace] op {} failed: {}", msg.op, e.toString());
            reply(player, msg.op, false, msg.path, e.getMessage() == null ? "error" : e.getMessage());
        }
    }

    public static void sendList(ServerPlayer player) {
        Path workspace = SprautePaths.workspace(player.getServer());
        List<WorkspaceBrowserRules.Entry> entries = WorkspaceBrowserRules.scan(workspace);
        List<List<WorkspaceBrowserRules.Entry>> pages = paginate(entries);
        int pageCount = Math.max(1, pages.size());
        if (pages.isEmpty()) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new WorkspaceBrowserListPacket(0, 1, 0, List.of()));
        } else {
            for (int page = 0; page < pages.size(); page++) {
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new WorkspaceBrowserListPacket(page, pageCount, entries.size(), pages.get(page)));
            }
        }
        reply(player, WorkspaceBrowserRules.OP_LIST, true, "", "");
        sendLockSnapshot(player);
        sendLimits(player);
    }

    private static List<List<WorkspaceBrowserRules.Entry>> paginate(
            List<WorkspaceBrowserRules.Entry> entries) {
        List<List<WorkspaceBrowserRules.Entry>> pages = new ArrayList<>();
        List<WorkspaceBrowserRules.Entry> cur = new ArrayList<>();
        int bytes = 32;
        for (WorkspaceBrowserRules.Entry e : entries) {
            int add = 16 + Math.min(e.path.length(), WorkspaceBrowserRules.MAX_PATH_LEN) * 3;
            if (!cur.isEmpty() && (cur.size() >= WorkspaceBrowserRules.LIST_PAGE_SIZE
                    || bytes + add > WorkspaceBrowserRules.LIST_PAGE_BYTES)) {
                pages.add(cur);
                cur = new ArrayList<>();
                bytes = 32;
            }
            cur.add(e);
            bytes += add;
        }
        if (!cur.isEmpty()) pages.add(cur);
        return pages;
    }

    private static void handleRead(ServerPlayer player, String rawPath) throws IOException {
        Path file = WorkspaceBrowserRules.resolve(SprautePaths.workspace(player.getServer()), rawPath, false);
        if (file == null || !Files.isRegularFile(file)) {
            reply(player, WorkspaceBrowserRules.OP_READ, false, rawPath, "file not found");
            return;
        }
        long size = Files.size(file);
        if (size < 0 || size > WorkspaceBrowserRules.maxFileBytes()) {
            reply(player, WorkspaceBrowserRules.OP_READ, false, rawPath, "file too large");
            return;
        }
        byte[] all = Files.readAllBytes(file);
        String path = WorkspaceBrowserRules.sanitize(rawPath, false);
        if (all.length == 0) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    WorkspaceBrowserReplyPacket.readChunk(path, 0L, 0, true, new byte[0]));
            return;
        }
        int offset = 0;
        while (offset < all.length) {
            int n = Math.min(WorkspaceBrowserRules.CHUNK_BYTES, all.length - offset);
            byte[] chunk = new byte[n];
            System.arraycopy(all, offset, chunk, 0, n);
            boolean last = offset + n >= all.length;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    WorkspaceBrowserReplyPacket.readChunk(path, all.length, offset, last, chunk));
            offset += n;
        }
    }

    private static void handleWrite(ServerPlayer player, WorkspaceBrowserOpPacket msg) throws IOException {
        String path = WorkspaceBrowserRules.sanitize(msg.path, false);
        if (path == null) {
            reply(player, WorkspaceBrowserRules.OP_WRITE, false, msg.path, "bad path");
            return;
        }
        if (msg.fileSize < 0 || msg.fileSize > WorkspaceBrowserRules.maxFileBytes()) {
            INCOMING.remove(player.getUUID());
            reply(player, WorkspaceBrowserRules.OP_WRITE, false, path, "file too large");
            return;
        }
        Incoming inc = INCOMING.get(player.getUUID());
        if (inc == null || !path.equals(inc.path)) {
            inc = new Incoming(path, (int) msg.fileSize);
            INCOMING.put(player.getUUID(), inc);
        }
        if (msg.offset != inc.received || msg.offset + msg.data.length > inc.buf.length) {
            INCOMING.remove(player.getUUID());
            reply(player, WorkspaceBrowserRules.OP_WRITE, false, path, "chunk mismatch");
            return;
        }
        System.arraycopy(msg.data, 0, inc.buf, msg.offset, msg.data.length);
        inc.received += msg.data.length;
        if (!msg.last) return;
        if (inc.received != inc.buf.length) {
            INCOMING.remove(player.getUUID());
            reply(player, WorkspaceBrowserRules.OP_WRITE, false, path, "incomplete");
            return;
        }
        Path dest = WorkspaceBrowserRules.resolve(SprautePaths.workspace(player.getServer()), path, false);
        if (dest == null) {
            INCOMING.remove(player.getUUID());
            reply(player, WorkspaceBrowserRules.OP_WRITE, false, path, "bad path");
            return;
        }
        String runningScene = SceneRepository.sceneIdFromWorkspacePath(path);
        if (runningScene != null && SceneRuntime.isActive(runningScene)) {
            INCOMING.remove(player.getUUID());
            reply(player, WorkspaceBrowserRules.OP_WRITE, false, path,
                    "scene is running — stop it before saving");
            return;
        }
        Files.createDirectories(dest.getParent());
        Files.write(dest, inc.buf);
        INCOMING.remove(player.getUUID());
        afterMutation(player, path, true);
        reply(player, WorkspaceBrowserRules.OP_WRITE, true, path, "");
        sendListToRemoteOps(player.getServer());
        LOGGER.info("[Spraute Workspace] {} wrote {}", player.getGameProfile().getName(), path);
    }

    private static void handleDelete(ServerPlayer player, String rawPath) throws IOException {
        Path workspace = SprautePaths.workspace(player.getServer());
        String rel = rawPath == null ? "" : rawPath.replace('\\', '/');
        if (WorkspaceBrowserRules.ROOT_DIRS.contains(rel)) {
            reply(player, WorkspaceBrowserRules.OP_DELETE, false, rawPath, "cannot delete root folder");
            return;
        }
        Path file = WorkspaceBrowserRules.resolve(workspace, rawPath, false);
        Path dir = WorkspaceBrowserRules.resolve(workspace, rawPath, true);
        Path target = null;
        boolean directory = false;
        if (file != null && Files.isRegularFile(file)) {
            target = file;
        } else if (dir != null && Files.isDirectory(dir)) {
            target = dir;
            directory = true;
        }
        if (target == null) {
            reply(player, WorkspaceBrowserRules.OP_DELETE, false, rawPath, "not found");
            return;
        }
        if (directory) {
            try (var walk = Files.walk(target)) {
                List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
                for (Path p : all) Files.deleteIfExists(p);
            }
        } else {
            Files.deleteIfExists(target);
        }
        afterMutation(player, rawPath, false);
        dropLock(player, rawPath);
        reply(player, WorkspaceBrowserRules.OP_DELETE, true, rawPath, "");
        sendListToRemoteOps(player.getServer());
    }

    private static void handleMkdir(ServerPlayer player, String rawPath) throws IOException {
        Path dest = WorkspaceBrowserRules.resolve(SprautePaths.workspace(player.getServer()), rawPath, true);
        if (dest == null) {
            reply(player, WorkspaceBrowserRules.OP_MKDIR, false, rawPath, "bad path");
            return;
        }
        Files.createDirectories(dest);
        reply(player, WorkspaceBrowserRules.OP_MKDIR, true, rawPath, "");
        sendListToRemoteOps(player.getServer());
    }

    private static void handleRename(ServerPlayer player, String fromRaw, String toRaw) throws IOException {
        Path workspace = SprautePaths.workspace(player.getServer());
        Path fromFile = WorkspaceBrowserRules.resolve(workspace, fromRaw, false);
        Path fromDir = WorkspaceBrowserRules.resolve(workspace, fromRaw, true);
        Path toFile = WorkspaceBrowserRules.resolve(workspace, toRaw, false);
        Path toDir = WorkspaceBrowserRules.resolve(workspace, toRaw, true);
        Path from = (fromFile != null && Files.exists(fromFile)) ? fromFile
                : (fromDir != null && Files.exists(fromDir) ? fromDir : null);
        if (from == null) {
            reply(player, WorkspaceBrowserRules.OP_RENAME, false, fromRaw, "not found");
            return;
        }
        Path to = Files.isDirectory(from) ? toDir : toFile;
        if (to == null) {
            reply(player, WorkspaceBrowserRules.OP_RENAME, false, fromRaw, "bad path");
            return;
        }
        if (WorkspaceBrowserRules.ROOT_DIRS.contains(fromRaw.replace('\\', '/'))) {
            reply(player, WorkspaceBrowserRules.OP_RENAME, false, fromRaw, "cannot rename root folder");
            return;
        }
        if (Files.exists(to)) {
            reply(player, WorkspaceBrowserRules.OP_RENAME, false, fromRaw, "already exists");
            return;
        }
        Files.createDirectories(to.getParent());
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        afterMutation(player, fromRaw, false);
        afterMutation(player, toRaw, true);
        moveLock(player, fromRaw, toRaw);
        reply(player, WorkspaceBrowserRules.OP_RENAME, true, toRaw, "");
        sendListToRemoteOps(player.getServer());
    }

    public static void releaseLocks(ServerPlayer player) {
        if (player == null) return;
        UUID id = player.getUUID();
        List<String> released = new ArrayList<>();
        Iterator<Map.Entry<String, FileLock>> it = LOCKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FileLock> e = it.next();
            if (id.equals(e.getValue().playerId)) {
                released.add(e.getKey());
                it.remove();
            }
        }
        for (String path : released) {
            broadcastLockState(player, path, "");
        }
    }

    private static void handleLock(ServerPlayer player, String rawPath) {
        String path = WorkspaceBrowserRules.sanitize(rawPath, false);
        if (path == null) {
            reply(player, WorkspaceBrowserRules.OP_LOCK, false, rawPath, "bad path");
            return;
        }
        if (isScenePath(path)) {
            reply(player, WorkspaceBrowserRules.OP_LOCK, true, path, "");
            return;
        }
        FileLock existing = LOCKS.get(path);
        if (existing != null && player.getServer().getPlayerList().getPlayer(existing.playerId) == null) {
            LOCKS.remove(path);
            broadcastLockState(player, path, "");
            existing = null;
        }
        if (existing != null && !existing.playerId.equals(player.getUUID())) {
            reply(player, WorkspaceBrowserRules.OP_LOCK, false, path, existing.playerName);
            return;
        }
        String name = player.getGameProfile().getName();
        LOCKS.put(path, new FileLock(player.getUUID(), name));
        reply(player, WorkspaceBrowserRules.OP_LOCK, true, path, name);
        broadcastLockState(player, path, name);
    }

    private static void handleUnlock(ServerPlayer player, String rawPath) {
        String path = rawPath == null ? "" : rawPath.replace('\\', '/');
        if (path.isBlank()) {
            releaseLocks(player);
            reply(player, WorkspaceBrowserRules.OP_UNLOCK, true, "", "");
            return;
        }
        path = WorkspaceBrowserRules.sanitize(path, false);
        if (path == null) {
            reply(player, WorkspaceBrowserRules.OP_UNLOCK, false, rawPath, "bad path");
            return;
        }
        FileLock existing = LOCKS.get(path);
        if (existing != null && !existing.playerId.equals(player.getUUID())) {
            reply(player, WorkspaceBrowserRules.OP_UNLOCK, false, path, existing.playerName);
            return;
        }
        LOCKS.remove(path);
        reply(player, WorkspaceBrowserRules.OP_UNLOCK, true, path, "");
        broadcastLockState(player, path, "");
    }

    private static void moveLock(ServerPlayer player, String fromRaw, String toRaw) {
        String from = WorkspaceBrowserRules.sanitize(fromRaw, false);
        String to = WorkspaceBrowserRules.sanitize(toRaw, false);
        if (from == null) return;
        FileLock lock = LOCKS.remove(from);
        if (lock == null) return;
        broadcastLockState(player, from, "");
        if (to != null && !isScenePath(to)) {
            LOCKS.put(to, lock);
            broadcastLockState(player, to, lock.playerName);
        }
    }

    private static void dropLock(ServerPlayer player, String rawPath) {
        String path = WorkspaceBrowserRules.sanitize(rawPath, false);
        if (path == null) return;
        if (LOCKS.remove(path) != null) {
            broadcastLockState(player, path, "");
        }
    }

    private static void afterMutation(ServerPlayer player, String path, boolean broadcastContent) {
        String rel = path == null ? "" : path.replace('\\', '/');
        if (isLiveAssetPath(rel)) {
            AssetSyncServer.requestLivePush();
        }
        if (rel.startsWith("geo/") && rel.toLowerCase(Locale.ROOT).endsWith(".geo.json")) {
            org.zonarstudio.spraute_engine.core.model.SpResourceCache.invalidateModel(rel);
            org.zonarstudio.spraute_engine.entity.NpcBonePoseSolver.clear();
        }
        if (rel.startsWith("scripts/") && rel.endsWith(".spr")) {
            ScriptManager scripts = ScriptManager.getInstance();
            Path file = WorkspaceBrowserRules.resolve(SprautePaths.workspace(), rel, false);
            if (scripts != null && file != null) scripts.refreshScriptFile(file);
        }
        if (broadcastContent && (isScenePath(rel) || isLiveAssetPath(rel))) {
            broadcastFileChanged(player, rel);
        }
    }

    public static boolean isLiveAssetPath(String rel) {
        if (rel == null || rel.isBlank()) return false;
        String n = rel.replace('\\', '/');
        return n.startsWith("textures/") || n.startsWith("geo/")
                || n.startsWith("animations/") || n.startsWith("sounds/")
                || n.startsWith("fonts/") || n.startsWith("videos/");
    }

    private static void sendListToRemoteOps(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (!other.hasPermissions(2)) continue;
            if (other.connection.connection.isMemoryConnection()) continue;
            sendList(other);
        }
    }

    /** Integrated/LAN host wrote an asset on disk — same side-effects as a remote OP write. */
    public static void notifyHostMutation(MinecraftServer server, ServerPlayer writer, String path) {
        if (server == null) return;
        if (writer != null) {
            afterMutation(writer, path, true);
            return;
        }
        if (isLiveAssetPath(path)) {
            AssetSyncServer.requestLivePush();
        }
    }

    private static boolean isScenePath(String rel) {
        if (rel == null || rel.isBlank()) return false;
        String n = rel.replace('\\', '/').toLowerCase(Locale.ROOT);
        return n.startsWith("scenes/") && n.endsWith(".scene.json");
    }

    private static void sendLockSnapshot(ServerPlayer player) {
        for (Map.Entry<String, FileLock> e : LOCKS.entrySet()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    WorkspaceBrowserReplyPacket.status(
                            WorkspaceBrowserRules.OP_LOCK_STATE, true, e.getKey(), e.getValue().playerName));
        }
    }

    private static void broadcastLockState(ServerPlayer from, String path, String holderName) {
        if (from == null || from.getServer() == null) return;
        for (ServerPlayer other : from.getServer().getPlayerList().getPlayers()) {
            if (!other.hasPermissions(2)) continue;
            if (other.connection.connection.isMemoryConnection()) continue;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> other),
                    WorkspaceBrowserReplyPacket.status(
                            WorkspaceBrowserRules.OP_LOCK_STATE, true, path, holderName == null ? "" : holderName));
        }
    }

    private static void broadcastFileChanged(ServerPlayer writer, String path) {
        if (writer == null || writer.getServer() == null) return;
        for (ServerPlayer other : writer.getServer().getPlayerList().getPlayers()) {
            if (!other.hasPermissions(2)) continue;
            if (other.getUUID().equals(writer.getUUID())) continue;
            if (other.connection.connection.isMemoryConnection()) continue;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> other),
                    WorkspaceBrowserReplyPacket.status(
                            WorkspaceBrowserRules.OP_FILE_CHANGED, true, path, writer.getGameProfile().getName()));
        }
    }

    /** Scene deploy / other server-side writes that other OP editors should reload. */
    public static void notifyPeersFileChanged(ServerPlayer writer, String path) {
        broadcastFileChanged(writer, path);
        if (writer != null) sendListToRemoteOps(writer.getServer());
    }

    private static void sendLimits(ServerPlayer player) {
        org.zonarstudio.spraute_engine.config.SprauteConfig cfg =
                org.zonarstudio.spraute_engine.config.SprauteConfig.get();
        reply(player, WorkspaceBrowserRules.OP_SET_LIMITS, true, "",
                cfg.assetSyncMaxFileMb + "," + cfg.assetSyncMaxTotalMb + "," + cfg.assetSyncMaxFiles);
    }

    private static void handleSetLimits(ServerPlayer player, WorkspaceBrowserOpPacket msg) {
        org.zonarstudio.spraute_engine.config.SprauteConfig cfg =
                org.zonarstudio.spraute_engine.config.SprauteConfig.get();
        cfg.assetSyncMaxFileMb = (int) msg.fileSize;
        cfg.assetSyncMaxTotalMb = msg.offset;
        try {
            cfg.assetSyncMaxFiles = Integer.parseInt(msg.dest.trim());
        } catch (Exception ignored) {
        }
        cfg.normalize();
        org.zonarstudio.spraute_engine.config.SprauteConfig.save();
        AssetSyncServer.invalidate();
        LOGGER.info("[Spraute Workspace] {} set sync limits: {} MB/file, {} MB total, {} files",
                player.getGameProfile().getName(),
                cfg.assetSyncMaxFileMb, cfg.assetSyncMaxTotalMb, cfg.assetSyncMaxFiles);
        for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
            if (!other.hasPermissions(2)) continue;
            if (other.connection.connection.isMemoryConnection()) continue;
            sendLimits(other);
        }
        reply(player, WorkspaceBrowserRules.OP_SET_LIMITS, true, "",
                cfg.assetSyncMaxFileMb + "," + cfg.assetSyncMaxTotalMb + "," + cfg.assetSyncMaxFiles);
    }

    private static void reply(ServerPlayer player, byte op, boolean ok, String path, String message) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                WorkspaceBrowserReplyPacket.status(op, ok, path, message == null ? "" : message));
    }

    private static final class Incoming {
        final String path;
        final byte[] buf;
        int received;

        Incoming(String path, int size) {
            this.path = path;
            this.buf = new byte[Math.max(0, size)];
        }
    }

    private static final class FileLock {
        final UUID playerId;
        final String playerName;

        FileLock(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName == null ? "" : playerName;
        }
    }
}
