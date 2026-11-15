package org.telegram.utils.recorder.round;

import org.telegram.messenger.AudioInputPreference;
import org.telegram.messenger.AudioRouteMonitor;
import org.telegram.messenger.AudioRouteSession;
import org.telegram.messenger.AudioRouteReport;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.nanogram.NanogramFeatures;
import org.telegram.utils.recorder.audio.AudioVoiceEnhancer;
import org.telegram.messenger.video.RoundAudioTimeline;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

final class RoundVideoCodecRecorder implements RoundVideoGlProcessor.FrameTimingListener {
    interface ErrorListener {
        void onError(@NonNull Exception error);
    }

    private static final int AUDIO_SAMPLE_RATE = 48_000;
    private static final int AUDIO_BIT_RATE = 64_000;
    private static final int AUDIO_BYTES_PER_FRAME = 2;
    private static final int AUDIO_FRAMES_PER_INPUT = 1024;
    private static final int AUDIO_BYTES_PER_INPUT =
            AUDIO_FRAMES_PER_INPUT * AUDIO_BYTES_PER_FRAME;
    private static final long CODEC_TIMEOUT_US = 10_000;
    private static final int TIMING_CAPACITY = 256;
    private static final int TIMING_LOG_INTERVAL = 30;
    private static final long WORKER_JOIN_TIMEOUT_MS = 5000;
    private static final long WORKER_ABANDON_TIMEOUT_MS = 1000;

    private final RoundVideoMp4Writer writer;
    private final long timelineOffsetUs;
    private final int outputSize;
    private final int videoBitrate;
    private final int videoFrameRate;
    private final RoundVideoDiagnostics diagnostics;
    private final ErrorListener errorListener;
    private final ErrorListener segmentRejectedListener;
    private final AtomicBoolean errorReported = new AtomicBoolean();
    private final ArrayList<PendingAudioSample> pendingAudioSamples = new ArrayList<>();
    private final AudioTimestamp audioTimestamp = new AudioTimestamp();
    private final long[] submittedPtsUs = new long[TIMING_CAPACITY];
    private final long[] submittedTimeNs = new long[TIMING_CAPACITY];
    private MediaCodec videoCodec;
    private MediaCodec audioCodec;
    private AudioRecord audioRecord;
    private AudioVoiceEnhancer voiceEnhancer;
    private int voiceEnhancerRoute = -1;
    private long audioPtsShiftUs;
    private int voiceEnhancerReads;
    private AudioRouteSession audioRouteSession;
    private AudioRouteMonitor audioRouteMonitor;
    private Surface videoInputSurface;
    private Thread videoDrainThread;
    private Thread audioThread;
    private Runnable audioReadyListener;
    private Runnable videoReadyListener;
    private volatile boolean stopping;
    /** Deadline (System.nanoTime) of the initial route wait plus open; a reopen before the first read shares it. */
    private volatile long initialOpenDeadlineNs;
    private volatile boolean audioReadSeen;
    private volatile boolean workersAbandoned;
    private volatile boolean segmentRejected;
    private boolean stopFinalizationStarted;
    private int runningWorkers;
    private boolean releaseAfterWorkers;
    private volatile long stopPresentationTimeUs = Long.MAX_VALUE;
    private volatile long submittedSequence;
    private boolean prepared;
    private boolean started;
    private volatile long timeOriginNs;
    private long videoBytes;
    private long audioBytes;
    private int videoBuffers;
    private int audioBuffers;
    private int latencySamples;
    private long latencyTotalNs;
    private long latencyMaxNs;
    private int prependHeaderSize;
    private volatile long firstVideoPtsUs;
    private long lastVideoPtsUs;
    private long firstAudioPtsUs;
    private long lastAudioPtsUs;
    private long lastKeyframePtsUs;
    private long maximumKeyframeIntervalUs;
    private int videoKeyframes;
    private int nonMonotonicVideoPts;
    private int nonMonotonicAudioPts;
    private static final int REOPEN_REQUESTED = Integer.MIN_VALUE;
    private static final int MAX_AUDIO_REOPENS = 3;
    private static final long MAX_REOPEN_GAP_MS = 5_000;
    private volatile boolean reopenRequested;
    private volatile long lastAudioReadNs;
    private int audioReopens;
    private final AudioInputPreference.InputReopener inputReopener = this::requestReopenInput;
    private int audioRecordBufferBytes;
    private int audioReadErrors;
    private int emptyAudioReads;
    private int droppedAudioBuffers;
    private int droppedAudioAfterStop;
    private int droppedVideoAfterStop;
    private boolean audioStartAligned;
    private long alignedAudioStartDeltaUs;

    RoundVideoCodecRecorder(
            @NonNull RoundVideoMp4Writer writer,
            long timelineOffsetUs,
            int outputSize,
            int videoBitrate,
            int videoFrameRate,
            @NonNull RoundVideoDiagnostics diagnostics,
            @NonNull ErrorListener errorListener,
            @NonNull ErrorListener segmentRejectedListener
    ) {
        this.writer = writer;
        this.timelineOffsetUs = timelineOffsetUs;
        this.outputSize = outputSize;
        this.videoBitrate = videoBitrate;
        this.videoFrameRate = videoFrameRate;
        this.diagnostics = diagnostics;
        this.errorListener = errorListener;
        this.segmentRejectedListener = segmentRejectedListener;
    }

    @NonNull
    public synchronized Surface prepare() throws IOException {
        if (prepared) return videoInputSurface;
        resetStats();
        long startedNs = System.nanoTime();
        try {
            createVideoEncoder();
            createAudioEncoder();
            videoCodec.start();
            prepared = true;
            diagnostics.log("codecs prepared: video=" + videoCodec.getName()
                    + ", audio=" + audioCodec.getName()
                    + ", elapsedMs=" + elapsedMs(startedNs));
            return videoInputSurface;
        } catch (IOException | RuntimeException e) {
            release();
            throw e;
        }
    }

