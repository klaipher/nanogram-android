package org.telegram.messenger.nanogram;

import android.os.Build;
import android.os.StrictMode;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

/** Debug builds, or builds with the "strictmode" experimental flag, log main-thread disk/network work and leaks. */
public final class NanogramStrictMode {

    private NanogramStrictMode() {
    }

    public static boolean isEnabled() {
        return BuildVars.DEBUG_VERSION || NanogramSystemFlags.isEnabled(NanogramSystemFlags.STRICT_MODE);
    }

    public static void install() {
        if (!isEnabled()) {
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
