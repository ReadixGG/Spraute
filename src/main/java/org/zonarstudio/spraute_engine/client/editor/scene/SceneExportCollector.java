package org.zonarstudio.spraute_engine.client.editor.scene;

import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneRepository;
import org.zonarstudio.spraute_engine.scene.resource.SceneBlockDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneItemDef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walks a scene document (and referenced scripts) and collects workspace-relative
 * paths under {@code spraute_engine/} needed to run the scene elsewhere.
 */
public final class SceneExportCollector {
    private SceneExportCollector() {}

    private static final Pattern IMPORT = Pattern.compile(
            "import\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*\\)");
    private static final Pattern ASSET_QUOTE = Pattern.compile(
            "[\"']((?:geo|textures|animations|models|scripts)/[^\"'\\s]+)[\"']");
    private static final Pattern LOOSE_ASSET = Pattern.compile(
            "(?<![\\w./-])((?:geo|textures|animations|models)/[a-z0-9_./-]+\\.(?:geo\\.json|animation\\.json|png|jpg|jpeg|json))",
            Pattern.CASE_INSENSITIVE);

    public record Result(
            /** Relative paths under spraute_engine/, existing on disk. */
            List<String> paths,
            /** Expected relative paths that were referenced but missing. */
            List<String> missing,
            /** Counts by category for UI. */
            Map<String, Integer> counts
    ) {}

    public static Result collect(Path gameDir, SceneDocument doc) {
        Path root = gameDir.resolve("spraute_engine").normalize();
        LinkedHashSet<String> found = new LinkedHashSet<>();
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        LinkedHashSet<String> scannedScripts = new LinkedHashSet<>();
        Queue<String> scriptQueue = new ArrayDeque<>();

        if (doc != null) {
            String sceneId = doc.id != null && !doc.id.isBlank() ? doc.id : "scene";
            Path sceneFile = SceneRepository.scenePath(gameDir, sceneId);
            addExisting(root, "scenes/" + sceneId + SceneRepository.EXTENSION, sceneFile, found, missing);

            if (doc.root != null) walkNode(doc.root, root, found, missing, scriptQueue);
        }

        while (!scriptQueue.isEmpty()) {
            String scriptName = scriptQueue.poll();
            if (scriptName == null || scriptName.isBlank()) continue;
            String base = normalizeScriptName(scriptName);
            if (base.isEmpty() || !scannedScripts.add(base)) continue;

            Path spr = root.resolve("scripts").resolve(base + ".spr");
            Path sprv = root.resolve("scripts").resolve(base + ".sprv");
            boolean hasSpr = Files.isRegularFile(spr);
            boolean hasSprv = Files.isRegularFile(sprv);
            if (hasSpr) found.add("scripts/" + base + ".spr");
            if (hasSprv) found.add("scripts/" + base + ".sprv");
            if (!hasSpr && !hasSprv) missing.add("scripts/" + base + ".spr");

            scanScriptFile(spr, root, found, missing, scriptQueue);
            scanScriptFile(sprv, root, found, missing, scriptQueue);
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String p : found) {
            String cat = categoryOf(p);
            counts.merge(cat, 1, Integer::sum);
        }

        return new Result(new ArrayList<>(found), new ArrayList<>(missing), counts);
    }

    private static void walkNode(SceneNode node, Path root,
                                 Set<String> found, Set<String> missing, Queue<String> scripts) {
        if (node == null) return;

        if (node.script != null && !node.script.isBlank()) {
            scripts.add(node.script);
        }

        addAsset(node.model, root, found, missing);
        addAsset(node.texture, root, found, missing);
        addAsset(node.animation, root, found, missing);
        addAsset(node.animatorFile, root, found, missing);
        addAsset(node.playerSkinOverlay, root, found, missing);
        addAsset(node.uiTexture, root, found, missing);
        addAsset(node.uiVideo, root, found, missing);
        addAsset(node.uiEntityModel, root, found, missing);
        addAsset(node.uiEntityTexture, root, found, missing);
        addAsset(node.uiEntityAnim, root, found, missing);
        if (node.routeFile != null && !node.routeFile.isBlank()) {
            addAsset(node.routeFile, root, found, missing);
        }

        if (node.blockDef != null) collectBlock(node.blockDef, root, found, missing);
        if (node.itemDef != null) collectItem(node.itemDef, root, found, missing);
        if (node.tabDef != null && node.tabDef.icon != null) {
            addAsset(node.tabDef.icon, root, found, missing);
        }

        scanCustomData(node.customData, root, found, missing, scripts);

        if (node.children != null) {
            for (SceneNode child : node.children) walkNode(child, root, found, missing, scripts);
        }
    }

    private static void collectBlock(SceneBlockDef d, Path root, Set<String> found, Set<String> missing) {
        addAsset(d.model, root, found, missing);
        addAsset(d.texture, root, found, missing);
        addAsset(d.textureUp, root, found, missing);
        addAsset(d.textureDown, root, found, missing);
        addAsset(d.textureSides, root, found, missing);
        addAsset(d.textureNorth, root, found, missing);
        addAsset(d.textureSouth, root, found, missing);
        addAsset(d.textureWest, root, found, missing);
        addAsset(d.textureEast, root, found, missing);
    }

