package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Build;
import android.view.View;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.nanogram.NanogramFeatures;
import org.telegram.messenger.nanogram.NanogramOnDevice;
import org.telegram.messenger.nanogram.NanogramOnDeviceTranscription;
import org.telegram.messenger.nanogram.NanogramOnDeviceTranslation;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

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

    @Override
    public boolean onFragmentCreate() {
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
        items.add(UItem.asShadow(getString(R.string.NanogramMicrophoneInfo)));

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
