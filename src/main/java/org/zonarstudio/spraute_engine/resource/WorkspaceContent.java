package org.zonarstudio.spraute_engine.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Runtime file overlay: player {@code spraute_engine/} wins, then read-only story JARs.
 * Editor listings should keep using disk paths only.
 */
public final class WorkspaceContent {
    private WorkspaceContent() {}

    public static Path resolve(String relative) {
        String rel = StoryPackRegistry.normalizeRel(relative);
        if (rel == null) return null;
        Path disk = SprautePaths.workspace().resolve(rel).normalize();
        Path root = SprautePaths.workspace().normalize();
        if (disk.startsWith(root) && Files.isRegularFile(disk)) return disk;
        return StoryPackRegistry.find(rel);
    }

    public static boolean exists(String relative) {
        return resolve(relative) != null;
    }

    /**
     * Workspace scripts first, then pack scripts not shadowed by the same relative path.
     */
    public static List<Path> allSprFiles() {
        LinkedHashMap<String, Path> byRel = new LinkedHashMap<>();
        Path scripts = SprautePaths.scriptsDir();
        if (Files.isDirectory(scripts)) {
            try (var walk = Files.walk(scripts)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".spr"))
                        .forEach(p -> {
                            try {
                                String rel = "scripts/" + scripts.relativize(p).toString().replace('\\', '/');
                                byRel.put(rel, p);
                            } catch (RuntimeException ignored) {
                            }
                        });
            } catch (IOException ignored) {
            }
        }
        collectPackPrefix("scripts/", ".spr", byRel);
        return new ArrayList<>(byRel.values());
    }

    public static List<Path> listSceneFiles() {
        LinkedHashMap<String, Path> byRel = new LinkedHashMap<>();
        Path scenes = SprautePaths.scenesDir();
        if (Files.isDirectory(scenes)) {
            try (var stream = Files.list(scenes)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".scene.json"))
                        .forEach(p -> byRel.put("scenes/" + p.getFileName(), p));
            } catch (IOException ignored) {
            }
        }
        collectPackPrefix("scenes/", ".scene.json", byRel);
        return new ArrayList<>(byRel.values());
    }

    public static void collectPackFiles(String prefix, LinkedHashMap<String, Path> into) {
        collectPackPrefix(prefix, null, into);
    }

    private static void collectPackPrefix(String prefix, String suffix, LinkedHashMap<String, Path> into) {
        String pfx = prefix == null ? "" : prefix.replace('\\', '/');
        if (!pfx.isEmpty() && !pfx.endsWith("/")) pfx = pfx + "/";
        for (StoryPackRegistry.Mount mount : StoryPackRegistry.packs()) {
            Path dir = pfx.isEmpty() ? mount.packRoot() : mount.packRoot().resolve(pfx);
            if (!Files.isDirectory(dir)) continue;
            try (var walk = Files.walk(dir)) {
                String prefixCapture = pfx;
                String suffixCapture = suffix;
                walk.filter(Files::isRegularFile).forEach(file -> {
                    String rel;
                    try {
                        rel = mount.packRoot().relativize(file).toString().replace('\\', '/');
                    } catch (RuntimeException e) {
                        return;
                    }
                    if (rel.startsWith("_world/")) return;
                    if (suffixCapture != null && !rel.toLowerCase(Locale.ROOT).endsWith(suffixCapture.toLowerCase(Locale.ROOT))) {
                        return;
                    }
                    if (!prefixCapture.isEmpty() && !rel.startsWith(prefixCapture)) return;
                    into.putIfAbsent(rel, file);
                });
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * For native tools (ffmpeg) that cannot read a zip Path: copy pack bytes into cache/.
     */
    public static Path resolveNative(String relative) {
        Path src = resolve(relative);
        if (src == null) return null;
        try {
            String scheme = src.toUri().getScheme();
            if (scheme == null || "file".equalsIgnoreCase(scheme)) return src;
            Path dest = SprautePaths.workspace().resolve("cache").resolve("pack").resolve(relative.replace('\\', '/'));
            Files.createDirectories(dest.getParent());
            boolean stale = !Files.isRegularFile(dest);
            if (!stale) {
                try {
                    stale = Files.size(dest) != Files.size(src);
                } catch (IOException e) {
                    stale = true;
                }
            }
            if (stale) {
                try (InputStream in = Files.newInputStream(src)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return dest;
        } catch (Exception e) {
            return src;
        }
    }
}
