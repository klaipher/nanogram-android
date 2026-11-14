package org.telegram.messenger.nanogram;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Maps a system locale to the language pack keys to try, most specific first. */
public final class NanogramLocaleMap {

    private NanogramLocaleMap() {
    }

    public static List<String> candidateKeys(String language, String country, String script) {
        final List<String> keys = new ArrayList<>(3);
        if (language == null || language.isEmpty()) {
            keys.add("en");
            return keys;
        }
        final String lang = language.toLowerCase(Locale.ROOT);
        final String region = country == null ? "" : country.toLowerCase(Locale.ROOT);
        final String scr = script == null ? "" : script.toLowerCase(Locale.ROOT);
        if (!scr.isEmpty() && !region.isEmpty()) {
            keys.add(lang + "_" + scr + "_" + region);
        }
        if (!scr.isEmpty()) {
            keys.add(lang + "_" + scr);
        }
        if (!region.isEmpty()) {
            keys.add(lang + "_" + region);
        }
        keys.add(lang);
        return keys;
    }

    public static List<String> candidateKeys(Locale locale) {
        return candidateKeys(locale == null ? null : locale.getLanguage(), locale == null ? null : locale.getCountry(), locale == null ? null : locale.getScript());
    }
}
