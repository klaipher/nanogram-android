package org.telegram.utils.recorder.audio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LeadingSilenceTrimmerTest {

    private static ByteBuffer chunk(int... samples) {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(samples.length * 2).order(ByteOrder.nativeOrder());
        for (int sample : samples) {
            buffer.putShort((short) sample);
        }
        buffer.clear();
        return buffer;
    }

    @Test
    public void dropsZerosUntilTheFirstSample() {
        final LeadingSilenceTrimmer trimmer = new LeadingSilenceTrimmer(100);
        assertEquals(6, trimmer.leadingBytesToDrop(chunk(0, 0, 0, 5, 0, 1), 12));
        assertEquals(0, trimmer.leadingBytesToDrop(chunk(0, 0, 0, 0), 8));
        assertEquals(3, trimmer.trimmedSamples());
    }

    @Test
    public void keepsDroppingAcrossChunksOfSilence() {
        final LeadingSilenceTrimmer trimmer = new LeadingSilenceTrimmer(100);
        assertEquals(8, trimmer.leadingBytesToDrop(chunk(0, 0, 0, 0), 8));
        assertEquals(4, trimmer.leadingBytesToDrop(chunk(0, 0, 7, 7), 8));
        assertEquals(0, trimmer.leadingBytesToDrop(chunk(0, 0, 7, 7), 8));
    }

    @Test
    public void stopsAtTheBudget() {
        final LeadingSilenceTrimmer trimmer = new LeadingSilenceTrimmer(3);
        assertEquals(6, trimmer.leadingBytesToDrop(chunk(0, 0, 0, 0, 0), 10));
        assertEquals(0, trimmer.leadingBytesToDrop(chunk(0, 0), 4));
        assertEquals(3, trimmer.trimmedSamples());
    }

    @Test
    public void nothingToDropWhenTheCaptureStartsWithSound() {
        final LeadingSilenceTrimmer trimmer = new LeadingSilenceTrimmer(100);
        assertEquals(0, trimmer.leadingBytesToDrop(chunk(3, 0, 0), 6));
        assertEquals(0, trimmer.leadingBytesToDrop(chunk(0, 0, 0), 6));
    }

    @Test
    public void zeroBudgetDisablesIt() {
        assertEquals(0, new LeadingSilenceTrimmer(0).leadingBytesToDrop(chunk(0, 0), 4));
    }
}
