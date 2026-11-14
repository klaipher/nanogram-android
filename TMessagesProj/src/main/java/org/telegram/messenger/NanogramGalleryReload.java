package org.telegram.messenger;

public final class NanogramGalleryReload {

    public static final long DELAY_NO_RELOAD = -1;
    public static final long DELAY_PICKER_MS = 200;
    public static final long DELAY_DEFAULT_MS = 2000;

    private NanogramGalleryReload() {
    }

    public static long observerDelayMs(boolean appPaused, boolean pickerVisible) {
        if (appPaused) {
            return DELAY_NO_RELOAD;
        }
        return pickerVisible ? DELAY_PICKER_MS : DELAY_DEFAULT_MS;
    }

    /** The observers already flag every MediaStore change, so a clean gallery never needs a rescan on open. */
    public static boolean shouldReloadOnOpen(boolean dirty, boolean changedWhilePaused) {
        return dirty || changedWhilePaused;
    }

    public static boolean countsChanged(int cachedImages, int images, int cachedVideos, int videos) {
        return images != cachedImages || cachedVideos >= 0 && videos != cachedVideos;
    }
}
