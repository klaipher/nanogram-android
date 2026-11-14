package org.telegram.messenger;

import android.media.AudioDeviceInfo;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;

import java.util.function.Consumer;

/**
 * Checks, after an AudioRecord started, that the capture really routes to the chosen input (setPreferredDevice only
 * says the device is a valid input). Re-applies the preference once, reports every routing change and writes the
 * route, sample rates and codec class of the recording to the log and to Send Report. Runs on the main looper, so any
 * recorder thread can use it; AudioRecord calls are guarded because the record may be released at any time.
 */
public final class AudioRouteMonitor implements AudioRecord.OnRoutingChangedListener {

    private static final long CHECK_DELAY_MS = 300L;

    private final String recorder;
    private final AudioRecord record;
    private final AudioRouteSession session;
    private final int captureRate;
    private final Consumer<String> sink;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean stopped;
    private int checks;
    private int lastRoutedType = AudioRouteSession.NO_ROUTE;
    private int[] lastRates = new int[0];
    private volatile AudioRouteInfo.State state = AudioRouteInfo.State.PHONE;

    private AudioRouteMonitor(String recorder, AudioRecord record, AudioRouteSession session, int captureRate, Consumer<String> sink) {
        this.recorder = recorder;
        this.record = record;
        this.session = session;
        this.captureRate = captureRate;
        this.sink = sink;
    }

    /** Call right after startRecording(). The returned monitor must be stopped before the record is released. */
    public static AudioRouteMonitor start(String recorder, AudioRecord record, AudioRouteSession session, int captureRate, Consumer<String> sink) {
        final AudioRouteMonitor monitor = new AudioRouteMonitor(recorder, record, session, captureRate, sink);
        monitor.begin();
        return monitor;
    }

    private void begin() {
        try {
            record.addOnRoutingChangedListener(this, handler);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        emit("started " + describe());
        handler.postDelayed(this::check, CHECK_DELAY_MS);
    }

    public AudioRouteInfo.State state() {
        return state;
    }

    @Override
    public void onRoutingChanged(AudioRecord audioRecord) {
        if (stopped) {
            return;
        }
        final int routed = routedType();
        if (routed != lastRoutedType) {
            emit("routing changed to " + AudioRouteInfo.typeName(routed) + " " + describe());
        }
    }

    private void check() {
        if (stopped) {
            return;
        }
        final int requested = requestedChoice();
        final int routed = routedType();
        checks++;
        if (requested != AudioInputPreference.AUTOMATIC && routed != requested && checks == 1) {
            emit("routed " + AudioRouteInfo.typeName(routed) + " instead of " + AudioRouteInfo.typeName(requested) + ", applying the preferred device again");
            if (stopped) {
                return;
            }
            // Not applyTo: that would register a record the recorder thread may just have released.
            AudioInputPreference.reapplyPreferred(record);
            handler.postDelayed(this::check, CHECK_DELAY_MS);
            return;
        }
        emit((AudioRouteInfo.state(requested, routed) == AudioRouteInfo.State.FELL_BACK ? "fell back " : "checked ") + describe());
    }

    /** What the recording should come from now: the in-recorder choice when one was made, else the choice at start. */
    private int requestedChoice() {
        return AudioInputPreference.hasSessionOverride() ? AudioInputPreference.resolvedChoice() : session.choice();
    }

    private static String overrideSuffix() {
        return AudioInputPreference.hasSessionOverride() ? " override" : "";
    }

    private int routedType() {
        try {
            final AudioDeviceInfo routed = record.getRoutedDevice();
            return routed == null ? AudioRouteSession.NO_ROUTE : routed.getType();
        } catch (Throwable e) {
            return AudioRouteSession.NO_ROUTE;
        }
    }

    private int[] routedRates() {
        try {
            final AudioDeviceInfo routed = record.getRoutedDevice();
            return routed == null ? new int[0] : routed.getSampleRates();
        } catch (Throwable e) {
            return new int[0];
        }
    }

    private String describe() {
        final int routed = routedType();
        lastRoutedType = routed;
        lastRates = routedRates();
        final int requested = requestedChoice();
        state = AudioRouteInfo.state(requested, routed);
        return AudioRouteInfo.format(recorder, requested, String.valueOf(session.outcome()) + overrideSuffix(), routed, lastRates, captureRate, session.acquireMs());
    }

    private void emit(String text) {
        final String line = "route " + text;
        FileLog.i("AudioRoute " + line);
        AudioRouteReport.add(line);
        if (sink != null) {
            sink.accept(line);
        }
        AudioInputPreference.setRouteState(state);
    }

    /** Stop before releasing the record. Writes the last route as the recording's summary. */
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        try {
            record.removeOnRoutingChangedListener(this);
        } catch (Throwable ignored) {
        }
        final String line = "route summary " + AudioRouteInfo.format(recorder, requestedChoice(), String.valueOf(session.outcome()) + overrideSuffix(), lastRoutedType, lastRates, captureRate, session.acquireMs());
        FileLog.i("AudioRoute " + line);
        AudioRouteReport.add(line);
        if (sink != null) {
            sink.accept(line);
        }
    }
}
