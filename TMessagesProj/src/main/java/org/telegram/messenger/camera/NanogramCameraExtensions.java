package org.telegram.messenger.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraManager;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.nanogram.NanogramFeatures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Platform camera extensions (HDR / Night / Automatic) for the photo camera. The pick* helpers are pure and unit tested. */
public final class NanogramCameraExtensions {

    // Same values as CameraExtensionCharacteristics.EXTENSION_*, kept as literals so the pure helpers load on the JVM.
    public static final int EXT_AUTOMATIC = 0;
    public static final int EXT_HDR = 3;
    public static final int EXT_NIGHT = 4;

    public static final long MAX_STILL_PIXELS = 12_000_000L;

    private static final int[] PROBED = {EXT_AUTOMATIC, EXT_HDR, EXT_NIGHT};

    private static final Set<String> blacklist = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Support> supportCache = new ConcurrentHashMap<>();
    private static volatile boolean anySupported;
    private static volatile boolean warmedUp;
    private static volatile boolean warming;

    private NanogramCameraExtensions() {
    }

    /** Usable extensions of one camera: each has SurfaceTexture sizes and JPEG sizes ({w, h}). */
    public static final class Support {
        public final List<Integer> extensions = new ArrayList<>();
        public final Map<Integer, List<int[]>> previewSizes = new HashMap<>();
        public final Map<Integer, List<int[]>> jpegSizes = new HashMap<>();
    }

    /** What a Camera2Session should use for one camera id. */
    public static final class Plan {
        public final int extension;
        public final int previewWidth;
        public final int previewHeight;
        public final List<int[]> jpegSizes;

        Plan(int extension, int previewWidth, int previewHeight, List<int[]> jpegSizes) {
            this.extension = extension;
            this.previewWidth = previewWidth;
            this.previewHeight = previewHeight;
            this.jpegSizes = jpegSizes;
        }
    }

    public static int pickExtension(List<Integer> supported, int mode) {
        if (supported == null || supported.isEmpty()) {
            return -1;
        }
        final int[] order;
        switch (mode) {
            case NanogramFeatures.PHOTO_MODE_HDR:
                order = new int[]{EXT_HDR, EXT_AUTOMATIC};
                break;
            case NanogramFeatures.PHOTO_MODE_NIGHT:
                order = new int[]{EXT_NIGHT, EXT_AUTOMATIC};
                break;
            default:
                order = new int[]{EXT_AUTOMATIC, EXT_HDR, EXT_NIGHT};
                break;
        }
        for (int ext : order) {
            if (supported.contains(ext)) {
                return ext;
            }
        }
        return -1;
    }

