package org.telegram.messenger.nanogram;

import android.app.Notification;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

/**
 * Android 16 progress-centric notification (Notification.ProgressStyle, promoted ongoing "Live Update") for uploads and
 * video conversion. Only with the "progress_notifications" experimental flag on API 36+; otherwise the notification is unchanged.
 */
public final class NanogramProgressNotification {

    private NanogramProgressNotification() {
    }

    public static boolean isActive() {
        return Build.VERSION.SDK_INT >= 36 && NanogramSystemFlags.isEnabled(NanogramSystemFlags.PROGRESS_NOTIFICATIONS);
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
            builder.setRequestPromotedOngoing(true);
            if (!indeterminate) {
                builder.setShortCriticalText(shortText(percent));
            }
            return builder.build();
        }
    }
}
