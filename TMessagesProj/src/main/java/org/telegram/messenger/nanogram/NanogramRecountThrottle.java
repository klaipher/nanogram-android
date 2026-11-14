package org.telegram.messenger.nanogram;

public final class NanogramRecountThrottle {

    private static final long MIN_WINDOW_MS = 2000;
    private static final long IDLE_FACTOR = 9;

    private NanogramRecountThrottle() {
    }

    // Keeps nine times the last run's cost idle between full badge recounts (about a tenth of the queue), at least 2 s.
    public static long delayMs(long now, long lastRunEnd, long lastRunCost) {
        final long window = Math.max(MIN_WINDOW_MS, lastRunCost * IDLE_FACTOR);
        return Math.max(0, window - (now - lastRunEnd));
    }
}
