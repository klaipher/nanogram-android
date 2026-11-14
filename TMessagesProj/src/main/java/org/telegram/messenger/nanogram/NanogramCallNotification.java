package org.telegram.messenger.nanogram;

import android.text.TextUtils;

public final class NanogramCallNotification {

    private NanogramCallNotification() {
    }

    // Notification.CallStyle throws IllegalArgumentException for a Person without a name.
    public static String personName(String name) {
        return TextUtils.isEmpty(name) ? "___" : name;
    }
}
