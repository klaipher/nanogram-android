package org.telegram.messenger;

import android.os.SystemClock;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.function.BooleanSupplier;

public class NanogramFileCopy {

    public static final long CHUNK_SIZE = 4L * 1024 * 1024;
    private static final long PROGRESS_INTERVAL_MS = 500;

    public interface Progress {
        void onProgress(long done, long total);
    }

    public static long copy(FileChannel source, FileChannel destination, long size, BooleanSupplier cancelled, Progress progress) throws IOException {
        long done = 0;
        long lastProgress = 0;
        while (done < size) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                break;
            }
            long moved = source.transferTo(done, Math.min(CHUNK_SIZE, size - done), destination);
            if (moved <= 0) {
                if (source.size() <= done) {
                    break;
                }
                moved = copyThroughBuffer(source, destination, done, Math.min(CHUNK_SIZE, size - done));
                if (moved <= 0) {
                    break;
                }
            }
            done += moved;
            if (progress != null) {
                long now = nowMs();
                if (done >= size || lastProgress <= now - PROGRESS_INTERVAL_MS) {
                    lastProgress = now;
                    progress.onProgress(done, size);
                }
            }
        }
        return done;
    }

    private static long copyThroughBuffer(FileChannel source, FileChannel destination, long position, long count) throws IOException {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int) Math.min(count, 1024 * 1024));
        int read = source.read(buffer, position);
        if (read <= 0) {
            return 0;
        }
        buffer.flip();
        long written = 0;
        while (buffer.hasRemaining()) {
            written += destination.write(buffer);
        }
        return written;
    }

    private static long nowMs() {
        try {
            return SystemClock.elapsedRealtime();
        } catch (Throwable e) {
            return System.nanoTime() / 1_000_000;
        }
    }
}
