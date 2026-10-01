package org.zonarstudio.spraute_engine.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Prefabs: a saved scene-node subtree in {@code spraute_engine/prefabs/<name>.prefab.json}.
 * Created by dragging a node from the Hierarchy into the Project panel; instantiated
 * back by dragging the prefab into the Hierarchy or from scripts via {@code prefabSpawn}.
 */
public final class PrefabRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String EXTENSION = ".prefab.json";

    private PrefabRepository() {}

    public static Path prefabsDir(Path gameDir) {
        return gameDir.resolve("spraute_engine").resolve("prefabs");
    }

    public static Path prefabPath(Path gameDir, String name) {
        return prefabsDir(gameDir).resolve(sanitize(name) + EXTENSION);
    }

    /** File-safe prefab name: lowercase, spaces to _, only [a-z0-9_-]. */
    public static String sanitize(String name) {
        String s = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') out.append(c);
        }
        return out.length() == 0 ? "prefab" : out.toString();
    }

    public static List<String> listIds(Path gameDir) {
        List<String> result = new ArrayList<>();
        Path dir = prefabsDir(gameDir);
        if (!Files.isDirectory(dir)) return result;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> {
                        String fn = p.getFileName().toString();
                        result.add(fn.substring(0, fn.length() - EXTENSION.length()));
                    });
        } catch (IOException e) {
            LOGGER.warn("[Spraute Prefab] Failed to list prefabs: {}", e.getMessage());
        }
        return result;
    }

    /** Saves the subtree as a prefab; returns the unique name actually used. */
    public static String save(Path gameDir, SceneNode node, String preferredName) throws IOException {
        String base = sanitize(preferredName);
        Path dir = prefabsDir(gameDir);
        Files.createDirectories(dir);
        String name = base;
        int suffix = 2;
        while (Files.exists(dir.resolve(name + EXTENSION))) {
            name = base + "-" + suffix++;
        }
        writePrefabFile(dir.resolve(name + EXTENSION), node, name);
        LOGGER.info("[Spraute Prefab] Saved prefab '{}'", name);
        return name;
    }

    /**
     * Overwrites an existing prefab file with the current node subtree (keeps the same asset id).
     * Creates the file if missing. Returns the sanitized name.
     */
    public static String overwrite(Path gameDir, SceneNode node, String prefabName) throws IOException {
        String name = sanitize(prefabName);
        Path dir = prefabsDir(gameDir);
        Files.createDirectories(dir);
        writePrefabFile(dir.resolve(name + EXTENSION), node, name);
        LOGGER.info("[Spraute Prefab] Updated prefab '{}'", name);
        return name;
    }

    private static void writePrefabFile(Path file, SceneNode node, String assetName) throws IOException {
        // Persist without a live instance link; instantiate() re-binds prefabId.
        String previous = node.prefabId;
        node.prefabId = "";
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(node), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | UnsupportedOperationException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            node.prefabId = previous == null || previous.isBlank() ? assetName : previous;
        }
    }

    /** Loads a prefab subtree; null if missing/broken. Ids are as stored — copy with new ids before inserting. */
    public static SceneNode load(Path gameDir, String name) {
        Path file = prefabPath(gameDir, name);
        if (!Files.isRegularFile(file)) {
            file = org.zonarstudio.spraute_engine.resource.WorkspaceContent.resolve(
                    "prefabs/" + sanitize(name) + EXTENSION);
        }
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            SceneNode node = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), SceneNode.class);
            if (node == null || node.type == null) return null;
            SceneNode.hardenChildrenLists(node);
            return node;
        } catch (Exception e) {
            LOGGER.warn("[Spraute Prefab] Failed to load prefab '{}': {}", name, e.getMessage());
            return null;
        }
    }

    /** Fresh instance ready to insert into a scene (new ids for the whole subtree). */
    public static SceneNode instantiate(Path gameDir, String name) {
        SceneNode stored = load(gameDir, name);
        if (stored == null) return null;
        SceneNode fresh = stored.deepCopyWithNewIds();
        fresh.prefabId = sanitize(name);
        return fresh;
    }

    public static boolean exists(Path gameDir, String name) {
        if (name == null || name.isBlank()) return false;
        if (Files.isRegularFile(prefabPath(gameDir, name))) return true;
        return org.zonarstudio.spraute_engine.resource.WorkspaceContent.exists(
                "prefabs/" + sanitize(name) + EXTENSION);
    }
}
