package org.telegram.messenger.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoundAudioTimelineTest {

    private static final int RATE = 48000;

    @Test
    public void monotonicFrameClockNeedsNoConversion() {
        long now = 5_000_000_000_000L;
        assertEquals(0, RoundAudioTimeline.videoClockOffsetNs(now - 20_000_000L, now, now + 3_600_000_000_000L));
    }

    @Test
    public void boottimeFrameClockIsConvertedByTheSleepTime() {
        long mono = 5_000_000_000_000L;
        long sleep = 7_200_000_000_000L;
        long real = mono + sleep;
        long offset = RoundAudioTimeline.videoClockOffsetNs(real - 25_000_000L, mono, real);
        assertEquals(sleep, offset);
        assertEquals(mono - 25_000_000L, real - 25_000_000L - offset);
    }

    @Test
    public void deviceThatNeverSleptKeepsZeroOffset() {
        long mono = 9_000_000_000L;
        assertEquals(0, RoundAudioTimeline.videoClockOffsetNs(mono - 10_000_000L, mono, mono));
    }

    @Test
    public void bufferStartUsesTheFramePositionOfTheTimestamp() {
        long tsNano = 10_000_000_000L;
        assertEquals(10_000_000L, RoundAudioTimeline.bufferStartUs(tsNano, 48000, 48000, RATE));
        assertEquals(9_979_167L, RoundAudioTimeline.bufferStartUs(tsNano, 48000, 47000, RATE));
        assertEquals(10_021_333L, RoundAudioTimeline.bufferStartUs(tsNano, 48000, 49024, RATE));
    }

    @Test
    public void clockIsMonotonicAndSmoothFromTheSampleCount() {
        RoundAudioTimeline.AudioClock clock = new RoundAudioTimeline.AudioClock(RATE);
        long last = Long.MIN_VALUE;
        long start = 20_000_000_000L;
        for (int i = 0; i < 100; i++) {
            long startFrame = i * 1024L;
            long tsFrame = startFrame + 2048;
            long tsNano = start + tsFrame * 1_000_000_000L / RATE + (i % 3) * 200_000L;
            long stamp = clock.stampUs(startFrame, 1024, true, tsNano, tsFrame, 0);
            assertTrue(stamp > last);
            last = stamp;
        }
        long expectedFirst = start / 1000;
        RoundAudioTimeline.AudioClock fresh = new RoundAudioTimeline.AudioClock(RATE);
        assertEquals(expectedFirst, fresh.stampUs(0, 1024, true, start + 2048L * 1_000_000_000L / RATE, 2048, 0), 1);
    }

    @Test
    public void invalidTimestampsFallBackToTheReadTimeUntilAnchored() {
        RoundAudioTimeline.AudioClock clock = new RoundAudioTimeline.AudioClock(RATE);
        long now = 3_000_000_000L;
        long stamp = clock.stampUs(0, 1024, false, 0, 0, now);
        assertEquals(now / 1000 - 21_333, stamp);
        long second = clock.stampUs(1024, 1024, true, now + 5_000_000L, 4096, now);
        assertEquals(RoundAudioTimeline.bufferStartUs(now + 5_000_000L, 4096, 1024, RATE), second);
        long third = clock.stampUs(2048, 1024, false, 0, 0, now);
        assertEquals(second + 1024L * 1_000_000L / RATE, third);
    }

    @Test
    public void timestampJumpOverThirtyMillisecondsReanchorsAndShowsAsGap() {
        RoundAudioTimeline.AudioClock clock = new RoundAudioTimeline.AudioClock(RATE);
        long tsNano = 8_000_000_000L;
        long first = clock.stampUs(0, 1024, true, tsNano, 1024, 0);
        long afterGap = clock.stampUs(1024, 1024, true, tsNano + 21_333_000L + 200_000_000L, 2048, 0);
        assertTrue(afterGap - first > 200_000);
        long next = clock.stampUs(2048, 1024, true, tsNano + 2 * 21_333_000L + 200_000_000L, 3072, 0);
        assertEquals(afterGap + 21_333, next, 2);
    }

    @Test
    public void smallJitterDoesNotMoveTheAnchor() {
        RoundAudioTimeline.AudioClock clock = new RoundAudioTimeline.AudioClock(RATE);
        long tsNano = 8_000_000_000L;
        long first = clock.stampUs(0, 1024, true, tsNano, 1024, 0);
        long second = clock.stampUs(1024, 1024, true, tsNano + 21_333_000L + 5_000_000L, 2048, 0);
        assertEquals(first + 21_333, second, 2);
    }

    @Test
    public void audioStartsAtVideoFirstUnlessAudioBeginsLater() {
        assertEquals(1_000, RoundAudioTimeline.audioStartUs(1_000, 1_015));
        assertEquals(900, RoundAudioTimeline.audioStartUs(1_000, 900));
    }

    @Test
    public void alignmentSafetyNetWaitsHalfASecond() {
        assertFalse(RoundAudioTimeline.alignmentTimedOut(400_000, -1));
        assertFalse(RoundAudioTimeline.alignmentTimedOut(400_000, 0));
        assertTrue(RoundAudioTimeline.alignmentTimedOut(600_000, 0));
    }
}
