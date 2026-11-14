package org.telegram.messenger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramGifDecodeFlagsTest {

    @Test
    public void flagOffNeverSkips() {
        assertFalse(NanogramGifDecodeFlags.skipLoopFilter(false, 240, 240));
    }

    @Test
    public void smallTilesSkipWhenOn() {
        assertTrue(NanogramGifDecodeFlags.skipLoopFilter(true, 320, 240));
        assertTrue(NanogramGifDecodeFlags.skipLoopFilter(true, 240, 320));
    }

    @Test
    public void largeClipsKeepLoopFilter() {
        assertFalse(NanogramGifDecodeFlags.skipLoopFilter(true, 321, 240));
        assertFalse(NanogramGifDecodeFlags.skipLoopFilter(true, 1280, 720));
    }

    @Test
    public void unknownSizeKeepsLoopFilter() {
        assertFalse(NanogramGifDecodeFlags.skipLoopFilter(true, 0, 0));
    }
}
