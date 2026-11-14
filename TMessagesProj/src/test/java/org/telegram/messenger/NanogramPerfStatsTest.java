package org.telegram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class NanogramPerfStatsTest {

    @Before
    public void reset() {
        NanogramPerfStats.reset();
    }

    @Test
    public void keepsCountAverageAndLongest() {
        NanogramPerfStats.record("k", 2_000_000L);
        NanogramPerfStats.record("k", 6_000_000L);
        NanogramPerfStats.Snapshot s = NanogramPerfStats.snapshot().get("k");
        assertEquals(2, s.count);
        assertEquals(4.0, s.averageMillis(), 1e-9);
        assertEquals(6.0, s.maxMillis(), 1e-9);
    }

    @Test
    public void countsSlowFrames() {
        NanogramPerfStats.record(NanogramPerfStats.FRAME_TIME, 10_000_000L);
        NanogramPerfStats.record(NanogramPerfStats.FRAME_TIME, 20_000_000L);
        NanogramPerfStats.record(NanogramPerfStats.FRAME_TIME, 40_000_000L);
        NanogramPerfStats.Snapshot s = NanogramPerfStats.snapshot().get(NanogramPerfStats.FRAME_TIME);
        assertEquals(2, s.overSlow);
        assertEquals(1, s.overVerySlow);
    }

    @Test
    public void ignoresNegativeAndUnstartedDurations() {
        NanogramPerfStats.record("k", -1);
        NanogramPerfStats.recordSince("k", 0);
        assertNull(NanogramPerfStats.snapshot().get("k"));
    }

    @Test
    public void formatsLines() {
        assertEquals("k: no samples", NanogramPerfStats.formatLine("k", null));
        NanogramPerfStats.record("k", 1_500_000L);
        assertEquals("k: n=1, average 1.5 ms, longest 1.5 ms", NanogramPerfStats.formatLine("k", NanogramPerfStats.snapshot().get("k")));
        NanogramPerfStats.record(NanogramPerfStats.FRAME_TIME, 20_000_000L);
        assertTrue(NanogramPerfStats.formatLine(NanogramPerfStats.FRAME_TIME, NanogramPerfStats.snapshot().get(NanogramPerfStats.FRAME_TIME)).endsWith("over 16.7 ms 1, over 33.4 ms 0"));
    }

    @Test
    public void frameIntervalIgnoresIdleGaps() {
        assertTrue(NanogramFrameStats.isContinuous(8_300_000L));
        assertTrue(!NanogramFrameStats.isContinuous(0));
        assertTrue(!NanogramFrameStats.isContinuous(500_000_000L));
    }
}