    synchronized void startRecording(
            @NonNull Runnable audioReadyListener,
            @NonNull Runnable videoReadyListener
    ) {
        if (started) return;
        if (!prepared) throw new IllegalStateException("Recorder is not prepared");
        try {
            audioCodec.start();
            this.audioReadyListener = audioReadyListener;
            this.videoReadyListener = videoReadyListener;
            started = true;
            diagnostics.log("recording started; the audio input opens on the audio thread: timelineOffsetUs="
                    + timelineOffsetUs);
            videoDrainThread = new Thread(this::drainVideo, "RoundVideoVideoEncoder");
            audioThread = new Thread(this::captureAndEncodeAudio, "RoundVideoAudioEncoder");
            runningWorkers = 2;
            videoDrainThread.start();
            audioThread.start();
        } catch (RuntimeException e) {
            release();
            throw e;
        }
    }

    synchronized boolean isStarted() { return started; }

    int getVideoFrameRate() { return videoFrameRate; }

    long getTimeOriginNs() { return timeOriginNs; }

    @Override
    public void onFrameSubmitted(long presentationTimeUs, long submittedNs) {
        long sequence = submittedSequence;
        int index = (int) (sequence % TIMING_CAPACITY);
        submittedPtsUs[index] = presentationTimeUs;
        submittedTimeNs[index] = submittedNs;
        submittedSequence = sequence + 1;
        notifyVideoReady();
    }

    void stop() {
        requestStop();
        synchronized (this) {
            if (!prepared || stopFinalizationStarted) return;
            if (!started) {
                release();
                return;
            }
            stopFinalizationStarted = true;
        }
        try {
            videoCodec.signalEndOfInputStream();
        } catch (IllegalStateException e) {
            diagnostics.error("video encoder EOS failed", e);
        }
        join(videoDrainThread, WORKER_JOIN_TIMEOUT_MS);
        join(audioThread, WORKER_JOIN_TIMEOUT_MS);
        logSummary();
        synchronized (this) {
            if (runningWorkers > 0) {
                workersAbandoned = true;
            }
        }
        if (workersAbandoned) {
            join(videoDrainThread, WORKER_ABANDON_TIMEOUT_MS);
            join(audioThread, WORKER_ABANDON_TIMEOUT_MS);
        }
        synchronized (this) {
            if (runningWorkers > 0) {
                // Releasing a codec that a worker is still calling into crashes natively.
                releaseAfterWorkers = true;
                diagnostics.log("codec release deferred until encoder threads exit: running="
                        + runningWorkers);
                return;
            }
        }
        release();
    }

    long requestStop() {
        AudioRecord record;
        long cutoffUs;
        synchronized (this) {
            if (!prepared || !started) return Long.MAX_VALUE;
            if (stopping) return stopPresentationTimeUs;
            stopping = true;
            long originNs = timeOriginNs;
            // Zero until the audio input is open: a stop during the Bluetooth wait ends an empty segment.
            cutoffUs = originNs == 0 ? 0 : Math.max(0,
                    (SystemClock.elapsedRealtimeNanos() - originNs) / 1000L);
            stopPresentationTimeUs = cutoffUs;
            record = audioRecord;
        }
        try {
            if (record != null
                    && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop();
            }
        } catch (IllegalStateException e) {
            diagnostics.error("AudioRecord stop failed", e);
        }
        diagnostics.log("recording stop boundary: presentationTimeUs=" + cutoffUs);
        return cutoffUs;
    }

    private void resetStats() {
        stopping = false;
        audioReadSeen = false;
        initialOpenDeadlineNs = 0;
        workersAbandoned = false;
        segmentRejected = false;
        stopFinalizationStarted = false;
        runningWorkers = 0;
        releaseAfterWorkers = false;
        stopPresentationTimeUs = Long.MAX_VALUE;
        timeOriginNs = 0;
        videoBytes = audioBytes = 0;
        videoBuffers = audioBuffers = 0;
        submittedSequence = 0;
        latencySamples = 0;
        latencyTotalNs = latencyMaxNs = 0;
        firstVideoPtsUs = lastVideoPtsUs = Long.MIN_VALUE;
        firstAudioPtsUs = lastAudioPtsUs = Long.MIN_VALUE;
        lastKeyframePtsUs = Long.MIN_VALUE;
        maximumKeyframeIntervalUs = 0;
        videoKeyframes = 0;
        nonMonotonicVideoPts = nonMonotonicAudioPts = 0;
        audioReadErrors = emptyAudioReads = 0;
        audioReopens = 0;
        droppedAudioBuffers = 0;
        droppedAudioAfterStop = 0;
        droppedVideoAfterStop = 0;
        audioStartAligned = false;
        alignedAudioStartDeltaUs = Long.MIN_VALUE;
        audioReadyListener = null;
        videoReadyListener = null;
        pendingAudioSamples.clear();
    }

