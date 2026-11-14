package org.telegram.messenger.nanogram;

public final class NanogramCropMath {

    private NanogramCropMath() {
    }

    // Largest power-of-two sample that keeps the cropped region at or above the send size, never coarser
    // than the upstream whole-image path and never above the memory budget; 0 means "use the upstream path".
    public static int computeSampleSize(int width, int height, float cropPw, float cropPh, float cropScale, int totalRotation, int targetSize, long maxBytes) {
        final int rotation = totalRotation % 360;
        final boolean swapped = rotation == 90 || rotation == 270;
        final int rotatedWidth = swapped ? height : width;
        final int rotatedHeight = swapped ? width : height;
        final float cropWidth = rotatedWidth * cropPw;
        final float cropHeight = rotatedHeight * cropPh;
        final float cropLongSide = Math.max(cropWidth, cropHeight) / Math.max(1f, cropScale);
        if (cropLongSide <= 0f) {
            return 0;
        }
        int sample = 1;
        while (cropLongSide / (sample * 2) >= targetSize) {
            sample *= 2;
        }
        int coarsest = 1;
        while (Math.min(width, height) / (coarsest * 2) >= targetSize) {
            coarsest *= 2;
        }
        if (sample > coarsest) {
            sample = coarsest;
        }
        while (sample < coarsest && decodedBytes(width, height, cropWidth, cropHeight, sample) > maxBytes) {
            sample *= 2;
        }
        return decodedBytes(width, height, cropWidth, cropHeight, sample) > maxBytes ? 0 : sample;
    }

    public static long decodedBytes(int width, int height, float cropWidth, float cropHeight, int sample) {
        final long source = (long) (width / sample) * (height / sample);
        final long crop = (long) (cropWidth / sample) * (long) (cropHeight / sample);
        return (source + crop) * 4L;
    }
}
