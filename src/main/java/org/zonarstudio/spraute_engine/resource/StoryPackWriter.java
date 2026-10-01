package org.zonarstudio.spraute_engine.resource;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.ModList;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.script.ScriptLexer;
import org.zonarstudio.spraute_engine.script.ScriptObfuscator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a lowcode Forge jar that works on both 1.19.2 and 1.20.1:
 * {@code mods.toml} + {@code spraute_pack/} workspace files + optional world.
 */
public final class StoryPackWriter {

    private StoryPackWriter() {}

    public enum Phase { COLLECT, OBFUSCATE, META, PACK, WORLD }

    @FunctionalInterface
    public interface Progress {
        void accept(Phase phase, float fraction, String detail);
    }

    public record Spec(
            String modId,
            String displayName,
            String version,
            String description,
            String authors,
            String credits,
            String license,
            String homepage,
            String sceneId,
            List<String> workspaceFiles,
            Path logoFile,
            String worldFolder,
            String worldInstallName,
            boolean obfuscate
    ) {}

    public static void write(Path gameDir, Path outJar, Spec spec) throws IOException {
        write(gameDir, outJar, spec, null);
    }

    public static void write(Path gameDir, Path outJar, Spec spec, Progress progress) throws IOException {
        if (spec == null) throw new IOException("spec");
        String modId = StoryPackManifest.sanitizeModId(spec.modId());
        if (modId.isBlank()) throw new IOException("Некорректный modId");
        String version = spec.version() == null || spec.version().isBlank() ? "1.0.0" : spec.version().trim();
        String display = spec.displayName() == null || spec.displayName().isBlank() ? modId : spec.displayName().trim();
        Path sprauteRoot = gameDir.resolve("spraute_engine").normalize();

        report(progress, Phase.COLLECT, 0.02f, "");
        Set<String> files = new LinkedHashSet<>();
        if (spec.workspaceFiles() != null) {
            for (String raw : spec.workspaceFiles()) {
                if (raw == null) continue;
                String rel = raw.replace('\\', '/').replaceAll("^/+", "");
                if (rel.isBlank() || rel.contains("..")) continue;
                if (rel.startsWith(".story_packs/") || rel.equals(".story_packs")) continue;
                if (rel.startsWith("cache/") && !isPackedVideoCache(rel)) continue;
                Path abs = sprauteRoot.resolve(rel).normalize();
                if (!abs.startsWith(sprauteRoot) || !Files.exists(abs)) {
                    throw new IOException("Путь недоступен: " + rel);
                }
                if (Files.isDirectory(abs)) {
                    try (var walk = Files.walk(abs)) {
                        walk.filter(Files::isRegularFile).forEach(f ->
                                files.add(sprauteRoot.relativize(f).toString().replace('\\', '/')));
                    }
                } else {
                    files.add(rel);
                }
            }
        }
        if (files.isEmpty()) throw new IOException("Нет файлов для упаковки");

        Map<String, String> rename = Map.of();
        if (spec.obfuscate()) {
            List<String> sources = new ArrayList<>();
            int sprTotal = 0;
            for (String rel : files) {
                if (isSpr(rel)) sprTotal++;
            }
            int sprDone = 0;
            for (String rel : files) {
                if (!isSpr(rel)) continue;
                sprDone++;
                report(progress, Phase.OBFUSCATE, 0.05f + 0.12f * (sprDone / (float) Math.max(1, sprTotal)), rel);
                Path abs = sprauteRoot.resolve(rel).normalize();
                String src = Files.readString(abs, StandardCharsets.UTF_8);
                try {
                    new ScriptLexer(src).tokenize();
                } catch (RuntimeException e) {
                    throw new IOException("Не удалось разобрать скрипт для обфускации (" + rel + "): "
                            + e.getMessage(), e);
                }
                sources.add(src);
            }
            rename = ScriptObfuscator.buildRenameMap(sources);
        }

        StoryPackManifest manifest = new StoryPackManifest();
        manifest.id = modId;
        manifest.displayName = display;
        manifest.version = version;
        manifest.scene = spec.sceneId() == null ? "" : spec.sceneId().trim();
        manifest.engine = engineVersion();
        if (spec.worldFolder() != null && !spec.worldFolder().isBlank()) {
            manifest.worldFolder = spec.worldFolder().trim();
            String install = spec.worldInstallName() == null || spec.worldInstallName().isBlank()
                    ? manifest.worldFolder : spec.worldInstallName().trim();
            manifest.worldInstallName = StoryPackManifest.sanitizeFileToken(install);
            if (manifest.worldInstallName.isBlank()) manifest.worldInstallName = manifest.worldFolder;
        }

        Files.createDirectories(outJar.getParent());
        report(progress, Phase.META, 0.20f, "");
        try (OutputStream fos = Files.newOutputStream(outJar);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putString(zos, StoryPackManifest.JAR_PATH,
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                            .toJson(manifest.toJson()));
            putString(zos, "META-INF/mods.toml", modsToml(spec, manifest, engineVersion()));
            putString(zos, "pack.mcmeta", packMcmeta(display));

            if (spec.logoFile() != null && Files.isRegularFile(spec.logoFile())) {
                putFile(zos, spec.logoFile(), "pack.png");
            }

            int packed = 0;
            int total = Math.max(1, files.size());
            for (String rel : files) {
                packed++;
                if (spec.obfuscate() && isSprv(rel) && hasPackedSpr(files, rel, sprauteRoot)) {
                    continue;
                }
                report(progress, Phase.PACK, 0.22f + 0.60f * (packed / (float) total), rel);
                Path abs = sprauteRoot.resolve(rel).normalize();
                String entry = StoryPackManifest.CONTENT_ROOT + "/" + rel;
                if (spec.obfuscate() && isSpr(rel)) {
                    try {
                        String src = Files.readString(abs, StandardCharsets.UTF_8);
                        putString(zos, entry, ScriptObfuscator.obfuscate(src, rename));
                    } catch (RuntimeException e) {
                        throw new IOException("Обфускация не удалась (" + rel + "): " + e.getMessage(), e);
                    }
                } else if (isMainScene(rel, spec.sceneId())) {
                    putString(zos, entry, forceAutoStart(Files.readString(abs, StandardCharsets.UTF_8)));
                } else {
                    putFile(zos, abs, entry);
                }
            }

            if (manifest.worldFolder != null && !manifest.worldFolder.isBlank()) {
                if (manifest.worldFolder.contains("..") || manifest.worldFolder.contains("/")
                        || manifest.worldFolder.contains("\\")) {
                    throw new IOException("Некорректное имя мира");
                }
                Path worldAbs = gameDir.resolve("saves").resolve(manifest.worldFolder).normalize();
                if (!Files.isRegularFile(worldAbs.resolve("level.dat"))) {
                    throw new IOException("Мир не найден или повреждён (нет level.dat)");
                }
                report(progress, Phase.WORLD, 0.83f, manifest.worldFolder);
                putFolder(zos, worldAbs, StoryPackManifest.WORLD_ROOT + "/" + manifest.worldFolder,
                        progress, 0.83f, 0.97f);
            }
        }
        report(progress, Phase.PACK, 0.98f, "");
    }

