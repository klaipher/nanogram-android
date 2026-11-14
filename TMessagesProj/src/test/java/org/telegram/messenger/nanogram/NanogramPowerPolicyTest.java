package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramPowerPolicyTest {

    @Test
    public void systemBatterySaverApplies() {
        assertTrue(NanogramPowerPolicy.shouldApply(0, 90, false, false, true, true, 0));
    }

    @Test
    public void severeThermalApplies() {
        assertTrue(NanogramPowerPolicy.shouldApply(0, 90, false, false, true, false, 3));
        assertTrue(NanogramPowerPolicy.shouldApply(0, 90, false, false, true, false, 5));
    }

    @Test
    public void moderateThermalDoesNotApply() {
        assertFalse(NanogramPowerPolicy.shouldApply(0, 90, false, false, true, false, 2));
    }

    @Test
    public void percentThresholdUnchanged() {
        assertTrue(NanogramPowerPolicy.shouldApply(20, 20, false, false, true, false, 0));
        assertFalse(NanogramPowerPolicy.shouldApply(20, 21, false, false, true, false, 0));
        assertFalse(NanogramPowerPolicy.shouldApply(20, -1, false, false, true, false, 0));
        assertFalse(NanogramPowerPolicy.shouldApply(0, 5, false, false, true, false, 0));
        assertTrue(NanogramPowerPolicy.shouldApply(100, -1, false, false, true, false, 0));
    }

    @Test
    public void chargingSkipOnlyWhenEnabled() {
        assertTrue(NanogramPowerPolicy.shouldApply(20, 9, true, false, true, false, 0));
        assertFalse(NanogramPowerPolicy.shouldApply(20, 9, true, true, true, false, 0));
        assertTrue(NanogramPowerPolicy.shouldApply(20, 9, false, true, true, false, 0));
    }

    @Test
    public void systemSignalsBeatChargingSkip() {
        assertTrue(NanogramPowerPolicy.shouldApply(20, 9, true, true, true, true, 0));
        assertTrue(NanogramPowerPolicy.shouldApply(20, 9, true, true, true, false, 4));
    }

    @Test
    public void optOutIgnoresSystemSignals() {
        assertFalse(NanogramPowerPolicy.shouldApply(0, 90, false, false, false, true, 5));
        assertFalse(NanogramPowerPolicy.shouldApply(20, 90, false, false, false, true, 3));
        assertTrue(NanogramPowerPolicy.shouldApply(20, 10, false, false, false, true, 3));
        assertTrue(NanogramPowerPolicy.shouldApply(100, -1, false, false, false, true, 0));
    }

    @Test
    public void systemSignalHelper() {
        assertTrue(NanogramPowerPolicy.systemSignal(true, true, 0));
        assertTrue(NanogramPowerPolicy.systemSignal(true, false, 3));
        assertFalse(NanogramPowerPolicy.systemSignal(false, true, 5));
        assertFalse(NanogramPowerPolicy.systemSignal(true, false, 2));
    }

    @Test
    public void hotForecastAppliesOnlyWhenFollowingSystem() {
        assertTrue(NanogramPowerPolicy.systemSignal(true, false, 2, true));
        assertFalse(NanogramPowerPolicy.systemSignal(false, false, 2, true));
        assertFalse(NanogramPowerPolicy.systemSignal(true, false, 2, false));
    }

    @Test
    public void forecastEntersAtNinetyPercent() {
        final NanogramPowerPolicy.ThermalForecast f = new NanogramPowerPolicy.ThermalForecast();
        assertFalse(f.update(0.89f, true, 0));
        assertTrue(f.update(0.9f, true, 1));
    }

    @Test
    public void forecastIgnoresReadingsWithoutActiveWork() {
        final NanogramPowerPolicy.ThermalForecast f = new NanogramPowerPolicy.ThermalForecast();
        assertFalse(f.update(1.2f, false, 0));
    }

    @Test
    public void forecastIgnoresNaN() {
        final NanogramPowerPolicy.ThermalForecast f = new NanogramPowerPolicy.ThermalForecast();
        assertFalse(f.update(Float.NaN, true, 0));
        f.update(0.95f, true, 1);
        assertTrue(f.update(Float.NaN, true, 100_000));
    }

    @Test
    public void forecastHoldsThenReleasesWithHysteresis() {
        final NanogramPowerPolicy.ThermalForecast f = new NanogramPowerPolicy.ThermalForecast();
        f.update(0.95f, true, 0);
        assertTrue(f.update(0.5f, true, 10_000));
        assertTrue(f.update(0.8f, true, 40_000));
        assertFalse(f.update(0.7f, true, 41_000));
    }

    @Test
    public void forecastReleasesAfterHoldWhenWorkEnds() {
        final NanogramPowerPolicy.ThermalForecast f = new NanogramPowerPolicy.ThermalForecast();
        f.update(0.95f, true, 0);
        assertTrue(f.update(0.95f, false, 10_000));
        assertFalse(f.update(0.95f, false, 30_000));
    }
}
