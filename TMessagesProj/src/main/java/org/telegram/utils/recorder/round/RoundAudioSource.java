package org.telegram.utils.recorder.round;

import android.media.MediaRecorder;

import org.telegram.messenger.AudioInputPreference;

/** Which AudioRecord source round video V2 records from. */
final class RoundAudioSource {

    /** Wait for the Bluetooth route on the audio thread; the common A/V start timeout must stay above this plus the retry (half). */
    static final long ROUTE_WAIT_MS = 1500L;

    /** One deadline for the whole first open (both route attempts plus AudioRecord start); must leave room inside the common A/V start timeout. */
    static final long OPEN_BUDGET_MS = 2500L;

    private RoundAudioSource() {
    }

    /**
     * CAMCORDER (tuned for the microphone facing the camera) for the built-in microphone; DEFAULT with a Bluetooth
     * choice, because on some HALs CAMCORDER forces the built-in microphone whatever device is preferred.
     */
    /** DEFAULT only when the Bluetooth route is really active; a failed route records on the built-in mic with CAMCORDER. */
    static int forSession(org.telegram.messenger.AudioRouteSession session) {
        return session.outcome() == org.telegram.messenger.AudioRouteSession.Outcome.ROUTED
                ? forChoice(session.choice())
                : MediaRecorder.AudioSource.CAMCORDER;
    }

    static int forChoice(int resolvedChoice) {
        return AudioInputPreference.isBluetoothType(resolvedChoice)
                ? MediaRecorder.AudioSource.DEFAULT
                : MediaRecorder.AudioSource.CAMCORDER;
    }
}
