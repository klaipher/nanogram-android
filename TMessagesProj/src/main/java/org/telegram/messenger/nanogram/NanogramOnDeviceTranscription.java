package org.telegram.messenger.nanogram;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Voice message transcription with the phone's own speech recognizer (API 33+): the downloaded voice file is decoded
 * to PCM and fed through EXTRA_AUDIO_SOURCE, so nothing is sent to Telegram and the microphone is not used.
 */
public final class NanogramOnDeviceTranscription {

    private NanogramOnDeviceTranscription() {
    }

    public static boolean isSupported(Context context) {
        return NanogramOnDevice.transcriptionSupported(Build.VERSION.SDK_INT, available(context));
    }

    private static boolean available(Context context) {
        try {
            return Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isEnabled(Context context) {
        return NanogramFeatures.onDeviceTranscription() && isSupported(context);
    }

    // Asks the system to fetch the language pack when it is missing; the system shows its own download progress.
    @RequiresApi(33)
    public static void prepare(Context context, String tag, Utilities.Callback<Boolean> done) {
        try {
            final SpeechRecognizer recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            final Intent intent = baseIntent(tag);
            recognizer.checkRecognitionSupport(intent, Runnable::run, new RecognitionSupportCallback() {
                @Override
                public void onSupportResult(RecognitionSupport support) {
                    final List<String> installed = support.getInstalledOnDeviceLanguages();
                    final boolean supported = containsTag(support.getSupportedOnDeviceLanguages(), tag) || containsTag(installed, tag);
                    if (supported && !containsTag(installed, tag)) {
                        recognizer.triggerModelDownload(intent);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        recognizer.destroy();
                        done.run(supported);
                    });
                }

                @Override
                public void onError(int error) {
                    AndroidUtilities.runOnUIThread(() -> {
                        recognizer.destroy();
                        done.run(false);
                    });
                }
            });
        } catch (Throwable t) {
            FileLog.e("[ondevice] prepare failed", t);
            done.run(false);
        }
    }

    private static boolean containsTag(List<String> tags, String tag) {
        if (tags == null || tag == null) {
            return false;
        }
        for (String candidate : tags) {
            if (candidate.equalsIgnoreCase(tag) || candidate.toLowerCase().startsWith(tag.toLowerCase() + "-") || tag.toLowerCase().startsWith(candidate.toLowerCase() + "-")) {
                return true;
            }
        }
        return false;
    }

    private static Intent baseIntent(String tag) {
        final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        if (tag != null) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag);
        }
        return intent;
    }

    // done runs on the UI thread: the text, or null when the recognizer could not do it (the caller falls back to the server).
    @RequiresApi(33)
    public static void transcribe(Context context, File voice, String tag, Utilities.Callback<String> done) {
        final AtomicBoolean finished = new AtomicBoolean();
        final Utilities.Callback<String> finish = text -> {
            if (finished.compareAndSet(false, true)) {
                AndroidUtilities.runOnUIThread(() -> done.run(text));
            }
        };
        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            FileLog.e("[ondevice] pipe failed", e);
            finish.run(null);
            return;
        }
        final Thread decoder = new Thread(() -> {
            try (ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                decode(voice, out, (rate, channels) -> AndroidUtilities.runOnUIThread(() -> startRecognizer(context, tag, pipe[0], rate, channels, finish)));
            } catch (Throwable t) {
                FileLog.e("[ondevice] decode failed", t);
                try {
                    pipe[0].close();
                } catch (IOException ignore) {
                }
                finish.run(null);
            }
        }, "ngOnDeviceDecode");
        decoder.start();
    }

    @RequiresApi(33)
    private static void startRecognizer(Context context, String tag, ParcelFileDescriptor source, int rate, int channels, Utilities.Callback<String> finish) {
        try {
            final SpeechRecognizer recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onResults(Bundle results) {
                    final ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    recognizer.destroy();
                    finish.run(list == null || list.isEmpty() ? "" : list.get(0));
                }

                @Override
                public void onError(int error) {
                    recognizer.destroy();
                    // No match is a real answer for silence; anything else goes back to the server.
                    finish.run(error == SpeechRecognizer.ERROR_NO_MATCH ? "" : null);
                }

                @Override
                public void onReadyForSpeech(Bundle params) {
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });
            final Intent intent = baseIntent(tag);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, channels);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, rate);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            recognizer.startListening(intent);
        } catch (Throwable t) {
            FileLog.e("[ondevice] recognizer failed", t);
            finish.run(null);
        }
    }

    private interface FormatReady {
        void run(int rate, int channels);
    }

    // Voice messages are Ogg/Opus; the platform decoder turns them into 16-bit PCM.
    private static void decode(File file, java.io.OutputStream out, FormatReady ready) throws IOException {
        final MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(file.getAbsolutePath());
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); ++i) {
                final MediaFormat candidate = extractor.getTrackFormat(i);
                final String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    format = candidate;
                    break;
                }
            }
            if (format == null) {
                throw new IOException("no audio track");
            }
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false, announced = false;
            while (!outputDone) {
                if (!inputDone) {
                    final int inIndex = codec.dequeueInputBuffer(10_000);
                    if (inIndex >= 0) {
                        final ByteBuffer buffer = codec.getInputBuffer(inIndex);
                        final int size = extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                final int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    announced = announce(codec.getOutputFormat(), ready, announced);
                } else if (outIndex >= 0) {
                    if (!announced) {
                        announced = announce(codec.getOutputFormat(), ready, false);
                    }
                    final ByteBuffer buffer = codec.getOutputBuffer(outIndex);
                    if (info.size > 0 && buffer != null) {
                        final byte[] chunk = new byte[info.size];
                        buffer.position(info.offset);
                        buffer.get(chunk);
                        out.write(chunk);
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                }
            }
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Throwable ignore) {
                }
                codec.release();
            }
            extractor.release();
        }
    }

    private static boolean announce(MediaFormat format, FormatReady ready, boolean already) {
        if (already) {
            return true;
        }
        final int rate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 48000;
        final int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
        ready.run(rate, channels);
        return true;
    }
}
