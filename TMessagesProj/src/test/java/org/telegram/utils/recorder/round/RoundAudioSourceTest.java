package org.telegram.utils.recorder.round;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.media.MediaRecorder;

import org.junit.Test;

public class RoundAudioSourceTest {

    private static final int BUILTIN = 15;
    private static final int WIRED = 3;
    private static final int USB_HEADSET = 22;
    private static final int BT_SCO = 7;
    private static final int BLE = 26;

    @Test
    public void bluetoothChoiceUsesDefault() {
        assertEquals(MediaRecorder.AudioSource.DEFAULT, RoundAudioSource.forChoice(BT_SCO));
        assertEquals(MediaRecorder.AudioSource.DEFAULT, RoundAudioSource.forChoice(BLE));
    }

    @Test
    public void otherChoicesKeepCamcorder() {
        assertEquals(MediaRecorder.AudioSource.CAMCORDER, RoundAudioSource.forChoice(-1));
        assertEquals(MediaRecorder.AudioSource.CAMCORDER, RoundAudioSource.forChoice(BUILTIN));
        assertEquals(MediaRecorder.AudioSource.CAMCORDER, RoundAudioSource.forChoice(WIRED));
        assertEquals(MediaRecorder.AudioSource.CAMCORDER, RoundAudioSource.forChoice(USB_HEADSET));
    }

    @Test
    public void routeWaitFitsInsideTheCommonStartTimeout() {
        // the first open has one deadline (both route attempts + AudioRecord start); the A/V start timeout must leave >= 1.5 s after it
        assertTrue(RoundAudioSource.OPEN_BUDGET_MS + 1500 <= RoundVideoCameraController.COMMON_START_TIMEOUT_MS);
        // a single attempt plus its half-length retry, times the LE-to-SCO second try, is cut off by that deadline
        assertTrue(RoundAudioSource.ROUTE_WAIT_MS > 0);
    }
}
