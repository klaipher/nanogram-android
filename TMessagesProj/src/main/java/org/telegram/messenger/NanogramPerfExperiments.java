package org.telegram.messenger;

import android.os.Build;

import java.util.Map;

/** The dark hardware experiments testers can flip in Debug settings, with their numbers. All default off. */
public final class NanogramPerfExperiments {

    public enum Experiment {
        ADAPTIVE_REFRESH("adaptiveRefresh", "Adaptive refresh rate", "Android 15 or newer: the screen may drop below its top refresh rate when little moves, and rises while you scroll.", "Restart the app to apply.", Build.VERSION_CODES.VANILLA_ICE_CREAM, NanogramPerfStats.FRAME_TIME, NanogramPerfStats.FRAME_INTERVAL),
        TELECOM("telecom", "System call integration", "Android 14 or newer: one-to-one calls are also registered with the phone's call system (Bluetooth, car, watch buttons).", "Applies to the next call.", Build.VERSION_CODES.UPSIDE_DOWN_CAKE, NanogramPerfStats.TELECOM_REGISTER, NanogramPerfStats.CALL_SETUP),
        CAMERA2("camera2", "Camera2 capture", "Stories and the chat camera use the newer camera interface.", "Close and reopen the camera to apply.", 0, NanogramPerfStats.CAMERA_FIRST_FRAME),
        GPU_PREUPLOAD("gpuPreupload", "Early image upload to the GPU", "Pictures are prepared for the screen while they load, so the first draw has less work.", "Restart the app to apply.", 0, NanogramPerfStats.IMAGE_UPLOAD),
        GIF_FAST_DECODE("gifFastDecode", "Lighter decoding for small GIFs", "Small GIFs (up to 320 px) decode faster, with slightly softer edges possible.", "Restart the app to apply.", 0, NanogramPerfStats.GIF_FRAME_DECODE),
        DB_TUNING("dbTuning", "Database tuning", "A larger database cache and memory mapping for the local message database.", "Restart the app to apply.", 0);

        public final String id;
        public final String title;
        public final String description;
        public final String applyHint;
        public final int minSdk;
        public final String[] statKeys;

        Experiment(String id, String title, String description, String applyHint, int minSdk, String... statKeys) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.applyHint = applyHint;
            this.minSdk = minSdk;
            this.statKeys = statKeys;
        }

        public boolean isSupported(int sdkInt) {
            return sdkInt >= minSdk;
        }
    }

    private NanogramPerfExperiments() {
    }

    public static boolean isEnabled(Experiment experiment) {
        switch (experiment) {
            case ADAPTIVE_REFRESH:
                return NanogramExperiments.isEnabled(NanogramExperiments.ADAPTIVE_REFRESH_RATE);
            case TELECOM:
                return NanogramExperiments.isEnabled(NanogramExperiments.SELF_MANAGED_TELECOM);
            case CAMERA2:
                return NanogramExperiments.isEnabled(NanogramExperiments.CAMERA2_CAPTURE);
            case GPU_PREUPLOAD:
                return NanogramImageFlags.isGpuPreuploadEnabled();
            case GIF_FAST_DECODE:
                return NanogramGifDecodeFlags.isEnabled();
            case DB_TUNING:
                return NanogramDbTuning.isEnabled();
        }
        return false;
    }

    public static void setEnabled(Experiment experiment, boolean enabled) {
        NanogramPerfStats.reset(experiment.statKeys);
        switch (experiment) {
            case ADAPTIVE_REFRESH:
                NanogramExperiments.setEnabled(NanogramExperiments.ADAPTIVE_REFRESH_RATE, enabled);
                break;
            case TELECOM:
                NanogramExperiments.setEnabled(NanogramExperiments.SELF_MANAGED_TELECOM, enabled);
                break;
            case CAMERA2:
                NanogramExperiments.setEnabled(NanogramExperiments.CAMERA2_CAPTURE, enabled);
                break;
            case GPU_PREUPLOAD:
                NanogramImageFlags.setGpuPreuploadEnabled(enabled);
                break;
            case GIF_FAST_DECODE:
                NanogramGifDecodeFlags.setEnabled(enabled);
                break;
            case DB_TUNING:
                NanogramDbTuning.setEnabled(enabled);
                break;
        }
    }

    public static String deviceLine(String manufacturer, String model, String soc, String release, int sdkInt) {
        StringBuilder sb = new StringBuilder();
        sb.append(manufacturer).append(' ').append(model);
        sb.append(", SoC ").append(soc == null || soc.isEmpty() || "unknown".equalsIgnoreCase(soc) ? "unknown" : soc);
        sb.append(", Android ").append(release).append(" (API ").append(sdkInt).append(')');
        return sb.toString();
    }

    public static String deviceLine() {
        return deviceLine(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? Build.SOC_MODEL : null, Build.VERSION.RELEASE, Build.VERSION.SDK_INT);
    }

    public static String statsText(Experiment experiment, Map<String, NanogramPerfStats.Snapshot> snapshot) {
        if (experiment.statKeys.length == 0) {
            return "no measured numbers; compare smoothness and app start by feel";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : experiment.statKeys) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(NanogramPerfStats.formatLine(key, snapshot.get(key)));
        }
        return sb.toString();
    }

    public static String currentReport() {
        java.util.EnumMap<Experiment, Boolean> states = new java.util.EnumMap<>(Experiment.class);
        for (Experiment experiment : Experiment.values()) {
            states.put(experiment, isEnabled(experiment));
        }
        return report(deviceLine(), Build.VERSION.SDK_INT, states, NanogramPerfStats.snapshot());
    }

    public static String report(String device, int sdkInt, Map<Experiment, Boolean> states, Map<String, NanogramPerfStats.Snapshot> snapshot) {
        StringBuilder sb = new StringBuilder("Performance experiments (since each switch last changed; durations only)\n");
        sb.append("device: ").append(device).append('\n');
        for (Experiment experiment : Experiment.values()) {
            boolean supported = experiment.isSupported(sdkInt);
            Boolean state = states.get(experiment);
            sb.append('\n').append(experiment.title).append(": ")
                    .append(!supported ? "not available (needs API " + experiment.minSdk + ")" : state != null && state ? "ON" : "off").append('\n');
            for (String line : statsText(experiment, snapshot).split("\n")) {
                sb.append("  ").append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
