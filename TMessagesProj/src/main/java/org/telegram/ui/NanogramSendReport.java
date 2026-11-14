package org.telegram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.nanogram.NanogramDiagnostics;
import org.telegram.messenger.nanogram.NanogramLog;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.util.ArrayList;

/**
 * The one tester-visible "Send report": a short note of what is included, then one zip with everything,
 * sent to a Nanogram chat or through the share sheet.
 */
public final class NanogramSendReport {

    private final BaseFragment fragment;

    private NanogramSendReport(BaseFragment fragment) {
        this.fragment = fragment;
    }

    public static void start(BaseFragment fragment) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final NanogramSendReport flow = new NanogramSendReport(fragment);
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(getString(R.string.NanogramSendReport));
        builder.setMessage(getString(NanogramLog.verboseRecently() ? R.string.NanogramSendReportInfoVerbose : R.string.NanogramSendReportInfo));
        builder.setPositiveButton(getString(R.string.NanogramDiagnosticsSendToChat), (d, w) -> flow.createZip(flow::pickChat));
        builder.setNeutralButton(getString(R.string.NanogramDiagnosticsShare), (d, w) -> flow.createZip(flow::shareZip));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    private interface ZipReady {
        void onZip(File zip);
    }

    private void createZip(ZipReady ready) {
        final Activity activity = fragment.getParentActivity();
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
            if (zip == null || fragment.getParentActivity() == null) {
                if (error != null && fragment.getParentActivity() != null) {
                    Toast.makeText(fragment.getParentActivity(), getString(R.string.NanogramDiagnosticsFailed), Toast.LENGTH_SHORT).show();
                }
                return;
            }
            ready.onZip(zip);
        });
    }

    private void shareZip(File zip) {
        final Activity activity = fragment.getParentActivity();
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
        picker.setDelegate((pickerFragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            final int account = pickerFragment != null ? pickerFragment.getCurrentAccount() : fragment.getCurrentAccount();
            final Activity toastActivity = fragment.getParentActivity();
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
            if (pickerFragment != null) {
                pickerFragment.finishFragment();
            }
            if (toastActivity != null) {
                Toast.makeText(toastActivity, getString(R.string.NanogramDiagnosticsSent), Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        fragment.presentFragment(picker);
    }
}
