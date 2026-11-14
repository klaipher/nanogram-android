package org.telegram.messenger.nanogram;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Message translation with ML Kit models on the phone; null results make the caller use Telegram's server translation. */
public final class NanogramOnDeviceTranslation {

    public interface StatusListener {
        void onStatus(String language, int state);
    }

    public static final int STATE_DOWNLOADING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_FAILED = 3;

    private static volatile int lastState;
    private static volatile String lastLanguage;
    private static volatile StatusListener listener;

    private NanogramOnDeviceTranslation() {
    }

    public static void setStatusListener(StatusListener l) {
        listener = l;
    }

    public static int lastState() {
        return lastState;
    }

    public static String lastLanguage() {
        return lastLanguage;
    }

    public static boolean isEnabled() {
        return NanogramFeatures.onDeviceTranslation();
    }

    private static DownloadConditions conditions() {
        final DownloadConditions.Builder builder = new DownloadConditions.Builder();
        if (NanogramFeatures.modelsWifiOnly()) {
            builder.requireWifi();
        }
        return builder.build();
    }

    // The whole on-device batch gets this long (model download included) before the rest goes to the server.
    private static final long BATCH_TIMEOUT_MS = 30_000;

    private static String mlCode(String telegramLang) {
        final String code = NanogramOnDevice.translateCode(telegramLang);
        return code == null ? null : TranslateLanguage.fromLanguageTag(code);
    }

    private static String mlTargetCode(String telegramLang) {
        final String code = NanogramOnDevice.translateTargetCode(telegramLang);
        return code == null ? null : TranslateLanguage.fromLanguageTag(code);
    }

    private static void report(String language, int state) {
        lastLanguage = language;
        lastState = state;
        final StatusListener l = listener;
        if (l != null) {
            AndroidUtilities.runOnUIThread(() -> l.onStatus(language, state));
        }
    }

    // Called when the switch is turned on: the user's own language is fetched now, others when a message needs them.
    public static void prefetch(String telegramLang) {
        final String code = mlCode(telegramLang);
        if (code == null) {
            return;
        }
        report(code, STATE_DOWNLOADING);
        RemoteModelManager.getInstance()
            .download(new TranslateRemoteModel.Builder(code).build(), conditions())
            .addOnSuccessListener(v -> report(code, STATE_READY))
            .addOnFailureListener(e -> {
                FileLog.e("[ondevice] model download failed", e);
                report(code, STATE_FAILED);
            });
    }

    // done runs on the UI thread once, with one entry per text; a null entry means that text could not be translated here.
    public static void translate(ArrayList<String> texts, String toTelegramLang, Utilities.Callback<String[]> done) {
        final String[] results = new String[texts.size()];
        final String target = mlTargetCode(toTelegramLang);
        if (target == null || texts.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> done.run(results));
            return;
        }
        final AtomicBoolean answered = new AtomicBoolean();
        final Runnable timeout = () -> {
            if (answered.compareAndSet(false, true)) {
                FileLog.e("[ondevice] translation timed out");
                done.run(results.clone());
            }
        };
        AndroidUtilities.runOnUIThread(timeout, BATCH_TIMEOUT_MS);
        translateFrom(texts, 0, target, results, answered, () -> {
            if (answered.compareAndSet(false, true)) {
                AndroidUtilities.cancelRunOnUIThread(timeout);
                done.run(results);
            }
        });
    }

    private static void translateFrom(ArrayList<String> texts, int index, String target, String[] results, AtomicBoolean answered, Runnable finished) {
        if (index >= texts.size() || answered.get()) {
            AndroidUtilities.runOnUIThread(finished);
            return;
        }
        final Runnable next = () -> translateFrom(texts, index + 1, target, results, answered, finished);
        final String text = texts.get(index);
        LanguageDetector.detectLanguage(text, detected -> {
            if (NanogramOnDevice.noLanguage(detected)) {
                results[index] = text;
                next.run();
                return;
            }
            final String source = mlCode(detected);
            if (source == null) {
                next.run();
                return;
            }
            if (source.equals(target)) {
                results[index] = text;
                next.run();
                return;
            }
            translateText(text, source, target, translated -> {
                results[index] = translated;
                next.run();
            });
        }, e -> {
            results[index] = text;
            next.run();
        });
    }

    // With Wi-Fi only on and no Wi-Fi, a missing model cannot be fetched: fail now instead of waiting for Wi-Fi.
    private static void checkModelsReachable(String source, String target, Utilities.Callback<Boolean> done) {
        if (!NanogramFeatures.modelsWifiOnly() || ApplicationLoader.isConnectedToWiFi()) {
            done.run(true);
            return;
        }
        final ArrayList<String> codes = new ArrayList<>();
        codes.add(source);
        codes.add(target);
        if (!TranslateLanguage.ENGLISH.equals(source) && !TranslateLanguage.ENGLISH.equals(target)) {
            codes.add(TranslateLanguage.ENGLISH);
        }
        checkDownloaded(codes, 0, done);
    }

    private static void checkDownloaded(ArrayList<String> codes, int at, Utilities.Callback<Boolean> done) {
        if (at >= codes.size()) {
            done.run(true);
            return;
        }
        RemoteModelManager.getInstance()
            .isModelDownloaded(new TranslateRemoteModel.Builder(codes.get(at)).build())
            .addOnSuccessListener(downloaded -> {
                if (downloaded) {
                    checkDownloaded(codes, at + 1, done);
                } else {
                    done.run(false);
                }
            })
            .addOnFailureListener(e -> done.run(false));
    }

    private static void translateText(String text, String source, String target, Utilities.Callback<String> done) {
        checkModelsReachable(source, target, reachable -> {
            if (!reachable) {
                report(source, STATE_FAILED);
                done.run(null);
                return;
            }
            downloadAndTranslate(text, source, target, done);
        });
    }

    private static void downloadAndTranslate(String text, String source, String target, Utilities.Callback<String> done) {
        final Translator translator = Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build());
        report(source, STATE_DOWNLOADING);
        translator.downloadModelIfNeeded(conditions())
            .addOnSuccessListener(v -> {
                report(source, STATE_READY);
                final String[] lines = text.split("\n", -1);
                final ArrayList<Integer> indexes = NanogramOnDevice.translatableLines(lines);
                final String[] translated = new String[lines.length];
                translateLines(translator, lines, indexes, 0, translated, joined -> {
                    translator.close();
                    done.run(joined);
                });
            })
            .addOnFailureListener(e -> {
                FileLog.e("[ondevice] translation model unavailable", e);
                report(source, STATE_FAILED);
                translator.close();
                done.run(null);
            });
    }

    private static void translateLines(Translator translator, String[] lines, ArrayList<Integer> indexes, int at, String[] translated, Utilities.Callback<String> done) {
        if (at >= indexes.size()) {
            done.run(NanogramOnDevice.joinLines(lines, translated));
            return;
        }
        final int line = indexes.get(at);
        translator.translate(lines[line])
            .addOnSuccessListener(result -> {
                translated[line] = result;
                translateLines(translator, lines, indexes, at + 1, translated, done);
            })
            .addOnFailureListener(e -> {
                FileLog.e("[ondevice] translate failed", e);
                done.run(null);
            });
    }
}
