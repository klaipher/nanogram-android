package org.telegram.utils.recorder.audio;

import java.nio.ByteBuffer;

/**
 * Drops the exact-zero samples a Bluetooth link delivers while it is still starting, from the very start of a
 * capture and for at most maxSamples. Harmless when there are none: the first non-zero sample ends the trimming.
 */
final class LeadingSilenceTrimmer {

    private long remainingSamples;
    private boolean done;
    private long trimmedSamples;

    LeadingSilenceTrimmer(long maxSamples) {
        remainingSamples = maxSamples;
        done = maxSamples <= 0;
    }

    /** Returns how many bytes (always even) at the start of chunk[0, byteCount) to drop. */
    int leadingBytesToDrop(ByteBuffer chunk, int byteCount) {
        if (done) {
            return 0;
        }
        final int samples = byteCount / 2;
        int zeros = 0;
        while (zeros < samples && zeros < remainingSamples && chunk.getShort(zeros * 2) == 0) {
            zeros++;
        }
        remainingSamples -= zeros;
        trimmedSamples += zeros;
        if (zeros < samples || remainingSamples <= 0) {
            done = true;
        }
        return zeros * 2;
    }

    long trimmedSamples() {
        return trimmedSamples;
    }
}
