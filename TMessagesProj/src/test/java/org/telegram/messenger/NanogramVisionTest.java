package org.telegram.messenger;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NanogramVisionTest {

    @Test
    public void rotationKeepsRightAngles() {
        assertEquals(0, NanogramVision.normalizeRotation(0));
        assertEquals(90, NanogramVision.normalizeRotation(90));
        assertEquals(180, NanogramVision.normalizeRotation(180));
        assertEquals(270, NanogramVision.normalizeRotation(270));
    }

    @Test
    public void rotationWrapsAndRejectsOddAngles() {
        assertEquals(0, NanogramVision.normalizeRotation(360));
        assertEquals(90, NanogramVision.normalizeRotation(450));
        assertEquals(270, NanogramVision.normalizeRotation(-90));
        assertEquals(0, NanogramVision.normalizeRotation(45));
    }
}
