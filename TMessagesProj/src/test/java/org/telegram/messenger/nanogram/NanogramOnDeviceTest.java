package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class NanogramOnDeviceTest {

    @Test
    public void transcriptionNeedsApi33AndTheOnDeviceRecognizer() {
        assertFalse(NanogramOnDevice.transcriptionSupported(31, true));
        assertFalse(NanogramOnDevice.transcriptionSupported(32, true));
        assertFalse(NanogramOnDevice.transcriptionSupported(33, false));
        assertTrue(NanogramOnDevice.transcriptionSupported(33, true));
        assertTrue(NanogramOnDevice.transcriptionSupported(36, true));
    }

    @Test
    public void translateCodeIsTheBareLanguage() {
        assertEquals("pt", NanogramOnDevice.translateCode("pt-BR"));
        assertEquals("pt", NanogramOnDevice.translateCode("pt_br"));
        assertEquals("zh", NanogramOnDevice.translateCode("zh-hans"));
        assertEquals("en", NanogramOnDevice.translateCode(" EN "));
        assertEquals("he", NanogramOnDevice.translateCode("iw"));
        assertEquals("id", NanogramOnDevice.translateCode("in"));
        assertNull(NanogramOnDevice.translateCode(null));
        assertNull(NanogramOnDevice.translateCode(""));
        assertNull(NanogramOnDevice.translateCode("-US"));
    }

    @Test
    public void speechTagKeepsATwoLetterRegionOnly() {
        assertEquals("pt-BR", NanogramOnDevice.speechTag("pt_br"));
        assertEquals("en-GB", NanogramOnDevice.speechTag("en-gb"));
        assertEquals("zh", NanogramOnDevice.speechTag("zh-hans"));
        assertEquals("uk", NanogramOnDevice.speechTag("uk"));
        assertNull(NanogramOnDevice.speechTag(null));
    }

    @Test
    public void blankLinesAreNotTranslatedButKept() {
        final String[] lines = "one\n\n  \ntwo".split("\n", -1);
        assertEquals(Arrays.asList(0, 3), NanogramOnDevice.translatableLines(lines));
        final String[] translated = new String[4];
        translated[0] = "eins";
        translated[3] = "zwei";
        assertEquals("eins\n\n  \nzwei", NanogramOnDevice.joinLines(lines, translated));
    }

    @Test
    public void anyMissingResultMeansServerFallback() {
        assertTrue(NanogramOnDevice.allTranslated(new String[]{"a", "b"}));
        assertFalse(NanogramOnDevice.allTranslated(new String[]{"a", null}));
        assertTrue(NanogramOnDevice.allTranslated(new String[0]));
    }

    @Test
    public void traditionalChineseTargetsGoToTheServer() {
        assertNull(NanogramOnDevice.translateTargetCode("zh-hant"));
        assertNull(NanogramOnDevice.translateTargetCode("zh-TW"));
        assertNull(NanogramOnDevice.translateTargetCode("zh_hk"));
        assertNull(NanogramOnDevice.translateTargetCode("zh-Hant-TW"));
        assertEquals("zh", NanogramOnDevice.translateTargetCode("zh-hans"));
        assertEquals("zh", NanogramOnDevice.translateTargetCode("zh"));
        assertEquals("pt", NanogramOnDevice.translateTargetCode("pt-BR"));
        assertEquals("zh", NanogramOnDevice.translateCode("zh-hant"));
    }

    @Test
    public void undeterminedLanguageIsNothingToTranslate() {
        assertTrue(NanogramOnDevice.noLanguage("und"));
        assertTrue(NanogramOnDevice.noLanguage(null));
        assertTrue(NanogramOnDevice.noLanguage(""));
        assertFalse(NanogramOnDevice.noLanguage("de"));
    }

    @Test
    public void linkOnlyLinesAreNotTranslated() {
        final String[] lines = {"see this", "https://example.com/a?b=c", "  www.example.com ", "https://x.com and more"};
        assertEquals(Arrays.asList(0, 3), NanogramOnDevice.translatableLines(lines));
    }

    @Test
    public void converterDownmixesAndDecimatesTo16k() {
        final NanogramOnDevice.Pcm16kConverter c = new NanogramOnDevice.Pcm16kConverter(48000, 2);
        final byte[] in = new byte[48000 * 2 * 2 / 10];
        for (int i = 0; i < in.length; i += 4) {
            in[i] = (byte) 0xe8;
            in[i + 1] = 0x03;
            in[i + 2] = (byte) 0xd0;
            in[i + 3] = 0x07;
        }
        final byte[] out = c.convert(in, in.length);
        assertEquals(1600 * 2, out.length);
        assertEquals(1500, (short) ((out[0] & 0xff) | (out[1] << 8)));
    }

    @Test
    public void converterKeepsStateAcrossOddChunks() {
        final NanogramOnDevice.Pcm16kConverter whole = new NanogramOnDevice.Pcm16kConverter(48000, 1);
        final NanogramOnDevice.Pcm16kConverter split = new NanogramOnDevice.Pcm16kConverter(48000, 1);
        final byte[] in = new byte[600];
        for (int i = 0; i < in.length; ++i) {
            in[i] = (byte) (i * 7);
        }
        final byte[] a = whole.convert(in, in.length);
        final byte[] first = split.convert(in, 101);
        final byte[] rest = split.convert(java.util.Arrays.copyOfRange(in, 101, in.length), in.length - 101);
        final byte[] b = new byte[first.length + rest.length];
        System.arraycopy(first, 0, b, 0, first.length);
        System.arraycopy(rest, 0, b, first.length, rest.length);
        assertArrayEquals(a, b);
        assertEquals(100 * 2, a.length);
    }

    @Test
    public void nonPremiumWithTheSwitchNeverCallsTheServer() {
        assertTrue(NanogramOnDevice.localOnly(true, false));
        assertFalse(NanogramOnDevice.mayCallServer(true, false));
        assertFalse(NanogramOnDevice.localOnly(true, true));
        assertTrue(NanogramOnDevice.mayCallServer(true, true));
        assertFalse(NanogramOnDevice.localOnly(false, false));
        assertTrue(NanogramOnDevice.mayCallServer(false, false));
        assertTrue(NanogramOnDevice.mayCallServer(false, true));
    }

    @Test
    public void localResultIsAFinalTranscriptionWithIdZero() {
        assertTrue(NanogramOnDevice.isLocalResult(0, true));
        assertFalse(NanogramOnDevice.isLocalResult(0, false));
        assertFalse(NanogramOnDevice.isLocalResult(42, true));
        assertFalse(NanogramOnDevice.isLocalResult(42, false));
    }

    @Test
    public void floatPcmClipsAndKeepsLength() {
        final short[] out = NanogramOnDevice.floatToPcm16(new float[]{0f, 1f, -1f, 2.5f, -3f, 0.5f, -0.5f, Float.NaN, 9f}, 8);
        assertEquals(8, out.length);
        assertEquals(0, out[0]);
        assertEquals(Short.MAX_VALUE, out[1]);
        assertEquals(Short.MIN_VALUE, out[2]);
        assertEquals(Short.MAX_VALUE, out[3]);
        assertEquals(Short.MIN_VALUE, out[4]);
        assertEquals(16384, out[5]);
        assertEquals(-16383, out[6]);
        assertEquals(0, out[7]);
        assertEquals(0, NanogramOnDevice.floatToPcm16(new float[0], 0).length);
    }

    @Test
    public void featureSwitchIsOffOnTheJvm() {
        assertFalse(NanogramFeatures.onDeviceTranscription());
    }
}
