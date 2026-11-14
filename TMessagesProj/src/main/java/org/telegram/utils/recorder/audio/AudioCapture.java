package org.telegram.utils.recorder.audio;

import org.telegram.messenger.AudioInputPreference;
import org.telegram.messenger.AudioRouteMonitor;
import org.telegram.messenger.AudioRouteSession;
import org.telegram.messenger.AudioRouteReport;
import org.telegram.messenger.nanogram.NanogramFeatures;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Captures microphone PCM and encodes it into one Ogg/Opus output.
 *
 * <p>Capture and encoding run on separate threads, so the microphone starts immediately while
 * the encoder is still re-encoding a retained prefix. Captured chunks queue up on the encoder
 * thread until it is ready.</p>
 */
final class AudioCapture {

    interface Callback {
        /** Called when the microphone is recording; the encoder may still be preparing. */
        void onStarted();

        /** Called with the raw microphone RMS amplitude at most every 32 ms. */
        void onProgress(long capturedSamples, double amplitude);

        /** Called when the output file has been flushed up to the given size. */
        void onOutputCommitted(long committedFileSize);

        void onStopped(long capturedSamples, long finalFileSize);

        void onError(@NonNull Exception error);
    }

    static final int SAMPLE_RATE = 48_000;
    static final int FRAME_SAMPLES = 960;
    static final int FRAME_BYTES = FRAME_SAMPLES * 2;

    private static final long OUTPUT_FLUSH_INTERVAL_MS = 100L;
    private static final long PROGRESS_INTERVAL_MS = 32L;
    private static final long EMPTY_READ_RETRY_MS = 5L;
    private static final long LEADING_SILENCE_MAX_SAMPLES = SAMPLE_RATE / 2;
    private static final int ENCODER_QUEUE_SOFT_CAP = 500;
    private static final byte[] ZERO_FRAME = new byte[FRAME_BYTES];

    private final File outputFile;
    private final File prefixFile;
    private final long prefixStartMs;
    private final long prefixEndMs;
    private final AudioRecordingSession.NoiseSuppression noiseSuppression;
    private final AudioRecorderDiagnostics diagnostics;
    private final Callback callback;
    private final HandlerThread captureThread;
    private final Handler captureHandler;
    private final HandlerThread encoderThread;
    private final Handler encoderHandler;
    private final ConcurrentLinkedQueue<ByteBuffer> chunkPool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedChunks = new AtomicInteger();

    private volatile boolean stopRequested;
    private volatile boolean captureStopped;

    // Capture thread.
    private AudioRecord audioRecord;
    private AudioRouteSession routeSession;
    private AudioRouteMonitor routeMonitor;
    private LeadingSilenceTrimmer silenceTrimmer = new LeadingSilenceTrimmer(LEADING_SILENCE_MAX_SAMPLES);
    private int audioRecordBufferSize;
    private int consecutiveEmptyReads;
    private int errorReopens;
    private long lastReadMs;
    private final AudioInputPreference.InputReopener inputReopener = this::requestReopenInput;
    private long capturedSamples;
    private long lastProgressMs;

    // Encoder thread.
    private final ByteBuffer frameBuffer = ByteBuffer.allocateDirect(FRAME_BYTES)
            .order(ByteOrder.nativeOrder());
    private AudioRecorderNative encoder;
    private AudioNoiseSuppressor noiseSuppressor;
    private Exception encoderFailure;
    private boolean encodingFinished;
    private long lastFlushMs;
    private int maximumQueuedChunks;
    private boolean queueCapReported;
    private long noiseProcessingCount;
    private long noiseProcessingTotalNs;
    private long noiseProcessingMaxNs;
    private AudioVoiceEnhancer enhancer;
    private int enhancerProfile = AudioVoiceEnhancer.PROFILE_NONE;
    private boolean routeKnown;
    private int routeType = -1;
    private int[] routeRates = new int[0];
    private int reportedClass = AudioVoiceEnhancer.CLASS_UNKNOWN;
    private long enhancedFrames;
    private long inputSamples;
    private int inputPeak;
    private long inputClipped;
    private long inputSum;
    private long lastEnhancerCheckFrames;
    private AudioRecord.OnRoutingChangedListener routeListener;
    // Capture thread: the routed device can still be unknown right after startRecording, so it is read again after the first data.
    private boolean routePostedAfterData;
    // Encoder thread: the backlog while a resumed recording re-encodes its prefix is not a CPU fault.
    private boolean backlogDrained;

