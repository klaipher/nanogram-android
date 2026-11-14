package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramPhotoPickerTest {

    @Test
    public void usesPickerOnlyWhenFlagSdkAndAvailabilityAllow() {
        assertTrue(NanogramPhotoPicker.canUse(true, 33, true));
        assertFalse(NanogramPhotoPicker.canUse(false, 34, true));
        assertFalse(NanogramPhotoPicker.canUse(true, 32, true));
        assertFalse(NanogramPhotoPicker.canUse(true, 34, false));
    }

    @Test
    public void detectsVideoMimeTypes() {
        assertTrue(NanogramPhotoPicker.isVideoType("video/mp4"));
        assertFalse(NanogramPhotoPicker.isVideoType("image/jpeg"));
        assertFalse(NanogramPhotoPicker.isVideoType(null));
    }
}
