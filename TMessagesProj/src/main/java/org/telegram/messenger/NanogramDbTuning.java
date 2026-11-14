package org.telegram.messenger;

import android.app.Activity;

import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;

import java.util.ArrayList;
import java.util.List;

public class NanogramDbTuning {

    private static final String KEY = "nanogramDbTuning";
    private static volatile int enabled = -1;

    public static boolean isEnabled() {
        int value = enabled;
        if (value == -1) {
            boolean on = false;
            try {
                on = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean(KEY, false);
            } catch (Throwable ignore) {}
            enabled = value = on ? 1 : 0;
        }
        return value == 1;
    }

    public static void setEnabled(boolean on) {
        enabled = on ? 1 : 0;
        try {
            ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
        } catch (Throwable ignore) {}
    }

    public static List<String> pragmas(boolean flagOn, int performanceClass, boolean is64Bit) {
        ArrayList<String> list = new ArrayList<>();
        if (!flagOn) {
            return list;
        }
        if (performanceClass >= 1) {
            list.add("PRAGMA cache_size = -8192");
        }
        if (performanceClass >= 2 && is64Bit) {
            list.add("PRAGMA mmap_size = 67108864");
        }
        list.add("PRAGMA wal_autocheckpoint = 1000");
        // Refresh planner statistics only for tables whose indexes changed a lot (bit 0x10000 = analyze up to
        // a bounded number of rows); cheap on an up-to-date database.
        list.add("PRAGMA optimize=0x10002");
        return list;
    }

    /** Small side database (file paths): small page cache, no mmap. */
    public static List<String> lightPragmas(boolean flagOn) {
        ArrayList<String> list = new ArrayList<>();
        if (flagOn) {
            list.add("PRAGMA cache_size = -2048");
            list.add("PRAGMA optimize=0x10002");
        }
        return list;
    }

    private static void run(SQLiteDatabase database, List<String> pragmas) {
        for (String pragma : pragmas) {
            try {
                database.executeFast(pragma).stepThis().dispose();
            } catch (SQLiteException e) {
                FileLog.e(e);
            }
        }
    }

    public static void applyLight(SQLiteDatabase database) {
        run(database, lightPragmas(isEnabled()));
    }

    public static void apply(SQLiteDatabase database) {
        if (!isEnabled()) {
            return;
        }
        run(database, pragmas(true, SharedConfig.getDevicePerformanceClass(), android.os.Process.is64Bit()));
    }
}
