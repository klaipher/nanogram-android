package org.telegram.messenger.video;

public final class RoundVideoEncodingPolicy {

    public static final int PERFORMANCE_LOW = 0;

    public static final int MIN_BOOSTED_SIZE = 480;
    public static final int MAX_SIZE = 720;
    public static final int MAX_CAMERA_REQUEST = 960;
    public static final int BOOSTED_FRAME_RATE = 30;
    public static final int MAX_BOOSTED_BITRATE = 2_000_000;
    public static final float BITS_PER_PIXEL = 0.2f;

    public static final int PROFILE_AVC_HIGH = 0x08;
    public static final int LEVEL_AVC_31 = 0x200;

    private RoundVideoEncodingPolicy() {
    }

    public static boolean boosted(boolean enabled, int performanceClass) {
        return enabled && performanceClass > PERFORMANCE_LOW;
    }

    public static int size(int serverSize, boolean boosted) {
        int size = serverSize;
        if (boosted) {
            size = Math.min(MAX_SIZE, Math.max(serverSize, MIN_BOOSTED_SIZE));
        }
        size = Math.max(2, size);
        return size & ~1;
    }

    public static int bitrate(int serverBitrate, int serverSize, int size, boolean boosted) {
        if (!boosted || size <= serverSize) {
            return serverBitrate;
        }
        long wanted = Math.round((double) BITS_PER_PIXEL * size * size * BOOSTED_FRAME_RATE);
        long cap = Math.max(serverBitrate, MAX_BOOSTED_BITRATE);
        return (int) Math.min(cap, Math.max(serverBitrate, wanted));
    }

    public static int cameraRequestSize(int outputSize, boolean boosted) {
        if (!boosted) {
            return outputSize;
        }
        return Math.max(outputSize, Math.min(outputSize * 2, MAX_CAMERA_REQUEST));
    }

    public static int frameRate(int defaultFrameRate, boolean boosted) {
        return boosted ? BOOSTED_FRAME_RATE : defaultFrameRate;
    }

    public static boolean cameraSizeAcceptable(int requestedSize, int receivedWidth, int receivedHeight) {
        long area = (long) receivedWidth * receivedHeight;
        long limit = (long) requestedSize * requestedSize * 3;
        return area <= limit;
    }

    /**
     * Profiles and levels come in pairs from CodecCapabilities.profileLevels. Returns the High profile with the lowest level
     * of at least 3.1 as {profile, level}, or null when the encoder does not offer it.
     */
    public static int[] pickHighProfile(int[] profiles, int[] levels) {
        int bestLevel = -1;
        if (profiles != null && levels != null) {
            for (int a = 0, n = Math.min(profiles.length, levels.length); a < n; a++) {
                if (profiles[a] == PROFILE_AVC_HIGH && levels[a] >= LEVEL_AVC_31 && (bestLevel < 0 || levels[a] < bestLevel)) {
                    bestLevel = levels[a];
                }
            }
        }
        return bestLevel < 0 ? null : new int[]{PROFILE_AVC_HIGH, bestLevel};
    }
}
