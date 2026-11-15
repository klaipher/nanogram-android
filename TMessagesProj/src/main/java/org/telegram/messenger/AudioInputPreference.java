package org.telegram.messenger;

import android.Manifest;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioDeviceCallback;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.Components.PermissionRequest;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Which microphone voice messages and round videos record from. 0 = automatic (the system default, as upstream);
 * any other value is an AudioDeviceInfo.TYPE_* of an input device. The choice is a hint: when that device is not
 * connected the recording falls back to automatic.
 */
public final class AudioInputPreference {

    public static final int AUTOMATIC = 0;

    private static final int[] CHOICE_ORDER = {
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET
    };

    /** No in-recorder choice is active: the stored setting decides. (AUTOMATIC is a valid override: system default.) */
    public static final int NONE = -1;

    public static final int SWITCH_ROUTED = 0;
    /** The request is made but the route did not move in time; the next recorder segment or reopen follows it. */
    public static final int SWITCH_PENDING = 1;
    public static final int SWITCH_FAILED = 2;

    private static final int LIVE_ROUTE_WAIT_MS = 500;

    // Order of the external pick for the in-recorder switch: LE Audio (BLE) is preferred over classic SCO.
    private static final int[] EXTERNAL_ORDER = {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    };

    private static volatile int sessionOverride = NONE;
    private static volatile int switchGeneration;
    /** Bumped when the in-recorder choice ends, so a switch still in flight knows its recording is over. */
    private static final AtomicInteger overrideEpoch = new AtomicInteger();
    /** Own thread: a switch can wait seconds for the Bluetooth route and must not hold up the app-wide global queue. */
    private static final DispatchQueue SWITCH_QUEUE = new DispatchQueue("micSwitch");
    private static final ArrayList<WeakReference<AudioRecord>> LIVE_RECORDS = new ArrayList<>();
    private static final ArrayList<Runnable> INPUT_LISTENERS = new ArrayList<>();
    private static AudioDeviceCallback deviceCallback;

    /**
     * A recorder that can move its capture to another input without losing the recording (the capture thread reopens the
     * AudioRecord, keeps the encoder and fills the gap with silence). Used when a live setPreferredDevice is not honoured.
     */
    public interface InputReopener {
        /** Moves capture to the input resolved right now (resolvedChoice()). Returns false when it cannot. */
        boolean reopenInput();
    }

