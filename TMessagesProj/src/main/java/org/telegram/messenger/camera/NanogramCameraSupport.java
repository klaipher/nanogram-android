package org.telegram.messenger.camera;

public final class NanogramCameraSupport {

    private NanogramCameraSupport() {
    }

    public static boolean contains(int[] modes, int mode) {
        if (modes == null) {
            return false;
        }
        for (int m : modes) {
            if (m == mode) {
                return true;
            }
        }
        return false;
    }
}
