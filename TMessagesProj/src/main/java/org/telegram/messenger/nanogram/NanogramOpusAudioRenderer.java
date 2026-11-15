package org.telegram.messenger.nanogram;

import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;

/**
 * Plays Opus (voice messages) through {@link NanogramOpusDecoder}. VideoPlayer puts it in front of the platform renderer only
 * for a voice message while "Enhance voice messages" is on; with the switch off it is never created.
 */
@UnstableApi
public final class NanogramOpusAudioRenderer extends DecoderAudioRenderer<NanogramOpusDecoder> {

    private static final String TAG = "NanogramOpusAudioRenderer";
    private static final int NUM_BUFFERS = 16;
    /** Opus packets of a voice message are small; this is the size of the first input buffer. */
    private static final int INITIAL_INPUT_BUFFER_SIZE = 960 * 6;

    public NanogramOpusAudioRenderer(
            @Nullable Handler eventHandler,
            @Nullable AudioRendererEventListener eventListener,
            AudioSink audioSink) {
        super(eventHandler, eventListener, audioSink);
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    protected @C.FormatSupport int supportsFormatInternal(Format format) {
        if (!MimeTypes.AUDIO_OPUS.equals(format.sampleMimeType) || !NanogramOpusDecoder.isAvailable()) {
            return C.FORMAT_UNSUPPORTED_TYPE;
        }
        if (!NanogramOpusDecoder.supportsChannelCount(format.channelCount == Format.NO_VALUE ? 2 : format.channelCount)
                || format.initializationData.isEmpty()
                || !sinkSupportsFormat(Util.getPcmFormat(C.ENCODING_PCM_16BIT, 2, NanogramOpusSkip.SAMPLE_RATE))) {
            return C.FORMAT_UNSUPPORTED_SUBTYPE;
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return C.FORMAT_UNSUPPORTED_DRM;
        }
        return C.FORMAT_HANDLED;
    }

    @Override
    protected NanogramOpusDecoder createDecoder(Format format, @Nullable CryptoConfig cryptoConfig)
            throws NanogramOpusDecoderException {
        TraceUtil.beginSection("createNanogramOpusDecoder");
        try {
            return new NanogramOpusDecoder(NUM_BUFFERS, INITIAL_INPUT_BUFFER_SIZE, format.initializationData,
                    NanogramOpusEnhancement.options(true, true));
        } finally {
            TraceUtil.endSection();
        }
    }

    @Override
    protected Format getOutputFormat(NanogramOpusDecoder decoder) {
        return Util.getPcmFormat(C.ENCODING_PCM_16BIT, decoder.getChannelCount(), NanogramOpusSkip.SAMPLE_RATE);
    }
}
