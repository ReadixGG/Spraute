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
 *   pack.mcmeta
 * </pre>
 */
public final class WorkspaceInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ROOT = "spraute_engine";
    private static final String BUNDLE_PREFIX = "/default_assets/";

    private static final List<String> DIRECTORIES = List.of(
            "scripts",
            "geo",
            "animations",
            "textures/entity",
            "textures/particle",
            "sounds"
    );

    private static final List<String> DEFAULT_FILES = List.of(
            "geo/defolt.geo.json",
            "animations/npc_classic.animation.json",
            "textures/entity/defolt.png"
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
            for (String relative : DEFAULT_FILES) {
                copyBundledIfMissing(root, relative);
            }
            LOGGER.info("[Spraute Engine] Workspace ready at {}", root.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[Spraute Engine] Failed to initialize workspace at {}: {}", root, e.getMessage());
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
}
