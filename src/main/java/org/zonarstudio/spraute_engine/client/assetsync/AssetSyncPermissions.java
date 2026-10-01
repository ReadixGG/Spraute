package org.zonarstudio.spraute_engine.client.assetsync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Remembers "always allow this server" for asset sync. */
public final class AssetSyncPermissions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data;
    private static Path path;

    public static final class Data {
        public List<String> alwaysAllow = new ArrayList<>();
    }

    private AssetSyncPermissions() {}

    public static boolean isAlwaysAllowed(String serverKey) {
        if (serverKey == null || serverKey.isBlank() || "unknown".equals(serverKey)) return false;
        load();
        String key = serverKey.trim().toLowerCase(Locale.ROOT);
        for (String s : data.alwaysAllow) {
            if (key.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    public static void remember(String serverKey) {
        if (serverKey == null || serverKey.isBlank() || "unknown".equals(serverKey)) return;
        load();
        String key = serverKey.trim().toLowerCase(Locale.ROOT);
        if (!isAlwaysAllowed(key)) {
            data.alwaysAllow.add(key);
            save();
        }
    }

    private static void load() {
        if (data != null) return;
        path = FMLPaths.GAMEDIR.get().resolve("config").resolve("spraute_engine").resolve("asset_sync.json");
        try {
            if (Files.exists(path)) {
                data = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Data.class);
            }
        } catch (Exception e) {
            LOGGER.warn("[Spraute AssetSync] failed to read permissions: {}", e.getMessage());
        }
        if (data == null) data = new Data();
        if (data.alwaysAllow == null) data.alwaysAllow = new ArrayList<>();
    }

    private static void save() {
        load();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[Spraute AssetSync] failed to save permissions: {}", e.getMessage());
        }
    }
}
