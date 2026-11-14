package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.media.AudioDeviceInfo;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AudioInputPreference;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.nanogram.NanogramFeatures;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.FlashViews;

/**
 * The in-recorder microphone switch (Nanogram features > Microphone switch while recording): one tap moves the current
 * recording between the phone microphone and the connected headset. The chip in the round-video button row and the pill
 * of the voice recorder are two views over the same Switcher.
 */
public final class RecorderMicSwitcher {

    private final Context context;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Runnable onChanged;
    private FrameLayout bulletinContainer;
    private java.util.function.Supplier<org.telegram.ui.ActionBar.BaseFragment> bulletinFragment;
    private boolean busy;
    // The target of a switch that is not routed yet, so the icon and the next tap follow what the user asked for.
    private int pendingTarget = AudioInputPreference.NONE;

    public RecorderMicSwitcher(Context context, Theme.ResourcesProvider resourcesProvider, Runnable onChanged) {
        this.context = context;
        this.resourcesProvider = resourcesProvider;
        this.onChanged = onChanged;
    }

    public void setBulletinContainer(FrameLayout container) {
        bulletinContainer = container;
    }

    /** Bulletins go through the chat fragment (as upstream's own hints do) instead of the small enter view. */
    public void setBulletinFragment(java.util.function.Supplier<org.telegram.ui.ActionBar.BaseFragment> fragment) {
        bulletinFragment = fragment;
    }

    public boolean isAvailable() {
        try {
            return AudioInputPreference.switchAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean isBusy() {
        return busy;
    }

    /** The type the UI shows: the routed input, or what the user just asked for while the route settles. */
    public int displayType() {
        if (pendingTarget != AudioInputPreference.NONE) {
            if (!AudioInputPreference.hasSessionOverride()) {
                pendingTarget = AudioInputPreference.NONE;
            } else {
                final int routed = AudioInputPreference.currentRoutedType();
                if (routed == pendingTarget) {
                    pendingTarget = AudioInputPreference.NONE;
                } else {
                    return pendingTarget;
                }
            }
        }
        return AudioInputPreference.currentRoutedType();
    }

    public static int iconFor(int type) {
        if (AudioInputPreference.isBluetoothType(type)) {
            return R.drawable.msg_voice_bluetooth;
        }
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_USB_HEADSET) {
            return R.drawable.msg_voice_headphones;
        }
        return R.drawable.msg_voice_phone;
    }

    public static String labelFor(int type) {
        if (AudioInputPreference.isBluetoothType(type)) {
            return LocaleController.getString(R.string.NanogramMicBluetoothShort);
        }
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_USB_HEADSET) {
            return LocaleController.getString(R.string.NanogramMicHeadsetShort);
        }
        return LocaleController.getString(R.string.NanogramMicPhoneShort);
    }

