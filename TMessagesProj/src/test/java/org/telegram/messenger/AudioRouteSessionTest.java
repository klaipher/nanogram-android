package org.telegram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AudioRouteSessionTest {

    private static final int SCO = 7;
    private static final int BLE = 26;
    private static final int SPEAKER = 2;

    private static final class FakePlatform implements AudioRouteSession.Platform {
        volatile int active = AudioRouteSession.NO_ROUTE;
        volatile Runnable listener;
        int requests;
        int clears;
        int removedListeners;
        /** -1: never activates; 0: immediately; n: after n requests it activates, with the given delay. */
        int activateOnRequest = 1;
        long activateDelayMs;
        boolean accept = true;

        @Override
        public boolean request(int type) {
            requests++;
            if (!accept) {
                return false;
            }
            if (activateOnRequest > 0 && requests >= activateOnRequest) {
                final Runnable fire = () -> {
                    active = type;
                    final Runnable l = listener;
                    if (l != null) {
                        l.run();
                    }
                };
                if (activateDelayMs <= 0) {
                    fire.run();
                } else {
                    final long delay = activateDelayMs;
                    new Thread(() -> {
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ignored) {
                        }
                        fire.run();
                    }).start();
                }
            }
            return true;
        }

        @Override
        public int activeType() {
            return active;
        }

        @Override
        public void clear() {
            clears++;
            active = AudioRouteSession.NO_ROUTE;
        }

        @Override
        public Object addListener(Runnable onChange) {
            listener = onChange;
            return onChange;
        }

        @Override
        public void removeListener(Object handle) {
            removedListeners++;
            listener = null;
        }
    }

    @Test
    public void routesWhenTheListenerFiresLate() {
        final FakePlatform platform = new FakePlatform();
        platform.activateDelayMs = 150;
        final AudioRouteSession session = new AudioRouteSession(platform, SCO);
        assertEquals(AudioRouteSession.Outcome.ROUTED, session.acquire(SCO, 2000, true));
        assertEquals(1, platform.requests);
        assertEquals(0, platform.clears);
        assertEquals(1, platform.removedListeners);
        assertTrue(session.acquireMs() >= 100);
        assertTrue(session.isOwned());
    }

    @Test
    public void fallsBackWhenTheRouteNeverComesAndClearsWhatItSet() {
        final FakePlatform platform = new FakePlatform();
        platform.activateOnRequest = -1;
        final AudioRouteSession session = new AudioRouteSession(platform, SCO);
        assertEquals(AudioRouteSession.Outcome.FALLBACK, session.acquire(SCO, 200, true));
        assertEquals(2, platform.requests);
        assertEquals(2, platform.clears);
        assertFalse(session.isOwned());
        assertEquals(AudioRouteSession.Outcome.FALLBACK, session.outcome());
    }

    @Test
    public void retriesOnceAfterClearing() {
        final FakePlatform platform = new FakePlatform();
        platform.activateOnRequest = 2;
        final AudioRouteSession session = new AudioRouteSession(platform, BLE);
        assertEquals(AudioRouteSession.Outcome.ROUTED, session.acquire(BLE, 200, true));
        assertEquals(2, platform.requests);
        assertEquals(1, platform.clears);
        session.release();
        assertEquals(AudioRouteSession.NO_ROUTE, platform.active);
    }

    @Test
    public void refusedRequestFallsBack() {
        final FakePlatform platform = new FakePlatform();
        platform.accept = false;
        final AudioRouteSession session = new AudioRouteSession(platform, SCO);
        assertEquals(AudioRouteSession.Outcome.FALLBACK, session.acquire(SCO, 100, true));
        assertEquals(0, platform.clears);
    }

    @Test
    public void releaseWithoutAcquireDoesNothing() {
        final FakePlatform platform = new FakePlatform();
        platform.active = SCO;
        new AudioRouteSession(platform, SCO).release();
        assertEquals(0, platform.clears);
        assertEquals(SCO, platform.active);
    }

    @Test
    public void releaseIsIdempotent() {
        final FakePlatform platform = new FakePlatform();
        final AudioRouteSession session = new AudioRouteSession(platform, SCO);
        session.acquire(SCO, 500, true);
        session.release();
        session.release();
        assertEquals(1, platform.clears);
    }

    @Test
    public void releaseKeepsARouteSomeoneElseTookOver() {
        final FakePlatform platform = new FakePlatform();
        final AudioRouteSession session = new AudioRouteSession(platform, SCO);
        session.acquire(SCO, 500, true);
        platform.active = SPEAKER;
        session.release();
        assertEquals(0, platform.clears);
        assertEquals(SPEAKER, platform.active);
    }

    @Test
    public void nothingToRouteForAutomatic() {
        final FakePlatform platform = new FakePlatform();
        final AudioRouteSession session = new AudioRouteSession(platform, 0);
        assertEquals(AudioRouteSession.Outcome.NOT_NEEDED, session.acquire(AudioRouteSession.NO_ROUTE, 500, true));
        assertEquals(0, platform.requests);
        assertEquals(AudioRouteSession.Outcome.NOT_NEEDED, AudioRouteSession.none(15).outcome());
    }

    @Test
    public void withoutAThreadToBlockOnlyTheRequestIsMade() {
        final FakePlatform platform = new FakePlatform();
        platform.activateOnRequest = -1;
        final AudioRouteSession session = new AudioRouteSession(platform, SCO);
        assertEquals(AudioRouteSession.Outcome.ROUTED, session.acquire(SCO, 2000, false));
        assertEquals(1, platform.requests);
        assertTrue(session.isOwned());
    }

    @Test
    public void cancelDuringTheWaitReturnsQuicklyAndWithdrawsTheRequest() throws Exception {
        final FakePlatform platform = new FakePlatform();
        platform.activateOnRequest = -1;
        final AudioRouteSession session = new AudioRouteSession(platform, SCO);
        final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicReference<AudioRouteSession.Outcome> result = new java.util.concurrent.atomic.AtomicReference<>();
        final Thread worker = new Thread(() -> result.set(session.acquire(SCO, 10_000, true, cancelled::get)));
        worker.start();
        Thread.sleep(120);
        cancelled.set(true);
        worker.join(2000);
        assertFalse(worker.isAlive());
        assertEquals(AudioRouteSession.Outcome.FALLBACK, result.get());
        assertEquals(1, platform.requests);
        assertEquals(1, platform.clears);
        assertFalse(session.isOwned());
        assertEquals(1, platform.removedListeners);
    }

    @Test
    public void cancelBeforeTheFirstRequestMakesNoRequest() {
        final FakePlatform platform = new FakePlatform();
        final AudioRouteSession session = new AudioRouteSession(platform, SCO);
        assertEquals(AudioRouteSession.Outcome.FALLBACK, session.acquire(SCO, 2000, true, () -> true));
        assertEquals(0, platform.requests);
        assertFalse(session.isOwned());
    }

    @Test
    public void neverCancelledBehavesAsBefore() {
        final FakePlatform platform = new FakePlatform();
        final AudioRouteSession session = new AudioRouteSession(platform, SCO);
        assertEquals(AudioRouteSession.Outcome.ROUTED, session.acquire(SCO, 2000, true, () -> false));
        assertTrue(session.isOwned());
    }
}