    private static void collectItem(SceneItemDef d, Path root, Set<String> found, Set<String> missing) {
        addAsset(d.model, root, found, missing);
        addAsset(d.texture, root, found, missing);
        addAsset(d.geo, root, found, missing);
        addAsset(d.armorTexture, root, found, missing);
    }

    private static void scanCustomData(Object value, Path root,
                                       Set<String> found, Set<String> missing, Queue<String> scripts) {
        if (value == null) return;
        if (value instanceof String s) {
            maybeQueueImport(s, scripts);
            addAsset(s, root, found, missing);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object v : map.values()) scanCustomData(v, root, found, missing, scripts);
            return;
        }
        if (value instanceof Iterable<?> it) {
            for (Object v : it) scanCustomData(v, root, found, missing, scripts);
        }
    }

    private static void scanScriptFile(Path file, Path root,
                                       Set<String> found, Set<String> missing, Queue<String> scripts) {
        if (!Files.isRegularFile(file)) return;
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        Matcher im = IMPORT.matcher(text);
        while (im.find()) scripts.add(im.group(1));

        Matcher aq = ASSET_QUOTE.matcher(text);
        while (aq.find()) {
            String raw = aq.group(1);
            if (raw.startsWith("scripts/")) {
                String name = raw.substring("scripts/".length());
                scripts.add(name);
            } else {
                addAsset(raw, root, found, missing);
            }
        }
        Matcher loose = LOOSE_ASSET.matcher(text);
        while (loose.find()) addAsset(loose.group(1), root, found, missing);
    }

    private static void maybeQueueImport(String s, Queue<String> scripts) {
        if (s == null) return;
        Matcher im = IMPORT.matcher(s);
        while (im.find()) scripts.add(im.group(1));
    }

    private static void addAsset(String raw, Path root, Set<String> found, Set<String> missing) {
        String rel = toWorkspaceRel(raw);
        if (rel == null) return;
        Path abs = root.resolve(rel).normalize();
        if (!abs.startsWith(root)) return;
        addExisting(root, rel, abs, found, missing);
        // Texture refs sometimes omit .png
        if (!Files.exists(abs) && !rel.contains(".")
                && (rel.startsWith("textures/") || rel.contains("/textures/"))) {
            Path png = root.resolve(rel + ".png");
            if (Files.isRegularFile(png)) {
                found.add(rel + ".png");
                missing.remove(rel);
            }
        }
    }

    private static void addExisting(Path root, String rel, Path abs,
                                    Set<String> found, Set<String> missing) {
        if (rel == null || rel.isBlank()) return;
        String n = rel.replace('\\', '/');
        if (Files.isRegularFile(abs)) {
            found.add(n);
            missing.remove(n);
        } else if (Files.isDirectory(abs)) {
            // Rare: folder reference — include whole tree
            try (var walk = Files.walk(abs)) {
                walk.filter(Files::isRegularFile).forEach(f -> {
                    String r = root.relativize(f).toString().replace('\\', '/');
                    found.add(r);
                });
            } catch (IOException ignored) {}
        } else {
            missing.add(n);
        }
    }

    /**
     * Converts a storage path to a relative path under spraute_engine/, or null if
     * it is empty / vanilla / external namespace.
     */
    static String toWorkspaceRel(String raw) {
        if (raw == null) return null;
        String path = raw.trim().replace('\\', '/');
        if (path.isEmpty()) return null;
        if (path.startsWith("minecraft:")) return null;

        int colon = path.indexOf(':');
        if (colon > 0) {
            String ns = path.substring(0, colon).toLowerCase(Locale.ROOT);
            path = path.substring(colon + 1);
            if (!"spraute_engine".equals(ns) && !"spraute".equals(ns)) return null;
        }
        while (path.startsWith("/")) path = path.substring(1);
        if (path.startsWith("assets/spraute_engine/")) {
            path = path.substring("assets/spraute_engine/".length());
        }
        if (path.startsWith("spraute_engine/")) {
            path = path.substring("spraute_engine/".length());
        }
        if (path.isEmpty()) return null;
        // Skip pure registry ids like "stone" without a folder prefix
        if (!path.contains("/") && !path.contains(".")) return null;
        return path;
    }

    private static String normalizeScriptName(String name) {
        String relative = name == null ? "" : name.trim().replace('\\', '/');
        if (relative.startsWith("scripts/")) relative = relative.substring("scripts/".length());
        if (relative.endsWith(".sprv")) relative = relative.substring(0, relative.length() - 5);
        if (relative.endsWith(".spr")) relative = relative.substring(0, relative.length() - 4);
        while (relative.startsWith("/")) relative = relative.substring(1);
        return relative;
    }

    static String categoryOf(String rel) {
        String p = rel.toLowerCase(Locale.ROOT);
        if (p.startsWith("scenes/")) return "Сцены";
        if (p.startsWith("scripts/")) return "Скрипты";
        if (p.startsWith("geo/") || p.startsWith("models/")) return "Модели";
        if (p.startsWith("textures/")) return "Текстуры";
        if (p.startsWith("animations/") || p.endsWith(".animation.json")) return "Анимации";
        if (p.startsWith("prefabs/")) return "Префабы";
        return "Прочее";
    }
}
