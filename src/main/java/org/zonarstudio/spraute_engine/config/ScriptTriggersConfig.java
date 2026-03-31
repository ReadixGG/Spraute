package org.zonarstudio.spraute_engine.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Config for automatic script triggers:
 * - on_join: script to run when any player joins
 * - on_first_join: script to run when player joins for the first time
 * - after: map of script -> next script to run when that script finishes
 */
public class ScriptTriggersConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;

    public String on_join = "";
    public String on_first_join = "";
    public Map<String, String> after = new HashMap<>();

    public static void load(Path gameDir) {
        configPath = gameDir.resolve("config").resolve("spraute_engine").resolve("triggers.json");
        try {
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath, StandardCharsets.UTF_8);
                ScriptTriggersConfig loaded = GSON.fromJson(json, ScriptTriggersConfig.class);
                INSTANCE = loaded != null ? loaded : new ScriptTriggersConfig();
                if (INSTANCE.after == null) INSTANCE.after = new HashMap<>();
            } else {
                INSTANCE = new ScriptTriggersConfig();
                save();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load script triggers", e);
            INSTANCE = new ScriptTriggersConfig();
        }
    }

    private static ScriptTriggersConfig INSTANCE;

    public static ScriptTriggersConfig get() {
        return INSTANCE != null ? INSTANCE : new ScriptTriggersConfig();
    }

    public static void save() {
        try {
            if (configPath != null && configPath.getParent() != null && INSTANCE != null) {
                Files.createDirectories(configPath.getParent());
                String json = GSON.toJson(INSTANCE);
                Files.writeString(configPath, json, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save script triggers", e);
        }
    }
}
