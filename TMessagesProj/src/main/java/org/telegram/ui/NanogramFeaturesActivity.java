package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.messenger.nanogram.NanogramFeatures;
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

        items.add(UItem.asHeader(getString(R.string.NanogramFeaturesPower)));
        items.add(UItem.asCheck(ID_FOLLOW_SYSTEM_POWER, getString(R.string.LiteFollowSystemSaver)).setChecked(LiteMode.isFollowSystemPowerSaver()));
        items.add(UItem.asShadow(getString(R.string.LiteFollowSystemSaverInfo)));
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
            case ID_FOLLOW_SYSTEM_POWER:
                LiteMode.setFollowSystemPowerSaver(!LiteMode.isFollowSystemPowerSaver());
                break;
            default:
                return;
        }
        listView.adapter.update(true);
    }
}