    private static final ArrayList<InputReopener> REOPENERS = new ArrayList<>();

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
        final int override = sessionOverride;
        if (override != NONE) {
            final boolean btAllowed = PermissionRequest.hasPermission(Manifest.permission.BLUETOOTH_CONNECT);
            if (!isBluetoothType(override) || btAllowed) {
                final boolean storedUsable = stored != AUTOMATIC && (!isBluetoothType(stored) || btAllowed);
                return resolveWithOverride(storedUsable ? stored : AUTOMATIC, override, connectedInputTypes());
            }
        }
        if (stored == AUTOMATIC) {
            return AUTOMATIC;
        }
        if (isBluetoothType(stored) && !PermissionRequest.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return AUTOMATIC;
        }
        final int[] connected = connectedInputTypes();
        // A headset with an LE Audio microphone records over LE Audio instead of the older call-quality link.
        return resolve(AudioRouteInfo.preferLowEnergy(stored, connected), connected);
    }

    /**
     * The in-recorder choice wins while its device is connected (AUTOMATIC always counts as connected). When it is gone,
     * the stored setting decides as before.
     */
    public static int resolveWithOverride(int stored, int override, int[] connectedTypes) {
        if (override == AUTOMATIC) {
            return AUTOMATIC;
        }
        if (override != NONE && isSupportedType(override) && connectedTypes != null) {
            for (int type : connectedTypes) {
                if (type == override) {
                    return override;
                }
            }
        }
        return resolve(stored, connectedTypes);
    }

    private static boolean contains(int[] types, int type) {
        if (types != null) {
            for (int t : types) {
                if (t == type) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The two inputs the in-recorder switch toggles between: {BUILTIN_MIC, external}, or an empty array when there is
     * nothing to switch to. The external pick is the first connected of wired, USB, LE Audio, classic Bluetooth;
     * Bluetooth counts only when the permission is held.
     */
    public static int[] quickPair(int[] connectedTypes, boolean btPermitted) {
        if (!contains(connectedTypes, AudioDeviceInfo.TYPE_BUILTIN_MIC)) {
            return new int[0];
        }
        for (int type : EXTERNAL_ORDER) {
            if (isBluetoothType(type) && !btPermitted) {
                continue;
            }
            if (contains(connectedTypes, type)) {
                return new int[]{AudioDeviceInfo.TYPE_BUILTIN_MIC, type};
            }
        }
        return new int[0];
    }

    /** Where one tap goes from the routed input. AUTOMATIC, or any input outside the pair, goes to the external one. */
    public static int toggle(int current, int[] pair) {
        if (pair == null || pair.length != 2) {
            return AUTOMATIC;
        }
        // The phone is the way back from any external input (also a headset that fell back to the other Bluetooth link).
        if (current == pair[0]) {
            return pair[1];
        }
        return current == pair[1] || (current != AUTOMATIC && current != AudioDeviceInfo.TYPE_BUILTIN_MIC) ? pair[0] : pair[1];
    }

    public static boolean switchAvailable(boolean featureOn, boolean callActive, int[] connectedTypes, boolean btPermitted) {
        return featureOn && !callActive && quickPair(connectedTypes, btPermitted).length == 2;
    }

    public static boolean switchAvailable() {
        if (!org.telegram.messenger.nanogram.NanogramFeatures.recorderMicSwitch()) {
            return false;
        }
        return switchAvailable(
                org.telegram.messenger.nanogram.NanogramFeatures.recorderMicSwitch(),
                VoIPService.isAnyKindOfCallActive(),
                connectedInputTypes(),
                PermissionRequest.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        );
    }

    public static int[] currentQuickPair() {
        return quickPair(connectedInputTypes(), PermissionRequest.hasPermission(Manifest.permission.BLUETOOTH_CONNECT));
    }

    /** Bumped on every completed switch; capture threads compare it to know when to run the timeline guard. */
    public static int switchGeneration() {
        return switchGeneration;
    }

    public static boolean hasSessionOverride() {
        return sessionOverride != NONE;
    }

    /** Ends the in-recorder choice: the stored setting decides again. Call when a recording ends, is cancelled or fails. */
    public static void clearSessionOverride() {
        overrideEpoch.incrementAndGet();
        setBluetoothRouting(false);
        if (sessionOverride != NONE) {
            sessionOverride = NONE;
            notifyInputListeners();
        }
    }

    /**
     * The input the recording really comes from: the routed device of a live recorder, else the resolved choice, else
     * what the system default picks (a wired or USB headset when present, otherwise the phone).
     */
    public static int currentRoutedType() {
        synchronized (ROUTE_LOCK) {
            for (int a = LIVE_RECORDS.size() - 1; a >= 0; a--) {
                final AudioRecord record = LIVE_RECORDS.get(a).get();
                if (record == null) {
                    LIVE_RECORDS.remove(a);
                    continue;
                }
                try {
                    if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        final AudioDeviceInfo routed = record.getRoutedDevice();
                        if (routed != null && routed.getType() != AudioDeviceInfo.TYPE_TELEPHONY) {
                            return routed.getType();
                        }
                    }
                } catch (Throwable ignore) {
                }
            }
        }
        final int choice = resolvedChoice();
        if (choice != AUTOMATIC) {
            return choice;
        }
        final int[] connected = connectedInputTypes();
        if (contains(connected, AudioDeviceInfo.TYPE_WIRED_HEADSET)) {
            return AudioDeviceInfo.TYPE_WIRED_HEADSET;
        }
        if (contains(connected, AudioDeviceInfo.TYPE_USB_HEADSET)) {
            return AudioDeviceInfo.TYPE_USB_HEADSET;
        }
        return AudioDeviceInfo.TYPE_BUILTIN_MIC;
    }

    public static void register(AudioRecord record) {
        if (record == null) {
            return;
        }
        synchronized (ROUTE_LOCK) {
            for (int a = LIVE_RECORDS.size() - 1; a >= 0; a--) {
                final AudioRecord other = LIVE_RECORDS.get(a).get();
                if (other == null) {
                    LIVE_RECORDS.remove(a);
                } else if (other == record) {
                    return;
                }
            }
            LIVE_RECORDS.add(new WeakReference<>(record));
        }
    }

    /** Call just before AudioRecord.release(). */
    public static void unregister(AudioRecord record) {
        if (record == null) {
            return;
        }
        synchronized (ROUTE_LOCK) {
            for (int a = LIVE_RECORDS.size() - 1; a >= 0; a--) {
                final AudioRecord other = LIVE_RECORDS.get(a).get();
                if (other == null || other == record) {
                    LIVE_RECORDS.remove(a);
                }
            }
        }
    }

    public static void registerReopener(InputReopener reopener) {
        synchronized (ROUTE_LOCK) {
            if (reopener != null && !REOPENERS.contains(reopener)) {
                REOPENERS.add(reopener);
            }
        }
    }

    public static void unregisterReopener(InputReopener reopener) {
        synchronized (ROUTE_LOCK) {
            REOPENERS.remove(reopener);
        }
    }

    /** Runs on the main thread whenever inputs are plugged, unplugged, or the in-recorder choice is cleared. */
    public static void registerInputListener(Runnable listener) {
        if (listener == null) {
            return;
        }
        synchronized (INPUT_LISTENERS) {
            if (!INPUT_LISTENERS.contains(listener)) {
                INPUT_LISTENERS.add(listener);
            }
            if (deviceCallback == null) {
                try {
                    final AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
                    deviceCallback = new AudioDeviceCallback() {
                        @Override
                        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                            notifyInputListeners();
                        }

                        @Override
                        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                            final int override = sessionOverride;
                            if (override != NONE && override != AUTOMATIC) {
                                for (AudioDeviceInfo device : removedDevices) {
                                    if (device.getType() == override) {
                                        sessionOverride = NONE;
                                        break;
                                    }
                                }
                            }
                            notifyInputListeners();
                        }
                    };
                    am.registerAudioDeviceCallback(deviceCallback, new Handler(Looper.getMainLooper()));
                } catch (Throwable e) {
                    deviceCallback = null;
                    FileLog.e(e);
                }
            }
        }
    }

    public static void unregisterInputListener(Runnable listener) {
        synchronized (INPUT_LISTENERS) {
            INPUT_LISTENERS.remove(listener);
            if (INPUT_LISTENERS.isEmpty() && deviceCallback != null) {
                try {
                    final AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
                    am.unregisterAudioDeviceCallback(deviceCallback);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                deviceCallback = null;
            }
        }
    }

    private static void notifyInputListeners() {
        final ArrayList<Runnable> copy;
        synchronized (INPUT_LISTENERS) {
            copy = new ArrayList<>(INPUT_LISTENERS);
        }
        if (copy.isEmpty()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            for (Runnable listener : copy) {
                listener.run();
            }
        });
    }

    /**
     * Moves the current recording to the given input type (AUTOMATIC for the system default) and reports on the UI
     * thread: SWITCH_ROUTED, SWITCH_PENDING (the route did not move yet; the next segment follows) or SWITCH_FAILED.
     * Never blocks the caller.
     */
    public static void switchTo(int type, Utilities.Callback<Integer> result) {
        final int epoch = overrideEpoch.get();
        SWITCH_QUEUE.postRunnable(() -> {
            final int previous = sessionOverride;
            int status;
            try {
                status = switchToBlocking(type, epoch);
            } catch (Throwable e) {
                FileLog.e(e);
                if (overrideEpoch.get() == epoch) {
                    sessionOverride = previous;
                    restoreRouting();
                }
                status = SWITCH_FAILED;
            }
            final int finalStatus = status;
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("AudioInputPreference switchTo " + type + " -> " + finalStatus);
            }
            notifyInputListeners();
            if (result != null) {
                AndroidUtilities.runOnUIThread(() -> result.run(finalStatus));
            }
        });
    }

    private static void restoreRouting() {
        try {
            setBluetoothRouting(false);
            setBluetoothRouting(true);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static int switchToBlocking(int type, int epoch) {
        if (overrideEpoch.get() != epoch) {
            return SWITCH_FAILED;
        }
        if (type != AUTOMATIC && !isSupportedType(type)) {
            return SWITCH_FAILED;
        }
        final AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return SWITCH_FAILED;
        }
        final boolean bluetooth = isBluetoothType(type);
        if (bluetooth && !PermissionRequest.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return SWITCH_FAILED;
        }
        sessionOverride = type;
        boolean btReady = true;
        if (bluetooth) {
            // The same acquire the recorders use at start, now with the override as the resolved choice.
            // acquire waits until the communication device is active, so there is no second wait here.
            setBluetoothRouting(false);
            final AudioRouteSession session = setBluetoothRouting(true);
            btReady = session != null && session.outcome() == AudioRouteSession.Outcome.ROUTED;
        } else {
            // Without this the capture can stay on the headset: the communication device outranks the preferred device.
            setBluetoothRouting(false);
            clearCommunicationDeviceNow(am);
        }
        if (overrideEpoch.get() != epoch) {
            // The recording ended while the route was being acquired: give the hold back, leave no override behind.
            setBluetoothRouting(false);
            sessionOverride = NONE;
            return SWITCH_FAILED;
        }
        final ArrayList<AudioRecord> records = new ArrayList<>();
        final ArrayList<InputReopener> reopeners;
        synchronized (ROUTE_LOCK) {
            for (int a = LIVE_RECORDS.size() - 1; a >= 0; a--) {
                final AudioRecord record = LIVE_RECORDS.get(a).get();
                if (record == null) {
                    LIVE_RECORDS.remove(a);
                } else {
                    records.add(record);
                }
            }
            reopeners = new ArrayList<>(REOPENERS);
        }
        if (records.isEmpty() && reopeners.isEmpty()) {
            // Nothing is capturing yet (round camera still starting): the first AudioRecord follows the override.
            switchGeneration++;
            return SWITCH_ROUTED;
        }
        boolean routed = true;
        for (AudioRecord record : records) {
            try {
                record.setPreferredDevice(type == AUTOMATIC ? null : findInput(am, type));
                routed &= waitForRoutedType(record, type, LIVE_ROUTE_WAIT_MS);
            } catch (Throwable e) {
                FileLog.e(e);
                routed = false;
            }
        }
        if (!routed && !reopeners.isEmpty()) {
            boolean reopened = true;
            for (InputReopener reopener : reopeners) {
                try {
                    reopened &= reopener.reopenInput();
                } catch (Throwable e) {
                    FileLog.e(e);
                    reopened = false;
                }
            }
            routed = reopened;
        }
        switchGeneration++;
        if (routed && btReady) {
            return SWITCH_ROUTED;
        }
        return SWITCH_PENDING;
    }

    private static boolean waitForRoutedType(AudioRecord record, int type, int timeoutMs) {
        final long end = SystemClock.elapsedRealtime() + timeoutMs;
        do {
            try {
                if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    // Not capturing right now (paused or starting): the next start follows the override.
                    return true;
                }
                final AudioDeviceInfo routed = record.getRoutedDevice();
                if (routed != null && (type == AUTOMATIC || routed.getType() == type)) {
                    return true;
                }
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Throwable e) {
                return false;
            }
        } while (SystemClock.elapsedRealtime() < end);
        return false;
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
        register(record);
        reapplyPreferred(record);
    }

    /** The setPreferredDevice part of applyTo, without registering the record again (the route monitor uses it). */
    public static void reapplyPreferred(AudioRecord record) {
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

    private static final Object ROUTE_LOCK = new Object();
    private static AudioRouteSession switchSession;

    /**
     * The in-recorder switch's own hold on the Bluetooth route (recorders hold theirs through AudioRouteSession).
     * Releasing clears only what the session set; use clearCommunicationDeviceNow to leave the headset for the phone.
     */
    private static AudioRouteSession setBluetoothRouting(boolean on) {
        final AudioRouteSession old;
        synchronized (ROUTE_LOCK) {
            old = switchSession;
            switchSession = null;
        }
        if (old != null) {
            old.release();
        }
        if (on) {
            final AudioRouteSession session = acquireRoute();
            synchronized (ROUTE_LOCK) {
                switchSession = session;
            }
            return session;
        }
        return null;
    }

    /**
     * Gives back the switch's own hold on the Bluetooth route without ending the in-recorder choice (pause: the
     * preview plays over the normal media route; the next segment acquires the route again from the choice).
     */
    public static void releaseSwitchRoute() {
        setBluetoothRouting(false);
    }

    private static void clearCommunicationDeviceNow(AudioManager am) {
        try {
            am.clearCommunicationDevice();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * Activates the Bluetooth communication device when the resolved choice needs one. Call it on the recorder's own
     * worker thread before creating the AudioRecord: it waits for the route (up to 2 s, plus a retry) and never
     * returns null. Release the result in a finally block.
     */
    public static AudioRouteSession acquireRoute() {
        return acquireRoute(AudioRouteSession.ACQUIRE_TIMEOUT_MS, null);
    }

    /** As {@link #acquireRoute()} with its own wait and a cancel check; a cancelled wait returns a FALLBACK session that holds nothing. */
    public static AudioRouteSession acquireRoute(long timeoutMs, java.util.function.BooleanSupplier cancelled) {
        int choice = AUTOMATIC;
        try {
            choice = resolvedChoice();
            if (!shouldRoute(choice, VoIPService.isAnyKindOfCallActive()) || !isBluetoothType(choice)) {
                return AudioRouteSession.none(choice);
            }
            final AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return AudioRouteSession.none(choice);
            }
            AudioRouteSession session = new AudioRouteSession(AudioRouteSession.systemPlatform(am), choice);
            AudioRouteSession.Outcome outcome = session.acquire(choice, timeoutMs, !AudioRouteSession.onMainThread(), cancelled);
            if (outcome == AudioRouteSession.Outcome.FALLBACK && (cancelled == null || !cancelled.getAsBoolean()) && choice == AudioDeviceInfo.TYPE_BLE_HEADSET
                    && SharedConfig.recordInputDevice == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    && contains(connectedInputTypes(), AudioDeviceInfo.TYPE_BLUETOOTH_SCO)) {
                // LE Audio was preferred but not usable as the communication device: the call-quality link still works.
                session.release();
                FileLog.i("AudioInputPreference LE Audio route failed, falling back to the Bluetooth SCO link");
                choice = AudioDeviceInfo.TYPE_BLUETOOTH_SCO;
                session = new AudioRouteSession(AudioRouteSession.systemPlatform(am), choice);
                outcome = session.acquire(choice, timeoutMs, !AudioRouteSession.onMainThread(), cancelled);
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("AudioInputPreference route type " + choice + " " + outcome + " in " + session.acquireMs() + " ms");
            }
            return session;
        } catch (Throwable e) {
            FileLog.e(e);
            return AudioRouteSession.none(choice);
        }
    }
}
