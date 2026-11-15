package org.telegram.messenger.nanogram;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Opus decoder over FFmpeg's libopus decoder (the klaipher/FFmpeg fork with the osce_complexity / osce_bwe options) and our
 * libopus 1.6 with the OSCE speech-enhancement models. Used by {@link NanogramOpusAudioRenderer}, which exists only while
 * Settings > Nanogram features > "Enhance voice messages" is on. Output is 16-bit PCM at 48 kHz.
 *
 * It follows media3's own decoder_opus {@code OpusDecoder}: the trimming of the pre-skip and of the seek pre-roll happens
 * here ({@link NanogramOpusSkip}), because FFmpeg's decoder does not get the skip side data from ExoPlayer. FFmpeg's own
 * trimming is switched off (AV_CODEC_FLAG2_SKIP_MANUAL in ffmpeg_jni.cc), and the native context is recreated on every reset.
 */
@UnstableApi
public final class NanogramOpusDecoder
        extends SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, NanogramOpusDecoderException> {

    private static final int BYTES_PER_SAMPLE = 2;
    /** The longest packet Opus can hold (120 ms) at 48 kHz, per channel. */
    private static final int MAX_FRAME_SAMPLES = 5760;
    /** AV_INPUT_BUFFER_PADDING_SIZE of FFmpeg: the packet is read from a buffer that is not exactly its size. */
    private static final int INPUT_PADDING_SIZE = 64;
    private static final int RESULT_INVALID_DATA = -1;

    private static volatile Boolean available;

    private final int channelCount;
    private final int preSkipSamples;
    private final int seekPreRollSamples;
    private final int maxOutputBytes;
    private int skipSamples;
    /** Added by the renderer to every buffer time; a seek to the start has timeUs == streamOffsetUs. */
    private final long streamOffsetUs;
    private long nativeContext;

    /** True when the native library with the decoder is loaded (never true on the JVM). */
    public static boolean isAvailable() {
        Boolean value = available;
        if (value == null) {
            try {
                nativeRelease(0);
                value = true;
            } catch (Throwable t) {
                value = false;
            }
            available = value;
        }
        return value;
    }

    /** Opus supports one or two channels in a voice message; the renderer asks before it takes a format. */
    public static boolean supportsChannelCount(int channelCount) {
        return channelCount == 1 || channelCount == 2;
    }

    public NanogramOpusDecoder(
            int numBuffers,
            int initialInputBufferSize,
            List<byte[]> initializationData,
            NanogramOpusEnhancement.Options options,
            long streamOffsetUs) throws NanogramOpusDecoderException {
        super(new DecoderInputBuffer[numBuffers], new SimpleDecoderOutputBuffer[numBuffers]);
        if (!isAvailable()) {
            throw new NanogramOpusDecoderException("The native Opus decoder is not loaded");
        }
        if (initializationData.isEmpty() || initializationData.get(0).length < NanogramOpusSkip.HEADER_SIZE) {
            throw new NanogramOpusDecoderException("Invalid Opus header");
        }
        byte[] header = initializationData.get(0);
        channelCount = NanogramOpusSkip.channelCount(header);
        if (!supportsChannelCount(channelCount)) {
            throw new NanogramOpusDecoderException("Unsupported Opus channel count " + channelCount);
        }
        preSkipSamples = NanogramOpusSkip.preSkipSamples(initializationData);
        seekPreRollSamples = NanogramOpusSkip.seekPreRollSamples(initializationData);
        skipSamples = preSkipSamples;
        this.streamOffsetUs = streamOffsetUs;
        maxOutputBytes = MAX_FRAME_SAMPLES * channelCount * BYTES_PER_SAMPLE;
        nativeContext = nativeCreate(header, options.complexity, options.bandwidthExtension);
        if (nativeContext == 0) {
            throw new NanogramOpusDecoderException("Initialization failed (see logcat)");
        }
        setInitialInputBufferSize(initialInputBufferSize);
    }

    @Override
    public String getName() {
        return "NanogramOpusDecoder";
    }

    public int getChannelCount() {
        return channelCount;
    }

    @Override
    protected DecoderInputBuffer createInputBuffer() {
        return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT, INPUT_PADDING_SIZE);
    }

    @Override
    protected SimpleDecoderOutputBuffer createOutputBuffer() {
        return new SimpleDecoderOutputBuffer(this::releaseOutputBuffer);
    }

    @Override
    protected NanogramOpusDecoderException createUnexpectedDecodeException(Throwable error) {
        return new NanogramOpusDecoderException("Unexpected decode error", error);
    }

    @Nullable
    @Override
    protected NanogramOpusDecoderException decode(
            DecoderInputBuffer inputBuffer, SimpleDecoderOutputBuffer outputBuffer, boolean reset) {
        if (reset) {
            if (!nativeReset(nativeContext)) {
                return new NanogramOpusDecoderException("Error resetting (see logcat)");
            }
            // To the start: the delay in the header. Anywhere else: the seek pre-roll.
            skipSamples = NanogramOpusSkip.skipAfterReset(inputBuffer.timeUs, streamOffsetUs, preSkipSamples, seekPreRollSamples);
        }
        ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
        ByteBuffer outputData = outputBuffer.init(inputBuffer.timeUs, maxOutputBytes);
        int result = nativeDecode(nativeContext, inputData, inputData.limit(), outputData, maxOutputBytes);
        if (result == RESULT_INVALID_DATA) {
            // Like MediaCodec: a broken packet is not fatal, it produces nothing.
            outputBuffer.shouldBeSkipped = true;
            return null;
        }
        if (result < 0) {
            return new NanogramOpusDecoderException("Error decoding (see logcat)");
        }
        outputData = Util.castNonNull(outputBuffer.data);
        outputData.position(0);
        outputData.limit(result);
        NanogramOpusSkip.Trim trim = NanogramOpusSkip.trim(skipSamples, result, channelCount * BYTES_PER_SAMPLE);
        skipSamples = trim.remainingSkipSamples;
        if (trim.skipBuffer) {
            outputBuffer.shouldBeSkipped = true;
        }
        outputData.position(trim.dropBytes);
        return null;
    }

    @Override
    public void release() {
        super.release();
        nativeRelease(nativeContext);
        nativeContext = 0;
    }

    private static native long nativeCreate(byte[] header, int complexity, boolean bandwidthExtension);

    private static native int nativeDecode(long context, ByteBuffer input, int inputSize, ByteBuffer output, int outputCapacity);

    private static native boolean nativeReset(long context);

    private static native void nativeRelease(long context);
}
