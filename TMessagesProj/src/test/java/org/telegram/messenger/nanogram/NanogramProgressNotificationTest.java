package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramProgressNotificationTest {

    @Test
    public void clampsPercent() {
        assertEquals(0, NanogramProgressNotification.clampPercent(-5));
        assertEquals(42, NanogramProgressNotification.clampPercent(42));
        assertEquals(100, NanogramProgressNotification.clampPercent(250));
    }

    @Test
    public void shortTextIsClampedPercent() {
        assertEquals("0%", NanogramProgressNotification.shortText(-1));
        assertEquals("67%", NanogramProgressNotification.shortText(67));
        assertEquals("100%", NanogramProgressNotification.shortText(101));
    }

    @Test
    public void promotionNeedsApi361() {
        assertFalse(NanogramProgressNotification.supportsPromotion(3600000));
        assertTrue(NanogramProgressNotification.supportsPromotion(3600001));
        assertTrue(NanogramProgressNotification.supportsPromotion(3700000));
    }
}
