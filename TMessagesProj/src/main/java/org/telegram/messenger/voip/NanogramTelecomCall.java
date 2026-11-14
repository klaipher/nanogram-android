package org.telegram.messenger.voip;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.OutcomeReceiver;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallControlCallback;
import android.telecom.CallEventCallback;
import android.telecom.CallException;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NanogramPerfStats;

import java.util.List;
import java.util.function.Consumer;

/**
 * Tells the system about a 1:1 call (transactional self-managed Telecom call, API 34+), so Bluetooth, car and
 * watch call controls, Do Not Disturb call exceptions and call priority see it. tgcalls keeps owning audio routing:
 * endpoint callbacks are ignored. Used only behind the selfManagedTelecom experiment.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
final class NanogramTelecomCall {

    interface Host {
        void onSystemAnswer();

        void onSystemDisconnect();
    }

    private static final String ACCOUNT_ID = "nanogram-calls";

    static boolean shouldUse(int sdkInt, boolean experimentOn, boolean legacyConnectionService, boolean oneToOne) {
        return sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && experimentOn && !legacyConnectionService && oneToOne;
    }

    static boolean oneToOne(boolean hasGroupCall, boolean hasUser) {
        return !hasGroupCall && hasUser;
    }

    static boolean startsCall(boolean incomingRinging, boolean outgoingRequesting, boolean oneToOne) {
        return oneToOne && (incomingRinging || outgoingRequesting);
    }

    /** CallControl.disconnect accepts only LOCAL, REMOTE, MISSED and REJECTED. */
    static int disconnectCode(boolean failed, boolean remoteBusy, boolean incomingNotAnswered, boolean incomingDeclined) {
        if (failed) {
            return DisconnectCause.LOCAL;
        }
        if (remoteBusy) {
            return DisconnectCause.REMOTE;
        }
        if (incomingDeclined) {
            return DisconnectCause.REJECTED;
        }
        if (incomingNotAnswered) {
            return DisconnectCause.MISSED;
        }
        return DisconnectCause.LOCAL;
    }

    private final Host host;
    private final boolean incoming;
    private final boolean video;
    private volatile CallControl control;
    private boolean pendingActive;
    private boolean active;
    private boolean answered;
    private boolean pendingAnswer;
    private int pendingDisconnect = -1;
    private boolean finished;

    NanogramTelecomCall(Host host, boolean incoming, boolean video) {
        this.host = host;
        this.incoming = incoming;
        this.video = video;
    }

    private long registerStartNanos;

    void start(Context context, String displayName) {
        registerStartNanos = System.nanoTime();
        try {
            TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            PhoneAccountHandle handle = new PhoneAccountHandle(new ComponentName(context, TelegramConnectionService.class), ACCOUNT_ID);
            tm.registerPhoneAccount(new PhoneAccount.Builder(handle, "Nanogram")
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED | PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                    .build());
            CallAttributes attributes = new CallAttributes.Builder(
                    handle,
                    incoming ? CallAttributes.DIRECTION_INCOMING : CallAttributes.DIRECTION_OUTGOING,
                    displayName,
                    Uri.fromParts(PhoneAccount.SCHEME_SIP, "nanogram-call", null)
            ).setCallType(video ? CallAttributes.VIDEO_CALL : CallAttributes.AUDIO_CALL)
                    .setCallCapabilities(video ? CallAttributes.SUPPORTS_VIDEO_CALLING : 0)
                    .build();
            tm.addCall(attributes, Runnable::run, new OutcomeReceiver<CallControl, CallException>() {
                @Override
                public void onResult(CallControl result) {
                    NanogramPerfStats.recordSince(NanogramPerfStats.TELECOM_REGISTER, registerStartNanos);
                    onControl(result);
                }

                @Override
                public void onError(@NonNull CallException error) {
                    FileLog.e("Telecom addCall failed, code " + error.getCode(), error);
                    synchronized (NanogramTelecomCall.this) {
                        finished = true;
                    }
                }
            }, callbacks, events);
        } catch (Throwable e) {
            FileLog.e(e);
            finished = true;
        }
    }

    private synchronized void onControl(CallControl result) {
        if (finished) {
            safeDisconnect(result, DisconnectCause.LOCAL);
            return;
        }
        control = result;
        if (pendingAnswer) {
            pendingAnswer = false;
            result.answer(videoState(), Runnable::run, noop());
        }
        if (pendingActive) {
            pendingActive = false;
            result.setActive(Runnable::run, noop());
        }
        if (pendingDisconnect >= 0) {
            int code = pendingDisconnect;
            pendingDisconnect = -1;
            finished = true;
            safeDisconnect(result, code);
        }
    }

    synchronized void answer() {
        if (finished || answered) {
            return;
        }
        answered = true;
        CallControl c = control;
        if (c == null) {
            pendingAnswer = true;
        } else {
            c.answer(videoState(), Runnable::run, noop());
        }
    }

    synchronized void setActive() {
        if (finished || active) {
            return;
        }
        active = true;
        CallControl c = control;
        if (c == null) {
            pendingActive = true;
        } else {
            c.setActive(Runnable::run, noop());
        }
    }

    synchronized void disconnect(int cause) {
        if (finished) {
            return;
        }
        CallControl c = control;
        if (c == null) {
            pendingDisconnect = cause;
            return;
        }
        finished = true;
        safeDisconnect(c, cause);
    }

    private static void safeDisconnect(CallControl c, int cause) {
        try {
            c.disconnect(new DisconnectCause(cause), Runnable::run, noop());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private synchronized boolean markFinishedBySystem() {
        boolean was = finished;
        finished = true;
        return !was;
    }

    private int videoState() {
        return video ? VideoProfile.STATE_BIDIRECTIONAL : VideoProfile.STATE_AUDIO_ONLY;
    }

    private static OutcomeReceiver<Void, CallException> noop() {
        return new OutcomeReceiver<Void, CallException>() {
            @Override
            public void onResult(Void result) {
            }

            @Override
            public void onError(@NonNull CallException error) {
                FileLog.e("Telecom call control error, code " + error.getCode());
            }
        };
    }

    private final CallControlCallback callbacks = new CallControlCallback() {
        @Override
        public void onSetActive(@NonNull Consumer<Boolean> wasCompleted) {
            wasCompleted.accept(true);
        }

        @Override
        public void onSetInactive(@NonNull Consumer<Boolean> wasCompleted) {
            wasCompleted.accept(false);
        }

        @Override
        public void onAnswer(int videoState, @NonNull Consumer<Boolean> wasCompleted) {
            wasCompleted.accept(true);
            AndroidUtilities.runOnUIThread(host::onSystemAnswer);
        }

        @Override
        public void onDisconnect(@NonNull DisconnectCause cause, @NonNull Consumer<Boolean> wasCompleted) {
            wasCompleted.accept(true);
            if (markFinishedBySystem()) {
                AndroidUtilities.runOnUIThread(host::onSystemDisconnect);
            }
        }

        @Override
        public void onCallStreamingStarted(@NonNull Consumer<Boolean> wasCompleted) {
            wasCompleted.accept(true);
        }
    };

    private final CallEventCallback events = new CallEventCallback() {
        @Override
        public void onCallEndpointChanged(@NonNull android.telecom.CallEndpoint newCallEndpoint) {
        }

        @Override
        public void onAvailableCallEndpointsChanged(@NonNull List<android.telecom.CallEndpoint> availableEndpoints) {
        }

        @Override
        public void onMuteStateChanged(boolean isMuted) {
        }

        @Override
        public void onCallStreamingFailed(int reason) {
        }

        @Override
        public void onEvent(@NonNull String event, @NonNull android.os.Bundle extras) {
        }
    };
}
