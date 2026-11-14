package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Range;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.NanogramCameraExtensions;
import org.telegram.messenger.nanogram.NanogramFeatures;
import org.telegram.messenger.nanogram.NanogramOnDevice;
import org.telegram.messenger.nanogram.NanogramOnDeviceTranscription;
import org.telegram.messenger.nanogram.NanogramOnDeviceTranslation;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.PermissionRequest;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.utils.recorder.round.RoundVideoZoomPlan;

import java.util.ArrayList;

/** Settings > Nanogram features: user-facing Nanogram switches, each with a plain description. */
public class NanogramFeaturesActivity extends UniversalFragment {

    private static final int ID_SEARCH_OPERATORS = 1;
    private static final int ID_STRIP_TRACKING = 2;
    private static final int ID_CONFIRM_TELEGRAM = 3;
    private static final int ID_MICROPHONE = 4;
    private static final int ID_FOLLOW_SYSTEM_POWER = 5;
    private static final int ID_ON_DEVICE_TRANSCRIPTION = 6;
    private static final int ID_ON_DEVICE_TRANSLATION = 7;
    private static final int ID_MODELS_WIFI_ONLY = 8;
    private static final int ID_ROUND_WIDE_ANGLE = 9;
    private static final int ID_RECORDER_MIC_SWITCH = 10;
    private static final int ID_ENHANCED_PHOTOS = 11;
    private static final int ID_ENHANCED_PHOTO_MODE = 12;
    private static final int ID_VOICE_ENHANCEMENT = 13;

    // Feature C turns this on when its recorder button lands; until then its row stays hidden.
    private static final boolean RECORDER_MIC_SWITCH_AVAILABLE = true;

    // Support probes run once off the main thread; null means not known yet, and the rows then behave as supported.
    private static volatile Boolean wideAngleSupported;
    private static volatile int[] enhancedPhotoModes;
    private static boolean probesStarted;

