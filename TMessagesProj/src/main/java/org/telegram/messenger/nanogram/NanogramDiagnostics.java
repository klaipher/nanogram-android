package org.telegram.messenger.nanogram;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Process;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.ForegroundDetector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Entry point of Nanogram's diagnostics: start-up hooks, the exportable zip and the "what went wrong
 * lately" summary shown on the diagnostics screen. All heavy work runs on a dedicated thread.
 */
public final class NanogramDiagnostics {

    public interface Listener {
        void onDone(File zip, Throwable error);
    }

    public static final class Problem {
        public long time;
        public String text;
        public boolean isNew;
    }

    private static final String PREFS = "nanogram_diagnostics";
    private static final String KEY_LOGGED_EXIT = "loggedExitTs";
    private static final String KEY_LOGGED_CRASH = "loggedCrashTs";
    private static final String KEY_VIEWED = "viewedTs";

    private static final long MAX_LOG_BYTES_PER_FILE = 8L * 1024 * 1024;
    private static final long MAX_LOG_BYTES_TOTAL = 24L * 1024 * 1024;
    private static final long MAX_VOIP_BYTES_TOTAL = 8L * 1024 * 1024;
    private static final long MAX_TRACE_BYTES = 512 * 1024;
    private static final int MAX_TRACES = 4;
    private static final long QUIET_DAYS = 3;
    private static final long VERBOSE_DAYS = 7;
    private static final String ZIP_DIR = "nanogram_diagnostics";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        final Thread thread = new Thread(r, "nanogram-diagnostics");
        thread.setDaemon(true);
        return thread;
    });

    private NanogramDiagnostics() {
    }

    public static ForegroundDetector.Listener foregroundListener() {
        return new ForegroundDetector.Listener() {
            @Override
            public void onBecameForeground() {
                FileLog.i("app foreground");
            }

            @Override
            public void onBecameBackground() {
                FileLog.i("app background");
            }
        };
    }

    public static String formatTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date(millis));
    }

    public static String appLabel(Context context) {
        try {
            final PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            final long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return info.versionName + " (" + code + ")";
        } catch (Throwable e) {
            return "unknown";
        }
    }

    private static String buildNumber(Context context) {
        try {
            final PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return String.valueOf(Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode);
        } catch (Throwable e) {
            return "0";
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void onAppStart(Context context) {
        final String label = appLabel(context);
        try {
            NanogramCrashHandler.install(context, label);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        FileLog.i("start " + label + " pid " + Process.myPid() + ", " + NanogramSystemInfo.summaryLine());
        executor.execute(() -> logPreviousExits(context));
    }

    // Puts exits and crashes since the last start into the quiet log, so a log alone tells what ended the previous run.
    private static void logPreviousExits(Context context) {
        try {
            final SharedPreferences prefs = prefs(context);
            final long loggedExit = prefs.getLong(KEY_LOGGED_EXIT, 0);
            final long loggedCrash = prefs.getLong(KEY_LOGGED_CRASH, 0);
            long newestExit = loggedExit;
            final List<NanogramExitInfo.Record> records = NanogramExitInfo.load(context, NanogramExitInfo.MAX_RECORDS);
            for (int i = records.size() - 1; i >= 0; i--) {
                final NanogramExitInfo.Record record = records.get(i);
                if (record.timestamp > loggedExit) {
                    newestExit = Math.max(newestExit, record.timestamp);
                    if (record.problem) {
                        FileLog.e("previous process exit: " + NanogramExitInfo.describe(record));
                    }
                }
            }
            long newestCrash = loggedCrash;
            final List<File> crashes = NanogramCrashHandler.list(NanogramCrashHandler.directory(context));
            Collections.reverse(crashes);
            for (File crash : crashes) {
                final long time = NanogramCrashHandler.timeOf(crash);
                if (time > loggedCrash) {
                    newestCrash = Math.max(newestCrash, time);
                    FileLog.e("previous Java crash at " + formatTime(time) + ": " + crashSummary(crash));
                }
            }
            prefs.edit().putLong(KEY_LOGGED_EXIT, newestExit).putLong(KEY_LOGGED_CRASH, newestCrash).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static String crashSummary(File crash) {
        try (BufferedReader reader = new BufferedReader(new FileReader(crash))) {
            String line;
            boolean body = false;
            String first = null;
            while ((line = reader.readLine()) != null) {
                if (!body) {
                    body = line.isEmpty();
                    continue;
                }
                if (first == null) {
                    first = line;
                } else if (line.startsWith("\tat ")) {
                    return first + " " + line.trim();
                }
            }
            return first == null ? "(empty)" : first;
        } catch (Throwable e) {
            return "(unreadable)";
        }
    }

    /** Problems newest first: exit-history entries that were not normal, plus own crash records not already covered by one. */
    public static List<Problem> recentProblems(Context context, int max) {
        final long viewed = prefs(context).getLong(KEY_VIEWED, 0);
        final List<Problem> out = new ArrayList<>();
        final List<Long> exitCrashTimes = new ArrayList<>();
        for (NanogramExitInfo.Record record : NanogramExitInfo.load(context, NanogramExitInfo.MAX_RECORDS)) {
            if (!record.problem) {
                continue;
            }
            if (record.reason == android.app.ApplicationExitInfo.REASON_CRASH) {
                exitCrashTimes.add(record.timestamp);
            }
            final Problem p = new Problem();
            p.time = record.timestamp;
            p.text = NanogramExitInfo.friendlyReason(record);
            p.isNew = record.timestamp > viewed;
            out.add(p);
        }
        for (File crash : NanogramCrashHandler.list(NanogramCrashHandler.directory(context))) {
            final long time = NanogramCrashHandler.timeOf(crash);
            boolean covered = false;
            for (long t : exitCrashTimes) {
                if (Math.abs(t - time) < 15_000) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                final Problem p = new Problem();
                p.time = time;
                p.text = "crashed";
                p.isNew = time > viewed;
                out.add(p);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.time, a.time));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    public static void markViewed(Context context) {
        prefs(context).edit().putLong(KEY_VIEWED, System.currentTimeMillis()).apply();
    }

    public static void collectAsync(Context context, Listener listener) {
        executor.execute(() -> {
            File zip = null;
            Throwable error = null;
            try {
                zip = buildZip(context);
            } catch (Throwable e) {
                error = e;
                FileLog.e(e);
            }
            final File resultZip = zip;
            final Throwable resultError = error;
            AndroidUtilities.runOnUIThread(() -> listener.onDone(resultZip, resultError));
        });
    }

    public static void runInBackground(Runnable task) {
        executor.execute(task);
    }

    private static File zipDir(Context context) {
        final File dir = new File(context.getCacheDir(), ZIP_DIR);
        dir.mkdirs();
        return dir;
    }

    public static File buildZip(Context context) throws IOException {
        final File dir = zipDir(context);
        final File[] old = dir.listFiles();
        if (old != null) {
            for (File file : old) {
                file.delete();
            }
        }
        final String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        final File zip = new File(dir, "Nanogram-diagnostics-" + buildNumber(context) + "-" + stamp + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            addText(out, "README.txt", readme(context));
            addSafely(out, "device-info.txt", () -> NanogramSystemInfo.build(context));
            addSafely(out, "exit-history.txt", () -> NanogramExitInfo.describeAll(context));
            addCrashes(context, out);
            addTraces(context, out);
            addLogs(context, out);
        } catch (IOException | RuntimeException e) {
            zip.delete();
            throw e;
        }
        return zip;
    }

    private interface TextSource {
        String get() throws Throwable;
    }

    private static void addSafely(ZipOutputStream out, String name, TextSource source) throws IOException {
        String text;
        try {
            text = source.get();
        } catch (Throwable e) {
            text = "failed: " + e.getClass().getName() + "\n";
        }
        addText(out, name, text);
    }

    private static String readme(Context context) {
        return "Nanogram diagnostics " + appLabel(context) + "\ncreated " + formatTime(System.currentTimeMillis()) + "\n"
                + NanogramSystemInfo.summaryLine() + "\n\n"
                + "Files\n"
                + "  device-info.txt    device, Android version, power and network state, permissions, notification channel states, memory\n"
                + "  exit-history.txt   why earlier Nanogram processes ended (Android 11+) and how they started (Android 15+)\n"
                + "  crashes/           uncaught Java exceptions recorded by Nanogram: stack traces only\n"
                + "  traces/            system ANR traces and native crash tombstones for the latest problems (Android 11+)\n"
                + "  logs/              Nanogram log files from the last " + (NanogramLog.isVerbose() ? VERBOSE_DAYS : QUIET_DAYS) + " days (" + (NanogramLog.isVerbose() ? "verbose" : "errors, warnings and key events") + ")\n"
                + "  voip/              call logs from the last " + QUIET_DAYS + " days\n\n"
                + "Not included: message text, contacts, phone numbers, account or session data, keys, push tokens, databases, media, heap dumps.\n"
                + "Chat names and ids are not collected. Exception messages and system descriptions are copied as they are.\n";
    }

    private static void addCrashes(Context context, ZipOutputStream out) {
        try {
            for (File crash : NanogramCrashHandler.list(NanogramCrashHandler.directory(context))) {
                addFile(out, "crashes/" + crash.getName(), crash, MAX_LOG_BYTES_PER_FILE);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void addTraces(Context context, ZipOutputStream out) {
        if (!NanogramExitInfo.isSupported()) {
            return;
        }
        try {
            int added = 0;
            for (NanogramExitInfo.Record record : NanogramExitInfo.load(context, NanogramExitInfo.MAX_RECORDS)) {
                if (added >= MAX_TRACES) {
                    break;
                }
                if (!NanogramExitInfo.hasTrace(record)) {
                    continue;
                }
                try (InputStream in = NanogramExitInfo.openTrace(record)) {
                    if (in == null) {
                        continue;
                    }
                    final String ext = record.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE ? ".tombstone.pb" : ".txt";
                    out.putNextEntry(new ZipEntry("traces/" + record.timestamp + "_" + record.reasonName + "_pid" + record.pid + ext));
                    copyCapped(in, out, MAX_TRACE_BYTES);
                    out.closeEntry();
                    added++;
                } catch (Throwable e) {
                    // One unreadable trace must not stop the rest; the entry may be half written, so the zip is still valid because closeEntry runs below.
                    try {
                        out.closeEntry();
                    } catch (Throwable ignore) {
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void addLogs(Context context, ZipOutputStream out) {
        final long days = NanogramLog.isVerbose() ? VERBOSE_DAYS : QUIET_DAYS;
        addDirectory(out, "logs/", AndroidUtilities.getLogsDir(), days, MAX_LOG_BYTES_TOTAL, true);
        addDirectory(out, "voip/", new File(context.getCacheDir(), "voip_logs"), QUIET_DAYS, MAX_VOIP_BYTES_TOTAL, false);
    }

    private static void addDirectory(ZipOutputStream out, String prefix, File dir, long days, long totalCap, boolean skipTl) {
        try {
            final File[] files = dir == null ? null : dir.listFiles();
            if (files == null) {
                return;
            }
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            final long oldest = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000;
            long total = 0;
            for (File file : files) {
                final String name = file.getName();
                if (!file.isFile() || file.lastModified() < oldest || name.endsWith(".zip") || name.endsWith(".hprof")) {
                    continue;
                }
                // TL dumps are debug-only and can carry message payloads.
                if (skipTl && name.endsWith("_mtproto.txt")) {
                    continue;
                }
                final long size = Math.min(file.length(), MAX_LOG_BYTES_PER_FILE);
                if (total + size > totalCap) {
                    break;
                }
                total += size;
                addFile(out, prefix + name, file, MAX_LOG_BYTES_PER_FILE);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void addText(ZipOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    // Large files contribute their tail: the end of a log is what led to the problem.
    private static void addFile(ZipOutputStream out, String name, File file, long maxBytes) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            final long length = file.length();
            final ZipEntry entry = new ZipEntry(name);
            entry.setTime(file.lastModified());
            out.putNextEntry(entry);
            if (length > maxBytes) {
                out.write(("[truncated: first " + ((length - maxBytes) >> 10) + " KB omitted]\n").getBytes(StandardCharsets.UTF_8));
                in.skip(length - maxBytes);
            }
            copyCapped(in, out, maxBytes);
            out.closeEntry();
        } catch (java.io.FileNotFoundException e) {
            // Rotated or deleted while exporting.
        }
    }

    private static void copyCapped(InputStream in, ZipOutputStream out, long cap) throws IOException {
        final byte[] buffer = new byte[32 * 1024];
        long copied = 0;
        int n;
        while (copied < cap && (n = in.read(buffer, 0, (int) Math.min(buffer.length, cap - copied))) > 0) {
            out.write(buffer, 0, n);
            copied += n;
        }
    }

    public static void clearAll(Context context) {
        FileLog.cleanupLogs();
        final File crashes = NanogramCrashHandler.directory(context);
        deleteFiles(crashes);
        deleteFiles(new File(context.getCacheDir(), "voip_logs"));
        deleteFiles(zipDir(context));
        final File[] hprof = AndroidUtilities.getLogsDir() == null ? null : AndroidUtilities.getLogsDir().listFiles((d, name) -> name.endsWith(".hprof"));
        if (hprof != null) {
            for (File file : hprof) {
                file.delete();
            }
        }
        prefs(context).edit().putLong(KEY_VIEWED, System.currentTimeMillis()).apply();
    }

    private static void deleteFiles(File dir) {
        final File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
    }
}
