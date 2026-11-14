package org.telegram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

public class NanogramHealthMathTest {

    private static Map<String, Long> map(Object... kv) {
        TreeMap<String, Long> result = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            result.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        }
        return result;
    }

    private static NanogramHealthMath.Sample sample(long elapsed, Map<String, Long> counters, Map<String, Long> energy, boolean supported) {
        return new NanogramHealthMath.Sample(elapsed, counters, energy, supported, null);
    }

    @Test
    public void deltaDropsResetCounters() {
        Map<String, Long> d = NanogramHealthMath.delta(map("a", 10, "b", 5, "c", 7), map("a", 4, "b", 9));
        assertEquals(6L, (long) d.get("a"));
        assertNull(d.get("b"));
        assertNull(d.get("c"));
    }

    @Test
    public void counterDeltaCountsNewTimersFromZero() {
        Map<String, Long> d = NanogramHealthMath.counterDelta(map("a", 10, "new", 3), map("a", 4));
        assertEquals(6L, (long) d.get("a"));
        assertEquals(3L, (long) d.get("new"));
    }

    @Test
    public void perMinuteNormalises() {
        assertEquals(600.0, NanogramHealthMath.perMinute(300, 30_000), 1e-9);
        assertEquals(100.0, NanogramHealthMath.perMinute(200, 120_000), 1e-9);
        assertTrue(Double.isNaN(NanogramHealthMath.perMinute(5, 0)));
    }

    @Test
    public void microwattSecondsPerMillisecondAreMilliwatts() {
        assertEquals(50.0, NanogramHealthMath.averageMilliwatts(3_000_000L, 60_000L), 1e-9);
        assertEquals(3000.0, NanogramHealthMath.millijoules(3_000_000L), 1e-9);
    }

    @Test
    public void powerMonitorsNeedApi35AndAList() {
        assertFalse(NanogramHealthMath.powerMonitorsUsable(34, 5));
        assertFalse(NanogramHealthMath.powerMonitorsUsable(35, 0));
        assertTrue(NanogramHealthMath.powerMonitorsUsable(35, 1));
        assertTrue(NanogramHealthMath.powerMonitorsUsable(36, 20));
    }

    @Test
    public void sumsRailsAndConsumersSeparately() {
        Map<String, Long> e = map("rail:A", 100, "rail:B", 50, "consumer:CPU", 70);
        long[] rails = NanogramHealthMath.sumEnergy(e, NanogramHealthMath.RAIL_PREFIX);
        long[] consumers = NanogramHealthMath.sumEnergy(e, NanogramHealthMath.CONSUMER_PREFIX);
        assertEquals(150, rails[0]);
        assertEquals(2, rails[1]);
        assertEquals(70, consumers[0]);
        assertEquals(1, consumers[1]);
    }

    @Test
    public void cpuLineGivesRatesPerWallAndScreenMinute() {
        Map<String, Long> d = map(NanogramHealthMath.CPU_USER, 3000, NanogramHealthMath.CPU_SYSTEM, 1000, NanogramHealthMath.STATE_TOP, 120_000);
        String line = NanogramHealthMath.cpuLine(d, 240_000);
        assertTrue(line, line.contains("CPU time 4000 ms (user 3000, system 1000) over 4.0 min"));
        assertTrue(line, line.contains("1000.0 ms/min"));
        assertTrue(line, line.contains("2000.0 ms per minute on screen (2.0 min on screen)"));
    }

    @Test
    public void shortSpanHasNoRate() {
        String line = NanogramHealthMath.cpuLine(map(NanogramHealthMath.CPU_USER, 100), 20_000);
        assertTrue(line, line.contains("under 30 s"));
        assertFalse(line, line.contains("ms/min"));
    }

    @Test
    public void unsupportedEnergySaysSo() {
        String text = NanogramHealthMath.experimentText(sample(0, map(), map(), false), sample(120_000, map(NanogramHealthMath.CPU_USER, 1200), map(), false));
        assertTrue(text, text.contains("Energy: not available (this phone has no power monitors"));
        assertTrue(text, text.contains("600.0 ms/min"));
    }

    @Test
    public void supportedEnergyShowsPerMinute() {
        Sample2 s = new Sample2();
        String text = NanogramHealthMath.experimentText(s.base, s.now);
        assertTrue(text, text.contains("rails (1) 3000.0 mJ/min, avg 50.0 mW"));
        assertTrue(text, text.contains("modeled consumers (1) 600.0 mJ/min, avg 10.0 mW"));
    }

    private static final class Sample2 {
        final NanogramHealthMath.Sample base = sample(1_000, map(NanogramHealthMath.CPU_USER, 0), map("rail:S1", 0, "consumer:CPU", 0), true);
        final NanogramHealthMath.Sample now = sample(61_000, map(NanogramHealthMath.CPU_USER, 500), map("rail:S1", 3_000_000, "consumer:CPU", 600_000), true);
    }

    @Test
    public void experimentWithoutBaselineIsNotMeasured() {
        assertTrue(NanogramHealthMath.experimentText(null, null).contains("not measured"));
    }

    @Test
    public void fullReportSaysNoMonitorsAndListsSpans() {
        NanogramHealthMath.Sample start = sample(0, map(NanogramHealthMath.CPU_USER, 0), map(), false);
        NanogramHealthMath.Sample now = sample(120_000, map(NanogramHealthMath.CPU_USER, 2400), map(), false);
        Map<String, NanogramHealthMath.Sample> bases = new TreeMap<>();
        bases.put("camera2", sample(60_000, map(NanogramHealthMath.CPU_USER, 1000), map(), false));
        String text = NanogramHealthMath.fullReport(start, now, bases, 34);
        assertTrue(text, text.contains("NOT supported on this phone"));
        assertTrue(text, text.contains("Since app start"));
        assertTrue(text, text.contains("Since switch 'camera2' last changed"));
        assertTrue(text, text.contains("1200.0 ms/min"));
        assertTrue(text, text.contains("1400 ms"));
    }

    @Test
    public void batteryTextHandlesUnsupportedCounters() {
        NanogramHealthMath.Battery now = new NanogramHealthMath.Battery(70, false, 312, Long.MIN_VALUE, 0, Long.MIN_VALUE, 1, Float.NaN);
        NanogramHealthMath.Battery start = new NanogramHealthMath.Battery(75, false, 300, Long.MIN_VALUE, 0, Long.MIN_VALUE, 0, Float.NaN);
        String text = NanogramHealthMath.batteryText(start, now);
        assertTrue(text, text.contains("level 70%, not charging (at app start 75%, change -5 points)"));
        assertTrue(text, text.contains("temperature 31.2 C"));
        assertTrue(text, text.contains("current now not supported"));
        assertTrue(text, text.contains("energy counter not supported"));
        assertTrue(text, text.contains("thermal status light, headroom 10 s ahead not available"));
    }

    @Test
    public void experimentReportIncludesHealthLinesOnlyForSupported() {
        EnumMap<NanogramPerfExperiments.Experiment, Boolean> states = new EnumMap<>(NanogramPerfExperiments.Experiment.class);
        String text = NanogramPerfExperiments.report("dev", 33, states, new TreeMap<>(), e -> "HEALTH " + e.id);
        assertTrue(text, text.contains("HEALTH camera2"));
        assertFalse(text, text.contains("HEALTH adaptiveRefresh"));
    }
}
