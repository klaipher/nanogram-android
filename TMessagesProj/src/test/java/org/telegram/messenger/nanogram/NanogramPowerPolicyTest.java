package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramPowerPolicyTest {

    @Test
    public void systemBatterySaverApplies() {
        assertTrue(NanogramPowerPolicy.shouldApply(0, 90, false, false, true, 0));
    }

    @Test
    public void severeThermalApplies() {
        assertTrue(NanogramPowerPolicy.shouldApply(0, 90, false, false, false, 3));
        assertTrue(NanogramPowerPolicy.shouldApply(0, 90, false, false, false, 5));
    }

    @Test
    public void moderateThermalDoesNotApply() {
        assertFalse(NanogramPowerPolicy.shouldApply(0, 90, false, false, false, 2));
    }

    @Test
    public void percentThresholdUnchanged() {
        assertTrue(NanogramPowerPolicy.shouldApply(20, 20, false, false, false, 0));
        assertFalse(NanogramPowerPolicy.shouldApply(20, 21, false, false, false, 0));
        assertFalse(NanogramPowerPolicy.shouldApply(20, -1, false, false, false, 0));
        assertFalse(NanogramPowerPolicy.shouldApply(0, 5, false, false, false, 0));
        assertTrue(NanogramPowerPolicy.shouldApply(100, -1, false, false, false, 0));
    }

    @Test
    public void chargingSkipOnlyWhenEnabled() {
        assertTrue(NanogramPowerPolicy.shouldApply(20, 9, true, false, false, 0));
        assertFalse(NanogramPowerPolicy.shouldApply(20, 9, true, true, false, 0));
        assertTrue(NanogramPowerPolicy.shouldApply(20, 9, false, true, false, 0));
    }

    @Test
    public void systemSignalsBeatChargingSkip() {
        assertTrue(NanogramPowerPolicy.shouldApply(20, 9, true, true, true, 0));
        assertTrue(NanogramPowerPolicy.shouldApply(20, 9, true, true, false, 4));
    }
}
