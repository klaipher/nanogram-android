package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

public class NanogramImageFlags {

    private static final String KEY_GPU_PREUPLOAD = "nanogramGpuPreupload";
    public static final int MAX_PREUPLOAD_BYTES = 4 * 1024 * 1024;

    private static volatile int gpuPreupload = -1;

    public static boolean isGpuPreuploadEnabled() {
        int value = gpuPreupload;
        if (value == -1) {
            boolean on = false;
            try {
                SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                on = NanogramExperiments.resolve(prefs.contains(KEY_GPU_PREUPLOAD) ? prefs.getBoolean(KEY_GPU_PREUPLOAD, false) : null, NanogramMediaCapabilities.autoGpuPreupload(NanogramMediaCapabilities.performanceClass(), NanogramMediaCapabilities.isLowRam()));
            } catch (Throwable ignore) {}
            gpuPreupload = value = on ? 1 : 0;
        }
        return value == 1;
    }

    public static boolean isGpuPreuploadOverridden() {
        try {
            return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).contains(KEY_GPU_PREUPLOAD);
        } catch (Throwable e) {
            return false;
        }
    }

    public static void clearGpuPreuploadOverride() {
        gpuPreupload = -1;
        try {
            ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().remove(KEY_GPU_PREUPLOAD).apply();
        } catch (Throwable ignore) {}
    }

    public static void setGpuPreuploadEnabled(boolean on) {
        gpuPreupload = on ? 1 : 0;
        try {
            SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit();
            editor.putBoolean(KEY_GPU_PREUPLOAD, on).apply();
        } catch (Throwable ignore) {}
    }

    public static boolean shouldPreupload(boolean flagOn, boolean hasReceivers, boolean recycled, boolean hardware, int byteCount) {
        return flagOn && hasReceivers && !recycled && !hardware && byteCount > 0 && byteCount <= MAX_PREUPLOAD_BYTES;
    }

    public static void preuploadIfEnabled(Bitmap bitmap, boolean hasReceivers) {
        if (bitmap == null || !isGpuPreuploadEnabled()) {
            return;
        }
        try {
            boolean hardware = bitmap.getConfig() == Bitmap.Config.HARDWARE;
            if (shouldPreupload(true, hasReceivers, bitmap.isRecycled(), hardware, bitmap.getAllocationByteCount())) {
                long start = System.nanoTime();
                bitmap.prepareToDraw();
                NanogramPerfStats.recordSince(NanogramPerfStats.IMAGE_UPLOAD, start);
            }
        } catch (Throwable e) {
            FileLog.e(e, false);
        }
    }
}
