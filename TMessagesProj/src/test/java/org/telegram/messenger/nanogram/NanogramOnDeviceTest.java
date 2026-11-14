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
        assertArrayEquals(new String[]{"a"}, new String[]{"a"});
    }
}
