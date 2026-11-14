package org.telegram.messenger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AudioInputPreferenceTest {

    private static final int BUILTIN = 15;
    private static final int WIRED = 3;
    private static final int USB_HEADSET = 22;
    private static final int BT_SCO = 7;
    private static final int BLE = 26;
    private static final int TELEPHONY = 18;

    @Test
    public void legacyScoSettingMigrates() {
        assertEquals(BT_SCO, AudioInputPreference.migrateLegacy(true));
        assertEquals(AudioInputPreference.AUTOMATIC, AudioInputPreference.migrateLegacy(false));
    }

    @Test
    public void unknownStoredValuesBecomeAutomatic() {
        assertEquals(AudioInputPreference.AUTOMATIC, AudioInputPreference.sanitize(TELEPHONY));
        assertEquals(AudioInputPreference.AUTOMATIC, AudioInputPreference.sanitize(-5));
        assertEquals(WIRED, AudioInputPreference.sanitize(WIRED));
    }

    @Test
    public void choicesListOnlyConnectedSupportedInputsInFixedOrder() {
        assertArrayEquals(new int[]{BUILTIN, WIRED, BT_SCO}, AudioInputPreference.choices(new int[]{BT_SCO, TELEPHONY, WIRED, BUILTIN, BT_SCO}));
        assertArrayEquals(new int[]{BUILTIN, USB_HEADSET, BLE}, AudioInputPreference.choices(new int[]{BLE, USB_HEADSET, BUILTIN}));
        assertEquals(0, AudioInputPreference.choices(new int[0]).length);
        assertEquals(0, AudioInputPreference.choices(null).length);
    }

    @Test
    public void missingChosenDeviceFallsBackToAutomatic() {
        assertEquals(AudioInputPreference.AUTOMATIC, AudioInputPreference.resolve(WIRED, new int[]{BUILTIN}));
        assertEquals(WIRED, AudioInputPreference.resolve(WIRED, new int[]{BUILTIN, WIRED}));
        assertEquals(AudioInputPreference.AUTOMATIC, AudioInputPreference.resolve(AudioInputPreference.AUTOMATIC, new int[]{BUILTIN}));
        assertEquals(AudioInputPreference.AUTOMATIC, AudioInputPreference.resolve(BT_SCO, null));
    }

    @Test
    public void automaticNeverRoutesAndCallsBlockRouting() {
        assertFalse(AudioInputPreference.shouldRoute(AudioInputPreference.AUTOMATIC, false));
        assertFalse(AudioInputPreference.shouldRoute(BT_SCO, true));
        assertTrue(AudioInputPreference.shouldRoute(BT_SCO, false));
    }

    @Test
    public void bluetoothTypes() {
        assertTrue(AudioInputPreference.isBluetoothType(BT_SCO));
        assertTrue(AudioInputPreference.isBluetoothType(BLE));
        assertFalse(AudioInputPreference.isBluetoothType(WIRED));
    }
}