    private static void report(Progress progress, Phase phase, float fraction, String detail) {
        if (progress == null) return;
        progress.accept(phase, Math.max(0f, Math.min(1f, fraction)), detail == null ? "" : detail);
    }

    private static String engineVersion() {
        try {
            return ModList.get().getModContainerById(Spraute_engine.MODID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("1.7");
        } catch (RuntimeException e) {
            return "1.7";
        }
    }

    /**
     * Forge treats every mod jar as a resource pack. Without {@code pack.mcmeta}
     * the client shows “failed to load resource pack information”.
     * Formats are the 1.19.2 values so the same jar also loads on 1.20.1.
     */
    private static String packMcmeta(String description) {
        String desc = description == null ? "" : description
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
        return """
                {
                  "pack": {
                    "description": "%s",
                    "pack_format": 9,
                    "forge:resource_pack_format": 8,
                    "forge:data_pack_format": 9
                  }
                }
                """.formatted(desc);
    }

    private static String modsToml(Spec spec, StoryPackManifest manifest, String engineVer) {
        String license = blankTo(spec.license(), "All Rights Reserved");
        String authors = blankTo(spec.authors(), "");
        String credits = blankTo(spec.credits(), "");
        String homepage = blankTo(spec.homepage(), "");
        String desc = spec.description() == null || spec.description().isBlank()
                ? "Сюжет Spraute Engine: " + manifest.displayName
                : spec.description().trim();
        boolean hasLogo = spec.logoFile() != null && Files.isRegularFile(spec.logoFile());
        String engineRange = engineRange(engineVer);

        StringBuilder b = new StringBuilder();
        b.append("modLoader=\"lowcodefml\"\n");
        b.append("loaderVersion=\"[40,)\"\n");
        b.append("license=").append(tomlBasic(license)).append('\n');
        if (!homepage.isBlank()) b.append("issueTrackerURL=").append(tomlBasic(homepage)).append('\n');
        b.append('\n');
        b.append("[[mods]]\n");
        b.append("modId=").append(tomlBasic(manifest.id)).append('\n');
        b.append("version=").append(tomlBasic(manifest.version)).append('\n');
        b.append("displayName=").append(tomlBasic(manifest.displayName)).append('\n');
        b.append("displayTest=\"IGNORE_ALL_VERSION\"\n");
        if (hasLogo) b.append("logoFile=\"pack.png\"\n");
        if (!authors.isBlank()) b.append("authors=").append(tomlBasic(authors)).append('\n');
        if (!credits.isBlank()) b.append("credits=").append(tomlBasic(credits)).append('\n');
        if (!homepage.isBlank()) b.append("displayURL=").append(tomlBasic(homepage)).append('\n');
        b.append("description='''\n").append(tomlMultiline(desc)).append("\n'''\n\n");

        b.append("[[dependencies.").append(manifest.id).append("]]\n");
        b.append("modId=\"forge\"\nmandatory=true\nversionRange=\"[40,)\"\nordering=\"NONE\"\nside=\"BOTH\"\n\n");

        b.append("[[dependencies.").append(manifest.id).append("]]\n");
        b.append("modId=\"minecraft\"\nmandatory=true\nversionRange=\"[1.19.2,1.20.2)\"\nordering=\"NONE\"\nside=\"BOTH\"\n\n");

        b.append("[[dependencies.").append(manifest.id).append("]]\n");
        b.append("modId=\"spraute_engine\"\nmandatory=true\nversionRange=\"").append(engineRange)
                .append("\"\nordering=\"AFTER\"\nside=\"BOTH\"\n");
        return b.toString();
    }

    private static String engineRange(String engineVer) {
        if (engineVer == null || engineVer.isBlank()) return "[1.7,)";
        String[] parts = engineVer.split("[.-]");
        if (parts.length >= 2 && parts[0].matches("\\d+") && parts[1].matches("\\d+")) {
            return "[" + parts[0] + "." + parts[1] + ",)";
        }
        if (parts.length >= 1 && parts[0].matches("\\d+")) {
            return "[" + parts[0] + ",)";
        }
        return "[1.7,)";
    }

    private static boolean isPackedVideoCache(String rel) {
        if (rel == null) return false;
        String n = rel.replace('\\', '/').toLowerCase(Locale.ROOT);
        return n.startsWith("cache/videos/") && n.endsWith(".spvc");
    }

    private static boolean isMainScene(String rel, String sceneId) {
        if (rel == null || sceneId == null || sceneId.isBlank()) return false;
        String want = "scenes/" + sceneId.trim() + ".scene.json";
        return rel.replace('\\', '/').equalsIgnoreCase(want);
    }

    private static String forceAutoStart(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            o.addProperty("autoStart", true);
            return new GsonBuilder().disableHtmlEscaping().create().toJson(o);
        } catch (RuntimeException e) {
            return json;
        }
    }

