package org.telegram.messenger.nanogram;

import android.content.Context;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.Arrays;

/**
 * Policy for the rolling low-verbosity log that release builds keep without the debug toggle:
 * errors, warnings and key lifecycle lines only, capped in size and age. Verbose logs stay behind
 * {@link BuildVars#LOGS_ENABLED} exactly as upstream.
 */
public final class NanogramLog {

    public static final long MAX_TOTAL_BYTES = 5L * 1024 * 1024;
    public static final long MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000;
    public static final long MAX_QUIET_FILE_BYTES = 1024 * 1024;
    private static final int MAX_FILES = 30;

    // A tight error loop must not turn the quiet log into a CPU or disk cost.
    private static final int BURST_LIMIT = 40;
    private static final long BURST_WINDOW_MS = 10_000;
    private static long windowStart;
    private static int windowCount;
    private static int droppedInWindow;
    private static long eventWindowStart;
    private static int eventCount;
    private static final String PREF_VERBOSE_OFF_AT = "logsVerboseOffAt";

    private NanogramLog() {
    }

    public static boolean shouldLog() {
        return BuildVars.LOGS_ENABLED || allowQuiet();
    }

    // Key events have their own small budget so an error flood cannot hide them.
    public static boolean shouldLogEvent() {
        return BuildVars.LOGS_ENABLED || allowEvent();
    }

    private static synchronized boolean allowEvent() {
        final long now = SystemClock.elapsedRealtime();
        if (now - eventWindowStart > BURST_WINDOW_MS) {
            eventWindowStart = now;
            eventCount = 0;
        }
        return eventCount++ < BURST_LIMIT;
    }

    private static synchronized boolean allowQuiet() {
        final long now = SystemClock.elapsedRealtime();
        if (now - windowStart > BURST_WINDOW_MS) {
            final int dropped = droppedInWindow;
            windowStart = now;
            windowCount = 0;
            droppedInWindow = 0;
            if (dropped > 0) {
                windowCount++;
                FileLog.i("quiet log: dropped " + dropped + " lines in the previous " + (BURST_WINDOW_MS / 1000) + " s");
            }
        }
        if (windowCount >= BURST_LIMIT) {
            droppedInWindow++;
            return false;
        }
        windowCount++;
        return true;
    }

    public static boolean isVerbose() {
        return BuildVars.LOGS_ENABLED;
    }

    public static void setVerbose(boolean enabled) {
        final boolean wasEnabled = BuildVars.LOGS_ENABLED;
        BuildVars.LOGS_ENABLED = enabled || BuildVars.DEBUG_VERSION;
        final android.content.SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE).edit().putBoolean("logsEnabled", BuildVars.LOGS_ENABLED);
        if (wasEnabled && !BuildVars.LOGS_ENABLED) {
            editor.putLong(PREF_VERBOSE_OFF_AT, System.currentTimeMillis());
        }
        editor.commit();
        FileLog.i("verbose logs " + (BuildVars.LOGS_ENABLED ? "enabled" : "disabled") + " by the user");
    }

    // Verbose files stay (age cap only) for a while after the switch is turned off, so they can still be exported.
    public static boolean verboseRecently() {
        if (BuildVars.LOGS_ENABLED) {
            return true;
        }
        try {
            final long offAt = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE).getLong(PREF_VERBOSE_OFF_AT, 0);
            return offAt != 0 && System.currentTimeMillis() - offAt < MAX_AGE_MS;
        } catch (Throwable e) {
            return false;
        }
    }

    // Reopening the newest small quiet file of today keeps frequent process restarts from using up the file cap.
    public static File reusableQuietFile(File dir, String date) {
        final File[] files = dir.listFiles((d, name) -> name.endsWith(".txt") && !name.endsWith("_mtproto.txt") && name.startsWith(date.substring(0, 10)));
        File best = null;
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.length() < MAX_QUIET_FILE_BYTES / 2 && (best == null || file.lastModified() > best.lastModified())) {
                    best = file;
                }
            }
        }
        return best;
    }

    public static long logsSize() {
        final File dir = AndroidUtilities.getLogsDir();
        long total = 0;
        final File[] files = dir == null ? null : dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    total += file.length();
                }
            }
        }
        return total;
    }

    public static void prune(File dir) {
        if (dir == null || BuildVars.LOGS_ENABLED) {
            return;
        }
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        final boolean capsApply = !verboseRecently();
        long total = 0;
        int kept = 0;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            final boolean tooOld = now - file.lastModified() > MAX_AGE_MS;
            if (tooOld || capsApply && (kept >= MAX_FILES || total + file.length() > MAX_TOTAL_BYTES)) {
                file.delete();
            } else {
                total += file.length();
                kept++;
            }
        }
    }
}
