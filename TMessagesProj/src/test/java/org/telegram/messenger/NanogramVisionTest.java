package org.telegram.messenger;

import static org.junit.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void failedDetectorFallsBackToZxing() {
        assertTrue(NanogramVision.useDetectorResult(true, true));
        assertFalse(NanogramVision.useDetectorResult(true, false));
        assertFalse(NanogramVision.useDetectorResult(false, true));
    }

    @Test
    public void backoffWindow() {
        assertTrue(NanogramVision.inBackoff(100, 200));
        assertFalse(NanogramVision.inBackoff(200, 200));
        assertFalse(NanogramVision.inBackoff(300, 200));
    }
}
