package org.telegram.messenger.nanogram;

import android.app.Notification;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.NanogramExperiments;
import org.telegram.messenger.R;

/**
 * Android 16 progress-centric notification (Notification.ProgressStyle, promoted ongoing "Live Update") for uploads and
 * video conversion. Only with the "Progress notifications" Debug experiment on API 36+; otherwise the notification is unchanged.
 */
public final class NanogramProgressNotification {

    // Build.VERSION_CODES_FULL.BAKLAVA_1 (36.1), spelled out so the JVM tests do not need the constant.
    private static final int PROMOTION_MIN_SDK_FULL = 36 * 100000 + 1;

    private NanogramProgressNotification() {
    }

    public static boolean isActive() {
        return Build.VERSION.SDK_INT >= 36 && NanogramExperiments.isEnabled(NanogramExperiments.PROGRESS_NOTIFICATIONS);
    }

    /** setRequestPromotedOngoing arrived in API 36.1; Android 16.0 gets the progress style without promotion. */
    static boolean supportsPromotion(int sdkIntFull) {
        return sdkIntFull >= PROMOTION_MIN_SDK_FULL;
    }

    public static int smallIcon(int fallback) {
        return isActive() ? R.drawable.notification : fallback;
    }

    public static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    public static String shortText(int percent) {
        return clampPercent(percent) + "%";
    }

    /** Returns {@code base} unchanged unless the flag is on and the device runs Android 16. */
    public static Notification promote(Context context, Notification base, int percent, boolean indeterminate) {
        if (!isActive()) {
            return base;
        }
        try {
            return Api36.promote(context, base, clampPercent(percent), indeterminate);
        } catch (Throwable e) {
            FileLog.e(e);
            return base;
        }
    }

    @RequiresApi(36)
    private static final class Api36 {
        static Notification promote(Context context, Notification base, int percent, boolean indeterminate) {
            final Notification.Builder builder = Notification.Builder.recoverBuilder(context, base);
            builder.setStyle(new Notification.ProgressStyle()
                    .addProgressSegment(new Notification.ProgressStyle.Segment(100))
                    .setProgress(percent)
                    .setProgressIndeterminate(indeterminate));
            if (supportsPromotion(Build.VERSION.SDK_INT_FULL)) {
                builder.setRequestPromotedOngoing(true);
            }
            if (!indeterminate) {
                builder.setShortCriticalText(shortText(percent));
            }
            return builder.build();
        }
    }
}
