package org.telegram.messenger.nanogram;

public final class NanogramLiveLocation {

    private NanogramLiveLocation() {
    }

    // "Until stopped" is period 0x7FFFFFFF; the int sum with the message date overflows and reads as already over.
    public static boolean isExpired(int messageDate, int period, int now) {
        return (long) messageDate + period <= now;
    }
}
