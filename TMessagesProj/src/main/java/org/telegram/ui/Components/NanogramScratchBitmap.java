package org.telegram.ui.Components;

import android.graphics.Bitmap;

public class NanogramScratchBitmap {

    public static boolean fits(int width, int height, int scratchWidth, int scratchHeight, boolean recycled, boolean mutable) {
        return !recycled && mutable && width > 0 && height > 0 && width == scratchWidth && height == scratchHeight;
    }

    public static Bitmap reuseOrCreate(Bitmap scratch, int width, int height) {
        if (scratch != null && fits(width, height, scratch.getWidth(), scratch.getHeight(), scratch.isRecycled(), scratch.isMutable())) {
            return scratch;
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }
}
