package org.zonarstudio.spraute_engine.resource;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Path;

/**
 * Single source of truth for the {@code spraute_engine/} workspace on disk.
 * While a server is running this is always {@code server.getServerDirectory()} —
 * dedicated hosts, TLauncher version folders, and integrated SP stay consistent.
 */
public final class SprautePaths {
    public static final String ROOT = "spraute_engine";

    private SprautePaths() {}

    /** Game/server directory that owns {@code spraute_engine/}. */
    public static Path gameDir() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.getServerDirectory() != null) {
            return server.getServerDirectory().toPath();
        }
        return FMLPaths.GAMEDIR.get();
    }

    public static Path gameDir(MinecraftServer server) {
        if (server != null && server.getServerDirectory() != null) {
            return server.getServerDirectory().toPath();
        }
        return gameDir();
    }

    public static Path workspace() {
        return gameDir().resolve(ROOT);
    }

    public static Path workspace(MinecraftServer server) {
        return gameDir(server).resolve(ROOT);
    }

    public static Path scriptsDir() {
        return workspace().resolve("scripts");
    }

    public static Path scenesDir() {
        return workspace().resolve("scenes");
    }

    public static Path prefabsDir() {
        return workspace().resolve("prefabs");
    }

    public static Path pluginsDir() {
        return workspace().resolve("plugins");
    }
}
