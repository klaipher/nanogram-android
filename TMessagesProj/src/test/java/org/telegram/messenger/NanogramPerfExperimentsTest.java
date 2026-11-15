package org.telegram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

public class NanogramPerfExperimentsTest {

    @Before
    public void reset() {
        NanogramPerfStats.reset();
    }

    @Test
    public void everyExperimentHasOwnIdAndText() {
        Set<String> ids = new HashSet<>();
        for (NanogramPerfExperiments.Experiment e : NanogramPerfExperiments.Experiment.values()) {
            assertTrue(ids.add(e.id));
            assertFalse(e.title.isEmpty());
            assertFalse(e.description.isEmpty());
            assertFalse(e.applyHint.isEmpty());
        }
        assertEquals(8, ids.size());
    }

    @Test
    public void sdkGates() {
        assertFalse(NanogramPerfExperiments.Experiment.ADAPTIVE_REFRESH.isSupported(34));
        assertTrue(NanogramPerfExperiments.Experiment.ADAPTIVE_REFRESH.isSupported(35));
        assertFalse(NanogramPerfExperiments.Experiment.TELECOM.isSupported(33));
        assertTrue(NanogramPerfExperiments.Experiment.TELECOM.isSupported(34));
        assertTrue(NanogramPerfExperiments.Experiment.CAMERA2.isSupported(23));
    }

    @Test
    public void statKeysMatchMeasurementPoints() {
        assertEquals(NanogramPerfStats.FRAME_TIME, NanogramPerfExperiments.Experiment.ADAPTIVE_REFRESH.statKeys[0]);
        assertEquals(NanogramPerfStats.CALL_SETUP, NanogramPerfExperiments.Experiment.TELECOM.statKeys[1]);
        assertEquals(NanogramPerfStats.CAMERA_FIRST_FRAME, NanogramPerfExperiments.Experiment.CAMERA2.statKeys[0]);
        assertEquals(NanogramPerfStats.IMAGE_UPLOAD, NanogramPerfExperiments.Experiment.GPU_PREUPLOAD.statKeys[0]);
        assertEquals(NanogramPerfStats.GIF_FRAME_DECODE, NanogramPerfExperiments.Experiment.GIF_FAST_DECODE.statKeys[0]);
        assertEquals(0, NanogramPerfExperiments.Experiment.DB_TUNING.statKeys.length);
        assertEquals(NanogramPerfStats.TELECOM_CALLS, NanogramPerfExperiments.Experiment.TELECOM.statKeys[2]);
        assertEquals(NanogramPerfStats.CAMERA2_OPENED, NanogramPerfExperiments.Experiment.CAMERA2.statKeys[1]);
        assertEquals(0, NanogramPerfExperiments.Experiment.PROGRESS_NOTIFICATIONS.statKeys.length);
        assertEquals(0, NanogramPerfExperiments.Experiment.SYSTEM_PHOTO_PICKER.statKeys.length);
    }

    @Test
    public void sdkGatesOfMovedExperiments() {
        assertFalse(NanogramPerfExperiments.Experiment.PROGRESS_NOTIFICATIONS.isSupported(35));
        assertTrue(NanogramPerfExperiments.Experiment.PROGRESS_NOTIFICATIONS.isSupported(36));
        assertFalse(NanogramPerfExperiments.Experiment.SYSTEM_PHOTO_PICKER.isSupported(32));
        assertTrue(NanogramPerfExperiments.Experiment.SYSTEM_PHOTO_PICKER.isSupported(33));
    }

    @Test
    public void countersAreReportedAsTimes() {
        NanogramPerfStats.count(NanogramPerfStats.CAMERA2_OPENED);
        NanogramPerfStats.count(NanogramPerfStats.CAMERA2_OPENED);
        assertEquals("camera2Opened: 2 times", NanogramPerfStats.formatLine(NanogramPerfStats.CAMERA2_OPENED, NanogramPerfStats.snapshot().get(NanogramPerfStats.CAMERA2_OPENED)));
        assertEquals("telecomCalls: 0 times", NanogramPerfStats.formatLine(NanogramPerfStats.TELECOM_CALLS, null));
    }

    @Test
    public void deviceLine() {
        assertEquals("Google Pixel 8, SoC Tensor G3, Android 15 (API 35)", NanogramPerfExperiments.deviceLine("Google", "Pixel 8", "Tensor G3", "15", 35));
        assertEquals("Google Pixel 4, SoC unknown, Android 13 (API 33)", NanogramPerfExperiments.deviceLine("Google", "Pixel 4", null, "13", 33));
        assertEquals("X Y, SoC unknown, Android 12 (API 31)", NanogramPerfExperiments.deviceLine("X", "Y", "unknown", "12", 31));
    }

    @Test
    public void reportShowsStatesNumbersAndUnavailable() {
        NanogramPerfStats.record(NanogramPerfStats.GIF_FRAME_DECODE, 3_000_000L);
        EnumMap<NanogramPerfExperiments.Experiment, Boolean> states = new EnumMap<>(NanogramPerfExperiments.Experiment.class);
        states.put(NanogramPerfExperiments.Experiment.GIF_FAST_DECODE, true);
        String report = NanogramPerfExperiments.report("Google Pixel 8", 33, states, NanogramPerfStats.snapshot());
        assertTrue(report.contains("device: Google Pixel 8\n"));
        assertTrue(report.contains("Lighter decoding for small GIFs: ON\n  gifFrameDecode: n=1, average 3.0 ms, longest 3.0 ms\n"));
        assertTrue(report.contains("Adaptive refresh rate: not available (needs API 35)\n"));
        assertTrue(report.contains("System call integration: not available (needs API 34)\n"));
        assertTrue(report.contains("Camera2 capture: off\n  cameraFirstFrame: no samples\n"));
        assertTrue(report.contains("Database tuning: off\n  no measured numbers"));
    }
}
