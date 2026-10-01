package org.zonarstudio.spraute_engine.client.ui;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast progressive cache builder via an optional ffmpeg binary (PATH, {@code FFMPEG}, or
 * {@code spraute_engine/tools/ffmpeg}). Returns false when ffmpeg is missing/fails
 * so the pure-Java JCodec path can run.
 */
final class VideoFfmpegCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern DURATION = Pattern.compile(
            "Duration:\\s*(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
    private static final Pattern FPS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*fps");
    private static final Pattern FRAME = Pattern.compile("frame=\\s*(\\d+)");

    private static volatile String cachedBin;
    private static volatile boolean lookupDone;

    @FunctionalInterface
    interface JpegSink {
        void accept(double pts, int width, int height, byte[] jpeg) throws IOException;
    }

    @FunctionalInterface
    interface ProgressSink {
        void accept(float progress, long etaMillis);
    }

    private VideoFfmpegCache() {}

    static boolean tryDecode(Path mp4, int maxEdge, JpegSink sink, ProgressSink progress) {
        String bin = findBinary();
        if (bin == null) return false;
        Probe probe = probe(bin, mp4);
        double fps = probe != null && probe.fps > 1 ? probe.fps : 30.0;
        int estimated = 0;
        double durationSec = 0;
        if (probe != null) {
            durationSec = probe.durationSec;
            if (probe.durationSec > 0.2) {
                estimated = Math.max(1, (int) Math.round(probe.durationSec * fps));
            }
        }
        VideoFrameCache.seedEstimate(mp4, estimated, durationSec);
        Process process = null;
        Thread stderr = null;
        long startedAt = System.nanoTime();
        AtomicLong etaBox = new AtomicLong(-1L);
        try {
            List<String> cmd = List.of(
                    bin, "-hide_banner", "-nostdin",
                    "-i", mp4.toAbsolutePath().toString(),
                    "-an",
                    "-vf", "scale=" + maxEdge + ":" + maxEdge
                            + ":force_original_aspect_ratio=decrease",
                    "-q:v", "5",
                    "-vsync", "0",
                    "-f", "image2pipe",
                    "-vcodec", "mjpeg",
                    "pipe:1");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectInput(ProcessBuilder.Redirect.DISCARD);
            process = pb.start();
            Process running = process;
            int est = estimated;
            stderr = new Thread(() -> drainProgress(running.getErrorStream(), progress, est, startedAt, etaBox),
                    "Spraute-ffmpeg-log");
            stderr.setDaemon(true);
            stderr.start();
            int frames = readMjpeg(running.getInputStream(), sink, fps, progress, est, startedAt, etaBox);
            boolean finished = running.waitFor(2, TimeUnit.HOURS);
            if (!finished) {
                running.destroyForcibly();
                return false;
            }
            if (running.exitValue() != 0 && frames == 0) {
                LOGGER.warn("[Spraute Video] ffmpeg exit {} for {}", running.exitValue(), mp4.getFileName());
                return false;
            }
            if (frames == 0) return false;
            LOGGER.info("[Spraute Video] ffmpeg streamed {} frames from {}", frames, mp4.getFileName());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LOGGER.warn("[Spraute Video] ffmpeg failed: {}", e.getMessage());
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (stderr != null) {
                try {
                    stderr.join(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static int readMjpeg(InputStream in, JpegSink sink, double fps, ProgressSink progress,
                                 int estimated, long startedAt, AtomicLong etaBox) throws IOException {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream(96 * 1024);
        byte[] buf = new byte[64 * 1024];
        boolean inFrame = false;
        int prev = -1;
        int width = 0;
        int height = 0;
        int count = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("interrupted");
            for (int i = 0; i < n; i++) {
                int b = buf[i] & 0xFF;
                if (!inFrame) {
                    if (prev == 0xFF && b == 0xD8) {
                        jpeg.reset();
                        jpeg.write(0xFF);
                        jpeg.write(0xD8);
                        inFrame = true;
                    }
                } else {
                    jpeg.write(b);
                    if (prev == 0xFF && b == 0xD9) {
                        if (width == 0 || height == 0) {
                            int[] size = jpegSize(jpeg);
                            width = size[0];
                            height = size[1];
                        }
                        byte[] bytes = jpeg.toByteArray();
                        double pts = count / Math.max(1.0, fps);
                        sink.accept(pts, Math.max(2, width), Math.max(2, height), bytes);
                        count++;
                        jpeg.reset();
                        inFrame = false;
                        if (progress != null && (count & 7) == 0) {
                            float p;
                            long eta = -1L;
                            if (estimated > 0) {
                                p = Math.min(0.95f, count / (float) estimated);
                                double elapsed = (System.nanoTime() - startedAt) / 1_000_000_000.0;
                                if (count >= 8 && elapsed > 0.2) {
                                    double rate = count / elapsed;
                                    eta = Math.max(0L, Math.round((estimated - count) / rate * 1000.0));
                                    etaBox.set(eta);
                                }
                            } else {
                                p = Math.min(0.9f, 1f - (float) Math.exp(-count / 40.0));
                            }
                            progress.accept(p, eta);
                        }
                    }
                }
                prev = b;
            }
        }
        return count;
    }

    private static int[] jpegSize(ByteArrayOutputStream jpeg) {
        byte[] data = jpeg.toByteArray();
        for (int i = 0; i + 8 < data.length; i++) {
            if ((data[i] & 0xFF) != 0xFF) continue;
            int marker = data[i + 1] & 0xFF;
            if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
                int h = ((data[i + 5] & 0xFF) << 8) | (data[i + 6] & 0xFF);
                int w = ((data[i + 7] & 0xFF) << 8) | (data[i + 8] & 0xFF);
                if (w > 1 && h > 1) return new int[]{w, h};
            }
        }
        return new int[]{2, 2};
    }

    private static void drainProgress(InputStream err, ProgressSink progress, int estimated,
                                      long startedAt, AtomicLong etaBox) {
        try {
            byte[] buf = new byte[1024];
            StringBuilder line = new StringBuilder();
            int n;
            while ((n = err.read(buf)) >= 0) {
                for (int i = 0; i < n; i++) {
                    char c = (char) (buf[i] & 0xFF);
                    if (c == '\r' || c == '\n') {
                        handleLogLine(line.toString(), progress, estimated, startedAt, etaBox);
                        line.setLength(0);
                    } else {
                        line.append(c);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void handleLogLine(String line, ProgressSink progress, int estimated,
                                      long startedAt, AtomicLong etaBox) {
        if (progress == null || estimated <= 0 || line.isBlank()) return;
        Matcher m = FRAME.matcher(line);
        if (m.find()) {
            try {
                int frame = Integer.parseInt(m.group(1));
                float p = Math.min(0.95f, frame / (float) estimated);
                long eta = -1L;
                double elapsed = (System.nanoTime() - startedAt) / 1_000_000_000.0;
                if (frame >= 8 && elapsed > 0.2) {
                    double rate = frame / elapsed;
                    eta = Math.max(0L, Math.round((estimated - frame) / rate * 1000.0));
                    etaBox.set(eta);
                }
                progress.accept(p, eta);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private record Probe(double fps, double durationSec) {}

    private static Probe probe(String bin, Path mp4) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(bin, "-hide_banner", "-i", mp4.toAbsolutePath().toString());
            pb.redirectInput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectErrorStream(true);
            process = pb.start();
            String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(12, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            double duration = 0;
            Matcher dm = DURATION.matcher(log);
            if (dm.find()) {
                duration = Integer.parseInt(dm.group(1)) * 3600
                        + Integer.parseInt(dm.group(2)) * 60
                        + Double.parseDouble(dm.group(3));
            }
            double fps = 0;
            Matcher fm = FPS.matcher(log);
            if (fm.find()) fps = Double.parseDouble(fm.group(1));
            return new Probe(fps, duration);
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static String findBinary() {
        if (lookupDone) return cachedBin;
        synchronized (VideoFfmpegCache.class) {
            if (lookupDone) return cachedBin;
            List<String> candidates = new ArrayList<>();
            String env = System.getenv("FFMPEG");
            if (env != null && !env.isBlank()) candidates.add(env.trim());
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                Path tools = mc.gameDirectory.toPath().resolve("spraute_engine").resolve("tools");
                candidates.add(tools.resolve("ffmpeg.exe").toString());
                candidates.add(tools.resolve("ffmpeg").toString());
            }
            candidates.add("ffmpeg");
            candidates.add("ffmpeg.exe");
            for (String cand : candidates) {
                if (cand == null || cand.isBlank()) continue;
                if (works(cand)) {
                    cachedBin = cand;
                    LOGGER.info("[Spraute Video] Using ffmpeg at {}", cand);
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