    /** Largest size within 3% of the aspect and under the pixel cap, else the largest under the cap, else null. */
    public static int[] pickStillSize(List<int[]> sizes, float aspect, long maxPixels) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        int[] best = null;
        int[] bestAny = null;
        for (int[] s : sizes) {
            final long area = (long) s[0] * s[1];
            if (s[0] <= 0 || s[1] <= 0 || area > maxPixels) {
                continue;
            }
            if (bestAny == null || area > (long) bestAny[0] * bestAny[1]) {
                bestAny = s;
            }
            if (aspect > 0 && Math.abs(((float) s[0] / s[1]) / aspect - 1f) <= 0.03f) {
                if (best == null) {
                    best = s;
                } else {
                    final long bestArea = (long) best[0] * best[1];
                    if (area > bestArea || area == bestArea && aspectError(s, aspect) < aspectError(best, aspect)) {
                        best = s;
                    }
                }
            }
        }
        return best != null ? best : bestAny;
    }

    private static float aspectError(int[] s, float aspect) {
        return Math.abs(((float) s[0] / s[1]) / aspect - 1f);
    }

    /** Same rule as Camera2Session.chooseOptimalSize (notBigger = false), but null for an empty list. */
    public static int[] pickPreviewSize(List<int[]> sizes, int width, int height) {
        if (sizes == null || sizes.isEmpty() || width <= 0 || height <= 0) {
            return null;
        }
        int[] bestAspect = null;
        int[] bestBig = null;
        int[] largest = null;
        for (int[] o : sizes) {
            final long area = (long) o[0] * o[1];
            if (largest == null || area > (long) largest[0] * largest[1]) {
                largest = o;
            }
            if (o[1] == o[0] * height / width && o[0] >= width && o[1] >= height) {
                if (bestAspect == null || area < (long) bestAspect[0] * bestAspect[1]) {
                    bestAspect = o;
                }
            } else if (area <= (long) width * height * 4 && o[0] >= width && o[1] >= height) {
                if (bestBig == null || area < (long) bestBig[0] * bestBig[1]) {
                    bestBig = o;
                }
            }
        }
        if (bestAspect != null) {
            return bestAspect;
        }
        return bestBig != null ? bestBig : largest;
    }

    private static String key(String cameraId, int extension) {
        return cameraId + ":" + extension;
    }

    public static void blacklist(String cameraId, int extension) {
        blacklist.add(key(cameraId, extension));
    }

    public static boolean isBlacklisted(String cameraId, int extension) {
        return blacklist.contains(key(cameraId, extension));
    }

    public static void clearBlacklist() {
        blacklist.clear();
    }

    public static List<Integer> withoutBlacklisted(String cameraId, List<Integer> supported) {
        final List<Integer> result = new ArrayList<>();
        if (supported != null) {
            for (int ext : supported) {
                if (!isBlacklisted(cameraId, ext)) {
                    result.add(ext);
                }
            }
        }
        return result;
    }

    /** Which of the three picker modes (NanogramFeatures.PHOTO_MODE_*) the phone offers on at least one camera. Empty when none. */
    public static int[] modesFor(Iterable<Support> supports) {
        boolean auto = false, hdr = false, night = false;
        for (Support s : supports) {
            auto |= s.extensions.contains(EXT_AUTOMATIC);
            hdr |= s.extensions.contains(EXT_HDR);
            night |= s.extensions.contains(EXT_NIGHT);
        }
        final int count = (auto ? 1 : 0) + (hdr ? 1 : 0) + (night ? 1 : 0);
        if (count == 0) {
            return new int[0];
        }
        if (count == 1) {
            return new int[]{NanogramFeatures.PHOTO_MODE_AUTO};
        }
        int n = 1 + (hdr ? 1 : 0) + (night ? 1 : 0);
        int[] modes = new int[n];
        int i = 0;
        modes[i++] = NanogramFeatures.PHOTO_MODE_AUTO;
        if (hdr) modes[i++] = NanogramFeatures.PHOTO_MODE_HDR;
        if (night) modes[i] = NanogramFeatures.PHOTO_MODE_NIGHT;
        return modes;
    }

    /**
     * Picks the extension and the preview size for a camera from cached support, never blocking.
     * Preview sizes must also be valid for a normal session so that the size survives a fallback.
     * Returns null when the extension should not be used.
     */
    public static Plan plan(String cameraId, int mode, int viewWidth, int viewHeight, List<int[]> normalPreviewSizes) {
        final Support support = supportCache.get(cameraId);
        if (support == null) {
            warmUp();
            return null;
        }
        final int ext = pickExtension(withoutBlacklisted(cameraId, support.extensions), mode);
        if (ext < 0) {
            return null;
        }
        final List<int[]> usable = new ArrayList<>();
        final List<int[]> extPreview = support.previewSizes.get(ext);
        if (extPreview != null && normalPreviewSizes != null) {
            for (int[] s : extPreview) {
                for (int[] n : normalPreviewSizes) {
                    if (n[0] == s[0] && n[1] == s[1]) {
                        usable.add(s);
                        break;
                    }
                }
            }
        }
        final int[] preview = pickPreviewSize(usable, viewWidth, viewHeight);
        final List<int[]> jpeg = support.jpegSizes.get(ext);
        if (preview == null || jpeg == null || jpeg.isEmpty()) {
            return null;
        }
        return new Plan(ext, preview[0], preview[1], jpeg);
    }

    public static boolean anySupportedCached() {
        if (!warmedUp) {
            warmUp();
        }
        return anySupported;
    }

    /** Fills the support cache on a background thread once. */
    public static void warmUp() {
        if (warmedUp || warming) {
            return;
        }
        warming = true;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                anySupported(org.telegram.messenger.ApplicationLoader.applicationContext);
            } finally {
                warming = false;
            }
        });
    }

    public static boolean anySupported(Context context) {
        return modes(context).length > 0;
    }

    /** Blocking; call off the main thread. Refreshes the cache. */
    public static int[] modes(Context context) {
        final List<Support> all = new ArrayList<>();
        try {
            final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            for (String id : manager.getCameraIdList()) {
                final Support support = query(manager, id);
                supportCache.put(id, support);
                all.add(support);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        final int[] modes = modesFor(all);
        anySupported = modes.length > 0;
        warmedUp = true;
        return modes;
    }

    private static Support query(CameraManager manager, String id) {
        final Support support = new Support();
        try {
            final CameraExtensionCharacteristics chars = manager.getCameraExtensionCharacteristics(id);
            final List<Integer> supported = chars.getSupportedExtensions();
            for (int ext : PROBED) {
                if (!supported.contains(ext)) {
                    continue;
                }
                final List<int[]> preview = toList(chars.getExtensionSupportedSizes(ext, SurfaceTexture.class));
                final List<int[]> jpeg = toList(chars.getExtensionSupportedSizes(ext, ImageFormat.JPEG));
                if (preview.isEmpty() || jpeg.isEmpty()) {
                    continue;
                }
                support.extensions.add(ext);
                support.previewSizes.put(ext, preview);
                support.jpegSizes.put(ext, jpeg);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return support;
    }

    private static List<int[]> toList(List<android.util.Size> sizes) {
        final List<int[]> list = new ArrayList<>();
        if (sizes != null) {
            for (android.util.Size s : sizes) {
                list.add(new int[]{s.getWidth(), s.getHeight()});
            }
        }
        return list;
    }

    public static String name(int extension) {
        switch (extension) {
            case EXT_AUTOMATIC:
                return "auto";
            case EXT_HDR:
                return "hdr";
            case EXT_NIGHT:
                return "night";
            default:
                return "ext" + extension;
        }
    }
}
