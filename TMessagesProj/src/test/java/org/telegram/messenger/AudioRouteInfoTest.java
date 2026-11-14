package org.telegram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AudioRouteInfoTest {

    private static final int BUILTIN = 15;
    private static final int SCO = 7;
    private static final int BLE = 26;

    @Test
    public void stateShowsTheActualRoute() {
        assertEquals(AudioRouteInfo.State.PHONE, AudioRouteInfo.state(0, BUILTIN));
        assertEquals(AudioRouteInfo.State.PHONE, AudioRouteInfo.state(BUILTIN, BUILTIN));
        assertEquals(AudioRouteInfo.State.HEADSET_CALL_QUALITY, AudioRouteInfo.state(SCO, SCO));
        assertEquals(AudioRouteInfo.State.HEADSET_LE, AudioRouteInfo.state(BLE, BLE));
        assertEquals(AudioRouteInfo.State.FELL_BACK, AudioRouteInfo.state(SCO, BUILTIN));
        assertEquals(AudioRouteInfo.State.FELL_BACK, AudioRouteInfo.state(BLE, AudioRouteSession.NO_ROUTE));
    }

    @Test
    public void codecClassFromTypeAndRates() {
        assertEquals("CVSD (narrowband)", AudioRouteInfo.codecClass(SCO, new int[]{8000}));
        assertEquals("mSBC (wideband)", AudioRouteInfo.codecClass(SCO, new int[]{8000, 16000}));
        assertTrue(AudioRouteInfo.codecClass(SCO, new int[0]).contains("not reported"));
        assertTrue(AudioRouteInfo.codecClass(SCO, null).contains("not reported"));
        assertTrue(AudioRouteInfo.codecClass(BLE, new int[0]).contains("LC3"));
        assertEquals("PCM (no Bluetooth codec)", AudioRouteInfo.codecClass(BUILTIN, new int[]{48000}));
        assertEquals("unknown", AudioRouteInfo.codecClass(AudioRouteSession.NO_ROUTE, null));
    }

    @Test
    public void classicHeadsetUpgradesToLeAudioWhenOneIsConnected() {
        assertEquals(BLE, AudioRouteInfo.preferLowEnergy(SCO, new int[]{BUILTIN, SCO, BLE}));
        assertEquals(BLE, AudioRouteInfo.preferLowEnergy(SCO, new int[]{BUILTIN, BLE}));
        assertEquals(SCO, AudioRouteInfo.preferLowEnergy(SCO, new int[]{BUILTIN, SCO}));
        assertEquals(BUILTIN, AudioRouteInfo.preferLowEnergy(BUILTIN, new int[]{BUILTIN, BLE}));
        assertEquals(0, AudioRouteInfo.preferLowEnergy(0, new int[]{BLE}));
        assertEquals(SCO, AudioRouteInfo.preferLowEnergy(SCO, null));
    }

    @Test
    public void formatNamesRouteRatesAndCodec() {
        final String line = AudioRouteInfo.format("voice-v2", SCO, "ROUTED", SCO, new int[]{8000, 16000}, 48000, 420);
        assertTrue(line, line.contains("recorder=voice-v2"));
        assertTrue(line, line.contains("requested=BLUETOOTH_SCO(7)"));
        assertTrue(line, line.contains("acquire=ROUTED (420 ms)"));
        assertTrue(line, line.contains("deviceRates=[8000,16000]"));
        assertTrue(line, line.contains("captureRate=48000"));
        assertTrue(line, line.contains("codec=mSBC (wideband)"));
        assertTrue(line, line.contains("state=HEADSET_CALL_QUALITY"));
    }

    @Test
    public void reportKeepsTheLatestLines() {
        AudioRouteReport.clear();
        for (int a = 0; a < AudioRouteReport.MAX_LINES + 5; a++) {
            AudioRouteReport.add(1_700_000_000_000L + a, "line" + a);
        }
        final String text = AudioRouteReport.describe();
        assertTrue(text.contains("line" + (AudioRouteReport.MAX_LINES + 4)));
        assertTrue(!text.contains("line0\n"));
        AudioRouteReport.clear();
        assertTrue(AudioRouteReport.describe().contains("no recording since the app started"));
    }
}
