package org.telegram.messenger;

import android.app.ActivityManager;
import android.content.Context;

public class NanogramImageCacheSize {

    public static final int CLASS_LOW = 0;
    public static final int CLASS_AVERAGE = 1;
    public static final int CLASS_HIGH = 2;

    private static final long MIN_4GB_CLASS = 3584L * 1024L * 1024L;
    private static final long MIN_6GB_CLASS = 5632L * 1024L * 1024L;

    public static int upstreamMegabytes(int memoryClass) {
        return Math.min(memoryClass >= 192 ? 30 : 15, memoryClass / 7);
    }

    public static int megabytes(int memoryClass, int performanceClass, boolean lowRamDevice, long totalMemBytes) {
        int base = upstreamMegabytes(memoryClass);
        if (lowRamDevice || performanceClass == CLASS_LOW || memoryClass < 192 || (totalMemBytes > 0 && totalMemBytes < MIN_4GB_CLASS)) {
            return base;
        }
        int target;
        if (performanceClass == CLASS_HIGH && memoryClass >= 256 && (totalMemBytes <= 0 || totalMemBytes >= MIN_6GB_CLASS)) {
            target = 48;
        } else {
            target = 32;
        }
        return Math.max(base, Math.min(target, memoryClass / 5));
    }

    public static int bytes(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();
        long total = 0;
        try {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            total = info.totalMem;
        } catch (Throwable ignore) {}
        int performanceClass = SharedConfig.getDevicePerformanceClass();
        return megabytes(memoryClass, performanceClass, am.isLowRamDevice(), total) * 1024 * 1024;
    }
}
