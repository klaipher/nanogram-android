package org.telegram.messenger;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.app.PendingIntent;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.telegram.ui.Components.AlertsCreator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nanogram's own update channel: a small JSON manifest and the APK, both published on GitHub releases.
 * Telegram's server update method is not used for Nanogram (our api_id gets no updates there).
 */
public class NanogramUpdater {

    public static final String MANIFEST_URL = "https://github.com/klaipher/nanogram-android-releases/releases/latest/download/update.json";
    private static final String APK_URL_PREFIX = "https://github.com/klaipher/nanogram-android-releases/releases/download/";
    private static final String INSTALL_ACTION = "org.klaipher.nanogram.UPDATE_INSTALL_RESULT";
    private static final long CHECK_INTERVAL = 6 * 60 * 60 * 1000L;
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;

    private static NanogramUpdater instance;

    public static NanogramUpdater getInstance() {
        if (instance == null) {
            instance = new NanogramUpdater();
        }
        return instance;
    }

    public static boolean isEnabled() {
        return !BuildVars.DEBUG_VERSION || getInstance().isDebugToggleOn();
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String versionName;
    private int versionCode;
    private String buildNumber;
    private String apkUrl;
    private String apkSha256;
    private long apkSize;
    private String notes;
    private long lastCheck;
    private String path;

    private volatile boolean checking;
    private volatile boolean lastCheckFailed;
    private volatile boolean downloading;
    private volatile boolean cancelled;
    private volatile float downloadingProgress;
    private volatile boolean installing;
    private volatile String error;

    private NanogramUpdater() {
        load();
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nanogram_updater", Activity.MODE_PRIVATE);
    }

    public boolean isDebugToggleOn() {
        return prefs().getBoolean("debugEnabled", false);
    }

    public void setDebugToggle(boolean on) {
        prefs().edit().putBoolean("debugEnabled", on).apply();
    }

    private void load() {
        final SharedPreferences p = prefs();
        versionName = p.getString("versionName", null);
        versionCode = p.getInt("versionCode", 0);
        buildNumber = p.getString("buildNumber", null);
        apkUrl = p.getString("apkUrl", null);
        apkSha256 = p.getString("apkSha256", null);
        apkSize = p.getLong("apkSize", 0L);
        notes = p.getString("notes", null);
        lastCheck = p.getLong("lastCheck", 0L);
        path = p.getString("path", null);
        if (versionCode <= getInstalledVersionCode()) {
            clearUpdate();
            save();
        } else if (!TextUtils.isEmpty(path) && !new File(path).exists()) {
            path = null;
            save();
        }
    }

    private void clearUpdate() {
        deleteDownloaded();
        versionName = null;
        versionCode = 0;
        buildNumber = null;
        apkUrl = null;
        apkSha256 = null;
        apkSize = 0;
        notes = null;
    }

    private void save() {
        prefs().edit()
            .putString("versionName", versionName)
            .putInt("versionCode", versionCode)
            .putString("buildNumber", buildNumber)
            .putString("apkUrl", apkUrl)
            .putString("apkSha256", apkSha256)
            .putLong("apkSize", apkSize)
            .putString("notes", notes)
            .putLong("lastCheck", lastCheck)
            .putString("path", path)
            .apply();
    }

    private void deleteDownloaded() {
        if (!TextUtils.isEmpty(path)) {
            try {
                new File(path).delete();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        path = null;
    }

    private static int getInstalledVersionCode() {
        try {
            return ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            FileLog.e(e);
            return Integer.MAX_VALUE;
        }
    }

    public BetaUpdate getUpdate() {
        if (versionName == null || versionCode == 0) {
            return null;
        }
        return new BetaUpdate(versionName, versionCode, notes);
    }

    public String getVersionName() {
        return versionName;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public long getApkSize() {
        return apkSize;
    }

    public boolean isChecking() {
        return checking;
    }

    public boolean hasCheckFailed() {
        return lastCheckFailed;
    }

    public boolean isInstalling() {
        return installing;
    }

    public String getError() {
        return error;
    }

    public long getLastCheckTime() {
        return lastCheck;
    }

    public boolean isDownloading() {
        return downloading;
    }

    public float getDownloadingProgress() {
        return downloadingProgress;
    }

    public File getDownloadedFile() {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        final File file = new File(path);
        if (!file.exists()) {
            path = null;
            save();
            return null;
        }
        return file;
    }

    public void checkForUpdate(boolean force, Runnable whenDone) {
        if (checking) {
            return;
        }
        if (!force && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL && System.currentTimeMillis() >= lastCheck) {
            if (whenDone != null) {
                whenDone.run();
            }
            return;
        }
        checking = true;
        lastCheckFailed = false;
        notifyChanged();
        executor.execute(() -> {
            JSONObject json = null;
            try {
                json = new JSONObject(fetchText(MANIFEST_URL));
            } catch (Exception e) {
                FileLog.e("Nanogram update check failed", e);
            }
            final JSONObject result = json;
            AndroidUtilities.runOnUIThread(() -> {
                checking = false;
                final int oldCode = versionCode;
                if (result == null) {
                    lastCheckFailed = true;
                } else {
                    try {
                        applyManifest(result);
                        lastCheck = System.currentTimeMillis();
                        save();
                    } catch (Exception e) {
                        FileLog.e("Nanogram update manifest rejected", e);
                        lastCheckFailed = true;
                    }
                }
                if (versionCode != oldCode || !lastCheckFailed) {
                    notifyChanged();
                }
                if (whenDone != null) {
                    whenDone.run();
                }
            });
        });
    }

    private static String fetchText(String address) throws Exception {
        final HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200) {
                throw new Exception("HTTP " + connection.getResponseCode());
            }
            try (InputStream in = connection.getInputStream()) {
                final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                final byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_MANIFEST_BYTES) {
                        throw new Exception("manifest too large");
                    }
                }
                return out.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }

    private void applyManifest(JSONObject json) throws Exception {
        final int newCode = json.getInt("versionCode");
        final String newName = json.getString("versionName");
        final String newUrl = json.getString("apkUrl");
        final String newSha = json.getString("apkSha256").toLowerCase(Locale.US);
        final int minSdk = json.optInt("minSdk", 0);
        if (!newUrl.startsWith(APK_URL_PREFIX) || !newSha.matches("[0-9a-f]{64}") || newCode <= 0) {
            throw new Exception("invalid manifest");
        }
        if (newCode <= getInstalledVersionCode() || minSdk > Build.VERSION.SDK_INT) {
            clearUpdate();
            return;
        }
        if (newCode != versionCode || !newSha.equals(apkSha256)) {
            deleteDownloaded();
        }
        versionName = newName;
        versionCode = newCode;
        buildNumber = json.optString("buildNumber", "");
        apkUrl = newUrl;
        apkSha256 = newSha;
        apkSize = json.optLong("apkSize", 0L);
        notes = json.optString("notes", null);
    }

    public void downloadUpdate() {
        if (downloading || apkUrl == null || getDownloadedFile() != null) {
            return;
        }
        downloading = true;
        cancelled = false;
        error = null;
        downloadingProgress = 0f;
        notifyLoading();
        final String url = apkUrl;
        final String sha = apkSha256;
        final long expectedSize = apkSize;
        final int code = versionCode;
        executor.execute(() -> {
            File result = null;
            String failure = null;
            final File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "nanogram-update");
            dir.mkdirs();
            final File part = new File(dir, "Nanogram-" + code + ".apk.part");
            final File apk = new File(dir, "Nanogram-" + code + ".apk");
            try {
                final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                try {
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    if (connection.getResponseCode() != 200) {
                        throw new Exception("HTTP " + connection.getResponseCode());
                    }
                    final long total = connection.getContentLengthLong() > 0 ? connection.getContentLengthLong() : expectedSize;
                    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    long done = 0;
                    int lastPercent = -1;
                    try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                        final byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            if (cancelled) {
                                throw new InterruptedException();
                            }
                            out.write(buffer, 0, read);
                            digest.update(buffer, 0, read);
                            done += read;
                            final int percent = total > 0 ? (int) (done * 100 / total) : 0;
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                downloadingProgress = percent / 100f;
                                AndroidUtilities.runOnUIThread(this::notifyLoading);
                            }
                        }
                    }
                    if (expectedSize > 0 && done != expectedSize) {
                        throw new Exception("size mismatch");
                    }
                    if (!toHex(digest.digest()).equals(sha)) {
                        throw new Exception("checksum mismatch");
                    }
                } finally {
                    connection.disconnect();
                }
                if (apk.exists() && !apk.delete()) {
                    throw new Exception("cannot replace old file");
                }
                if (!part.renameTo(apk)) {
                    throw new Exception("cannot finish file");
                }
                failure = verifyArchive(apk, code);
                if (failure == null) {
                    result = apk;
                }
            } catch (InterruptedException e) {
                failure = null;
            } catch (Exception e) {
                FileLog.e("Nanogram update download failed", e);
                failure = e.getMessage() == null ? "download failed" : e.getMessage();
            }
            if (result == null) {
                part.delete();
                apk.delete();
            }
            final File finalResult = result;
            final String finalFailure = failure;
            AndroidUtilities.runOnUIThread(() -> {
                downloading = false;
                error = finalFailure;
                if (finalResult != null) {
                    path = finalResult.getAbsolutePath();
                    downloadingProgress = 1f;
                    save();
                }
                notifyChanged();
            });
        });
    }

    public void cancelDownloadingUpdate() {
        if (!downloading) {
            return;
        }
        cancelled = true;
    }

    private static String verifyArchive(File apk, int expectedCode) {
        final Context context = ApplicationLoader.applicationContext;
        final PackageManager pm = context.getPackageManager();
        try {
            final int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            final PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            if (archive == null) {
                return "not a valid APK";
            }
            if (!context.getPackageName().equals(archive.packageName)) {
                return "wrong package";
            }
            final long archiveCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? archive.getLongVersionCode() : archive.versionCode;
            if (archiveCode != expectedCode) {
                return "version mismatch";
            }
            final TreeSet<String> archiveSigners = signerDigests(archive);
            final TreeSet<String> installedSigners = signerDigests(pm.getPackageInfo(context.getPackageName(), flags));
            if (archiveSigners.isEmpty() || !archiveSigners.equals(installedSigners)) {
                return "signature mismatch";
            }
            return null;
        } catch (Exception e) {
            FileLog.e(e);
            return "verification failed";
        }
    }

    @SuppressWarnings("deprecation")
    private static TreeSet<String> signerDigests(PackageInfo info) throws Exception {
        Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            final SigningInfo signingInfo = info.signingInfo;
            if (signingInfo != null) {
                signatures = signingInfo.getApkContentsSigners();
            }
        } else {
            signatures = info.signatures;
        }
        final TreeSet<String> digests = new TreeSet<>();
        if (signatures != null) {
            for (Signature signature : signatures) {
                digests.add(toHex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
            }
        }
        return digests;
    }

    private static String toHex(byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    public void install(Activity activity) {
        final File file = getDownloadedFile();
        if (file == null || installing) {
            return;
        }
        final Context context = ApplicationLoader.applicationContext;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.getPackageManager().canRequestPackageInstalls()) {
            if (activity != null) {
                AlertsCreator.createApkRestrictedDialog(activity, null).show();
            }
            return;
        }
        installing = true;
        error = null;
        notifyChanged();
        final InstallReceiver receiver = new InstallReceiver();
        ContextCompat.registerReceiver(context, receiver, new IntentFilter(INSTALL_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED);
        executor.execute(() -> {
            String failure = null;
            final PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            int sessionId = -1;
            try {
                if (!TextUtils.isEmpty(verifyArchive(file, versionCode))) {
                    throw new Exception("verification failed");
                }
                final PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(context.getPackageName());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                }
                sessionId = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    try (InputStream in = new FileInputStream(file); OutputStream out = session.openWrite("nanogram.apk", 0, file.length())) {
                        final byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            out.write(buffer, 0, read);
                        }
                        session.fsync(out);
                    }
                    final Intent intent = new Intent(INSTALL_ACTION).setPackage(context.getPackageName());
                    final int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
                    session.commit(PendingIntent.getBroadcast(context, sessionId, intent, flags).getIntentSender());
                }
            } catch (Exception e) {
                FileLog.e("Nanogram update install failed", e);
                failure = e.getMessage() == null ? "install failed" : e.getMessage();
                if (sessionId != -1) {
                    try {
                        installer.abandonSession(sessionId);
                    } catch (Exception ignore) {
                    }
                }
            }
            if (failure != null) {
                final String finalFailure = failure;
                AndroidUtilities.runOnUIThread(() -> receiver.finish(finalFailure, false));
            }
        });
    }

    private final class InstallReceiver extends BroadcastReceiver {
        private boolean finished;

        @Override
        @SuppressWarnings("deprecation")
        public void onReceive(Context context, Intent intent) {
            final int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                final Intent confirm = Build.VERSION.SDK_INT >= 33 ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class) : intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    try {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(confirm);
                    } catch (Exception e) {
                        FileLog.e(e);
                        finish("cannot open installer", false);
                    }
                }
                return;
            }
            final boolean aborted = status == PackageInstaller.STATUS_FAILURE_ABORTED;
            finish(status == PackageInstaller.STATUS_SUCCESS ? null : intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), aborted);
        }

        void finish(String failure, boolean keepFile) {
            if (finished) {
                return;
            }
            finished = true;
            try {
                ApplicationLoader.applicationContext.unregisterReceiver(this);
            } catch (Exception ignore) {
            }
            installing = false;
            if (failure != null) {
                error = failure;
                if (!keepFile) {
                    deleteDownloaded();
                    save();
                }
            }
            notifyChanged();
        }
    }

    private void notifyChanged() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    private void notifyLoading() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading);
    }
}
