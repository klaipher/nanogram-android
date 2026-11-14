package org.telegram.messenger.nanogram;

public final class NanogramCallNotification {

    private NanogramCallNotification() {
    }

    // Notification.CallStyle throws IllegalArgumentException for a Person without a name.
    public static String personName(String name) {
        return name == null || name.isEmpty() ? "___" : name;
    }
}
