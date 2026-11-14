package org.telegram.messenger;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Delta, per-minute and text formatting for the health and energy numbers. Pure Java, no Android types. */
public final class NanogramHealthMath {

    public static final long MIN_RATE_MILLIS = 30_000L;
    public static final long MIN_FOREGROUND_RATE_MILLIS = 10_000L;
    public static final int POWER_MONITOR_API = 35;

    public static final String CPU_USER = "cpu.user_ms";
    public static final String CPU_SYSTEM = "cpu.system_ms";
    public static final String STATE_TOP = "state.top_ms";
    public static final String STATE_FG_SERVICE = "state.foreground_service_ms";
    public static final String STATE_TOP_SLEEPING = "state.top_sleeping_ms";
    public static final String STATE_FOREGROUND = "state.foreground_ms";
    public static final String STATE_BACKGROUND = "state.background_ms";
    public static final String STATE_CACHED = "state.cached_ms";
    public static final String WAKELOCK_PARTIAL_COUNT = "wakelock.partial.count";
    public static final String WAKELOCK_PARTIAL_MS = "wakelock.partial.time_ms";
    public static final String WAKELOCK_FULL_COUNT = "wakelock.full.count";
    public static final String WAKELOCK_FULL_MS = "wakelock.full.time_ms";
    public static final String WAKELOCK_WINDOW_COUNT = "wakelock.window.count";
    public static final String WAKELOCK_WINDOW_MS = "wakelock.window.time_ms";
    public static final String JOB_COUNT = "job.count";
    public static final String JOB_MS = "job.time_ms";
    public static final String SYNC_COUNT = "sync.count";
    public static final String SYNC_MS = "sync.time_ms";
    public static final String RADIO_ACTIVE_COUNT = "mobile_radio_active.count";
    public static final String RADIO_ACTIVE_MS = "mobile_radio_active.time_ms";
    public static final String MOBILE_RX_BYTES = "net.mobile.rx_bytes";
    public static final String MOBILE_TX_BYTES = "net.mobile.tx_bytes";
    public static final String MOBILE_RX_PACKETS = "net.mobile.rx_packets";
    public static final String MOBILE_TX_PACKETS = "net.mobile.tx_packets";
    public static final String WIFI_RX_BYTES = "net.wifi.rx_bytes";
    public static final String WIFI_TX_BYTES = "net.wifi.tx_bytes";
    public static final String WIFI_RX_PACKETS = "net.wifi.rx_packets";
    public static final String WIFI_TX_PACKETS = "net.wifi.tx_packets";
    public static final String PROCESS_FOREGROUND_MS = "process.foreground_ms";
    public static final String PROCESS_STARTS = "process.starts";
    public static final String BATTERY_REALTIME_MS = "battery.realtime_ms";
    public static final double ON_BATTERY_SHARE = 0.9;

    public static final String RAIL_PREFIX = "rail:";
    public static final String CONSUMER_PREFIX = "consumer:";

    /** One reading: HealthStats counters plus energy of the system power monitors (empty when unsupported). */
    public static final class Sample {
        public final long elapsedMillis;
        public final Map<String, Long> counters;
        public final Map<String, Long> energyUws;
        public final boolean energySupported;
        public final Battery battery;
        /** Elapsed realtime of the power-monitor reading, which can lag {@link #elapsedMillis} after the stats sync. */
        public final long energyElapsedMillis;

        public Sample(long elapsedMillis, Map<String, Long> counters, Map<String, Long> energyUws, boolean energySupported, Battery battery) {
            this(elapsedMillis, counters, energyUws, energySupported, battery, elapsedMillis);
        }

        public Sample(long elapsedMillis, Map<String, Long> counters, Map<String, Long> energyUws, boolean energySupported, Battery battery, long energyElapsedMillis) {
            this.energyElapsedMillis = energyElapsedMillis;
            this.elapsedMillis = elapsedMillis;
            this.counters = counters;
            this.energyUws = energyUws;
            this.energySupported = energySupported;
            this.battery = battery;
        }
    }