    private static boolean isSpr(String rel) {
        return rel != null && rel.toLowerCase(Locale.ROOT).endsWith(".spr");
    }

    private static boolean isSprv(String rel) {
        return rel != null && rel.toLowerCase(Locale.ROOT).endsWith(".sprv");
    }

    private static boolean hasPackedSpr(Set<String> files, String sprvRel, Path sprauteRoot) {
        String sprRel = sprvRel.substring(0, sprvRel.length() - 5) + ".spr";
        for (String f : files) {
            if (f.equalsIgnoreCase(sprRel)) return true;
        }
        return Files.isRegularFile(sprauteRoot.resolve(sprRel));
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static String tomlBasic(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "") + "\"";
    }

    private static String tomlMultiline(String s) {
        return s.replace("'''", "''′");
    }

    private static void putString(ZipOutputStream zos, String name, String text) throws IOException {
        zos.putNextEntry(new ZipEntry(name.replace('\\', '/')));
        zos.write(text.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void putFile(ZipOutputStream zos, Path file, String entryName) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName.replace('\\', '/')));
        try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(zos);
        }
        zos.closeEntry();
    }

    private static void putFolder(ZipOutputStream zos, Path folder, String zipPrefix,
                                 Progress progress, float start, float end) throws IOException {
        String prefix = zipPrefix.endsWith("/") ? zipPrefix : zipPrefix + "/";
        zos.putNextEntry(new ZipEntry(prefix));
        zos.closeEntry();
        int[] total = {0};
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!"session.lock".equalsIgnoreCase(file.getFileName().toString())) total[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        int[] done = {0};
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if ("session.lock".equals(name)) return FileVisitResult.CONTINUE;
                String rel = folder.relativize(file).toString().replace('\\', '/');
                putFile(zos, file, prefix + rel);
                done[0]++;
                float t = total[0] <= 0 ? 1f : done[0] / (float) total[0];
                report(progress, Phase.WORLD, start + (end - start) * t, rel);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(folder)) return FileVisitResult.CONTINUE;
                String rel = folder.relativize(dir).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(prefix + rel + "/"));
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
