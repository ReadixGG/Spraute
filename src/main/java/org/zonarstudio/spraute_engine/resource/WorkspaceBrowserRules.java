package org.zonarstudio.spraute_engine.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sandbox for the OP in-game Project explorer talking to a dedicated server.
 * Broader than {@link AssetSyncRules}: scripts/scenes/prefabs as well as assets.
 */
public final class WorkspaceBrowserRules {
    public static final Set<String> ROOT_DIRS = Set.of(
            "scripts", "scenes", "prefabs", "textures", "geo",
            "animations", "sounds", "videos", "fonts", "routes", "plugins", "lang");
    public static final Set<String> FILE_EXTENSIONS = Set.of(
            ".spr", ".sprv", ".json", ".txt", ".md",
            ".png", ".jpg", ".jpeg", ".svg", ".ogg", ".mp3", ".mp4", ".ttf", ".otf", ".mcmeta");

    public static final int MAX_PATH_LEN = 240;
    public static final int MAX_ENTRIES = 4000;
    /** Vanilla custom-payload cap is 32767 — keep a page well under that. */
    public static final int LIST_PAGE_SIZE = 80;
    public static final int LIST_PAGE_BYTES = 28_000;
    public static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    public static final int CHUNK_BYTES = 16 * 1024;

    public static long maxFileBytes() {
        try {
            return org.zonarstudio.spraute_engine.resource.AssetSyncRules.maxFileBytes();
        } catch (Exception e) {
            return MAX_FILE_BYTES;
        }
    }

    public static final byte OP_LIST = 0;
    public static final byte OP_READ = 1;
    public static final byte OP_WRITE = 2;
    public static final byte OP_DELETE = 3;
    public static final byte OP_MKDIR = 4;
    public static final byte OP_RENAME = 5;
    public static final byte OP_LOCK = 6;
    public static final byte OP_UNLOCK = 7;
    /** S2C snapshot/update: {@code message} is the holder name, empty = free. */
    public static final byte OP_LOCK_STATE = 8;
    /** S2C: a workspace file changed; OP clients should pull and refresh. */
    public static final byte OP_FILE_CHANGED = 9;
    /** C2S/S2C: asset-sync size caps. C2S uses fileSize=fileMb, offset=totalMb, dest=maxFiles. */
    public static final byte OP_SET_LIMITS = 10;

    private WorkspaceBrowserRules() {}

    public static final class Entry {
        public final String path;
        public final boolean dir;
        public final long size;

        public Entry(String path, boolean dir, long size) {
            this.path = path;
            this.dir = dir;
            this.size = size;
        }
    }

    public static String sanitize(String raw, boolean directory) {
        if (raw == null) return null;
        String s = raw.replace('\\', '/').trim();
        if (s.isEmpty() || s.length() > MAX_PATH_LEN) return null;
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty() || s.charAt(0) == '/' || s.indexOf('\0') >= 0 || s.indexOf(':') >= 0) return null;
        if (s.contains("..")) return null;
        String[] parts = s.split("/");
        if (parts.length < 1) return null;
        if (!ROOT_DIRS.contains(parts[0])) return null;
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return null;
            if (part.charAt(0) == '.') return null;
        }
        if (directory) return s;
        int dot = s.lastIndexOf('.');
        if (dot < 0 || dot == s.length() - 1) return null;
        String name = parts[parts.length - 1];
        String ext = extensionOf(name);
        if (ext == null || !FILE_EXTENSIONS.contains(ext)) return null;
        return s;
    }

    public static Path resolve(Path workspace, String relative, boolean directory) {
        String path = sanitize(relative, directory);
        if (path == null || workspace == null) return null;
        Path root = workspace.toAbsolutePath().normalize();
        Path dest = root.resolve(path).normalize();
        if (!dest.startsWith(root)) return null;
        return dest;
    }

    public static List<Entry> scan(Path workspace) {
        LinkedHashMap<String, Entry> map = new LinkedHashMap<>();
        for (String dir : ROOT_DIRS) {
            map.put(dir, new Entry(dir, true, 0L));
        }
        if (workspace == null || !Files.isDirectory(workspace)) {
            return List.copyOf(map.values());
        }
        Path root = workspace.toAbsolutePath().normalize();
        for (String dir : ROOT_DIRS) {
            Path sub = root.resolve(dir);
            if (!Files.isDirectory(sub)) continue;
            try (Stream<Path> walk = Files.walk(sub)) {
                List<Path> found = walk.toList();
                for (Path file : found) {
                    if (map.size() >= MAX_ENTRIES) break;
                    Path abs = file.toAbsolutePath().normalize();
                    if (!abs.startsWith(root) || abs.equals(root.resolve(dir))) continue;
                    String rel = root.relativize(abs).toString().replace('\\', '/');
                    if (Files.isDirectory(abs)) {
                        String sane = sanitize(rel, true);
                        if (sane != null) map.putIfAbsent(sane, new Entry(sane, true, 0L));
                    } else if (Files.isRegularFile(abs)) {
                        String sane = sanitize(rel, false);
                        if (sane == null) continue;
                        long size;
                        try {
                            size = Files.size(abs);
                        } catch (IOException e) {
                            continue;
                        }
                        if (size > maxFileBytes()) continue;
                        map.put(sane, new Entry(sane, false, size));
                    }
                }
            } catch (IOException ignored) {
            }
            if (map.size() >= MAX_ENTRIES) break;
        }
        List<Entry> out = new ArrayList<>(map.values());
        out.sort(Comparator
                .comparing((Entry e) -> e.path, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static String parentOf(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    public static String nameOf(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String extensionOf(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".geo.json") || lower.endsWith(".animation.json") || lower.endsWith(".prefab.json")
                || lower.endsWith(".scene.json") || lower.endsWith(".mcmeta")) {
            int dot = lower.lastIndexOf('.');
            return lower.substring(dot);
        }
        int dot = lower.lastIndexOf('.');
        if (dot < 0) return null;
        return lower.substring(dot);
    }
}
