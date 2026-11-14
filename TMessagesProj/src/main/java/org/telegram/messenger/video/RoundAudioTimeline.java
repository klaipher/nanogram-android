package org.telegram.messenger.video;

/**
 * Timestamp arithmetic for the legacy round-video recorder: puts the camera clock and the audio clock on one
 * time base and stamps every audio buffer with the capture time of its first sample.
 */
public final class RoundAudioTimeline {

    public static final long REANCHOR_DRIFT_US = 30_000;
    public static final long ALIGN_WAIT_US = 500_000;

    /** After a microphone switch the audio clock is allowed this much lag before silence is inserted. */
    public static final long SILENCE_GUARD_US = 40_000;
    public static final long SILENCE_MAX_US = 2_000_000;

    private RoundAudioTimeline() {
    }

    /**
     * Frames of silence that put audio back on the video clock: the capture time of the next frame (actualUs) is later
     * than where the encoder input stands (expectedUs). Nothing under the guard, never negative, capped at two seconds.
     */
    public static long silenceFrames(long expectedUs, long actualUs, int sampleRate) {
        long lagUs = actualUs - expectedUs;
        if (lagUs < SILENCE_GUARD_US) {
            return 0;
        }
        return Math.min(lagUs, SILENCE_MAX_US) * sampleRate / 1_000_000L;
    }

    /**
     * Camera frame timestamps are either CLOCK_MONOTONIC (System.nanoTime) or CLOCK_BOOTTIME (elapsedRealtimeNanos, which
     * also counts deep sleep). Returns what to subtract from a frame timestamp to get CLOCK_MONOTONIC.
     */
    public static long videoClockOffsetNs(long frameTimestampNs, long nanoTimeNs, long elapsedRealtimeNs) {
        long toMonotonic = Math.abs(frameTimestampNs - nanoTimeNs);
        long toBoottime = Math.abs(frameTimestampNs - elapsedRealtimeNs);
        return toBoottime < toMonotonic ? elapsedRealtimeNs - nanoTimeNs : 0;
    }

    /** Capture time (us, CLOCK_MONOTONIC) of the frame at bufferStartFrame, from an AudioTimestamp pair. */
    public static long bufferStartUs(long tsNanoTime, long tsFramePosition, long bufferStartFrame, int sampleRate) {
        return tsNanoTime / 1000 + (bufferStartFrame - tsFramePosition) * 1_000_000L / sampleRate;
    }

    public static boolean alignmentTimedOut(long nowUs, long waitStartUs) {
        return waitStartUs >= 0 && nowUs - waitStartUs > ALIGN_WAIT_US;
    }

    public static long audioStartUs(long videoFirstUs, long firstAudioUs) {
        return Math.min(videoFirstUs, firstAudioUs);
    }

    public static final class AudioClock {

        private final int sampleRate;
        private boolean anchored;
        private long anchorUs;
        private long anchorFrame;

        public AudioClock(int sampleRate) {
            this.sampleRate = sampleRate;
        }

        public long stampUs(long startFrame, int frames, boolean timestampValid, long tsNanoTime, long tsFramePosition, long nowNanoTime) {
            long fresh = timestampValid ? bufferStartUs(tsNanoTime, tsFramePosition, startFrame, sampleRate) : Long.MIN_VALUE;
            if (!anchored) {
                if (!timestampValid) {
                    return nowNanoTime / 1000 - frames * 1_000_000L / sampleRate;
                }
                anchored = true;
                anchorUs = fresh;
                anchorFrame = startFrame;
                return fresh;
            }
            long sample = anchorUs + (startFrame - anchorFrame) * 1_000_000L / sampleRate;
            if (timestampValid && Math.abs(fresh - sample) > REANCHOR_DRIFT_US) {
                anchorUs = fresh;
                anchorFrame = startFrame;
                return fresh;
            }
            return sample;
        }
    }
}