    public static final class Battery {
        public static final int UNKNOWN = Integer.MIN_VALUE;
        public final int percent;
        public final boolean charging;
        public final int temperatureTenthsC;
        public final long currentNowUa;
        public final long chargeCounterUah;
        public final long energyCounterNwh;
        public final int thermalStatus;
        public final float thermalHeadroom;

        public Battery(int percent, boolean charging, int temperatureTenthsC, long currentNowUa, long chargeCounterUah, long energyCounterNwh, int thermalStatus, float thermalHeadroom) {
            this.percent = percent;
            this.charging = charging;
            this.temperatureTenthsC = temperatureTenthsC;
            this.currentNowUa = currentNowUa;
            this.chargeCounterUah = chargeCounterUah;
            this.energyCounterNwh = energyCounterNwh;
            this.thermalStatus = thermalStatus;
            this.thermalHeadroom = thermalHeadroom;
        }
    }

    private NanogramHealthMath() {
    }

    public static boolean powerMonitorsUsable(int sdkInt, int supportedCount) {
        return sdkInt >= POWER_MONITOR_API && supportedCount > 0;
    }

    /** Counters that grew since the base; a counter that went down (reset) or is new on one side only is left out. */
    public static Map<String, Long> delta(Map<String, Long> now, Map<String, Long> base) {
        TreeMap<String, Long> result = new TreeMap<>();
        if (now == null || base == null) {
            return result;
        }
        for (Map.Entry<String, Long> entry : now.entrySet()) {
            Long before = base.get(entry.getKey());
            if (before != null && entry.getValue() >= before) {
                result.put(entry.getKey(), entry.getValue() - before);
            }
        }
        return result;
    }

    /** Counters that exist in now but not in base count from zero (timers appear on first use); used for HealthStats only. */
    public static Map<String, Long> counterDelta(Map<String, Long> now, Map<String, Long> base) {
        TreeMap<String, Long> result = new TreeMap<>();
        if (now == null || base == null) {
            return result;
        }
        for (Map.Entry<String, Long> entry : now.entrySet()) {
            Long before = base.get(entry.getKey());
            long from = before == null ? 0 : before;
            if (entry.getValue() >= from) {
                result.put(entry.getKey(), entry.getValue() - from);
            }
        }
        return result;
    }

    /** Value per minute of the given span, or NaN when the span is not positive. */
    public static double perMinute(long value, long elapsedMillis) {
        if (elapsedMillis <= 0) {
            return Double.NaN;
        }
        return value * 60_000.0 / elapsedMillis;
    }

    public static long get(Map<String, Long> map, String key) {
        Long value = map.get(key);
        return value == null ? 0 : value;
    }

    /** Average power in mW: microwatt-seconds per millisecond is milliwatts. */
    public static double averageMilliwatts(long energyUws, long elapsedMillis) {
        return elapsedMillis <= 0 ? Double.NaN : energyUws / (double) elapsedMillis;
    }

    public static double millijoules(double energyUws) {
        return energyUws / 1000.0;
    }

