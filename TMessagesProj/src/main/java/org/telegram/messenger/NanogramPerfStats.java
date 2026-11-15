package org.telegram.messenger;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Durations of the performance experiments since the experiment's switch last changed. Durations and event counts only: no content or ids. */
public final class NanogramPerfStats {

    public static final String FRAME_TIME = "frameTime";
    public static final String FRAME_INTERVAL = "frameInterval";
    public static final String GIF_FRAME_DECODE = "gifFrameDecode";
    public static final String IMAGE_UPLOAD = "imageUpload";
    public static final String CAMERA_FIRST_FRAME = "cameraFirstFrame";
    public static final String TELECOM_REGISTER = "telecomRegister";
    public static final String CALL_SETUP = "callSetup";
    // Event counters (count only, no durations): recorded with count().
    public static final String CAMERA2_OPENED = "camera2Opened";
    public static final String CAMERA2_UNAVAILABLE = "camera2Unavailable";
    public static final String CAMERA_EXTENSION_OPENED = "cameraExtensionOpened";
    public static final String CAMERA_EXTENSION_FAILED = "cameraExtensionFailed";
    public static final String TELECOM_CALLS = "telecomCalls";

    public static final long SLOW_FRAME_NANOS = 16_700_000L;
    public static final long VERY_SLOW_FRAME_NANOS = 33_400_000L;

    public static final class Snapshot {
        public final int count;
        public final long totalNanos;
        public final long maxNanos;
        public final int overSlow;
        public final int overVerySlow;

        Snapshot(int count, long totalNanos, long maxNanos, int overSlow, int overVerySlow) {
            this.count = count;
            this.totalNanos = totalNanos;
            this.maxNanos = maxNanos;
            this.overSlow = overSlow;
            this.overVerySlow = overVerySlow;
        }

        public double averageMillis() {
            return count == 0 ? 0 : totalNanos / 1e6 / count;
        }

        public double maxMillis() {
            return maxNanos / 1e6;
        }
    }

    private static final class Stat {
        int count;
        long total;
        long max;
        int overSlow;
        int overVerySlow;

        synchronized void add(long nanos) {
            count++;
            total += nanos;
            if (nanos > max) {
                max = nanos;
            }
            if (nanos > SLOW_FRAME_NANOS) {
                overSlow++;
            }
            if (nanos > VERY_SLOW_FRAME_NANOS) {
                overVerySlow++;
            }
        }

        synchronized Snapshot snapshot() {
            return new Snapshot(count, total, max, overSlow, overVerySlow);
        }
    }

    private static final ConcurrentHashMap<String, Stat> stats = new ConcurrentHashMap<>();

    private NanogramPerfStats() {
    }

    public static void record(String key, long nanos) {
        if (nanos < 0) {
            return;
        }
        Stat stat = stats.get(key);
        if (stat == null) {
            Stat created = new Stat();
            stat = stats.putIfAbsent(key, created);
            if (stat == null) {
                stat = created;
            }
        }
        stat.add(nanos);
    }

    /** Counts one event under {@code key}; shown as "n times". */
    public static void count(String key) {
        record(key, 0);
    }

    public static boolean isCounter(String key) {
        return CAMERA2_OPENED.equals(key) || CAMERA2_UNAVAILABLE.equals(key) || CAMERA_EXTENSION_OPENED.equals(key) || CAMERA_EXTENSION_FAILED.equals(key) || TELECOM_CALLS.equals(key);
    }

    public static void recordSince(String key, long startNanos) {
        if (startNanos > 0) {
            record(key, System.nanoTime() - startNanos);
        }
    }

    public static Map<String, Snapshot> snapshot() {
        TreeMap<String, Snapshot> result = new TreeMap<>();
        for (Map.Entry<String, Stat> entry : stats.entrySet()) {
            result.put(entry.getKey(), entry.getValue().snapshot());
        }
        return result;
    }

    public static void reset() {
        stats.clear();
    }

    public static void reset(String... keys) {
        for (String key : keys) {
            stats.remove(key);
        }
    }

    public static String formatLine(String key, Snapshot s) {
        if (isCounter(key)) {
            return key + ": " + (s == null ? 0 : s.count) + " times";
        }
        if (s == null || s.count == 0) {
            return key + ": no samples";
        }
        StringBuilder sb = new StringBuilder(key).append(": n=").append(s.count)
                .append(", average ").append(oneDecimal(s.averageMillis())).append(" ms")
                .append(", longest ").append(oneDecimal(s.maxMillis())).append(" ms");
        if (FRAME_TIME.equals(key)) {
            sb.append(", over 16.7 ms ").append(s.overSlow).append(", over 33.4 ms ").append(s.overVerySlow);
        }
        return sb.toString();
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }
}
