package org.telegram.messenger.nanogram;

/**
 * Decides when Lite mode's power saver applies. Pure logic: the caller supplies the current signals.
 * Thermal status values match android.os.PowerManager.THERMAL_STATUS_*.
 */
public final class NanogramPowerPolicy {

    public static final int THERMAL_STATUS_SEVERE = 3;

    private NanogramPowerPolicy() {
    }

    /** True when system Battery Saver or severe heat should force the power saver; false whenever the user opted out. */
    public static boolean systemSignal(boolean followSystem, boolean systemSaver, int thermalStatus) {
        return followSystem && (systemSaver || thermalStatus >= THERMAL_STATUS_SEVERE);
    }

    /**
     * @param saverLevel      user's Power Saver level in percent (0 = off, 100 = always)
     * @param batteryLevel    battery percent, -1 if unknown
     * @param charging        device is charging or full
     * @param skipWhileCharging ignore the percent trigger while charging
     * @param followSystem    the user's "follow system Battery Saver and heat" setting
     * @param systemSaver     system Battery Saver is on
     * @param thermalStatus   PowerManager thermal status, 0 if unknown
     */
    public static boolean shouldApply(int saverLevel, int batteryLevel, boolean charging, boolean skipWhileCharging, boolean followSystem, boolean systemSaver, int thermalStatus) {
        if (systemSignal(followSystem, systemSaver, thermalStatus)) {
            return true;
        }
        if (saverLevel <= 0) {
            return false;
        }
        if (saverLevel >= 100) {
            return true;
        }
        if (skipWhileCharging && charging) {
            return false;
        }
        return batteryLevel >= 0 && batteryLevel <= saverLevel;
    }
}
