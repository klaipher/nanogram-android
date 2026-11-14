package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import org.telegram.messenger.regular.BuildConfig;
import org.telegram.ui.Components.NanogramUpdateAlertDialog;
import org.telegram.ui.Components.NanogramUpdateLayout;
import org.telegram.ui.IUpdateLayout;

import java.io.File;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    public boolean isStoreFlavor() {
        return BuildConfig.STORE_FLAVOR;
    }

    @Override
    public boolean isCustomUpdate() {
        return !BuildConfig.STORE_FLAVOR && NanogramUpdater.isEnabled();
    }

    @Override
    public BetaUpdate getUpdate() {
        return isCustomUpdate() ? NanogramUpdater.getInstance().getUpdate() : null;
    }

    @Override
    public void checkUpdate(boolean force, Runnable whenDone) {
        if (isCustomUpdate()) {
            NanogramUpdater.getInstance().checkForUpdate(force, whenDone);
        }
    }

    @Override
    public void downloadUpdate() {
        if (isCustomUpdate()) {
            NanogramUpdater.getInstance().downloadUpdate();
        }
    }

    @Override
    public void cancelDownloadingUpdate() {
        NanogramUpdater.getInstance().cancelDownloadingUpdate();
    }

    @Override
    public boolean isDownloadingUpdate() {
        return isCustomUpdate() && NanogramUpdater.getInstance().isDownloading();
    }

    @Override
    public float getDownloadingUpdateProgress() {
        return NanogramUpdater.getInstance().getDownloadingProgress();
    }

    @Override
    public File getDownloadedUpdateFile() {
        return isCustomUpdate() ? NanogramUpdater.getInstance().getDownloadedFile() : null;
    }

    @Override
    public IUpdateLayout takeUpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        return isCustomUpdate() ? new NanogramUpdateLayout(activity, sideMenuContainer) : null;
    }

    @Override
    public boolean showCustomUpdateAppPopup(Context context, BetaUpdate update, int account) {
        try {
            new NanogramUpdateAlertDialog(context, update, account).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }
}
