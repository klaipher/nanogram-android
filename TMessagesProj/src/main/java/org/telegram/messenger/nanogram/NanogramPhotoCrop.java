package org.telegram.messenger.nanogram;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Pair;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;

public class NanogramPhotoCrop {

    private NanogramPhotoCrop() {
    }

    public static Bitmap renderHighQualityCrop(MediaController.MediaEditState entry, int targetSize) {
        final MediaController.CropState cropState = entry.cropState;
        if (cropState == null || entry.isVideo || entry.isLivePhoto() || entry.paintPath != null || targetSize <= 0) {
            return null;
        }
        final String path = sourcePath(entry);
        if (path == null) {
            return null;
        }

        final BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        final int width = bounds.outWidth;
        final int height = bounds.outHeight;
        if (width <= 0 || height <= 0 || Math.max(width, height) <= targetSize) {
            return null;
        }

        final int[] orientation = sourceOrientation(entry);
        final int sample = computeSampleSize(width, height, cropState, orientation[0], targetSize, memoryBudget());
        if (sample <= 0) {
            return null;
        }

        final BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError e) {
            bitmap = null;
        }
        return cropAndRecycle(bitmap, cropState, orientation);
    }

    public static Bitmap bakeCropForViewer(MediaController.MediaEditState entry, Bitmap viewerBitmap, int[] viewerOrientation, int targetSize) {
        // Runs on the UI thread: crop the bitmap already in memory; the full-resolution render happens in rebuildPhoto when sending.
        final Bitmap cropped = viewerBitmap != null ? PhotoViewer.createCroppedBitmap(viewerBitmap, entry.cropState, viewerOrientation, true) : null;
        return cropped != null ? cropped : rebakeCroppedBitmap(entry);
    }

    /** The edit source: the filtered image when there is one, else the original. */
    private static String sourcePath(MediaController.MediaEditState entry) {
        final String path = entry.filterPath != null && !entry.filterPath.isEmpty() ? entry.filterPath : entry.getPath();
        return path == null || path.isEmpty() || !new File(path).exists() ? null : path;
    }

    public static int[] sourceOrientation(MediaController.MediaEditState entry) {
        if (entry.filterPath != null && !entry.filterPath.isEmpty()) {
            // the filtered copy is written upright and carries no EXIF of its own
            return new int[]{0, 0};
        }
        if (entry instanceof MediaController.PhotoEntry) {
            final MediaController.PhotoEntry photo = (MediaController.PhotoEntry) entry;
            return new int[]{photo.orientation, photo.invert};
        }
        final Pair<Integer, Integer> exif = AndroidUtilities.getImageOrientation(entry.getPath());
        return new int[]{exif.first, exif.second};
    }

    public static Bitmap rebakeCroppedBitmap(MediaController.MediaEditState entry) {
        final String path = sourcePath(entry);
        if (entry.cropState == null || path == null) {
            return null;
        }
        final int size = AndroidUtilities.getPhotoSize(true);
        return cropAndRecycle(StoryEntry.getScaledBitmap(opts -> BitmapFactory.decodeFile(path, opts), size, size, false, true), entry.cropState, sourceOrientation(entry));
    }

    /** Cuts {@code cropState} out of a decoded source and releases the source. */
    private static Bitmap cropAndRecycle(Bitmap source, MediaController.CropState cropState, int[] orientation) {
        if (source == null) {
            return null;
        }
        try {
            return PhotoViewer.createCroppedBitmap(source, cropState, orientation, true);
        } finally {
            source.recycle();
        }
    }

    /** What is actually free right now, with the same headroom StoryEntry.getScaledBitmap keeps. */
    static long memoryBudget() {
        final Runtime runtime = Runtime.getRuntime();
        final long available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        return (long) (available / 1.1f);
    }

    static int computeSampleSize(int width, int height, MediaController.CropState cropState, int sourceRotation, int targetSize, long maxBytes) {
        return NanogramCropMath.computeSampleSize(width, height, cropState.cropPw, cropState.cropPh, cropState.cropScale, cropState.transformRotation + sourceRotation, targetSize, maxBytes);
    }
}
