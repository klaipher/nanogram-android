package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Nanogram branding for strings that name the app itself. Cloud language packs win over local strings, so the
 * app name is replaced in whatever value is shown, in every language. Strings about the Telegram network
 * (Premium, Stars, accounts, "anyone on Telegram") are not in the list and stay unchanged.
 */
public final class NanogramBranding {

    public static final String APP_NAME = "Nanogram";
    private static final String UPSTREAM_NAME = "Telegram";

    private static final Set<String> KEYS = new HashSet<>(Arrays.asList(
            "AgeVerificationNeedCameraPermission",
            "AllowFillNumber",
            "AllowReadCall",
            "AllowReadCallAndLog",
            "AllowReadCallLog",
            "AppName",
            "AppNameBeta",
            "AppUpdate",
            "AppUpdateBeta",
            "BotLocationPermissionRequestDeniedApp",
            "BrowserExternalRestricted",
            "ChangePasscodeInfo",
            "ClearingCacheDescription",
            "ContactsPermissionAlert",
            "Gift2ExportTONUpdateRequiredText",
            "LiveLocationPausedText",
            "LocalDatabaseInfo",
            "LowDiskSpaceMessage",
            "NotificationsPermissionAlertSubtitle",
            "Page1Title",
            "PermissionBackgroundLocation",
            "PermissionDrawAboveOtherApps",
            "PermissionDrawAboveOtherAppsGroupCall",
            "PermissionFSILockscreen",
            "PermissionNoAudioStorageStory",
            "PermissionNoAudioVideoWithHint",
            "PermissionNoAudioWithHint",
            "PermissionNoBluetoothWithHint",
            "PermissionNoCameraMicVideo",
            "PermissionNoCameraWithHint",
            "PermissionNoContactsSaving",
            "PermissionNoContactsSharing",
            "PermissionNoLocation",
            "PermissionNoLocationFriends",
            "PermissionNoLocationNavigation",
            "PermissionNoLocationStory",
            "PermissionNoStorageAvatar",
            "PermissionStorageWithHint",
            "PermissionXiaomiLockscreen",
            "PremiumPreviewAppIconDescription",
            "QRCodePermissionNoCameraWithHint",
            "SdCardErrorDescription",
            "StorageUsageTelegram",
            "StorageUsageTelegramLess",
            "TelegramVersion",
            "UnsupportedBlockMessage",
            "UnsupportedMessageMessage",
            "UpdateAppAlert",
            "UpdateContactsMessage",
            "UpdateTelegram",
            "VoipNeedCameraPermission",
            "VoipNeedMicCameraPermissionWithHint",
            "VoipNeedMicPermissionWithHint"
    ));

    // Nanogram names for upstream's alternate launcher icons (the artwork is replaced in the app module).
    private static final Map<String, String> FIXED = new HashMap<>();
    static {
        FIXED.put("AppIconVintage", "Comet");
        FIXED.put("AppIconAqua", "Orbit");
        FIXED.put("AppIconPremium", "Glass");
        FIXED.put("AppIconTurbo", "Speed");
        FIXED.put("AppIconNox", "Night");
    }

    private static int[] ids;
    private static int[] fixedIds;
    private static String[] fixedValues;

    private NanogramBranding() {
    }

