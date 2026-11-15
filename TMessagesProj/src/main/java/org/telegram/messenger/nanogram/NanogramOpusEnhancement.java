package org.telegram.messenger.nanogram;

/**
 * When and how the voice-message playback uses libopus 1.6 speech enhancement ("Enhance voice messages", Settings > Nanogram
 * features, off by default). The same decision as iOS and macOS (OpusSpeechEnhancement.decoderOptions): our decoder is used
 * only when the switch is on AND the player plays a voice message; with the switch off the player is built exactly as
 * before and the platform decoder (MediaCodec c2.android.opus.decoder) plays the message.
 */
public final class NanogramOpusEnhancement {

    /** libopus decoder complexity 7 selects NoLACE (6 would be LACE) and lets bandwidth extension run. */
    public static final int PLAYBACK_COMPLEXITY = 7;

    private NanogramOpusEnhancement() {
    }

    /** The options of one decoder open. */
    public static final class Options {
        public final int complexity;
        public final boolean bandwidthExtension;

        Options(int complexity, boolean bandwidthExtension) {
            this.complexity = complexity;
            this.bandwidthExtension = bandwidthExtension;
        }
    }

    /** True when the player of this message gets our decoder (a voice message and the switch on). */
    public static boolean shouldUse(boolean switchOn, boolean isVoiceMessage) {
        return switchOn && isVoiceMessage;
    }

    /** The decoder options for a use, or null (the stock decoder) when the switch is off or it is not a voice message. */
    public static Options options(boolean switchOn, boolean isVoiceMessage) {
        if (!shouldUse(switchOn, isVoiceMessage)) {
            return null;
        }
        return new Options(PLAYBACK_COMPLEXITY, true);
    }
}
