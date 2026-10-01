package org.zonarstudio.spraute_engine.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.locating.IModFile;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Story JARs stay mounted as read-only overlays. Nothing is copied into
 * {@code spraute_engine/} — remove the mod and the story is gone.
 * Optional worlds still copy into {@code saves/} once (Minecraft cannot load a save from a zip).
 */
public final class StoryPackRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Mount> PACKS = new ArrayList<>();
    private static boolean booted;

    private StoryPackRegistry() {}

    public record Mount(StoryPackManifest manifest, Path packRoot) {}

    public static synchronized void boot(Path gameDir) {
        if (gameDir == null) return;
        scan();
        cleanupOldWorkspaceCopies(gameDir);
        for (Mount mount : PACKS) {
            try {
                installWorldIfMissing(mount.packRoot(), gameDir, mount.manifest());
            } catch (Exception e) {
                LOGGER.warn("[Spraute StoryPack] world {}: {}", mount.manifest().id, e.toString());
            }
        }
        booted = true;
        if (!PACKS.isEmpty()) {
            LOGGER.info("[Spraute StoryPack] mounted {} pack(s) (read-only, not unpacked)", PACKS.size());
        }
    }

    public static synchronized List<Mount> packs() {
        if (!booted) {
            try {
                scan();
                booted = true;
            } catch (RuntimeException ignored) {
            }
        }
        return Collections.unmodifiableList(PACKS);
    }

    /** First pack file for a workspace-relative path, or null. */
    public static Path find(String relative) {
        String rel = normalizeRel(relative);
        if (rel == null) return null;
        for (Mount mount : packs()) {
            if (rel.startsWith("_world/") || rel.equals("_world")) continue;
            Path candidate = mount.packRoot().resolve(rel).normalize();
            if (!candidate.startsWith(mount.packRoot())) continue;
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    public static String relativize(Path file) {
        if (file == null) return null;
        Path abs = file.toAbsolutePath().normalize();
        for (Mount mount : packs()) {
            try {
                Path root = mount.packRoot().toAbsolutePath().normalize();
                if (abs.startsWith(root)) {
                    return root.relativize(abs).toString().replace('\\', '/');
                }
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    private static synchronized void scan() {
        PACKS.clear();
        try {
            if (ModList.get() == null) return;
            for (var info : ModList.get().getModFiles()) {
                IModFile file = info.getFile();
                if (file == null) continue;
                try {
                    Mount mount = mountFromModFile(file);
                    if (mount != null) PACKS.add(mount);
                } catch (Exception e) {
                    LOGGER.warn("[Spraute StoryPack] skip {}: {}", file.getFilePath(), e.toString());
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[Spraute StoryPack] scan: {}", t.toString());
        }
    }

    private static Mount mountFromModFile(IModFile file) throws IOException {
        Path loc = file.getFilePath();
        Path manifestPath = tryFindResource(file, "META-INF", "spraute-pack.json");
        Path packRoot = tryFindResource(file, StoryPackManifest.CONTENT_ROOT);
        if ((manifestPath == null || !Files.isRegularFile(manifestPath) || packRoot == null || !Files.isDirectory(packRoot))
                && loc != null) {
            Path[] opened = openJarRoot(loc);
            if (opened != null) {
                Path jarRoot = opened[0];
                Path m = jarRoot.resolve(StoryPackManifest.JAR_PATH);
                if (!Files.isRegularFile(m)) m = jarRoot.resolve("META-INF").resolve("spraute-pack.json");
                Path p = jarRoot.resolve(StoryPackManifest.CONTENT_ROOT);
                if (!Files.isDirectory(p)) p = jarRoot.resolve("spraute_pack");
                if (Files.isRegularFile(m) && Files.isDirectory(p)) {
                    manifestPath = m;
                    packRoot = p;
                }
            }
        }
        if (manifestPath == null || !Files.isRegularFile(manifestPath)) return null;
        if (packRoot == null || !Files.isDirectory(packRoot)) return null;
        StoryPackManifest manifest = StoryPackManifest.fromJson(Files.readString(manifestPath, StandardCharsets.UTF_8));
        if (manifest.id == null || manifest.id.isBlank()) return null;
        return new Mount(manifest, packRoot);
    }

    private static Path tryFindResource(IModFile file, String... parts) {
        try {
            Path p = file.findResource(parts);
            return p;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** {@code opened[0]} = jar root. Caller must not close Forge-owned filesystems. */
    private static Path[] openJarRoot(Path loc) {
        if (loc == null) return null;
        if (Files.isDirectory(loc)) return new Path[]{loc};
        if (!Files.isRegularFile(loc) || !loc.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return null;
        }
        try {
            FileSystem fs;
            try {
                fs = FileSystems.newFileSystem(loc);
            } catch (java.nio.file.FileSystemAlreadyExistsException e) {
                fs = FileSystems.getFileSystem(java.net.URI.create("jar:" + loc.toAbsolutePath().toUri()));
            } catch (java.nio.file.ProviderNotFoundException e) {
                fs = FileSystems.newFileSystem(
                        java.net.URI.create("jar:" + loc.toAbsolutePath().toUri()), java.util.Map.of());
            }
            return new Path[]{fs.getPath("")};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Previous versions copied pack files into the workspace. Delete only files we marked
     * as owned so leftover copies don't outlive the JAR.
     */
    private static void cleanupOldWorkspaceCopies(Path gameDir) {
        Path workspace = gameDir.resolve("spraute_engine").normalize();
        Path markerDir = workspace.resolve(".story_packs");
        if (!Files.isDirectory(markerDir)) return;
        try (var stream = Files.list(markerDir)) {
            for (Path marker : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                Installed prev = Installed.load(marker);
                for (String rel : prev.files) {
                    if (rel == null || rel.isBlank() || rel.contains("..")) continue;
                    Path dest = workspace.resolve(rel).normalize();
                    if (!dest.startsWith(workspace) || !Files.isRegularFile(dest)) continue;
                    try {
                        Files.deleteIfExists(dest);
                    } catch (IOException ignored) {
                    }
                }
                try {
                    Files.deleteIfExists(marker);
                } catch (IOException ignored) {
                }
                LOGGER.info("[Spraute StoryPack] removed leftover workspace copy from {}", marker.getFileName());
            }
        } catch (IOException ignored) {
        }
    }

    private static void installWorldIfMissing(Path packRoot, Path gameDir, StoryPackManifest manifest) throws IOException {
        if (manifest.worldFolder == null || manifest.worldFolder.isBlank()) return;
        Path packed = packRoot.resolve("_world").resolve(manifest.worldFolder);
        if (!Files.isDirectory(packed)) packed = packRoot.resolve("_world");
        if (!Files.isRegularFile(packed.resolve("level.dat"))) return;
        String folder = StoryPackManifest.sanitizeFileToken(
                manifest.worldInstallName == null || manifest.worldInstallName.isBlank()
                        ? manifest.worldFolder : manifest.worldInstallName);
        if (folder.isBlank()) folder = manifest.worldFolder;
        Path dest = gameDir.resolve("saves").resolve(folder).normalize();
        Path saves = gameDir.resolve("saves").normalize();
        if (!dest.startsWith(saves)) return;
        if (Files.isRegularFile(dest.resolve("level.dat"))) return;
        Files.createDirectories(dest);
        try (var walk = Files.walk(packed)) {
            for (Path src : walk.filter(Files::isRegularFile).toList()) {
                String name = src.getFileName().toString().toLowerCase(Locale.ROOT);
                if ("session.lock".equals(name)) continue;
                Path out = dest.resolve(packed.relativize(src).toString()).normalize();
                if (!out.startsWith(dest)) continue;
                Files.createDirectories(out.getParent());
                Files.copy(src, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        LOGGER.info("[Spraute StoryPack] world → saves/{} (copy-if-missing)", folder);
    }

    static String normalizeRel(String relative) {
        if (relative == null) return null;
        String rel = relative.replace('\\', '/').replaceAll("^/+", "");
        if (rel.isBlank() || rel.contains("..")) return null;
        if (rel.startsWith("_world/") || rel.equals("_world")) return null;
        return rel;
    }

    private record Installed(String version, Set<String> files) {
        static Installed load(Path file) {
            if (!Files.isRegularFile(file)) return new Installed("", new LinkedHashSet<>());
            try {
                JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                String ver = o.has("version") ? o.get("version").getAsString() : "";
                Set<String> files = new LinkedHashSet<>();
                if (o.has("files") && o.get("files").isJsonArray()) {
                    JsonArray arr = o.getAsJsonArray("files");
                    for (var el : arr) files.add(el.getAsString());
                }
                return new Installed(ver, files);
            } catch (Exception e) {
                return new Installed("", new LinkedHashSet<>());
            }
        }
    }
}
