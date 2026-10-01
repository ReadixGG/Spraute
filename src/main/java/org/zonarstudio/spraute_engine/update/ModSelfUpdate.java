package org.zonarstudio.spraute_engine.update;

import com.mojang.logging.LogUtils;
import net.minecraft.SharedConstants;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Locate the installed Spraute Engine jar and swap it after a server-sent update.
 * Only files inside the game {@code mods/} folder are replaced (never Gradle build output).
 */
public final class ModSelfUpdate {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final long MAX_JAR_BYTES = 48L * 1024L * 1024L;
    public static final int CHUNK_BYTES = AssetSyncRules.CHUNK_BYTES;

    private ModSelfUpdate() {}

    public static String currentVersion() {
        return ModList.get().getModContainerById(Spraute_engine.MODID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    public static String currentMcVersion() {
        try {
            return SharedConstants.getCurrentVersion().getName();
        } catch (Exception e) {
            return "";
        }
    }

    public static Path installedJar() {
        try {
            var info = ModList.get().getModFileById(Spraute_engine.MODID);
            if (info == null) return null;
            Path path = info.getFile().getFilePath();
            if (path == null) return null;
            path = path.toAbsolutePath().normalize();
            if (!isReplaceableJar(path)) return null;
            return path;
        } catch (Exception e) {
            LOGGER.debug("[Spraute Update] jar path: {}", e.toString());
            return null;
        }
    }

    public static Path modsDir() {
        try {
            Path mods = FMLPaths.MODSDIR.get();
            if (mods != null && Files.isDirectory(mods)) return mods.toAbsolutePath().normalize();
        } catch (Exception ignored) {
        }
        Path game = FMLPaths.GAMEDIR.get();
        return game == null ? null : game.resolve("mods").toAbsolutePath().normalize();
    }

    public static boolean isReplaceableJar(Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) return false;
        String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jar")) return false;
        Path parent = jar.getParent();
        return parent != null && "mods".equalsIgnoreCase(parent.getFileName().toString());
    }

    public static String suggestedFileName(String version, String mcVersion) {
        String ver = sanitizeToken(version, "unknown");
        String mc = sanitizeToken(mcVersion, "mc");
        Path current = installedJar();
        if (current != null) {
            String name = current.getFileName().toString();
            String curVer = currentVersion();
            if (curVer != null && !curVer.isBlank() && name.contains(curVer)) {
                String renamed = name.replace(curVer, ver);
                if (isSafeJarName(renamed)) return renamed;
            }
        }
        return "spraute_engine-" + ver + "-" + mc + ".jar";
    }

    public static String sanitizeFileName(String raw, String fallbackVersion, String fallbackMc) {
        if (raw != null) {
            String name = Path.of(raw.replace('\\', '/')).getFileName().toString().trim();
            if (isSafeJarName(name)) return name;
        }
        return suggestedFileName(fallbackVersion, fallbackMc);
    }

    public static boolean isSafeJarName(String name) {
        if (name == null || name.length() < 5 || name.length() > 120) return false;
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) return false;
        if (name.contains("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return false;
        return name.matches("[A-Za-z0-9._-]+\\.jar");
    }

    public static void scheduleReplace(Path currentJar, Path pending, Path targetJar) {
        if (pending == null || targetJar == null) return;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                swapNow(currentJar, pending, targetJar);
            } catch (Exception e) {
                LOGGER.warn("[Spraute Update] shutdown swap failed: {}", e.toString());
            }
        }, "spraute-mod-swap"));
        spawnDetachedSwapper(currentJar, pending, targetJar);
        LOGGER.info("[Spraute Update] scheduled replace {} → {}", currentJar, targetJar);
    }

    static void swapNow(Path currentJar, Path pending, Path targetJar) throws IOException {
        if (pending == null || !Files.isRegularFile(pending)) return;
        Path mods = targetJar.getParent();
        if (currentJar != null && Files.isRegularFile(currentJar)
                && !currentJar.toAbsolutePath().normalize().equals(targetJar.toAbsolutePath().normalize())) {
            try {
                Files.deleteIfExists(currentJar);
            } catch (IOException e) {
                LOGGER.debug("[Spraute Update] could not delete old jar yet: {}", e.toString());
            }
        }
        try {
            Files.move(pending, targetJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.copy(pending, targetJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(pending);
        }
        deleteOtherSprauteJars(mods, targetJar.getFileName().toString());
    }

    public static void deleteOtherSprauteJars(Path mods, String keepName) {
        if (mods == null || !Files.isDirectory(mods) || keepName == null) return;
        try (Stream<Path> stream = Files.list(mods)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.startsWith("spraute_engine") && n.endsWith(".jar")
                                && !p.getFileName().toString().equalsIgnoreCase(keepName);
                    })
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            LOGGER.debug("[Spraute Update] leftover {}: {}", p, e.toString());
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static void spawnDetachedSwapper(Path currentJar, Path pending, Path targetJar) {
        try {
            long pid = ProcessHandle.current().pid();
            Path mods = targetJar.getParent();
            String old = currentJar == null ? "" : currentJar.toAbsolutePath().toString();
            String pend = pending.toAbsolutePath().toString();
            String target = targetJar.toAbsolutePath().toString();
            String modsDir = mods == null ? "" : mods.toAbsolutePath().toString();
            boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            if (win) {
                Path bat = Files.createTempFile("spraute-mod-swap-", ".bat");
                String script = """
                        @echo off
                        setlocal
                        set "PID=%~1"
                        set "OLD=%~2"
                        set "PENDING=%~3"
                        set "TARGET=%~4"
                        set "MODS=%~5"
                        :wait
                        timeout /t 1 /nobreak >nul
                        tasklist /FI "PID eq %PID%" | find "%PID%" >nul
                        if not errorlevel 1 goto wait
                        if not "%OLD%"=="" if exist "%OLD%" del /f /q "%OLD%"
                        if exist "%PENDING%" (
                          move /y "%PENDING%" "%TARGET%" >nul
                        )
                        if exist "%MODS%" (
                          for %%F in ("%MODS%\\spraute_engine-*.jar") do (
                            if /I not "%%~nxF"=="%~nx4" del /f /q "%%F"
                          )
                        )
                        """;
                Files.writeString(bat, script);
                new ProcessBuilder("cmd.exe", "/c", "start", "spraute-swap", "/min",
                        "cmd.exe", "/c",
                        "call", bat.toAbsolutePath().toString(),
                        String.valueOf(pid), old, pend, target, modsDir)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } else {
                String keep = targetJar.getFileName().toString().replace("'", "'\\''");
                String sh = "while kill -0 " + pid + " 2>/dev/null; do sleep 1; done; "
                        + "rm -f '" + escapeSh(old) + "'; "
                        + "mv -f '" + escapeSh(pend) + "' '" + escapeSh(target) + "'; "
                        + "if [ -d '" + escapeSh(modsDir) + "' ]; then "
                        + "for f in '" + escapeSh(modsDir) + "'/spraute_engine-*.jar; do "
                        + "[ \"$(basename \"$f\")\" = '" + keep + "' ] || rm -f \"$f\"; "
                        + "done; fi";
                new ProcessBuilder("sh", "-c", "nohup sh -c " + shellQuote(sh) + " >/dev/null 2>&1 &")
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
            }
        } catch (Exception e) {
            LOGGER.warn("[Spraute Update] could not spawn swapper: {}", e.toString());
        }
    }

    private static String sanitizeToken(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return s.isEmpty() ? fallback : s;
    }

    private static String escapeSh(String s) {
        if (s == null) return "";
        return s.replace("'", "'\\''");
    }

    private static String shellQuote(String s) {
        return "'" + escapeSh(s) + "'";
    }
}
