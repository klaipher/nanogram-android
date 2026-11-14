package org.telegram.messenger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class NanogramFileCopyTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File write(int size) throws IOException {
        byte[] data = new byte[size];
        new Random(size).nextBytes(data);
        File f = folder.newFile();
        Files.write(f.toPath(), data);
        return f;
    }

    private long run(File src, File dst, java.util.function.BooleanSupplier cancelled, NanogramFileCopy.Progress progress) throws IOException {
        try (FileInputStream in = new FileInputStream(src); FileChannel s = in.getChannel(); FileChannel d = new FileOutputStream(dst).getChannel()) {
            return NanogramFileCopy.copy(s, d, s.size(), cancelled, progress);
        }
    }

    @Test
    public void copiesMultiChunkFileExactly() throws IOException {
        File src = write((int) (NanogramFileCopy.CHUNK_SIZE * 2 + 12345));
        File dst = folder.newFile();
        long done = run(src, dst, () -> false, null);
        assertEquals(src.length(), done);
        assertArrayEquals(Files.readAllBytes(src.toPath()), Files.readAllBytes(dst.toPath()));
    }

    @Test
    public void copiesEmptyAndTinyFiles() throws IOException {
        for (int size : new int[]{0, 1, 4095, 4097}) {
            File src = write(size);
            File dst = folder.newFile();
            assertEquals(size, run(src, dst, () -> false, null));
            assertArrayEquals(Files.readAllBytes(src.toPath()), Files.readAllBytes(dst.toPath()));
        }
    }

    @Test
    public void stopsWhenCancelled() throws IOException {
        File src = write((int) (NanogramFileCopy.CHUNK_SIZE * 3));
        File dst = folder.newFile();
        AtomicInteger checks = new AtomicInteger();
        long done = run(src, dst, () -> checks.incrementAndGet() > 1, null);
        assertEquals(NanogramFileCopy.CHUNK_SIZE, done);
        assertEquals(NanogramFileCopy.CHUNK_SIZE, dst.length());
    }

    @Test
    public void reportsFinalProgress() throws IOException {
        File src = write((int) (NanogramFileCopy.CHUNK_SIZE + 5));
        File dst = folder.newFile();
        long[] last = new long[2];
        run(src, dst, () -> false, (done, total) -> {
            last[0] = done;
            last[1] = total;
        });
        assertEquals(src.length(), last[0]);
        assertEquals(src.length(), last[1]);
        assertTrue(dst.length() == src.length());
    }
}
