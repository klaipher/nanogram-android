package org.telegram.utils.settings;

public final class StartupSettings {

    public static final BooleanSetting skipPlayBillingAtStartup =
        BooleanSetting.of("startup_skip_play_billing", false);

    public static final BooleanSetting lazyAccountSlots =
        BooleanSetting.of("startup_lazy_account_slots", false);

    private StartupSettings() {
    }
}
