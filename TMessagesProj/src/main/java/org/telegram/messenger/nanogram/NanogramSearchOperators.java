package org.telegram.messenger.nanogram;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Search operators for in-chat search (shared grammar: ios-integration docs/specs/search-operators.md): from:, before:, after:, date:, type:.
 * Pure parser; the caller maps the result onto messages.search (from_id, min_date, max_date, filter).
 */
public final class NanogramSearchOperators {

    public enum Type {
        PHOTO, VIDEO, FILE, LINK, VOICE, MUSIC, GIF, ROUND, POLL, PINNED
    }

    public static final class Result {
        public final String text;
        public final String textWithFrom;
        public final String from;
        public final int minDate;
        public final int maxDate;
        public final Type type;

        Result(String text, String textWithFrom, String from, int minDate, int maxDate, Type type) {
            this.text = text;
            this.textWithFrom = textWithFrom;
            this.from = from;
            this.minDate = minDate;
            this.maxDate = maxDate;
            this.type = type;
        }

        public boolean hasOperators() {
            return from != null || minDate != 0 || maxDate != 0 || type != null;
        }
    }

    private NanogramSearchOperators() {
    }

    public static Result parse(String query, TimeZone zone, long nowMillis) {
        if (query == null) {
            return new Result("", "", null, 0, 0, null);
        }
        final List<String> rest = new ArrayList<>();
        final List<String> restWithFrom = new ArrayList<>();
        String from = null;
        int afterMin = 0;
        int beforeMax = 0;
        int dateMin = 0;
        int dateMax = 0;
        Type type = null;
        for (String token : tokenize(query)) {
            final int colon = token.indexOf(':');
            if (colon <= 0 || colon == token.length() - 1) {
                rest.add(token);
                restWithFrom.add(token);
                continue;
            }
            final String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1);
            if (value.indexOf('"') >= 0) {
                if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"' || value.indexOf('"', 1) != value.length() - 1) {
                    rest.add(token);
                    restWithFrom.add(token);
                    continue;
                }
                value = value.substring(1, value.length() - 1);
            }
            boolean used = false;
            switch (key) {
                case "from": {
                    final String name = value.startsWith("@") ? value.substring(1) : value;
                    if (!name.isEmpty()) {
                        from = name;
                        used = true;
                        restWithFrom.add(token);
                    }
                    break;
                }
                case "before": {
                    final long[] span = span(value, zone, nowMillis);
                    if (span != null) {
                        beforeMax = seconds(span[0]);
                        used = true;
                    }
                    break;
                }
                case "after": {
                    final long[] span = span(value, zone, nowMillis);
                    if (span != null) {
                        afterMin = seconds(span[1]);
                        used = true;
                    }
                    break;
                }
                case "date": {
                    final long[] span = span(value, zone, nowMillis);
                    if (span != null) {
                        dateMin = seconds(span[0]);
                        dateMax = seconds(span[1]);
                        used = true;
                    }
                    break;
                }
                case "type": {
                    final Type parsed = typeOf(value);
                    if (parsed != null) {
                        type = parsed;
                        used = true;
                    }
                    break;
                }
                default:
                    break;
            }
            if (!used) {
                rest.add(token);
                restWithFrom.add(token);
            }
        }
        final int minDate = Math.max(afterMin, dateMin);
        final int maxDate = beforeMax == 0 ? dateMax : dateMax == 0 ? beforeMax : Math.min(beforeMax, dateMax);
        return new Result(String.join(" ", rest), String.join(" ", restWithFrom), from, minDate, maxDate, type);
    }

    static Type typeOf(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "photo":
            case "photos":
            case "image":
                return Type.PHOTO;
            case "video":
            case "videos":
                return Type.VIDEO;
            case "file":
            case "files":
            case "document":
            case "doc":
                return Type.FILE;
            case "link":
            case "links":
            case "url":
                return Type.LINK;
            case "voice":
                return Type.VOICE;
            case "music":
            case "audio":
                return Type.MUSIC;
            case "gif":
            case "gifs":
                return Type.GIF;
            case "round":
            case "roundvideo":
                return Type.ROUND;
            case "poll":
            case "polls":
                return Type.POLL;
            case "pinned":
                return Type.PINNED;
            default:
                return null;
        }
    }

    private static int seconds(long millis) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, millis / 1000));
    }

    // Returns {start, endExclusive} in millis of the day, month or year the value names, or null.
    private static long[] span(String value, TimeZone zone, long nowMillis) {
        final Calendar start = Calendar.getInstance(zone, Locale.ROOT);
        start.setTimeInMillis(nowMillis);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        int field = Calendar.DAY_OF_MONTH;
        final String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("today")) {
            // start is already today
        } else if (lower.equals("yesterday")) {
            start.add(Calendar.DAY_OF_MONTH, -1);
        } else {
            final String[] parts = value.split("-", -1);
            if (parts.length < 1 || parts.length > 3) {
                return null;
            }
            final int year = number(parts[0], 4);
            final int month = parts.length > 1 ? number(parts[1], 2) : 1;
            final int day = parts.length > 2 ? number(parts[2], 2) : 1;
            if (year < 1970 || year > 2100 || month < 1 || month > 12 || day < 1 || day > 31) {
                return null;
            }
            start.clear();
            start.setLenient(false);
            start.set(year, month - 1, day, 0, 0, 0);
            try {
                start.getTimeInMillis();
            } catch (IllegalArgumentException e) {
                return null;
            }
            field = parts.length == 1 ? Calendar.YEAR : parts.length == 2 ? Calendar.MONTH : Calendar.DAY_OF_MONTH;
        }
        final long from = start.getTimeInMillis();
        start.setLenient(true);
        start.add(field, 1);
        return new long[]{from, start.getTimeInMillis()};
    }

    private static int number(String s, int digits) {
        if (s.length() != digits) {
            return -1;
        }
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            n = n * 10 + (c - '0');
        }
        return n;
    }

    // Whitespace separated; a quote only opens a run when a closing quote follows, so an unclosed quote stays plain text.
    private static List<String> tokenize(String query) {
        final List<String> tokens = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < query.length(); i++) {
            final char c = query.charAt(i);
            if (c == '"') {
                if (quoted) {
                    quoted = false;
                } else if (query.indexOf('"', i + 1) >= 0) {
                    quoted = true;
                }
                current.append(c);
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
