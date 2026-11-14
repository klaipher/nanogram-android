package org.telegram.messenger.nanogram;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

public final class NanogramClipboard {

    private NanogramClipboard() {
    }

    public static boolean supportsSensitiveFlag(int sdk) {
        return sdk >= Build.VERSION_CODES.TIRAMISU;
    }

    public static void markSensitive(ClipData clip) {
        if (clip == null || !supportsSensitiveFlag(Build.VERSION.SDK_INT)) {
            return;
        }
        final PersistableBundle extras = new PersistableBundle();
        extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
        clip.getDescription().setExtras(extras);
    }

    public static boolean isPrivateInviteLink(String link) {
        return link != null && (link.contains("/+") || link.contains("/joinchat/"));
    }

    public static boolean copyLink(String link) {
        return copy(link, isPrivateInviteLink(link));
    }

    public static boolean copySensitive(CharSequence text) {
        return copy(text, true);
    }

    private static boolean copy(CharSequence text, boolean sensitive) {
        try {
            final ClipboardManager clipboard = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            final ClipData clip = ClipData.newPlainText("label", text);
            if (sensitive) {
                markSensitive(clip);
            }
            clipboard.setPrimaryClip(clip);
            return true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }
}
