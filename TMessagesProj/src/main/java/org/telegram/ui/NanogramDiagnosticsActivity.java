package org.telegram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NanogramUpdater;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.nanogram.NanogramDiagnostics;
import org.telegram.messenger.nanogram.NanogramLog;
import org.telegram.messenger.nanogram.NanogramProfiling;
import org.telegram.messenger.nanogram.NanogramSystemInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Nanogram diagnostics: what happened lately, the verbose-log switch, and sending a diagnostics zip
 * through the single Send report flow.
 */
public class NanogramDiagnosticsActivity extends UniversalFragment {

    private static final int ID_SHARE = 1;
    private static final int ID_VERBOSE = 3;
    private static final int ID_CLEAR = 4;
    private static final int ID_INFO = 5;
    private static final int ID_UPDATE = 6;
    private static final int ID_TRACE = 7;
    private static final int ID_TRACE_TRIGGERS = 8;

    private boolean recordingTrace;

    private List<NanogramDiagnostics.Problem> problems = new ArrayList<>();
    private long logsSize;
    private boolean loaded;

    @Override
    public boolean onFragmentCreate() {
        loadState();
        return super.onFragmentCreate();
    }

    private void loadState() {
        final Context context = ApplicationLoader.applicationContext;
        NanogramDiagnostics.runInBackground(() -> {
            final List<NanogramDiagnostics.Problem> found = NanogramDiagnostics.recentProblems(context, 10);
            final long size = NanogramLog.logsSize();
            // Marked as seen once listed: the next visit shows only what is new since this one.
            NanogramDiagnostics.markViewed(context);
            AndroidUtilities.runOnUIThread(() -> {
                problems = found;
                logsSize = size;
                loaded = true;
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        });
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
        return getString(R.string.NanogramDiagnosticsTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final Context context = ApplicationLoader.applicationContext;

        items.add(UItem.asHeader(getString(R.string.NanogramDiagnosticsSummary)));
        items.add(UItem.asButton(ID_INFO, getString(R.string.NanogramDiagnosticsDevice), NanogramSystemInfo.summaryLine()));
        items.add(UItem.asButton(ID_INFO, getString(R.string.NanogramDiagnosticsApp), NanogramDiagnostics.appLabel(context)));
        items.add(UItem.asButton(ID_INFO, getString(R.string.NanogramDiagnosticsLogSize), AndroidUtilities.formatFileSize(logsSize)));
        if (ApplicationLoader.applicationLoaderInstance.isCustomUpdate()) {
            final String status = NanogramUpdater.getInstance().statusText();
            items.add(UItem.asButton(ID_UPDATE, getString(R.string.NgUpdateCheck), status == null ? "" : status));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.NanogramDiagnosticsProblems)));
        if (!loaded) {
            items.add(UItem.asShadow(getString(R.string.NanogramDiagnosticsLoading)));
        } else if (problems.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.NanogramDiagnosticsNoProblems)));
        } else {
            final NanogramDiagnostics.Problem newest = problems.get(0);
            items.add(UItem.asShadow(formatString(R.string.NanogramDiagnosticsClosedUnexpectedly, NanogramDiagnostics.formatTime(newest.time), newest.text)));
            for (NanogramDiagnostics.Problem problem : problems) {
                items.add(UItem.asButton(ID_INFO, NanogramDiagnostics.formatTime(problem.time), (problem.isNew ? getString(R.string.NanogramDiagnosticsNew) + " · " : "") + problem.text));
            }
            items.add(UItem.asShadow(null));
        }

        items.add(UItem.asHeader(getString(R.string.NanogramDiagnosticsSend)));
        items.add(UItem.asButton(ID_SHARE, R.drawable.msg_send, getString(R.string.NanogramSendReport)).accent());
        items.add(UItem.asShadow(getString(NanogramLog.verboseRecently() ? R.string.NanogramSendReportInfoVerbose : R.string.NanogramSendReportInfo)));

        if (NanogramProfiling.isSupported()) {
            items.add(UItem.asHeader(getString(R.string.NanogramDiagnosticsTraces)));
            items.add(UItem.asButton(ID_TRACE, getString(R.string.NanogramDiagnosticsRecordTrace), recordingTrace ? getString(R.string.NanogramDiagnosticsRecording) : ""));
            if (NanogramProfiling.triggersSupported()) {
                items.add(UItem.asCheck(ID_TRACE_TRIGGERS, getString(NanogramProfiling.coldStartTriggerSupported() ? R.string.NanogramDiagnosticsTraceTriggers : R.string.NanogramDiagnosticsTraceTriggersAnr)).setChecked(NanogramProfiling.isTriggersEnabled(ApplicationLoader.applicationContext)));
            }
            items.add(UItem.asShadow(getString(R.string.NanogramDiagnosticsTracesInfo)));
        }

        items.add(UItem.asHeader(getString(R.string.NanogramDiagnosticsLogs)));
        items.add(UItem.asCheck(ID_VERBOSE, getString(R.string.NanogramDiagnosticsVerbose)).setChecked(NanogramLog.isVerbose()));
        items.add(UItem.asShadow(getString(R.string.NanogramDiagnosticsVerboseInfo)));
        items.add(UItem.asButton(ID_CLEAR, getString(R.string.NanogramDiagnosticsClear)).red());
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_SHARE:
                NanogramSendReport.start(this);
                break;
            case ID_VERBOSE: {
                NanogramLog.setVerbose(!NanogramLog.isVerbose());
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), getString(R.string.NanogramDiagnosticsRestartHint), Toast.LENGTH_LONG).show();
                }
                listView.adapter.update(true);
                break;
            }
            case ID_TRACE: {
                if (recordingTrace) {
                    break;
                }
                recordingTrace = true;
                listView.adapter.update(true);
                NanogramProfiling.recordSystemTrace(ApplicationLoader.applicationContext, Runnable::run, (file, error) -> AndroidUtilities.runOnUIThread(() -> {
                    recordingTrace = false;
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                    if (getParentActivity() != null) {
                        Toast.makeText(getParentActivity(), file != null ? getString(R.string.NanogramDiagnosticsTraceSaved) : error, Toast.LENGTH_LONG).show();
                    }
                }));
                break;
            }
            case ID_TRACE_TRIGGERS: {
                NanogramProfiling.setTriggersEnabled(ApplicationLoader.applicationContext, !NanogramProfiling.isTriggersEnabled(ApplicationLoader.applicationContext));
                listView.adapter.update(true);
                break;
            }
            case ID_UPDATE: {
                final NanogramUpdater updater = NanogramUpdater.getInstance();
                if (updater.isDownloading() || updater.isChecking() || updater.isInstalling()) {
                    break;
                }
                if (updater.getDownloadedFile() != null) {
                    updater.install(getParentActivity());
                } else if (updater.getUpdate() != null) {
                    ApplicationLoader.applicationLoaderInstance.showCustomUpdateAppPopup(getParentActivity(), updater.getUpdate(), currentAccount);
                } else {
                    updater.checkForUpdate(true, () -> {
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                        if (updater.getUpdate() == null && !updater.hasCheckFailed()) {
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.YourVersionIsLatest)).show();
                        }
                    });
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                break;
            }
            case ID_CLEAR: {
                final Context context = ApplicationLoader.applicationContext;
                NanogramDiagnostics.runInBackground(() -> {
                    NanogramDiagnostics.clearAll(context);
                    final long size = NanogramLog.logsSize();
                    AndroidUtilities.runOnUIThread(() -> {
                        logsSize = size;
                        problems = new ArrayList<>();
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                        if (getParentActivity() != null) {
                            Toast.makeText(getParentActivity(), getString(R.string.NanogramDiagnosticsCleared), Toast.LENGTH_SHORT).show();
                        }
                    });
                });
                break;
            }
            default:
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
