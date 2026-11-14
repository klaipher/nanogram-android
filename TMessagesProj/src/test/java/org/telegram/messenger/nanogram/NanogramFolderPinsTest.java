package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramFolderPinsTest {

    @Test
    public void existingPinsCancelOutOfTheComparison() {
        final int alwaysShow = 30;
        final int pinned = 12;
        final int max = NanogramFolderPins.maxPinned(100, alwaysShow, pinned);
        assertEquals(82, max);
        assertTrue(pinned + 1 <= max);
    }

    @Test
    public void pinIsRefusedOnlyWhenTheFolderIsFull() {
        final int max = NanogramFolderPins.maxPinned(100, 100, 5);
        assertEquals(5, max);
        assertTrue(5 + 1 > max);
    }

    @Test
    public void premiumLimitIsHigher() {
        assertTrue(NanogramFolderPins.maxPinned(200, 100, 3) > NanogramFolderPins.maxPinned(100, 100, 3));
    }
}
