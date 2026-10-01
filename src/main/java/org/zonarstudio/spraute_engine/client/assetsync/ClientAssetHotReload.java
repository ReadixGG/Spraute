package org.zonarstudio.spraute_engine.client.assetsync;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient;
import org.zonarstudio.spraute_engine.client.editor.scene.EditorAssetIndex;
import org.zonarstudio.spraute_engine.client.editor.scene.EditorTexturePreview;
import org.zonarstudio.spraute_engine.client.editor.scene.HierarchyPanel;
import org.zonarstudio.spraute_engine.resource.WorkspaceBrowserServer;

import java.nio.file.Path;
import java.util.UUID;

/**
 * After an editor writes textures/geo/sounds on disk, refresh explorer previews
 * and reload Minecraft packs so items/NPC/UI pick up the new pixels.
 *
 * <p>Dedicated writers already have the file locally, so AssetSync skips them —
 * this class still reloads packs. Integrated/LAN host also kicks a live push
 * so remote clients get the same file.
 */
public final class ClientAssetHotReload {
    private static final long DELAY_MS = 400L;

    private static long reloadAfterMs;
    private static boolean inFlight;

    private ClientAssetHotReload() {}

    public static void reset() {
        reloadAfterMs = 0L;
        inFlight = false;
    }

    public static void afterLocalSave(Path file) {
        EditorTexturePreview.invalidate(file);
        org.zonarstudio.spraute_engine.client.particle.ParticleTextureCache.invalidate(file);
        EditorAssetIndex.refresh();
        HierarchyPanel.projectNeedsReload = true;
        notifyIntegratedHost(file);
        schedulePackReload();
    }

    public static void afterRemoteFile(String rel) {
        if (rel == null || rel.isBlank()) return;
        EditorTexturePreview.invalidate(RemoteProjectClient.localPath(rel));
        org.zonarstudio.spraute_engine.client.particle.ParticleTextureCache.invalidate(
                RemoteProjectClient.localPath(rel));
        EditorAssetIndex.refresh();
        HierarchyPanel.projectNeedsReload = true;
        schedulePackReload();
    }

    public static void noteFileWritten(String rel) {
        if (rel == null || rel.isBlank()) return;
        EditorTexturePreview.invalidate(RemoteProjectClient.localPath(rel));
        org.zonarstudio.spraute_engine.client.particle.ParticleTextureCache.invalidate(
                RemoteProjectClient.localPath(rel));
        HierarchyPanel.projectNeedsReload = true;
    }

    /** Packs were rebuilt; keep a pending reload if another save landed during it. */
    public static void markPacksReloaded() {
        if (reloadAfterMs <= System.currentTimeMillis()) {
            reloadAfterMs = 0L;
        }
        EditorTexturePreview.clear();
        org.zonarstudio.spraute_engine.client.particle.ParticleTextureCache.clear();
        EditorAssetIndex.refresh();
        HierarchyPanel.projectNeedsReload = true;
    }

    public static void tick() {
        if (reloadAfterMs <= 0L || inFlight) return;
        if (AssetSyncClient.isBusy()) {
            reloadAfterMs = System.currentTimeMillis() + DELAY_MS;
            return;
        }
        if (System.currentTimeMillis() < reloadAfterMs) return;
        reloadAfterMs = 0L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        inFlight = true;
        mc.reloadResourcePacks().whenComplete((ok, err) -> mc.execute(() -> {
            inFlight = false;
            markPacksReloaded();
        }));
    }

    private static void schedulePackReload() {
        reloadAfterMs = System.currentTimeMillis() + DELAY_MS;
    }

    private static void notifyIntegratedHost(Path file) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || file == null) return;
        String rel = RemoteProjectClient.relativeOf(file);
        if (rel == null || rel.isBlank()) return;
        UUID id = mc.player == null ? null : mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer host = id == null ? null : server.getPlayerList().getPlayer(id);
            WorkspaceBrowserServer.notifyHostMutation(server, host, rel);
        });
    }
}
