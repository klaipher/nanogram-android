package org.telegram.messenger.camera;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramCameraSupportTest {

    @Test
    public void findsAdvertisedMode() {
        assertTrue(NanogramCameraSupport.contains(new int[]{0, 1}, 1));
    }

    @Test
    public void missingOrNullModesAreUnsupported() {
        assertFalse(NanogramCameraSupport.contains(new int[]{0}, 1));
        assertFalse(NanogramCameraSupport.contains(new int[0], 1));
        assertFalse(NanogramCameraSupport.contains(null, 1));
    }
}
