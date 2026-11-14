package org.telegram.messenger;

/** Pure helpers that name an input route for the recorder state, the recording log and Send Report. */
public final class AudioRouteInfo {

    // AudioDeviceInfo.TYPE_* values, spelled out so the class works in JVM tests.
    private static final int TYPE_BUILTIN_MIC = 15;
    private static final int TYPE_BLUETOOTH_SCO = 7;
    private static final int TYPE_BLE_HEADSET = 26;
    private static final int TYPE_WIRED_HEADSET = 3;
    private static final int TYPE_USB_DEVICE = 11;
    private static final int TYPE_USB_HEADSET = 22;
    private static final int TYPE_TELEPHONY = 18;

    /** What the recorder UI shows: the actual route, not the requested one. */
    public enum State {
        PHONE,
        HEADSET_CALL_QUALITY,
        HEADSET_LE,
        FELL_BACK
    }

    private AudioRouteInfo() {
    }

    public static String typeName(int type) {
        switch (type) {
            case 0:
                return "AUTOMATIC";
            case TYPE_BUILTIN_MIC:
                return "BUILTIN_MIC(15)";
            case TYPE_BLUETOOTH_SCO:
                return "BLUETOOTH_SCO(7)";
            case TYPE_BLE_HEADSET:
                return "BLE_HEADSET(26)";
            case TYPE_WIRED_HEADSET:
                return "WIRED_HEADSET(3)";
            case TYPE_USB_HEADSET:
                return "USB_HEADSET(22)";
            case TYPE_USB_DEVICE:
                return "USB_DEVICE(11)";
            case TYPE_TELEPHONY:
                return "TELEPHONY(18)";
            case AudioRouteSession.NO_ROUTE:
                return "none";
            default:
                return "type " + type;
        }
    }

    /**
     * Best guess at the radio codec from the routed device. Android has no API that names it, so this is a class
     * from the type and the sample rates the device reports (empty for many SCO stacks).
     */
    public static String codecClass(int routedType, int[] sampleRates) {
        int max = 0;
        if (sampleRates != null) {
            for (int rate : sampleRates) {
                max = Math.max(max, rate);
            }
        }
        switch (routedType) {
            case TYPE_BLE_HEADSET:
                return max >= 32000 ? "LC3 super-wideband or better" : "LC3 (LE Audio)";
            case TYPE_BLUETOOTH_SCO:
                if (max >= 32000) {
                    return "LC3 over SCO (super-wideband)";
                } else if (max >= 16000) {
                    return "mSBC (wideband)";
                } else if (max > 0) {
                    return "CVSD (narrowband)";
                }
                return "SCO, CVSD or mSBC (rates not reported)";
            case AudioRouteSession.NO_ROUTE:
                return "unknown";
            default:
                return "PCM (no Bluetooth codec)";
        }
    }

    /**
     * @param requested the resolved choice (0 = automatic)
     * @param routed    the type AudioRecord actually reports, or NO_ROUTE
     */
    public static State state(int requested, int routed) {
        if (requested == TYPE_BLUETOOTH_SCO || requested == TYPE_BLE_HEADSET) {
            if (routed == requested) {
                return requested == TYPE_BLE_HEADSET ? State.HEADSET_LE : State.HEADSET_CALL_QUALITY;
            }
            return State.FELL_BACK;
        }
        return State.PHONE;
    }

    /**
     * The owner wants LE Audio microphones when the user picked the headset and one is connected: a stored
     * Bluetooth SCO choice becomes TYPE_BLE_HEADSET when the connected inputs include one.
     */
    public static int preferLowEnergy(int stored, int[] connected) {
        if (stored != TYPE_BLUETOOTH_SCO || connected == null) {
            return stored;
        }
        for (int type : connected) {
            if (type == TYPE_BLE_HEADSET) {
                return TYPE_BLE_HEADSET;
            }
        }
        return stored;
    }

    public static String format(String recorder, int requested, String acquire, int routed, int[] sampleRates, int captureRate, long acquireMs) {
        final StringBuilder rates = new StringBuilder("[");
        if (sampleRates != null) {
            for (int a = 0; a < sampleRates.length; a++) {
                if (a > 0) {
                    rates.append(',');
                }
                rates.append(sampleRates[a]);
            }
        }
        rates.append(']');
        return "recorder=" + recorder
                + " requested=" + typeName(requested)
                + " acquire=" + acquire + " (" + acquireMs + " ms)"
                + " routed=" + typeName(routed)
                + " deviceRates=" + rates
                + " captureRate=" + captureRate
                + " codec=" + codecClass(routed, sampleRates)
                + " state=" + state(requested, routed);
    }
}
