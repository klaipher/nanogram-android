package org.telegram.messenger.nanogram;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Records every uncaught Java exception to its own small file, synchronously, before chaining to the
 * previous handler (upstream's FileLog handler, then the system one that kills the process). Works in
 * release builds and does not depend on the log queue, which may be dead at crash time.
 */
public final class NanogramCrashHandler implements Thread.UncaughtExceptionHandler {

    private static final int MAX_FILES = 20;
    private static final int MAX_FRAMES = 120;
    private static final int MAX_CAUSES = 5;
    private static final int MAX_MESSAGE = 300;

    private final Thread.UncaughtExceptionHandler previous;
    private final File dir;
    private final Context context;
    private boolean recording;

    private NanogramCrashHandler(Thread.UncaughtExceptionHandler previous, File dir, Context context) {
        this.previous = previous;
        this.dir = dir;
        this.context = context;
    }

    public static File directory(Context context) {
        return new File(context.getFilesDir(), "nanogram_crashes");
    }

    public static void install(Context context) {
        final Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof NanogramCrashHandler) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new NanogramCrashHandler(current, directory(context), context));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable exception) {
        synchronized (this) {
            if (!recording) {
                recording = true;
                try {
                    write(thread, exception);
                } catch (Throwable ignore) {
                }
            }
        }
        if (previous != null) {
            previous.uncaughtException(thread, exception);
        }
    }

    private void write(Thread thread, Throwable exception) throws IOException {
        dir.mkdirs();
        final long now = System.currentTimeMillis();
        final StringBuilder sb = new StringBuilder(4096);
        sb.append("time: ").append(NanogramDiagnostics.formatTime(now)).append('\n');
        sb.append("app: ").append(NanogramDiagnostics.appLabel(context)).append('\n');
        sb.append("thread: ").append(thread == null ? "?" : thread.getName()).append('\n');
        sb.append("process uptime ms: ").append(SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()).append('\n');
        final Runtime runtime = Runtime.getRuntime();
        sb.append("java heap used/total/max MB: ")
                .append((runtime.totalMemory() - runtime.freeMemory()) >> 20).append('/')
                .append(runtime.totalMemory() >> 20).append('/')
                .append(runtime.maxMemory() >> 20).append('\n');
        sb.append("threads: ").append(Thread.activeCount()).append('\n');
        sb.append('\n');
        Throwable t = exception;
        for (int depth = 0; t != null && depth < MAX_CAUSES; depth++) {
            sb.append(depth == 0 ? "" : "Caused by: ").append(t.getClass().getName());
            final String message = t.getMessage();
            if (message != null) {
                sb.append(": ").append(message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) + "..." : message);
            }
            sb.append('\n');
            final StackTraceElement[] stack = t.getStackTrace();
            for (int i = 0; i < stack.length && i < MAX_FRAMES; i++) {
                sb.append("\tat ").append(stack[i]).append('\n');
            }
            if (stack.length > MAX_FRAMES) {
                sb.append("\t... ").append(stack.length - MAX_FRAMES).append(" more\n");
            }
            t = t.getCause() == t ? null : t.getCause();
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir, "crash_" + now + ".txt"))) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        prune(dir);
    }

    private static void prune(File dir) {
        final List<File> files = list(dir);
        for (int i = MAX_FILES; i < files.size(); i++) {
            files.get(i).delete();
        }
    }

    public static List<File> list(File dir) {
        final File[] files = dir.listFiles((d, name) -> name.startsWith("crash_") && name.endsWith(".txt"));
        if (files == null) {
            return new ArrayList<>();
        }
        Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
        return new ArrayList<>(Arrays.asList(files));
    }

    public static long timeOf(File crashFile) {
        try {
            final String name = crashFile.getName();
            return Long.parseLong(name.substring("crash_".length(), name.length() - ".txt".length()));
        } catch (Exception e) {
            return crashFile.lastModified();
        }
    }
}
