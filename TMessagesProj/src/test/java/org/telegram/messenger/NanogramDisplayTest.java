package org.telegram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramDisplayTest {

    @Test
    public void hintsNeedAndroid15() {
        assertFalse(NanogramDisplay.supportsHints(34));
        assertTrue(NanogramDisplay.supportsHints(35));
    }

    @Test
    public void velocityHintNeedsAndroid15() {
        assertFalse(NanogramDisplay.supportsVelocityHint(34));
        assertTrue(NanogramDisplay.supportsVelocityHint(35));
    }

    @Test
    public void overlayKeepsPanelMaximumUnlessAdaptive() {
        assertEquals(120f, NanogramDisplay.overlayRefreshRate(false, 120f), 0f);
        assertEquals(0f, NanogramDisplay.overlayRefreshRate(true, 120f), 0f);
    }
}