    @Override
    public boolean onFragmentCreate() {
        startProbes();
        NanogramOnDeviceTranslation.setStatusListener((language, state) -> {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NanogramOnDeviceTranslation.setStatusListener(null);
        super.onFragmentDestroy();
    }

    private void startProbes() {
        if (probesStarted) {
            return;
        }
        probesStarted = true;
        Utilities.globalQueue.postRunnable(() -> {
            boolean wide = true;
            int[] modes = null;
            try {
                wide = probeWideAngle();
            } catch (Throwable ignore) {
            }
            try {
                modes = probeEnhancedPhotoModes();
            } catch (Throwable ignore) {
            }
            wideAngleSupported = wide;
            enhancedPhotoModes = modes;
            AndroidUtilities.runOnUIThread(() -> {
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        });
    }

    // True when a front or back camera zooms out below 1x. Runs on globalQueue.
    private static boolean probeWideAngle() throws Exception {
        final CameraManager manager = (CameraManager) ApplicationLoader.applicationContext.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            return false;
        }
        for (String id : manager.getCameraIdList()) {
            final CameraCharacteristics c = manager.getCameraCharacteristics(id);
            final Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || (facing != CameraCharacteristics.LENS_FACING_FRONT && facing != CameraCharacteristics.LENS_FACING_BACK)) {
                continue;
            }
            final Range<Float> range = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (RoundVideoZoomPlan.minimumRatio(range == null ? null : range.getLower(), true) < 1f) {
                return true;
            }
        }
        return false;
    }

    // The modes the cameras offer, using NanogramFeatures.PHOTO_MODE_*; empty means none. Runs on globalQueue.
    private static int[] probeEnhancedPhotoModes() {
        return NanogramCameraExtensions.modes(ApplicationLoader.applicationContext);
    }

    private static boolean enhancedPhotosSupported() {
        final int[] modes = enhancedPhotoModes;
        return modes == null || modes.length > 0;
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        fragmentView = super.createView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        return fragmentView;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.NanogramFeatures);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.NanogramFeaturesSearch)));
        items.add(UItem.asCheck(ID_SEARCH_OPERATORS, getString(R.string.NanogramSearchOperators)).setChecked(NanogramFeatures.searchOperators()));
        items.add(UItem.asShadow(getString(R.string.NanogramSearchOperatorsInfo)));

        items.add(UItem.asHeader(getString(R.string.NanogramFeaturesLinks)));
        items.add(UItem.asCheck(ID_STRIP_TRACKING, getString(R.string.NanogramStripTracking)).setChecked(NanogramFeatures.stripTrackingParams()));
        items.add(UItem.asCheck(ID_CONFIRM_TELEGRAM, getString(R.string.NanogramConfirmTelegramLinks)).setChecked(NanogramFeatures.confirmTelegramLinks()));
        items.add(UItem.asShadow(getString(R.string.NanogramLinksInfo)));

        items.add(UItem.asHeader(getString(R.string.NanogramFeaturesRecording)));
        items.add(UItem.asButton(ID_MICROPHONE, getString(R.string.MicrophoneForRecordings), ThemeActivity.microphoneValue()));
        if (RECORDER_MIC_SWITCH_AVAILABLE) {
            items.add(UItem.asCheck(ID_RECORDER_MIC_SWITCH, getString(R.string.NanogramRecorderMicSwitch)).setChecked(NanogramFeatures.recorderMicSwitch()));
            items.add(UItem.asShadow(getString(R.string.NanogramMicrophoneInfo) + "\n\n" + getString(R.string.NanogramRecorderMicSwitchInfo)));
        } else {
            items.add(UItem.asShadow(getString(R.string.NanogramMicrophoneInfo)));
        }
        items.add(UItem.asCheck(ID_VOICE_ENHANCEMENT, getString(R.string.NanogramVoiceEnhancement)).setChecked(NanogramFeatures.headsetVoiceEnhancement()));
        items.add(UItem.asShadow(getString(R.string.NanogramVoiceEnhancementInfo)));

        items.add(UItem.asHeader(getString(R.string.NanogramFeaturesCamera)));
        items.add(UItem.asCheck(ID_ROUND_WIDE_ANGLE, getString(R.string.NanogramRoundWideAngle)).setChecked(NanogramFeatures.roundWideAngle()));
        items.add(UItem.asCheck(ID_ENHANCED_PHOTOS, getString(R.string.NanogramEnhancedPhotos)).setChecked(NanogramFeatures.enhancedPhotos()));
        final int[] modes = enhancedPhotoModes;
        if (NanogramFeatures.enhancedPhotos() && modes != null && modes.length > 1) {
            items.add(UItem.asButton(ID_ENHANCED_PHOTO_MODE, getString(R.string.NanogramEnhancedPhotosMode), photoModeTitle(NanogramFeatures.enhancedPhotoMode())));
        }
        items.add(UItem.asShadow(getString(R.string.NanogramRoundWideAngleInfo) + "\n\n" + getString(R.string.NanogramEnhancedPhotosInfo)));

        items.add(UItem.asHeader(getString(R.string.NanogramFeaturesOnDevice)));
        final Context context = getContext();
        // The recognizer can read a file only from API 33; below that, or without an on-device recognizer, the switch is not offered.
        final boolean transcription = context != null && NanogramOnDeviceTranscription.isSupported(context);
        if (transcription) {
            items.add(UItem.asCheck(ID_ON_DEVICE_TRANSCRIPTION, getString(R.string.NanogramOnDeviceTranscription)).setChecked(NanogramFeatures.onDeviceTranscription()));
        }
        items.add(UItem.asCheck(ID_ON_DEVICE_TRANSLATION, getString(R.string.NanogramOnDeviceTranslation)).setChecked(NanogramFeatures.onDeviceTranslation()));
        if (NanogramFeatures.onDeviceTranslation()) {
            items.add(UItem.asCheck(ID_MODELS_WIFI_ONLY, getString(R.string.NanogramModelsWifiOnly)).setChecked(NanogramFeatures.modelsWifiOnly()));
        }
        items.add(UItem.asShadow(onDeviceInfo(transcription)));

        items.add(UItem.asHeader(getString(R.string.NanogramFeaturesPower)));
        items.add(UItem.asCheck(ID_FOLLOW_SYSTEM_POWER, getString(R.string.LiteFollowSystemSaver)).setChecked(LiteMode.isFollowSystemPowerSaver()));
        items.add(UItem.asShadow(getString(R.string.LiteFollowSystemSaverInfo)));
    }

    private static String photoModeTitle(int mode) {
        switch (mode) {
            case NanogramFeatures.PHOTO_MODE_HDR:
                return getString(R.string.NanogramEnhancedPhotosHdr);
            case NanogramFeatures.PHOTO_MODE_NIGHT:
                return getString(R.string.NanogramEnhancedPhotosNight);
            default:
                return getString(R.string.NanogramEnhancedPhotosAuto);
        }
    }

    private void showPhotoModeDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final int[] modes = enhancedPhotoModes;
        if (modes == null || modes.length == 0) {
            return;
        }
        final CharSequence[] titles = new CharSequence[modes.length];
        for (int i = 0; i < modes.length; i++) {
            titles[i] = photoModeTitle(modes[i]);
        }
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.NanogramEnhancedPhotosMode))
                .setItems(titles, (dialog, which) -> {
                    NanogramFeatures.setEnhancedPhotoMode(modes[which]);
                    listView.adapter.update(true);
                })
                .create());
    }

    private CharSequence onDeviceInfo(boolean transcription) {
        final StringBuilder info = new StringBuilder();
        if (transcription) {
            info.append(getString(R.string.NanogramOnDeviceTranscriptionInfo)).append("\n\n");
        }
        info.append(getString(R.string.NanogramOnDeviceTranslationInfo));
        final String language = NanogramOnDeviceTranslation.lastLanguage();
        if (NanogramFeatures.onDeviceTranslation() && language != null) {
            final String name = new java.util.Locale(language).getDisplayLanguage(LocaleController.getInstance().getCurrentLocale());
            switch (NanogramOnDeviceTranslation.lastState()) {
                case NanogramOnDeviceTranslation.STATE_DOWNLOADING:
                    info.append("\n\n").append(LocaleController.formatString(R.string.NanogramModelDownloading, name));
                    break;
                case NanogramOnDeviceTranslation.STATE_READY:
                    info.append("\n\n").append(LocaleController.formatString(R.string.NanogramModelReady, name));
                    break;
                case NanogramOnDeviceTranslation.STATE_FAILED:
                    info.append("\n\n").append(LocaleController.formatString(R.string.NanogramModelFailed, name));
                    break;
            }
        }
        return info.toString();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_SEARCH_OPERATORS:
                NanogramFeatures.setSearchOperators(!NanogramFeatures.searchOperators());
                break;
            case ID_STRIP_TRACKING:
                NanogramFeatures.setStripTrackingParams(!NanogramFeatures.stripTrackingParams());
                break;
            case ID_CONFIRM_TELEGRAM:
                NanogramFeatures.setConfirmTelegramLinks(!NanogramFeatures.confirmTelegramLinks());
                break;
            case ID_MICROPHONE:
                ThemeActivity.showMicrophoneDialog(this, () -> listView.adapter.update(true));
                return;
            case ID_ROUND_WIDE_ANGLE: {
                final Boolean supported = wideAngleSupported;
                if (!NanogramFeatures.roundWideAngle() && supported != null && !supported) {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.NanogramNotSupportedOnPhone)).show();
                    return;
                }
                NanogramFeatures.setRoundWideAngle(!NanogramFeatures.roundWideAngle());
                break;
            }
            case ID_RECORDER_MIC_SWITCH:
                NanogramFeatures.setRecorderMicSwitch(!NanogramFeatures.recorderMicSwitch());
                if (NanogramFeatures.recorderMicSwitch() && !PermissionRequest.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    // A denial keeps the switch on; Bluetooth then simply is not offered.
                    PermissionRequest.ensurePermission(R.raw.permission_request_microphone, R.string.PermissionNoBluetoothWithHint, Manifest.permission.BLUETOOTH_CONNECT);
                }
                break;
            case ID_VOICE_ENHANCEMENT:
                NanogramFeatures.setHeadsetVoiceEnhancement(!NanogramFeatures.headsetVoiceEnhancement());
                break;
            case ID_ENHANCED_PHOTOS:
                if (!NanogramFeatures.enhancedPhotos() && !enhancedPhotosSupported()) {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.NanogramNotSupportedOnPhone)).show();
                    return;
                }
                NanogramFeatures.setEnhancedPhotos(!NanogramFeatures.enhancedPhotos());
                break;
            case ID_ENHANCED_PHOTO_MODE:
                showPhotoModeDialog();
                return;
            case ID_ON_DEVICE_TRANSCRIPTION: {
                final boolean on = !NanogramFeatures.onDeviceTranscription();
                NanogramFeatures.setOnDeviceTranscription(on);
                if (on && Build.VERSION.SDK_INT >= 33 && getContext() != null) {
                    final String tag = NanogramOnDevice.speechTag(LocaleController.getInstance().getCurrentLocale().toLanguageTag());
                    NanogramOnDeviceTranscription.prepare(getContext(), tag, supported -> {
                        if (!supported) {
                            BulletinFactory.of(this).createErrorBulletin(getString(R.string.NanogramOnDeviceTranscriptionNoLanguage)).show();
                        }
                    });
                }
                break;
            }
            case ID_ON_DEVICE_TRANSLATION: {
                final boolean on = !NanogramFeatures.onDeviceTranslation();
                NanogramFeatures.setOnDeviceTranslation(on);
                if (on) {
                    NanogramOnDeviceTranslation.prefetch(LocaleController.getInstance().getCurrentLocale().toLanguageTag());
                }
                break;
            }
            case ID_MODELS_WIFI_ONLY:
                NanogramFeatures.setModelsWifiOnly(!NanogramFeatures.modelsWifiOnly());
                break;
            case ID_FOLLOW_SYSTEM_POWER:
                LiteMode.setFollowSystemPowerSaver(!LiteMode.isFollowSystemPowerSaver());
                break;
            default:
                return;
        }
        listView.adapter.update(true);
    }
}
