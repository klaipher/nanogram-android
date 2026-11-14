package org.telegram.messenger.nanogram;

import java.util.ArrayList;
import java.util.Locale;

/** Pure helpers for the on-device transcription and translation switches. */
public final class NanogramOnDevice {

    // EXTRA_AUDIO_SOURCE (a stream the recognizer reads instead of the microphone) exists from API 33.
    public static final int MIN_SDK_TRANSCRIPTION = 33;

    private NanogramOnDevice() {
    }

    public static boolean transcriptionSupported(int sdk, boolean onDeviceRecognizerAvailable) {
        return sdk >= MIN_SDK_TRANSCRIPTION && onDeviceRecognizerAvailable;
    }

    // ML Kit translation models are keyed by bare language code ("pt", not "pt-BR").
    public static String translateCode(String telegramLang) {
        if (telegramLang == null) {
            return null;
        }
        String lang = telegramLang.trim().toLowerCase(Locale.ROOT);
        final int cut = indexOfSeparator(lang);
        if (cut >= 0) {
            lang = lang.substring(0, cut);
        }
        if (lang.isEmpty()) {
            return null;
        }
        switch (lang) {
            case "iw":
                return "he";
            case "in":
                return "id";
            case "ji":
                return "yi";
            default:
                return lang;
        }
    }

    // Language tag for the speech recognizer: "pt_br" -> "pt-BR", "zh-hans" -> "zh".
    public static String speechTag(String telegramLang) {
        final String base = translateCode(telegramLang);
        if (base == null) {
            return null;
        }
        final String lang = telegramLang.trim();
        final int cut = indexOfSeparator(lang);
        if (cut >= 0) {
            final String region = lang.substring(cut + 1);
            if (region.length() == 2 && Character.isLetter(region.charAt(0)) && Character.isLetter(region.charAt(1))) {
                return base + "-" + region.toUpperCase(Locale.ROOT);
            }
        }
        return base;
    }

    // Blank lines are kept as they are and never sent to the translator.
    public static ArrayList<Integer> translatableLines(String[] lines) {
        final ArrayList<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < lines.length; ++i) {
            if (!lines[i].trim().isEmpty()) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    public static String joinLines(String[] lines, String[] translated) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; ++i) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(translated[i] != null ? translated[i] : lines[i]);
        }
        return out.toString();
    }

    // One failed message sends the whole batch to the server, so no message is answered twice.
    public static boolean allTranslated(String[] results) {
        for (String result : results) {
            if (result == null) {
                return false;
            }
        }
        return true;
    }

    private static int indexOfSeparator(String s) {
        final int dash = s.indexOf('-');
        final int under = s.indexOf('_');
        if (dash < 0) {
            return under;
        }
        return under < 0 ? dash : Math.min(dash, under);
    }
}
