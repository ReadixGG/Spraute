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

/**
 * Loads and saves {@code spraute_engine/scenes/<id>.scene.json}.
 * Saves go through a temp file so a crash never truncates a scene.
 */
public final class SceneRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String EXTENSION = ".scene.json";

    private SceneRepository() {}

    public static Path scenesDir(Path gameDir) {
        return gameDir.resolve("spraute_engine").resolve("scenes");
    }

    public static Path scenePath(Path gameDir, String sceneId) {
        return scenesDir(gameDir).resolve(sceneId + EXTENSION);
    }

    /**
     * Disk scene if present, otherwise the same id from a mounted story JAR.
     * Editor listings should keep using {@link #listScenes} / {@link #listSceneIds}.
     */
    public static Path findPlayableScene(Path gameDir, String sceneId) {
        if (sceneId == null || sceneId.isBlank()) return null;
        Path disk = scenePath(gameDir, sceneId);
        if (Files.isRegularFile(disk)) return disk;
        return org.zonarstudio.spraute_engine.resource.WorkspaceContent.resolve("scenes/" + sceneId + EXTENSION);
    }

    /** Workspace scenes first, then story-pack scenes not shadowed by the same id. */
    public static List<Path> listPlayableScenes(Path gameDir) {
        return org.zonarstudio.spraute_engine.resource.WorkspaceContent.listSceneFiles();
    }

    public static List<String> listPlayableSceneIds(Path gameDir) {
        List<String> result = new ArrayList<>();
        for (Path path : listPlayableScenes(gameDir)) {
            String fileName = path.getFileName().toString();
            if (fileName.endsWith(EXTENSION)) {
                result.add(fileName.substring(0, fileName.length() - EXTENSION.length()));
            }
        }
        return result;
    }

    public static List<Path> listScenes(Path gameDir) {
        List<Path> result = new ArrayList<>();
        Path dir = scenesDir(gameDir);
        try {
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(result::add);
            }
        } catch (IOException e) {
            LOGGER.warn("[Spraute Scene] Failed to list scenes: {}", e.getMessage());
        }
        return result;
    }

    /** {@code scenes/foo.scene.json} → {@code foo}, otherwise {@code null}. */
    public static String sceneIdFromWorkspacePath(String rel) {
        if (rel == null || rel.isBlank()) return null;
        String n = rel.replace('\\', '/');
        if (n.startsWith("/")) n = n.substring(1);
        if (!n.regionMatches(true, 0, "scenes/", 0, 7)) return null;
        if (!n.regionMatches(true, n.length() - EXTENSION.length(), EXTENSION, 0, EXTENSION.length())) {
            return null;
        }
        String id = n.substring(7, n.length() - EXTENSION.length());
        int slash = id.lastIndexOf('/');
        if (slash >= 0) id = id.substring(slash + 1);
        return id.isBlank() ? null : id;
    }

    /** Scene ids without the {@code .scene.json} suffix, for commands/UI. */
    public static List<String> listSceneIds(Path gameDir) {
        List<String> result = new ArrayList<>();
        for (Path path : listScenes(gameDir)) {
            String fileName = path.getFileName().toString();
            if (fileName.endsWith(EXTENSION)) {
                result.add(fileName.substring(0, fileName.length() - EXTENSION.length()));
            }
        }
        return result;
    }

    public static SceneDocument load(Path file) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        SceneDocument doc = GSON.fromJson(json, SceneDocument.class);
        if (doc == null) throw new IOException("Empty scene file: " + file);
        if (doc.root == null) doc.root = SceneNode.sceneRoot(doc.id);
        SceneNode.hardenChildrenLists(doc.root);
        doc.ensurePlayersNode();
        migrateToWorldContainers(doc);
        migrateWorldCanvases(doc);
        normalizeRouteLayouts(doc);
        SceneRouteStorage.loadRoutes(doc, file);
        SceneNode.hardenChildrenLists(doc.root);
        SceneNodeIds.ensureAllIds(doc);
        return doc;
    }

    public static void saveAtomic(SceneDocument doc, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        SceneRouteStorage.saveRoutes(doc, file);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(persistentCopy(doc, true)), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UnsupportedOperationException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String toJson(SceneDocument doc) {
        return GSON.toJson(persistentCopy(doc, false));
    }

    public static SceneDocument fromJson(String json) {
        SceneDocument doc = GSON.fromJson(json, SceneDocument.class);
        if (doc != null) {
            if (doc.root != null) SceneNode.hardenChildrenLists(doc.root);
            doc.ensurePlayersNode();
            migrateToWorldContainers(doc);
            migrateWorldCanvases(doc);
            normalizeRouteLayouts(doc);
        }
        return doc;
    }

    /**
     * One-time migration for scenes saved before GLOBAL_WORLD/LOCAL_WORLD existed (format &lt; 4):
     * every existing root child (besides the PLAYERS system container) becomes LOCAL_WORLD
     * content — i.e. it keeps behaving exactly as it did before (today, everything is one shared
     * simulation anyway; LOCAL_WORLD is where that same content lives once per-player instancing
     * ships). A child type LOCAL_WORLD can't host (resource defs — block/item/craft/…) falls back
     * to GLOBAL_WORLD instead, since those are register-once definitions, not per-player content.
     * No-op for scenes already on format 4+ (checked against the document's OWN pre-bump format,
     * since {@link #normalizeRouteLayouts} unconditionally advances it afterwards).
     */
    private static void migrateToWorldContainers(SceneDocument doc) {
        if (doc == null || doc.root == null) return;
        SceneNode global = doc.ensureGlobalWorldNode();
        SceneNode local = doc.ensureLocalWorldNode();
        if (doc.format >= 4) return;
        List<SceneNode> toMove = new ArrayList<>();
        for (SceneNode child : doc.root.children) {
            if (child.type == SceneNodeType.PLAYERS || child.type == SceneNodeType.GLOBAL_WORLD
                    || child.type == SceneNodeType.LOCAL_WORLD) {
                continue;
            }
            toMove.add(child);
        }
        doc.root.children.removeAll(toMove);
        for (SceneNode child : toMove) {
            if (SceneNodeType.LOCAL_WORLD.allowedChildTypes().contains(child.type)) {
                local.children.add(child);
            } else {
                global.children.add(child);
            }
        }
        if (!toMove.isEmpty()) {
            LOGGER.info("[Spraute Scene] Migrated {} root node(s) of '{}' into Локальный мир (format {} → 4)",
                    toMove.size(), doc.id, doc.format);
        }
    }

    /**
     * Format &lt; 5: a 3D canvas used to be a {@code worldCanvas} CUSTOM node whose single CANVAS
     * child held the widgets. The CANVAS node itself now carries the world render mode, so the
     * wrapper collapses: the child canvas takes the wrapper's place (and its world transform),
     * keeps its own widgets, and the wrapper's customData becomes the canvas' world fields.
     */
    private static void migrateWorldCanvases(SceneDocument doc) {
        if (doc == null || doc.root == null || doc.format >= 5) return;
        int[] migrated = {0};
        collapseWorldCanvases(doc.root, migrated);
        if (migrated[0] > 0) {
            LOGGER.info("[Spraute Scene] Migrated {} 3D canvas(es) of '{}' to world-space CANVAS nodes (format {} → 5)",
                    migrated[0], doc.id, doc.format);
        }
    }

    private static void collapseWorldCanvases(SceneNode parent, int[] migrated) {
        for (int i = 0; i < parent.children.size(); i++) {
            SceneNode child = parent.children.get(i);
            if (isLegacyWorldCanvas(child)) {
                SceneNode canvas = null;
                for (SceneNode inner : child.children) {
                    if (inner.type == SceneNodeType.CANVAS) {
                        canvas = inner;
                        break;
                    }
                }
                if (canvas == null) {
                    // Wrapper without content: becomes an empty world canvas of its own.
                    canvas = SceneNode.worldCanvas(child.name);
                    canvas.id = child.id;
                }
                canvas.name = child.name;
                canvas.transform = child.transform;      // world placement lived on the wrapper
                canvas.active = child.active;
                canvas.locked = child.locked;
                canvas.canvasWorldSpace = true;
                canvas.canvasAutoShow = false;
                // A world panel is sized in its own pixels. A canvas that was sized in percent
                // of the screen has no meaningful pixel size to keep, so it takes the default.
                if (canvas.uiPercentW || canvas.uiCanvasW < 16) canvas.uiCanvasW = 320;
                if (canvas.uiPercentH || canvas.uiCanvasH < 16) canvas.uiCanvasH = 180;
                canvas.uiPercentX = false;
                canvas.uiPercentY = false;
                canvas.uiPercentW = false;
                canvas.uiPercentH = false;
                canvas.canvasWorldWidth = (float) legacyNumber(child, "width", 2);
                canvas.canvasWorldHeight = (float) legacyNumber(child, "height", 0);
                canvas.canvasWorldResolution = (float) legacyNumber(child, "resolution", 2);
                canvas.canvasWorldMaxDistance = (float) legacyNumber(child, "maxDistance", 48);
                canvas.canvasWorldRefresh = (int) Math.round(legacyNumber(child, "refresh", 5));
                Object orientation = child.customData == null ? null : child.customData.get("orientation");
                canvas.canvasWorldOrientation = orientation == null || String.valueOf(orientation).isBlank()
                        ? "billboard" : String.valueOf(orientation);
                parent.children.set(i, canvas);
                migrated[0]++;
                collapseWorldCanvases(canvas, migrated);
                continue;
            }
            collapseWorldCanvases(child, migrated);
        }
    }

    private static boolean isLegacyWorldCanvas(SceneNode node) {
        return node != null && node.type == SceneNodeType.CUSTOM
                && org.zonarstudio.spraute_engine.scene.custom.WorldCanvasNodeType.ID.equals(node.customTypeId);
    }

    private static double legacyNumber(SceneNode node, String key, double def) {
        Object value = node.customData == null ? null : node.customData.get(key);
        if (value instanceof Number n) return n.doubleValue();
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static void normalizeRouteLayouts(SceneDocument doc) {
        if (doc == null || doc.root == null) return;
        boolean legacy = doc.format < 3;
        doc.walk((node, ignored) -> {
            if (node.type != SceneNodeType.ROUTE) return;
            if (legacy) {
                node.routeLayoutSpacing = 2f;
                node.routeLayoutAlignYaw = true;
            }
            node.routeLayoutSpacing = Math.max(0f, node.routeLayoutSpacing);
            node.routeLayoutAnchor = SceneRouteLayout.normalizeAnchor(node.routeLayoutAnchor);
            node.routeLayoutOverflow = SceneRouteLayout.normalizeOverflow(node.routeLayoutOverflow);
        });
        doc.format = SceneDocument.CURRENT_FORMAT;
    }

    private static SceneDocument persistentCopy(SceneDocument source, boolean forDisk) {
        SceneDocument copy = GSON.fromJson(GSON.toJson(source), SceneDocument.class);
        if (copy == null) return new SceneDocument();
        if (copy.root != null) SceneNode.hardenChildrenLists(copy.root);
        SceneNode players = copy.ensurePlayersNode();
        // Live PLAYER cameras/post are per-player and not persisted. Do not copy the
        // first online player onto PLAYERS defaults — that replayed one pose to everyone.
        players.children.removeIf(child -> child.type == SceneNodeType.PLAYER);
        if (forDisk) {
            copy.walk((node, ignored) -> {
                if (node.type == SceneNodeType.ROUTE) {
                    node.children.removeIf(child -> child.type == SceneNodeType.ROUTE_POINT);
                }
            });
        }
        return copy;
    }
}
