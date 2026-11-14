package org.telegram.messenger;

import android.app.ActivityManager;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
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
    private static final java.util.Set<String> pickedHardware = ConcurrentHashMap.newKeySet();
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

    /** Adaptive refresh needs Android 16 plus a display whose HAL supports it (Display.hasArrSupport); API 35 stays tester-only. */
    public static boolean autoAdaptiveRefresh(int sdkInt, boolean displayHasArr) {
        return sdkInt >= Build.VERSION_CODES.BAKLAVA && displayHasArr;
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

    /** The platform default encoder when it is already hardware (today's behaviour); a hardware encoder by name only when the default is software. */
    public static MediaCodec createEncoder(String mime, int width, int height, int fps) throws java.io.IOException {
        MediaCodec codec = MediaCodec.createEncoderByType(mime);
        try {
            MediaCodecInfo info = codec.getCodecInfo();
            if (isHardware(info.isHardwareAccelerated(), info.isSoftwareOnly())) {
                return codec;
            }
            String key = mime + width + "x" + height + "@" + fps;
            String name = encoderNames.get(key);
            if (name == null) {
                name = findHardware(mime, true, width, height, fps);
                encoderNames.put(key, name == null ? "" : name);
            }
            if (!name.isEmpty() && !name.equals(codec.getName())) {
                MediaCodec hardware = MediaCodec.createByCodecName(name);
                codec.release();
                pickedHardware.add(name);
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("video encoder " + mime + ": " + name + " (hardware)");
                }
                return hardware;
            }
        } catch (Throwable e) {
            FileLog.e(e, false);
        }
        return codec;
    }

    /** Configures an encoder from createEncoder; when the hardware one it picked refuses, that name is dropped and the platform default is configured instead. */
    public static MediaCodec configureEncoder(MediaCodec codec, MediaFormat format, String mime) throws java.io.IOException {
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            return codec;
        } catch (RuntimeException e) {
            String name = codec.getName();
            if (!pickedHardware.contains(name)) {
                throw e;
            }
            FileLog.e(e, false);
            dropName(encoderNames, name);
            pickedHardware.remove(name);
            try {
                codec.release();
            } catch (Throwable ignore) {
            }
            MediaCodec fallback = MediaCodec.createEncoderByType(mime);
            fallback.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            return fallback;
        }
    }

    static void dropName(java.util.Map<String, String> names, String name) {
        for (java.util.Map.Entry<String, String> entry : names.entrySet()) {
            if (name.equals(entry.getValue())) {
                entry.setValue("");
            }
        }
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
