package org.telegram.messenger;

import android.app.ActivityManager;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** One place that asks the phone what its media hardware can do; every answer is false/0 when unknown, which keeps today's behaviour. */
public final class NanogramMediaCapabilities {

    public static final int CLASS_S = 31;

    public static final class Candidate {
        public final String name;
        public final boolean hardwareAccelerated;
        public final boolean softwareOnly;
        public final boolean supportsRequest;

        public Candidate(String name, boolean hardwareAccelerated, boolean softwareOnly, boolean supportsRequest) {
            this.name = name;
            this.hardwareAccelerated = hardwareAccelerated;
            this.softwareOnly = softwareOnly;
            this.supportsRequest = supportsRequest;
        }
    }

    private static final ConcurrentHashMap<String, String> encoderNames = new ConcurrentHashMap<>();
    private static volatile int av1 = -1;

    private NanogramMediaCapabilities() {
    }

    public static boolean isHardware(boolean hardwareAccelerated, boolean softwareOnly) {
        return hardwareAccelerated && !softwareOnly;
    }

    /** First candidate that is hardware and supports the request, in list order; null when none. */
    public static String pickHardware(List<Candidate> candidates) {
        for (Candidate c : candidates) {
            if (c.supportsRequest && isHardware(c.hardwareAccelerated, c.softwareOnly)) {
                return c.name;
            }
        }
        return null;
    }

    /** Stable reorder: items for which the predicate holds first; the list is returned unchanged when none or all hold. */
    public static <T> List<T> hardwareFirst(List<T> items, java.util.function.Predicate<T> hardware) {
        List<T> hw = new ArrayList<>();
        List<T> other = new ArrayList<>();
        for (T item : items) {
            (hardware.test(item) ? hw : other).add(item);
        }
        if (hw.isEmpty() || other.isEmpty()) {
            return items;
        }
        hw.addAll(other);
        return hw;
    }

    public static int performanceClass() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? Build.VERSION.MEDIA_PERFORMANCE_CLASS : 0;
    }

    public static boolean isLowRam() {
        try {
            Context context = ApplicationLoader.applicationContext;
            return context == null || ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).isLowRamDevice();
        } catch (Throwable e) {
            return true;
        }
    }

    /** Auto rules for experiments that have a solid device signal; false means "stay as the tester sets it". */
    public static boolean autoGpuPreupload(int performanceClass, boolean lowRam) {
        return performanceClass >= CLASS_S && !lowRam;
    }

    public static boolean autoAdaptiveRefresh(int sdkInt) {
        return NanogramDisplay.supportsHints(sdkInt);
    }

    public static boolean hasHardwareDecoder(String mime, int width, int height, int fps) {
        return findHardware(mime, false, width, height, fps) != null;
    }

    public static boolean hasHardwareEncoder(String mime, int width, int height, int fps) {
        return findHardware(mime, true, width, height, fps) != null;
    }

    public static boolean isAv1HardwareDecodeAvailable() {
        int value = av1;
        if (value == -1) {
            av1 = value = hasHardwareDecoder("video/av01", 0, 0, 0) ? 1 : 0;
        }
        return value == 1;
    }

    public static boolean isHardwareDecoderName(String codecName, String mime) {
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (!info.isEncoder() && info.getName().equals(codecName) && supportsType(info, mime)) {
                    return isHardware(info.isHardwareAccelerated(), info.isSoftwareOnly());
                }
            }
        } catch (Throwable e) {
            FileLog.e(e, false);
        }
        return false;
    }

    /** Hardware encoder for the mime that can do the size and rate, created by name; the platform default encoder when none (or on any error). */
    public static MediaCodec createEncoder(String mime, int width, int height, int fps) throws java.io.IOException {
        String name = encoderNames.get(mime + width + "x" + height + "@" + fps);
        if (name == null) {
            name = findHardware(mime, true, width, height, fps);
            encoderNames.put(mime + width + "x" + height + "@" + fps, name == null ? "" : name);
        }
        if (name != null && !name.isEmpty()) {
            try {
                MediaCodec codec = MediaCodec.createByCodecName(name);
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("video encoder " + mime + ": " + name + " (hardware)");
                }
                return codec;
            } catch (Throwable e) {
                FileLog.e(e, false);
            }
        }
        MediaCodec codec = MediaCodec.createEncoderByType(mime);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("video encoder " + mime + ": " + codec.getName() + " (platform default)");
        }
        return codec;
    }

    private static String findHardware(String mime, boolean encoder, int width, int height, int fps) {
        try {
            List<Candidate> candidates = new ArrayList<>();
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder() != encoder || !supportsType(info, mime)) {
                    continue;
                }
                candidates.add(new Candidate(info.getName(), info.isHardwareAccelerated(), info.isSoftwareOnly(), supportsRequest(info, mime, width, height, fps)));
            }
            return pickHardware(candidates);
        } catch (Throwable e) {
            FileLog.e(e, false);
            return null;
        }
    }

    private static boolean supportsType(MediaCodecInfo info, String mime) {
        for (String type : info.getSupportedTypes()) {
            if (type.equalsIgnoreCase(mime)) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportsRequest(MediaCodecInfo info, String mime, int width, int height, int fps) {
        if (width <= 0 || height <= 0) {
            return true;
        }
        try {
            MediaCodecInfo.VideoCapabilities caps = info.getCapabilitiesForType(mime).getVideoCapabilities();
            if (caps == null) {
                return false;
            }
            if (fps > 0) {
                return caps.areSizeAndRateSupported(width, height, fps);
            }
            return caps.isSizeSupported(width, height);
        } catch (Throwable e) {
            return false;
        }
    }
}
