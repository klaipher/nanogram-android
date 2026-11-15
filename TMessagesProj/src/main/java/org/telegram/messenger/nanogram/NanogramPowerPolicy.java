package org.telegram.messenger.nanogram;

/**
 * Decides when Lite mode's power saver applies. Pure logic: the caller supplies the current signals.
 * Thermal status values match android.os.PowerManager.THERMAL_STATUS_*.
 */
public final class NanogramPowerPolicy {

    public static final int THERMAL_STATUS_SEVERE = 3;

    private NanogramPowerPolicy() {
    }

    /** True when the forecast is hot as well; the forecast is ignored when the user opted out of following the system. */
    public static boolean systemSignal(boolean followSystem, boolean systemSaver, int thermalStatus, boolean forecastHot) {
        return systemSignal(followSystem, systemSaver, thermalStatus) || followSystem && forecastHot;
    }

    /** True when system Battery Saver or severe heat should force the power saver; false whenever the user opted out. */
    public static boolean systemSignal(boolean followSystem, boolean systemSaver, int thermalStatus) {
        return followSystem && (systemSaver || thermalStatus >= THERMAL_STATUS_SEVERE);
    }

    /**
     * @param saverLevel      user's Power Saver level in percent (0 = off, 100 = always)
     * @param batteryLevel    battery percent, -1 if unknown
     * @param followSystem    the user's "follow system Battery Saver and heat" setting
     * @param systemSaver     system Battery Saver is on
     * @param thermalStatus   PowerManager thermal status, 0 if unknown
     */
    public static boolean shouldApply(int saverLevel, int batteryLevel, boolean followSystem, boolean systemSaver, int thermalStatus) {
        if (systemSignal(followSystem, systemSaver, thermalStatus)) {
            return true;
        }
        if (saverLevel <= 0) {
            return false;
        }
        if (saverLevel >= 100) {
            return true;
        }
        return batteryLevel >= 0 && batteryLevel <= saverLevel;
    }

    /**
     * Turns PowerManager.getThermalHeadroom(10) readings into an on/off "getting hot" signal with hysteresis.
     * Headroom 1.0 means throttling; NaN means the device cannot forecast. Not thread safe: use from one thread.
     */
    public static final class ThermalForecast {

        public static final float ENTER = 0.9f;
        public static final float LEAVE = 0.7f;
        public static final long MIN_HOLD_MS = 30_000L;
        /** The system keeps sampling only while queried at least every 10 s; slower polling always reads a one-sample buffer with no forecast. */
        public static final long POLL_INTERVAL_MS = 5_000L;

        private boolean hot;
        private long hotSince;

        public boolean isHot() {
            return hot;
        }

        /** Returns the new state. A NaN reading changes nothing while work is active; without active work the signal is released after the hold time. */
        public boolean update(float headroom, boolean workActive, long nowMs) {
            if (!hot) {
                if (workActive && !Float.isNaN(headroom) && headroom >= ENTER) {
                    hot = true;
                    hotSince = nowMs;
                }
            } else if (nowMs - hotSince >= MIN_HOLD_MS) {
                if (!workActive || !Float.isNaN(headroom) && headroom <= LEAVE) {
                    hot = false;
                }
            }
            return hot;
        }

        public void reset() {
            hot = false;
        }
    }
}
