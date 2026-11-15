package org.telegram.utils.recorder.round;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

public class RoundVideoZoomPlanTest {

    private static final float EPS = 1e-5f;

    @Test
    public void minimumRatioIsOneWhenOff() {
        assertEquals(1f, RoundVideoZoomPlan.minimumRatio(0.5f, false), 0f);
    }

    @Test
    public void minimumRatioUsesLowerBoundWhenOn() {
        assertEquals(0.7f, RoundVideoZoomPlan.minimumRatio(0.7f, true), 0f);
        assertEquals(0.5f, RoundVideoZoomPlan.minimumRatio(0.5f, true), 0f);
    }

    @Test
    public void minimumRatioIgnoresNearOneAndInvalid() {
        assertEquals(1f, RoundVideoZoomPlan.minimumRatio(0.96f, true), 0f);
        assertEquals(1f, RoundVideoZoomPlan.minimumRatio(1f, true), 0f);
        assertEquals(1f, RoundVideoZoomPlan.minimumRatio(null, true), 0f);
        assertEquals(1f, RoundVideoZoomPlan.minimumRatio(Float.NaN, true), 0f);
        assertEquals(1f, RoundVideoZoomPlan.minimumRatio(0f, true), 0f);
        assertEquals(1f, RoundVideoZoomPlan.minimumRatio(-1f, true), 0f);
    }

    @Test
    public void restingRatioAndLensRatio() {
        assertEquals(0.5f, RoundVideoZoomPlan.restingRatio(0.5f), 0f);
        assertEquals(0.5f, RoundVideoZoomPlan.lensRatio(true, 0.5f), 0f);
        assertEquals(1f, RoundVideoZoomPlan.lensRatio(false, 0.5f), 0f);
        assertEquals(1f, RoundVideoZoomPlan.lensRatio(true, 1f), 0f);
    }

    @Test
    public void stops() {
        assertArrayEquals(new float[]{0.5f, 1f}, RoundVideoZoomPlan.stops(0.5f, 4f), 0f);
        assertArrayEquals(new float[]{1f}, RoundVideoZoomPlan.stops(1f, 4f), 0f);
    }

    @Test
    public void labels() {
        assertEquals("0.7x", RoundVideoZoomPlan.labelFor(0.7f));
        assertEquals("1x", RoundVideoZoomPlan.labelFor(1f));
        assertEquals("1x", RoundVideoZoomPlan.labelFor(0.995f));
        assertEquals("0.5x", RoundVideoZoomPlan.labelFor(0.5f));
    }

    @Test
    public void labelsIgnoreDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("de-DE"));
            assertEquals("0.7x", RoundVideoZoomPlan.labelFor(0.7f));
            assertEquals("1x", RoundVideoZoomPlan.labelFor(1f));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void pinchMatchesTodayWhenSwitchIsOff() {
        float effort = 1.5f;
        for (float scale : new float[]{0.5f, 1f, 1.3f, 2f, 5f}) {
            float today = Math.min(4f, 1f + Math.max(0f, scale - 1f) / effort);
            assertEquals(today, RoundVideoZoomPlan.pinchRatio(1f, scale, 1f, 4f, effort), EPS);
        }
    }

    @Test
    public void pinchInStopsAtMinimumWhenWide() {
        assertEquals(0.5f, RoundVideoZoomPlan.pinchRatio(1f, 0.1f, 0.5f, 4f, 1.5f), EPS);
        assertEquals(1f - 0.15f / 1.5f, RoundVideoZoomPlan.pinchRatio(1f, 0.85f, 0.5f, 4f, 1.5f), EPS);
        assertEquals(0.7f + 0.3f / 1.5f, RoundVideoZoomPlan.pinchRatio(0.7f, 1.3f, 0.7f, 4f, 1.5f), EPS);
    }

    @Test
    public void pinchClampsToMaximum() {
        assertEquals(4f, RoundVideoZoomPlan.pinchRatio(3f, 10f, 0.5f, 4f, 1.5f), 0f);
    }

    @Test
    public void progressRoundTrip() {
        float max = 4f;
        for (float progress : new float[]{0f, 0.25f, 0.5f, 1f}) {
            float ratio = RoundVideoZoomPlan.progressToRatio(progress, max);
            assertEquals(progress, RoundVideoZoomPlan.ratioToProgress(ratio, max), EPS);
        }
        assertEquals(1f, RoundVideoZoomPlan.progressToRatio(0f, max), 0f);
        assertEquals(0f, RoundVideoZoomPlan.ratioToProgress(0.5f, max), 0f);
        assertEquals(0f, RoundVideoZoomPlan.ratioToProgress(2f, 1f), 0f);
    }
}
