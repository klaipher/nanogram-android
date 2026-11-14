package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.concurrent.ConcurrentHashMap;

/** Device-dependent or behaviour-changing experiments. A flag with a solid device signal is automatic until a tester sets it either way; the rest are off until switched on in Debug settings. */
public final class NanogramExperiments {

    public static final String ADAPTIVE_REFRESH_RATE = "adaptiveRefreshRate";
    public static final String SELF_MANAGED_TELECOM = "selfManagedTelecom";
    public static final String CAMERA2_CAPTURE = "camera2Capture";

    private static final String PREFS = "nanogram_experiments";
    private static final ConcurrentHashMap<String, Boolean> cache = new ConcurrentHashMap<>();

    private NanogramExperiments() {
    }

    public static boolean isEnabled(String key) {
        Boolean cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean value = false;
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context != null) {
                SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                value = resolve(prefs.contains(key) ? prefs.getBoolean(key, false) : null, autoDefault(key));
                cache.put(key, value);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return value;
    }

    public static boolean resolve(Boolean stored, boolean auto) {
        return stored != null ? stored : auto;
    }

    public static boolean autoDefault(String key) {
        return ADAPTIVE_REFRESH_RATE.equals(key) && NanogramMediaCapabilities.autoAdaptiveRefresh(Build.VERSION.SDK_INT);
    }

    public static boolean isOverridden(String key) {
        try {
            return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(key);
        } catch (Throwable e) {
            return false;
        }
    }

    public static void clearOverride(String key) {
        cache.remove(key);
        try {
            ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void setEnabled(String key, boolean enabled) {
        cache.put(key, enabled);
        try {
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(key, enabled).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static boolean toggle(String key) {
        boolean value = !isEnabled(key);
        setEnabled(key, value);
        return value;
    }

    public static void count(String key) {
        try {
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putInt("count_" + key, prefs.getInt("count_" + key, 0) + 1).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static int getCount(String key) {
        try {
            return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("count_" + key, 0);
        } catch (Throwable e) {
            return 0;
        }
    }
}
