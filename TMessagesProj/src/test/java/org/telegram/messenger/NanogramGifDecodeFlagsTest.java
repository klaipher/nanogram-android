package org.telegram.messenger;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NanogramGifDecodeFlagsTest {

    @Test
    public void flagOffPassesZeroToNative() {
        assertEquals(0, NanogramGifDecodeFlags.nativeMaxSide(false));
    }

    @Test
    public void flagOnPassesThreshold() {
        assertEquals(320, NanogramGifDecodeFlags.nativeMaxSide(true));
        assertEquals(NanogramGifDecodeFlags.SMALL_TILE_MAX_SIDE, NanogramGifDecodeFlags.nativeMaxSide(true));
    }
}
