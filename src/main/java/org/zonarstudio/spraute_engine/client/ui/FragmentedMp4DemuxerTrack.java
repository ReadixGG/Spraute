package org.zonarstudio.spraute_engine.client.ui;

import org.jcodec.codecs.aac.AACUtils;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.mp4.AvcCBox;
import org.jcodec.common.ArrayUtil;
import org.jcodec.common.Codec;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.SeekableDemuxerTrack;
import org.jcodec.common.TrackType;
import org.jcodec.common.VideoCodecMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Packet.FrameType;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.MP4TrackType;
import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.MP4Util.Atom;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.MovieFragmentBox;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.PixelAspectExt;
import org.jcodec.containers.mp4.boxes.SampleEntry;
import org.jcodec.containers.mp4.boxes.TrackFragmentBaseMediaDecodeTimeBox;
import org.jcodec.containers.mp4.boxes.TrackFragmentBox;
import org.jcodec.containers.mp4.boxes.TrackFragmentHeaderBox;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.jcodec.containers.mp4.boxes.TrunBox;
import org.jcodec.containers.mp4.boxes.VideoSampleEntry;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.jcodec.common.VideoCodecMeta.createSimpleVideoCodecMeta;

/**
 * Reads H.264 samples from fragmented MP4 (moov + moof/mdat) inside a single file.
 * JCodec 0.2.5 cannot demux these via {@link org.jcodec.api.FrameGrab#createFrameGrab}.
 */
final class FragmentedMp4DemuxerTrack implements SeekableDemuxerTrack, Closeable {
    private record Fragment(TrackFragmentBox traf, long moofOffset) {}

    private final SeekableByteChannel input;
    private final TrakBox trak;
    private final SampleEntry[] sampleEntries;
    private final Codec codec;
    private final AvcCBox avcC;
    private final ByteBuffer codecPrivate;
    private final int[][] sizes;
    private final int[][] compOffsets;
    private final long[] chunkOffsets;
    private final int[] avgDur;
    private final int[][] sampleDurations;
    private final int frameCount;
    private final long totalDurationTicks;
    private final DemuxerTrackMeta meta;

    private int curFrag;
    private int curFrame;
    private long globalFrame;
    private long pts;
    private long offInChunk;

    private FragmentedMp4DemuxerTrack(SeekableByteChannel input, MovieBox moov, TrakBox trak,
                                        Fragment[] fragments) {
        this.input = input;
        this.trak = trak;
        this.sampleEntries = NodeBox.findAllPath(trak, SampleEntry.class,
                new String[]{"mdia", "minf", "stbl", "stsd", null});
        this.codec = Codec.codecByFourcc(sampleEntries[0].getFourcc());
        if (codec == Codec.H264) {
            this.avcC = H264Utils.parseAVCC((VideoSampleEntry) sampleEntries[0]);
            this.codecPrivate = H264Utils.avcCToAnnexB(avcC);
        } else {
            this.avcC = null;
            this.codecPrivate = codec == Codec.AAC ? AACUtils.getCodecPrivate(sampleEntries[0]) : null;
        }

        if (sortable(fragments)) {
            Arrays.sort(fragments, Comparator.comparingLong(FragmentedMp4DemuxerTrack::fragmentDecodeTime));
        }

        int n = fragments.length;
        sizes = new int[n][];
        compOffsets = new int[n][];
        chunkOffsets = new long[n];
        avgDur = new int[n];
        sampleDurations = new int[n][];

        long prevOffset = 0;
        int prevSampleCount = 0;
        int frames = 0;
        long durationTicks = 0;

        for (int i = 0; i < n; i++) {
            TrackFragmentBox traf = fragments[i].traf;
            TrackFragmentHeaderBox tfhd = NodeBox.findFirst(traf, TrackFragmentHeaderBox.class, "tfhd");
            TrunBox trun = NodeBox.findFirst(traf, TrunBox.class, "trun");
            if (tfhd == null || trun == null) {
                throw new IllegalArgumentException("Broken fMP4 fragment at moof offset " + fragments[i].moofOffset);
            }

            TrackFragmentBaseMediaDecodeTimeBox tfdt =
                    NodeBox.findFirst(traf, TrackFragmentBaseMediaDecodeTimeBox.class, "tfdt");
            if (tfdt != null) {
                if (i > 0) {
                    avgDur[i - 1] = (int) ((tfdt.getBaseMediaDecodeTime() - prevOffset) / prevSampleCount);
                }
                prevOffset = tfdt.getBaseMediaDecodeTime();
                durationTicks = tfdt.getBaseMediaDecodeTime();
            }
            prevSampleCount = (int) trun.getSampleCount();

            sizes[i] = readSampleSizes(tfhd, trun);
            compOffsets[i] = trun.getSampleCompositionOffsets();
            sampleDurations[i] = trun.isSampleDurationAvailable() ? trun.getSampleDurations() : null;

            long base = tfhd.isBaseDataOffsetAvailable()
                    ? tfhd.getBaseDataOffset()
                    : fragments[i].moofOffset;
            chunkOffsets[i] = base + (trun.isDataOffsetAvailable() ? trun.getDataOffset() : 0);

            if (sampleDurations[i] != null) {
                durationTicks += ArrayUtil.sumInt(sampleDurations[i]);
            }
            frames += sizes[i].length;
        }
        if (avgDur.length > 1) {
            avgDur[avgDur.length - 1] = avgDur[avgDur.length - 2];
            durationTicks += (long) avgDur[avgDur.length - 1] * prevSampleCount;
        }

        frameCount = frames;
        totalDurationTicks = durationTicks;
        meta = buildMeta();
    }

