package org.telegram.messenger.nanogram;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a task with a time limit on a shared daemon pool (no queue) instead of a new executor per call.
 * Linkify ignores interrupts, so a timed-out task can keep its thread: when all pool threads are busy a call gets a one-off daemon thread, never waits behind them.
 * Short inputs skip the pool: see {@link #INLINE_MAX_LENGTH}.
 */
public final class NanogramSafeRunner {

    /** Linkify cannot backtrack catastrophically on text this short, so it runs on the calling thread. */
    public static final int INLINE_MAX_LENGTH = 1000;

    private static final int MAX_POOL_THREADS = 4;
    private static final AtomicInteger counter = new AtomicInteger();
    private static final ThreadPoolExecutor pool = createPool();

    private NanogramSafeRunner() {
    }

    private static Thread newThread(Runnable runnable) {
        final Thread thread = new Thread(runnable, "ng-safe-" + counter.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    private static ThreadPoolExecutor createPool() {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, MAX_POOL_THREADS, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), NanogramSafeRunner::newThread, (runnable, self) -> newThread(runnable).start());
        return executor;
    }

    public static boolean shouldRunInline(int length) {
        return length <= INLINE_MAX_LENGTH;
    }

    /** True if the task finished in time and returned true. A task that times out is interrupted and its result dropped. */
    public static boolean run(Callable<Boolean> task, long timeoutMs) {
        Future<Boolean> future = null;
        try {
            future = pool.submit(task);
            final Boolean result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return result != null && result;
        } catch (TimeoutException e) {
            future.cancel(true);
        } catch (InterruptedException e) {
            if (future != null) {
                future.cancel(true);
            }
            Thread.currentThread().interrupt();
        } catch (Exception ignore) {
        }
        return false;
    }

    static int poolSizeForTest() {
        return pool.getPoolSize();
    }
}
