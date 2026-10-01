package org.zonarstudio.spraute_engine.client.ui;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.api.specific.AVCMP4Adaptor;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.slf4j.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.zonarstudio.spraute_engine.resource.SprautePaths;
import org.zonarstudio.spraute_engine.resource.WorkspaceContent;

/**
 * Decodes an MP4 into a seekable JPEG frame cache ({@code .spvc}).
 * Playback can start while decoding continues (progressive).
 */
public final class VideoFrameCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Spraute Video Cache — not related to visual scripts ({@code .sprv}). */
    public static final String EXTENSION = ".spvc";
    private static final int MAGIC = 0x53505643; // SPVC
    private static final int VERSION = 1;
    private static final int MAX_EDGE = 1280;
    private static final float JPEG_QUALITY = 0.72f;
    private static final double MIN_FRAME_GAP = 1.0 / 30.0;
    /** Start playback once this many frames exist (or decode finished). */
    public static final int START_FRAMES = 12;
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LiveSource> LIVE = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface Progress {
        void onProgress(float value, long etaMillis);
    }

    public static final class Frame {
        public final int width;
        public final int height;
        public final int[] argb;
        public final double pts;

        public Frame(int width, int height, int[] argb, double pts) {
            this.width = width;
            this.height = height;
            this.argb = argb;
            this.pts = pts;
        }
    }

    record FrameMeta(double pts, int seq, int width, int height, long blobOffset, int length) {}

    /** Readable frame stream: finished cache or live progressive build. */
    public interface Source extends AutoCloseable {
        int availableFrames();
        boolean isComplete();
        boolean failed();
        String error();
        float progress();
        long etaMillis();
        double duration();
        double pts(int index);
        Frame read(int index) throws IOException;
        void awaitFrames(int minCount, long timeoutMs) throws InterruptedException;

        @Override
        void close();
    }

    public static final class Reader implements Source {
        private final RandomAccessFile file;
        private final int width;
        private final int height;
        private final int frameCount;
        private final double duration;
        private final double[] pts;
        private final long[] offsets;
        private final int[] lengths;

        private Reader(RandomAccessFile file, int width, int height, int frameCount, double duration,
                       double[] pts, long[] offsets, int[] lengths) {
            this.file = file;
            this.width = width;
            this.height = height;
            this.frameCount = frameCount;
            this.duration = duration;
            this.pts = pts;
            this.offsets = offsets;
            this.lengths = lengths;
        }

        @Override public int availableFrames() { return frameCount; }
        @Override public boolean isComplete() { return true; }
        @Override public boolean failed() { return false; }
        @Override public String error() { return ""; }
        @Override public float progress() { return 1f; }
        @Override public long etaMillis() { return 0L; }
        @Override public double duration() { return duration; }
        @Override public double pts(int index) { return pts[index]; }

        @Override
        public Frame read(int index) throws IOException {
            if (index < 0 || index >= frameCount) return null;
            byte[] jpeg = new byte[lengths[index]];
            synchronized (file) {
                file.seek(offsets[index]);
                file.readFully(jpeg);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (image == null) throw new IOException("Broken cached JPEG at frame " + index);
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
            return new Frame(w, h, pixels, pts[index]);
        }

        @Override
        public void awaitFrames(int minCount, long timeoutMs) {
            // already complete
        }

        @Override
        public void close() {
            try {
                file.close();
            } catch (IOException ignored) {
            }
        }
    }

    private VideoFrameCache() {}

    public static Path cacheDir() {
        return SprautePaths.workspace().resolve("cache").resolve("videos");
    }

    public static Path cacheFileFor(Path mp4) throws IOException {
        Files.createDirectories(cacheDir());
        return cacheDir().resolve(cacheKey(mp4) + EXTENSION);
    }

    public static boolean isValid(Path cache, Path mp4) {
        if (cache == null || mp4 == null || !Files.isRegularFile(cache) || !Files.isRegularFile(mp4)) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(cache.toFile(), "r")) {
            if (raf.length() < 44 || raf.readInt() != MAGIC || raf.readInt() != VERSION) return false;
            long size = raf.readLong();
            raf.readLong();
            return size == Files.size(mp4);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Opens a finished {@code .spvc} or starts / joins a progressive decode.
     * Playback may begin before {@link Source#isComplete()}.
     */
    public static Source openOrBuild(Path mp4, Progress progress) throws Exception {
        Path cache = cacheFileFor(mp4);
        if (isValid(cache, mp4)) {
            if (progress != null) progress.onProgress(1f, 0L);
            return open(cache);
        }
        installPackedCache(mp4, cache);
        if (isValid(cache, mp4)) {
            if (progress != null) progress.onProgress(1f, 0L);
            return open(cache);
        }
        String key = cache.toAbsolutePath().normalize().toString();
        Object lock = LOCKS.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            if (isValid(cache, mp4)) {
                if (progress != null) progress.onProgress(1f, 0L);
                return open(cache);
            }
            installPackedCache(mp4, cache);
            if (isValid(cache, mp4)) {
                if (progress != null) progress.onProgress(1f, 0L);
                return open(cache);
            }
            LiveSource live = LIVE.get(key);
            if (live != null && !live.failed() && !live.abandoned) {
                if (live.isComplete() && isValid(cache, mp4)) {
                    if (progress != null) progress.onProgress(1f, 0L);
                    return open(cache);
                }
                live.addListener(progress);
                return live.retain();
            }
            live = new LiveSource(mp4, cache, progress);
            LIVE.put(key, live);
            live.start();
            return live.retain();
        }
    }

    /** Blocks until a complete {@code .spvc} exists (prewarm). */
    public static Path ensure(Path mp4, Progress progress) throws Exception {
        Path cache = cacheFileFor(mp4);
        try (Source source = openOrBuild(mp4, progress)) {
            while (!isValid(cache, mp4) && !source.failed()) {
                source.awaitFrames(Integer.MAX_VALUE, 200L);
            }
            if (source.failed()) {
                throw new IOException(source.error().isEmpty() ? "Video decode failed" : source.error());
            }
            if (!isValid(cache, mp4)) {
                throw new IOException("Video cache missing after decode");
            }
            return cache;
        }
    }

    public static Reader open(Path cache) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(cache.toFile(), "r");
        try {
            if (raf.readInt() != MAGIC || raf.readInt() != VERSION) {
                throw new IOException("Unknown video cache format");
            }
            raf.readLong();
            raf.readLong();
            int width = raf.readInt();
            int height = raf.readInt();
            int frameCount = raf.readInt();
            double duration = raf.readDouble();
            if (frameCount < 1 || frameCount > 200_000) {
                throw new IOException("Bad cached frame count: " + frameCount);
            }
            double[] pts = new double[frameCount];
            long[] offsets = new long[frameCount];
            int[] lengths = new int[frameCount];
            for (int i = 0; i < frameCount; i++) {
                pts[i] = raf.readDouble();
                offsets[i] = raf.readLong();
                lengths[i] = raf.readInt();
            }
            return new Reader(raf, width, height, frameCount, duration, pts, offsets, lengths);
        } catch (IOException e) {
            try {
                raf.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    /** Progressive build shared across players for the same mp4. */
    private static final class LiveSource implements Source {
        private final Path mp4;
        private final Path cache;
        private final Path blob;
        private final Object lock = new Object();
        private final List<FrameMeta> frames = new ArrayList<>();
        private final List<Progress> listeners = new ArrayList<>();
        private final long startedAt = System.nanoTime();

        private RandomAccessFile raf;
        private Thread worker;
        private int refs = 0;
        private volatile boolean complete;
        private volatile boolean failed;
        private volatile boolean abandoned;
        private volatile String error = "";
        private volatile float progress;
        private volatile long etaMillis = -1L;
        private volatile double durationHint;
        private volatile int estimatedFrames;
        private Reader packed;
        private long blobOff;

        LiveSource(Path mp4, Path cache, Progress progress) throws IOException {
            this.mp4 = mp4;
            this.cache = cache;
            this.blob = cache.resolveSibling(cache.getFileName() + ".blob");
            Files.createDirectories(cache.getParent());
            Files.deleteIfExists(blob);
            Files.deleteIfExists(cache.resolveSibling(cache.getFileName() + ".tmp"));
            raf = new RandomAccessFile(blob.toFile(), "rw");
            addListener(progress);
        }

        LiveSource retain() {
            synchronized (lock) {
                refs++;
                return this;
            }
        }

        void addListener(Progress progress) {
            if (progress == null) return;
            synchronized (lock) {
                listeners.add(progress);
                progress.onProgress(this.progress, etaMillis);
            }
        }

        void start() {
            worker = new Thread(this::runDecode, "Spraute-Video-Build");
            worker.setDaemon(true);
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            worker.start();
        }

        private void runDecode() {
            boolean prevCache = ImageIO.getUseCache();
            ImageIO.setUseCache(false);
            try {
                boolean ok = VideoFfmpegCache.tryDecode(mp4, MAX_EDGE, this::appendJpeg, this::reportProgress);
                if (!ok) {
                    LOGGER.info("[Spraute Video] Decoding {} with JCodec (slow).", mp4.getFileName());
                    decodeWithJcodec();
                }
                List<FrameMeta> snapshot;
                synchronized (lock) {
                    if (frames.isEmpty()) throw new IOException("No decodable frames in " + mp4.getFileName());
                    snapshot = new ArrayList<>(frames);
                    complete = true;
                    progress = 1f;
                    etaMillis = 0L;
                    if (!snapshot.isEmpty()) {
                        durationHint = Math.max(durationHint, snapshot.get(snapshot.size() - 1).pts);
                    }
                    lock.notifyAll();
                    notifyListenersUnlocked();
                }
                snapshot.sort(Comparator.comparingDouble(FrameMeta::pts).thenComparingInt(FrameMeta::seq));
                Path tmp = cache.resolveSibling(cache.getFileName() + ".tmp");
                writePackedFromLive(tmp, mp4, snapshot);
                try {
                    Files.move(tmp, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, cache, StandardCopyOption.REPLACE_EXISTING);
                }
                Reader reader = open(cache);
                synchronized (lock) {
                    packed = reader;
                    closeBlobUnlocked();
                    lock.notifyAll();
                }
                LOGGER.info("[Spraute Video] Cached {} frames from {}", snapshot.size(), mp4.getFileName());
            } catch (Throwable t) {
                String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                LOGGER.warn("[Spraute Video] Decode failed for {}: {}", mp4.getFileName(), message);
                synchronized (lock) {
                    failed = true;
                    error = message;
                    complete = true;
                    lock.notifyAll();
                    notifyListenersUnlocked();
                }
            } finally {
                ImageIO.setUseCache(prevCache);
                String key = cache.toAbsolutePath().normalize().toString();
                LIVE.remove(key, this);
                try {
                    Files.deleteIfExists(blob);
                } catch (IOException ignored) {
                }
                try {
                    Files.deleteIfExists(cache.resolveSibling(cache.getFileName() + ".tmp"));
                } catch (IOException ignored) {
                }
            }
        }

        private void appendJpeg(double pts, int width, int height, byte[] jpeg) throws IOException {
            synchronized (lock) {
                if (raf == null) throw new IOException("blob closed");
                raf.seek(blobOff);
                raf.write(jpeg);
                frames.add(new FrameMeta(pts, frames.size(), width, height, blobOff, jpeg.length));
                blobOff += jpeg.length;
                if (durationHint < pts) durationHint = pts;
                updateEtaUnlocked();
                lock.notifyAll();
                notifyListenersUnlocked();
            }
        }

        private void reportProgress(float value, long eta) {
            synchronized (lock) {
                progress = Math.max(progress, Math.min(0.99f, value));
                if (eta >= 0L) etaMillis = eta;
                notifyListenersUnlocked();
            }
        }

        private void updateEtaUnlocked() {
            int done = frames.size();
            if (estimatedFrames > 0) {
                progress = Math.min(0.99f, done / (float) estimatedFrames);
            } else if (durationHint > 0.5 && done > 8) {
                double lastPts = frames.get(done - 1).pts;
                if (lastPts > 0.2) progress = Math.min(0.99f, (float) (lastPts / Math.max(durationHint, lastPts)));
            } else {
                progress = Math.min(0.9f, 1f - (float) Math.exp(-done / 40.0));
            }
            if (done >= 8) {
                double elapsed = (System.nanoTime() - startedAt) / 1_000_000_000.0;
                double rate = done / Math.max(0.001, elapsed);
                if (estimatedFrames > done && rate > 0.01) {
                    etaMillis = Math.max(0L, Math.round((estimatedFrames - done) / rate * 1000.0));
                } else if (progress > 0.02f && progress < 0.98f) {
                    etaMillis = Math.max(0L, Math.round(elapsed / progress * (1.0 - progress) * 1000.0));
                }
            }
        }

        private void notifyListenersUnlocked() {
            for (Progress listener : listeners) {
                try {
                    listener.onProgress(progress, etaMillis);
                } catch (Exception ignored) {
                }
            }
        }

        private void decodeWithJcodec() throws Exception {
            SeekableByteChannel channel = null;
            try {
                channel = NIOUtils.readableChannel(mp4.toFile());
                FrameGrab grab;
                if (FragmentedMp4DemuxerTrack.isFragmented(mp4)) {
                    FragmentedMp4DemuxerTrack track = FragmentedMp4DemuxerTrack.open(channel);
                    grab = new FrameGrab(track, new AVCMP4Adaptor(track.meta()));
                } else {
                    grab = FrameGrab.createFrameGrab(channel);
                }
                var meta = grab.getVideoTrack() == null ? null : grab.getVideoTrack().getMeta();
                if (meta != null) {
                    estimatedFrames = Math.max(0, meta.getTotalFrames());
                    if (meta.getTotalDuration() > 0) {
                        durationHint = meta.getTotalDuration();
                    }
                }
                FrameConverter converter = new FrameConverter();
                JpegEncoder jpeg = new JpegEncoder();
                int seq = 0;
                int skipped = 0;
                double lastKeptPts = Double.NEGATIVE_INFINITY;
                try {
                    while (true) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new IOException("Video decode interrupted");
                        }
                        PictureWithMetadata packet = grab.getNativeFrameWithMetadata();
                        if (packet == null) break;
                        Picture picture = packet.getPicture();
                        if (picture == null) {
                            if (++skipped > 64 && frames.isEmpty()) break;
                            continue;
                        }
                        skipped = 0;
                        double pts = packet.getTimestamp();
                        if (lastKeptPts != Double.NEGATIVE_INFINITY && pts - lastKeptPts < MIN_FRAME_GAP - 1e-4) {
                            seq++;
                            continue;
                        }
                        BufferedImage image = converter.toImage(picture);
                        byte[] bytes = jpeg.encode(image);
                        appendJpeg(pts, image.getWidth(), image.getHeight(), bytes);
                        lastKeptPts = pts;
                        seq++;
                    }
                } finally {
                    jpeg.close();
                }
            } finally {
                if (channel != null) NIOUtils.closeQuietly(channel);
            }
        }

        void setEstimated(int frames, double durationSec) {
            synchronized (lock) {
                if (frames > 0) estimatedFrames = frames;
                if (durationSec > 0.2) durationHint = Math.max(durationHint, durationSec);
            }
        }

        private void closeBlobUnlocked() {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
                raf = null;
            }
        }

        @Override
        public int availableFrames() {
            synchronized (lock) {
                if (packed != null) return packed.availableFrames();
                return frames.size();
            }
        }

        @Override public boolean isComplete() { return complete && !failed; }
        @Override public boolean failed() { return failed; }
        @Override public String error() { return error; }
        @Override public float progress() { return progress; }
        @Override public long etaMillis() { return etaMillis; }

        @Override
        public double duration() {
            synchronized (lock) {
                if (packed != null) return packed.duration();
                if (!frames.isEmpty()) {
                    return Math.max(durationHint, frames.get(frames.size() - 1).pts);
                }
                return durationHint;
            }
        }

        @Override
        public double pts(int index) {
            synchronized (lock) {
                if (packed != null) return packed.pts(index);
                return frames.get(index).pts;
            }
        }

        @Override
        public Frame read(int index) throws IOException {
            synchronized (lock) {
                if (packed != null) return packed.read(index);
                if (index < 0 || index >= frames.size()) return null;
                FrameMeta meta = frames.get(index);
                byte[] jpeg = new byte[meta.length];
                if (raf == null) throw new IOException("blob closed");
                raf.seek(meta.blobOffset);
                raf.readFully(jpeg);
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
                if (image == null) throw new IOException("Broken live JPEG at frame " + index);
                int w = image.getWidth();
                int h = image.getHeight();
                int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
                return new Frame(w, h, pixels, meta.pts);
            }
        }

        private byte[] readJpegBytes(FrameMeta meta) throws IOException {
            synchronized (lock) {
                if (raf == null) throw new IOException("blob closed");
                byte[] jpeg = new byte[meta.length];
                raf.seek(meta.blobOffset);
                raf.readFully(jpeg);
                return jpeg;
            }
        }

        private void writePackedFromLive(Path tmp, Path mp4File, List<FrameMeta> ordered) throws IOException {
            if (ordered.isEmpty()) throw new IOException("empty");
            int width = ordered.get(0).width;
            int height = ordered.get(0).height;
            double duration = ordered.get(ordered.size() - 1).pts;
            try (RandomAccessFile out = new RandomAccessFile(tmp.toFile(), "rw")) {
                out.setLength(0);
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeLong(Files.size(mp4File));
                out.writeLong(Files.getLastModifiedTime(mp4File).toMillis());
                out.writeInt(width);
                out.writeInt(height);
                out.writeInt(ordered.size());
                out.writeDouble(Math.max(duration, 0));
                long indexStart = out.getFilePointer();
                out.seek(indexStart + (long) ordered.size() * 20L);
                long[] offsets = new long[ordered.size()];
                for (int i = 0; i < ordered.size(); i++) {
                    byte[] jpeg = readJpegBytes(ordered.get(i));
                    offsets[i] = out.getFilePointer();
                    out.write(jpeg);
                }
                out.seek(indexStart);
                for (int i = 0; i < ordered.size(); i++) {
                    out.writeDouble(ordered.get(i).pts);
                    out.writeLong(offsets[i]);
                    out.writeInt(ordered.get(i).length);
                }
            }
        }

        @Override
        public void awaitFrames(int minCount, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + Math.max(1L, timeoutMs);
            synchronized (lock) {
                while (!failed && !abandoned) {
                    int have = packed != null ? packed.availableFrames() : frames.size();
                    if (have >= minCount || (complete && have > 0)) return;
                    if (complete && have == 0) return;
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0L) return;
                    lock.wait(Math.min(left, 100L));
                }
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                refs = Math.max(0, refs - 1);
                if (refs > 0) return;
                if (!complete) {
                    abandoned = true;
                    if (worker != null) worker.interrupt();
                    closeBlobUnlocked();
                } else if (packed != null) {
                    packed.close();
                    packed = null;
                }
                lock.notifyAll();
            }
            if (abandoned || (complete && packed == null && raf == null)) {
                LIVE.remove(cache.toAbsolutePath().normalize().toString(), this);
            }
        }
    }

    /** Used by ffmpeg path to seed ETA before frames arrive. */
    static void seedEstimate(Path mp4, int estimatedFrames, double durationSec) {
        try {
            Path cache = cacheFileFor(mp4);
            LiveSource live = LIVE.get(cache.toAbsolutePath().normalize().toString());
            if (live != null) live.setEstimated(estimatedFrames, durationSec);
        } catch (Exception ignored) {
        }
    }

    private static final class FrameConverter {
        private BufferedImage scaled;
        private Graphics2D scaledG;

        BufferedImage toImage(Picture src) {
            return scaleIfNeeded(AWTUtil.toBufferedImage(src));
        }

        private BufferedImage scaleIfNeeded(BufferedImage src) {
            int w = src.getWidth();
            int h = src.getHeight();
            int edge = Math.max(w, h);
            if (edge <= MAX_EDGE) return src;
            float scale = MAX_EDGE / (float) edge;
            int nw = Math.max(2, Math.round(w * scale) & ~1);
            int nh = Math.max(2, Math.round(h * scale) & ~1);
            if (scaled == null || scaled.getWidth() != nw || scaled.getHeight() != nh) {
                if (scaledG != null) scaledG.dispose();
                scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_3BYTE_BGR);
                scaledG = scaled.createGraphics();
                scaledG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }
            scaledG.drawImage(src, 0, 0, nw, nh, null);
            return scaled;
        }
    }

    private static final class JpegEncoder implements Closeable {
        private final ImageWriter writer;
        private final ImageWriteParam param;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(96 * 1024);

        JpegEncoder() throws IOException {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) throw new IOException("No JPEG writer");
            writer = writers.next();
            param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
        }

        byte[] encode(BufferedImage image) throws IOException {
            bytes.reset();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bytes)) {
                if (writer.getOutput() != null) writer.reset();
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return bytes.toByteArray();
        }

        @Override
        public void close() {
            writer.dispose();
        }
    }

    private static void writePacked(Path tmp, Path mp4, int width, int height, double duration,
                                    List<FrameMeta> frames, Path jpegBlob) throws IOException {
        int maxLen = 1;
        for (FrameMeta frame : frames) maxLen = Math.max(maxLen, frame.length);
        byte[] buf = new byte[maxLen];
        try (RandomAccessFile raf = new RandomAccessFile(tmp.toFile(), "rw");
             RandomAccessFile blobRaf = new RandomAccessFile(jpegBlob.toFile(), "r")) {
            raf.setLength(0);
            raf.writeInt(MAGIC);
            raf.writeInt(VERSION);
            raf.writeLong(Files.size(mp4));
            raf.writeLong(Files.getLastModifiedTime(mp4).toMillis());
            raf.writeInt(width);
            raf.writeInt(height);
            raf.writeInt(frames.size());
            raf.writeDouble(Math.max(duration, frames.isEmpty() ? 0 : frames.get(frames.size() - 1).pts));
            long indexStart = raf.getFilePointer();
            raf.seek(indexStart + (long) frames.size() * 20L);
            long[] offsets = new long[frames.size()];
            for (int i = 0; i < frames.size(); i++) {
                FrameMeta frame = frames.get(i);
                offsets[i] = raf.getFilePointer();
                blobRaf.seek(frame.blobOffset);
                blobRaf.readFully(buf, 0, frame.length);
                raf.write(buf, 0, frame.length);
            }
            raf.seek(indexStart);
            for (int i = 0; i < frames.size(); i++) {
                raf.writeDouble(frames.get(i).pts);
                raf.writeLong(offsets[i]);
                raf.writeInt(frames.get(i).length);
            }
        }
    }

    /**
     * Copies a packed {@code .spvc} from a story JAR into the local cache, if present.
     * Zip paths cannot be opened with {@link RandomAccessFile}.
     */
    private static void installPackedCache(Path mp4, Path local) {
        if (mp4 == null || local == null) return;
        try {
            String rel = "cache/videos/" + cacheKey(mp4) + EXTENSION;
            Path packed = WorkspaceContent.resolve(rel);
            if (packed == null || packed.toAbsolutePath().normalize().equals(local.toAbsolutePath().normalize())) {
                return;
            }
            if (!Files.exists(packed)) return;
            Files.createDirectories(local.getParent());
            Path tmp = local.resolveSibling(local.getFileName() + ".pack");
            try (InputStream in = Files.newInputStream(packed)) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, local, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, local, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.debug("[Spraute Video] Packed cache copy skipped: {}", e.toString());
        }
    }

    /**
     * Ensures a {@code .spvc} exists for every packed MP4 and returns the file list
     * with those cache paths added ({@code cache/videos/<key>.spvc}).
     */
    public static List<String> attachPackedCaches(Path sprauteRoot, Collection<String> files, Progress progress) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (files != null) {
            for (String raw : files) {
                if (raw == null || raw.isBlank()) continue;
                out.add(raw.replace('\\', '/'));
            }
        }
        if (sprauteRoot == null) return new ArrayList<>(out);
        Path root = sprauteRoot.toAbsolutePath().normalize();
        List<String> mp4s = new ArrayList<>();
        for (String rel : out) {
            String lower = rel.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".mp4") && rel.startsWith("videos/")) mp4s.add(rel);
        }
        int total = Math.max(1, mp4s.size());
        int done = 0;
        for (String rel : mp4s) {
            done++;
            Path mp4 = root.resolve(rel).normalize();
            if (!mp4.startsWith(root) || !Files.isRegularFile(mp4)) continue;
            try {
                if (progress != null) progress.onProgress((done - 1) / (float) total, -1L);
                Path cache = ensure(mp4, progress);
                out.add("cache/videos/" + cache.getFileName().toString());
            } catch (Exception e) {
                LOGGER.warn("[Spraute Video] Export cache skipped for {}: {}", rel, e.toString());
            }
        }
        if (progress != null) progress.onProgress(1f, 0L);
        return new ArrayList<>(out);
    }

    public static Path resolveMp4(String source) {
        String relative = source == null ? "" : source.replace('\\', '/').trim();
        if (relative.startsWith("videos/")) relative = relative.substring("videos/".length());
        if (!relative.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            throw new IllegalArgumentException("Video not found or outside videos/: " + source);
        }
        Path overlay = org.zonarstudio.spraute_engine.resource.WorkspaceContent.resolveNative("videos/" + relative);
        if (overlay != null && Files.isRegularFile(overlay)) return overlay;
        Path root = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("spraute_engine").resolve("videos").toAbsolutePath().normalize();
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Video not found or outside videos/: " + source);
        }
        return file;
    }

    private static String cacheKey(Path mp4) {
        try {
            String raw = videosRel(mp4) + "|" + Files.size(mp4);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format(Locale.ROOT, "%02x", digest[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(mp4.getFileName().toString().hashCode());
        }
    }

    /** Stable id: {@code videos/foo.mp4}, including pack copies under {@code cache/pack/}. */
    private static String videosRel(Path mp4) {
        if (mp4 == null) return "";
        Path abs = mp4.toAbsolutePath().normalize();
        Path ws = SprautePaths.workspace().toAbsolutePath().normalize();
        Path videos = ws.resolve("videos");
        if (abs.startsWith(videos)) {
            return "videos/" + videos.relativize(abs).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        }
        Path packCopy = ws.resolve("cache").resolve("pack").resolve("videos");
        if (abs.startsWith(packCopy)) {
            return "videos/" + packCopy.relativize(abs).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        }
        String slash = abs.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        int idx = slash.lastIndexOf("/videos/");
        if (idx >= 0) return "videos/" + slash.substring(idx + "/videos/".length());
        return mp4.getFileName().toString().toLowerCase(Locale.ROOT);
    }
}
