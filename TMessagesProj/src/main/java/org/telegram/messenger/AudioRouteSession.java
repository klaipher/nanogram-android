package org.telegram.messenger;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Looper;

/**
 * One recorder's hold on the Bluetooth communication device. The recorder acquires it on its own worker thread
 * (never the main thread, where the wait is skipped), reads from the microphone only once the route is active and
 * releases it in a finally block. A release clears only what this session set, so a call or another recorder that
 * took the communication device over in the meantime keeps its routing.
 *
 * <p>The decision logic works on {@link Platform}, so it runs on the JVM with a fake.</p>
 */
public final class AudioRouteSession {

    public static final int NO_ROUTE = -1;
    public static final long ACQUIRE_TIMEOUT_MS = 2000L;

    public enum Outcome {
        /** Nothing had to be routed: automatic, built-in, wired or USB input. */
        NOT_NEEDED,
        /** The requested communication device is active. */
        ROUTED,
        /** The device could not be activated in time; the recording continues on whatever the system picks. */
        FALLBACK
    }

    /** The system side of the communication device, replaceable in tests. */
    public interface Platform {
        /** Asks the system to use this communication device; false when it is not available or was refused. */
        boolean request(int type);

        /** Type of the active communication device, or {@link #NO_ROUTE}. */
        int activeType();

        void clear();

        /** Runs onChange every time the communication device changes. Returns a handle for removeListener. */
        Object addListener(Runnable onChange);

        void removeListener(Object handle);
    }

    private final Platform platform;
    private final int choice;
    private final Object lock = new Object();
    private int routingType = NO_ROUTE;
    private boolean owned;
    private Outcome outcome = Outcome.NOT_NEEDED;
    private long acquireMs;

    /**
     * @param choice the resolved input choice ({@link AudioInputPreference#AUTOMATIC} or an AudioDeviceInfo type),
     *               kept for verification after the recording started
     */
    public AudioRouteSession(Platform platform, int choice) {
        this.platform = platform;
        this.choice = choice;
    }

    /** A session that routes nothing but remembers the choice. */
    public static AudioRouteSession none(int choice) {
        return new AudioRouteSession(null, choice);
    }

    /**
     * Activates the communication device of this type and waits for it. Blocks the calling thread for up to
     * timeoutMs, then retries once after clearing (half the time). With mayBlock false (main thread) it only
     * makes the request. Safe to call again after release.
     */
    public Outcome acquire(int type, long timeoutMs, boolean mayBlock) {
        return acquire(type, timeoutMs, mayBlock, null);
    }

    /**
     * As above, but a true result of cancelled (checked every wait slice, 50 ms at most) ends the wait: the request
     * is withdrawn and the outcome is FALLBACK, so a recorder that was stopped meanwhile does not hold the route.
     */
    public Outcome acquire(int type, long timeoutMs, boolean mayBlock, java.util.function.BooleanSupplier cancelled) {
        if (platform == null || type == NO_ROUTE) {
            outcome = Outcome.NOT_NEEDED;
            return outcome;
        }
        final long startedAt = System.nanoTime();
        routingType = type;
        Object handle = null;
        try {
            handle = platform.addListener(() -> {
                synchronized (lock) {
                    lock.notifyAll();
                }
            });
            for (int attempt = 0; attempt < 2; attempt++) {
                if (attempt == 1) {
                    clearPendingRequest();
                }
                if (cancelled != null && cancelled.getAsBoolean()) {
                    break;
                }
                if (platform.request(type)) {
                    synchronized (lock) {
                        owned = true;
                    }
                    // Without a thread to block (main thread) the request is all there is: report it as routed and
                    // let the post-start check verify it.
                    if (!mayBlock || waitUntilActive(type, attempt == 0 ? timeoutMs : timeoutMs / 2, cancelled)) {
                        outcome = Outcome.ROUTED;
                        return outcome;
                    }
                }
                if (!mayBlock) {
                    break;
                }
            }
            clearPendingRequest();
            outcome = Outcome.FALLBACK;
            return outcome;
        } finally {
            if (handle != null) {
                platform.removeListener(handle);
            }
            acquireMs = (System.nanoTime() - startedAt) / 1_000_000L;
        }
    }

    private boolean waitUntilActive(int type, long timeoutMs, java.util.function.BooleanSupplier cancelled) {
        final long end = System.nanoTime() + timeoutMs * 1_000_000L;
        synchronized (lock) {
            while (platform.activeType() != type) {
                final long remainingMs = (end - System.nanoTime()) / 1_000_000L;
                if (remainingMs <= 0 || (cancelled != null && cancelled.getAsBoolean())) {
                    return false;
                }
                try {
                    // A slice, not the whole remainder: a missed callback costs at most 50 ms.
                    lock.wait(Math.min(remainingMs, 50L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    /** Idempotent. Clears the communication device only when this session set it and it is still the active one. */
    public void release() {
        clearOwned();
    }

    /**
     * Withdraws a request that was accepted but never became active, whatever is active now: the system would
     * otherwise switch to the device later with nobody to release it. Safe because acquire never runs during a call.
     */
    private void clearPendingRequest() {
        final boolean clear;
        synchronized (lock) {
            clear = owned;
            owned = false;
        }
        if (clear && platform != null) {
            platform.clear();
        }
    }

    private void clearOwned() {
        final boolean clear;
        synchronized (lock) {
            clear = owned;
            owned = false;
        }
        if (clear && platform != null && platform.activeType() == routingType) {
            platform.clear();
        }
    }

    public Outcome outcome() {
        return outcome;
    }

    /** The resolved input choice this session was made for. */
    public int choice() {
        return choice;
    }

    /** The Bluetooth type this session asked for, or {@link #NO_ROUTE}. */
    public int routingType() {
        return routingType;
    }

    public long acquireMs() {
        return acquireMs;
    }

    public boolean isOwned() {
        synchronized (lock) {
            return owned;
        }
    }

    public static boolean onMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    /** The real communication device of the phone (Android 12+, which is the minimum SDK). */
    public static Platform systemPlatform(AudioManager am) {
        return new Platform() {
            @Override
            public boolean request(int type) {
                try {
                    for (AudioDeviceInfo device : am.getAvailableCommunicationDevices()) {
                        if (device.getType() == type) {
                            return am.setCommunicationDevice(device);
                        }
                    }
                } catch (SecurityException ignored) {
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                return false;
            }

            @Override
            public int activeType() {
                try {
                    final AudioDeviceInfo device = am.getCommunicationDevice();
                    return device == null ? NO_ROUTE : device.getType();
                } catch (Throwable e) {
                    return NO_ROUTE;
                }
            }

            @Override
            public void clear() {
                try {
                    am.clearCommunicationDevice();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }

            @Override
            public Object addListener(Runnable onChange) {
                final AudioManager.OnCommunicationDeviceChangedListener listener = device -> onChange.run();
                try {
                    am.addOnCommunicationDeviceChangedListener(Runnable::run, listener);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                return listener;
            }

            @Override
            public void removeListener(Object handle) {
                try {
                    am.removeOnCommunicationDeviceChangedListener((AudioManager.OnCommunicationDeviceChangedListener) handle);
                } catch (Throwable ignored) {
                }
            }
        };
    }
}
