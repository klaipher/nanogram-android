package org.telegram.messenger;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** The routes of the latest recordings, kept for Send Report (text only: device types, rates, codec class). */
public final class AudioRouteReport {

    static final int MAX_LINES = 20;

    private static final ArrayDeque<String> LINES = new ArrayDeque<>();

    private AudioRouteReport() {
    }

    public static synchronized void add(String line) {
        add(System.currentTimeMillis(), line);
    }

    static synchronized void add(long timeMs, String line) {
        LINES.addLast(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(timeMs)) + "  " + line);
        while (LINES.size() > MAX_LINES) {
            LINES.removeFirst();
        }
    }

    static synchronized void clear() {
        LINES.clear();
    }

    public static synchronized String describe() {
        final StringBuilder out = new StringBuilder("Microphone route of the latest recordings in this app run (oldest first)\n");
        out.append("requested = the setting; routed = what the system reported; codec is a class guess from the device type and rates\n\n");
        if (LINES.isEmpty()) {
            out.append("no recording since the app started\n");
        }
        for (String line : LINES) {
            out.append(line).append('\n');
        }
        return out.toString();
    }
}
