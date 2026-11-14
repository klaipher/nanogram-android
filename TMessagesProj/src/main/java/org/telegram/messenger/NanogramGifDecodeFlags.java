package org.telegram.messenger;

import android.app.Activity;

public class NanogramGifDecodeFlags {

    private static final String KEY = "nanogramGifSkipLoopFilter";
    public static final int SMALL_TILE_MAX_SIDE = 320;
    private static volatile int enabled = -1;

    public static boolean isEnabled() {
        int value = enabled;
        if (value == -1) {
            boolean on = false;
            try {
                on = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean(KEY, false);
            } catch (Throwable ignore) {}
            enabled = value = on ? 1 : 0;
        }
        return value == 1;
    }

    public static void setEnabled(boolean on) {
        enabled = on ? 1 : 0;
        try {
            ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
        } catch (Throwable ignore) {}
    }

    /** Same policy as the native side (gifvideo.cpp): H.264 deblocking is skipped for tiles up to 320 px. */
    public static boolean skipLoopFilter(boolean flagOn, int width, int height) {
        if (!flagOn || width <= 0 || height <= 0) {
            return false;
        }
        return Math.max(width, height) <= SMALL_TILE_MAX_SIDE;
    }
}
