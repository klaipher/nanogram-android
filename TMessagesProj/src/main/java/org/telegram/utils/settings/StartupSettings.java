package org.telegram.utils.settings;

public final class StartupSettings {

    public static final BooleanSetting skipPlayBillingAtStartup =
        BooleanSetting.of("startup_skip_play_billing", false);

    private StartupSettings() {
    }
}