    /**
     * @param prefixFile    previous output whose range is re-encoded before the new samples
     * @param prefixStartMs start of the retained prefix range
     * @param prefixEndMs   end of the retained prefix range, or a negative value for the whole file
     */
    AudioCapture(
            @NonNull File outputFile,
            @Nullable File prefixFile,
            long prefixStartMs,
            long prefixEndMs,
            @NonNull AudioRecordingSession.NoiseSuppression noiseSuppression,
            @NonNull AudioRecorderDiagnostics diagnostics,
            @NonNull Callback callback
    ) {
        this.outputFile = outputFile;
        this.prefixFile = prefixFile;
        this.prefixStartMs = prefixStartMs;
        this.prefixEndMs = prefixEndMs;
        this.noiseSuppression = noiseSuppression;
        this.diagnostics = diagnostics;
        this.callback = callback;
        captureThread = new HandlerThread("AudioRecorderCapture", Process.THREAD_PRIORITY_AUDIO);
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
        encoderThread = new HandlerThread("AudioRecorderEncoder", Process.THREAD_PRIORITY_AUDIO);
        encoderThread.start();
        encoderHandler = new Handler(encoderThread.getLooper());
    }

    void start() {
        encoderHandler.post(this::startEncoder);
        captureHandler.post(this::startCapture);
    }

    void stop() {
        stopRequested = true;
        if (!captureStopped) {
            captureHandler.postAtFrontOfQueue(() -> stopCapture(null));
        }
    }

    private void startCapture() {
        try {
            if (stopRequested) {
                stopCapture(null);
                return;
            }
            int minimumBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            if (minimumBufferSize <= 0) {
                minimumBufferSize = FRAME_BYTES * 4;
            }
            audioRecordBufferSize = Math.max(minimumBufferSize * 2, FRAME_BYTES * 4);
            diagnostics.log("AudioRecord buffer: minBufferBytes=" + minimumBufferSize
                    + ", bufferBytes=" + audioRecordBufferSize);
            openInput("start");
            if (stopRequested) {
                stopCapture(null);
                return;
            }
            lastReadMs = SystemClock.elapsedRealtime();
            AudioInputPreference.registerReopener(inputReopener);
            lastProgressMs = SystemClock.elapsedRealtime();
            callback.onStarted();
            captureHandler.post(this::readOnce);
        } catch (Throwable error) {
            stopCapture(asException(error));
        }
    }

