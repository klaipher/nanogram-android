package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramPhotoPickerTest {

    @Test
    public void usesPickerOnlyWhenFlagAndSdkAllow() {
        assertTrue(NanogramPhotoPicker.canUse(true, 33));
        assertFalse(NanogramPhotoPicker.canUse(false, 34));
        assertFalse(NanogramPhotoPicker.canUse(true, 32));
    }

    @Test
    public void detectsVideoMimeTypes() {
        assertTrue(NanogramPhotoPicker.isVideoType("video/mp4"));
        assertFalse(NanogramPhotoPicker.isVideoType("image/jpeg"));
        assertFalse(NanogramPhotoPicker.isVideoType(null));
    }
}
