package org.telegram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramGalleryReloadTest {

    @Test
    public void pausedAppOnlyMarksTheGalleryDirty() {
        assertEquals(NanogramGalleryReload.DELAY_NO_RELOAD, NanogramGalleryReload.observerDelayMs(true, true));
        assertEquals(NanogramGalleryReload.DELAY_NO_RELOAD, NanogramGalleryReload.observerDelayMs(true, false));
    }

    @Test
    public void visiblePickerReloadsQuicklyOthersKeepTwoSeconds() {
        assertEquals(200, NanogramGalleryReload.observerDelayMs(false, true));
        assertEquals(2000, NanogramGalleryReload.observerDelayMs(false, false));
    }

    @Test
    public void openingThePickerReloadsOnlyWhenTheGalleryChanged() {
        assertTrue(NanogramGalleryReload.shouldReloadOnOpen(true, false));
        assertTrue(NanogramGalleryReload.shouldReloadOnOpen(false, true));
        assertFalse(NanogramGalleryReload.shouldReloadOnOpen(false, false));
    }

    @Test
    public void videosAreOnlyComparedWithVideos() {
        assertFalse(NanogramGalleryReload.countsChanged(100, 100, 7, 7));
        assertTrue(NanogramGalleryReload.countsChanged(100, 101, 7, 7));
        assertTrue(NanogramGalleryReload.countsChanged(100, 100, 7, 8));
        assertFalse(NanogramGalleryReload.countsChanged(100, 100, -1, 9));
    }
}
