package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramCropMathTest {

    private static final long NO_LIMIT = Long.MAX_VALUE;

    @Test
    public void samplesDownOnlyAsFarAsTheCropAllows() {
        assertEquals(1, NanogramCropMath.computeSampleSize(8000, 6000, 0.25f, 0.25f, 1f, 0, 2560, NO_LIMIT));
        assertEquals(2, NanogramCropMath.computeSampleSize(8000, 6000, 1f, 1f, 1f, 0, 2560, NO_LIMIT));
    }

    @Test
    public void countsTheLongSideAfterRotation() {
        assertEquals(4, NanogramCropMath.computeSampleSize(8000, 4000, 0.25f, 0.5f, 1f, 0, 500, NO_LIMIT));
        assertEquals(8, NanogramCropMath.computeSampleSize(8000, 4000, 0.25f, 0.5f, 1f, 90, 500, NO_LIMIT));
    }

    @Test
    public void rotationsAddUpModuloAFullTurn() {
        assertEquals(NanogramCropMath.computeSampleSize(8000, 4000, 0.25f, 0.5f, 1f, 90, 500, NO_LIMIT),
                NanogramCropMath.computeSampleSize(8000, 4000, 0.25f, 0.5f, 1f, 450, 500, NO_LIMIT));
    }

    @Test
    public void neverSamplesCoarserThanTheWholeImagePath() {
        assertEquals(1, NanogramCropMath.computeSampleSize(8000, 2000, 1f, 0.5f, 1f, 0, 1280, NO_LIMIT));
        assertEquals(1, NanogramCropMath.computeSampleSize(8000, 2000, 1f, 0.5f, 1f, 0, 1280, 128L * 1024 * 1024));
    }

    @Test
    public void countsTheMagnificationOfAZoomedCrop() {
        assertEquals(4, NanogramCropMath.computeSampleSize(16000, 16000, 0.5f, 0.5f, 1f, 0, 1280, NO_LIMIT));
        assertEquals(2, NanogramCropMath.computeSampleSize(16000, 16000, 0.5f, 0.5f, 2f, 0, 1280, NO_LIMIT));
    }

    @Test
    public void fallsBackWhenTheDecodeDoesNotFit() {
        assertEquals(1, NanogramCropMath.computeSampleSize(4000, 3000, 0.5f, 0.5f, 1f, 0, 2560, NO_LIMIT));
        assertEquals(0, NanogramCropMath.computeSampleSize(4000, 3000, 0.5f, 0.5f, 1f, 0, 2560, 32L * 1024 * 1024));
        assertTrue(NanogramCropMath.decodedBytes(4000, 3000, 2000f, 1500f, 1) <= 64L * 1024 * 1024);
    }

    @Test
    public void refusesADegenerateCrop() {
        assertEquals(0, NanogramCropMath.computeSampleSize(4000, 3000, 0f, 0f, 1f, 0, 1280, NO_LIMIT));
    }
}
