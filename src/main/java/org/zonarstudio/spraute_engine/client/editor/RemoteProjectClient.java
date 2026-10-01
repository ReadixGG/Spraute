package org.zonarstudio.spraute_engine.client.editor;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.client.editor.scene.EditorAssetIndex;
import org.zonarstudio.spraute_engine.client.editor.scene.HierarchyPanel;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.WorkspaceBrowserListPacket;
import org.zonarstudio.spraute_engine.network.WorkspaceBrowserOpPacket;
import org.zonarstudio.spraute_engine.network.WorkspaceBrowserReplyPacket;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;
import org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules;
import org.zonarstudio.spraute_engine.resource.WorkspaceBrowserServer;
import org.zonarstudio.spraute_engine.script.ScriptConsole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Client view of the dedicated-server {@code spraute_engine/} workspace.
 * Singleplayer / LAN host keep using the local disk — this is for OP on a remote server.
 *
 * <p>Scripts/scenes run on the server disk only. Local files go up only when an OP
 * presses «Залить…» in the Project panel — not on join.
 */
public final class RemoteProjectClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SOURCE = "Project";

    /** Content the server must have for scripts/scenes to run (not visual assets). */
    private static final List<String> RUNTIME_ROOTS = List.of(
            "scripts", "scenes", "prefabs", "plugins", "routes", "lang");
    private static final int MAX_AUTO_PUSH_FILES = 800;

    private static final List<WorkspaceBrowserRules.Entry> entries = new ArrayList<>();
    private static final Map<Integer, List<WorkspaceBrowserRules.Entry>> listPages = new HashMap<>();
    private static int expectedPages;
    private static boolean listReady;
    private static boolean listLoading;
    private static String lastError = "";

    private static final Map<String, IncomingRead> reads = new HashMap<>();
    private static final Map<String, List<Consumer<Boolean>>> readCallbacks = new HashMap<>();
    private static final ArrayDeque<WriteJob> writeQueue = new ArrayDeque<>();
    private static WriteJob currentWrite;
    private static boolean writeHadError;
    private static final List<java.util.function.Consumer<Boolean>> idleWriteCallbacks = new ArrayList<>();
    private static final Map<String, String> locks = new HashMap<>();
    private static final Map<String, List<Consumer<Boolean>>> lockCallbacks = new HashMap<>();
    private static final java.util.Set<String> heldLocks = new java.util.HashSet<>();
    private static int serverMaxFileMb;
    private static int serverMaxTotalMb;
    private static int serverMaxFiles;

    private RemoteProjectClient() {}

    public static boolean isRemote() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getSingleplayerServer() == null && mc.getConnection() != null;
    }

    public static Path localRoot() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("spraute_engine");
    }

    public static boolean hasList() {
        return listReady;
    }

    public static boolean isLoading() {
        return listLoading && !listReady;
    }

    public static String lastError() {
        return lastError;
    }

    public static List<WorkspaceBrowserRules.Entry> entries() {
        return entries;
    }

    public static boolean hasPath(String rel) {
        String path = normalize(rel);
        if (path.isEmpty()) return false;
        for (WorkspaceBrowserRules.Entry e : entries) {
            if (path.equals(e.path)) return true;
        }
        return false;
    }

    public static List<WorkspaceBrowserRules.Entry> children(String parentRel) {
        String parent = normalize(parentRel);
        List<WorkspaceBrowserRules.Entry> out = new ArrayList<>();
        for (WorkspaceBrowserRules.Entry e : entries) {
            if (WorkspaceBrowserRules.parentOf(e.path).equals(parent)) out.add(e);
        }
        return out;
    }

    public static String relativeOf(Path path) {
        if (path == null) return "";
        try {
            Path rel = localRoot().toAbsolutePath().normalize()
                    .relativize(path.toAbsolutePath().normalize());
            return rel.toString().replace('\\', '/');
        } catch (Exception e) {
            return "";
        }
    }

    public static String lockHolder(Path path) {
        return lockHolder(relativeOf(path));
    }

    public static String lockHolder(String rel) {
        String path = WorkspaceBrowserRules.sanitize(rel, false);
        if (path == null) return "";
        String name = locks.get(path);
        return name == null ? "" : name;
    }

    public static boolean isLockedByOther(Path path) {
        return isLockedByOther(relativeOf(path));
    }

    public static boolean isLockedByOther(String rel) {
        String holder = lockHolder(rel);
        if (holder.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        String me = mc.player == null ? "" : mc.player.getGameProfile().getName();
        return !holder.equalsIgnoreCase(me);
    }

    /** Exclusive lock for scripts / visual / assets. Scenes are not locked. */
    public static void requestLock(String rel, Consumer<Boolean> then) {
        if (!isRemote()) {
            if (then != null) then.accept(true);
            return;
        }
        String path = WorkspaceBrowserRules.sanitize(rel, false);
        if (path == null) {
            if (then != null) then.accept(false);
            return;
        }
        if (heldLocks.contains(path)) {
            if (then != null) then.accept(true);
            return;
        }
        // The local lock snapshot is only presentation state and can be stale if an
        // unlock broadcast was missed. Always let the server arbitrate ownership.
        if (then != null) {
            lockCallbacks.computeIfAbsent(path, k -> new ArrayList<>()).add(then);
        }
        ModNetwork.CHANNEL.sendToServer(WorkspaceBrowserOpPacket.lock(path));
    }

    public static void unlock(Path path) {
        unlock(relativeOf(path));
    }

    public static void unlock(String rel) {
        if (!isRemote()) return;
        String path = WorkspaceBrowserRules.sanitize(rel, false);
        if (path == null) return;
        heldLocks.remove(path);
        ModNetwork.CHANNEL.sendToServer(WorkspaceBrowserOpPacket.unlock(path));
    }

    public static void unlockAll() {
        if (!isRemote() || heldLocks.isEmpty()) return;
        for (String path : List.copyOf(heldLocks)) {
            ModNetwork.CHANNEL.sendToServer(WorkspaceBrowserOpPacket.unlock(path));
        }
        heldLocks.clear();
    }

    public static boolean hasSyncLimits() {
        return serverMaxFileMb > 0 && serverMaxTotalMb > 0;
    }

    public static int serverMaxFileMb() { return serverMaxFileMb; }
    public static int serverMaxTotalMb() { return serverMaxTotalMb; }
    public static int serverMaxFiles() { return serverMaxFiles; }

    public static void pushSyncLimits(int fileMb, int totalMb, int maxFiles) {
        if (!isRemote()) return;
        ModNetwork.CHANNEL.sendToServer(WorkspaceBrowserOpPacket.setLimits(fileMb, totalMb, maxFiles));
    }

    private static long uploadMaxFileBytes() {
        if (serverMaxFileMb > 0) return serverMaxFileMb * 1024L * 1024L;
        return WorkspaceBrowserRules.maxFileBytes();
    }

    private static void applyLimitsMessage(String raw) {
        if (raw == null || raw.isBlank()) return;
        String[] parts = raw.split(",");
        if (parts.length < 3) return;
        try {
            serverMaxFileMb = Integer.parseInt(parts[0].trim());
            serverMaxTotalMb = Integer.parseInt(parts[1].trim());
            serverMaxFiles = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException ignored) {
        }
    }

    private static void onLockReply(WorkspaceBrowserReplyPacket msg) {
        if (msg.ok) {
            heldLocks.add(msg.path);
            if (msg.message != null && !msg.message.isBlank()) {
                locks.put(msg.path, msg.message);
            }
        } else if (msg.message != null && !msg.message.isBlank()) {
            locks.put(msg.path, msg.message);
        }
        List<Consumer<Boolean>> cbs = lockCallbacks.remove(msg.path);
        if (cbs == null) return;
        for (Consumer<Boolean> cb : cbs) {
            try {
                cb.accept(msg.ok);
            } catch (Exception e) {
                LOGGER.warn("[Spraute Workspace] lock callback: {}", e.toString());
            }
        }
    }

    private static void onPeerFileChanged(String rel) {
        String path = WorkspaceBrowserRules.sanitize(rel, false);
        if (path == null) return;
        if (isSceneRel(path)) {
            ensureLocal(path, () -> org.zonarstudio.spraute_engine.client.editor.scene.SceneEditorSession
                    .applyRemoteIfOpen(localPath(path)));
            return;
        }
        if (WorkspaceBrowserServer.isLiveAssetPath(path)) {
            ensureLocal(path, () -> org.zonarstudio.spraute_engine.client.assetsync.ClientAssetHotReload
                    .afterRemoteFile(path));
        }
    }

    private static boolean isSceneRel(String rel) {
        if (rel == null || rel.isBlank()) return false;
        String n = rel.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        return n.startsWith("scenes/") && n.endsWith(".scene.json");
    }

    public static Path localPath(String rel) {
        Path p = localRoot();
        String path = normalize(rel);
        if (path.isEmpty()) return p;
        for (String part : path.split("/")) {
            if (!part.isEmpty()) p = p.resolve(part);
        }
        return p;
    }

    public static void reset() {
        entries.clear();
        listPages.clear();
        expectedPages = 0;
        listReady = false;
        listLoading = false;
        lastError = "";
        reads.clear();
        readCallbacks.clear();
        writeQueue.clear();
        currentWrite = null;
        writeHadError = false;
        idleWriteCallbacks.clear();
        locks.clear();
        lockCallbacks.clear();
        heldLocks.clear();
        serverMaxFileMb = 0;
        serverMaxTotalMb = 0;
        serverMaxFiles = 0;
    }

    public static void requestList() {
        if (!isRemote()) return;
        listLoading = true;
        listReady = false;
        listPages.clear();
        expectedPages = 0;
        ModNetwork.CHANNEL.sendToServer(WorkspaceBrowserOpPacket.list());
    }

    public static void onListPage(WorkspaceBrowserListPacket msg) {
        if (msg.pageIndex == 0) listPages.clear();
        expectedPages = Math.max(1, msg.pageCount);
        listPages.put(msg.pageIndex, new ArrayList<>(msg.entries));
        if (listPages.size() < expectedPages) return;
        entries.clear();
        for (int i = 0; i < expectedPages; i++) {
            List<WorkspaceBrowserRules.Entry> page = listPages.get(i);
            if (page != null) entries.addAll(page);
        }
        listPages.clear();
        listReady = true;
        listLoading = false;
        lastError = "";
        EditorAssetIndex.refresh();
        HierarchyPanel.projectNeedsReload = true;
    }

    /**
     * Upload local workspace runtime files to the dedicated server.
     *
     * @param onlyMissing if true, skip paths already present on the server list
     * @return number of files queued for upload
     */
    public static int pushLocalProject(boolean onlyMissing) {
        if (!isRemote() || !listReady) return 0;
        Path root = localRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) return 0;
        int queued = 0;
        long maxBytes = uploadMaxFileBytes();
        for (String dirName : RUNTIME_ROOTS) {
            Path dir = root.resolve(dirName);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    if (queued >= MAX_AUTO_PUSH_FILES) {
                        ScriptConsole.warn(SOURCE, EditorI18n.t("Достигнут лимит заливки файлов на сервер"));
                        return queued;
                    }
                    String rel = relativeOf(file);
                    String path = WorkspaceBrowserRules.sanitize(rel, false);
                    if (path == null) continue;
                    if (org.zonarstudio.spraute_engine.registry.SyncedRegistrySnapshot
                            .isSyncedRegistryRelative(path)) {
                        continue; // server owns this file
                    }
                    if (onlyMissing && hasPath(path)) continue;
                    try {
                        long size = Files.size(file);
                        if (size > maxBytes) {
                            ScriptConsole.warn(SOURCE, EditorI18n.t("Пропуск (слишком большой): ") + path);
                            continue;
                        }
                        byte[] data = Files.readAllBytes(file);
                        enqueueWrite(path, data);
                        queued++;
                    } catch (IOException e) {
                        ScriptConsole.warn(SOURCE, EditorI18n.t("Не удалось прочитать: ") + path);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("[Spraute Workspace] push walk {}: {}", dirName, e.toString());
            }
        }
        return queued;
    }

    public static void onReply(WorkspaceBrowserReplyPacket msg) {
        if (msg.op == WorkspaceBrowserRules.OP_READ) {
            onReadChunk(msg);
            return;
        }
        if (msg.op == WorkspaceBrowserRules.OP_LOCK) {
            onLockReply(msg);
            return;
        }
        if (msg.op == WorkspaceBrowserRules.OP_LOCK_STATE) {
            if (msg.message == null || msg.message.isBlank()) locks.remove(msg.path);
            else locks.put(msg.path, msg.message);
            return;
        }
        if (msg.op == WorkspaceBrowserRules.OP_FILE_CHANGED) {
            onPeerFileChanged(msg.path);
            return;
        }
        if (msg.op == WorkspaceBrowserRules.OP_UNLOCK) {
            return;
        }
        if (msg.op == WorkspaceBrowserRules.OP_SET_LIMITS) {
            applyLimitsMessage(msg.message);
            return;
        }
        if (msg.op == WorkspaceBrowserRules.OP_LIST) {
            if (!msg.ok) {
                listLoading = false;
                lastError = msg.message;
                ScriptConsole.error(SOURCE, EditorI18n.t("Не удалось получить список с сервера: ") + msg.message);
            }
            return;
        }
        if (msg.op == WorkspaceBrowserRules.OP_WRITE && currentWrite != null
                && currentWrite.path.equals(msg.path)) {
            if (!msg.ok) {
                writeHadError = true;
                ScriptConsole.error(SOURCE, EditorI18n.t("Не удалось записать на сервер: ")
                        + msg.path + " — " + msg.message);
            }
            currentWrite = null;
            pumpWrites();
            fireIdleWrites();
            return;
        }
        if (!msg.ok) {
            lastError = msg.message;
            ScriptConsole.error(SOURCE, EditorI18n.t("Ошибка проводника: ") + msg.message);
        }
    }

    public static void mkdir(String rel) {
        String path = WorkspaceBrowserRules.sanitize(rel, true);
        if (path == null || !isRemote()) {
            if (isRemote() && path == null) {
                ScriptConsole.error(SOURCE, EditorI18n.t("Папка должна быть внутри scripts, geo, textures…"));
            }
            return;
        }
        try {
            Files.createDirectories(localPath(path));
        } catch (IOException ignored) {
        }
        ModNetwork.CHANNEL.sendToServer(WorkspaceBrowserOpPacket.mkdir(path));
    }

    public static void delete(String rel) {
        if (!isRemote() || rel == null || rel.isBlank()) return;
        ModNetwork.CHANNEL.sendToServer(WorkspaceBrowserOpPacket.delete(rel));
        Path local = localPath(rel);
        try {
            if (Files.isDirectory(local)) {
                try (var walk = Files.walk(local)) {
                    List<Path> all = walk.sorted((a, b) -> b.compareTo(a)).toList();
                    for (Path p : all) Files.deleteIfExists(p);
                }
            } else {
                Files.deleteIfExists(local);
            }
        } catch (IOException ignored) {
        }
    }

    public static void rename(String from, String to) {
        if (!isRemote()) return;
        ModNetwork.CHANNEL.sendToServer(WorkspaceBrowserOpPacket.rename(from, to));
        Path src = localPath(from);
        Path dest = localPath(to);
        try {
            if (Files.exists(src)) {
                Files.createDirectories(dest.getParent());
                Files.move(src, dest);
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Runs {@code then} when the upload queue is empty.
     * {@code true} means every finished write since the last idle wait succeeded.
     */
    public static void whenWritesIdle(java.util.function.Consumer<Boolean> then) {
        if (then == null) return;
        if (currentWrite == null && writeQueue.isEmpty()) {
            boolean ok = !writeHadError;
            writeHadError = false;
            then.accept(ok);
            return;
        }
        idleWriteCallbacks.add(then);
    }

    private static void fireIdleWrites() {
        if (currentWrite != null || !writeQueue.isEmpty() || idleWriteCallbacks.isEmpty()) return;
        boolean ok = !writeHadError;
        writeHadError = false;
        List<java.util.function.Consumer<Boolean>> cbs = new ArrayList<>(idleWriteCallbacks);
        idleWriteCallbacks.clear();
        for (java.util.function.Consumer<Boolean> cb : cbs) {
            try {
                cb.accept(ok);
            } catch (Exception e) {
                LOGGER.warn("[Spraute Workspace] idle write callback: {}", e.toString());
            }
        }
    }

    /** Queue a local file (already on disk) to be written to the server. */
    public static void pushIfRemote(Path localFile) {
        if (!isRemote() || localFile == null || !Files.isRegularFile(localFile)) return;
        String rel = relativeOf(localFile);
        String path = WorkspaceBrowserRules.sanitize(rel, false);
        if (path == null) return;
        if (isRunningScenePath(path)) return;
        try {
            byte[] data = Files.readAllBytes(localFile);
            if (data.length > uploadMaxFileBytes()) {
                ScriptConsole.error(SOURCE, EditorI18n.t("Файл слишком большой для сервера: ") + path);
                return;
            }
            enqueueWrite(path, data);
        } catch (IOException e) {
            ScriptConsole.error(SOURCE, EditorI18n.t("Не удалось прочитать для отправки: ") + e.getMessage());
        }
    }

    public static void writeBytes(String rel, byte[] data) {
        String path = WorkspaceBrowserRules.sanitize(rel, false);
        if (path == null || !isRemote()) return;
        if (isRunningScenePath(path)) return;
        byte[] bytes = data == null ? new byte[0] : data;
        if (bytes.length > uploadMaxFileBytes()) {
            ScriptConsole.error(SOURCE, EditorI18n.t("Файл слишком большой для сервера: ") + path);
            return;
        }
        try {
            Path local = localPath(path);
            Files.createDirectories(local.getParent());
            Files.write(local, bytes);
        } catch (IOException ignored) {
        }
        enqueueWrite(path, bytes);
    }

    /**
     * Fetch {@code rel} from the server into the local workspace, then run {@code then}.
     * If already local and listed, still re-fetches so the server stays source of truth.
     */
    public static void ensureLocal(String rel, Runnable then) {
        String path = WorkspaceBrowserRules.sanitize(rel, false);
        if (path == null) return;
        if (!isRemote()) {
            if (then != null) then.run();
            return;
        }
        Consumer<Boolean> cb = ok -> {
            if (ok && then != null) then.run();
            else if (!ok) {
                ScriptConsole.error(SOURCE, EditorI18n.t("Не удалось скачать: ") + path);
            }
        };
        readCallbacks.computeIfAbsent(path, k -> new ArrayList<>()).add(cb);
        if (reads.containsKey(path)) return;
        reads.put(path, new IncomingRead(path));
        ModNetwork.CHANNEL.sendToServer(WorkspaceBrowserOpPacket.read(path));
    }

    private static void onReadChunk(WorkspaceBrowserReplyPacket msg) {
        if (!msg.ok) {
            reads.remove(msg.path);
            fireRead(msg.path, false);
            ScriptConsole.error(SOURCE, EditorI18n.t("Не удалось скачать: ")
                    + msg.path + (msg.message.isBlank() ? "" : " — " + msg.message));
            return;
        }
        IncomingRead inc = reads.get(msg.path);
        if (inc == null) {
            inc = new IncomingRead(msg.path);
            reads.put(msg.path, inc);
        }
        if (inc.buf == null) {
            if (msg.fileSize < 0 || msg.fileSize > AssetSyncRules.HARD_MAX_FILE_BYTES) {
                reads.remove(msg.path);
                fireRead(msg.path, false);
                return;
            }
            inc.buf = new byte[(int) msg.fileSize];
        }
        if (msg.offset + msg.data.length > inc.buf.length) {
            reads.remove(msg.path);
            fireRead(msg.path, false);
            return;
        }
        System.arraycopy(msg.data, 0, inc.buf, msg.offset, msg.data.length);
        inc.received += msg.data.length;
        if (!msg.last) return;
        try {
            Path local = localPath(msg.path);
            Files.createDirectories(local.getParent());
            Files.write(local, inc.buf == null ? new byte[0] : inc.buf);
            reads.remove(msg.path);
            fireRead(msg.path, true);
        } catch (IOException e) {
            reads.remove(msg.path);
            fireRead(msg.path, false);
            LOGGER.warn("[Spraute Workspace] write local {}: {}", msg.path, e.toString());
        }
    }

    private static void fireRead(String path, boolean ok) {
        List<Consumer<Boolean>> cbs = readCallbacks.remove(path);
        if (cbs == null) return;
        for (Consumer<Boolean> cb : cbs) {
            try {
                cb.accept(ok);
            } catch (Exception e) {
                LOGGER.warn("[Spraute Workspace] read callback: {}", e.toString());
            }
        }
    }

    private static void enqueueWrite(String path, byte[] data) {
        // Continuous inspector/gizmo edits may write the same file every frame.
        // Keep only the newest queued snapshot; the active upload is allowed to finish.
        writeQueue.removeIf(job -> job.path.equals(path));
        writeQueue.add(new WriteJob(path, data == null ? new byte[0] : data));
        pumpWrites();
    }

    private static void pumpWrites() {
        if (currentWrite != null || writeQueue.isEmpty()) return;
        currentWrite = writeQueue.poll();
        byte[] data = currentWrite.data;
        if (data.length == 0) {
            ModNetwork.CHANNEL.sendToServer(
                    WorkspaceBrowserOpPacket.writeChunk(currentWrite.path, 0L, 0, true, new byte[0]));
            return;
        }
        int offset = 0;
        while (offset < data.length) {
            int n = Math.min(WorkspaceBrowserRules.CHUNK_BYTES, data.length - offset);
            byte[] chunk = new byte[n];
            System.arraycopy(data, offset, chunk, 0, n);
            boolean last = offset + n >= data.length;
            ModNetwork.CHANNEL.sendToServer(
                    WorkspaceBrowserOpPacket.writeChunk(currentWrite.path, data.length, offset, last, chunk));
            offset += n;
        }
    }

    private static String normalize(String rel) {
        if (rel == null) return "";
        String s = rel.replace('\\', '/').trim();
        if (s.startsWith("/")) s = s.substring(1);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static boolean isRunningScenePath(String rel) {
        String id = org.zonarstudio.spraute_engine.scene.SceneRepository.sceneIdFromWorkspacePath(rel);
        return id != null && org.zonarstudio.spraute_engine.client.ClientSceneRuntime.isActive(id);
    }

    private static final class IncomingRead {
        final String path;
        byte[] buf;
        int received;

        IncomingRead(String path) {
            this.path = path;
        }
    }

    private static final class WriteJob {
        final String path;
        final byte[] data;

        WriteJob(String path, byte[] data) {
            this.path = path;
            this.data = data;
        }
    }
}
