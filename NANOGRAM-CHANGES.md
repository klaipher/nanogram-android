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
| minSdk | 24 (Android 7.0) instead of upstream's 23 | Owner decision 2026-11-14: current Google libraries (cast 23, maps 20.1, auth 22, webkit 1.18, palette 1.1, reCAPTCHA 18.9, Firebase BoM 35) need it |
| Google sign-in (e-mail login) | Credential Manager "Sign in with Google" (`NanogramGoogleSignIn`, googleid 1.2.1) instead of the removed GoogleSignIn API | play-services-auth 22 removed GoogleSignIn |
| Diagnostics | Rolling quiet log in release builds (errors, warnings, key events; 5 MB, 3 days), own crash records, Settings > Send diagnostics: zip with device/power/network state, process exit history, ANR traces, logs; share sheet or Nanogram chat picker; verbose toggle | Testers are friends on their own phones and nobody on the team has an Android device; mirrors Nanogram iOS debug log export and MetricKit crash reports |
| Updates | GitHub releases instead of Telegram's server update call: manifest + APK from `klaipher/nanogram-android-releases`, SHA-256 and signer check, PackageInstaller; Settings row "Check for updates" | Our api_id gets no updates from Telegram; friends sideload the APK |
| Native LTO | ThinLTO on all C/C++ in release (upstream: full LTO on C files only, because voip/CMakeLists.txt resets the C++ flags) | Smaller and faster native code; parallel, cached release links |
| Native speed | `-O2` instead of `-Oz` for tgnet, sqlite and instead of `-Os` for the main library (set per source, as linked targets' PUBLIC -Os/-O3 would otherwise win); opus `-O2`; FFmpeg and libvpx built without `--enable-small`; libvpx arm64 with run-time dotprod/i8mm kernels | Message/database/frame paths are hot; size cost is small with ThinLTO and gc-sections |
