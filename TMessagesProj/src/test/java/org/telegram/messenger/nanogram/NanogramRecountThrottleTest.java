package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NanogramRecountThrottleTest {

    @Test
    public void firstRunAfterQuietSpellIsImmediate() {
        assertEquals(0, NanogramRecountThrottle.delayMs(100_000, 0, 0));
        assertEquals(0, NanogramRecountThrottle.delayMs(10_000, 5_000, 100));
    }

    @Test
    public void burstIsSpreadAtLeastTwoSecondsApart() {
        assertEquals(1500, NanogramRecountThrottle.delayMs(10_500, 10_000, 50));
    }

    @Test
    public void slowRecountKeepsNineTimesItsCostIdle() {
        assertEquals(8000, NanogramRecountThrottle.delayMs(11_000, 10_000, 1000));
        assertEquals(0, NanogramRecountThrottle.delayMs(19_000, 10_000, 1000));
    }
}
