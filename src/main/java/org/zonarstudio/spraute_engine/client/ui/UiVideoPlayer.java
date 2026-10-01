package org.zonarstudio.spraute_engine.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Plays a UI video from a JPEG cache; can start while decoding is still running. */
public final class UiVideoPlayer implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String textureKey = "spraute_video/" + UUID.randomUUID().toString().replace("-", "");
    private final AtomicReference<VideoFrameCache.Frame> pendingFrame = new AtomicReference<>();
    private final AtomicBoolean stateDirty = new AtomicBoolean(true);
    private final Object lock = new Object();

    private volatile boolean closed;
    private volatile boolean enabled = true;
    private volatile boolean playing;
    private volatile boolean loop;
    private volatile boolean ended;
    private volatile boolean preparing;
    private volatile float preparingProgress;
    private volatile long preparingEtaMillis = -1L;
    private volatile String source = "";
    private volatile boolean restartRequested;
    private volatile boolean frameVisible;
    private volatile double currentTime;
    private volatile double durationSeconds;
    private volatile String lastError = "";

    private Thread decoderThread;
    private VideoFrameCache.Source cache;
    private int playIndex;
    private long clockOriginNanos = -1L;
    private double clockOriginPts;
    private long pauseAnchorNanos = -1L;
    private DynamicTexture texture;
    private ResourceLocation textureLocation;
    private int textureWidth;
    private int textureHeight;

    public UiVideoPlayer(String source, boolean enabled, boolean playing, boolean loop) {
        configure(source, enabled, playing, loop);
        decoderThread = new Thread(this::playLoop, "Spraute-UI-Video");
        decoderThread.setDaemon(true);
        decoderThread.setPriority(Thread.MIN_PRIORITY);
        decoderThread.start();
    }

    public void configure(String source, boolean enabled, boolean playing, boolean loop) {
        String normalized = source == null ? "" : source.replace('\\', '/').trim();
        synchronized (lock) {
            if (!normalized.equals(this.source)) {
                this.source = normalized;
                restartRequested = true;
                frameVisible = false;
                lastError = "";
                currentTime = 0;
                ended = false;
                preparing = false;
                preparingProgress = 0f;
                preparingEtaMillis = -1L;
                closeCache();
                markDirty();
            }
            boolean wasPlaying = this.playing;
            this.enabled = enabled;
            this.playing = playing;
            this.loop = loop;
            if (wasPlaying && !playing) {
                pauseAnchorNanos = System.nanoTime();
                markDirty();
            } else if (!wasPlaying && playing) {
                if (pauseAnchorNanos >= 0L && clockOriginNanos >= 0L) {
                    clockOriginNanos += System.nanoTime() - pauseAnchorNanos;
                }
                pauseAnchorNanos = -1L;
                ended = false;
                markDirty();
            }
            lock.notifyAll();
        }
    }

    public void stop() {
        synchronized (lock) {
            playing = false;
            ended = false;
            currentTime = 0;
            playIndex = 0;
            restartRequested = true;
            pendingFrame.set(null);
            frameVisible = false;
            pauseAnchorNanos = -1L;
            clockOriginNanos = -1L;
            markDirty();
            lock.notifyAll();
        }
    }

    public void restart() {
        synchronized (lock) {
            enabled = true;
            playing = true;
            ended = false;
            currentTime = 0;
            playIndex = 0;
            restartRequested = true;
            pendingFrame.set(null);
            frameVisible = false;
            pauseAnchorNanos = -1L;
            clockOriginNanos = -1L;
            markDirty();
            lock.notifyAll();
        }
    }

    public boolean isPlaying() {
        return playing && enabled && !ended && !preparing;
    }

    public boolean hasEnded() {
        return ended;
    }

    public boolean isPreparing() {
        return preparing;
    }

    public float preparingProgress() {
        return preparingProgress;
    }

    /** Remaining decode time in ms, or -1 if unknown. */
    public long preparingEtaMillis() {
        return preparingEtaMillis;
    }

    public double timeSeconds() {
        return currentTime;
    }

    public double durationSeconds() {
        return durationSeconds;
    }

    public boolean consumeStateDirty() {
        return stateDirty.getAndSet(false);
    }

    public ResourceLocation textureLocation() {
        uploadPendingFrame();
        return frameVisible ? textureLocation : null;
    }

    public String error() {
        return lastError;
    }

    private void playLoop() {
        while (!closed) {
            try {
                synchronized (lock) {
                    while (!closed && (!enabled || !playing || source.isBlank())) {
                        if (!enabled || source.isBlank()) {
                            closeCache();
                            restartRequested = false;
                            preparing = false;
                        }
                        lock.wait(100L);
                    }
                    if (closed) break;
                    if (restartRequested) {
                        playIndex = 0;
                        clockOriginNanos = -1L;
                        clockOriginPts = 0;
                        restartRequested = false;
                    }
                }

                if (!ensureCache()) continue;
                if (cache == null || cache.failed()) {
                    handleError(cache == null ? "No video cache" : cache.error());
                    continue;
                }

                if (!waitUntilFrameReady(playIndex)) continue;

                if (cache.isComplete() && playIndex >= cache.availableFrames()) {
                    handleEndOfStream();
                    continue;
                }
                if (playIndex >= cache.availableFrames()) {
                    continue;
                }

                double pts = cache.pts(playIndex);
                if (!waitForPresentation(pts)) continue;
                VideoFrameCache.Frame frame = cache.read(playIndex);
                if (frame == null) {
                    playIndex++;
                    continue;
                }
                preparing = false;
                pendingFrame.set(frame);
                durationSeconds = Math.max(durationSeconds, cache.duration());
                currentTime = durationSeconds > 0 ? Math.min(Math.max(0.0, pts), durationSeconds) : Math.max(0.0, pts);
                frameVisible = true;
                playIndex++;
                markDirty();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                handleError(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
            }
        }
        closeCache();
    }

    private void handleError(String message) {
        if (message != null && !message.equals(lastError)) {
            LOGGER.warn("[Spraute Video] {}: {}", source, message);
            lastError = message == null ? "" : message;
        }
        playing = false;
        preparing = false;
        markDirty();
        closeCache();
    }

    private boolean ensureCache() throws Exception {
        if (cache != null) return true;
        Path file = VideoFrameCache.resolveMp4(source);
        preparing = true;
        preparingProgress = VideoFrameCache.isValid(VideoFrameCache.cacheFileFor(file), file) ? 1f : 0f;
        preparingEtaMillis = preparingProgress >= 1f ? 0L : -1L;
        markDirty();
        cache = VideoFrameCache.openOrBuild(file, (value, eta) -> {
            preparingProgress = value;
            preparingEtaMillis = eta;
            markDirty();
        });
        // Wait for a small buffer, then start playback while decode continues.
        while (!closed && enabled && playing && cache != null && !cache.failed()) {
            int have = cache.availableFrames();
            if (have >= VideoFrameCache.START_FRAMES || cache.isComplete()) break;
            preparing = true;
            preparingProgress = cache.progress();
            preparingEtaMillis = cache.etaMillis();
            markDirty();
            cache.awaitFrames(VideoFrameCache.START_FRAMES, 100L);
        }
        if (cache == null || cache.failed() || cache.availableFrames() <= 0) {
            preparing = false;
            return cache != null && !cache.failed() && cache.availableFrames() > 0;
        }
        durationSeconds = Math.max(durationSeconds, cache.duration());
        preparing = false;
        markDirty();
        synchronized (lock) {
            lock.notifyAll();
        }
        return true;
    }

    /** If the playhead caught up to the decoder, show wait UI until more frames exist. */
    private boolean waitUntilFrameReady(int index) throws InterruptedException {
        while (!closed && enabled && playing && cache != null && !cache.failed()) {
            int have = cache.availableFrames();
            if (index < have) {
                if (preparing) {
                    preparing = false;
                    markDirty();
                }
                return true;
            }
            if (cache.isComplete()) {
                preparing = false;
                return true;
            }
            preparing = true;
            preparingProgress = cache.progress();
            preparingEtaMillis = cache.etaMillis();
            durationSeconds = Math.max(durationSeconds, cache.duration());
            markDirty();
            cache.awaitFrames(index + 1, 100L);
        }
        return false;
    }

    private void handleEndOfStream() {
        if (loop) {
            synchronized (lock) {
                playIndex = 0;
                currentTime = 0;
                ended = false;
                clockOriginNanos = -1L;
                markDirty();
            }
            return;
        }
        playing = false;
        ended = true;
        if (durationSeconds > 0) currentTime = durationSeconds;
        markDirty();
    }

    private boolean waitForPresentation(double pts) throws InterruptedException {
        synchronized (lock) {
            while (!closed && enabled && playing) {
                long remaining = deadlineNanos(pts) - System.nanoTime();
                if (remaining <= 0L) return true;
                long waitMs = Math.min(40L, remaining / 1_000_000L);
                if (waitMs <= 0L) {
                    lock.wait(0L, (int) Math.min(999_999L, Math.max(1L, remaining)));
                } else {
                    lock.wait(waitMs);
                }
            }
            return false;
        }
    }

    private long deadlineNanos(double pts) {
        if (clockOriginNanos < 0L) {
            clockOriginNanos = System.nanoTime();
            clockOriginPts = pts;
        }
        return clockOriginNanos + (long) ((pts - clockOriginPts) * 1_000_000_000.0);
    }

    private void uploadPendingFrame() {
        VideoFrameCache.Frame frame = pendingFrame.getAndSet(null);
        if (frame == null) return;
        if (texture == null || textureWidth != frame.width || textureHeight != frame.height) {
            destroyTexture();
            NativeImage image = new NativeImage(frame.width, frame.height, true);
            texture = new DynamicTexture(image);
            textureLocation = Minecraft.getInstance().getTextureManager().register(textureKey, texture);
            textureWidth = frame.width;
            textureHeight = frame.height;
        }
        NativeImage image = texture == null ? null : texture.getPixels();
        if (image == null) return;
        for (int y = 0; y < frame.height; y++) {
            int row = y * frame.width;
            for (int x = 0; x < frame.width; x++) {
                int argb = frame.argb[row + x];
                int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                image.setPixelRGBA(x, y, abgr);
            }
        }
        texture.upload();
    }

    private void closeCache() {
        if (cache != null) {
            cache.close();
            cache = null;
        }
        playIndex = 0;
        clockOriginNanos = -1L;
        clockOriginPts = 0;
    }

    private void destroyTexture() {
        if (textureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(textureLocation);
        }
        texture = null;
        textureLocation = null;
        textureWidth = 0;
        textureHeight = 0;
    }

    private void markDirty() {
        stateDirty.set(true);
    }

    @Override
    public void close() {
        closed = true;
        synchronized (lock) {
            lock.notifyAll();
        }
        if (decoderThread != null) decoderThread.interrupt();
        closeCache();
        destroyTexture();
    }
}
