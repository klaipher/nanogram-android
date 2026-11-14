package org.telegram.messenger;

/**
 * One switch for store builds (Google Play, App Store): Nanogram cannot sell Telegram's in-app products, so every
 * entry that would buy them is off. What the account already owns keeps working. Flipped by the build flavor.
 */
public final class StorePurchasePolicy {

    public enum Entry {
        PREMIUM_SUBSCRIPTION(true),
        PREMIUM_UPSELL(true),
        PREMIUM_GIFT(true),
        PREMIUM_GIVEAWAY(true),
        PREMIUM_AUTH_CODE(true),
        STARS_TOPUP(true),
        STARS_GIFT(true),
        STARS_GIVEAWAY(true),
        ADS_TOPUP(true),
        PURCHASE_LINK(true),
        PLAY_BILLING(true),
        OWNED_PREMIUM_FEATURES(false),
        OWNED_STARS_SPENDING(false),
        GIFT_RECEIVE_AND_CONVERT(false),
        BOT_PHYSICAL_GOODS_PAYMENT(false);

        final boolean buysTelegramProduct;

        Entry(boolean buysTelegramProduct) {
            this.buysTelegramProduct = buysTelegramProduct;
        }
    }

    private static final StorePurchasePolicy OPEN = new StorePurchasePolicy(false);
    private static final StorePurchasePolicy STORE = new StorePurchasePolicy(true);

    private final boolean restrict;

    private StorePurchasePolicy(boolean restrict) {
        this.restrict = restrict;
    }

    public static StorePurchasePolicy of(boolean storeBuild) {
        return storeBuild ? STORE : OPEN;
    }

    public static StorePurchasePolicy current() {
        final ApplicationLoader loader = ApplicationLoader.applicationLoaderInstance;
        return of(loader != null && loader.isStoreFlavor());
    }

    public boolean restrictsPurchases() {
        return restrict;
    }

    public boolean allows(Entry entry) {
        return !restrict || !entry.buysTelegramProduct;
    }

    public static boolean blocks(Entry entry) {
        return !current().allows(entry);
    }
}
