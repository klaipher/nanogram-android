package org.telegram.messenger.nanogram;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import org.telegram.messenger.FileLog;

public final class NanogramPhotoPicker {

    private NanogramPhotoPicker() {
    }

    public static boolean canUse(boolean flagEnabled, int sdk, boolean pickerAvailable) {
        return flagEnabled && sdk >= Build.VERSION_CODES.TIRAMISU && pickerAvailable;
    }

    public static boolean isVideoType(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }

    public static Intent createIntent(Context context, boolean images, boolean video) {
        try {
            if (context == null || !canUse(NanogramSystemFlags.isEnabled(NanogramSystemFlags.SYSTEM_PHOTO_PICKER), Build.VERSION.SDK_INT, MediaStore.isPhotoPickerAvailable())) {
                return null;
            }
            final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            if (images && !video) {
                intent.setType("image/*");
            } else if (video && !images) {
                intent.setType("video/*");
            }
            return intent;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    public static boolean isVideo(Context context, Uri uri) {
        if (uri == null) {
            return false;
        }
        if (uri.toString().contains("video")) {
            return true;
        }
        try {
            return isVideoType(context.getContentResolver().getType(uri));
        } catch (Throwable e) {
            return false;
        }
    }
}
