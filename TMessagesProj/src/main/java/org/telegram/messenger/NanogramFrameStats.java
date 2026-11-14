package org.telegram.messenger;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.FrameMetrics;
import android.view.Window;

/** Frame time and frame interval of the main window, for the refresh-rate experiment. Passive: asks the system for nothing. */
public final class NanogramFrameStats {

    private static final long MAX_INTERVAL_NANOS = 100_000_000L;

    private static HandlerThread thread;
    private static Handler handler;

    private NanogramFrameStats() {
    }

    /** Frame intervals count only while frames follow each other; an idle gap is not an interval. */
    public static boolean isContinuous(long intervalNanos) {
        return intervalNanos > 0 && intervalNanos < MAX_INTERVAL_NANOS;
    }

    public static Window.OnFrameMetricsAvailableListener attach(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return null;
        }
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
        if (listener == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        try {
            activity.getWindow().removeOnFrameMetricsAvailableListener(listener);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
