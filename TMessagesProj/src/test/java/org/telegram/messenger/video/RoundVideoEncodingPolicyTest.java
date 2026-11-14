package org.telegram.messenger.video;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoundVideoEncodingPolicyTest {

    @Test
    public void lowClassOrFlagOffKeepsServerValues() {
        assertFalse(RoundVideoEncodingPolicy.boosted(true, 0));
        assertFalse(RoundVideoEncodingPolicy.boosted(false, 2));
        assertEquals(384, RoundVideoEncodingPolicy.size(384, false));
        assertEquals(1_048_576, RoundVideoEncodingPolicy.bitrate(1_048_576, 384, 384, false));
        assertEquals(384, RoundVideoEncodingPolicy.cameraRequestSize(384, false));
        assertEquals(25, RoundVideoEncodingPolicy.frameRate(25, false));
    }

    @Test
    public void averageAndHighGetAtLeast480() {
        assertTrue(RoundVideoEncodingPolicy.boosted(true, 1));
        assertEquals(480, RoundVideoEncodingPolicy.size(384, true));
        assertEquals(480, RoundVideoEncodingPolicy.size(400, true));
    }

    @Test
    public void largerServerValueIsHonouredButCappedAndEven() {
        assertEquals(640, RoundVideoEncodingPolicy.size(640, true));
        assertEquals(640, RoundVideoEncodingPolicy.size(1080, true));
        assertEquals(500, RoundVideoEncodingPolicy.size(501, true));
        assertEquals(0, RoundVideoEncodingPolicy.size(501, true) % 2);
    }

    @Test
    public void bitrateScalesWithAreaWithFloorAndCap() {
        int size = RoundVideoEncodingPolicy.size(384, true);
        int bitrate = RoundVideoEncodingPolicy.bitrate(1_048_576, 384, size, true);
        assertTrue(bitrate > 1_048_576);
        assertTrue(bitrate <= 2_000_000);
        assertEquals(1_382_400, bitrate);
        assertEquals(2_000_000, RoundVideoEncodingPolicy.bitrate(1_048_576, 384, 640, true));
        assertEquals(3_000_000, RoundVideoEncodingPolicy.bitrate(3_000_000, 384, 480, true));
        assertEquals(1_048_576, RoundVideoEncodingPolicy.bitrate(1_048_576, 480, 480, true));
    }

    @Test
    public void cameraRequestIsAboutDoubleAndCapped() {
        assertEquals(960, RoundVideoEncodingPolicy.cameraRequestSize(480, true));
        assertEquals(960, RoundVideoEncodingPolicy.cameraRequestSize(720, true));
        assertEquals(960, RoundVideoEncodingPolicy.cameraRequestSize(960, true));
        assertEquals(30, RoundVideoEncodingPolicy.frameRate(25, true));
    }

    @Test
    public void hugeCameraStreamsAreRejected() {
        assertTrue(RoundVideoEncodingPolicy.cameraSizeAcceptable(960, 1280, 960));
        assertTrue(RoundVideoEncodingPolicy.cameraSizeAcceptable(960, 1920, 1080));
        assertFalse(RoundVideoEncodingPolicy.cameraSizeAcceptable(960, 4000, 3000));
    }

    @Test
    public void highProfilePicksLowestLevelFromThreeOne() {
        assertArrayEquals(new int[]{8, 0x200}, RoundVideoEncodingPolicy.pickHighProfile(new int[]{1, 8, 8, 8}, new int[]{0x200, 0x800, 0x200, 0x400}));
        assertArrayEquals(new int[]{8, 0x400}, RoundVideoEncodingPolicy.pickHighProfile(new int[]{8, 8}, new int[]{0x100, 0x400}));
        assertNull(RoundVideoEncodingPolicy.pickHighProfile(new int[]{1, 2}, new int[]{0x800, 0x800}));
        assertNull(RoundVideoEncodingPolicy.pickHighProfile(new int[]{8}, new int[]{0x100}));
        assertNull(RoundVideoEncodingPolicy.pickHighProfile(null, null));
    }
}
