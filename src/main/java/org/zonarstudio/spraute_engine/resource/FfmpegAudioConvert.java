package org.zonarstudio.spraute_engine.resource;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Converts workspace {@code .mp3} to Vorbis {@code .ogg} via ffmpeg
 * (PATH, {@code FFMPEG}, or {@code spraute_engine/tools/ffmpeg}).
 */
public final class FfmpegAudioConvert {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile String cachedBin;
    private static volatile boolean lookupDone;

    private FfmpegAudioConvert() {}

    public static boolean available() {
        return findBinary() != null;
    }

    public static boolean toOgg(Path src, Path dest) {
        if (src == null || dest == null || !Files.isRegularFile(src)) return false;
        try {
            if (Files.isRegularFile(dest) && Files.getLastModifiedTime(dest).toMillis()
                    >= Files.getLastModifiedTime(src).toMillis()) {
                return true;
            }
        } catch (Exception ignored) {
        }
        String bin = findBinary();
        if (bin == null) {
            LOGGER.warn("[Spraute Audio] ffmpeg not found, cannot convert {}", src.getFileName());
            return false;
        }
        Process process = null;
        try {
            Files.createDirectories(dest.getParent());
            Path tmp = dest.resolveSibling(dest.getFileName().toString() + ".part");
            Files.deleteIfExists(tmp);
            List<String> cmd = List.of(
                    bin, "-y", "-hide_banner", "-nostdin",
                    "-i", src.toAbsolutePath().toString(),
                    "-vn", "-c:a", "libvorbis", "-q:a", "5",
                    tmp.toAbsolutePath().toString());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectInput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectErrorStream(true);
            process = pb.start();
            process.getInputStream().readAllBytes();
            if (!process.waitFor(3, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                Files.deleteIfExists(tmp);
                return false;
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(tmp) || Files.size(tmp) <= 0) {
                Files.deleteIfExists(tmp);
                LOGGER.warn("[Spraute Audio] ffmpeg failed for {} (exit {})", src.getFileName(), process.exitValue());
                return false;
            }
            Files.move(tmp, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LOGGER.warn("[Spraute Audio] ffmpeg convert failed: {}", e.getMessage());
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static String findBinary() {
        if (lookupDone) return cachedBin;
        synchronized (FfmpegAudioConvert.class) {
            if (lookupDone) return cachedBin;
            List<String> candidates = new ArrayList<>();
            String env = System.getenv("FFMPEG");
            if (env != null && !env.isBlank()) candidates.add(env.trim());
            Path tools = SprautePaths.workspace().resolve("tools");
            candidates.add(tools.resolve("ffmpeg.exe").toString());
            candidates.add(tools.resolve("ffmpeg").toString());
            candidates.add("ffmpeg");
            candidates.add("ffmpeg.exe");
            for (String cand : candidates) {
                if (cand == null || cand.isBlank()) continue;
                if (works(cand)) {
                    cachedBin = cand;
                    LOGGER.info("[Spraute Audio] Using ffmpeg at {}", cand);
                    break;
                }
            }
            lookupDone = true;
            return cachedBin;
        }
    }

    private static boolean works(String bin) {
        Process process = null;
        try {
            if (bin.contains("\\") || bin.contains("/")) {
                if (!Files.isRegularFile(Path.of(bin))) return false;
            }
            ProcessBuilder pb = new ProcessBuilder(bin, "-version");
            pb.redirectInput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectErrorStream(true);
            process = pb.start();
            process.getInputStream().readAllBytes();
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
