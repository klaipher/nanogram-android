package org.telegram.messenger.nanogram;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderException;

/** Thrown by {@link NanogramOpusDecoder}. */
@UnstableApi
public final class NanogramOpusDecoderException extends DecoderException {

    public NanogramOpusDecoderException(String message) {
        super(message);
    }

    public NanogramOpusDecoderException(String message, Throwable cause) {
        super(message, cause);
    }
}
