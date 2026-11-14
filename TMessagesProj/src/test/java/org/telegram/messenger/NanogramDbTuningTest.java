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
        assertEquals(Arrays.asList("PRAGMA wal_autocheckpoint = 1000"), NanogramDbTuning.pragmas(true, 0, true));
    }

    @Test
    public void averageClassGetsCacheNoMmap() {
        assertEquals(Arrays.asList("PRAGMA cache_size = -8192", "PRAGMA wal_autocheckpoint = 1000"), NanogramDbTuning.pragmas(true, 1, true));
    }

    @Test
    public void highClass64BitGetsMmap() {
        assertEquals(Arrays.asList("PRAGMA cache_size = -8192", "PRAGMA mmap_size = 67108864", "PRAGMA wal_autocheckpoint = 1000"), NanogramDbTuning.pragmas(true, 2, true));
        assertEquals(Arrays.asList("PRAGMA cache_size = -8192", "PRAGMA wal_autocheckpoint = 1000"), NanogramDbTuning.pragmas(true, 2, false));
    }
}
