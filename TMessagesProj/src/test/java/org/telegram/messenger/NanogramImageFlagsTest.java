package org.telegram.messenger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramImageFlagsTest {

    @Test
    public void preuploadsSmallVisibleSoftwareBitmaps() {
        assertTrue(NanogramImageFlags.shouldPreupload(true, true, false, false, 900_000));
        assertTrue(NanogramImageFlags.shouldPreupload(true, true, false, false, NanogramImageFlags.MAX_PREUPLOAD_BYTES));
    }

    @Test
    public void skipsWhenFlagOffPrefetchLargeHardwareOrRecycled() {
        assertFalse(NanogramImageFlags.shouldPreupload(false, true, false, false, 1000));
        assertFalse(NanogramImageFlags.shouldPreupload(true, false, false, false, 1000));
        assertFalse(NanogramImageFlags.shouldPreupload(true, true, true, false, 1000));
        assertFalse(NanogramImageFlags.shouldPreupload(true, true, false, true, 1000));
        assertFalse(NanogramImageFlags.shouldPreupload(true, true, false, false, NanogramImageFlags.MAX_PREUPLOAD_BYTES + 1));
        assertFalse(NanogramImageFlags.shouldPreupload(true, true, false, false, 0));
    }
}