    /** Capture thread. Waits for the route (never on the main thread), then opens and starts the AudioRecord. */
    private void openInput(String reason) {
        AudioRouteSession session = AudioInputPreference.acquireRoute();
        routeSession = session;
        if (stopRequested) {
            // stop() came while the route was acquired: do not open the microphone for nothing.
            session.release();
            routeSession = null;
            return;
        }
        routePostedAfterData = false;
        AudioRecord record = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioRecordBufferSize
        );
        try {
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("Unable to initialize AudioRecord");
            }
            AudioInputPreference.applyTo(record);
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("Unable to start AudioRecord");
            }
        } catch (Throwable error) {
            AudioInputPreference.unregister(record);
            record.release();
            session.release();
            routeSession = null;
            throw error;
        }
        audioRecord = record;
        routeListener = changed -> postRoute(changed);
        record.addOnRoutingChangedListener(routeListener, captureHandler);
        postRoute(record);
        routeMonitor = AudioRouteMonitor.start("voice-v2", record, session, SAMPLE_RATE, diagnostics::log);
        diagnostics.log("AudioRecord started (" + reason + "): sessionId=" + record.getAudioSessionId()
                + ", routeAcquire=" + session.outcome() + " in " + session.acquireMs() + " ms");
    }

    /** Capture thread. Stops the monitor and the record and releases the route; returns a failure of stop(). */
    @Nullable
    private Throwable closeInput() {
        Throwable failure = null;
        AudioRouteMonitor monitor = routeMonitor;
        routeMonitor = null;
        if (monitor != null) monitor.stop();
        AudioRecord record = audioRecord;
        audioRecord = null;
        if (record != null) {
            if (routeListener != null) {
                try {
                    record.removeOnRoutingChangedListener(routeListener);
                } catch (Throwable ignored) {
                }
                routeListener = null;
            }
            try {
                if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop();
                }
            } catch (Throwable error) {
                failure = error;
            } finally {
                AudioInputPreference.unregister(record);
                record.release();
            }
        }
        AudioRouteSession session = routeSession;
        routeSession = null;
        if (session != null) session.release();
        return failure;
    }

    /** Any thread: moves capture to the input resolved now (read errors and the microphone switch). */
    boolean requestReopenInput() {
        if (captureStopped || stopRequested) return false;
        captureHandler.post(() -> {
            if (captureStopped || stopRequested) return;
            try {
                reopenInput("requested");
            } catch (Throwable error) {
                stopCapture(asException(error));
            }
        });
        return true;
    }

    /**
     * Capture thread. Keeps the encoder and the output, replaces the AudioRecord and its route and fills the time
     * the microphone was away with silence, so the duration stays right.
     */
    private void reopenInput(String reason) {
        diagnostics.log("reopening input: " + reason);
        closeInput();
        openInput("reopen: " + reason);
        long nowMs = SystemClock.elapsedRealtime();
        int silenceBytes = AudioReadPolicy.silenceBytes(nowMs - lastReadMs, SAMPLE_RATE);
        queueSilence(silenceBytes);
        diagnostics.log("input reopened: gapMs=" + (nowMs - lastReadMs) + ", silenceBytes=" + silenceBytes);
        lastReadMs = nowMs;
        consecutiveEmptyReads = 0;
    }

    private void queueSilence(int bytes) {
        while (bytes > 0) {
            final ByteBuffer chunk = obtainChunk();
            int count = Math.min(bytes, FRAME_BYTES);
            chunk.put(ZERO_FRAME, 0, count);
            chunk.flip();
            capturedSamples += count / 2;
            queuedChunks.incrementAndGet();
            encoderHandler.post(() -> encodeChunk(chunk));
            bytes -= count;
        }
    }

    private void readOnce() {
        if (captureStopped) return;
        if (stopRequested) {
            stopCapture(null);
            return;
        }
        ByteBuffer chunk = obtainChunk();
        boolean queued = false;
        try {
            int bytesRead = audioRecord.read(chunk, chunk.capacity());
            AudioReadPolicy.Action action = AudioReadPolicy.next(bytesRead, consecutiveEmptyReads, errorReopens);
            if (action == AudioReadPolicy.Action.FAIL) {
                throw new IllegalStateException("AudioRecord read failed: " + bytesRead);
            }
            if (action != AudioReadPolicy.Action.DATA) {
                recycleChunk(chunk);
                queued = true;
                if (action == AudioReadPolicy.Action.RETRY) {
                    consecutiveEmptyReads++;
                    captureHandler.postDelayed(this::readOnce, EMPTY_READ_RETRY_MS);
                } else {
                    errorReopens++;
                    reopenInput("read result " + bytesRead);
                    captureHandler.post(this::readOnce);
                }
                return;
            }
            consecutiveEmptyReads = 0;
            lastReadMs = SystemClock.elapsedRealtime();
            if (!routePostedAfterData) {
                routePostedAfterData = true;
                postRoute(audioRecord);
            }
            int drop = silenceTrimmer.leadingBytesToDrop(chunk, bytesRead);
            if (drop > 0) {
                if (drop >= bytesRead) {
                    recycleChunk(chunk);
                    queued = true;
                    captureHandler.post(this::readOnce);
                    return;
                }
                chunk.position(drop);
                chunk.limit(bytesRead);
                chunk.compact();
                chunk.flip();
                bytesRead -= drop;
            }
            chunk.limit(bytesRead);
            long sum = 0L;
            int sampleCount = bytesRead / 2;
            for (int offset = 0; offset + 1 < bytesRead; offset += 2) {
                int sample = chunk.getShort(offset);
                sum += (long) sample * sample;
            }
            capturedSamples += sampleCount;
            queuedChunks.incrementAndGet();
            encoderHandler.post(() -> encodeChunk(chunk));
            queued = true;

            long nowMs = SystemClock.elapsedRealtime();
            if (nowMs - lastProgressMs >= PROGRESS_INTERVAL_MS) {
                double amplitude = sampleCount == 0 ? 0d : Math.sqrt((double) sum / sampleCount);
                callback.onProgress(capturedSamples, amplitude);
                lastProgressMs = nowMs;
            }
            captureHandler.post(this::readOnce);
        } catch (Throwable error) {
            if (!queued) recycleChunk(chunk);
            stopCapture(stopRequested ? null : asException(error));
        }
    }

    private void stopCapture(@Nullable Exception failure) {
        if (captureStopped) return;
        captureStopped = true;
        stopRequested = true;
        if (failure != null) {
            diagnostics.error("capture failed", failure);
        }
        AudioInputPreference.unregisterReopener(inputReopener);
        Throwable closeFailure = closeInput();
        if (closeFailure != null && failure == null) failure = asException(closeFailure);
        if (silenceTrimmer.trimmedSamples() > 0) {
            diagnostics.log("leading digital silence trimmed: samples=" + silenceTrimmer.trimmedSamples()
                    + ", ms=" + samplesToMs(silenceTrimmer.trimmedSamples()));
        }
        long samples = capturedSamples;
        Exception captureFailure = failure;
        encoderHandler.post(() -> finishEncoding(samples, captureFailure));
        captureThread.quitSafely();
    }

    private void startEncoder() {
        try {
            long startedAtMs = SystemClock.elapsedRealtime();
            encoder = AudioRecorderNative.create(
                    outputFile,
                    SAMPLE_RATE,
                    prefixFile,
                    prefixStartMs,
                    prefixEndMs
            );
            if (encoder == null) {
                throw new IllegalStateException("Unable to initialize native Opus recorder");
            }
            if (noiseSuppression == AudioRecordingSession.NoiseSuppression.RNNOISE) {
                noiseSuppressor = AudioNoiseSuppressor.create();
                if (noiseSuppressor == null) {
                    diagnostics.log("RNNoise unavailable; recording unprocessed PCM");
                } else {
                    diagnostics.log("RNNoise started: sampleRate=" + SAMPLE_RATE
                            + ", captureFrameSamples=" + FRAME_SAMPLES);
                }
            }
            long initialFileSize = encoder.flush();
            diagnostics.log("Opus encoder started: prefix="
                    + (prefixFile == null ? "none" : prefixFile.getName()
                    + " " + prefixStartMs + ".." + prefixEndMs)
                    + ", initialBytes=" + initialFileSize
                    + ", queuedChunks=" + queuedChunks.get()
                    + ", elapsedMs=" + (SystemClock.elapsedRealtime() - startedAtMs));
            lastFlushMs = SystemClock.elapsedRealtime();
            if (initialFileSize > 0L) {
                callback.onOutputCommitted(initialFileSize);
            }
        } catch (Throwable error) {
            failEncoder(error);
        }
    }

    private void encodeChunk(@NonNull ByteBuffer chunk) {
        int queued = queuedChunks.getAndDecrement();
        try {
            if (encoderFailure != null || encodingFinished) return;
            maximumQueuedChunks = Math.max(maximumQueuedChunks, queued);
            if (queued > ENCODER_QUEUE_SOFT_CAP && !queueCapReported) {
                queueCapReported = true;
                diagnostics.log("encoder queue over " + ENCODER_QUEUE_SOFT_CAP + " chunks");
            }
            copyAndEncode(chunk);
            long nowMs = SystemClock.elapsedRealtime();
            if (nowMs - lastFlushMs >= OUTPUT_FLUSH_INTERVAL_MS) {
                long committedFileSize = encoder.flush();
                lastFlushMs = nowMs;
                if (committedFileSize >= 0L) {
                    callback.onOutputCommitted(committedFileSize);
                }
            }
        } catch (Throwable error) {
            failEncoder(error);
        } finally {
            recycleChunk(chunk);
        }
    }

    private void failEncoder(@NonNull Throwable error) {
        if (encoderFailure != null) return;
        diagnostics.error("encoding failed", error);
        encoderFailure = asException(error);
        stopRequested = true;
    }

    private void copyAndEncode(@NonNull ByteBuffer chunk) {
        chunk.position(0);
        while (chunk.hasRemaining()) {
            int byteCount = Math.min(chunk.remaining(), frameBuffer.remaining());
            int previousLimit = chunk.limit();
            chunk.limit(chunk.position() + byteCount);
            frameBuffer.put(chunk);
            chunk.limit(previousLimit);
            if (!frameBuffer.hasRemaining()) {
                encodeFrame(FRAME_BYTES);
                frameBuffer.clear();
            }
        }
    }

    private void postRoute(@NonNull AudioRecord record) {
        int type = -1;
        int[] rates = new int[0];
        try {
            AudioDeviceInfo routed = record.getRoutedDevice();
            if (routed != null) {
                type = routed.getType();
                rates = routed.getSampleRates();
            }
        } catch (Throwable ignored) {
        }
        final int routedType = type;
        final int[] routedRates = rates;
        encoderHandler.post(() -> applyRoute(routedType, routedRates));
    }

    /** Encoder thread: picks the voice enhancement profile and the Opus limits for the input that really records. */
    private void applyRoute(int type, @NonNull int[] rates) {
        if (encodingFinished || encoder == null || encoderFailure != null) return;
        if (routeKnown && type == routeType) return;
        routeKnown = true;
        routeType = type;
        routeRates = rates;
        try {
            int profile = AudioVoiceEnhancer.profileFor(NanogramFeatures.headsetVoiceEnhancement(), type);
            if (profile != enhancerProfile) {
                drainEnhancer();
                if (enhancer != null) {
                    enhancer.recycle();
                    enhancer = null;
                }
                enhancerProfile = AudioVoiceEnhancer.PROFILE_NONE;
                int initialClass = type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        ? AudioVoiceEnhancer.CLASS_WIDE : AudioVoiceEnhancer.CLASS_SUPER_WIDE;
                enhancer = AudioVoiceEnhancer.create(profile, initialClass,
                        noiseSuppression == AudioRecordingSession.NoiseSuppression.RNNOISE, true);
                if (enhancer != null) {
                    enhancerProfile = profile;
                    reportedClass = enhancer.bandwidthClass();
                } else if (profile != AudioVoiceEnhancer.PROFILE_NONE) {
                    diagnostics.log("voice enhancement unavailable; recording unprocessed PCM");
                }
            }
            diagnostics.log("voice enhancement: inputType=" + type + ", rates=" + java.util.Arrays.toString(rates)
                    + ", profile=" + enhancerProfile
                    + ", delaySamples=" + (enhancer == null ? 0 : enhancer.delaySamples()));
            applyOpusRoute();
        } catch (Throwable error) {
            diagnostics.error("voice enhancement route setup failed", error);
            if (enhancer != null) {
                enhancer.recycle();
                enhancer = null;
            }
            enhancerProfile = AudioVoiceEnhancer.PROFILE_NONE;
        }
    }

    private void applyOpusRoute() {
        boolean processing = enhancer != null && enhancer.modifiesAudio();
        boolean extending = processing && enhancer.profile() == AudioVoiceEnhancer.PROFILE_HEADSET
                && enhancer.level() == AudioVoiceEnhancer.LEVEL_FULL;
        int cls = enhancer == null ? AudioVoiceEnhancer.CLASS_UNKNOWN : enhancer.bandwidthClass();
        int bandwidth = AudioVoiceEnhancer.opusMaxBandwidth(routeType, routeRates, cls, extending);
        boolean limited = encoder.setMaxBandwidth(bandwidth);
        // OPUS_SIGNAL stays automatic unless the enhancement changes the audio (then it is known to be speech).
        boolean voice = encoder.setVoiceSignal(processing);
        diagnostics.log("Opus route settings: maxBandwidth=" + bandwidth + " (" + limited + "), voiceSignal="
                + processing + " (" + voice + "), measuredClass=" + AudioVoiceEnhancer.className(cls));
    }

    /** Writes what is still inside the chain; used before the chain is replaced and at the end. */
    private void drainEnhancer() {
        if (enhancer == null || !enhancer.modifiesAudio() || encoder == null) return;
        ByteBuffer tail = ByteBuffer.allocateDirect(8192 * 2).order(ByteOrder.nativeOrder());
        int samples = enhancer.flush(tail, 8192);
        if (samples > 0) {
            encoder.writeFrame(tail, samples * 2);
        }
    }

    private void collectInputStats(int sampleCount) {
        for (int index = 0; index < sampleCount; index++) {
            int sample = frameBuffer.getShort(index * 2);
            int magnitude = Math.abs(sample);
            if (magnitude > inputPeak) inputPeak = magnitude;
            if (magnitude >= 32767) inputClipped++;
            inputSum += sample;
        }
        inputSamples += sampleCount;
    }

    /** Runs the chain on the frame; returns the bytes to encode, or 0 while the start-up delay is dropped. */
    private int runEnhancer(int byteCount) {
        int samples = enhancer.process(frameBuffer, byteCount / 2);
        if (samples < 0) {
            diagnostics.log("voice enhancement failed; recording unprocessed PCM");
            enhancer.recycle();
            enhancer = null;
            enhancerProfile = AudioVoiceEnhancer.PROFILE_NONE;
            return byteCount;
        }
        enhancedFrames++;
        int queued = queuedChunks.get();
        if (queued <= 5) {
            backlogDrained = true;
        }
        // Only a backlog that built up after the encoder caught up counts: re-encoding a resumed prefix queues chunks too.
        if (backlogDrained) {
            if (queued > 500) {
                enhancer.limitLevel(AudioVoiceEnhancer.LEVEL_BYPASS);
            } else if (queued > 50) {
                enhancer.limitLevel(AudioVoiceEnhancer.LEVEL_REDUCED);
            }
        }
        if (enhancedFrames - lastEnhancerCheckFrames >= 50) {
            lastEnhancerCheckFrames = enhancedFrames;
            int micros = enhancer.takeAverageBlockMicros();
            int cls = enhancer.bandwidthClass();
            diagnostics.log("voice enhancement: averageBlockMicros=" + micros + ", level=" + enhancer.level()
                    + ", class=" + AudioVoiceEnhancer.className(cls));
            if (cls != reportedClass) {
                reportedClass = cls;
                applyOpusRoute();
            }
        }
        return samples * 2;
    }

    private void encodeFrame(int byteCount) {
        frameBuffer.position(0);
        frameBuffer.limit(byteCount);
        collectInputStats(byteCount / 2);
        boolean enhanced = false;
        if (enhancer != null) {
            enhanced = enhancer.modifiesAudio();
            int bytes = runEnhancer(byteCount);
            if (enhancer == null) enhanced = false;
            if (bytes <= 0) return;
            byteCount = bytes;
            frameBuffer.position(0);
            frameBuffer.limit(byteCount);
        }
        if (noiseSuppressor != null && !enhanced) {
            long startedAtNs = System.nanoTime();
            boolean processed = noiseSuppressor.process(frameBuffer, byteCount / 2);
            long elapsedNs = System.nanoTime() - startedAtNs;
            noiseProcessingCount++;
            noiseProcessingTotalNs += elapsedNs;
            noiseProcessingMaxNs = Math.max(noiseProcessingMaxNs, elapsedNs);
            if (!processed) {
                diagnostics.log("RNNoise processing failed; recording unprocessed PCM");
                noiseSuppressor.recycle();
                noiseSuppressor = null;
            } else if (noiseProcessingCount % 100L == 0L) {
                diagnostics.log("RNNoise processing: averageMs="
                        + nsToMs(noiseProcessingTotalNs / noiseProcessingCount)
                        + ", maxMs=" + nsToMs(noiseProcessingMaxNs));
            }
            frameBuffer.position(0);
            frameBuffer.limit(byteCount);
        }
        if (encoder == null || encoder.writeFrame(frameBuffer, byteCount) == 0) {
            throw new IllegalStateException("Native Opus encoding failed");
        }
    }

    private void finishEncoding(long samples, @Nullable Exception captureFailure) {
        if (encodingFinished) return;
        encodingFinished = true;
        Exception failure = encoderFailure != null ? encoderFailure : captureFailure;
        try {
            if (failure == null && encoder != null && frameBuffer.position() > 0) {
                encodeFrame(frameBuffer.position());
                frameBuffer.clear();
            }
            if (failure == null) drainEnhancer();
        } catch (Throwable error) {
            failure = asException(error);
        } finally {
            if (noiseSuppressor != null) {
                noiseSuppressor.recycle();
                noiseSuppressor = null;
            }
            logEnhancementSummary();
            if (enhancer != null) {
                enhancer.recycle();
                enhancer = null;
            }
            if (encoder != null) {
                try {
                    if (!encoder.finish() && failure == null) {
                        failure = new IllegalStateException("Unable to finalize native Opus recorder");
                    }
                } catch (Throwable error) {
                    if (failure == null) failure = asException(error);
                }
                encoder.recycle();
                encoder = null;
            }
        }

        long finalSize = outputFile.isFile() ? outputFile.length() : 0L;
        if (noiseProcessingCount > 0L) {
            diagnostics.log("RNNoise summary: frames=" + noiseProcessingCount
                    + ", averageMs=" + nsToMs(noiseProcessingTotalNs / noiseProcessingCount)
                    + ", maxMs=" + nsToMs(noiseProcessingMaxNs));
        }
        diagnostics.log("capture stopped: samples=" + samples
                + ", durationMs=" + samplesToMs(samples)
                + ", bytes=" + finalSize
                + ", maxQueuedChunks=" + maximumQueuedChunks);
        if (failure == null) {
            callback.onStopped(samples, finalSize);
        } else {
            callback.onError(failure);
        }
        encoderThread.quitSafely();
        chunkPool.clear();
    }

    private void logEnhancementSummary() {
        if (inputSamples == 0L) return;
        double peakDb = inputPeak == 0 ? -120d : 20d * Math.log10(inputPeak / 32768d);
        String line = "input levels: peakDbfs=" + Math.round(peakDb * 10d) / 10d
                + ", clippedSamples=" + inputClipped
                + ", dcOffset=" + Math.round((double) inputSum / inputSamples)
                + ", voiceEnhancement=" + (enhancer == null ? "off" : "profile " + enhancerProfile
                + " class " + AudioVoiceEnhancer.className(enhancer.bandwidthClass())
                + " level " + enhancer.level());
        diagnostics.log(line);
        AudioRouteReport.add("voice " + line);
    }

    @NonNull
    private ByteBuffer obtainChunk() {
        ByteBuffer chunk = chunkPool.poll();
        if (chunk == null) {
            chunk = ByteBuffer.allocateDirect(FRAME_BYTES).order(ByteOrder.nativeOrder());
        }
        chunk.clear();
        return chunk;
    }

    private void recycleChunk(@NonNull ByteBuffer chunk) {
        if (!encodingFinished) {
            chunkPool.offer(chunk);
        }
    }

    private static long samplesToMs(long samples) {
        return samples * 1000L / SAMPLE_RATE;
    }

    private static double nsToMs(long durationNs) {
        return durationNs / 1_000_000d;
    }

    private static Exception asException(@NonNull Throwable error) {
        return error instanceof Exception ? (Exception) error : new RuntimeException(error);
    }
}
