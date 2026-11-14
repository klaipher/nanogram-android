package org.telegram.messenger.nanogram;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.PowerManager;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.system.Os;
import android.text.TextUtils;
import android.system.OsConstants;
import android.util.DisplayMetrics;
import android.webkit.WebView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Builds the device, runtime and permission report. Every item is collected on its own so one failing
 * system call only costs that line. Items that exist only on newer Android versions say so. Nothing
 * here reads messages, contacts, phone numbers, tokens, keys or file contents.
 */
public final class NanogramSystemInfo {

    private interface Item {
        void run() throws Throwable;
    }

    private static final class Report {
        final StringBuilder sb = new StringBuilder(8192);

        void section(String title) {
            sb.append("\n== ").append(title).append(" ==\n");
        }

        void line(String key, Object value) {
            sb.append(key).append(": ").append(value).append('\n');
        }

        void since(String key, int api, Object value) {
            sb.append(key).append(" (API ").append(api).append("+): ").append(value).append('\n');
        }

        void unavailable(String key, int api) {
            sb.append(key).append(": not available (needs API ").append(api).append(", device has ").append(Build.VERSION.SDK_INT).append(")\n");
        }

        void run(String name, Item item) {
            final int mark = sb.length();
            try {
                item.run();
            } catch (Throwable e) {
                sb.setLength(mark);
                sb.append(name).append(": unavailable (").append(e.getClass().getSimpleName()).append(")\n");
            }
        }
    }

    private NanogramSystemInfo() {
    }

    public static String summaryLine() {
        return Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    public static String build(Context context) {
        final Report r = new Report();
        r.sb.append("Nanogram diagnostics report\ngenerated: ").append(NanogramDiagnostics.formatTime(System.currentTimeMillis())).append('\n');
        r.run("app", () -> app(context, r));
        r.run("device", () -> device(context, r));
        r.run("storage", () -> storage(context, r));
        r.run("power", () -> power(context, r));
        r.run("network", () -> network(context, r));
        r.run("permissions", () -> permissions(context, r));
        r.run("notification channels", () -> channels(context, r));
        r.run("memory", () -> memory(context, r));
        r.run("messenger state", () -> messenger(r));
        return r.sb.toString();
    }

    private static void app(Context context, Report r) throws Exception {
        r.section("App");
        final PackageManager pm = context.getPackageManager();
        final PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
        r.line("package", context.getPackageName());
        r.line("versionName", info.versionName);
        r.line("versionCode", info.getLongVersionCode());
        r.line("build flavor", BuildVars.DEBUG_VERSION ? "debug" : "release");
        r.line("first install", NanogramDiagnostics.formatTime(info.firstInstallTime));
        r.line("last update", NanogramDiagnostics.formatTime(info.lastUpdateTime));
        r.line("targetSdk", context.getApplicationInfo().targetSdkVersion);
        r.line("process uptime", ((SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000) + " s");
        r.line("verbose logs", NanogramLog.isVerbose());
        r.run("install source", () -> {
            final android.content.pm.InstallSourceInfo source = pm.getInstallSourceInfo(context.getPackageName());
            r.line("installing package", source.getInstallingPackageName());
            if (Build.VERSION.SDK_INT >= 34) {
                r.since("update ownership", 34, source.getUpdateOwnerPackageName());
            }
        });
        r.run("signing certificate", () -> r.line("signing cert SHA-256", signingFingerprint(context)));
        r.run("webview", () -> {
            final PackageInfo webView = WebView.getCurrentWebViewPackage();
            r.line("WebView", webView == null ? "none" : webView.packageName + " " + webView.versionName);
        });
    }

    private static String signingFingerprint(Context context) throws Exception {
        final PackageManager pm = context.getPackageManager();
        final PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        final SigningInfo signing = info.signingInfo;
        final Signature[] signers = signing == null ? null : signing.getApkContentsSigners();
        final byte[] cert = signers == null || signers.length == 0 ? null : signers[0].toByteArray();
        if (cert == null) {
            return "none";
        }
        final byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format(Locale.US, "%02X", digest[i] & 0xFF));
        }
        return sb.toString();
    }

