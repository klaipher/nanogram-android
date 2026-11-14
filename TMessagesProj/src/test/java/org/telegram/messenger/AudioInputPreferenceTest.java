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

    @Test
    public void quickPairPicksPhoneAndExternal() {
        assertArrayEquals(new int[]{BUILTIN, WIRED}, AudioInputPreference.quickPair(new int[]{BUILTIN, WIRED}, false));
        assertArrayEquals(new int[]{BUILTIN, BLE}, AudioInputPreference.quickPair(new int[]{BUILTIN, BT_SCO, BLE}, true));
        assertArrayEquals(new int[]{BUILTIN, BT_SCO}, AudioInputPreference.quickPair(new int[]{BUILTIN, BT_SCO}, true));
        assertArrayEquals(new int[0], AudioInputPreference.quickPair(new int[]{BUILTIN, BT_SCO}, false));
        assertArrayEquals(new int[0], AudioInputPreference.quickPair(new int[]{BUILTIN}, true));
        assertArrayEquals(new int[0], AudioInputPreference.quickPair(new int[]{WIRED}, true));
        assertArrayEquals(new int[]{BUILTIN, WIRED}, AudioInputPreference.quickPair(new int[]{TELEPHONY, 99, BUILTIN, WIRED}, true));
    }

    @Test
    public void toggleGoesBothWays() {
        final int[] pair = {BUILTIN, BLE};
        assertEquals(BLE, AudioInputPreference.toggle(BUILTIN, pair));
        assertEquals(BUILTIN, AudioInputPreference.toggle(BLE, pair));
        assertEquals(BLE, AudioInputPreference.toggle(AudioInputPreference.AUTOMATIC, pair));
        // An external input outside the pair (a headset that fell back to the other link) goes back to the phone.
        assertEquals(BUILTIN, AudioInputPreference.toggle(USB_HEADSET, pair));
        assertEquals(BUILTIN, AudioInputPreference.toggle(BT_SCO, pair));
    }

    @Test
    public void switchAvailableMatrix() {
        final int[] both = {BUILTIN, WIRED};
        assertTrue(AudioInputPreference.switchAvailable(true, false, both, false));
        assertFalse(AudioInputPreference.switchAvailable(false, false, both, false));
        assertFalse(AudioInputPreference.switchAvailable(true, true, both, false));
        assertFalse(AudioInputPreference.switchAvailable(true, false, new int[]{BUILTIN}, true));
        assertFalse(AudioInputPreference.switchAvailable(true, false, new int[]{BUILTIN, BT_SCO}, false));
        assertTrue(AudioInputPreference.switchAvailable(true, false, new int[]{BUILTIN, BT_SCO}, true));
    }

    @Test
    public void overrideWinsWhileConnected() {
        final int[] connected = {BUILTIN, WIRED};
        assertEquals(BUILTIN, AudioInputPreference.resolveWithOverride(WIRED, BUILTIN, connected));
        assertEquals(WIRED, AudioInputPreference.resolveWithOverride(AudioInputPreference.AUTOMATIC, WIRED, connected));
        assertEquals(WIRED, AudioInputPreference.resolveWithOverride(WIRED, BLE, connected));
        assertEquals(AudioInputPreference.AUTOMATIC, AudioInputPreference.resolveWithOverride(BLE, BLE, connected));
        assertEquals(AudioInputPreference.AUTOMATIC, AudioInputPreference.resolveWithOverride(WIRED, AudioInputPreference.AUTOMATIC, connected));
        assertEquals(WIRED, AudioInputPreference.resolveWithOverride(WIRED, AudioInputPreference.NONE, connected));
    }
}
