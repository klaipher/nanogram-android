package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class NanogramProfilingTest {

    @Test
    public void keepsNewestWithinCaps() {
        assertTrue(NanogramProfiling.indexesToDelete(new long[]{10, 10, 10}).isEmpty());
    }

    @Test
    public void dropsBeyondFileCount() {
        final long[] sizes = new long[NanogramProfiling.MAX_FILES + 2];
        Arrays.fill(sizes, 1);
        assertEquals(Arrays.asList(NanogramProfiling.MAX_FILES, NanogramProfiling.MAX_FILES + 1), NanogramProfiling.indexesToDelete(sizes));
    }

    @Test
    public void dropsWhenTotalTooBig() {
        final long half = NanogramProfiling.MAX_TOTAL_BYTES / 2 + 1;
        assertEquals(Arrays.asList(1, 2), NanogramProfiling.indexesToDelete(new long[]{half, half, 1}));
    }

    @Test
    public void manualResultIsNotStoredTwice() {
        assertFalse(NanogramProfiling.shouldStoreTriggerResult("manual"));
        assertTrue(NanogramProfiling.shouldStoreTriggerResult("trigger"));
        assertTrue(NanogramProfiling.shouldStoreTriggerResult(null));
    }

    @Test
    public void unknownTriggerConstantIsNotSupportedAndDoesNotThrow() {
        assertNull(NanogramProfiling.triggerType("TRIGGER_TYPE_DOES_NOT_EXIST"));
    }

    @Test
    public void coldStartSupportFollowsTheTriggerLookup() {
        assertEquals(NanogramProfiling.triggerType("TRIGGER_TYPE_COLD_START") != null, NanogramProfiling.coldStartTriggerSupported());
    }
}
