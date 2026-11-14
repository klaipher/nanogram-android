package org.telegram.messenger.nanogram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/** User-facing Nanogram switches shown in Settings > Nanogram features. */
public final class NanogramFeatures {

    private static final String PREFS = "nanogram_features";
    private static final String SEARCH_OPERATORS = "search_operators";
    private static final String LINK_STRIP = "link_strip_tracking";
    private static final String LINK_CONFIRM_TELEGRAM = "link_confirm_telegram";

    private static volatile Boolean searchOperators;
    private static volatile Boolean linkStrip;
    private static volatile Boolean linkConfirmTelegram;

    private NanogramFeatures() {
    }

    public static boolean searchOperators() {
        Boolean value = searchOperators;
        if (value == null) {
            searchOperators = value = read(SEARCH_OPERATORS, true);
        }
        return value;
    }

    public static void setSearchOperators(boolean value) {
        searchOperators = value;
        write(SEARCH_OPERATORS, value);
    }

    public static boolean stripTrackingParams() {
        Boolean value = linkStrip;
        if (value == null) {
            linkStrip = value = read(LINK_STRIP, false);
        }
        return value;
    }

    public static void setStripTrackingParams(boolean value) {
        linkStrip = value;
        write(LINK_STRIP, value);
    }

    public static boolean confirmTelegramLinks() {
        Boolean value = linkConfirmTelegram;
        if (value == null) {
            linkConfirmTelegram = value = read(LINK_CONFIRM_TELEGRAM, false);
        }
        return value;
    }

    public static void setConfirmTelegramLinks(boolean value) {
        linkConfirmTelegram = value;
        write(LINK_CONFIRM_TELEGRAM, value);
    }

    // For links the user opens or copies; null stays null and message text never goes through here.
    public static String cleanLink(String url) {
        return stripTrackingParams() ? NanogramLinkHygiene.clean(url) : url;
    }

    private static boolean read(String key, boolean fallback) {
        try {
            final Context context = ApplicationLoader.applicationContext;
            if (context != null) {
                return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, fallback);
            }
        } catch (Throwable ignore) {
        }
        return fallback;
    }

    private static void write(String key, boolean value) {
        try {
            final SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            editor.putBoolean(key, value).apply();
        } catch (Throwable ignore) {
        }
    }
}