    /**
     * Returns the AVC encoder configuration used for recording. Trimming re-encodes with the same
     * configuration so the re-encoded frames share the recorded SPS/PPS.
     */
    @NonNull
    static MediaFormat createVideoEncoderFormat(int outputSize, int videoBitrate, int videoFrameRate) {
        MediaFormat format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                outputSize,
                outputSize
        );
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        return format;
    }

    /** Returns the size of SPS/PPS an encoder prepends to sync frames, or zero. */
    static int getPrependedHeaderSize(@NonNull MediaFormat format) {
        if (!format.containsKey(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES)
                || format.getInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) != 1) {
            return 0;
        }
        ByteBuffer sps = format.getByteBuffer("csd-0");
        ByteBuffer pps = format.getByteBuffer("csd-1");
        return (sps == null ? 0 : sps.remaining()) + (pps == null ? 0 : pps.remaining());
    }

    private void createVideoEncoder() throws IOException {
        MediaFormat format = createVideoEncoderFormat(outputSize, videoBitrate, videoFrameRate);
        videoCodec = org.telegram.messenger.NanogramMediaCapabilities.createEncoder(MediaFormat.MIMETYPE_VIDEO_AVC, outputSize, outputSize, videoFrameRate);
        MediaCodecInfo.VideoCapabilities capabilities = videoCodec.getCodecInfo()
                .getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                .getVideoCapabilities();
        if (!capabilities.isSizeSupported(outputSize, outputSize)
                || !capabilities.areSizeAndRateSupported(
                outputSize,
                outputSize,
                videoFrameRate
        )) {
            throw new IOException("Video encoder does not support "
                    + outputSize + "x" + outputSize + " at " + videoFrameRate + " fps");
        }
        diagnostics.log("video encoder configure: codec=" + videoCodec.getName()
                + ", format=" + format);
        videoCodec = org.telegram.messenger.NanogramMediaCapabilities.configureEncoder(videoCodec, format, MediaFormat.MIMETYPE_VIDEO_AVC);
        videoInputSurface = videoCodec.createInputSurface();
    }

    private void createAudioEncoder() throws IOException {
        int minBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize <= 0) throw new IllegalStateException("Unsupported audio recording configuration");
        int audioBufferSize = Math.max(minBufferSize * 4, 16_384);
        audioRecordBufferBytes = audioBufferSize;
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, 1);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSize);
        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        diagnostics.log("audio encoder configure: codec=" + audioCodec.getName()
                + ", format=" + format
                + ", minBufferSize=" + minBufferSize
                + ", audioBufferSize=" + audioBufferSize);
        audioCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    /**
     * Audio thread. Acquires the Bluetooth route (cancelled by stop) and opens the AudioRecord; the recorder's lock is
     * not held during the wait. The time origin is set once the input records, and the common A/V start waits for the
     * first audio read, so video starts when audio does. Returns false when stop came first.
     */
    private boolean openInitialAudioInput() {
        AudioInputPreference.registerReopener(inputReopener);
        long acquireStartedNs = System.nanoTime();
        final long deadlineNs = acquireStartedNs + RoundAudioSource.OPEN_BUDGET_MS * 1_000_000L;
        initialOpenDeadlineNs = deadlineNs;
        AudioRouteSession session = AudioInputPreference.acquireRoute(RoundAudioSource.ROUTE_WAIT_MS,
                () -> stopping || System.nanoTime() > deadlineNs);
        synchronized (this) {
            audioRouteSession = session;
        }
        diagnostics.log("audio route acquired: outcome=" + session.outcome() + ", waitMs=" + elapsedMs(acquireStartedNs));
        if (stopping) return false;
        int source = RoundAudioSource.forSession(session);
        AudioRecord record = new AudioRecord(source, AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, audioRecordBufferBytes);
        try {
            if (record.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Unable to initialize AudioRecord");
            AudioInputPreference.applyTo(record);
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("Unable to start AudioRecord");
            }
        } catch (RuntimeException e) {
            AudioInputPreference.unregister(record);
            record.release();
            throw e;
        }
        AudioRouteMonitor monitor = AudioRouteMonitor.start("round-v2", record, session, AUDIO_SAMPLE_RATE, diagnostics::log);
        boolean lateStop;
        synchronized (this) {
            lateStop = stopping;
            if (!lateStop) {
                audioRecord = record;
                audioRouteMonitor = monitor;
            }
        }
        if (lateStop) {
            // requestStop() ran before the recorder was published and could not stop it.
            monitor.stop();
            try {
                record.stop();
            } catch (IllegalStateException ignored) {
            }
            AudioInputPreference.unregister(record);
            record.release();
            return false;
        }
        lastAudioReadNs = System.nanoTime();
        timeOriginNs = SystemClock.elapsedRealtimeNanos();
        diagnostics.log("audio input recording: timeOriginNs=" + timeOriginNs
                + ", source=" + source
                + ", audioSessionId=" + record.getAudioSessionId());
        return true;
    }

    private void drainVideo() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            boolean ended = false;
            while (!ended && !workersAbandoned) {
                int index = videoCodec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    setTrackFormat(true, videoCodec.getOutputFormat());
                } else if (index >= 0) {
                    if (isMediaBuffer(info)) {
                        if (info.presentationTimeUs < stopPresentationTimeUs) {
                            videoBuffers++;
                            videoBytes += info.size;
                            recordSampleStats(true, info);
                            recordCodecLatency(info.presentationTimeUs);
                            writeSample(true, videoCodec.getOutputBuffer(index), info);
                        } else {
                            droppedVideoAfterStop++;
                        }
                    }
                    ended = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    videoCodec.releaseOutputBuffer(index, false);
                }
            }
        } catch (RuntimeException e) {
            if (!stopping) reportError(e);
            else diagnostics.error("video drain failed while stopping", e);
        } finally {
            onWorkerExited();
        }
    }

    private void captureAndEncodeAudio() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long submittedFrames = 0;
        long readFrames = 0;
        int seenSwitch = AudioInputPreference.switchGeneration();
        long switchGuardUntil = 0;
        long guardSilenceFrames = 0;
        long audioBasePtsUs = Long.MIN_VALUE;
        boolean inputEnded = false;
        boolean outputEnded = false;
        try {
            openInitialAudioInput();
            while (!outputEnded && !workersAbandoned) {
                outputEnded = drainAudioOutput(info, inputEnded ? CODEC_TIMEOUT_US : 0);
                if (outputEnded) break;
                int index = audioCodec.dequeueInputBuffer(CODEC_TIMEOUT_US);
                if (index < 0) continue;
                if (stopping) {
                    long ptsUs = audioBasePtsUs == Long.MIN_VALUE ? 0 : audioBasePtsUs + framesToDurationUs(submittedFrames);
                    audioCodec.queueInputBuffer(index, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    inputEnded = true;
                    continue;
                }
                ByteBuffer buffer = audioCodec.getInputBuffer(index);
                if (buffer == null) {
                    audioCodec.queueInputBuffer(index, 0, 0, 0, 0);
                    continue;
                }
                buffer.clear();
                int bytesRead;
                if (reopenRequested) {
                    reopenRequested = false;
                    bytesRead = REOPEN_REQUESTED;
                } else {
                    bytesRead = audioRecord.read(
                            buffer,
                            Math.min(buffer.remaining(), AUDIO_BYTES_PER_INPUT)
                    );
                }
                if (bytesRead > 0) {
                    lastAudioReadNs = System.nanoTime();
                }
                if (bytesRead <= 0) {
                    boolean reopen = bytesRead == REOPEN_REQUESTED;
                    if (bytesRead < 0 && !reopen) {
                        audioReadErrors++;
                        reopen = (bytesRead == AudioRecord.ERROR_DEAD_OBJECT || bytesRead == AudioRecord.ERROR_INVALID_OPERATION)
                                && audioReopens < MAX_AUDIO_REOPENS;
                        if (reopen) {
                            audioReopens++;
                        } else if (bytesRead == AudioRecord.ERROR_DEAD_OBJECT || audioReadErrors >= 3) {
                            throw new IllegalStateException("AudioRecord read failed: " + bytesRead);
                        }
                    }
                    if (reopen) {
                        long gapFrames = reopenAudioInput("read result " + bytesRead);
                        readFrames = 0;
                        if (audioBasePtsUs != Long.MIN_VALUE && gapFrames > 0) {
                            submittedFrames += queueSilence(info, gapFrames, audioBasePtsUs, submittedFrames);
                        }
                    } else if (bytesRead == 0) {
                        emptyAudioReads++;
                    }
                    long ptsUs = audioBasePtsUs == Long.MIN_VALUE
                            ? 0
                            : audioBasePtsUs + framesToDurationUs(submittedFrames);
                    audioCodec.queueInputBuffer(index, 0, 0, ptsUs, 0);
                    continue;
                }
                int framesRead = bytesRead / AUDIO_BYTES_PER_FRAME;
                if (audioBasePtsUs == Long.MIN_VALUE) {
                    audioBasePtsUs = resolveAudioBasePtsUs(framesRead);
                    diagnostics.log("first audio input: basePtsUs=" + audioBasePtsUs
                            + ", frames=" + framesRead
                            + ", timestampSource="
                            + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                            ? "AudioTimestamp" : "read completion"));
                    startVoiceEnhancer();
                    if (voiceEnhancer != null) {
                        audioPtsShiftUs = Math.min(audioBasePtsUs, framesToDurationUs(voiceEnhancer.delaySamples()));
                        audioBasePtsUs -= audioPtsShiftUs;
                    }
                }
                collectInputStats(buffer, framesRead);
                if (voiceEnhancer != null) {
                    enhanceVoice(buffer, framesRead);
                }
                int generation = AudioInputPreference.switchGeneration();
                if (generation != seenSwitch) {
                    seenSwitch = generation;
                    switchGuardUntil = SystemClock.elapsedRealtime() + 1500;
                    guardSilenceFrames = 0;
                }
                if (SystemClock.elapsedRealtime() < switchGuardUntil) {
                    // The cap holds for the whole switch, not for each read: a bad timestamp must not add 2 s per read.
                    long gap = Math.min(audioGapFrames(audioBasePtsUs, submittedFrames, readFrames),
                            RoundAudioTimeline.SILENCE_MAX_US * AUDIO_SAMPLE_RATE / 1_000_000L - guardSilenceFrames);
                    if (gap > 0) {
                        diagnostics.log("microphone switch: audio lags, silence frames=" + gap);
                        long queuedSilence = queueSilence(info, gap, audioBasePtsUs, submittedFrames);
                        guardSilenceFrames += queuedSilence;
                        submittedFrames += queuedSilence;
                    }
                }
                readFrames += framesRead;
                long ptsUs = audioBasePtsUs + framesToDurationUs(submittedFrames);
                audioCodec.queueInputBuffer(index, 0, framesRead * AUDIO_BYTES_PER_FRAME, ptsUs, 0);
                submittedFrames += framesRead;
                notifyAudioReady();
            }
        } catch (RuntimeException e) {
            if (!stopping) reportError(e);
            else diagnostics.error("audio capture failed while stopping", e);
        } finally {
            logInputLevels();
            finishVoiceEnhancer();
            onWorkerExited();
        }
    }

    private void onWorkerExited() {
        boolean release;
        synchronized (this) {
            runningWorkers--;
            release = releaseAfterWorkers && runningWorkers == 0;
            if (release) releaseAfterWorkers = false;
        }
        if (release) {
            diagnostics.log("deferred codec release: encoder threads exited");
            release();
        }
    }

    private boolean drainAudioOutput(MediaCodec.BufferInfo info, long timeoutUs) {
        boolean ended = false;
        int index = audioCodec.dequeueOutputBuffer(info, timeoutUs);
        while (index >= 0) {
            if (isMediaBuffer(info)) {
                writeOrBufferAudioSample(audioCodec.getOutputBuffer(index), info);
            }
            ended |= (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            audioCodec.releaseOutputBuffer(index, false);
            index = audioCodec.dequeueOutputBuffer(info, 0);
        }
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) setTrackFormat(false, audioCodec.getOutputFormat());
        if (ended && !audioStartAligned && firstVideoPtsUs != Long.MIN_VALUE) {
            flushPendingAudioSamples(firstVideoPtsUs, true);
        }
        return ended;
    }

    /** Moves capture to the input resolved now (microphone switch); the audio thread does the work. */
    synchronized boolean requestReopenInput() {
        if (!started || stopping) return false;
        reopenRequested = true;
        return true;
    }

    /** Audio thread only. Replaces the AudioRecord and its route, returns the frames missed meanwhile. */
    private long reopenAudioInput(String reason) {
        diagnostics.log("reopening audio input: " + reason);
        AudioRecord old;
        AudioRouteMonitor oldMonitor;
        AudioRouteSession oldSession;
        synchronized (this) {
            old = audioRecord;
            oldMonitor = audioRouteMonitor;
            oldSession = audioRouteSession;
            audioRouteMonitor = null;
        }
        if (oldMonitor != null) oldMonitor.stop();
        if (old != null) {
            try {
                if (old.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) old.stop();
            } catch (IllegalStateException ignored) {
            }
            AudioInputPreference.unregister(old);
            old.release();
        }
        if (oldSession != null) oldSession.release();
        // Before the first audio read the common A/V start timeout is running: share the initial open's deadline.
        final long deadlineNs = audioReadSeen
                ? System.nanoTime() + RoundAudioSource.OPEN_BUDGET_MS * 1_000_000L
                : initialOpenDeadlineNs;
        AudioRouteSession session = AudioInputPreference.acquireRoute(RoundAudioSource.ROUTE_WAIT_MS,
                () -> stopping || System.nanoTime() > deadlineNs);
        if (stopping) {
            session.release();
            return 0;
        }
        AudioRecord next = null;
        try {
            next = new AudioRecord(RoundAudioSource.forSession(session), AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, audioRecordBufferBytes);
            if (next.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Unable to initialize AudioRecord");
            AudioInputPreference.applyTo(next);
            next.startRecording();
            if (next.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("Unable to start AudioRecord");
        } catch (RuntimeException e) {
            if (next != null) {
                AudioInputPreference.unregister(next);
                next.release();
            }
            session.release();
            throw e;
        }
        AudioRouteMonitor monitor = AudioRouteMonitor.start("round-v2", next, session, AUDIO_SAMPLE_RATE, diagnostics::log);
        boolean lateStop;
        synchronized (this) {
            lateStop = stopping;
            audioRecord = next;
            audioRouteSession = session;
            audioRouteMonitor = monitor;
        }
        if (lateStop) {
            // Published, so release() frees everything; only the live capture must end now.
            try {
                next.stop();
            } catch (IllegalStateException ignored) {
            }
        }
        long lastReadNs = lastAudioReadNs;
        lastAudioReadNs = System.nanoTime();
        if (lastReadNs == 0) return 0;
        long gapMs = Math.min(MAX_REOPEN_GAP_MS, (lastAudioReadNs - lastReadNs) / 1_000_000L);
        return gapMs * AUDIO_SAMPLE_RATE / 1000L;
    }

    private void notifyAudioReady() {
        Runnable listener;
        synchronized (this) {
            if (audioReadyListener == null || stopping) return;
            listener = audioReadyListener;
            audioReadyListener = null;
        }
        audioReadSeen = true;
        diagnostics.log("audio capture ready; waiting for common A/V start frame");
        listener.run();
    }

    private void notifyVideoReady() {
        Runnable listener;
        synchronized (this) {
            if (videoReadyListener == null || stopping) return;
            listener = videoReadyListener;
            videoReadyListener = null;
        }
        diagnostics.log("first synchronized video frame submitted");
        listener.run();
    }

    /** Audio thread: the enhancement chain for the input that records (a round video keeps one chain for its whole length). */
    private void startVoiceEnhancer() {
        try {
            voiceEnhancerRoute = routedInputType();
            int profile = AudioVoiceEnhancer.profileFor(NanogramFeatures.headsetVoiceEnhancement(), voiceEnhancerRoute);
            if (profile == AudioVoiceEnhancer.PROFILE_NONE) return;
            int initialClass = voiceEnhancerRoute == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    ? AudioVoiceEnhancer.CLASS_WIDE : AudioVoiceEnhancer.CLASS_SUPER_WIDE;
            voiceEnhancer = AudioVoiceEnhancer.create(profile, initialClass, false, false);
            diagnostics.log("voice enhancement: inputType=" + voiceEnhancerRoute + ", profile=" + profile
                    + ", started=" + (voiceEnhancer != null)
                    + (voiceEnhancer == null ? "" : ", delaySamples=" + voiceEnhancer.delaySamples()));
        } catch (Throwable error) {
            diagnostics.error("voice enhancement start failed", error);
            voiceEnhancer = null;
        }
    }

    private int routedInputType() {
        try {
            AudioDeviceInfo routed = audioRecord.getRoutedDevice();
            return routed == null ? -1 : routed.getType();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private void enhanceVoice(ByteBuffer buffer, int frames) {
        try {
            if (++voiceEnhancerReads % 50 == 0) {
                int type = routedInputType();
                if (type != voiceEnhancerRoute) {
                    voiceEnhancerRoute = type;
                    int profile = AudioVoiceEnhancer.profileFor(NanogramFeatures.headsetVoiceEnhancement(), type);
                    if (profile != voiceEnhancer.profile()) {
                        voiceEnhancer.limitLevel(AudioVoiceEnhancer.LEVEL_BYPASS);
                        diagnostics.log("voice enhancement: input changed to type " + type + ", chain bypassed");
                    }
                }
            }
            if (voiceEnhancer.process(buffer, frames) < 0) {
                diagnostics.log("voice enhancement failed; recording unprocessed audio");
                finishVoiceEnhancer();
            }
        } catch (Throwable error) {
            diagnostics.error("voice enhancement failed", error);
            finishVoiceEnhancer();
        }
    }

    private long inputSamples;
    private int inputPeak;
    private long inputClipped;
    private long inputSum;

    /** Audio thread: level counters of what the microphone delivered, before any processing. */
    private void collectInputStats(ByteBuffer buffer, int frames) {
        java.nio.ShortBuffer samples = buffer.duplicate().order(java.nio.ByteOrder.nativeOrder()).asShortBuffer();
        for (int index = 0; index < frames && index < samples.limit(); index++) {
            int sample = samples.get(index);
            int magnitude = Math.abs(sample);
            if (magnitude > inputPeak) inputPeak = magnitude;
            if (magnitude >= 32767) inputClipped++;
            inputSum += sample;
        }
        inputSamples += frames;
    }

    private void logInputLevels() {
        if (inputSamples == 0L) return;
        double peakDb = inputPeak == 0 ? -120d : 20d * Math.log10(inputPeak / 32768d);
        String line = "input levels: peakDbfs=" + Math.round(peakDb * 10d) / 10d
                + ", clippedSamples=" + inputClipped
                + ", dcOffset=" + Math.round((double) inputSum / inputSamples);
        diagnostics.log(line);
        AudioRouteReport.add("round " + line);
    }

    private void finishVoiceEnhancer() {
        AudioVoiceEnhancer enhancer = voiceEnhancer;
        voiceEnhancer = null;
        if (enhancer == null) return;
        String line = "round voice enhancement: inputType=" + voiceEnhancerRoute
                + ", class=" + AudioVoiceEnhancer.className(enhancer.bandwidthClass())
                + ", level=" + enhancer.level()
                + ", averageBlockMicros=" + enhancer.takeAverageBlockMicros();
        diagnostics.log(line);
        AudioRouteReport.add(line);
        enhancer.recycle();
    }

    private long audioGapFrames(long basePtsUs, long submittedFrames, long readFrames) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                || audioRecord.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_BOOTTIME) != AudioRecord.SUCCESS) {
            return 0;
        }
        long actualUs = (audioTimestamp.nanoTime - timeOriginNs) / 1000L
                + (readFrames - audioTimestamp.framePosition) * 1_000_000L / AUDIO_SAMPLE_RATE;
        long expectedUs = basePtsUs + audioPtsShiftUs + framesToDurationUs(submittedFrames);
        return RoundAudioTimeline.silenceFrames(expectedUs, actualUs, AUDIO_SAMPLE_RATE);
    }

    /**
     * Feeds zero samples to the encoder (ahead of the buffer just read) and returns the frames queued. The encoder's
     * output is drained on every pass: a long gap (a reopen can be seconds) needs far more input buffers than the
     * encoder owns, and without draining it stalls after the first few.
     */
    private long queueSilence(MediaCodec.BufferInfo info, long frames, long basePtsUs, long submittedFrames) {
        long queued = 0;
        int timeouts = 0;
        while (queued < frames && !stopping && !workersAbandoned) {
            drainAudioOutput(info, 0);
            int index = audioCodec.dequeueInputBuffer(CODEC_TIMEOUT_US);
            if (index < 0) {
                if (++timeouts >= 20) break;
                continue;
            }
            timeouts = 0;
            ByteBuffer buffer = audioCodec.getInputBuffer(index);
            if (buffer == null) {
                audioCodec.queueInputBuffer(index, 0, 0, 0, 0);
                break;
            }
            buffer.clear();
            int chunkFrames = (int) Math.min(frames - queued, Math.min(buffer.remaining(), AUDIO_BYTES_PER_INPUT) / AUDIO_BYTES_PER_FRAME);
            int bytes = chunkFrames * AUDIO_BYTES_PER_FRAME;
            for (int a = 0; a < bytes; a++) buffer.put((byte) 0);
            if (voiceEnhancer != null) {
                // Through the chain too, so what it still holds from before the gap does not play after the silence.
                try {
                    voiceEnhancer.process(buffer, chunkFrames);
                } catch (Throwable error) {
                    diagnostics.error("voice enhancement failed on silence", error);
                }
            }
            audioCodec.queueInputBuffer(index, 0, bytes, basePtsUs + framesToDurationUs(submittedFrames + queued), 0);
            queued += chunkFrames;
        }
        return queued;
    }

    private long resolveAudioBasePtsUs(int firstFramesRead) {
        long readCompletionBaseUs = Math.max(
                0,
                (SystemClock.elapsedRealtimeNanos() - timeOriginNs) / 1000L
                        - framesToDurationUs(firstFramesRead)
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && audioRecord.getTimestamp(
                audioTimestamp,
                AudioTimestamp.TIMEBASE_BOOTTIME
        ) == AudioRecord.SUCCESS) {
            long timestampBaseUs = Math.max(
                    0,
                    (audioTimestamp.nanoTime - timeOriginNs) / 1000L
                            - framesToDurationUs(audioTimestamp.framePosition)
            );
            diagnostics.log("audio timestamp alignment: framePosition="
                    + audioTimestamp.framePosition
                    + ", timestampDeltaUs="
                    + (audioTimestamp.nanoTime - timeOriginNs) / 1000L
                    + ", timestampBaseUs=" + timestampBaseUs
                    + ", readCompletionBaseUs=" + readCompletionBaseUs);
            return timestampBaseUs;
        }
        diagnostics.log("audio timestamp unavailable: readCompletionBaseUs="
                + readCompletionBaseUs);
        return readCompletionBaseUs;
    }

    private void writeOrBufferAudioSample(ByteBuffer source, MediaCodec.BufferInfo info) {
        if (source == null || segmentRejected) return;
        if (info.presentationTimeUs >= stopPresentationTimeUs) {
            droppedAudioBuffers++;
            droppedAudioAfterStop++;
            return;
        }
        if (audioStartAligned) {
            writeAudioSample(source, info);
            return;
        }
        bufferAudioSample(source, info);
        long videoStartUs = firstVideoPtsUs;
        if (videoStartUs != Long.MIN_VALUE && info.presentationTimeUs >= videoStartUs) {
            flushPendingAudioSamples(videoStartUs, false);
        }
    }

    private void bufferAudioSample(ByteBuffer source, MediaCodec.BufferInfo info) {
        ByteBuffer copy = ByteBuffer.allocateDirect(info.size);
        int oldPosition = source.position();
        int oldLimit = source.limit();
        try {
            source.position(info.offset);
            source.limit(info.offset + info.size);
            copy.put(source).flip();
        } finally {
            source.limit(oldLimit);
            source.position(oldPosition);
        }
        MediaCodec.BufferInfo copyInfo = new MediaCodec.BufferInfo();
        copyInfo.set(0, info.size, info.presentationTimeUs, info.flags);
        pendingAudioSamples.add(new PendingAudioSample(copy, copyInfo));
    }

    private void flushPendingAudioSamples(long videoStartUs, boolean force) {
        if (pendingAudioSamples.isEmpty()) return;
        int nextIndex = -1;
        for (int i = 0; i < pendingAudioSamples.size(); i++) {
            if (pendingAudioSamples.get(i).info.presentationTimeUs >= videoStartUs) {
                nextIndex = i;
                break;
            }
        }
        if (nextIndex < 0) {
            if (!force) return;
            nextIndex = pendingAudioSamples.size() - 1;
        } else if (nextIndex > 0) {
            long previousDelta = videoStartUs
                    - pendingAudioSamples.get(nextIndex - 1).info.presentationTimeUs;
            long nextDelta = pendingAudioSamples.get(nextIndex).info.presentationTimeUs
                    - videoStartUs;
            if (previousDelta <= nextDelta) nextIndex--;
        }
        for (int i = 0; i < nextIndex; i++) {
            droppedAudioBuffers++;
        }
        PendingAudioSample first = pendingAudioSamples.get(nextIndex);
        alignedAudioStartDeltaUs = first.info.presentationTimeUs - videoStartUs;
        for (int i = nextIndex; i < pendingAudioSamples.size(); i++) {
            PendingAudioSample sample = pendingAudioSamples.get(i);
            writeAudioSample(sample.data, sample.info);
        }
        pendingAudioSamples.clear();
        audioStartAligned = true;
        diagnostics.log("A/V start aligned: videoPtsUs=" + videoStartUs
                + ", audioPtsUs=" + first.info.presentationTimeUs
                + ", deltaUs=" + alignedAudioStartDeltaUs);
    }

    private void writeAudioSample(ByteBuffer source, MediaCodec.BufferInfo info) {
        audioBuffers++;
        audioBytes += info.size;
        recordSampleStats(false, info);
        writeSample(false, source, info);
    }

    private void setTrackFormat(boolean video, MediaFormat format) {
        try {
            if (video) {
                prependHeaderSize = getPrependedHeaderSize(format);
                writer.setEncoderFrameRate(videoFrameRate);
            }
            diagnostics.log((video ? "video" : "audio") + " output format: " + format);
            writer.setTrackFormat(video, format);
        } catch (RoundVideoMp4Writer.CodecConfigurationChangedException e) {
            rejectSegment(e);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Drops this segment when its encoder output cannot be appended to the earlier segments.
     * The samples already written stay valid, so the recording degrades to what was kept.
     */
    private void rejectSegment(@NonNull Exception error) {
        if (segmentRejected) return;
        segmentRejected = true;
        diagnostics.error("recording segment rejected; earlier segments are kept", error);
        segmentRejectedListener.onError(error);
    }

    private void writeSample(boolean video, ByteBuffer source, MediaCodec.BufferInfo sourceInfo) {
        if (source == null || segmentRejected) return;
        try {
            if (video
                    && prependHeaderSize > 0
                    && (sourceInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                    && sourceInfo.size > prependHeaderSize) {
                sourceInfo.offset += prependHeaderSize;
                sourceInfo.size -= prependHeaderSize;
            }
            writer.writeSample(video, source, sourceInfo, timelineOffsetUs);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void reportError(@NonNull Exception error) {
        diagnostics.error("codec error" + codecDiagnostic(error), error);
        if (errorReported.compareAndSet(false, true)) errorListener.onError(error);
    }

    private void recordCodecLatency(long ptsUs) {
        long sequence = submittedSequence;
        long first = Math.max(0, sequence - TIMING_CAPACITY);
        for (long i = sequence - 1; i >= first; i--) {
            int index = (int) (i % TIMING_CAPACITY);
            if (submittedPtsUs[index] != ptsUs) continue;
            long latencyNs = System.nanoTime() - submittedTimeNs[index];
            latencySamples++;
            latencyTotalNs += latencyNs;
            latencyMaxNs = Math.max(latencyMaxNs, latencyNs);
            if (latencySamples % TIMING_LOG_INTERVAL == 0) {
                diagnostics.log("codec latency: average="
                        + latencyTotalNs / (float) latencySamples / 1_000_000f
                        + " ms, max=" + latencyMaxNs / 1_000_000f + " ms");
            }
            return;
        }
    }

    private void recordSampleStats(boolean video, @NonNull MediaCodec.BufferInfo info) {
        long ptsUs = info.presentationTimeUs;
        if (video) {
            if (firstVideoPtsUs == Long.MIN_VALUE) firstVideoPtsUs = ptsUs;
            if (lastVideoPtsUs != Long.MIN_VALUE && ptsUs < lastVideoPtsUs) {
                nonMonotonicVideoPts++;
            }
            lastVideoPtsUs = Math.max(lastVideoPtsUs, ptsUs);
            if ((info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                videoKeyframes++;
                if (lastKeyframePtsUs != Long.MIN_VALUE) {
                    maximumKeyframeIntervalUs = Math.max(
                            maximumKeyframeIntervalUs,
                            ptsUs - lastKeyframePtsUs
                    );
                }
                lastKeyframePtsUs = ptsUs;
            }
        } else {
            if (firstAudioPtsUs == Long.MIN_VALUE) firstAudioPtsUs = ptsUs;
            if (lastAudioPtsUs != Long.MIN_VALUE && ptsUs < lastAudioPtsUs) {
                nonMonotonicAudioPts++;
            }
            lastAudioPtsUs = Math.max(lastAudioPtsUs, ptsUs);
        }
    }

    private void logSummary() {
        long startDeltaUs = hasAudioVideoPts() ? firstAudioPtsUs - firstVideoPtsUs : Long.MIN_VALUE;
        long endDeltaUs = hasAudioVideoPts() ? lastAudioPtsUs - lastVideoPtsUs : Long.MIN_VALUE;
        long videoDurationUs = durationUs(firstVideoPtsUs, lastVideoPtsUs);
        long audioDurationUs = durationUs(firstAudioPtsUs, lastAudioPtsUs);
        diagnostics.log("codec summary: videoBuffers=" + videoBuffers
                + ", videoBytes=" + videoBytes
                + ", videoPts=" + ptsRange(firstVideoPtsUs, lastVideoPtsUs)
                + ", actualVideoBitrate=" + bitrate(videoBytes, videoDurationUs)
                + ", keyframes=" + videoKeyframes
                + ", maxKeyframeIntervalUs=" + maximumKeyframeIntervalUs
                + ", nonMonotonicVideoPts=" + nonMonotonicVideoPts
                + ", audioBuffers=" + audioBuffers
                + ", audioBytes=" + audioBytes
                + ", audioPts=" + ptsRange(firstAudioPtsUs, lastAudioPtsUs)
                + ", actualAudioBitrate=" + bitrate(audioBytes, audioDurationUs)
                + ", nonMonotonicAudioPts=" + nonMonotonicAudioPts
                + ", avStartDeltaUs=" + optionalValue(startDeltaUs)
                + ", avEndDeltaUs=" + optionalValue(endDeltaUs)
                + ", audioReadErrors=" + audioReadErrors
                + ", emptyAudioReads=" + emptyAudioReads
                + ", droppedAudioBuffers=" + droppedAudioBuffers
                + ", droppedAudioAfterStop=" + droppedAudioAfterStop
                + ", droppedVideoAfterStop=" + droppedVideoAfterStop
                + ", stopPresentationTimeUs="
                + (stopPresentationTimeUs == Long.MAX_VALUE
                ? "n/a" : stopPresentationTimeUs)
                + ", alignedAudioStartDeltaUs="
                + optionalValue(alignedAudioStartDeltaUs)
                + ", codecLatencyAvgMs="
                + (latencySamples == 0 ? "n/a"
                : latencyTotalNs / (float) latencySamples / 1_000_000f)
                + ", codecLatencyMaxMs=" + latencyMaxNs / 1_000_000f);
    }

    private boolean hasAudioVideoPts() {
        return firstVideoPtsUs != Long.MIN_VALUE
                && lastVideoPtsUs != Long.MIN_VALUE
                && firstAudioPtsUs != Long.MIN_VALUE
                && lastAudioPtsUs != Long.MIN_VALUE;
    }

    @NonNull
    private static String ptsRange(long firstPtsUs, long lastPtsUs) {
        return firstPtsUs == Long.MIN_VALUE ? "n/a" : firstPtsUs + ".." + lastPtsUs;
    }

    @NonNull
    private static String optionalValue(long value) {
        return value == Long.MIN_VALUE ? "n/a" : String.valueOf(value);
    }

    private static long durationUs(long firstPtsUs, long lastPtsUs) {
        return firstPtsUs == Long.MIN_VALUE || lastPtsUs <= firstPtsUs
                ? 0
                : lastPtsUs - firstPtsUs;
    }

    @NonNull
    private static String bitrate(long bytes, long durationUs) {
        return durationUs <= 0 ? "n/a" : String.valueOf(bytes * 8_000_000L / durationUs);
    }

    @NonNull
    private static String codecDiagnostic(@NonNull Exception error) {
        if (error instanceof MediaCodec.CodecException) {
            MediaCodec.CodecException codecError = (MediaCodec.CodecException) error;
            return ": diagnostic=" + codecError.getDiagnosticInfo()
                    + ", recoverable=" + codecError.isRecoverable()
                    + ", transient=" + codecError.isTransient();
        }
        return "";
    }

    private static long elapsedMs(long startedNs) {
        return (System.nanoTime() - startedNs) / 1_000_000L;
    }

    private void release() {
        prepared = false;
        started = false;
        audioReadyListener = null;
        videoReadyListener = null;
        AudioInputPreference.unregisterReopener(inputReopener);
        if (audioRouteMonitor != null) { audioRouteMonitor.stop(); audioRouteMonitor = null; }
        if (audioRecord != null) { AudioInputPreference.unregister(audioRecord); audioRecord.release(); audioRecord = null; }
        if (audioRouteSession != null) { audioRouteSession.release(); audioRouteSession = null; }
        if (videoInputSurface != null) { videoInputSurface.release(); videoInputSurface = null; }
        releaseCodec(videoCodec);
        releaseCodec(audioCodec);
        videoCodec = audioCodec = null;
        videoDrainThread = audioThread = null;
    }

    private static long framesToDurationUs(long frames) { return frames * 1_000_000L / AUDIO_SAMPLE_RATE; }
    private static boolean isMediaBuffer(MediaCodec.BufferInfo info) { return info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0; }

    private static void releaseCodec(MediaCodec codec) {
        if (codec == null) return;
        try {
            codec.stop();
        } catch (IllegalStateException e) {
            FileLog.d("codec stop skipped: " + e.getMessage());
        }
        codec.release();
    }

    private void join(Thread thread, long timeoutMs) {
        if (thread == null) return;
        try {
            thread.join(timeoutMs);
            if (thread.isAlive()) diagnostics.log("encoder thread did not stop: " + thread.getName());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static final class PendingAudioSample {
        final ByteBuffer data;
        final MediaCodec.BufferInfo info;

        PendingAudioSample(ByteBuffer data, MediaCodec.BufferInfo info) {
            this.data = data;
            this.info = info;
        }
    }

}
