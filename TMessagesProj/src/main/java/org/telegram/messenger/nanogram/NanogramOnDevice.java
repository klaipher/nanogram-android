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

    // With the switch on, a non-Premium account never reaches messages.transcribeAudio, trial or not.
    public static boolean localOnly(boolean onDeviceEnabled, boolean premium) {
        return onDeviceEnabled && !premium;
    }

    public static boolean mayCallServer(boolean onDeviceEnabled, boolean premium) {
        return !localOnly(onDeviceEnabled, premium);
    }

    // Server transcriptions always carry a non-zero id; the phone's own result is stored with id 0.
    public static boolean isLocalResult(long transcriptionId, boolean isFinal) {
        return isFinal && transcriptionId == 0;
    }

    public static short[] floatToPcm16(float[] in, int n) {
        final short[] out = new short[n];
        for (int i = 0; i < n; ++i) {
            final float v = in[i];
            if (v != v) {
                out[i] = 0;
            } else if (v >= 1f) {
                out[i] = Short.MAX_VALUE;
            } else if (v <= -1f) {
                out[i] = Short.MIN_VALUE;
            } else {
                out[i] = (short) Math.round(v * 32767f);
            }
        }
        return out;
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

    // The ML Kit "zh" model is Simplified only, so a Traditional Chinese target goes to the server.
    public static String translateTargetCode(String telegramLang) {
        if (telegramLang != null) {
            final String lang = telegramLang.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            if (lang.startsWith("zh-hant") || lang.equals("zh-tw") || lang.equals("zh-hk") || lang.equals("zh-mo")) {
                return null;
            }
        }
        return translateCode(telegramLang);
    }

    // Language-id answers "und" (or fails) for emoji, links and numbers: there is nothing to translate, not a failure.
    public static boolean noLanguage(String detected) {
        return detected == null || detected.isEmpty() || "und".equals(detected);
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
            if (!lines[i].trim().isEmpty() && !isLinkOnly(lines[i])) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    // The translator would rewrite a bare URL.
    public static boolean isLinkOnly(String line) {
        final String t = line.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.indexOf(' ') >= 0 || t.indexOf('\t') >= 0) {
            return false;
        }
        return t.startsWith("http://") || t.startsWith("https://") || t.startsWith("www.") || t.startsWith("tg://");
    }

    // Mono 16 kHz 16-bit little-endian PCM from any rate and channel count; keeps its state between chunks.
    public static final class Pcm16kConverter {
        public static final int TARGET_RATE = 16000;
        private final int channels;
        private final double step;
        private double acc;
        private double filled;
        private int partialBytes;
        private final byte[] partial = new byte[2];
        private final int[] frame;
        private int frameFill;

        public Pcm16kConverter(int rate, int channels) {
            this.channels = Math.max(1, channels);
            this.step = (double) rate / TARGET_RATE;
            this.frame = new int[this.channels];
        }

        public byte[] convert(byte[] in, int length) {
            final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(length / 2 / Math.max(1, (int) step) + 4);
            for (int i = 0; i < length; ++i) {
                partial[partialBytes++] = in[i];
                if (partialBytes < 2) {
                    continue;
                }
                partialBytes = 0;
                frame[frameFill++] = (short) ((partial[0] & 0xff) | (partial[1] << 8));
                if (frameFill < channels) {
                    continue;
                }
                frameFill = 0;
                long sum = 0;
                for (int c = 0; c < channels; ++c) {
                    sum += frame[c];
                }
                feed((double) sum / channels, out);
            }
            return out.toByteArray();
        }

        private void feed(double sample, java.io.ByteArrayOutputStream out) {
            double remaining = 1.0;
            while (remaining > 1e-9) {
                final double take = Math.min(remaining, step - filled);
                acc += sample * take;
                filled += take;
                remaining -= take;
                if (filled >= step - 1e-9) {
                    final long v = Math.round(acc / step);
                    final int clamped = (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
                    out.write(clamped & 0xff);
                    out.write((clamped >> 8) & 0xff);
                    acc = 0;
                    filled = 0;
                }
            }
        }
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
