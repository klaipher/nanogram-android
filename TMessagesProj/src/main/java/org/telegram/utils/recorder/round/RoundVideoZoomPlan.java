package org.telegram.utils.recorder.round;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Zoom ratio arithmetic for the round recorder, including the wide-angle lens stop. */
public final class RoundVideoZoomPlan {
    private static final float WIDE_THRESHOLD = 0.95f;

    private RoundVideoZoomPlan() {}

    /** Returns the lowest usable zoom ratio: 1 unless the switch is on and the camera zooms out. */
    public static float minimumRatio(@Nullable Float rangeLower, boolean wideEnabled) {
        if (!wideEnabled || rangeLower == null) return 1f;
        float lower = rangeLower;
        if (Float.isNaN(lower) || lower <= 0f || lower >= WIDE_THRESHOLD) return 1f;
        return lower;
    }

    /** Returns the ratio the recording opens at. */
    public static float restingRatio(float minimum) {
        return minimum;
    }

    /** Returns the ratio for a lens choice: the minimum when wide, otherwise 1. */
    public static float lensRatio(boolean wide, float minimum) {
        return wide && minimum < 1f ? minimum : 1f;
    }

    /** Returns the lens button stops in ascending order. */
    @NonNull
    public static float[] stops(float minimum, float maximum) {
        if (minimum < 1f) return new float[]{minimum, 1f};
        return new float[]{1f};
    }

    /** Formats a ratio with at most one decimal, independent of the default locale. */
    @NonNull
    public static String labelFor(float ratio) {
        int tenths = Math.round(ratio * 10f);
        int whole = tenths / 10;
        int fraction = tenths % 10;
        return (fraction == 0 ? Integer.toString(whole) : whole + "." + fraction) + "x";
    }

    /** Maps a pinch scale to a zoom ratio relative to the ratio the pinch started at. */
    public static float pinchRatio(float startRatio, float scale, float minimum, float maximum, float effort) {
        float ratio = startRatio + (scale - 1f) / effort;
        return Math.max(minimum, Math.min(maximum, ratio));
    }

    /** Maps a ratio in [1, maximum] to the normalized progress in [0, 1]. */
    public static float ratioToProgress(float ratio, float maximum) {
        if (maximum <= 1f) return 0f;
        return Math.max(0f, Math.min(1f, (ratio - 1f) / (maximum - 1f)));
    }

    /** Maps normalized progress in [0, 1] to a ratio in [1, maximum]. */
    public static float progressToRatio(float progress, float maximum) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        return 1f + clamped * Math.max(0f, maximum - 1f);
    }
}
