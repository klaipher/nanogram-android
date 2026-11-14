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

    /**
     * Max clip side (px) up to which native code skips H.264 deblocking; 0 when the flag is off. The native side
     * (gifvideo.cpp) applies it to H.264 clips by codec size, in every AnimatedFileNative decoder (chat tiles,
     * full-screen GIFs, previews). Round videos are recorded at 384 px by default and are not covered.
     */
    public static int nativeMaxSide(boolean flagOn) {
        return flagOn ? SMALL_TILE_MAX_SIDE : 0;
    }
}