    private static void device(Context context, Report r) throws Exception {
        r.section("Device");
        r.line("manufacturer", Build.MANUFACTURER);
        r.line("brand", Build.BRAND);
        r.line("model", Build.MODEL);
        r.line("device / product / hardware / board", Build.DEVICE + " / " + Build.PRODUCT + " / " + Build.HARDWARE + " / " + Build.BOARD);
        r.line("SoC", Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL);
        r.line("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + (Build.VERSION.CODENAME.equals("REL") ? "" : ", " + Build.VERSION.CODENAME) + ")");
        r.line("security patch", Build.VERSION.SECURITY_PATCH);
        r.line("build fingerprint", Build.FINGERPRINT);
        r.line("supported ABIs", TextUtils.join(",", Build.SUPPORTED_ABIS));
        r.line("64-bit process", Process.is64Bit());
        r.line("CPU cores", Runtime.getRuntime().availableProcessors());
        final long pageSize = Os.sysconf(OsConstants._SC_PAGESIZE);
        r.line("memory page size", pageSize + (pageSize == 16384 ? " (16 KB device)" : ""));
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        r.line("low-RAM device", am.isLowRamDevice());
        r.line("app heap class MB (normal / large)", am.getMemoryClass() + " / " + am.getLargeMemoryClass());
        final ConfigurationInfo gl = am.getDeviceConfigurationInfo();
        r.line("OpenGL ES", gl.getGlEsVersion() + " (reqGlEsVersion 0x" + Integer.toHexString(gl.reqGlEsVersion) + ")");
        final DisplayMetrics dm = context.getResources().getDisplayMetrics();
        r.line("display", dm.widthPixels + "x" + dm.heightPixels + " @ " + dm.densityDpi + " dpi, font scale " + context.getResources().getConfiguration().fontScale);
        r.line("night mode", (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        r.line("locale", Locale.getDefault().toLanguageTag());
        final TimeZone tz = TimeZone.getDefault();
        r.line("timezone", tz.getID() + " (UTC" + (tz.getRawOffset() >= 0 ? "+" : "") + (tz.getRawOffset() / 3600000f) + ")");
    }

    private static void storage(Context context, Report r) {
        r.section("Storage");
        r.run("internal", () -> {
            final StatFs fs = new StatFs(context.getFilesDir().getAbsolutePath());
            r.line("internal free / total MB", (fs.getAvailableBytes() >> 20) + " / " + (fs.getTotalBytes() >> 20));
        });
        r.run("external", () -> {
            final File ext = context.getExternalFilesDir(null);
            if (ext == null) {
                r.line("app external storage", "unavailable");
            } else {
                final StatFs fs = new StatFs(ext.getAbsolutePath());
                r.line("app external free / total MB", (fs.getAvailableBytes() >> 20) + " / " + (fs.getTotalBytes() >> 20));
            }
        });
        r.run("logs size", () -> r.line("logs size KB", NanogramLog.logsSize() / 1024));
        r.run("app storage", () -> {
            r.line("app files MB", dirSize(context.getFilesDir(), new int[]{0}) >> 20);
            r.line("app cache MB", dirSize(context.getCacheDir(), new int[]{0}) >> 20);
            r.line("app databases MB", dirSize(context.getDatabasePath("x").getParentFile(), new int[]{0}) >> 20);
            final File ext = context.getExternalFilesDir(null);
            if (ext != null) {
                r.line("app external files MB", dirSize(ext, new int[]{0}) >> 20);
            }
        });
    }

    // Bounded walk: a huge cache must not turn the report into a long disk scan.
    private static long dirSize(File dir, int[] visited) {
        long total = 0;
        final File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (++visited[0] > 200_000) {
                break;
            }
            total += file.isDirectory() ? dirSize(file, visited) : file.length();
        }
        return total;
    }

    private static void power(Context context, Report r) {
        r.section("Power and background limits");
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        r.run("battery", () -> {
            final Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) {
                r.line("battery", "unavailable");
                return;
            }
            final int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            final int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            final int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            r.line("battery", (scale > 0 ? level * 100 / scale : level) + "%, charging status " + battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    + ", plugged " + (plugged == 0 ? "no" : plugged == BatteryManager.BATTERY_PLUGGED_AC ? "AC" : plugged == BatteryManager.BATTERY_PLUGGED_USB ? "USB" : "wireless/other")
                    + ", temperature " + battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f + " C, health " + battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1));
        });
        r.run("power save", () -> r.line("power save mode", pm.isPowerSaveMode()));
        r.run("doze", () -> r.line("device idle (doze)", pm.isDeviceIdleMode()));
        r.run("interactive", () -> r.line("screen interactive", pm.isInteractive()));
        r.run("battery optimization", () -> r.line("ignoring battery optimizations", pm.isIgnoringBatteryOptimizations(context.getPackageName())));
        r.run("thermal", () -> {
            r.line("thermal status", thermalName(pm.getCurrentThermalStatus()));
            r.line("thermal headroom (10 s forecast, 1.0 = throttling)", pm.getThermalHeadroom(10));
        });
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        r.run("background restricted", () -> {
            r.line("background restricted by the user", am.isBackgroundRestricted());
        });
        r.run("standby bucket", () -> {
            final UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            r.line("app standby bucket", bucketName(usm.getAppStandbyBucket()));
        });
        r.run("data saver", () -> {
            final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final int status = cm.getRestrictBackgroundStatus();
            r.line("data saver", status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED ? "off"
                    : status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED ? "on, app allowed" : "on, app restricted");
        });
    }

    private static String thermalName(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "none";
            case PowerManager.THERMAL_STATUS_LIGHT: return "light";
            case PowerManager.THERMAL_STATUS_MODERATE: return "moderate";
            case PowerManager.THERMAL_STATUS_SEVERE: return "severe";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "shutdown";
            default: return String.valueOf(status);
        }
    }

    private static String bucketName(int bucket) {
        switch (bucket) {
            case UsageStatsManager.STANDBY_BUCKET_ACTIVE: return "active";
            case UsageStatsManager.STANDBY_BUCKET_WORKING_SET: return "working set";
            case UsageStatsManager.STANDBY_BUCKET_FREQUENT: return "frequent";
            case UsageStatsManager.STANDBY_BUCKET_RARE: return "rare";
            case UsageStatsManager.STANDBY_BUCKET_RESTRICTED: return "restricted";
            default: return "bucket " + bucket;
        }
    }

    private static void network(Context context, Report r) {
        r.section("Network");
        final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        r.run("active network", () -> {
            final Network network = cm.getActiveNetwork();
            final NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
            if (caps == null) {
                r.line("active network", "none");
                return;
            }
            final List<String> transports = new ArrayList<>();
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("wifi");
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.add("cellular");
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("ethernet");
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) transports.add("bluetooth");
            r.line("transport", transports.isEmpty() ? "other" : TextUtils.join("+", transports));
            r.line("validated (internet reachable)", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
            r.line("metered", !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
            r.line("VPN", caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN));
            r.line("roaming", !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING));
            r.line("estimated bandwidth down / up kbps", caps.getLinkDownstreamBandwidthKbps() + " / " + caps.getLinkUpstreamBandwidthKbps());
        });
        r.run("airplane mode", () -> r.line("airplane mode", Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0));
        r.run("proxy", () -> r.line("proxy in use", SharedConfig.currentProxy != null));
    }

    private static void permissions(Context context, Report r) {
        r.section("Permissions and special access");
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        r.run("notifications enabled", () -> r.line("notifications enabled", nm.areNotificationsEnabled()));
        r.run("POST_NOTIFICATIONS", () -> {
            if (Build.VERSION.SDK_INT >= 33) {
                r.since("POST_NOTIFICATIONS", 33, granted(context, Manifest.permission.POST_NOTIFICATIONS));
            } else {
                r.unavailable("POST_NOTIFICATIONS runtime permission", 33);
            }
        });
        r.run("do not disturb", () -> r.line("interruption filter", nm.getCurrentInterruptionFilter()));
        r.run("exact alarms", () -> {
            final AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            r.line("can schedule exact alarms", alarm.canScheduleExactAlarms());
        });
        r.run("full-screen intent", () -> {
            if (Build.VERSION.SDK_INT >= 34) {
                r.since("can use full-screen intents (incoming calls)", 34, nm.canUseFullScreenIntent());
            } else {
                r.unavailable("can use full-screen intents", 34);
            }
        });
        r.run("overlay", () -> r.line("can draw over other apps", Settings.canDrawOverlays(context)));
        r.run("runtime permissions", () -> {
            r.line("RECORD_AUDIO", granted(context, Manifest.permission.RECORD_AUDIO));
            r.line("CAMERA", granted(context, Manifest.permission.CAMERA));
            r.line("READ_CONTACTS", granted(context, Manifest.permission.READ_CONTACTS));
            r.line("BLUETOOTH_CONNECT", granted(context, Manifest.permission.BLUETOOTH_CONNECT));
        });
    }

    private static String granted(Context context, String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ? "granted" : "denied";
    }

    private static final Pattern GLOBAL_CHANNEL = Pattern.compile("^\\d+channel_(private|groups|channels|stories|reactions|silent)(_ia)?(_[0-9a-f]{1,5})?_-?\\d+$");

    private static void channels(Context context, Report r) {
        r.section("Notification channels");
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        final List<NotificationChannel> channels = nm.getNotificationChannels();
        int perChat = 0;
        int perChatBlocked = 0;
        for (NotificationChannel channel : channels) {
            final String id = channel.getId();
            final boolean blocked = channel.getImportance() == NotificationManager.IMPORTANCE_NONE;
            // Per-chat channels carry chat ids and chat titles: only counted, never listed.
            if (GLOBAL_CHANNEL.matcher(id).matches() || id.startsWith("incoming_calls") || id.startsWith("Other") || id.startsWith("wallet_tonconnect")) {
                r.line("channel " + id.replaceAll("_-?\\d{6,}$", "").replaceAll("^Other\\d+$", "Other*"),
                        "importance " + channel.getImportance() + (blocked ? " (BLOCKED)" : "") + ", bypass DND " + channel.canBypassDnd());
            } else {
                perChat++;
                if (blocked) perChatBlocked++;
            }
        }
        r.line("other channels (per chat)", perChat + ", blocked " + perChatBlocked);
        r.line("total channels", channels.size());
    }

    private static void memory(Context context, Report r) {
        r.section("Memory and process");
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        r.run("system memory", () -> {
            final ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            r.line("system RAM available / total MB", (mi.availMem >> 20) + " / " + (mi.totalMem >> 20));
            r.line("low-memory threshold MB / currently low", (mi.threshold >> 20) + " / " + mi.lowMemory);
        });
        r.run("java heap", () -> {
            final Runtime rt = Runtime.getRuntime();
            r.line("java heap used / total / max MB", ((rt.totalMemory() - rt.freeMemory()) >> 20) + " / " + (rt.totalMemory() >> 20) + " / " + (rt.maxMemory() >> 20));
            r.line("native heap allocated / size MB", (Debug.getNativeHeapAllocatedSize() >> 20) + " / " + (Debug.getNativeHeapSize() >> 20));
        });
        r.run("pss", () -> {
            final Debug.MemoryInfo mi = new Debug.MemoryInfo();
            Debug.getMemoryInfo(mi);
            r.line("total PSS MB", mi.getTotalPss() / 1024);
            final Map<String, String> stats = mi.getMemoryStats();
            for (Map.Entry<String, String> entry : stats.entrySet()) {
                r.line("  " + entry.getKey() + " KB", entry.getValue());
            }
        });
        r.run("proc status", () -> {
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("VmRSS") || line.startsWith("VmSwap") || line.startsWith("VmPeak") || line.startsWith("VmHWM") || line.startsWith("Threads")) {
                        r.sb.append(line.replaceAll("\\s+", " ")).append('\n');
                    }
                }
            }
        });
        r.run("disk io", () -> {
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/io"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("write_bytes") || line.startsWith("read_bytes") || line.startsWith("wchar") || line.startsWith("rchar")) {
                        r.sb.append("io ").append(line).append('\n');
                    }
                }
            }
        });
        r.run("open files", () -> {
            final String[] fds = new File("/proc/self/fd").list();
            r.line("open file descriptors", fds == null ? "unavailable" : fds.length);
        });
    }

    private static void messenger(Report r) {
        r.section("Messenger state (no account data)");
        r.run("accounts", () -> r.line("signed-in accounts", UserConfig.getActivatedAccountsCount()));
        r.run("push", () -> {
            r.line("push type", SharedConfig.pushType);
            r.line("push status", SharedConfig.pushStringStatus);
            r.line("push token present", SharedConfig.pushString != null && !SharedConfig.pushString.isEmpty());
            if (SharedConfig.pushStringGetTimeStart > 0) {
                r.line("push token request ms", SharedConfig.pushStringGetTimeEnd > 0 ? SharedConfig.pushStringGetTimeEnd - SharedConfig.pushStringGetTimeStart : "pending");
            }
        });
        r.run("connection", () -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    r.line("account slot " + a, "connection " + connectionName(ConnectionsManager.getInstance(a).getConnectionState())
                            + ", registered for push " + UserConfig.getInstance(a).registeredForPush);
                }
            }
        });
        r.run("tablet", () -> r.line("tablet UI", AndroidUtilities.isTablet()));
    }

    private static String connectionName(int state) {
        switch (state) {
            case ConnectionsManager.ConnectionStateConnecting: return "connecting";
            case ConnectionsManager.ConnectionStateWaitingForNetwork: return "waiting for network";
            case ConnectionsManager.ConnectionStateConnected: return "connected";
            case ConnectionsManager.ConnectionStateConnectingToProxy: return "connecting to proxy";
            case ConnectionsManager.ConnectionStateUpdating: return "updating";
            default: return String.valueOf(state);
        }
    }
}
