package org.telegram.messenger.nanogram;

import java.util.regex.Pattern;

/** The same masking as Send Report on iOS and macOS: e-mail addresses first, then phone numbers starting with +. */
public final class NanogramReportMasking {

    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("\\+\\d[\\d \\-().]{6,}\\d");

    private NanogramReportMasking() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return PHONE.matcher(EMAIL.matcher(text).replaceAll("[email]")).replaceAll("[phone]");
    }

    public static boolean isMaskedTextFile(String name) {
        return name.endsWith(".txt") || name.endsWith(".log");
    }
}
