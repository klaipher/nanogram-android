package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ConcurrentHashMap;

/** Device-dependent or behaviour-changing experiments; every flag is off until switched on in Debug settings. */
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
                value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false);
                cache.put(key, value);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return value;
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
}
