package org.zonarstudio.spraute_engine.client.update;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.config.SprauteConfig;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.ModUpdateChunkPacket;
import org.zonarstudio.spraute_engine.network.ModUpdateHelloPacket;
import org.zonarstudio.spraute_engine.network.ModUpdateOfferPacket;
import org.zonarstudio.spraute_engine.network.ModUpdateRequestPacket;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;
import org.zonarstudio.spraute_engine.update.ModSelfUpdate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class ModUpdateClient {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean helloSent;
    private static boolean downloading;
    private static boolean pendingRestart;
    private static boolean requireRestart;
    private static String targetVersion = "";
    private static String fileName = "";
    private static byte[] expectedHash;
    private static byte[] buffer;
    private static int received;
    private static long fileSize;
    private static Path pendingFile;

    private ModUpdateClient() {}

    public static void reset() {
        helloSent = false;
        downloading = false;
        if (!pendingRestart) {
            requireRestart = false;
            targetVersion = "";
            fileName = "";
            expectedHash = null;
            buffer = null;
            received = 0;
            fileSize = 0L;
            deletePendingQuiet();
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (downloading || (pendingRestart && requireRestart)) {
            openScreen(mc);
        }
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.hasSingleplayerServer()) return;
        if (mc.getConnection().getConnection().isMemoryConnection()) return;
        if (helloSent) return;
        if (pendingRestart) return;
        helloSent = true;
        ModNetwork.CHANNEL.sendToServer(new ModUpdateHelloPacket(
                ModSelfUpdate.currentVersion(), ModSelfUpdate.currentMcVersion()));
    }

    public static void onOffer(ModUpdateOfferPacket msg) {
        requireRestart = msg.requireRestart;
        if (!SprauteConfig.get().isAutoUpdateModEnabled()) return;
        if (downloading || pendingRestart) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) return;
        String local = ModSelfUpdate.currentVersion();
        if (local.equals(msg.modVersion)) return;
        if (!ModSelfUpdate.currentMcVersion().equals(msg.mcVersion)) {
            LOGGER.warn("[Spraute Update] ignore offer for other MC {}", msg.mcVersion);
            return;
        }
        if (msg.size <= 0 || msg.size > ModSelfUpdate.MAX_JAR_BYTES) {
            LOGGER.warn("[Spraute Update] offer size {}", msg.size);
            return;
        }
        Path mods = ModSelfUpdate.modsDir();
        Path current = ModSelfUpdate.installedJar();
        if (mods == null || current == null) {
            chat(Component.translatable("spraute_engine.mod_update.not_mods_jar"));
            return;
        }
        fileName = ModSelfUpdate.sanitizeFileName(msg.fileName, msg.modVersion, msg.mcVersion);
        targetVersion = msg.modVersion;
        expectedHash = msg.sha256;
        fileSize = msg.size;
        buffer = new byte[(int) msg.size];
        received = 0;
        downloading = true;
        pendingFile = mods.resolve(fileName + ".pending");
        LOGGER.info("[Spraute Update] downloading {} ({} bytes)", fileName, msg.size);
        openScreen(mc);
        ModNetwork.CHANNEL.sendToServer(new ModUpdateRequestPacket());
    }

    public static void onChunk(ModUpdateChunkPacket msg) {
        if (!downloading || buffer == null) return;
        if (msg.fileSize != fileSize || msg.offset < 0 || msg.data.length == 0) return;
        if ((long) msg.offset + msg.data.length > fileSize) return;
        System.arraycopy(msg.data, 0, buffer, msg.offset, msg.data.length);
        received = Math.max(received, msg.offset + msg.data.length);
        if (!msg.last) return;
        finishDownload();
    }

    public static boolean isDownloading() {
        return downloading;
    }

    public static boolean isPendingRestart() {
        return pendingRestart;
    }

    public static boolean isRestartRequired() {
        return requireRestart;
    }

    public static float overlayRatio() {
        if (pendingRestart) return 1f;
        if (fileSize <= 0) return 0f;
        return Math.min(1f, received / (float) fileSize);
    }

    public static String overlayVersion() {
        return targetVersion;
    }

    public static long overlayBytes() {
        return fileSize;
    }

    public static long overlayReceived() {
        return received;
    }

    public static void quitGame() {
        Minecraft.getInstance().stop();
    }

    private static void openScreen(Minecraft mc) {
        if (mc.screen instanceof ModUpdateScreen) return;
        mc.setScreen(new ModUpdateScreen());
    }

    private static void finishDownload() {
        downloading = false;
        byte[] data = buffer;
        buffer = null;
        if (data == null || received < fileSize) {
            fail("incomplete");
            return;
        }
        byte[] hash = AssetSyncRules.sha256(data);
        if (!AssetSyncRules.hashesEqual(hash, expectedHash)) {
            fail("hash");
            return;
        }
        Path mods = ModSelfUpdate.modsDir();
        Path current = ModSelfUpdate.installedJar();
        if (mods == null || current == null) {
            fail("path");
            return;
        }
        Path target = mods.resolve(fileName).normalize();
        if (!target.startsWith(mods)) {
            fail("path");
            return;
        }
        try {
            Files.createDirectories(mods);
            pendingFile = mods.resolve(fileName + ".pending");
            Files.write(pendingFile, data);
            ModSelfUpdate.scheduleReplace(current, pendingFile, target);
            pendingRestart = true;
            LOGGER.info("[Spraute Update] jar ready {}, restart required", target.getFileName());
            openScreen(Minecraft.getInstance());
        } catch (IOException e) {
            LOGGER.warn("[Spraute Update] write failed: {}", e.toString());
            fail("write");
        } finally {
            Arrays.fill(data, (byte) 0);
        }
    }

    private static void fail(String reason) {
        downloading = false;
        buffer = null;
        received = 0;
        deletePendingQuiet();
        LOGGER.warn("[Spraute Update] failed: {}", reason);
        chat(Component.translatable("spraute_engine.mod_update.failed", reason));
    }

    private static void deletePendingQuiet() {
        if (pendingFile == null) return;
        try {
            Files.deleteIfExists(pendingFile);
        } catch (IOException ignored) {
        }
        pendingFile = null;
    }

    private static void chat(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(text, false);
    }
}
