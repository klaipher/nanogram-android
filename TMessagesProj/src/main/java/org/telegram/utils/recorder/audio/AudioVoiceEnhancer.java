package org.telegram.utils.recorder.audio;

import android.media.AudioDeviceInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one native voice enhancement chain (tgcalls fork voice_enhance/, JNI glue jni/voice_enhance_jni.cc): 48 kHz mono 16-bit PCM in, PCM out. */
public final class AudioVoiceEnhancer {

    public static final int PROFILE_NONE = 0;
    public static final int PROFILE_HEADSET = 1;
    public static final int PROFILE_PHONE_FAR = 2;
    public static final int PROFILE_ANALYZE = 3;

    public static final int CLASS_UNKNOWN = 0;
    public static final int CLASS_NARROW = 1;
    public static final int CLASS_WIDE = 2;
    public static final int CLASS_SUPER_WIDE = 3;

    public static final int LEVEL_BYPASS = 0;
    public static final int LEVEL_REDUCED = 1;
    public static final int LEVEL_FULL = 2;

    // OPUS_BANDWIDTH_* values.
    public static final int OPUS_NARROWBAND = 1101;
    public static final int OPUS_WIDEBAND = 1103;
    public static final int OPUS_SUPERWIDEBAND = 1104;
    public static final int OPUS_FULLBAND = 1105;

    private long nativeHandle;
    private final int profile;
    private final AtomicBoolean released = new AtomicBoolean(false);

    private AudioVoiceEnhancer(long nativeHandle, int profile) {
        this.nativeHandle = nativeHandle;
        this.profile = profile;
    }

    /**
     * What to do for the input that really records. Bluetooth microphones get the headset chain (or only the
     * measurement while the switch is off), the built-in microphone gets the far-microphone chain when the switch is on,
     * wired and USB microphones are never touched.
     *
     * @param routedType AudioDeviceInfo type of the routed input, or a negative value when unknown (treated as the phone)
     */
    public static int profileFor(boolean enabled, int routedType) {
        if (isBluetooth(routedType)) {
            return enabled ? PROFILE_HEADSET : PROFILE_ANALYZE;
        }
        if (enabled && (routedType == AudioDeviceInfo.TYPE_BUILTIN_MIC || routedType < 0)) {
            return PROFILE_PHONE_FAR;
        }
        return PROFILE_NONE;
    }

    public static boolean isBluetooth(int type) {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET;
    }

    /** Opus bandwidth limit for a route: nothing is above what the radio carries, so no bits go to empty bands. */
    public static int opusMaxBandwidth(int routedType, @Nullable int[] sampleRates, int measuredClass, boolean extending) {
        if (routedType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            // Super-wideband HFP headsets (LC3-SWB) also route as SCO: keep what they carry.
            if (measuredClass == CLASS_SUPER_WIDE) return OPUS_SUPERWIDEBAND;
            return measuredClass == CLASS_NARROW && !extending ? OPUS_NARROWBAND : OPUS_WIDEBAND;
        }
        if (routedType == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            if (sampleRates != null) {
                for (int rate : sampleRates) {
                    if (rate >= 44_100) return OPUS_FULLBAND;
                }
            }
            return OPUS_SUPERWIDEBAND;
        }
        return OPUS_FULLBAND;
    }

    public static @Nullable AudioVoiceEnhancer create(int profile, int initialClass, boolean useRnnoise, boolean dropLatency) {
        if (profile == PROFILE_NONE) return null;
        long handle;
        try {
            handle = nCreate(profile, initialClass, useRnnoise, dropLatency);
        } catch (UnsatisfiedLinkError error) {
            return null;
        }
        return handle == 0L ? null : new AudioVoiceEnhancer(handle, profile);
    }

    /** True when the chain changes the audio (the analysis-only profile does not). */
    public boolean modifiesAudio() {
        return profile != PROFILE_ANALYZE;
    }

    public int profile() {
        return profile;
    }

    /**
     * Processes sampleCount samples in place.
     *
     * @return the number of samples now at the start of the buffer, or a negative value on failure
     */
    public int process(@NonNull ByteBuffer pcm, int sampleCount) {
        checkNotReleased();
        return nProcess(nativeHandle, pcm, sampleCount);
    }

    /** Samples still inside the chain at the end of a recording (only when created with dropLatency). */
    public int flush(@NonNull ByteBuffer pcm, int maxSamples) {
        checkNotReleased();
        return nFlush(nativeHandle, pcm, maxSamples);
    }

    public int delaySamples() {
        return released.get() ? 0 : nDelaySamples(nativeHandle);
    }

    public int bandwidthClass() {
        return released.get() ? CLASS_UNKNOWN : nGetClass(nativeHandle);
    }

    public int level() {
        return released.get() ? LEVEL_BYPASS : nGetLevel(nativeHandle);
    }

    /** Only lowers the level. */
    public void limitLevel(int level) {
        if (!released.get()) nSetLevel(nativeHandle, level);
    }

    public int takeAverageBlockMicros() {
        return released.get() ? 0 : nTakeAverageBlockMicros(nativeHandle);
    }

    public static String className(int bandwidthClass) {
        switch (bandwidthClass) {
            case CLASS_NARROW: return "narrowband(~4kHz)";
            case CLASS_WIDE: return "wideband(~8kHz)";
            case CLASS_SUPER_WIDE: return "wider";
            default: return "unknown";
        }
    }

    public void recycle() {
        if (!released.compareAndSet(false, true)) return;
        long handle = nativeHandle;
        nativeHandle = 0L;
        if (handle != 0L) nDestroy(handle);
    }

    private void checkNotReleased() {
        if (released.get()) throw new IllegalStateException("Native voice enhancer is already released");
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            recycle();
        } finally {
            super.finalize();
        }
    }

    private static native long nCreate(int profile, int initialClass, boolean useRnnoise, boolean dropLatency);

    private static native int nProcess(long handle, ByteBuffer pcm, int sampleCount);

    private static native int nFlush(long handle, ByteBuffer pcm, int maxSamples);

    private static native int nDelaySamples(long handle);

    private static native int nGetClass(long handle);

    private static native int nGetLevel(long handle);

    private static native void nSetLevel(long handle, int level);

    private static native int nTakeAverageBlockMicros(long handle);

    private static native void nDestroy(long handle);
}
