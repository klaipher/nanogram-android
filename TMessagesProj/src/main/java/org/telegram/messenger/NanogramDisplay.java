package org.telegram.messenger;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

/** Adaptive refresh rate hints (Android 15 ARR). Everything here is a no-op unless the experiment is on. */
public final class NanogramDisplay {

    public static final float CONTENT_FPS = 60f;
    public static final int NO_PREFERRED_RATE = 0;

    private NanogramDisplay() {
    }

    public static boolean isAdaptiveRefreshEnabled() {
        return supportsHints(Build.VERSION.SDK_INT) && NanogramExperiments.isEnabled(NanogramExperiments.ADAPTIVE_REFRESH_RATE);
    }

    public static boolean defaultDisplayHasArr() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA || ApplicationLoader.applicationContext == null) {
                return false;
            }
            android.hardware.display.DisplayManager dm = (android.hardware.display.DisplayManager) ApplicationLoader.applicationContext.getSystemService(android.content.Context.DISPLAY_SERVICE);
            android.view.Display display = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY);
            return display != null && display.hasArrSupport();
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean supportsHints(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM;
    }

    public static boolean supportsVelocityHint(int sdkInt) {
        return supportsHints(sdkInt);
    }

    /** The window-level rate for overlays: the panel maximum by default, no preference when the experiment lets the system decide. */
    public static float overlayRefreshRate(boolean adaptive, float screenMax) {
        return adaptive ? NO_PREFERRED_RATE : screenMax;
    }

    public static void voteContent(View view) {
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || !isAdaptiveRefreshEnabled()) {
            return;
        }
        if (view.getRequestedFrameRate() != CONTENT_FPS) {
            view.setRequestedFrameRate(CONTENT_FPS);
        }
    }

    public static void voteContent(Drawable drawable) {
        if (drawable != null && drawable.getCallback() instanceof View) {
            voteContent((View) drawable.getCallback());
        }
    }

    public static void onFling(View view, float velocityPxPerSecond) {
        if (view == null || !supportsVelocityHint(Build.VERSION.SDK_INT) || !NanogramExperiments.isEnabled(NanogramExperiments.ADAPTIVE_REFRESH_RATE)) {
            return;
        }
        view.setFrameContentVelocity(Math.abs(velocityPxPerSecond));
    }
}
