package org.zonarstudio.spraute_engine.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SprauteConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SprauteConfig INSTANCE;
    private static Path configPath;

    // Config fields
    public String chatType = "standard";
    public String nameFormat = "[$Name]:";
    public String nameColor = "#FFFFFF"; // Default white

    public static void load(Path gameDir) {
        configPath = gameDir.resolve("config").resolve("spraute_engine").resolve("config.json");
        try {
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath, StandardCharsets.UTF_8);
                INSTANCE = GSON.fromJson(json, SprauteConfig.class);
            } else {
                INSTANCE = new SprauteConfig();
                save();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load config", e);
            INSTANCE = new SprauteConfig();
        }
    }

    public static void save() {
        try {
            if (configPath.getParent() != null) {
                Files.createDirectories(configPath.getParent());
            }
            String json = GSON.toJson(INSTANCE);
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public static SprauteConfig get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Config not initialized!");
        }
        return INSTANCE;
    }
}
