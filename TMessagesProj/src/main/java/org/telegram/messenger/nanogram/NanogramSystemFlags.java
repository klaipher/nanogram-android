package org.telegram.messenger.nanogram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Experimental switches for device-dependent or behaviour-changing system work. All default to off.
 * Debug builds flip them in the "nanogram_experimental" preferences file; the cache is read once per key.
 */
public final class NanogramSystemFlags {

    public static final String NETWORK_CALLBACK = "net_callback";
    public static final String POWER_SKIP_WHILE_CHARGING = "power_skip_charging";
    public static final String STRICT_MODE = "strictmode";
    public static final String PROGRESS_NOTIFICATIONS = "progress_notifications";
    public static final String SYSTEM_PHOTO_PICKER = "photo_picker";

    private static final String PREFS = "nanogram_experimental";
    private static final ConcurrentHashMap<String, Boolean> cache = new ConcurrentHashMap<>();

    private NanogramSystemFlags() {
    }

    public static boolean isEnabled(String key) {
        final Boolean cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean value = false;
        try {
            final Context context = ApplicationLoader.applicationContext;
            if (context != null) {
                final SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                value = prefs.getBoolean(key, false);
            }
        } catch (Throwable ignore) {
        }
        cache.put(key, value);
        return value;
    }

    public static void set(String key, boolean value) {
        cache.put(key, value);
        try {
            ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply();
        } catch (Throwable ignore) {
        }
    }
}
