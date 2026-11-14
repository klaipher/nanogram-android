package org.telegram.messenger.nanogram;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.ApplicationStartInfo;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Process exit history (ActivityManager.getHistoricalProcessExitReasons, Android 11+) and start history
 * (Android 15+). Everything here is empty on older versions; callers print that fact.
 */
public final class NanogramExitInfo {

    public static final int MAX_RECORDS = 30;

    public static final class Record {
        public int pid;
        public String processName;
        public int reason;
        public String reasonName;
        public int importance;
        public String importanceName;
        public int status;
        public long pssKb;
        public long rssKb;
        public long timestamp;
        public String description;
        public boolean problem;
        Object raw;
    }

    private NanogramExitInfo() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public static List<Record> load(Context context, int max) {
        if (!isSupported()) {
            return new ArrayList<>();
        }
        try {
            return Api30.load(context, max);
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    public static Record newestProblem(List<Record> records) {
        for (Record record : records) {
            if (record.problem) {
                return record;
            }
        }
        return null;
    }

    public static boolean hasTrace(Record record) {
        return isSupported() && record.raw != null && (record.reason == ApplicationExitInfo.REASON_ANR || record.reason == ApplicationExitInfo.REASON_CRASH_NATIVE);
    }

    public static InputStream openTrace(Record record) throws IOException {
        if (!isSupported() || record.raw == null) {
            return null;
        }
        return Api30.openTrace(record.raw);
    }

    public static String describe(Record record) {
        return NanogramDiagnostics.formatTime(record.timestamp) + "  " + record.reasonName
                + (record.problem ? "  [problem]" : "")
                + "  process=" + record.processName + " pid=" + record.pid
                + "  importance=" + record.importanceName
                + "  pss=" + (record.pssKb / 1024) + "MB rss=" + (record.rssKb / 1024) + "MB"
                + "  status=" + record.status
                + (record.description == null || record.description.isEmpty() ? "" : "\n    " + record.description);
    }

    public static String describeAll(Context context) {
        final StringBuilder sb = new StringBuilder();
        if (!isSupported()) {
            sb.append("Process exit history needs Android 11 (API 30); this device runs API ").append(Build.VERSION.SDK_INT).append(".\n");
        } else {
            final List<Record> records = load(context, MAX_RECORDS);
            if (records.isEmpty()) {
                sb.append("No exit records (fresh install, or the system keeps none).\n");
            }
            for (Record record : records) {
                sb.append(describe(record)).append('\n');
            }
        }
        sb.append("\nProcess start history (needs Android 15, API 35):\n");
        if (Build.VERSION.SDK_INT < 35) {
            sb.append("not available on API ").append(Build.VERSION.SDK_INT).append('\n');
        } else {
            try {
                sb.append(Api35.describeStarts(context));
            } catch (Throwable e) {
                sb.append("unavailable (").append(e.getClass().getSimpleName()).append(")\n");
            }
        }
        return sb.toString();
    }

    static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_ANR: return "ANR";
            case ApplicationExitInfo.REASON_CRASH: return "CRASH (Java)";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "DEPENDENCY_DIED";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_EXIT_SELF: return "EXIT_SELF";
            case ApplicationExitInfo.REASON_FREEZER: return "FREEZER";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_OTHER: return "OTHER";
            case ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE: return "PACKAGE_STATE_CHANGE";
            case ApplicationExitInfo.REASON_PACKAGE_UPDATED: return "PACKAGE_UPDATED";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE: return "PERMISSION_CHANGE";
            case ApplicationExitInfo.REASON_SIGNALED: return "SIGNALED";
            case ApplicationExitInfo.REASON_UNKNOWN: return "UNKNOWN";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "USER_STOPPED";
            default: return "REASON_" + reason;
        }
    }

    public static String importanceName(int importance) {
        if (importance <= 100) return "FOREGROUND";
        if (importance <= 125) return "FOREGROUND_SERVICE";
        if (importance <= 150) return "TOP_SLEEPING";
        if (importance <= 200) return "VISIBLE";
        if (importance <= 230) return "PERCEPTIBLE";
        if (importance <= 270) return "CANT_SAVE_STATE";
        if (importance <= 300) return "SERVICE";
        if (importance <= 400) return "CACHED";
        return "GONE(" + importance + ")";
    }

