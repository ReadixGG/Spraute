package org.zonarstudio.spraute_engine.client.assetsync;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.network.AssetChunkPacket;
import org.zonarstudio.spraute_engine.network.AssetManifestPacket;
import org.zonarstudio.spraute_engine.network.AssetSyncFinishedPacket;
import org.zonarstudio.spraute_engine.network.AssetSyncRequestPacket;
import org.zonarstudio.spraute_engine.network.AssetSyncStatusPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;
import org.zonarstudio.spraute_engine.resource.SprautePaths;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AssetSyncClient {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static String fingerprint = "";
    private static int pageCount;
    private static int totalFiles;
    private static long totalBytes;
    private static final Map<String, AssetSyncRules.FileEntry> remote = new HashMap<>();
    private static final List<AssetSyncRules.FileEntry> needed = new ArrayList<>();
    private static boolean waitingPrompt;
    private static boolean downloading;
    private static boolean reloading;
    private static boolean finishedHandled;
    private static boolean sessionReady;
    private static boolean liveUpdate;
    private static boolean pendingOffer;
    private static boolean registryRestartNeeded;
    private static int filesWritten;
    private static int filesFailed;
    private static int neededTotal;
    private static long neededBytesTotal;
    private static long bytesWritten;
    private static int progressTicks;
    private static final Map<String, Incoming> incoming = new HashMap<>();

    private AssetSyncClient() {}

    public static void reset() {
        fingerprint = "";
        pageCount = 0;
        totalFiles = 0;
        totalBytes = 0L;
        remote.clear();
        needed.clear();
        waitingPrompt = false;
        downloading = false;
        reloading = false;
        finishedHandled = false;
        sessionReady = false;
        liveUpdate = false;
        pendingOffer = false;
        registryRestartNeeded = false;
        filesWritten = 0;
        filesFailed = 0;
        neededTotal = 0;
        neededBytesTotal = 0L;
        bytesWritten = 0L;
        progressTicks = 0;
        incoming.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AssetSyncPromptScreen) {
            mc.setScreen(null);
        }
    }

    public static boolean isOverlayVisible() {
        return waitingPrompt || downloading || reloading;
    }

    public static boolean isBusy() {
        return downloading || reloading;
    }

    public static boolean isLiveHud() {
        return liveUpdate && (downloading || reloading);
    }

    public static String overlayPhaseKey() {
        if (liveUpdate) {
            if (reloading) return "spraute_engine.asset_sync.overlay.reload";
            return "spraute_engine.asset_sync.overlay.live";
        }
        if (reloading) return "spraute_engine.asset_sync.overlay.reload";
        if (downloading) return "spraute_engine.asset_sync.overlay.download";
        return "spraute_engine.asset_sync.overlay.wait";
    }

    public static String overlayCounts() {
        return filesWritten + "/" + Math.max(neededTotal, needed.size());
    }

    public static int overlayFilesRemaining() {
        if (reloading) return 0;
        return Math.max(0, Math.max(neededTotal, needed.size()) - filesWritten);
    }

    public static long overlayBytes() {
        return neededBytesTotal > 0 ? neededBytesTotal : neededBytes();
    }

    public static long overlayBytesRemaining() {
        if (reloading) return 0L;
        return Math.max(0L, overlayBytes() - bytesWritten);
    }

    public static float overlayRatio() {
        if (reloading) return 1f;
        long total = overlayBytes();
        if (total > 0L) {
            return Math.min(1f, bytesWritten / (float) total);
        }
        int files = Math.max(neededTotal, needed.size());
        if (files <= 0) return 0f;
        return Math.min(1f, filesWritten / (float) files);
    }

    public static void onManifest(AssetManifestPacket msg) {
        if (msg.pageIndex == 0 || !msg.fingerprint.equals(fingerprint)) {
            remote.clear();
            fingerprint = msg.fingerprint;
            pageCount = msg.pageCount;
            totalFiles = msg.totalFiles;
            totalBytes = msg.totalBytes;
            waitingPrompt = false;
            finishedHandled = false;
            filesWritten = 0;
            filesFailed = 0;
            bytesWritten = 0L;
            incoming.clear();
            if (!reloading) {
                downloading = false;
            }
        }
        for (AssetSyncRules.FileEntry e : msg.entries) {
            remote.put(e.path, e);
        }
        if (msg.pageIndex + 1 >= Math.max(1, msg.pageCount)) {
            if (reloading) {
                pendingOffer = true;
            } else {
                diffAndOffer();
            }
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (waitingPrompt && mc.screen == null && !needed.isEmpty()) {
            mc.setScreen(new AssetSyncPromptScreen(
                    needed.size(), neededBytes(), currentServerKey()));
        }
        if (downloading && neededTotal > 0 && !liveUpdate) {
            progressTicks++;
            if (progressTicks % 20 == 0) {
                bar(Component.translatable("spraute_engine.asset_sync.progress",
                        filesWritten, neededTotal));
            }
        }
    }

    public static void accept(boolean always) {
        waitingPrompt = false;
        if (always) {
            AssetSyncPermissions.remember(currentServerKey());
        }
        ModNetwork.CHANNEL.sendToServer(AssetSyncStatusPacket.allow());
        startDownload();
    }

    public static void deny() {
        waitingPrompt = false;
        downloading = false;
        reloading = false;
        sessionReady = true;
        liveUpdate = false;
        pendingOffer = false;
        needed.clear();
        remote.clear();
        neededTotal = 0;
        neededBytesTotal = 0L;
        bytesWritten = 0L;
        ModNetwork.CHANNEL.sendToServer(AssetSyncStatusPacket.deny());
        bar(Component.translatable("spraute_engine.asset_sync.denied"));
    }

    public static void onChunk(AssetChunkPacket msg) {
        if (!downloading) return;
        String path = AssetSyncRules.sanitizeRelative(msg.path);
        if (path == null) return;
        AssetSyncRules.FileEntry expected = findNeeded(path);
        if (expected == null) return;
        if (msg.fileSize <= 0 || msg.fileSize > AssetSyncRules.HARD_MAX_FILE_BYTES) return;
        if (msg.fileSize != expected.size) return;
        if (msg.offset < 0 || msg.data.length > AssetSyncRules.CHUNK_BYTES) return;
        if (msg.offset + (long) msg.data.length > msg.fileSize) return;
        Incoming slot = incoming.get(path);
        if (slot == null) {
            slot = new Incoming((int) msg.fileSize, expected.sha256);
            incoming.put(path, slot);
        }
        if (slot.buf.length != msg.fileSize) return;
        System.arraycopy(msg.data, 0, slot.buf, msg.offset, msg.data.length);
        slot.received += msg.data.length;
        if (!msg.last) return;
        finishFile(path, slot);
    }

    public static void onFinished(AssetSyncFinishedPacket msg) {
        if (finishedHandled) return;
        finishedHandled = true;
        downloading = false;
        incoming.clear();
        sessionReady = true;
        boolean wasLive = liveUpdate;
        int written = filesWritten;
        needed.clear();
        remote.clear();
        if (written > 0) {
            reloading = true;
            Minecraft mc = Minecraft.getInstance();
            mc.reloadResourcePacks().whenComplete((ok, err) -> mc.execute(() -> {
                reloading = false;
                neededTotal = 0;
                neededBytesTotal = 0L;
                bytesWritten = 0L;
                liveUpdate = false;
                ClientAssetHotReload.markPacksReloaded();
                ModNetwork.CHANNEL.sendToServer(AssetSyncStatusPacket.complete(written));
                if (err != null) {
                    LOGGER.warn("[Spraute AssetSync] resource reload failed", err);
                    bar(Component.translatable("spraute_engine.asset_sync.failed",
                            err.getMessage() == null ? "reload" : err.getMessage()));
                } else if (!wasLive) {
                    bar(Component.translatable("spraute_engine.asset_sync.done", written));
                }
                maybePromptRegistryRestart();
                org.zonarstudio.spraute_engine.client.ui.VideoPrewarm.retryPending();
                if (pendingOffer) {
                    pendingOffer = false;
                    diffAndOffer();
                }
            }));
            return;
        }
        neededTotal = 0;
        neededBytesTotal = 0L;
        bytesWritten = 0L;
        liveUpdate = false;
        ModNetwork.CHANNEL.sendToServer(AssetSyncStatusPacket.complete(written));
        if (!msg.success && filesFailed > 0) {
            bar(Component.translatable("spraute_engine.asset_sync.failed", msg.message));
        } else if (!wasLive && written > 0) {
            bar(Component.translatable("spraute_engine.asset_sync.done", written));
        }
        maybePromptRegistryRestart();
        org.zonarstudio.spraute_engine.client.ui.VideoPrewarm.retryPending();
        if (pendingOffer) {
            pendingOffer = false;
            diffAndOffer();
        }
    }

    private static void maybePromptRegistryRestart() {
        if (!registryRestartNeeded) return;
        registryRestartNeeded = false;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!(mc.screen instanceof RegistryRestartPromptScreen)) {
                mc.setScreen(new RegistryRestartPromptScreen());
            }
        });
    }

    public static String currentServerKey() {
        ServerData data = Minecraft.getInstance().getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) {
            return data.ip.trim().toLowerCase(Locale.ROOT);
        }
        if (!fingerprint.isBlank()) return "fp:" + fingerprint;
        return "unknown";
    }

    private static void diffAndOffer() {
        needed.clear();
        Path workspace = SprautePaths.workspace();
        long extra = 0L;
        for (AssetSyncRules.FileEntry remoteEntry : remote.values()) {
            if (extra + remoteEntry.size > AssetSyncRules.HARD_MAX_TOTAL_BYTES) break;
            if (needed.size() >= AssetSyncRules.HARD_MAX_FILES) break;
            Path local = AssetSyncRules.resolveInWorkspace(workspace, remoteEntry.path);
            if (local == null) continue;
            if (Files.isRegularFile(local)) {
                try {
                    if (Files.size(local) == remoteEntry.size
                            && AssetSyncRules.hashesEqual(AssetSyncRules.sha256(local), remoteEntry.sha256)) {
                        continue;
                    }
                } catch (IOException ignored) {
                    // treat as missing
                }
            }
            needed.add(remoteEntry);
            extra += remoteEntry.size;
        }
        neededTotal = needed.size();
        neededBytesTotal = extra;
        if (needed.isEmpty()) {
            if (!sessionReady) {
                LOGGER.info("[Spraute AssetSync] client already has {} files", remote.size());
            }
            remote.clear();
            sessionReady = true;
            liveUpdate = false;
            ModNetwork.CHANNEL.sendToServer(AssetSyncStatusPacket.skipped());
            return;
        }
        LOGGER.info("[Spraute AssetSync] need {} files ({})", needed.size(),
                AssetSyncRules.formatBytes(extra));
        if (sessionReady) {
            liveUpdate = true;
            startDownload();
        } else if (AssetSyncPermissions.isAlwaysAllowed(currentServerKey())) {
            liveUpdate = false;
            ModNetwork.CHANNEL.sendToServer(AssetSyncStatusPacket.allow());
            startDownload();
        } else {
            liveUpdate = false;
            waitingPrompt = true;
        }
    }

    private static void startDownload() {
        if (needed.isEmpty()) return;
        downloading = true;
        finishedHandled = false;
        filesWritten = 0;
        filesFailed = 0;
        bytesWritten = 0L;
        neededTotal = needed.size();
        incoming.clear();
        List<String> paths = new ArrayList<>(needed.size());
        for (AssetSyncRules.FileEntry e : needed) {
            paths.add(e.path);
        }
        ModNetwork.CHANNEL.sendToServer(new AssetSyncRequestPacket(paths));
        if (!liveUpdate) {
            bar(Component.translatable("spraute_engine.asset_sync.progress", 0, neededTotal));
        }
    }

    private static void finishFile(String path, Incoming slot) {
        incoming.remove(path);
        if (!AssetSyncRules.hashesEqual(AssetSyncRules.sha256(slot.buf), slot.hash)) {
            LOGGER.warn("[Spraute AssetSync] hash mismatch {}", path);
            filesFailed++;
            return;
        }
        Path dest = AssetSyncRules.resolveInWorkspace(SprautePaths.workspace(), path);
        if (dest == null) {
            filesFailed++;
            return;
        }
        try {
            Files.createDirectories(dest.getParent());
            Path tmp = dest.resolveSibling(dest.getFileName().toString() + ".part");
            Files.write(tmp, slot.buf);
            try {
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            filesWritten++;
            bytesWritten += slot.buf.length;
            ClientAssetHotReload.noteFileWritten(path);
            if (org.zonarstudio.spraute_engine.registry.SyncedRegistrySnapshot
                    .isSyncedRegistryRelative(path)) {
                registryRestartNeeded = true;
            }
        } catch (IOException e) {
            LOGGER.warn("[Spraute AssetSync] write failed {}: {}", path, e.getMessage());
            filesFailed++;
        }
    }

    private static AssetSyncRules.FileEntry findNeeded(String path) {
        for (AssetSyncRules.FileEntry e : needed) {
            if (e.path.equals(path)) return e;
        }
        return null;
    }

    private static long neededBytes() {
        long n = 0L;
        for (AssetSyncRules.FileEntry e : needed) n += e.size;
        return n;
    }

    private static void bar(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(text, true);
        }
    }

    private static final class Incoming {
        final byte[] buf;
        final byte[] hash;
        int received;

        Incoming(int size, byte[] hash) {
            this.buf = new byte[size];
            this.hash = Arrays.copyOf(hash, hash.length);
        }
    }
}