    public void toggle(View haptic) {
        if (busy) {
            return;
        }
        final int[] pair = AudioInputPreference.currentQuickPair();
        if (pair.length != 2) {
            changed();
            return;
        }
        final int target = AudioInputPreference.toggle(displayType(), pair);
        busy = true;
        changed();
        AudioInputPreference.switchTo(target, status -> {
            busy = false;
            if (status == AudioInputPreference.SWITCH_ROUTED) {
                pendingTarget = AudioInputPreference.NONE;
                if (haptic != null) {
                    haptic.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            } else if (status == AudioInputPreference.SWITCH_PENDING) {
                pendingTarget = target;
                showBulletin(R.raw.info, LocaleController.getString(R.string.NanogramMicSwitchAfterPause));
            } else {
                pendingTarget = AudioInputPreference.NONE;
                showBulletin(R.raw.error, LocaleController.getString(R.string.NanogramMicSwitchFailed));
            }
            changed();
        });
    }

    private void showBulletin(int icon, String text) {
        try {
            final org.telegram.ui.ActionBar.BaseFragment fragment = bulletinFragment == null ? null : bulletinFragment.get();
            if (fragment != null) {
                BulletinFactory.of(fragment).createSimpleBulletin(icon, text).show();
            } else if (bulletinContainer != null) {
                BulletinFactory.of(bulletinContainer, resourcesProvider).createSimpleBulletin(icon, text).show();
            }
        } catch (Throwable ignore) {
        }
    }

    private void changed() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    /** Ends the in-recorder choice and forgets any pending target. */
    public void reset() {
        pendingTarget = AudioInputPreference.NONE;
        busy = false;
        AudioInputPreference.clearSessionOverride();
    }

    /** The chip in the round-video button row, next to flip, flash and the lens chip. */
    public static final class Chip extends FlashViews.ImageViewInvertable {

        private final RecorderMicSwitcher switcher;
        private final Runnable refresher = this::refresh;
        private boolean allowed = true;
        private boolean controlsEnabled = true;
        private boolean registered;

        public Chip(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            switcher = new RecorderMicSwitcher(context, resourcesProvider, this::refresh);
            setScaleType(ScaleType.CENTER);
            setVisibility(GONE);
            setOnClickListener(v -> switcher.toggle(this));
        }

        public RecorderMicSwitcher getSwitcher() {
            return switcher;
        }

        public void setBulletinContainer(FrameLayout container) {
            switcher.setBulletinContainer(container);
        }

        /** The recorder state allows a switch (the round camera is starting or recording). */
        public void setControlsEnabled(boolean enabled) {
            controlsEnabled = enabled;
            refresh();
        }

        public void setAllowed(boolean value) {
            allowed = value;
            refresh();
        }

        public void refresh() {
            final boolean visible = allowed && switcher.isAvailable();
            if (visible) {
                final int type = switcher.displayType();
                setImageResource(iconFor(type));
                setContentDescription(LocaleController.formatString(R.string.AccDescrMicSource, labelFor(type)));
            }
            setVisibility(visible ? VISIBLE : GONE);
            setEnabled(controlsEnabled && !switcher.isBusy());
            setAlpha(switcher.isBusy() ? 0.5f : 1f);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            // With the switch off nothing is registered: the device callback is not worth a binder call in every chat.
            registered = NanogramFeatures.recorderMicSwitch();
            if (registered) {
                AudioInputPreference.registerInputListener(refresher);
            }
            refresh();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (registered) {
                registered = false;
                AudioInputPreference.unregisterInputListener(refresher);
            }
        }
    }

    /** The voice recorder pill: icon and a short label, 28dp high with a 44dp touch target. */
    public static final class Pill extends TextView {

        private final RecorderMicSwitcher switcher;
        private final Runnable refresher = this::refresh;
        private boolean allowed;
        private boolean overlapped;
        private boolean registered;

        public Pill(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            switcher = new RecorderMicSwitcher(context, resourcesProvider, this::refresh);
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            setTypeface(AndroidUtilities.bold());
            setGravity(Gravity.CENTER_VERTICAL);
            setSingleLine(true);
            setCompoundDrawablePadding(dp(4));
            // Icon only (the label is the content description): 14 + 16 + 14 = 44dp, so it fits beside the timer and Cancel.
            setPadding(dp(14), dp(8), dp(14), dp(8));
            setMinHeight(dp(44));
            setMinWidth(dp(44));
            setVisibility(GONE);
            setOnClickListener(v -> switcher.toggle(this));
            updateColors();
        }

        public RecorderMicSwitcher getSwitcher() {
            return switcher;
        }

        public void setBulletinContainer(FrameLayout container) {
            switcher.setBulletinContainer(container);
        }

        public void setBulletinFragment(java.util.function.Supplier<org.telegram.ui.ActionBar.BaseFragment> fragment) {
            switcher.setBulletinFragment(fragment);
        }

        /**
         * Set while the locked "Cancel" text would sit under the pill: the pill steps aside so a tap on Cancel cancels.
         * Only a flag, applied after the draw pass that found the overlap.
         */
        public void setOverlapped(boolean value) {
            if (overlapped != value) {
                overlapped = value;
                post(refresher);
            }
        }

        /** The recorder is locked (not hold mode) and records a voice message. */
        public void setAllowed(boolean value) {
            allowed = value;
            refresh();
        }

        public void updateColors() {
            final int color = Theme.getColor(Theme.key_chat_recordTime, switcher.resourcesProvider);
            setTextColor(color);
            final int fill = ColorUtils.setAlphaComponent(color, 31); // 12%
            final android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setColor(fill);
            shape.setCornerRadius(dp(14));
            // The 44dp touch target is the view; the 28dp pill is drawn inside it.
            setBackground(new android.graphics.drawable.InsetDrawable(shape, 0, dp(8), 0, dp(8)));
            refreshIcon();
        }

        private void refreshIcon() {
            if (getVisibility() != VISIBLE) {
                return;
            }
            final int color = Theme.getColor(Theme.key_chat_recordTime, switcher.resourcesProvider);
            final Drawable icon = ContextCompat.getDrawable(getContext(), iconFor(switcher.displayType()));
            if (icon != null) {
                final Drawable tinted = icon.mutate();
                tinted.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                tinted.setBounds(0, 0, dp(16), dp(16));
                setCompoundDrawables(tinted, null, null, null);
            }
        }

        public void refresh() {
            final boolean visible = allowed && !overlapped && switcher.isAvailable();
            if (visible) {
                final int type = switcher.displayType();
                setContentDescription(LocaleController.formatString(R.string.AccDescrMicSource, labelFor(type)));
            }
            setVisibility(visible ? VISIBLE : GONE);
            refreshIcon();
            setEnabled(!switcher.isBusy());
            setAlpha(switcher.isBusy() ? 0.5f : 1f);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            // With the switch off nothing is registered: the device callback is not worth a binder call in every chat.
            registered = NanogramFeatures.recorderMicSwitch();
            if (registered) {
                AudioInputPreference.registerInputListener(refresher);
            }
            refresh();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (registered) {
                registered = false;
                AudioInputPreference.unregisterInputListener(refresher);
            }
        }
    }
}
