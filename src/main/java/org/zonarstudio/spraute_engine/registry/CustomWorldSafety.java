package org.zonarstudio.spraute_engine.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.Spraute_engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Keeps players out of removed {@code create world} dimensions and helps saves load safely. */
public final class CustomWorldSafety {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CustomWorldSafety() {}

    public static boolean isSprauteDimension(ResourceLocation location) {
        return location != null && Spraute_engine.MODID.equals(location.getNamespace());
    }

    public static boolean isOrphanSprauteDimension(ResourceLocation location) {
        if (!isSprauteDimension(location)) return false;
        CustomWorldRegistry.ensureParsed();
        return !CustomWorldRegistry.hasWorld(location.getPath());
    }

    public static boolean rescuePlayerIfNeeded(ServerPlayer player) {
        if (player == null || player.server == null) return false;
        ResourceLocation dim = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player).dimension().location();
        if (!isOrphanSprauteDimension(dim)) return false;

        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        float angle = overworld.getSharedSpawnAngle();

        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, angle, 0);
        player.setRespawnPosition(overworld.dimension(), spawn, angle, true, false);
        player.sendSystemMessage(Component.translatable("spraute_engine.warn.orphan_dimension_rescue", dim.toString()));
        LOGGER.warn("[Spraute Engine] Moved {} out of removed dimension {}", player.getName().getString(), dim);
        return true;
    }

    public static void rescueAllPlayers(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            rescuePlayerIfNeeded(player);
        }
    }

    /** Scan save folders for spraute_engine dimensions that are no longer declared in scripts. */
    public static Set<String> discoverOrphanWorldIds(Path worldSaveRoot) {
        Set<String> found = new HashSet<>();
        if (worldSaveRoot == null || !Files.isDirectory(worldSaveRoot)) return found;

        Path dimDir = worldSaveRoot.resolve("DIM-1");
        if (!Files.isDirectory(dimDir)) return found;

        String prefix = Spraute_engine.MODID + "$";
        try (var stream = Files.newDirectoryStream(dimDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                String name = entry.getFileName().toString();
                if (!name.startsWith(prefix)) continue;
                String worldId = name.substring(prefix.length());
                if (!worldId.isEmpty() && !CustomWorldRegistry.hasWorld(worldId)) {
                    found.add(worldId);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[Spraute Engine] Failed to scan orphan dimensions in {}: {}", dimDir, e.getMessage());
        }
        return found;
    }

    public static void refreshOrphansFromSaves(Path gameDir) {
        if (gameDir == null) return;
        Path saves = gameDir.resolve("saves");
        if (!Files.isDirectory(saves)) return;
        try (var stream = Files.list(saves)) {
            for (Path save : stream.toList()) {
                if (!Files.isDirectory(save)) continue;
                CustomWorldRegistry.addOrphanWorldIds(discoverOrphanWorldIds(save));
            }
        } catch (Exception e) {
            LOGGER.warn("[Spraute Engine] Failed to scan saves for orphan dimensions: {}", e.getMessage());
        }
    }

    public static void refreshOrphansFromServer(MinecraftServer server) {
        if (server == null) return;
        CustomWorldRegistry.addOrphanWorldIds(discoverOrphanWorldIds(server.getWorldPath(LevelResource.ROOT)));
    }
}
