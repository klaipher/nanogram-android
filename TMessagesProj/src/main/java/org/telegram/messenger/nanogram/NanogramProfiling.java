package org.telegram.messenger.nanogram;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.ProfilingManager;
import android.os.ProfilingResult;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * System traces through ProfilingManager (Android 15+; ANR and cold-start triggers need Android 16).
 * Everything stays local in the app cache until the user shares the diagnostics zip.
 */
public final class NanogramProfiling {

    public static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    public static final int MAX_FILES = 6;

    private static final String PREFS = "nanogram_profiling";
    private static final String KEY_TRIGGERS = "triggers";
    private static final String DIR = "ng_profiles";

    public interface Listener {
        void onDone(File file, String error);
    }

    private NanogramProfiling() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= 35;
    }

    public static boolean triggersSupported() {
        return Build.VERSION.SDK_INT >= 36;
    }

    public static File directory(Context context) {
        final File dir = new File(context.getCacheDir(), DIR);
        dir.mkdirs();
        return dir;
    }

    public static boolean isTriggersEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TRIGGERS, false);
    }

    public static void setTriggersEnabled(Context context, boolean enabled) {
        final SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_TRIGGERS, enabled).apply();
        if (triggersSupported()) {
            if (enabled) {
                registerTriggers(context);
            } else {
                clearTriggers(context);
            }
        }
    }

    /** Called at app start: only registers when the user turned triggers on. */
    public static void onAppStart(Context context) {
        if (triggersSupported() && isTriggersEnabled(context)) {
            registerTriggers(context);
        }
    }

    public static void recordSystemTrace(Context context, Executor executor, Listener listener) {
        if (!isSupported()) {
            listener.onDone(null, "Needs Android 15 (API 35)");
            return;
        }
        try {
            Api35.record(context, executor, listener);
        } catch (Throwable e) {
            FileLog.e(e);
            listener.onDone(null, e.getClass().getSimpleName());
        }
    }

    public static List<File> list(Context context) {
        final File[] files = directory(context).listFiles();
        final List<File> result = new ArrayList<>();
        if (files != null) {
            result.addAll(Arrays.asList(files));
        }
        result.sort(Comparator.comparingLong(File::lastModified).reversed());
        return result;
    }

    public static void clear(Context context) {
        for (File file : list(context)) {
            file.delete();
        }
    }

    /** Names of files to drop so at most {@link #MAX_FILES} remain and their size stays under {@link #MAX_TOTAL_BYTES}; newest are kept. */
    static List<Integer> indexesToDelete(long[] sizesNewestFirst) {
        final List<Integer> drop = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < sizesNewestFirst.length; i++) {
            total += sizesNewestFirst[i];
            if (i >= MAX_FILES || total > MAX_TOTAL_BYTES) {
                drop.add(i);
            }
        }
        return drop;
    }

    private static void prune(Context context) {
        final List<File> files = list(context);
        final long[] sizes = new long[files.size()];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = files.get(i).length();
        }
        for (int index : indexesToDelete(sizes)) {
            files.get(index).delete();
        }
    }

    private static File store(Context context, String sourcePath, String tag) throws IOException {
        final File source = new File(sourcePath);
        final File target = new File(directory(context), (tag == null || tag.isEmpty() ? "trace" : tag) + "-" + System.currentTimeMillis() + ".perfetto-trace");
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
            final byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
        source.delete();
        prune(context);
        return target;
    }

    @RequiresApi(35)
    private static final class Api35 {
        static void record(Context context, Executor executor, Listener listener) {
            final ProfilingManager manager = (ProfilingManager) context.getSystemService(Context.PROFILING_SERVICE);
            if (manager == null) {
                listener.onDone(null, "ProfilingManager unavailable");
                return;
            }
            manager.requestProfiling(ProfilingManager.PROFILING_TYPE_SYSTEM_TRACE, new Bundle(), "manual", null, executor, result -> {
                if (result.getErrorCode() != ProfilingResult.ERROR_NONE || result.getResultFilePath() == null) {
                    listener.onDone(null, "error " + result.getErrorCode() + (result.getErrorMessage() == null ? "" : " " + result.getErrorMessage()));
                    return;
                }
                try {
                    listener.onDone(store(context, result.getResultFilePath(), "manual"), null);
                } catch (Throwable e) {
                    listener.onDone(null, e.getClass().getSimpleName());
                }
            });
        }
    }

    // ProfilingTrigger and the trigger registration methods are API 36: looked up by name so a signature change fails soft.
    private static void registerTriggers(Context context) {
        try {
            final Object manager = context.getSystemService(Context.PROFILING_SERVICE);
            if (manager == null) {
                return;
            }
            final Class<?> triggerClass = Class.forName("android.os.ProfilingTrigger");
            final Class<?> builderClass = Class.forName("android.os.ProfilingTrigger$Builder");
            final List<Object> triggers = new ArrayList<>();
            for (String field : new String[]{"TRIGGER_TYPE_ANR", "TRIGGER_TYPE_COLD_START"}) {
                final int type = triggerClass.getField(field).getInt(null);
                final Object builder = builderClass.getConstructor(int.class).newInstance(type);
                triggers.add(builderClass.getMethod("build").invoke(builder));
            }
            final Method add = manager.getClass().getMethod("addProfilingTriggers", List.class);
            add.invoke(manager, triggers);
            final Executor executor = Runnable::run;
            final Consumer<ProfilingResult> consumer = result -> {
                try {
                    if (result.getErrorCode() == ProfilingResult.ERROR_NONE && result.getResultFilePath() != null) {
                        store(context, result.getResultFilePath(), "trigger");
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            };
            manager.getClass().getMethod("registerForAllProfilingResults", Executor.class, Consumer.class).invoke(manager, executor, consumer);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void clearTriggers(Context context) {
        try {
            final Object manager = context.getSystemService(Context.PROFILING_SERVICE);
            if (manager != null) {
                manager.getClass().getMethod("clearProfilingTriggers").invoke(manager);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
