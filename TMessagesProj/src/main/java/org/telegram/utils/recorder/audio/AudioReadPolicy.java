package org.telegram.utils.recorder.audio;

/** What the capture thread does with the result of AudioRecord.read(). */
final class AudioReadPolicy {

    enum Action {
        DATA,
        RETRY,
        REOPEN,
        FAIL
    }

    static final int MAX_CONSECUTIVE_EMPTY_READS = 50;
    static final int MAX_REOPENS = 3;
    static final long MAX_GAP_MS = 5_000L;

    // AudioRecord.ERROR_INVALID_OPERATION and ERROR_DEAD_OBJECT, spelled out so the class works in JVM tests.
    private static final int ERROR_INVALID_OPERATION = -3;
    private static final int ERROR_DEAD_OBJECT = -6;

    private AudioReadPolicy() {
    }

    static Action next(int readResult, int consecutiveEmptyReads, int reopensSoFar) {
        if (readResult > 0) {
            return Action.DATA;
        }
        if (readResult == 0) {
            return consecutiveEmptyReads + 1 > MAX_CONSECUTIVE_EMPTY_READS ? Action.FAIL : Action.RETRY;
        }
        if ((readResult == ERROR_DEAD_OBJECT || readResult == ERROR_INVALID_OPERATION) && reopensSoFar < MAX_REOPENS) {
            return Action.REOPEN;
        }
        return Action.FAIL;
    }

    /** Bytes of silence that stand in for a gap of gapMs at the capture rate, whole samples, capped. */
    static int silenceBytes(long gapMs, int sampleRate) {
        final long capped = Math.max(0L, Math.min(gapMs, MAX_GAP_MS));
        return (int) (capped * sampleRate / 1000L) * 2;
    }
}