    static boolean isFragmented(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            long position = 0L;
            ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
            for (int atoms = 0; atoms < 256 && position + 8 <= fileSize; atoms++) {
                header.clear();
                channel.position(position);
                if (channel.read(header) < 8) break;
                header.flip();
                long atomSize = Integer.toUnsignedLong(header.getInt());
                byte[] typeBytes = new byte[4];
                header.get(typeBytes);
                String type = new String(typeBytes, StandardCharsets.ISO_8859_1);
                if ("moof".equals(type)) return true;
                if (atomSize == 1L) {
                    if (header.remaining() < 8) break;
                    atomSize = header.getLong();
                } else if (atomSize == 0L) {
                    break;
                }
                if (atomSize < 8L || position + atomSize > fileSize) break;
                position += atomSize;
            }
        }
        return false;
    }

    static FragmentedMp4DemuxerTrack open(SeekableByteChannel input) throws IOException {
        MovieBox moov = null;
        TrakBox videoTrak = null;
        List<Fragment> fragments = new ArrayList<>();

        for (Atom atom : MP4Util.getRootAtoms(input)) {
            String fourcc = atom.getHeader().getFourcc();
            if ("moov".equals(fourcc)) {
                moov = (MovieBox) atom.parseBox(input);
                videoTrak = findVideoTrack(moov);
            } else if ("moof".equalsIgnoreCase(fourcc) && videoTrak != null) {
                MovieFragmentBox mf = (MovieFragmentBox) atom.parseBox(input);
                int trackId = videoTrak.getTrackHeader().getTrackId();
                for (TrackFragmentBox traf : mf.getTracks()) {
                    TrackFragmentHeaderBox tfhd = NodeBox.findFirst(traf, TrackFragmentHeaderBox.class, "tfhd");
                    if (tfhd != null && tfhd.getTrackId() == trackId) {
                        fragments.add(new Fragment(traf, atom.getOffset()));
                        break;
                    }
                }
            }
        }

        if (moov == null || videoTrak == null || fragments.isEmpty()) {
            throw new IOException("No fragmented H.264 video track found");
        }
        return new FragmentedMp4DemuxerTrack(input, moov, videoTrak, fragments.toArray(Fragment[]::new));
    }

    DemuxerTrackMeta meta() {
        return meta;
    }

    @Override
    public synchronized Packet nextFrame() throws IOException {
        if (curFrag >= sizes.length) return null;
        if (curFrame >= sizes[curFrag].length) {
            curFrag++;
            curFrame = 0;
            offInChunk = 0;
        }
        if (curFrag >= sizes.length) return null;
        return getNextFrame(ByteBuffer.allocate(sizes[curFrag][curFrame]));
    }

    private synchronized MP4Packet getNextFrame(ByteBuffer storage) throws IOException {
        if (curFrag >= sizes.length || curFrame >= sizes[curFrag].length) return null;

        int size = sizes[curFrag][curFrame];
        if (storage != null && storage.remaining() < size) {
            throw new IllegalArgumentException("Buffer too small for fMP4 sample");
        }

        long pktPos = chunkOffsets[curFrag] + offInChunk;
        ByteBuffer result = readPacketData(storage, pktPos, size);
        if (result == null || result.remaining() < size) return null;
        result = convertPacket(result);

        int hintedDuration = frameCount > 0
                ? (int) (totalDurationTicks / frameCount)
                : trak.getTimescale() / 30;
        int duration = sampleDurations[curFrag] == null
                ? (avgDur[curFrag] == 0 ? hintedDuration : avgDur[curFrag])
                : sampleDurations[curFrag][curFrame];

        boolean sync = curFrame == 0;
        long realPts = pts;
        if (compOffsets[curFrag] != null) {
            realPts = pts + compOffsets[curFrag][curFrame];
        }

        FrameType ftype = sync ? FrameType.KEY : FrameType.INTER;
        MP4Packet pkt = new MP4Packet(result, realPts, trak.getTimescale(), duration, globalFrame, ftype,
                null, 0, realPts, 1, pktPos, size, false);

        offInChunk += size;
        pts += duration;
        curFrame++;
        globalFrame++;
        return pkt;
    }

    @Override
    public DemuxerTrackMeta getMeta() {
        return meta;
    }

    @Override
    public synchronized boolean gotoFrame(long frameNo) throws IOException {
        int frag = 0;
        int global = 0;
        long nextPts = 0;
        for (int[] fragSizes : sizes) {
            if (frameNo >= fragSizes.length) {
                frameNo -= fragSizes.length;
                nextPts += sampleDurations[frag] == null
                        ? (long) avgDur[frag] * fragSizes.length
                        : ArrayUtil.sumInt(sampleDurations[frag]);
                frag++;
                global += fragSizes.length;
            } else {
                curFrag = frag;
                curFrame = (int) frameNo;
                globalFrame = global + frameNo;
                pts = nextPts + (sampleDurations[frag] == null
                        ? (long) avgDur[frag] * frameNo
                        : ArrayUtil.sumInt3(sampleDurations[frag], 0, (int) frameNo));
                adjustOff();
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean gotoSyncFrame(long frameNo) throws IOException {
        int frag = 0;
        int global = 0;
        long nextPts = 0;
        for (int[] fragSizes : sizes) {
            if (frameNo >= fragSizes.length) {
                frameNo -= fragSizes.length;
                nextPts += sampleDurations[frag] == null
                        ? (long) avgDur[frag] * fragSizes.length
                        : ArrayUtil.sumInt(sampleDurations[frag]);
                frag++;
                global += fragSizes.length;
            } else {
                curFrag = frag;
                curFrame = 0;
                globalFrame = global;
                pts = nextPts;
                offInChunk = 0;
                return true;
            }
        }
        return false;
    }

    @Override
    public long getCurFrame() {
        return globalFrame;
    }

    @Override
    public synchronized void seek(double second) throws IOException {
        double remaining = second * trak.getTimescale();
        int frag = 0;
        int global = 0;
        for (int[] fragSizes : sizes) {
            int fragDur = sampleDurations[frag] != null
                    ? ArrayUtil.sumInt(sampleDurations[frag])
                    : avgDur[frag] * fragSizes.length;
            if (remaining > fragDur) {
                remaining -= fragDur;
                frag++;
                global += fragSizes.length;
            } else {
                curFrag = frag;
                if (sampleDurations[frag] != null) {
                    curFrame = 0;
                    for (int dur : sampleDurations[frag]) {
                        if (remaining < dur) break;
                        remaining -= dur;
                        curFrame++;
                    }
                } else if (avgDur[frag] > 0) {
                    curFrame = (int) (remaining / avgDur[frag]);
                } else {
                    curFrame = 0;
                }
                globalFrame = global + curFrame;
                pts = (long) (second * trak.getTimescale());
                adjustOff();
                return;
            }
        }
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    private void adjustOff() {
        offInChunk = 0;
        for (int i = 0; i < curFrame; i++) {
            offInChunk += sizes[curFrag][i];
        }
    }

    private ByteBuffer readPacketData(ByteBuffer buffer, long offset, int size) throws IOException {
        ByteBuffer result = buffer.duplicate();
        synchronized (input) {
            input.setPosition(offset);
            NIOUtils.readL(input, result, size);
        }
        result.flip();
        return result;
    }

    private ByteBuffer convertPacket(ByteBuffer data) {
        if (codecPrivate == null || codec != Codec.H264 || avcC == null) {
            return data;
        }
        ByteBuffer annexb = H264Utils.decodeMOVPacket(data, avcC);
        if (H264Utils.isByteBufferIDRSlice(annexb)) {
            return NIOUtils.combineBuffers(Arrays.asList(codecPrivate.duplicate(), annexb));
        }
        return annexb;
    }

    private DemuxerTrackMeta buildMeta() {
        int[] seekFrames = new int[sizes.length];
        for (int i = 0, numFrames = 0; i < sizes.length; i++) {
            seekFrames[i] = numFrames;
            numFrames += sizes[i].length;
        }
        double durationSec = trak.getTimescale() > 0
                ? (double) totalDurationTicks / trak.getTimescale()
                : 0.0;
        return new DemuxerTrackMeta(TrackType.VIDEO, Codec.H264, durationSec, seekFrames, frameCount,
                codecPrivate == null ? null : codecPrivate.duplicate(), getVideoCodecMeta(), null);
    }

    private VideoCodecMeta getVideoCodecMeta() {
        if (TrakBox.getTrackType(trak) != MP4TrackType.VIDEO) return null;
        VideoCodecMeta videoCodecMeta = createSimpleVideoCodecMeta(trak.getCodedSize(), getColorInfo());
        PixelAspectExt pasp = NodeBox.findFirst(sampleEntries[0], PixelAspectExt.class, "pasp");
        if (pasp != null) videoCodecMeta.setPixelAspectRatio(pasp.getRational());
        return videoCodecMeta;
    }

    private ColorSpace getColorInfo() {
        if (codec == Codec.H264 && avcC != null) {
            if (!avcC.getSpsList().isEmpty()) {
                SeqParameterSet sps = SeqParameterSet.read(avcC.getSpsList().get(0).duplicate());
                return sps.getChromaFormatIdc();
            }
        }
        return null;
    }

    private static int[] readSampleSizes(TrackFragmentHeaderBox tfhd, TrunBox trun) {
        int count = (int) trun.getSampleCount();
        if (trun.isSampleSizeAvailable()) {
            return trun.getSampleSizes();
        }
        if (tfhd.isDefaultSampleSizeAvailable()) {
            int[] sizes = new int[count];
            Arrays.fill(sizes, tfhd.getDefaultSampleSize());
            return sizes;
        }
        throw new IllegalArgumentException("fMP4 fragment has no sample sizes");
    }

    private static long fragmentDecodeTime(Fragment fragment) {
        TrackFragmentBaseMediaDecodeTimeBox tfdt =
                NodeBox.findFirst(fragment.traf, TrackFragmentBaseMediaDecodeTimeBox.class, "tfdt");
        return tfdt == null ? 0L : tfdt.getBaseMediaDecodeTime();
    }

    private static boolean sortable(Fragment[] fragments) {
        for (Fragment fragment : fragments) {
            if (NodeBox.findFirst(fragment.traf, TrackFragmentBaseMediaDecodeTimeBox.class, "tfdt") == null) {
                return false;
            }
        }
        return true;
    }

    private static TrakBox findVideoTrack(MovieBox moov) throws IOException {
        for (TrakBox trak : moov.getTracks()) {
            if (TrakBox.getTrackType(trak) == MP4TrackType.VIDEO) {
                return trak;
            }
        }
        throw new IOException("No video track in moov");
    }
}
