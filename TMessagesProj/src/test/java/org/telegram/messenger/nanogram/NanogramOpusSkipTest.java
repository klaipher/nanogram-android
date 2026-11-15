package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** The pre-skip / seek pre-roll arithmetic of NanogramOpusDecoder (the same rules as media3's OpusDecoder). */
public class NanogramOpusSkipTest {

    private static byte[] header(int channels, int preSkip) {
        byte[] h = new byte[19];
        h[0] = 'O';
        h[1] = 'p';
        h[2] = 'u';
        h[3] = 's';
        h[4] = 'H';
        h[5] = 'e';
        h[6] = 'a';
        h[7] = 'd';
        h[8] = 1;
        h[9] = (byte) channels;
        h[10] = (byte) (preSkip & 0xFF);
        h[11] = (byte) ((preSkip >> 8) & 0xFF);
        return h;
    }

    private static byte[] nativeLong(long value) {
        return ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(value).array();
    }

    private static List<byte[]> full(int preSkipSamples, int seekPreRollSamples) {
        List<byte[]> list = new ArrayList<>();
        list.add(header(1, 999)); // the header's own field must lose against the extractor's value
        list.add(nativeLong(preSkipSamples * 1_000_000_000L / 48_000));
        list.add(nativeLong(seekPreRollSamples * 1_000_000_000L / 48_000));
        return list;
    }

    @Test
    public void channelCountComesFromTheHeader() {
        assertEquals(1, NanogramOpusSkip.channelCount(header(1, 312)));
        assertEquals(2, NanogramOpusSkip.channelCount(header(2, 312)));
    }

    @Test
    public void preSkipUsesTheExtractorValueWhenThereIsOne() {
        assertEquals(312, NanogramOpusSkip.preSkipSamples(full(312, 3840)));
        assertEquals(3840, NanogramOpusSkip.preSkipSamples(full(3840, 3840)));
    }

    @Test
    public void preSkipFallsBackToTheHeaderField() {
        List<byte[]> onlyHeader = new ArrayList<>();
        onlyHeader.add(header(1, 0x0138)); // 312, little endian across both bytes
        assertEquals(312, NanogramOpusSkip.preSkipSamples(onlyHeader));
        onlyHeader.set(0, header(2, 0x1234));
        assertEquals(0x1234, NanogramOpusSkip.preSkipSamples(onlyHeader));
    }

    @Test
    public void seekPreRollIs80MsUnlessTheExtractorSaysOtherwise() {
        List<byte[]> onlyHeader = new ArrayList<>();
        onlyHeader.add(header(1, 312));
        assertEquals(3840, NanogramOpusSkip.seekPreRollSamples(onlyHeader));
        assertEquals(1920, NanogramOpusSkip.seekPreRollSamples(full(312, 1920)));
    }

    @Test
    public void nanosToSamplesIs48kHz() {
        assertEquals(312, NanogramOpusSkip.nanosToSamples(6_500_000L));
        assertEquals(3840, NanogramOpusSkip.nanosToSamples(80_000_000L));
        assertEquals(0, NanogramOpusSkip.nanosToSamples(0));
    }

    @Test
    public void resetToTheStartSkipsTheDelayAndAnySeekSkipsThePreRoll() {
        assertEquals(312, NanogramOpusSkip.skipAfterReset(0, 312, 3840));
        assertEquals(3840, NanogramOpusSkip.skipAfterReset(1, 312, 3840));
        assertEquals(3840, NanogramOpusSkip.skipAfterReset(5_000_000, 312, 3840));
    }

    @Test
    public void nothingToSkipLeavesThePacketAlone() {
        NanogramOpusSkip.Trim trim = NanogramOpusSkip.trim(0, 1920, 2);
        assertEquals(0, trim.remainingSkipSamples);
        assertEquals(0, trim.dropBytes);
        assertFalse(trim.skipBuffer);
    }

    @Test
    public void aPacketInsideTheSkippedPartIsDroppedWhole() {
        // 960 samples of mono 16-bit = 1920 bytes against 3840 samples to skip.
        NanogramOpusSkip.Trim trim = NanogramOpusSkip.trim(3840, 1920, 2);
        assertTrue(trim.skipBuffer);
        assertEquals(3840 - 960, trim.remainingSkipSamples);
        assertEquals(1920, trim.dropBytes);
    }

    @Test
    public void aPacketThatEndsExactlyAtTheSkipIsDroppedWhole() {
        NanogramOpusSkip.Trim trim = NanogramOpusSkip.trim(960, 1920, 2);
        assertTrue(trim.skipBuffer);
        assertEquals(0, trim.remainingSkipSamples);
        assertEquals(1920, trim.dropBytes);
    }

    @Test
    public void aPacketThatCrossesTheSkipKeepsItsTail() {
        // Stereo: 4 bytes per sample, 312 to skip, a 20 ms packet of 960 samples.
        NanogramOpusSkip.Trim trim = NanogramOpusSkip.trim(312, 960 * 4, 4);
        assertFalse(trim.skipBuffer);
        assertEquals(0, trim.remainingSkipSamples);
        assertEquals(312 * 4, trim.dropBytes);
    }

    @Test
    public void thePreRollIsConsumedAcrossPackets() {
        int skip = 3840;
        int played = 0;
        for (int packet = 0; packet < 6; packet++) {
            NanogramOpusSkip.Trim trim = NanogramOpusSkip.trim(skip, 960 * 2, 2);
            skip = trim.remainingSkipSamples;
            played += (960 * 2 - trim.dropBytes) / 2;
        }
        assertEquals(0, skip);
        assertEquals(2 * 960, played); // 4 packets (80 ms) dropped, 2 played
    }

    @Test
    public void anEmptyPacketWhileSkippingKeepsTheCount() {
        NanogramOpusSkip.Trim trim = NanogramOpusSkip.trim(312, 0, 2);
        assertTrue(trim.skipBuffer);
        assertEquals(312, trim.remainingSkipSamples);
    }
}
