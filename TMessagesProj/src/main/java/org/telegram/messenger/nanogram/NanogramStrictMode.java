package org.telegram.messenger.nanogram;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.StrictMode;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

/** Debug builds, or builds with the "strictmode" experimental flag, log main-thread disk/network work and leaks. */
public final class NanogramStrictMode {

    private NanogramStrictMode() {
    }

    /** Release builds never read the flag file at startup: only debuggable builds can turn the flag on. */
    public static boolean isEnabled(Context context) {
        if (BuildVars.DEBUG_VERSION) {
            return true;
        }
        return context != null && (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 && NanogramSystemFlags.isEnabled(NanogramSystemFlags.STRICT_MODE);
    }

    public static void install(Context context) {
        if (!isEnabled(context)) {
            return;
        }
        try {
            final StrictMode.ThreadPolicy.Builder thread = new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog();
            final StrictMode.VmPolicy.Builder vm = new StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectLeakedSqlLiteObjects()
                    .detectActivityLeaks()
                    .detectFileUriExposure()
                    .penaltyLog();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vm.detectUnsafeIntentLaunch().detectContentUriWithoutPermission();
            }
            StrictMode.setThreadPolicy(thread.build());
            StrictMode.setVmPolicy(vm.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
