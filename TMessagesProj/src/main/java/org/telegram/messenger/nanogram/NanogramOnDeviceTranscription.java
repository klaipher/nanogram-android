package org.telegram.messenger.nanogram;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Voice message transcription with the phone's own speech recognizer (API 33+): the downloaded voice file is decoded
 * to PCM and fed through EXTRA_AUDIO_SOURCE, so nothing is sent to Telegram and the microphone is not used.
 */
public final class NanogramOnDeviceTranscription {

    public static final int FAIL_GENERIC = 1;
    public static final int FAIL_NO_MIC = 2;
    public static final int FAIL_NO_LANGUAGE = 3;

    private static volatile Boolean supportedCache;
    // One recognizer at a time: a second one would answer ERROR_RECOGNIZER_BUSY. UI thread only.
    private static final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private static boolean busy;

    private NanogramOnDeviceTranscription() {
    }

    // The switch is read live; only the system query (isOnDeviceRecognitionAvailable) is cached, so list binds stay cheap.
    public static boolean isEnabledCached() {
        if (!NanogramFeatures.onDeviceTranscription()) {
            return false;
        }
        Boolean supported = supportedCache;
        if (supported == null) {
            final Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return false;
            }
            supported = isSupported(context);
            supportedCache = supported;
        }
        return supported;
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

    public static boolean hasMicrophonePermission(Context context) {
        return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    // done runs on the UI thread, once: (text, 0), or (null, FAIL_*) when the recognizer could not do it.
    @RequiresApi(33)
    public static void transcribe(Context context, File voice, String tag, long durationSeconds, Utilities.Callback2<String, Integer> done) {
        if (!hasMicrophonePermission(context)) {
            AndroidUtilities.runOnUIThread(() -> done.run(null, FAIL_NO_MIC));
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            queue.add(() -> run(context, voice, tag, durationSeconds, (text, failure) -> {
                busy = false;
                done.run(text, failure);
                next();
            }));
            next();
        });
    }

    private static void next() {
        if (busy) {
            return;
        }
        final Runnable job = queue.poll();
        if (job != null) {
            busy = true;
            job.run();
        }
    }

    @RequiresApi(33)
    private static void run(Context context, File voice, String tag, long durationSeconds, Utilities.Callback2<String, Integer> done) {
        final AtomicBoolean finished = new AtomicBoolean();
        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            FileLog.e("[ondevice] pipe failed", e);
            AndroidUtilities.runOnUIThread(() -> done.run(null, FAIL_GENERIC));
            return;
        }
        final SpeechRecognizer[] recognizer = new SpeechRecognizer[1];
        final Runnable[] timeout = new Runnable[1];
        // Closing our read end after any outcome lets the decoder thread get EPIPE and release the codec.
        final Utilities.Callback2<String, Integer> finish = (text, failure) -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (timeout[0] != null) {
                    AndroidUtilities.cancelRunOnUIThread(timeout[0]);
                }
                if (recognizer[0] != null) {
                    try {
                        recognizer[0].cancel();
                        recognizer[0].destroy();
                    } catch (Throwable ignore) {
                    }
                }
                try {
                    pipe[0].close();
                } catch (IOException ignore) {
                }
                done.run(text, failure);
            });
        };
        timeout[0] = () -> {
            FileLog.e("[ondevice] transcription timed out");
            finish.run(null, FAIL_GENERIC);
        };
        AndroidUtilities.runOnUIThread(timeout[0], (Math.max(1, durationSeconds) + 30) * 1000L);
        final Thread decoder = new Thread(() -> {
            try (ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                decode(voice, out, () -> AndroidUtilities.runOnUIThread(() -> {
                    if (!finished.get()) {
                        startRecognizer(context, tag, pipe[0], recognizer, finish);
                    }
                }));
            } catch (Throwable t) {
                FileLog.e("[ondevice] decode failed", t);
                finish.run(null, FAIL_GENERIC);
            }
        }, "ngOnDeviceDecode");
        decoder.start();
    }

    @RequiresApi(33)
    private static void startRecognizer(Context context, String tag, ParcelFileDescriptor source, SpeechRecognizer[] holder, Utilities.Callback2<String, Integer> finish) {
        try {
            final SpeechRecognizer recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            holder[0] = recognizer;
            final StringBuilder text = new StringBuilder();
            recognizer.setRecognitionListener(new RecognitionListener() {
                private void add(Bundle results) {
                    final ArrayList<String> list = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty() && !list.get(0).isEmpty()) {
                        if (text.length() > 0) {
                            text.append(' ');
                        }
                        text.append(list.get(0));
                    }
                }

                @Override
                public void onSegmentResults(Bundle segmentResults) {
                    add(segmentResults);
                }

                @Override
                public void onEndOfSegmentedSession() {
                    finish.run(text.toString(), 0);
                }

                @Override
                public void onResults(Bundle results) {
                    add(results);
                    finish.run(text.toString(), 0);
                }

                @Override
                public void onError(int error) {
                    // No match is a real answer for silence.
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        finish.run(text.toString(), 0);
                    } else if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                        finish.run(null, FAIL_NO_LANGUAGE);
                    } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        finish.run(null, FAIL_NO_MIC);
                    } else {
                        finish.run(null, FAIL_GENERIC);
                    }
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
            // Without this the session ends at the first pause; with it, only when the audio is closed.
            intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, NanogramOnDevice.Pcm16kConverter.TARGET_RATE);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            recognizer.startListening(intent);
        } catch (Throwable t) {
            FileLog.e("[ondevice] recognizer failed", t);
            finish.run(null, FAIL_GENERIC);
        }
    }

    private interface FormatReady {
        void run();
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
            boolean inputDone = false, outputDone = false;
            NanogramOnDevice.Pcm16kConverter converter = null;
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
                    converter = converterFor(codec.getOutputFormat(), converter, ready);
                } else if (outIndex >= 0) {
                    final MediaFormat outFormat = codec.getOutputFormat();
                    converter = converterFor(outFormat, converter, ready);
                    final ByteBuffer buffer = codec.getOutputBuffer(outIndex);
                    if (info.size > 0 && buffer != null) {
                        byte[] chunk = new byte[info.size];
                        buffer.position(info.offset);
                        buffer.get(chunk);
                        if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) && outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT) {
                            chunk = floatChunkToPcm16(chunk);
                        }
                        out.write(converter.convert(chunk, chunk.length));
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

    private static byte[] floatChunkToPcm16(byte[] chunk) {
        final int count = chunk.length / 4;
        final float[] floats = new float[count];
        ByteBuffer.wrap(chunk, 0, count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().get(floats);
        final short[] shorts = NanogramOnDevice.floatToPcm16(floats, count);
        final byte[] bytes = new byte[count * 2];
        for (int i = 0; i < count; ++i) {
            bytes[2 * i] = (byte) (shorts[i] & 0xff);
            bytes[2 * i + 1] = (byte) ((shorts[i] >> 8) & 0xff);
        }
        return bytes;
    }

    // The recognizer is told 16 kHz mono (its documented default); the decoder's own format is converted here.
    private static NanogramOnDevice.Pcm16kConverter converterFor(MediaFormat format, NanogramOnDevice.Pcm16kConverter existing, FormatReady ready) {
        if (existing != null) {
            return existing;
        }
        final int rate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 48000;
        final int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
        ready.run();
        return new NanogramOnDevice.Pcm16kConverter(rate, channels);
    }
}
