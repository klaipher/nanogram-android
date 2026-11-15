package org.telegram.messenger.nanogram;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * The Opus pre-skip and seek pre-roll arithmetic of {@link NanogramOpusDecoder}, as plain functions so the JVM can test it.
 *
 * FFmpeg's libopus decoder trims the encoder delay only when the packet carries AV_PKT_DATA_SKIP_SAMPLES side data, which
 * ExoPlayer never sends, and it restarts the trimming on every flush. So the trimming is done here, the way media3's own
 * decoder_opus {@code OpusDecoder} does it (lines for preSkipSamples, seekPreRollSamples and skipSamples there): the first
 * {@code preSkip} samples after the start of the stream are decoder delay, and after a seek the first {@code seekPreRoll}
 * samples are decoded only to warm the decoder up, and are not played.
 */
public final class NanogramOpusSkip {

    /** Opus always decodes at 48 kHz. */
    public static final int SAMPLE_RATE = 48_000;
    /** RFC 7845: at least 80 ms before the seek target. */
    public static final int DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3840;
    /** The OpusHead identification header is at least 19 bytes. */
    public static final int HEADER_SIZE = 19;

    private NanogramOpusSkip() {
    }

    /** What to do with the PCM of one decoded packet. */
    public static final class Trim {
        /** Samples still to drop after this packet. */
        public final int remainingSkipSamples;
        /** Bytes at the start of the packet's PCM to drop. */
        public final int dropBytes;
        /** True when the whole packet is inside the skipped part, so it produces no output at all. */
        public final boolean skipBuffer;

        Trim(int remainingSkipSamples, int dropBytes, boolean skipBuffer) {
            this.remainingSkipSamples = remainingSkipSamples;
            this.dropBytes = dropBytes;
            this.skipBuffer = skipBuffer;
        }
    }

    /** Channel count of an OpusHead header (byte 9). */
    public static int channelCount(byte[] header) {
        return header[9] & 0xFF;
    }

    /**
     * The decoder delay in 48 kHz samples. ExoPlayer's Ogg extractor sends {header, preSkipNanos, seekPreRollNanos} (the
     * nanosecond values as native-order longs); with the header only, the pre-skip is the header's own 16-bit field.
     */
    public static int preSkipSamples(List<byte[]> initializationData) {
        if (initializationData.size() == 3 && initializationData.get(1).length == 8) {
            return nanosToSamples(readNativeLong(initializationData.get(1)));
        }
        byte[] header = initializationData.get(0);
        return (header[10] & 0xFF) | ((header[11] & 0xFF) << 8);
    }

    /** The samples to decode and drop after a seek (80 ms unless the extractor said otherwise). */
    public static int seekPreRollSamples(List<byte[]> initializationData) {
        if (initializationData.size() == 3 && initializationData.get(2).length == 8) {
            return nanosToSamples(readNativeLong(initializationData.get(2)));
        }
        return DEFAULT_SEEK_PRE_ROLL_SAMPLES;
    }

    /** Samples to skip after the decoder was reset: the pre-skip when seeking to the start, the seek pre-roll otherwise. */
    public static int skipAfterReset(long timeUs, int preSkipSamples, int seekPreRollSamples) {
        return timeUs == 0 ? preSkipSamples : seekPreRollSamples;
    }

    /**
     * Applies the skip to one packet's PCM.
     *
     * @param skipSamples samples still to drop before this packet
     * @param pcmBytes size of the packet's decoded PCM
     * @param bytesPerFrame bytes of one sample for all channels (channels * 2 for 16-bit PCM)
     */
    public static Trim trim(int skipSamples, int pcmBytes, int bytesPerFrame) {
        if (skipSamples <= 0) {
            return new Trim(0, 0, false);
        }
        int skipBytes = skipSamples * bytesPerFrame;
        if (pcmBytes <= skipBytes) {
            return new Trim(skipSamples - pcmBytes / bytesPerFrame, pcmBytes, true);
        }
        return new Trim(0, skipBytes, false);
    }

    static int nanosToSamples(long nanos) {
        return (int) (nanos * SAMPLE_RATE / 1_000_000_000L);
    }

    private static long readNativeLong(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).getLong();
    }
}
