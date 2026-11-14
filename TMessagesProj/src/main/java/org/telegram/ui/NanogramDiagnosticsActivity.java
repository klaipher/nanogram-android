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
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.nanogram.NanogramDiagnostics;
import org.telegram.messenger.nanogram.NanogramLog;
import org.telegram.messenger.nanogram.NanogramSystemInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Nanogram diagnostics: what happened lately, the verbose-log switch, and sending a diagnostics zip
 * through the share sheet or a Nanogram chat picked by the user.
 */
public class NanogramDiagnosticsActivity extends UniversalFragment {

    private static final int ID_SHARE = 1;
    private static final int ID_CHAT = 2;
    private static final int ID_VERBOSE = 3;
    private static final int ID_CLEAR = 4;
    private static final int ID_INFO = 5;

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
        items.add(UItem.asButton(ID_SHARE, R.drawable.msg_share, getString(R.string.NanogramDiagnosticsShare)).accent());
        items.add(UItem.asButton(ID_CHAT, R.drawable.msg_send, getString(R.string.NanogramDiagnosticsSendToChat)).accent());
        items.add(UItem.asShadow(getString(NanogramLog.verboseRecently() ? R.string.NanogramDiagnosticsShareInfoVerbose : R.string.NanogramDiagnosticsShareInfo)));

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
                createZip(this::shareZip);
                break;
            case ID_CHAT:
                createZip(this::pickChat);
                break;
            case ID_VERBOSE: {
                NanogramLog.setVerbose(!NanogramLog.isVerbose());
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), getString(R.string.NanogramDiagnosticsRestartHint), Toast.LENGTH_LONG).show();
                }
                listView.adapter.update(true);
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

    private interface ZipReady {
        void onZip(File zip);
    }

    private void createZip(ZipReady ready) {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        NanogramDiagnostics.collectAsync(ApplicationLoader.applicationContext, (zip, error) -> {
            try {
                progress.dismiss();
            } catch (Exception ignore) {
            }
            if (zip == null || getParentActivity() == null || isFinishing()) {
                if (error != null && getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), getString(R.string.NanogramDiagnosticsFailed), Toast.LENGTH_SHORT).show();
                }
                return;
            }
            ready.onZip(zip);
        });
    }

    private void shareZip(File zip) {
        final Activity activity = getParentActivity();
        try {
            final Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", zip);
            final Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/zip");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, formatString(R.string.NanogramDiagnosticsSubject, NanogramDiagnostics.appLabel(activity)));
            send.setClipData(ClipData.newRawUri(null, uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(send, getString(R.string.NanogramDiagnosticsShare)));
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(activity, getString(R.string.NanogramDiagnosticsFailed), Toast.LENGTH_SHORT).show();
        }
    }

    private void pickChat(File zip) {
        final Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("canSelectTopics", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("allowSwitchAccount", true);
        args.putString("selectAlertString", getString(R.string.NanogramDiagnosticsSendToText));
        args.putString("selectAlertStringGroup", getString(R.string.NanogramDiagnosticsSendToText));
        final DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            final int account = fragment != null ? fragment.getCurrentAccount() : currentAccount;
            final AccountInstance accountInstance = AccountInstance.getInstance(account);
            final String caption = formatString(R.string.NanogramDiagnosticsSubject, NanogramDiagnostics.appLabel(ApplicationLoader.applicationContext));
            for (MessagesStorage.TopicKey key : dids) {
                final ArrayList<String> paths = new ArrayList<>();
                paths.add(zip.getAbsolutePath());
                MessageObject replyTo = null;
                if (key.topicId != 0) {
                    final TLRPC.TL_forumTopic topic = accountInstance.getMessagesController().getTopicsController().findTopic(-key.dialogId, key.topicId);
                    if (topic != null && topic.topicStartMessage != null) {
                        replyTo = new MessageObject(account, topic.topicStartMessage, false, false);
                        replyTo.isTopicMainMessage = true;
                    }
                }
                SendMessagesHelper.prepareSendingDocuments(accountInstance, paths, paths, null, caption, "application/zip", key.dialogId, replyTo, replyTo, null, null, null, true, 0, null, null, 0, false, 0);
            }
            if (fragment != null) {
                fragment.finishFragment();
            }
            if (getParentActivity() != null) {
                Toast.makeText(getParentActivity(), getString(R.string.NanogramDiagnosticsSent), Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        presentFragment(picker);
    }
}