    /** User-facing wording for the diagnostics screen. */
    public static String friendlyReason(Record record) {
        switch (record.reason) {
            case ApplicationExitInfo.REASON_ANR: return "stopped responding (ANR)";
            case ApplicationExitInfo.REASON_CRASH: return "crashed";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "crashed (native code)";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "stopped by the system: low memory";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "stopped by the system: too much resource use";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "could not start";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "stopped: a required service died";
            case ApplicationExitInfo.REASON_FREEZER: return "frozen by the system";
            case ApplicationExitInfo.REASON_SIGNALED: return "was killed by the system";
            default: return reasonName(record.reason).toLowerCase();
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static final class Api30 {
        static List<Record> load(Context context, int max) {
            final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            final List<ApplicationExitInfo> infos = am.getHistoricalProcessExitReasons(null, 0, max);
            final List<Record> out = new ArrayList<>();
            if (infos == null) {
                return out;
            }
            for (ApplicationExitInfo info : infos) {
                final Record r = new Record();
                r.pid = info.getPid();
                r.processName = info.getProcessName();
                r.reason = info.getReason();
                r.reasonName = reasonName(r.reason);
                r.importance = info.getImportance();
                r.importanceName = importanceName(r.importance);
                r.status = info.getStatus();
                r.pssKb = info.getPss();
                r.rssKb = info.getRss();
                r.timestamp = info.getTimestamp();
                String description = info.getDescription();
                if (description != null && description.length() > 400) {
                    description = description.substring(0, 400) + "...";
                }
                r.description = description;
                r.problem = isProblem(r);
                r.raw = info;
                out.add(r);
            }
            return out;
        }

        static InputStream openTrace(Object raw) throws IOException {
            return ((ApplicationExitInfo) raw).getTraceInputStream();
        }

        // Reclaiming a cached background process is normal; being killed while visible, perceptible or running a service is not.
        static boolean isProblem(Record r) {
            switch (r.reason) {
                case ApplicationExitInfo.REASON_ANR:
                case ApplicationExitInfo.REASON_CRASH:
                case ApplicationExitInfo.REASON_CRASH_NATIVE:
                case ApplicationExitInfo.REASON_LOW_MEMORY:
                case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                case ApplicationExitInfo.REASON_DEPENDENCY_DIED:
                case ApplicationExitInfo.REASON_FREEZER:
                    return true;
                case ApplicationExitInfo.REASON_SIGNALED:
                case ApplicationExitInfo.REASON_OTHER:
                case ApplicationExitInfo.REASON_UNKNOWN:
                    return r.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED;
                default:
                    return false;
            }
        }
    }

    @RequiresApi(35)
    private static final class Api35 {
        static String describeStarts(Context context) {
            final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            final List<ApplicationStartInfo> infos = am.getHistoricalProcessStartReasons(15);
            final StringBuilder sb = new StringBuilder();
            if (infos == null || infos.isEmpty()) {
                return "no start records\n";
            }
            for (ApplicationStartInfo info : infos) {
                final Map<Integer, Long> ts = info.getStartupTimestamps();
                final Long launch = ts == null ? null : ts.get(ApplicationStartInfo.START_TIMESTAMP_LAUNCH);
                final Long fork = ts == null ? null : ts.get(ApplicationStartInfo.START_TIMESTAMP_FORK);
                final Long onCreate = ts == null ? null : ts.get(ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE);
                final Long firstFrame = ts == null ? null : ts.get(ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME);
                sb.append("pid=").append(info.getPid())
                        .append(" process=").append(info.getProcessName())
                        .append(" type=").append(startType(info.getStartType()))
                        .append(" reason=").append(startReason(info.getReason()))
                        .append(" component=").append(info.getStartComponent())
                        .append(" state=").append(startupState(info.getStartupState()))
                        .append(" forceStopped=").append(info.wasForceStopped());
                if (launch != null) {
                    if (fork != null) sb.append(" fork+").append((fork - launch) / 1_000_000L).append("ms");
                    if (onCreate != null) sb.append(" onCreate+").append((onCreate - launch) / 1_000_000L).append("ms");
                    if (firstFrame != null) sb.append(" firstFrame+").append((firstFrame - launch) / 1_000_000L).append("ms");
                }
                sb.append('\n');
            }
            return sb.toString();
        }

        static String startType(int type) {
            switch (type) {
                case ApplicationStartInfo.START_TYPE_COLD: return "COLD";
                case ApplicationStartInfo.START_TYPE_WARM: return "WARM";
                case ApplicationStartInfo.START_TYPE_HOT: return "HOT";
                default: return "UNSET";
            }
        }

        static String startReason(int reason) {
            switch (reason) {
                case ApplicationStartInfo.START_REASON_ALARM: return "ALARM";
                case ApplicationStartInfo.START_REASON_BACKUP: return "BACKUP";
                case ApplicationStartInfo.START_REASON_BOOT_COMPLETE: return "BOOT_COMPLETE";
                case ApplicationStartInfo.START_REASON_BROADCAST: return "BROADCAST";
                case ApplicationStartInfo.START_REASON_CONTENT_PROVIDER: return "CONTENT_PROVIDER";
                case ApplicationStartInfo.START_REASON_JOB: return "JOB";
                case ApplicationStartInfo.START_REASON_LAUNCHER: return "LAUNCHER";
                case ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS: return "LAUNCHER_RECENTS";
                case ApplicationStartInfo.START_REASON_PUSH: return "PUSH";
                case ApplicationStartInfo.START_REASON_SERVICE: return "SERVICE";
                case ApplicationStartInfo.START_REASON_START_ACTIVITY: return "START_ACTIVITY";
                default: return "OTHER";
            }
        }

        static String startupState(int state) {
            switch (state) {
                case ApplicationStartInfo.STARTUP_STATE_STARTED: return "STARTED";
                case ApplicationStartInfo.STARTUP_STATE_ERROR: return "ERROR";
                case ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN: return "FIRST_FRAME_DRAWN";
                default: return String.valueOf(state);
            }
        }
    }
}
