# Google Play build (store flavor)

Owner decision 2026-11-14: Nanogram cannot sell Telegram's in-app products, so the store build shows no way to buy
them (Google Play Payments policy; the App Store 3.1.1 rule is the same on iOS). One switch, `StorePurchasePolicy`.

## Build

```
Tools/dev/ng build release --store play      # flavor afatPlay, same package and signing key as the GitHub build
Tools/dev/ng build release                   # flavor afat, GitHub build, unchanged
```

`afatPlay` is a release-only product flavor next to `afat` (`TMessagesProj_App/build.gradle`). It sets
`BuildConfig.STORE_FLAVOR = true`; `ApplicationLoaderImpl.isStoreFlavor()` hands that to the shared code, which asks
`StorePurchasePolicy.current()`. Flipping the flag to false for any build turns every entry point back on. Output
is `Nanogram-...-play.aab` (the bundle Google Play takes for new apps) plus `...-play.apk` for sideload testing; Play App Signing uses the existing release key.

What differs from the GitHub flavor, and nothing else does:

1. `StorePurchasePolicy` on: no Play Billing (the billing client never connects, the `BILLING` permission is removed
   by `src/afatPlay/AndroidManifest.xml`), the Premium, Stars, Premium-gift and giveaway buy paths are closed at their own entry points (table below). A Telegram-issued invoice link or bot invoice still opens the payment form: there is no guard on `PaymentFormActivity`, the same residual risk as iOS and Mac.
2. The in-app updater is off: `NanogramUpdater.isEnabled()` is false, so no update row, banner, download or install,
   and `REQUEST_INSTALL_PACKAGES` is removed from the manifest (Play forbids self-updates outside Play).

The GitHub flavor keeps today's behaviour (upstream's server flags decide what it offers, updater on).

## What stays

Premium status, badges and every Premium feature the account has; spending Stars already owned (paid reactions, paid
media, subscriptions, gifts paid with the existing balance); receiving, viewing, converting and selling gifts;
applying a received Premium gift code; the Settings and own-profile Stars row (shown when the server allows Stars, or the account has a balance or history), "Send a Gift" for star gifts paid from the balance; launching a giveaway that was already paid for (prepaid); bot payments for
physical goods and services (3.1.3(e) and Play policy allow them).

## Entry points (all behind `StorePurchasePolicy`)

| Entry point | Store build |
|---|---|
| `MessagesController.premiumPurchaseBlocked()` / `premiumFeaturesBlocked()` | true for accounts without Premium; this is upstream's own "premium locked" mode, which already hides Premium upsells in about 90 places (settings row, profile gift row, limit-reached sheets, Premium reaction/sticker/emoji lock sheets, `tg://premium_offer` via `PremiumPreviewFragment.onFragmentCreate`) |
| `MessagesController.starsEntryAvailable()` / `starGiftsAvailable()` | server-driven only (`!starsLocked` or a balance or history; `!premiumLocked`), so the Stars row and every "Send a Gift" entry stay in store builds |
| `MessagesController.starsPurchaseAvailable()` | false: every "balance too low, buy Stars" branch in `StarsController` (gifts, paid media, paid reactions, subscriptions, resale, upgrades) ends in the "not supported" dialog instead of a top-up |
| `PremiumPreviewFragment.buyPremium(...)` | returns at once; the buy button container is hidden (also for a Premium user's upgrade button) |
| `PremiumFeatureBottomSheet`, `PremiumPreviewBottomSheet`, `DoubledLimitsBottomSheet` | `show()` does nothing for accounts without Premium (gift-link preview with a received gift still shows) |
| `StarsIntroActivity.StarsOptionsSheet` (the Buy Stars list) | `show()` does nothing; opened by `tg://stars_topup`, the chat and gift "not enough Stars" bulletins, paid-reaction sheet, balance cloud, dice sheet |
| Stars screen (`StarsIntroActivity`) | Buy, Top up and Gift Stars buttons hidden; balance, history and stats stay. `BalanceCloud` "Buy more" link hidden; `StarsController.canBuy()` false so the "Stars needed" sheet lists no options |
| `GiftSheet.updatePremiumTiers()` and `StarsController.buyPremiumGift()` | no Premium gift tiers; star gifts paid with owned Stars stay |
| `BoostRepository.payGiftCode()`, `payGiveAway()`, `BoostPagerBottomSheet.show()` | no buying gift codes or giveaways; a prepaid giveaway can still be launched |
| `LoginActivity` paid SMS code ("pay for the code with Premium days") | button hidden |
| `BillingController.startConnection()`, `ApplicationLoader` startup | billing never connects |
| TON top-up | already off upstream unless the build is standalone, beta or Huawei, so off in both flavors |

Not purchases, left as is: the ads account link for channel owners (earnings, not a payment), TON wallet screens,
Fragment links for withdrawing, bot payment forms for goods.

## Checks

`StorePurchasePolicyTest` (pure JVM). Everything else needs a build: open Settings, a profile, a chat with a
Premium-only reaction, the Stars screen, a gift sheet, a channel's Boost screen and the login screen in the play flavor
and look for any price, Subscribe or Buy button. No app build was run for this change.
