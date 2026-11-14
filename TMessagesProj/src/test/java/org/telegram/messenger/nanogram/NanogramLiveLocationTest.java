package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramLiveLocationTest {

    private static final int UNTIL_STOPPED = 0x7FFFFFFF;

    @Test
    public void untilStoppedNeverExpires() {
        assertFalse(NanogramLiveLocation.isExpired(1_800_000_000, UNTIL_STOPPED, 1_800_000_100));
        assertFalse(NanogramLiveLocation.isExpired(Integer.MAX_VALUE - 10, UNTIL_STOPPED, Integer.MAX_VALUE));
    }

    @Test
    public void finitePeriodExpiresAtItsEnd() {
        assertFalse(NanogramLiveLocation.isExpired(1000, 900, 1899));
        assertTrue(NanogramLiveLocation.isExpired(1000, 900, 1900));
        assertTrue(NanogramLiveLocation.isExpired(1000, 900, 2500));
    }

    @Test
    public void zeroPeriodIsOverAtOnce() {
        assertTrue(NanogramLiveLocation.isExpired(1000, 0, 1000));
    }
}
