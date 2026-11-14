package org.telegram.messenger;

import android.Manifest;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;

import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.Components.PermissionRequest;

import java.util.ArrayList;

/**
 * Which microphone voice messages and round videos record from. 0 = automatic (the system default, as upstream);
 * any other value is an AudioDeviceInfo.TYPE_* of an input device. The choice is a hint: when that device is not
 * connected the recording falls back to automatic.
 */
public final class AudioInputPreference {

    public static final int AUTOMATIC = 0;

    private static final int SETTLE_WAIT_MS = 300;
    private static final int[] CHOICE_ORDER = {
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET
    };

    private static boolean communicationDeviceSet;
    private static boolean legacyScoStarted;

    private AudioInputPreference() {
    }

    public static boolean isSupportedType(int type) {
        for (int supported : CHOICE_ORDER) {
            if (supported == type) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBluetoothType(int type) {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET;
    }

    public static int migrateLegacy(boolean recordViaSco) {
        return recordViaSco ? AudioDeviceInfo.TYPE_BLUETOOTH_SCO : AUTOMATIC;
    }

    public static int sanitize(int stored) {
        return isSupportedType(stored) ? stored : AUTOMATIC;
    }

    public static int resolve(int stored, int[] connectedTypes) {
        if (!isSupportedType(stored) || connectedTypes == null) {
            return AUTOMATIC;
        }
        for (int type : connectedTypes) {
            if (type == stored) {
                return stored;
            }
        }
        return AUTOMATIC;
    }

    public static int[] choices(int[] connectedTypes) {
        ArrayList<Integer> result = new ArrayList<>();
        if (connectedTypes != null) {
            for (int type : CHOICE_ORDER) {
                for (int connected : connectedTypes) {
                    if (connected == type) {
                        result.add(type);
                        break;
                    }
                }
            }
        }
        int[] array = new int[result.size()];
        for (int a = 0; a < array.length; a++) {
            array[a] = result.get(a);
        }
        return array;
    }

    public static boolean shouldRoute(int resolved, boolean callActive) {
        return resolved != AUTOMATIC && !callActive;
    }

    public static int[] connectedInputTypes() {
        try {
            final AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
            final AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS);
            final int[] types = new int[devices.length];
            for (int a = 0; a < devices.length; a++) {
                types[a] = devices[a].getType();
            }
            return types;
        } catch (Throwable e) {
            FileLog.e(e);
            return new int[0];
        }
    }

    public static int resolvedChoice() {
        final int stored = SharedConfig.recordInputDevice;
        if (stored == AUTOMATIC) {
            return AUTOMATIC;
        }
        if (isBluetoothType(stored) && !PermissionRequest.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return AUTOMATIC;
        }
        return resolve(stored, connectedInputTypes());
    }

    private static AudioDeviceInfo findInput(AudioManager am, int type) {
        for (AudioDeviceInfo device : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (device.getType() == type) {
                return device;
            }
        }
        return null;
    }

    public static void applyTo(AudioRecord record) {
        if (record == null) {
            return;
        }
        try {
            final int choice = resolvedChoice();
            if (!shouldRoute(choice, VoIPService.isAnyKindOfCallActive())) {
                return;
            }
            final AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
            final AudioDeviceInfo device = findInput(am, choice);
            if (device != null) {
                final boolean accepted = record.setPreferredDevice(device);
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("AudioInputPreference preferred input type " + choice + " accepted " + accepted);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void logRoutedDevice(AudioRecord record) {
        if (!BuildVars.LOGS_ENABLED || record == null) {
            return;
        }
        try {
            final AudioDeviceInfo routed = record.getRoutedDevice();
            FileLog.d("AudioInputPreference routed input " + (routed == null ? "none" : routed.getType()));
        } catch (Throwable ignore) {
        }
    }

    public static synchronized void setBluetoothRouting(boolean on) {
        final AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return;
        }
        try {
            if (on) {
                final int choice = resolvedChoice();
                if (!shouldRoute(choice, VoIPService.isAnyKindOfCallActive()) || !isBluetoothType(choice)) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    for (AudioDeviceInfo device : am.getAvailableCommunicationDevices()) {
                        if (device.getType() == choice) {
                            if (am.setCommunicationDevice(device)) {
                                communicationDeviceSet = true;
                                waitForRoute(am, device);
                            }
                            return;
                        }
                    }
                } else if (choice == AudioDeviceInfo.TYPE_BLUETOOTH_SCO && am.isBluetoothScoAvailableOffCall() && !am.isBluetoothScoOn()) {
                    am.startBluetoothSco();
                    legacyScoStarted = true;
                    waitForRoute(am, null);
                }
            } else {
                if (communicationDeviceSet) {
                    communicationDeviceSet = false;
                    if (Build.VERSION.SDK_INT >= 31) {
                        am.clearCommunicationDevice();
                    }
                }
                if (legacyScoStarted) {
                    legacyScoStarted = false;
                    if (am.isBluetoothScoOn()) {
                        am.stopBluetoothSco();
                    }
                }
            }
        } catch (SecurityException ignored) {
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void waitForRoute(AudioManager am, AudioDeviceInfo device) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return;
        }
        final long end = SystemClock.elapsedRealtime() + SETTLE_WAIT_MS;
        while (SystemClock.elapsedRealtime() < end) {
            final boolean ready = device != null ? Build.VERSION.SDK_INT >= 31 && am.getCommunicationDevice() != null && am.getCommunicationDevice().getId() == device.getId() : am.isBluetoothScoOn();
            if (ready) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