    /** Sum of the delta energy of the monitors whose key starts with the prefix, and how many monitors counted. */
    public static long[] sumEnergy(Map<String, Long> energyDelta, String prefix) {
        long sum = 0;
        long count = 0;
        for (Map.Entry<String, Long> entry : energyDelta.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                sum += entry.getValue();
                count++;
            }
        }
        return new long[]{sum, count};
    }

    public static String formatMinutes(long elapsedMillis) {
        if (elapsedMillis < 60_000L) {
            return (elapsedMillis / 1000) + " s";
        }
        return one(elapsedMillis / 60_000.0) + " min";
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return one(bytes / 1024.0) + " KB";
        }
        return one(bytes / (1024.0 * 1024)) + " MB";
    }

    /** CPU and foreground lines for the span between two samples; per-minute figures only when the span is long enough. */
    public static String cpuLine(Map<String, Long> d, long elapsedMillis) {
        long user = get(d, CPU_USER);
        long system = get(d, CPU_SYSTEM);
        long cpu = user + system;
        StringBuilder sb = new StringBuilder("CPU time ").append(cpu).append(" ms (user ").append(user).append(", system ").append(system).append(") over ").append(formatMinutes(elapsedMillis));
        if (elapsedMillis >= MIN_RATE_MILLIS) {
            sb.append(": ").append(one(perMinute(cpu, elapsedMillis))).append(" ms/min");
            long top = get(d, STATE_TOP);
            if (top >= MIN_FOREGROUND_RATE_MILLIS) {
                sb.append(", ").append(one(perMinute(cpu, top))).append(" ms per minute on screen (").append(formatMinutes(top)).append(" on screen)");
            }
        } else {
            sb.append(" (under 30 s: no per-minute figure)");
        }
        return sb.toString();
    }

    /** Milliseconds the HealthStats counters covered: they only run on battery. -1 when this phone did not report it. */
    public static long batteryMillis(Sample base, Sample now) {
        Long before = base.counters.get(BATTERY_REALTIME_MS);
        Long after = now.counters.get(BATTERY_REALTIME_MS);
        return before == null || after == null ? -1 : after - before;
    }

    /** False when the span crossed charging time or the counters reset: they then cover less than the wall time. */
    public static boolean countersComparable(Sample base, Sample now) {
        long battery = batteryMillis(base, now);
        if (battery == -1 && !(base.counters.containsKey(BATTERY_REALTIME_MS) && now.counters.containsKey(BATTERY_REALTIME_MS))) {
            return true;
        }
        long wall = now.elapsedMillis - base.elapsedMillis;
        return battery >= 0 && (wall < MIN_RATE_MILLIS || battery >= wall * ON_BATTERY_SHARE);
    }

    /** CPU lines for a span, rates per minute on battery (what the counters cover); a warning instead of rates when not comparable. */
    public static String cpuText(Sample base, Sample now) {
        Map<String, Long> d = counterDelta(now.counters, base.counters);
        long wall = now.elapsedMillis - base.elapsedMillis;
        if (!countersComparable(base, now)) {
            long user = get(d, CPU_USER);
            long system = get(d, CPU_SYSTEM);
            return "CPU time " + (user + system) + " ms (user " + user + ", system " + system + ") over " + formatMinutes(wall)
                    + ": phone was charging or the counters reset in this span: CPU numbers not comparable";
        }
        long battery = batteryMillis(base, now);
        return cpuLine(d, battery > 0 ? battery : wall);
    }

    public static String energyLine(boolean supported, Map<String, Long> energyDelta, long elapsedMillis) {
        if (!supported) {
            return "Energy: not available (this phone has no power monitors, or Android is older than 15)";
        }
        long[] rails = sumEnergy(energyDelta, RAIL_PREFIX);
        long[] consumers = sumEnergy(energyDelta, CONSUMER_PREFIX);
        if (rails[1] == 0 && consumers[1] == 0) {
            return "Energy: power monitors present but no reading in this span";
        }
        StringBuilder sb = new StringBuilder("Energy, whole phone not only Nanogram: ");
        if (elapsedMillis < MIN_RATE_MILLIS) {
            return sb.append("under 30 s, no per-minute figure").toString();
        }
        if (rails[1] > 0) {
            sb.append("rails (").append(rails[1]).append(") ").append(one(millijoules(perMinute(rails[0], elapsedMillis)))).append(" mJ/min, avg ").append(one(averageMilliwatts(rails[0], elapsedMillis))).append(" mW");
        }
        if (consumers[1] > 0) {
            if (rails[1] > 0) {
                sb.append("; ");
            }
            sb.append("modeled consumers (").append(consumers[1]).append(") ").append(one(millijoules(perMinute(consumers[0], elapsedMillis)))).append(" mJ/min, avg ").append(one(averageMilliwatts(consumers[0], elapsedMillis))).append(" mW");
        }
        return sb.toString();
    }

    /** Compact lines for one experiment since its switch last changed: CPU and energy. */
    public static String experimentText(Sample base, Sample now) {
        if (base == null || now == null) {
            return "CPU and energy: not measured yet";
        }
        long energyElapsed = now.energyElapsedMillis - base.energyElapsedMillis;
        String energy = energyLine(now.energySupported, delta(now.energyUws, base.energyUws), energyElapsed);
        if (now.counters.isEmpty()) {
            return "CPU: not available on this phone\n" + energy;
        }
        return cpuText(base, now) + "\n" + energy;
    }

    /** Full text for health-energy.txt: since start, since each switch change, per-monitor energy, battery and heat. */
    public static String fullReport(Sample start, Sample now, Map<String, Sample> bases, int sdkInt) {
        StringBuilder sb = new StringBuilder("Health and energy (Android system health stats of Nanogram's own uid; energy of the whole phone)\n");
        sb.append("API ").append(sdkInt).append(", power monitors: ").append(now.energySupported ? "supported (" + now.energyUws.size() + " readings)" : "NOT supported on this phone (needs Android 15 and a phone with on-device power monitors, for example Pixel); energy numbers below are absent, use the battery lines").append('\n');
        sb.append("Numbers: CPU and times are of Nanogram's uid (all its processes); energy is the whole phone (screen, radios, other apps), so keep the phone otherwise identical in A and B.\n");
        appendSpan(sb, "Since app start", start, now);
        for (Map.Entry<String, Sample> entry : new TreeMap<>(bases).entrySet()) {
            appendSpan(sb, "Since switch '" + entry.getKey() + "' last changed", entry.getValue(), now);
        }
        sb.append('\n').append(batteryText(start == null ? null : start.battery, now.battery));
        return sb.toString();
    }

    private static void appendSpan(StringBuilder sb, String title, Sample base, Sample now) {
        sb.append('\n').append(title).append('\n');
        if (base == null) {
            sb.append("  no baseline\n");
            return;
        }
        long elapsed = now.energyElapsedMillis - base.energyElapsedMillis;
        Map<String, Long> d = counterDelta(now.counters, base.counters);
        if (now.counters.isEmpty()) {
            sb.append("  health stats: not available on this phone\n");
        } else {
            sb.append("  ").append(cpuText(base, now)).append('\n');
            sb.append("  process time: top ").append(get(d, STATE_TOP)).append(" ms, foreground service ").append(get(d, STATE_FG_SERVICE))
                    .append(" ms, top sleeping ").append(get(d, STATE_TOP_SLEEPING)).append(" ms, foreground ").append(get(d, STATE_FOREGROUND))
                    .append(" ms, background ").append(get(d, STATE_BACKGROUND)).append(" ms, cached ").append(get(d, STATE_CACHED)).append(" ms\n");
            sb.append("  wakelocks (count, ms): partial ").append(get(d, WAKELOCK_PARTIAL_COUNT)).append(", ").append(get(d, WAKELOCK_PARTIAL_MS))
                    .append("; full ").append(get(d, WAKELOCK_FULL_COUNT)).append(", ").append(get(d, WAKELOCK_FULL_MS))
                    .append("; window ").append(get(d, WAKELOCK_WINDOW_COUNT)).append(", ").append(get(d, WAKELOCK_WINDOW_MS)).append('\n');
            sb.append("  jobs ").append(get(d, JOB_COUNT)).append(" (").append(get(d, JOB_MS)).append(" ms), syncs ").append(get(d, SYNC_COUNT)).append(" (").append(get(d, SYNC_MS))
                    .append(" ms), mobile radio active ").append(get(d, RADIO_ACTIVE_COUNT)).append(" times (").append(get(d, RADIO_ACTIVE_MS)).append(" ms)\n");
            sb.append("  network: mobile rx ").append(formatBytes(get(d, MOBILE_RX_BYTES))).append(" / tx ").append(formatBytes(get(d, MOBILE_TX_BYTES)))
                    .append(" (").append(get(d, MOBILE_RX_PACKETS)).append(" / ").append(get(d, MOBILE_TX_PACKETS)).append(" packets); wifi rx ").append(formatBytes(get(d, WIFI_RX_BYTES)))
                    .append(" / tx ").append(formatBytes(get(d, WIFI_TX_BYTES))).append(" (").append(get(d, WIFI_RX_PACKETS)).append(" / ").append(get(d, WIFI_TX_PACKETS)).append(" packets)\n");
            sb.append("  processes: foreground ").append(get(d, PROCESS_FOREGROUND_MS)).append(" ms, starts ").append(get(d, PROCESS_STARTS)).append('\n');
        }
        Map<String, Long> energy = delta(now.energyUws, base.energyUws);
        sb.append("  ").append(energyLine(now.energySupported, energy, elapsed)).append('\n');
        if (now.energySupported && elapsed >= MIN_RATE_MILLIS) {
            for (Map.Entry<String, Long> entry : energy.entrySet()) {
                sb.append("    ").append(entry.getKey()).append(": ").append(one(millijoules(entry.getValue()))).append(" mJ, ")
                        .append(one(averageMilliwatts(entry.getValue(), elapsed))).append(" mW avg\n");
            }
        }
    }

    public static String batteryText(Battery start, Battery now) {
        StringBuilder sb = new StringBuilder("Battery and heat\n");
        if (now == null) {
            return sb.append("  not available\n").toString();
        }
        sb.append("  level ").append(percent(now.percent)).append(now.charging ? ", charging" : ", not charging");
        if (start != null && start.percent >= 0 && now.percent >= 0) {
            sb.append(" (at app start ").append(start.percent).append("%, change ").append(now.percent - start.percent).append(" points)");
        }
        sb.append('\n');
        sb.append("  temperature ").append(now.temperatureTenthsC == Battery.UNKNOWN ? "unknown" : one(now.temperatureTenthsC / 10.0) + " C").append('\n');
        sb.append("  current now ").append(now.currentNowUa == Long.MIN_VALUE || now.currentNowUa == Integer.MIN_VALUE || now.currentNowUa == 0 ? "not supported" : (now.currentNowUa / 1000) + " mA (instant value, sign differs by phone)").append('\n');
        sb.append("  charge counter ").append(now.chargeCounterUah <= 0 ? "not supported" : (now.chargeCounterUah / 1000.0) + " mAh");
        if (start != null && start.chargeCounterUah > 0 && now.chargeCounterUah > 0) {
            sb.append(" (change since app start ").append(one((now.chargeCounterUah - start.chargeCounterUah) / 1000.0)).append(" mAh)");
        }
        sb.append('\n');
        sb.append("  energy counter ").append(now.energyCounterNwh == Long.MIN_VALUE || now.energyCounterNwh <= 0 ? "not supported" : now.energyCounterNwh + " nWh");
        if (start != null && start.energyCounterNwh > 0 && now.energyCounterNwh > 0) {
            sb.append(" (change since app start ").append(now.energyCounterNwh - start.energyCounterNwh).append(" nWh)");
        }
        sb.append('\n');
        sb.append("  thermal status ").append(thermalName(now.thermalStatus)).append(", headroom 10 s ahead ").append(Float.isNaN(now.thermalHeadroom) || now.thermalHeadroom < 0 ? "not available" : one(now.thermalHeadroom) + " (1.0 = severe throttling)").append('\n');
        return sb.toString();
    }

    public static String thermalName(int status) {
        switch (status) {
            case 0: return "none";
            case 1: return "light";
            case 2: return "moderate";
            case 3: return "severe";
            case 4: return "critical";
            case 5: return "emergency";
            case 6: return "shutdown";
            default: return "unknown";
        }
    }

    private static String percent(int value) {
        return value < 0 ? "unknown" : value + "%";
    }

    private static String one(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.US, "%.1f", value);
    }
}
