package org.telegram.ui.Components;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramScratchBitmapTest {

    @Test
    public void reusesSameSizeMutableBitmap() {
        assertTrue(NanogramScratchBitmap.fits(1920, 1080, 1920, 1080, false, true));
    }

    @Test
    public void rejectsRecycledImmutableOrResized() {
        assertFalse(NanogramScratchBitmap.fits(1920, 1080, 1920, 1080, true, true));
        assertFalse(NanogramScratchBitmap.fits(1920, 1080, 1920, 1080, false, false));
        assertFalse(NanogramScratchBitmap.fits(1920, 1080, 1080, 1920, false, true));
        assertFalse(NanogramScratchBitmap.fits(0, 0, 0, 0, false, true));
    }
}
