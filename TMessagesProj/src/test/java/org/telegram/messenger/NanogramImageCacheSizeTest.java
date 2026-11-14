package org.telegram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramImageCacheSizeTest {

    private static final long GB = 1024L * 1024L * 1024L;

    @Test
    public void upstreamValuesAreKept() {
        assertEquals(30, NanogramImageCacheSize.upstreamMegabytes(512));
        assertEquals(30, NanogramImageCacheSize.upstreamMegabytes(256));
        assertEquals(15, NanogramImageCacheSize.upstreamMegabytes(128));
        assertEquals(14, NanogramImageCacheSize.upstreamMegabytes(100));
    }

    @Test
    public void lowClassLowRamAndSmallDevicesAreUnchanged() {
        assertEquals(30, NanogramImageCacheSize.megabytes(256, NanogramImageCacheSize.CLASS_LOW, false, 8 * GB));
        assertEquals(30, NanogramImageCacheSize.megabytes(256, NanogramImageCacheSize.CLASS_HIGH, true, 8 * GB));
        assertEquals(30, NanogramImageCacheSize.megabytes(256, NanogramImageCacheSize.CLASS_HIGH, false, 3 * GB));
        assertEquals(15, NanogramImageCacheSize.megabytes(128, NanogramImageCacheSize.CLASS_HIGH, false, 8 * GB));
    }

    @Test
    public void highClassBigRamGetsLargerCache() {
        assertEquals(48, NanogramImageCacheSize.megabytes(512, NanogramImageCacheSize.CLASS_HIGH, false, 12 * GB));
        assertEquals(48, NanogramImageCacheSize.megabytes(256, NanogramImageCacheSize.CLASS_HIGH, false, 8 * GB));
    }

    @Test
    public void averageClassIsModest() {
        assertEquals(32, NanogramImageCacheSize.megabytes(256, NanogramImageCacheSize.CLASS_AVERAGE, false, 6 * GB));
        assertEquals(32, NanogramImageCacheSize.megabytes(256, NanogramImageCacheSize.CLASS_HIGH, false, 5 * GB));
    }

    @Test
    public void neverBelowUpstream() {
        for (int memoryClass : new int[]{64, 128, 192, 256, 384, 512}) {
            for (int perf = 0; perf <= 2; perf++) {
                assertTrue(NanogramImageCacheSize.megabytes(memoryClass, perf, false, 8 * GB) >= NanogramImageCacheSize.upstreamMegabytes(memoryClass));
            }
        }
    }
}
