package org.zonarstudio.spraute_engine.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Workspace {@code sounds/**} as Minecraft sound ids ({@code spraute_engine:…}).
 * Supports {@code .ogg} natively and {@code .mp3} via {@link FfmpegAudioConvert}.
 */
public final class WorkspaceSoundIndex {
    public static final String NAMESPACE = "spraute_engine";
    private static final long CACHE_TTL_MS = 1500L;
    private static volatile List<Entry> cached = List.of();
    private static volatile long cachedAt;
    private static volatile Path cachedRoot;

    public record Entry(String pathId, String fullId, String label, Path file, boolean mp3, long modified) {}

    private WorkspaceSoundIndex() {}

    public static List<Entry> list() {
        return list(SprautePaths.workspace());
    }

    public static List<Entry> list(Path workspace) {
        Path root = workspace == null ? null : workspace.toAbsolutePath().normalize();
        long now = System.currentTimeMillis();
        if (root != null && root.equals(cachedRoot) && now - cachedAt < CACHE_TTL_MS && cached != null) {
            return cached;
        }
        LinkedHashMap<String, Entry> byId = new LinkedHashMap<>();
        if (root != null) {
            addFromDir(root.resolve("sounds"), byId);
        }
        LinkedHashMap<String, Path> packed = new LinkedHashMap<>();
        WorkspaceContent.collectPackFiles("sounds/", packed);
        for (Map.Entry<String, Path> e : packed.entrySet()) {
            addFile(e.getValue(), relativeNoExt(e.getKey()), byId);
        }
        List<Entry> out = new ArrayList<>(byId.values());
        out.sort((a, b) -> Long.compare(b.modified(), a.modified()));
        List<Entry> frozen = List.copyOf(out);
        cached = frozen;
        cachedAt = now;
        cachedRoot = root;
        return frozen;
    }

    public static void invalidate() {
        cachedAt = 0L;
    }

    public static Entry find(String soundId) {
        return find(SprautePaths.workspace(), soundId);
    }

    public static Entry find(Path workspace, String soundId) {
        if (soundId == null || soundId.isBlank()) return null;
        String raw = soundId.trim();
        String id = raw;
        if (id.startsWith(NAMESPACE + ":")) id = id.substring(NAMESPACE.length() + 1);
        for (Entry e : list(workspace)) {
            if (e.pathId.equals(id) || e.fullId.equals(raw)) return e;
        }
        return null;
    }

    /**
     * File Minecraft should stream for {@code assets/spraute_engine/sounds/<pathId>.ogg}.
     * Converts mp3 → ogg on demand when ffmpeg is available.
     */
    public static Path resolvePlayableOgg(Path workspace, String pathId) {
        Entry entry = find(workspace, pathId);
        if (entry == null || entry.file == null) return null;
        Path src = nativeFile(workspace, entry.file);
        if (src == null || !Files.isRegularFile(src)) return null;
        if (!entry.mp3) return src;
        if (workspace == null) return null;
        Path dest = workspace.resolve("cache").resolve("audio")
                .resolve(pathId.replace('/', '_') + ".ogg");
        if (FfmpegAudioConvert.toOgg(src, dest)) return dest;
        return Files.isRegularFile(dest) ? dest : null;
    }

    private static Path nativeFile(Path workspace, Path file) {
        if (file == null) return null;
        if (workspace != null) {
            try {
                Path rel = workspace.toAbsolutePath().normalize()
                        .relativize(file.toAbsolutePath().normalize());
                String overlay = rel.toString().replace('\\', '/');
                if (!overlay.startsWith("..")) {
                    Path nativePath = WorkspaceContent.resolveNative(overlay);
                    if (nativePath != null) return nativePath;
                }
            } catch (Exception ignored) {
            }
        }
        return file;
    }

    public static boolean isAudioFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ogg") || lower.endsWith(".mp3");
    }

    public static String fullIdForRelative(String soundsRelative) {
        String noExt = relativeNoExt(soundsRelative);
        if (noExt == null || noExt.isBlank()) return "";
        return NAMESPACE + ":" + pathId(noExt, soundsRelative);
    }

    public static String pathId(String relativeNoExt, String originalRelative) {
        String id = sanitizePath(relativeNoExt);
        String lowerOrig = originalRelative == null ? "" : originalRelative.replace('\\', '/').toLowerCase(Locale.ROOT);
        String lowerNoExt = relativeNoExt == null ? "" : relativeNoExt.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!id.equals(lowerNoExt)) {
            if (id.isBlank() || "_".equals(id)) id = "sound";
            int hash = Math.abs(lowerOrig.hashCode());
            id = id + "_" + Integer.toHexString(hash % 0xFFFFF);
        }
        return id;
    }

    private static void addFromDir(Path soundsDir, LinkedHashMap<String, Entry> byId) {
        if (soundsDir == null || !Files.isDirectory(soundsDir)) return;
        try (var stream = Files.walk(soundsDir)) {
            stream.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(p -> {
                        String rel = soundsDir.relativize(p).toString().replace('\\', '/');
                        addFile(p, relativeNoExt("sounds/" + rel), byId);
                    });
        } catch (IOException ignored) {
        }
    }

    private static void addFile(Path file, String relativeNoExt, LinkedHashMap<String, Entry> byId) {
        if (file == null || relativeNoExt == null || relativeNoExt.isBlank()) return;
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".ogg") && !name.endsWith(".mp3")) return;
        boolean mp3 = name.endsWith(".mp3");
        String origRel = relativeNoExt + (mp3 ? ".mp3" : ".ogg");
        String id = uniqueId(pathId(relativeNoExt, origRel), byId);
        String label = file.getFileName().toString();
        long modified = 0L;
        try {
            modified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ignored) {
        }
        byId.putIfAbsent(id, new Entry(id, NAMESPACE + ":" + id, label, file, mp3, modified));
    }

    private static String uniqueId(String id, LinkedHashMap<String, Entry> byId) {
        if (!byId.containsKey(id)) return id;
        for (int i = 2; i < 10_000; i++) {
            String cand = id + "_" + i;
            if (!byId.containsKey(cand)) return cand;
        }
        return id + "_" + Long.toHexString(System.nanoTime());
    }

    private static String relativeNoExt(String soundsRelative) {
        if (soundsRelative == null) return "";
        String rel = soundsRelative.replace('\\', '/');
        if (rel.startsWith("sounds/")) rel = rel.substring("sounds/".length());
        String lower = rel.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ogg") || lower.endsWith(".mp3")) {
            rel = rel.substring(0, rel.length() - 4);
        }
        return rel;
    }

    private static String sanitizePath(String relativeNoExt) {
        if (relativeNoExt == null) return "";
        String s = relativeNoExt.replace('\\', '/').toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(s.length());
        boolean lastUs = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/') {
                out.append('/');
                lastUs = false;
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_') {
                out.append(c);
                lastUs = c == '_';
            } else if (!lastUs) {
                out.append('_');
                lastUs = true;
            }
        }
        String id = out.toString();
        while (id.startsWith("/")) id = id.substring(1);
        while (id.endsWith("/")) id = id.substring(0, id.length() - 1);
        id = id.replaceAll("/{2,}", "/");
        return id;
    }
}
