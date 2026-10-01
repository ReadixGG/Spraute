package org.zonarstudio.spraute_engine.resource;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Ensures the external {@code spraute_engine/} workspace exists under the game directory
 * and ships default model / animation / texture files when they are missing.
 *
 * <p>Layout matches Spraute Studio ({@code app/electron-main.cjs}):
 * <pre>
 * spraute_engine/
 *   scripts/
 *   geo/
 *   animations/
 *   textures/entity/
 *   textures/particle/
 *   sounds/
 *   videos/
 *   lang/
 *   pack.mcmeta
 * </pre>
 */
public final class WorkspaceInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ROOT = "spraute_engine";
    private static final String BUNDLE_PREFIX = "/default_assets/";

    private static final List<String> DIRECTORIES = List.of(
            "scripts",
            "scenes",
            "prefabs",
            "geo",
            "animations",
            "textures/entity",
            "textures/particle",
            "sounds",
            "videos",
            "cache/videos",
            "cache/audio",
            "tools",
            "plugins",
            "lang",
            "fonts",
            "routes"
    );

    private static final List<String> DEFAULT_FILES = List.of(
            SprauteDefaultNpcAssets.MODEL,
            SprauteDefaultNpcAssets.ANIMATION,
            SprauteDefaultNpcAssets.TEXTURE,
            "scripts/examples/trade_shop.spr",
            "fonts/NotoSans-Regular.ttf"
    );

    private static final List<String> DEFAULT_SCENES = List.of(
            "test_hitbox.scene.json",
            "test_combat.scene.json",
            "test_camera.scene.json",
            "billboard_test.scene.json",
            "story_dialog_test.scene.json"
    );

    private WorkspaceInitializer() {}

    /** Idempotent: creates folders and copies bundled defaults only when destination files are absent. */
    public static void ensureWorkspace(Path gameDir) {
        Path root = gameDir.resolve(ROOT);
        try {
            for (String dir : DIRECTORIES) {
                Files.createDirectories(root.resolve(dir));
            }
            ensurePackMcmeta(root);
            ensureSampleLangFiles(root);
            for (String relative : DEFAULT_FILES) {
                copyBundledIfMissing(root, relative);
            }
            for (String sceneFile : DEFAULT_SCENES) {
                copyBundledSceneIfMissing(root, sceneFile);
            }
            BundledPluginUnpacker.unpackInto(root);
            StoryPackInstaller.installAll(gameDir);
            LOGGER.info("[Spraute Engine] Workspace ready at {}", root.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[Spraute Engine] Failed to initialize workspace at {}: {}", root, e.getMessage());
        }
    }

    private static void ensureSampleLangFiles(Path root) throws IOException {
        Path lang = root.resolve("lang");
        Files.createDirectories(lang);
        Path meta = lang.resolve("_meta.json");
        if (!Files.exists(meta)) {
            Files.writeString(meta, """
                    {
                      "fallback": "en"
                    }
                    """);
        }
        Path en = lang.resolve("en.json");
        Path ru = lang.resolve("ru.json");
        if (!Files.exists(en)) {
            Files.writeString(en, """
                    {
                      "welcome": "Welcome, {0}!",
                      "goodbye": "Goodbye!",
                      "quest": {
                        "start": "Talk to {npc}"
                      }
                    }
                    """);
        }
        if (!Files.exists(ru)) {
            Files.writeString(ru, """
                    {
                      "welcome": "Добро пожаловать, {0}!",
                      "goodbye": "До встречи!",
                      "quest": {
                        "start": "Поговори с {npc}"
                      }
                    }
                    """);
        }
    }

    private static void ensurePackMcmeta(Path root) throws IOException {
        Path mcmeta = root.resolve("pack.mcmeta");
        if (Files.exists(mcmeta)) {
            return;
        }
        //? if >=1.20.1 {
        String json = """
                {
                  "pack": {
                    "pack_format": 15,
                    "description": "Spraute Engine External Assets"
                  }
                }
                """;
        //?} else {
        /*String json = """
                {
                  "pack": {
                    "pack_format": 9,
                    "description": "Spraute Engine External Assets"
                  }
                }
                """;
        *///?}
        Files.writeString(mcmeta, json);
    }

    private static void copyBundledIfMissing(Path root, String relativePath) throws IOException {
        Path dest = root.resolve(relativePath);
        if (Files.exists(dest)) {
            return;
        }
        String resourcePath = BUNDLE_PREFIX + relativePath.replace('\\', '/');
        try (InputStream in = WorkspaceInitializer.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.warn("[Spraute Engine] Bundled default asset not found in mod jar: {}", resourcePath);
                return;
            }
            Files.createDirectories(dest.getParent());
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[Spraute Engine] Installed default asset: {}", relativePath);
        }
    }

    private static void copyBundledSceneIfMissing(Path root, String sceneFileName) throws IOException {
        Path dest = root.resolve("scenes").resolve(sceneFileName);
        if (Files.exists(dest)) {
            return;
        }
        String resourcePath = "/default_scenes/" + sceneFileName;
        try (InputStream in = WorkspaceInitializer.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.warn("[Spraute Engine] Bundled default scene not found in mod jar: {}", resourcePath);
                return;
            }
            Files.createDirectories(dest.getParent());
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[Spraute Engine] Installed default scene: scenes/{}", sceneFileName);
        }
    }
}
