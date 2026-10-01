package org.zonarstudio.spraute_engine.resource;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.registry.SyncedRegistrySnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Shared sandbox for in-band asset sync: visual/audio files under the workspace,
 * plus the registry snapshot {@code scripts/_spraute_server_registry.spr} so clients
 * can register the same create item/block/craft IDs after a restart.
 */
public final class AssetSyncRules {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Set<String> ROOT_DIRS = Set.of(
            "textures", "geo", "animations", "sounds", "videos", "fonts", "scripts");
    public static final Set<String> EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".svg", ".ogg", ".mp3", ".mp4", ".json", ".ttf", ".otf", ".mcmeta", ".spr");

    /** Only this script path is synced (not the whole scripts/ tree). */
    public static final String REGISTRY_SCRIPT = SyncedRegistrySnapshot.RELATIVE_PATH;

    public static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 150L * 1024 * 1024;
    public static final int MAX_FILES = 2000;
    public static final int MAX_PATH_LEN = 240;
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final int HASH_BYTES = 32;
    public static final int MANIFEST_PAGE_SIZE = 400;
    public static final long HARD_MAX_FILE_BYTES = 64L * 1024 * 1024;
    public static final long HARD_MAX_TOTAL_BYTES = 2048L * 1024 * 1024;
    public static final int HARD_MAX_FILES = 10000;

    private AssetSyncRules() {}

    public static long maxFileBytes() {
        try {
            return Math.min(HARD_MAX_FILE_BYTES, org.zonarstudio.spraute_engine.config.SprauteConfig.get().maxFileBytes());
        } catch (Exception e) {
            return MAX_FILE_BYTES;
        }
    }

    public static long maxTotalBytes() {
        try {
            return Math.min(HARD_MAX_TOTAL_BYTES, org.zonarstudio.spraute_engine.config.SprauteConfig.get().maxTotalBytes());
        } catch (Exception e) {
            return MAX_TOTAL_BYTES;
        }
    }

    public static int maxFiles() {
        try {
            return Math.min(HARD_MAX_FILES, org.zonarstudio.spraute_engine.config.SprauteConfig.get().maxFiles());
        } catch (Exception e) {
            return MAX_FILES;
        }
    }

    public static final class FileEntry {
        public final String path;
        public final long size;
        public final byte[] sha256;

        public FileEntry(String path, long size, byte[] sha256) {
            this.path = path;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    public static final class Manifest {
        public final List<FileEntry> entries;
        public final long totalBytes;
        public final String fingerprint;

        public Manifest(List<FileEntry> entries, long totalBytes, String fingerprint) {
            this.entries = List.copyOf(entries);
            this.totalBytes = totalBytes;
            this.fingerprint = fingerprint == null ? "" : fingerprint;
        }

        public static Manifest empty() {
            return new Manifest(List.of(), 0L, "");
        }
    }

    public static String sanitizeRelative(String raw) {
        if (raw == null) return null;
        String s = raw.replace('\\', '/').trim();
        if (s.isEmpty() || s.length() > MAX_PATH_LEN) return null;
        if (s.charAt(0) == '/' || s.indexOf('\0') >= 0 || s.indexOf(':') >= 0) return null;
        if (s.contains("..")) return null;
        String[] parts = s.split("/");
        if (parts.length < 2) return null;
        if (!ROOT_DIRS.contains(parts[0])) return null;
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return null;
            if (part.charAt(0) == '.') return null;
        }
        int dot = s.lastIndexOf('.');
        if (dot < 0 || dot == s.length() - 1) return null;
        String ext = s.substring(dot).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(ext)) return null;
        // scripts/: only the generated registry snapshot, never story scripts
        if ("scripts".equals(parts[0])) {
            if (!SyncedRegistrySnapshot.RELATIVE_PATH.equalsIgnoreCase(s)) return null;
        }
        return s;
    }

    public static Path resolveInWorkspace(Path workspace, String relative) {
        String path = sanitizeRelative(relative);
        if (path == null || workspace == null) return null;
        Path root = workspace.toAbsolutePath().normalize();
        Path dest = root.resolve(path).normalize();
        if (!dest.startsWith(root)) return null;
        return dest;
    }

    public static Manifest scan(Path workspace) {
        if (workspace == null || !Files.isDirectory(workspace)) {
            return Manifest.empty();
        }
        Path root = workspace.toAbsolutePath().normalize();
        List<FileEntry> entries = new ArrayList<>();
        long total = 0L;
        long fileCap = maxFileBytes();
        long totalCap = maxTotalBytes();
        int fileLimit = maxFiles();
        for (String dir : ROOT_DIRS) {
            Path sub = root.resolve(dir);
            if (!Files.isDirectory(sub)) continue;
            try (Stream<Path> walk = Files.walk(sub)) {
                List<Path> files = walk.filter(Files::isRegularFile).toList();
                for (Path file : files) {
                    if (entries.size() >= fileLimit) break;
                    Path abs = file.toAbsolutePath().normalize();
                    if (!abs.startsWith(root)) continue;
                    String rel = sanitizeRelative(root.relativize(abs).toString());
                    if (rel == null) continue;
                    long size;
                    try {
                        size = Files.size(abs);
                    } catch (IOException e) {
                        continue;
                    }
                    if (size <= 0 || size > fileCap) {
                        LOGGER.warn("[Spraute AssetSync] skip {} (size {})", rel, size);
                        continue;
                    }
                    if (total + size > totalCap) {
                        LOGGER.warn("[Spraute AssetSync] total size cap reached, remaining files omitted");
                        break;
                    }
                    byte[] hash;
                    try {
                        hash = sha256(abs);
                    } catch (IOException e) {
                        LOGGER.warn("[Spraute AssetSync] hash failed {}: {}", rel, e.getMessage());
                        continue;
                    }
                    entries.add(new FileEntry(rel, size, hash));
                    total += size;
                }
            } catch (IOException e) {
                LOGGER.warn("[Spraute AssetSync] walk failed in {}: {}", dir, e.getMessage());
            }
            if (entries.size() >= fileLimit || total >= totalCap) break;
        }
        entries.sort(Comparator.comparing(e -> e.path));
        return new Manifest(entries, total, fingerprint(entries));
    }

    public static byte[] sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    md.update(buf, 0, n);
                }
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String fingerprint(List<FileEntry> entries) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (FileEntry e : entries) {
                md.update(e.path.getBytes(StandardCharsets.UTF_8));
                md.update(longBytes(e.size));
                md.update(e.sha256);
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static boolean hashesEqual(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private static byte[] longBytes(long v) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) {
            b[i] = (byte) (v & 0xFF);
            v >>= 8;
        }
        return b;
    }
}
