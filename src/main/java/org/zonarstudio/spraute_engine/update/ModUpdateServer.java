package org.zonarstudio.spraute_engine.update;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.config.SprauteConfig;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.ModUpdateChunkPacket;
import org.zonarstudio.spraute_engine.network.ModUpdateOfferPacket;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;
import org.zonarstudio.spraute_engine.resource.AssetSyncServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Offers and streams the server's own jar to clients whose version differs. */
public final class ModUpdateServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNKS_PER_TICK = 8;
    private static final long HELLO_TIMEOUT_MS = 15_000L;
    private static final long REQUEST_TIMEOUT_MS = 15_000L;
    private static final int KICK_DELAY_TICKS = 60;
    private static final Object JAR_LOCK = new Object();

    private static byte[] cachedJar;
    private static byte[] cachedHash;
    private static String cachedName = "";
    private static long cachedAt;

    private static final Map<UUID, Transfer> TRANSFERS = new HashMap<>();
    private static final Map<UUID, Long> AWAITING_HELLO = new HashMap<>();
    private static final Map<UUID, Long> AWAITING_REQUEST = new HashMap<>();
    private static final Map<UUID, Integer> PENDING_KICK = new HashMap<>();

    private ModUpdateServer() {}

    public static void onPlayerJoin(ServerPlayer player) {
        if (player == null || AssetSyncServer.isLocalIntegratedPlayer(player)) return;
        if (!SprauteConfig.get().isAutoUpdateRequireRestart()) return;
        AWAITING_HELLO.put(player.getUUID(), System.currentTimeMillis());
    }

    public static void onHello(ServerPlayer player, String clientVersion, String clientMc) {
        if (player == null) return;
        if (AssetSyncServer.isLocalIntegratedPlayer(player)) return;
        AWAITING_HELLO.remove(player.getUUID());
        JarPayload jar = loadJar();
        String serverVer = ModSelfUpdate.currentVersion();
        String serverMc = ModSelfUpdate.currentMcVersion();
        String clientVer = clientVersion == null ? "" : clientVersion.trim();
        String clientMcVer = clientMc == null ? "" : clientMc.trim();
        if (serverVer.equals(clientVer)) return;
        boolean requireRestart = SprauteConfig.get().isAutoUpdateRequireRestart();
        if (!serverMc.equals(clientMcVer)) {
            LOGGER.info("[Spraute Update] skip {} (MC {} vs {})",
                    player.getGameProfile().getName(), clientMc, serverMc);
            if (requireRestart) kick(player);
            return;
        }
        boolean canOffer = SprauteConfig.get().isAutoUpdateModEnabled() && jar != null;
        if (canOffer) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ModUpdateOfferPacket(serverVer, serverMc, jar.fileName, jar.data.length,
                            jar.hash, requireRestart));
            LOGGER.info("[Spraute Update] offer {} → {} ({})",
                    clientVersion, serverVer, player.getGameProfile().getName());
            if (requireRestart) {
                AWAITING_REQUEST.put(player.getUUID(), System.currentTimeMillis());
            }
            return;
        }
        if (requireRestart) kick(player);
    }

    public static void beginTransfer(ServerPlayer player) {
        if (player == null || AssetSyncServer.isLocalIntegratedPlayer(player)) return;
        if (!SprauteConfig.get().isAutoUpdateModEnabled()) return;
        AWAITING_REQUEST.remove(player.getUUID());
        if (TRANSFERS.containsKey(player.getUUID())) return;
        JarPayload jar = loadJar();
        if (jar == null) return;
        TRANSFERS.put(player.getUUID(), new Transfer(jar.data));
        LOGGER.info("[Spraute Update] sending jar ({} bytes) to {}",
                jar.data.length, player.getGameProfile().getName());
    }

    public static void cancel(UUID playerId) {
        TRANSFERS.remove(playerId);
        AWAITING_HELLO.remove(playerId);
        AWAITING_REQUEST.remove(playerId);
        PENDING_KICK.remove(playerId);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        long now = System.currentTimeMillis();
        if (SprauteConfig.get().isAutoUpdateRequireRestart()) {
            tickTimeouts(server, AWAITING_HELLO, now, HELLO_TIMEOUT_MS);
            tickTimeouts(server, AWAITING_REQUEST, now, REQUEST_TIMEOUT_MS);
            tickPendingKicks(server);
        }
        if (TRANSFERS.isEmpty()) return;
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
                it.remove();
                if (SprauteConfig.get().isAutoUpdateRequireRestart()) {
                    PENDING_KICK.put(player.getUUID(), KICK_DELAY_TICKS);
                }
            }
        }
    }

    private static void tickTimeouts(MinecraftServer server, Map<UUID, Long> waiting,
                                     long now, long timeoutMs) {
        if (waiting.isEmpty()) return;
        Iterator<Map.Entry<UUID, Long>> it = waiting.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (now - e.getValue() < timeoutMs) continue;
            it.remove();
            kick(player);
        }
    }

    private static void tickPendingKicks(MinecraftServer server) {
        if (PENDING_KICK.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> it = PENDING_KICK.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            int left = e.getValue() - 1;
            if (left > 0) {
                e.setValue(left);
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player != null) kick(player);
        }
    }

    private static void kick(ServerPlayer player) {
        if (player == null || player.connection == null) return;
        LOGGER.info("[Spraute Update] disconnect {} (restart required)",
                player.getGameProfile().getName());
        player.connection.disconnect(Component.translatable("spraute_engine.mod_update.kick"));
    }

    private static boolean sendNextChunk(ServerPlayer player, Transfer transfer) {
        int remaining = transfer.data.length - transfer.offset;
        if (remaining <= 0) return false;
        int n = Math.min(ModSelfUpdate.CHUNK_BYTES, remaining);
        byte[] slice = new byte[n];
        System.arraycopy(transfer.data, transfer.offset, slice, 0, n);
        int offset = transfer.offset;
        transfer.offset += n;
        boolean last = transfer.offset >= transfer.data.length;
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ModUpdateChunkPacket(transfer.data.length, offset, last, slice));
        return !last;
    }

    private static JarPayload loadJar() {
        synchronized (JAR_LOCK) {
            long now = System.currentTimeMillis();
            if (cachedJar != null && now - cachedAt < 30_000L) {
                return new JarPayload(cachedJar, cachedHash, cachedName);
            }
            Path jar = ModSelfUpdate.installedJar();
            if (jar == null) {
                LOGGER.warn("[Spraute Update] server jar is not a mods/*.jar — auto-update disabled here");
                return null;
            }
            try {
                long size = Files.size(jar);
                if (size <= 0 || size > ModSelfUpdate.MAX_JAR_BYTES) {
                    LOGGER.warn("[Spraute Update] jar size {} out of range", size);
                    return null;
                }
                byte[] data = Files.readAllBytes(jar);
                cachedJar = data;
                cachedHash = AssetSyncRules.sha256(data);
                cachedName = jar.getFileName().toString();
                cachedAt = now;
                return new JarPayload(cachedJar, cachedHash, cachedName);
            } catch (IOException e) {
                LOGGER.warn("[Spraute Update] cannot read jar: {}", e.toString());
                return null;
            }
        }
    }

    private record JarPayload(byte[] data, byte[] hash, String fileName) {}

    private static final class Transfer {
        final byte[] data;
        int offset;

        Transfer(byte[] data) {
            this.data = data;
        }
    }
}
