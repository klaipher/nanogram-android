package org.telegram.messenger;

import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.FrameMetrics;
import android.view.Window;

/** Frame time and frame interval of the main window, for the refresh-rate experiment. Passive: asks the system for nothing. Off until the tester opens Performance experiments; overlay windows (full-screen photo viewer) are not measured. */
public final class NanogramFrameStats {

    private static final long MAX_INTERVAL_NANOS = 100_000_000L;

    private static final String KEY = "nanogramCollectFrameStats";

    private static HandlerThread thread;
    private static Handler handler;

    private NanogramFrameStats() {
    }

    /** Frame intervals count only while frames follow each other; an idle gap is not an interval. */
    public static boolean isContinuous(long intervalNanos) {
        return intervalNanos > 0 && intervalNanos < MAX_INTERVAL_NANOS;
    }

    public static boolean isCollecting() {
        try {
            return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean(KEY, false);
        } catch (Throwable e) {
            return false;
        }
    }

    public static void startCollecting() {
        if (isCollecting()) {
            return;
        }
        try {
            ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putBoolean(KEY, true).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static Window.OnFrameMetricsAvailableListener attach(Activity activity) {
        try {
            if (handler == null) {
                thread = new HandlerThread("nanogram-frame-stats");
                thread.start();
                handler = new Handler(thread.getLooper());
            }
            final long[] lastVsync = new long[1];
            Window.OnFrameMetricsAvailableListener listener = (window, metrics, dropCount) -> {
                if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) {
                    lastVsync[0] = 0;
                    return;
                }
                NanogramPerfStats.record(NanogramPerfStats.FRAME_TIME, metrics.getMetric(FrameMetrics.TOTAL_DURATION));
                long vsync = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP);
                if (lastVsync[0] != 0 && isContinuous(vsync - lastVsync[0])) {
                    NanogramPerfStats.record(NanogramPerfStats.FRAME_INTERVAL, vsync - lastVsync[0]);
                }
                lastVsync[0] = vsync;
            };
            activity.getWindow().addOnFrameMetricsAvailableListener(listener, handler);
            return listener;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    public static void detach(Activity activity, Window.OnFrameMetricsAvailableListener listener) {
        if (listener == null) {
            return;
        }
        try {
            activity.getWindow().removeOnFrameMetricsAvailableListener(listener);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
