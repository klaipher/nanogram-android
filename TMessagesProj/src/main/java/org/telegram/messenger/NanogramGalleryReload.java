package org.telegram.messenger;

public final class NanogramGalleryReload {

    public static final long DELAY_NO_RELOAD = -1;
    public static final long DELAY_PICKER_MS = 200;
    public static final long DELAY_DEFAULT_MS = 2000;
    public static final long STALE_AFTER_MS = 3000;

    private NanogramGalleryReload() {
    }

    public static long observerDelayMs(boolean appPaused, boolean pickerVisible) {
        if (appPaused) {
            return DELAY_NO_RELOAD;
        }
        return pickerVisible ? DELAY_PICKER_MS : DELAY_DEFAULT_MS;
    }

    public static boolean shouldReloadOnOpen(boolean dirty, long lastLoadMs, long nowMs) {
        return dirty || lastLoadMs <= 0 || nowMs - lastLoadMs > STALE_AFTER_MS;
    }

    public static boolean countsChanged(int cachedImages, int images, int cachedVideos, int videos) {
        return images != cachedImages || cachedVideos >= 0 && videos != cachedVideos;
    }
}
