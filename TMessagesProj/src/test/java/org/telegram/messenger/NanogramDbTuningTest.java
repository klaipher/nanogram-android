package org.telegram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class NanogramDbTuningTest {

    @Test
    public void flagOffAddsNothing() {
        assertTrue(NanogramDbTuning.pragmas(false, 2, true).isEmpty());
    }

    @Test
    public void lowClassOnlyCheckpoint() {
        assertEquals(Arrays.asList("PRAGMA wal_autocheckpoint = 1000", "PRAGMA optimize=0x10002"), NanogramDbTuning.pragmas(true, 0, true));
    }

    @Test
    public void averageClassGetsCacheNoMmap() {
        assertEquals(Arrays.asList("PRAGMA cache_size = -8192", "PRAGMA wal_autocheckpoint = 1000", "PRAGMA optimize=0x10002"), NanogramDbTuning.pragmas(true, 1, true));
    }

    @Test
    public void highClass64BitGetsMmap() {
        assertEquals(Arrays.asList("PRAGMA cache_size = -8192", "PRAGMA mmap_size = 67108864", "PRAGMA wal_autocheckpoint = 1000", "PRAGMA optimize=0x10002"), NanogramDbTuning.pragmas(true, 2, true));
        assertEquals(Arrays.asList("PRAGMA cache_size = -8192", "PRAGMA wal_autocheckpoint = 1000", "PRAGMA optimize=0x10002"), NanogramDbTuning.pragmas(true, 2, false));
    }

    @Test
    public void closePragmaOnlyWhenFlagOn() {
        assertEquals(Arrays.asList("PRAGMA optimize"), NanogramDbTuning.closePragmas(true));
        assertTrue(NanogramDbTuning.closePragmas(false).isEmpty());
    }

    @Test
    public void filePathDatabaseGetsSmallCacheAndOptimize() {
        assertTrue(NanogramDbTuning.lightPragmas(false).isEmpty());
        assertEquals(Arrays.asList("PRAGMA cache_size = -2048", "PRAGMA optimize=0x10002"), NanogramDbTuning.lightPragmas(true));
    }
}
