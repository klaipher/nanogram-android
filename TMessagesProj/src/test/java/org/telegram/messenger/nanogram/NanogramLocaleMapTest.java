package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Locale;

public class NanogramLocaleMapTest {

    @Test
    public void languageOnly() {
        assertEquals(Arrays.asList("de"), NanogramLocaleMap.candidateKeys(Locale.GERMAN));
    }

    @Test
    public void languageAndRegionTriesRegionFirst() {
        assertEquals(Arrays.asList("pt_br", "pt"), NanogramLocaleMap.candidateKeys("pt", "BR", ""));
    }

    @Test
    public void scriptVariants() {
        assertEquals(Arrays.asList("zh_hans_cn", "zh_hans", "zh_cn", "zh"), NanogramLocaleMap.candidateKeys("zh", "CN", "Hans"));
        assertEquals(Arrays.asList("zh_hant", "zh"), NanogramLocaleMap.candidateKeys("zh", "", "Hant"));
    }

    @Test
    public void emptyFallsBackToEnglish() {
        assertEquals(Arrays.asList("en"), NanogramLocaleMap.candidateKeys(null, null, null));
        assertEquals(Arrays.asList("en"), NanogramLocaleMap.candidateKeys((Locale) null));
    }
}
