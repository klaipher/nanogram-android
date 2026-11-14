package org.telegram.messenger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StorePurchasePolicyTest {

    private static final StorePurchasePolicy.Entry[] BUYING = {
        StorePurchasePolicy.Entry.PREMIUM_SUBSCRIPTION,
        StorePurchasePolicy.Entry.PREMIUM_UPSELL,
        StorePurchasePolicy.Entry.PREMIUM_GIFT,
        StorePurchasePolicy.Entry.PREMIUM_GIVEAWAY,
        StorePurchasePolicy.Entry.PREMIUM_AUTH_CODE,
        StorePurchasePolicy.Entry.STARS_TOPUP,
        StorePurchasePolicy.Entry.PLAY_BILLING,
    };

    private static final StorePurchasePolicy.Entry[] KEPT = {
        StorePurchasePolicy.Entry.OWNED_PREMIUM_FEATURES,
        StorePurchasePolicy.Entry.OWNED_STARS_SPENDING,
        StorePurchasePolicy.Entry.GIFT_RECEIVE_AND_CONVERT,
        StorePurchasePolicy.Entry.BOT_PHYSICAL_GOODS_PAYMENT,
    };

    @Test
    public void storePolicyBlocksEveryPurchaseEntry() {
        final StorePurchasePolicy policy = StorePurchasePolicy.of(true);
        for (StorePurchasePolicy.Entry entry : BUYING) {
            assertFalse(entry.name(), policy.allows(entry));
        }
    }

    @Test
    public void storePolicyKeepsWhatTheUserAlreadyHas() {
        final StorePurchasePolicy policy = StorePurchasePolicy.of(true);
        for (StorePurchasePolicy.Entry entry : KEPT) {
            assertTrue(entry.name(), policy.allows(entry));
        }
    }

    @Test
    public void openPolicyAllowsEverything() {
        final StorePurchasePolicy policy = StorePurchasePolicy.of(false);
        for (StorePurchasePolicy.Entry entry : StorePurchasePolicy.Entry.values()) {
            assertTrue(entry.name(), policy.allows(entry));
        }
    }

    @Test
    public void everyEntryIsClassified() {
        assertTrue(BUYING.length + KEPT.length == StorePurchasePolicy.Entry.values().length);
    }

    @Test
    public void restrictsFollowsTheSwitch() {
        assertTrue(StorePurchasePolicy.of(true).restrictsPurchases());
        assertFalse(StorePurchasePolicy.of(false).restrictsPurchases());
    }
}
