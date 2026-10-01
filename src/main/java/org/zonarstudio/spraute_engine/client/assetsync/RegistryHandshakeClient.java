package org.zonarstudio.spraute_engine.client.assetsync;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.gui.ModMismatchDisconnectedScreen;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.resource.SprautePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Client-side flags for dedicated-server create-item/particle sync.
 * Snapshot is written during login handshake; Forge registers IDs only on the next launch.
 */
public final class RegistryHandshakeClient {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean snapshotWrote;
    private static boolean ignoredServerEntries;
    private static boolean replaceDisconnect;

    private RegistryHandshakeClient() {}

    public static void resetSession() {
        snapshotWrote = false;
        ignoredServerEntries = false;
        replaceDisconnect = false;
    }

    public static void applySnapshot(byte[] utf8) {
        if (utf8 == null || utf8.length == 0) return;
        try {
            Path scripts = SprautePaths.scriptsDir();
            Files.createDirectories(scripts);
            Path out = scripts.resolve(org.zonarstudio.spraute_engine.registry.SyncedRegistrySnapshot.FILE_NAME);
            if (Files.isRegularFile(out)) {
                byte[] prev = Files.readAllBytes(out);
                if (Arrays.equals(prev, utf8)) return;
            }
            Files.write(out, utf8);
            snapshotWrote = true;
            replaceDisconnect = true;
            LOGGER.info("[Spraute Registry] Wrote handshake snapshot {} ({} bytes). Restart Minecraft to register IDs.",
                    org.zonarstudio.spraute_engine.registry.SyncedRegistrySnapshot.RELATIVE_PATH, utf8.length);
        } catch (IOException e) {
            LOGGER.warn("[Spraute Registry] Failed to write handshake snapshot: {}", e.toString());
        }
    }

    public static void markIgnoredServerEntries() {
        ignoredServerEntries = true;
        replaceDisconnect = true;
    }

    public static boolean needsRestartPrompt() {
        return snapshotWrote || ignoredServerEntries;
    }

    public static boolean consumeReplaceDisconnect() {
        if (!replaceDisconnect) return false;
        replaceDisconnect = false;
        return true;
    }

    public static boolean shouldReplaceDisconnectScreen(Screen incoming) {
        if (!replaceDisconnect && !snapshotWrote && !ignoredServerEntries) return false;
        return incoming instanceof ModMismatchDisconnectedScreen
                || incoming instanceof DisconnectedScreen;
    }

    public static void promptRestartIfNeeded() {
        boolean needed = needsRestartPrompt();
        snapshotWrote = false;
        ignoredServerEntries = false;
        // In-world now: later kicks (oversized packets, timeout) are not the handshake.
        replaceDisconnect = false;
        if (!needed) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!(mc.screen instanceof RegistryRestartPromptScreen)
                    && !(mc.screen instanceof RegistryRestartDisconnectScreen)) {
                mc.setScreen(new RegistryRestartPromptScreen());
            }
        });
    }
}