    private static int[] ids() {
        int[] result = ids;
        if (result == null) {
            result = new int[] {
                R.string.AgeVerificationNeedCameraPermission,
                R.string.AllowFillNumber,
                R.string.AllowReadCall,
                R.string.AllowReadCallAndLog,
                R.string.AllowReadCallLog,
                R.string.AppName,
                R.string.AppNameBeta,
                R.string.AppUpdate,
                R.string.AppUpdateBeta,
                R.string.BotLocationPermissionRequestDeniedApp,
                R.string.BrowserExternalRestricted,
                R.string.ChangePasscodeInfo,
                R.string.ClearingCacheDescription,
                R.string.ContactsPermissionAlert,
                R.string.Gift2ExportTONUpdateRequiredText,
                R.string.LiveLocationPausedText,
                R.string.LocalDatabaseInfo,
                R.string.LowDiskSpaceMessage,
                R.string.NotificationsPermissionAlertSubtitle,
                R.string.Page1Title,
                R.string.PermissionBackgroundLocation,
                R.string.PermissionDrawAboveOtherApps,
                R.string.PermissionDrawAboveOtherAppsGroupCall,
                R.string.PermissionFSILockscreen,
                R.string.PermissionNoAudioStorageStory,
                R.string.PermissionNoAudioVideoWithHint,
                R.string.PermissionNoAudioWithHint,
                R.string.PermissionNoBluetoothWithHint,
                R.string.PermissionNoCameraMicVideo,
                R.string.PermissionNoCameraWithHint,
                R.string.PermissionNoContactsSaving,
                R.string.PermissionNoContactsSharing,
                R.string.PermissionNoLocation,
                R.string.PermissionNoLocationFriends,
                R.string.PermissionNoLocationNavigation,
                R.string.PermissionNoLocationStory,
                R.string.PermissionNoStorageAvatar,
                R.string.PermissionStorageWithHint,
                R.string.PermissionXiaomiLockscreen,
                R.string.PremiumPreviewAppIconDescription,
                R.string.QRCodePermissionNoCameraWithHint,
                R.string.SdCardErrorDescription,
                R.string.StorageUsageTelegram,
                R.string.StorageUsageTelegramLess,
                R.string.TelegramVersion,
                R.string.UnsupportedBlockMessage,
                R.string.UnsupportedMessageMessage,
                R.string.UpdateAppAlert,
                R.string.UpdateContactsMessage,
                R.string.UpdateTelegram,
                R.string.VoipNeedCameraPermission,
                R.string.VoipNeedMicCameraPermissionWithHint,
                R.string.VoipNeedMicPermissionWithHint
            };
            Arrays.sort(result);
            ids = result;
        }
        return result;
    }

    public static boolean isAppNameString(String key, int res) {
        return (res != 0 && Arrays.binarySearch(ids(), res) >= 0) || (key != null && KEYS.contains(key));
    }

    private static String fixed(String key, int res) {
        if (key != null) {
            String value = FIXED.get(key);
            if (value != null) {
                return value;
            }
        }
        if (res == 0) {
            return null;
        }
        if (fixedIds == null) {
            int[] resIds = {R.string.AppIconVintage, R.string.AppIconAqua, R.string.AppIconPremium, R.string.AppIconTurbo, R.string.AppIconNox};
            String[] values = {"Comet", "Orbit", "Glass", "Speed", "Night"};
            fixedValues = values;
            fixedIds = resIds;
        }
        for (int i = 0; i < fixedIds.length; i++) {
            if (fixedIds[i] == res) {
                return fixedValues[i];
            }
        }
        return null;
    }

    public static String apply(String key, int res, String value) {
        if (value == null) {
            return null;
        }
        String fixedValue = fixed(key, res);
        if (fixedValue != null) {
            return fixedValue;
        }
        if (!isAppNameString(key, res)) {
            return value;
        }
        return value.replace(UPSTREAM_NAME, APP_NAME);
    }

    /** The app name as a text drawable, in place of upstream's Telegram wordmark (white, tinted by color filters). */
    public static Drawable wordmark() {
        return new Wordmark();
    }

    private static final class Wordmark extends Drawable {
        private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final int width;
        private final int height;
        private final float baselineOffset;

        Wordmark() {
            paint.setColor(0xFFFFFFFF);
            paint.setTypeface(AndroidUtilities.bold());
            paint.setTextSize(AndroidUtilities.dp(21));
            width = (int) Math.ceil(paint.measureText(APP_NAME));
            height = AndroidUtilities.dp(22);
            final Paint.FontMetrics fm = paint.getFontMetrics();
            baselineOffset = -(fm.ascent + fm.descent) / 2f;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float baseline = bounds.exactCenterY() + baselineOffset;
            float x = bounds.left + Math.max(0, (bounds.width() - width) / 2f);
            canvas.drawText(APP_NAME, x, baseline, paint);
        }

        @Override
        public int getIntrinsicWidth() {
            return width;
        }

        @Override
        public int getIntrinsicHeight() {
            return height;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
