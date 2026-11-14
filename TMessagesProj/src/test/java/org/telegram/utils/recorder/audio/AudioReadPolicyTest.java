package org.telegram.utils.recorder.audio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AudioReadPolicyTest {

    private static final int DEAD_OBJECT = -6;
    private static final int INVALID_OPERATION = -3;
    private static final int BAD_VALUE = -2;

    @Test
    public void dataIsData() {
        assertEquals(AudioReadPolicy.Action.DATA, AudioReadPolicy.next(1920, 7, 3));
    }

    @Test
    public void emptyReadsRetryThenFail() {
        assertEquals(AudioReadPolicy.Action.RETRY, AudioReadPolicy.next(0, 0, 0));
        assertEquals(AudioReadPolicy.Action.RETRY, AudioReadPolicy.next(0, AudioReadPolicy.MAX_CONSECUTIVE_EMPTY_READS - 1, 0));
        assertEquals(AudioReadPolicy.Action.FAIL, AudioReadPolicy.next(0, AudioReadPolicy.MAX_CONSECUTIVE_EMPTY_READS, 0));
    }

    @Test
    public void deadObjectAndInvalidOperationReopenUpToThreeTimes() {
        assertEquals(AudioReadPolicy.Action.REOPEN, AudioReadPolicy.next(DEAD_OBJECT, 0, 0));
        assertEquals(AudioReadPolicy.Action.REOPEN, AudioReadPolicy.next(INVALID_OPERATION, 0, 2));
        assertEquals(AudioReadPolicy.Action.FAIL, AudioReadPolicy.next(DEAD_OBJECT, 0, 3));
    }

    @Test
    public void otherErrorsFail() {
        assertEquals(AudioReadPolicy.Action.FAIL, AudioReadPolicy.next(BAD_VALUE, 0, 0));
        assertEquals(AudioReadPolicy.Action.FAIL, AudioReadPolicy.next(-1, 0, 0));
    }

    @Test
    public void silenceForTheGapIsWholeSamplesAndCapped() {
        assertEquals(96000, AudioReadPolicy.silenceBytes(1000, 48000));
        assertEquals(0, AudioReadPolicy.silenceBytes(-5, 48000));
        assertEquals(AudioReadPolicy.silenceBytes(5000, 48000), AudioReadPolicy.silenceBytes(60000, 48000));
        assertEquals(0, AudioReadPolicy.silenceBytes(1000, 48000) % 2);
    }
}
