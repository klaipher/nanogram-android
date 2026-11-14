package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class NanogramSafeRunnerTest {

    @Test
    public void returnsTaskResult() {
        assertTrue(NanogramSafeRunner.run(() -> true, 500));
        assertFalse(NanogramSafeRunner.run(() -> false, 500));
    }

    @Test
    public void exceptionIsFalse() {
        assertFalse(NanogramSafeRunner.run(() -> {
            throw new IllegalStateException();
        }, 500));
    }

    @Test
    public void timeoutIsFalseAndInterrupts() throws Exception {
        final CountDownLatch interrupted = new CountDownLatch(1);
        final boolean result = NanogramSafeRunner.run(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return true;
        }, 50);
        assertFalse(result);
        assertTrue(interrupted.await(2, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    public void reusesThreadsAcrossCalls() {
        for (int i = 0; i < 50; i++) {
            assertTrue(NanogramSafeRunner.run(() -> true, 500));
        }
        assertTrue(NanogramSafeRunner.poolSizeForTest() <= 4);
    }

    @Test
    public void runsOffTheCallingThread() {
        final Thread caller = Thread.currentThread();
        final AtomicBoolean other = new AtomicBoolean();
        NanogramSafeRunner.run(() -> {
            other.set(Thread.currentThread() != caller);
            return true;
        }, 500);
        assertTrue(other.get());
    }

    @Test
    public void inlineThreshold() {
        assertTrue(NanogramSafeRunner.shouldRunInline(0));
        assertTrue(NanogramSafeRunner.shouldRunInline(1000));
        assertFalse(NanogramSafeRunner.shouldRunInline(1001));
        assertEquals(1000, NanogramSafeRunner.INLINE_MAX_LENGTH);
    }
}
