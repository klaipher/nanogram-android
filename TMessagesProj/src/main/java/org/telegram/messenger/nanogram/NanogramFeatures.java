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

    private static final String ON_DEVICE_TRANSCRIPTION = "on_device_transcription";
    private static final String ON_DEVICE_TRANSLATION = "on_device_translation";
    private static final String MODELS_WIFI_ONLY = "on_device_models_wifi_only";

    private static final String ROUND_WIDE_ANGLE = "round_wide_angle";
    private static final String RECORDER_MIC_SWITCH = "recorder_mic_switch";
    private static final String HEADSET_VOICE_ENHANCEMENT = "headset_voice_enhancement";
    private static final String PHOTO_ENHANCED = "photo_enhanced";
    private static final String PHOTO_ENHANCED_MODE = "photo_enhanced_mode";

    public static final int PHOTO_MODE_AUTO = 0;
    public static final int PHOTO_MODE_HDR = 1;
    public static final int PHOTO_MODE_NIGHT = 2;

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

    public static boolean onDeviceTranscription() {
        return read(ON_DEVICE_TRANSCRIPTION, false);
    }

    public static void setOnDeviceTranscription(boolean value) {
        write(ON_DEVICE_TRANSCRIPTION, value);
    }

    public static boolean onDeviceTranslation() {
        return read(ON_DEVICE_TRANSLATION, false);
    }

    public static void setOnDeviceTranslation(boolean value) {
        write(ON_DEVICE_TRANSLATION, value);
    }

    public static boolean modelsWifiOnly() {
        return read(MODELS_WIFI_ONLY, true);
    }

    public static void setModelsWifiOnly(boolean value) {
        write(MODELS_WIFI_ONLY, value);
    }

    public static boolean roundWideAngle() {
        return read(ROUND_WIDE_ANGLE, false);
    }

    public static void setRoundWideAngle(boolean value) {
        write(ROUND_WIDE_ANGLE, value);
    }

    public static boolean recorderMicSwitch() {
        return read(RECORDER_MIC_SWITCH, false);
    }

    public static void setRecorderMicSwitch(boolean value) {
        write(RECORDER_MIC_SWITCH, value);
    }

    public static boolean headsetVoiceEnhancement() {
        return read(HEADSET_VOICE_ENHANCEMENT, false);
    }

    public static void setHeadsetVoiceEnhancement(boolean value) {
        write(HEADSET_VOICE_ENHANCEMENT, value);
    }

    public static boolean enhancedPhotos() {
        return read(PHOTO_ENHANCED, false);
    }

    public static void setEnhancedPhotos(boolean value) {
        write(PHOTO_ENHANCED, value);
        if (value) {
            org.telegram.messenger.camera.NanogramCameraExtensions.warmUp();
        }
    }

    public static int enhancedPhotoMode() {
        return readInt(PHOTO_ENHANCED_MODE, PHOTO_MODE_AUTO);
    }

    public static void setEnhancedPhotoMode(int value) {
        writeInt(PHOTO_ENHANCED_MODE, value);
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

    private static int readInt(String key, int fallback) {
        try {
            final Context context = ApplicationLoader.applicationContext;
            if (context != null) {
                return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key, fallback);
            }
        } catch (Throwable ignore) {
        }
        return fallback;
    }

    private static void writeInt(String key, int value) {
        try {
            final SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            editor.putInt(key, value).apply();
        } catch (Throwable ignore) {
        }
    }
}
