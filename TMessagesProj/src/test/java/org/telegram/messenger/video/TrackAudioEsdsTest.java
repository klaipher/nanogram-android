package org.telegram.messenger.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.coremedia.iso.boxes.sampleentry.AudioSampleEntry;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;

/** Guards the AAC esds box: isoparser 1.1.22 wrote it empty and broke every round video. */
public class TrackAudioEsdsTest {

    private static byte[] serialize(int rate, int channels) throws Exception {
        AudioSampleEntry entry = Track.createAacSampleEntry(rate, channels, "audio/mp4a-latm", 96000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entry.getBox(Channels.newChannel(out));
        return out.toByteArray();
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i + pattern.length <= data.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static byte[] hex(String s) {
        byte[] r = new byte[s.length() / 2];
        for (int i = 0; i < r.length; i++) r[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return r;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    @Test
    public void mono48kHzHasFullEsds() throws Exception {
        byte[] data = serialize(48000, 1);
        assertEquals(0x4b, data.length);
        int esds = indexOf(data, "esds".getBytes("US-ASCII"));
        assertTrue(esds > 4);
        int size = ((data[esds - 4] & 0xff) << 24) | ((data[esds - 3] & 0xff) << 16) | ((data[esds - 2] & 0xff) << 8) | (data[esds - 1] & 0xff);
        assertEquals(0x27, size);
        assertTrue(size > 12);
        assertTrue(indexOf(data, hex("05021188")) >= 0);
    }

    @Test
    public void stereo44kHzHasAudioSpecificConfig() throws Exception {
        byte[] data = serialize(44100, 2);
        assertTrue(indexOf(data, hex("05021210")) >= 0);
    }

    @Test
    public void mono48kHzMatchesIsoparser106Bytes() throws Exception {
        assertEquals("0000004b6d703461000000000000000100000000000000000001001000000000bb800000000000276573647300000000031900000004114015000600000177000000bb8005021188060102",
                toHex(serialize(48000, 1)));
    }
}
