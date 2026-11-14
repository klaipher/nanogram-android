# How Nanogram for Android differs from upstream Telegram for Android

Kept current with every change that alters behaviour or build compared with upstream (DrKLO/Telegram 13.2.2 (7303)).

| Area | Difference | Why |
|---|---|---|
| Repositories | Telegram-specific submodules point at klaipher forks; tgcalls shares `klaipher/tgcalls` with iOS/Mac (branch `nanogram-android`); media fork branch `nanogram-android` | Control over patches, one tgcalls fork for all apps |
| Identity | `org.klaipher.nanogram`, app name Nanogram, own api_id/api_hash via BuildConfig, own release key, own Firebase config only | Separate app; no Telegram credentials or Firebase project |
| Fork flags | `IS_BILLING_UNAVAILABLE = true`, `SUPPORTS_PASSKEYS = false`, empty Play Store URL | Play billing and passkeys only work for Telegram's app ids |
| ABIs | arm64-v8a only by default (`-PnanogramAbis=all` for all four) | Speed and size; like Apple-Silicon-only on the Mac |
| versionCode | minutes since 2020-01-01 of the build number × 10 + ABI digit | Every build updates the previous one; marketing version follows upstream |
| Toolchain | Gradle 9.9, AGP 9.5, Kotlin 2.4, NDK r30, CMake 4.1, compileSdk 37, Java 17 | Current toolchain; targetSdk 36 and minSdk 23 unchanged |
| Native build | ccache, no LTO in debug, prebuilts rebuilt from submodules into a cache, 16 KB flexible page sizes, uncompressed native libs | Build speed, disk, device storage, 16 KB-page devices |
| Native deps | TDLib = iOS pin 0e88653d (1.8.69); abseil 20260817.0; BoringSSL 0.20261105.0; OpenH264 2.7.0; boost-regex 1.92.0 | Latest releases; one TDLib for iOS and Android |
| Gradle modules | 11 of 40 media modules; Huawei/HockeyApp/Standalone/test apps only with `-Pnanogram.allModules=true` | Configuration time |
| Biometrics | No FingerprintManager (removed from SDK 37): `androidx.biometric` 1.1.0 everywhere (FingerprintManagerCompat copy deleted); wallet uses the framework BiometricPrompt on R+ and AndroidX below; face/iris accepted where the device rates them Class 3 | Newer API on compileSdk 37, no stubs |
| WebRTC | `Candidate::set_type` without lifetimebound | NDK r30 clang rejects it (same as Mac tg_owt) |
| Branding | Sphere launcher icon like iOS/Mac (layered adaptive icon, themed monochrome, legacy and round), 5 alternate icons = iOS Nanogram designs (Comet, Night, Glass, Orbit, Speed), Nanogram notification and caller icons, intro = Sphere on a dark disc, "Nanogram" title and chat-list wordmark, ~50 strings that name the app (any language, cloud packs included) say Nanogram | Same identity as Nanogram iOS/Mac; no Telegram logo or app wording for our app. Network names (Premium, Stars, "anyone on Telegram") unchanged |
