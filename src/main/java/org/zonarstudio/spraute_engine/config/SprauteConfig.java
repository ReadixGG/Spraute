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
    /** Per-file cap for asset sync and project upload (MB). Default 8. */
    public int assetSyncMaxFileMb = 8;
    /** Total cap for one asset-sync batch (MB). Default 150. */
    public int assetSyncMaxTotalMb = 150;
    /** Max files in one asset-sync batch. Default 2000. */
    public int assetSyncMaxFiles = 2000;
    /** ImGui editor UI scale (font + spacing). Client-only. Default 1.0. */
    public float editorUiScale = 1f;
    /** World move/rotate/scale handles in the editor viewport. Default on. */
    public Boolean editorShowGizmos = Boolean.TRUE;
    /** Frustum overlay for CAMERA nodes under a PLAYER. Default on. */
    public Boolean editorShowPlayerCameraGizmos = Boolean.TRUE;
    /** Route boxes, selection wireframes, hitboxes and other editor overlays. Default on. */
    public Boolean editorShowOverlays = Boolean.TRUE;
    /**
     * Client: request the server jar when versions differ.
     * Dedicated server: offer the jar to remote players.
     * Default on; missing key in old configs stays on via {@link #normalize()}.
     */
    public Boolean autoUpdateMod = Boolean.TRUE;
    /**
     * Server: mismatch cannot stay in the world — kick if they skip the download,
     * and disconnect after the jar is sent so they must quit and rejoin.
     */
    public Boolean autoUpdateRequireRestart = Boolean.FALSE;
    /** Client geo backend: auto, gpu or compat. */
    public String geoRenderBackend = "auto";
    public Boolean geoLodEnabled = Boolean.TRUE;
    public Boolean geoRenderStats = Boolean.FALSE;
    public float geoCullingMargin = 0.5f;
    /** 0 keeps vanilla distance; otherwise blocks from the active camera. */
    public float geoMaxRenderDistance = 0f;
    public float geoMinPixels = 1.5f;
    public int geoMidAnimationInterval = 2;
    public int geoFarAnimationInterval = 4;

    public static final int MIN_FILE_MB = 1;
    public static final int MAX_FILE_MB = 64;
    public static final int MIN_TOTAL_MB = 8;
    public static final int MAX_TOTAL_MB = 2048;
    public static final int MIN_FILES = 50;
    public static final int MAX_FILES = 10000;
    public static final float MIN_UI_SCALE = 0.75f;
    public static final float MAX_UI_SCALE = 2.0f;
    public static final float UI_SCALE_STEP = 0.05f;

    public static void load(Path gameDir) {
        configPath = gameDir.resolve("config").resolve("spraute_engine").resolve("config.json");
        try {
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath, StandardCharsets.UTF_8);
                INSTANCE = GSON.fromJson(json, SprauteConfig.class);
                if (INSTANCE == null) INSTANCE = new SprauteConfig();
                INSTANCE.normalize();
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
            if (INSTANCE == null) INSTANCE = new SprauteConfig();
            INSTANCE.normalize();
            if (configPath == null) {
                LOGGER.warn("Config path unset, skip disk save");
                return;
            }
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
            INSTANCE = new SprauteConfig();
        }
        INSTANCE.normalize();
        return INSTANCE;
    }

    public void normalize() {
        assetSyncMaxFileMb = clamp(assetSyncMaxFileMb, MIN_FILE_MB, MAX_FILE_MB);
        assetSyncMaxTotalMb = clamp(assetSyncMaxTotalMb, MIN_TOTAL_MB, MAX_TOTAL_MB);
        if (assetSyncMaxTotalMb < assetSyncMaxFileMb) {
            assetSyncMaxTotalMb = assetSyncMaxFileMb;
        }
        assetSyncMaxFiles = clamp(assetSyncMaxFiles, MIN_FILES, MAX_FILES);
        if (nameFormat == null || nameFormat.isBlank()) nameFormat = "[$Name]:";
        if (nameColor == null || nameColor.isBlank()) nameColor = "#FFFFFF";
        if (editorUiScale <= 0f || Float.isNaN(editorUiScale)) editorUiScale = 1f;
        editorUiScale = clampFloat(editorUiScale, MIN_UI_SCALE, MAX_UI_SCALE);
        editorUiScale = Math.round(editorUiScale / UI_SCALE_STEP) * UI_SCALE_STEP;
        editorUiScale = clampFloat(editorUiScale, MIN_UI_SCALE, MAX_UI_SCALE);
        if (autoUpdateMod == null) autoUpdateMod = Boolean.TRUE;
        if (autoUpdateRequireRestart == null) autoUpdateRequireRestart = Boolean.FALSE;
        if (editorShowGizmos == null) editorShowGizmos = Boolean.TRUE;
        if (editorShowPlayerCameraGizmos == null) editorShowPlayerCameraGizmos = Boolean.TRUE;
        if (editorShowOverlays == null) editorShowOverlays = Boolean.TRUE;
        if (geoRenderBackend == null) geoRenderBackend = "auto";
        geoRenderBackend = geoRenderBackend.trim().toLowerCase(java.util.Locale.ROOT);
        if (!geoRenderBackend.equals("auto") && !geoRenderBackend.equals("gpu")
                && !geoRenderBackend.equals("compat")) geoRenderBackend = "auto";
        if (geoLodEnabled == null) geoLodEnabled = Boolean.TRUE;
        if (geoRenderStats == null) geoRenderStats = Boolean.FALSE;
        geoCullingMargin = clampFloat(geoCullingMargin, 0f, 8f);
        geoMaxRenderDistance = clampFloat(geoMaxRenderDistance, 0f, 1024f);
        geoMinPixels = clampFloat(geoMinPixels, 0.25f, 16f);
        geoMidAnimationInterval = clamp(geoMidAnimationInterval, 1, 8);
        geoFarAnimationInterval = clamp(geoFarAnimationInterval, 1, 20);
    }

    public long maxFileBytes() {
        normalize();
        return assetSyncMaxFileMb * 1024L * 1024L;
    }

    public long maxTotalBytes() {
        normalize();
        return assetSyncMaxTotalMb * 1024L * 1024L;
    }

    public int maxFiles() {
        normalize();
        return assetSyncMaxFiles;
    }

    public boolean isAutoUpdateModEnabled() {
        normalize();
        return autoUpdateMod == null || autoUpdateMod;
    }

    public boolean isAutoUpdateRequireRestart() {
        normalize();
        return Boolean.TRUE.equals(autoUpdateRequireRestart);
    }

    public boolean isEditorShowGizmos() {
        normalize();
        return editorShowGizmos == null || editorShowGizmos;
    }

    public boolean isEditorShowPlayerCameraGizmos() {
        normalize();
        return editorShowPlayerCameraGizmos == null || editorShowPlayerCameraGizmos;
    }

    public boolean isEditorShowOverlays() {
        normalize();
        return editorShowOverlays == null || editorShowOverlays;
    }

    public boolean isGeoLodEnabled() {
        normalize();
        return geoLodEnabled == null || geoLodEnabled;
    }

    public boolean isGeoRenderStatsEnabled() {
        normalize();
        return Boolean.TRUE.equals(geoRenderStats);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clampFloat(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
