# Changelog (private, detailed)

Newest first: version, build number, date, commit; what changed and how.


## Unreleased

### Performance: core pass (branch nanogram/perf-core)
Commit ids are in `git log nanogram/perf-core`; each message starts with the finding id. Details and how to measure:
docs/nanogram/performance.md.
- UI-2: FileLog opens and prunes its files on `logQueue`; the start line and the app label (binder call) leave the main
  thread; the crash handler resolves the label when it writes.
- UI-4, MEMORY-2: the `:widgets` process is prewarmed only for users with a widget session in the last 14 days, and its
  hold is released in the background (30 s idle unbind), taken again in the foreground.
- BATTERY-1: `updateTimerProc` at most every 250 ms per account instead of every network loop wake-up.
- BATTERY-2, BATTERY-6: audio-only progress ticks 1/s with the screen off; long-audio position saved with `apply()`
  every 5 s and on pause/stop.
- BATTERY-3: a closed poll is applied to the queued message, so expired polls stop being re-fetched.
- BATTERY-5: MediaStore observers do nothing in the background; one reload in `checkGallery()` on return.
- MEMORY-1: `ImageLoader.onTrimMemory` frees caches by level. MEMORY-3: `VACUUM` uses a temp file.
- IO-1: plain downloads without O_SYNC per chunk (ranges recorded after `force(false)`, one sync before the rename).
  IO-2: `synchronous=NORMAL` for cache4.db. IO-3: files database in WAL, one transaction in `putPath`. IO-4: MP4 muxer
  syncs once. IO-5: streaming reads cache the available window. IO-6: tgnet.dat writes coalesced (1 s). IO-7: no fsync
  for the usage counters. IO-8: 128 KB copy buffers. IO-9: `sharing/` copies deleted after 30 days.
- Review fixes: no engine hold in a background or push-woken process; `cleanup()` forces the data file before closing so
  the newest ranges are recorded on pause; `clearAll` waits for log deletion (`FileLog.cleanupLogsSync`); the start line
  is queued first again (the label follows as `build ...`); `fatal` exits in debug-private even if the log did not
  open; docs list the power-loss trade-offs of IO-1 and IO-2 and the right file name for IO-3.
- Not done: BATTERY-4 (port of tgcalls ad9aa93, b483fca, abbdaed, 67e8982 with `tools/dev/tgcalls-port` from the iOS
  repo plus a pin bump) needs the shared fork and a native build; left for the main session.

### Native LTO for all code (ThinLTO)
- voip/CMakeLists.txt replaced CMAKE_CXX_FLAGS instead of appending. It is included into the main directory, whose
  final flags apply to every C++ target there, so upstream builds all C++ (tgnet, tgcalls, WebRTC, the main library)
  without LTO, -ffunction-sections and hidden visibility; only the 417 C files had them. Now it appends.
- Release native code uses ThinLTO (`-flto=thin`, cache in the build dir, 2 GB cap) instead of full LTO, now on all
  1867 files; debug keeps no LTO. Exported symbols unchanged (403 JNI functions + JNI_OnLoad/OnUnload).
- Measured (release arm64): libtmessages 22.24 -> 20.90 MB (-6%), libtwidget 5.31 -> 4.95 MB (-7%), APK 56.9 -> 55.2 MB;
  release build 389 s cold. Emulator: launches, login screen, MTProto connection to DC 149.154.167.41:443.

### isoparser 1.0.6 -> 1.1.22
- The last release of com.googlecode.mp4parser (2017); 1.1 renamed NalUnitTypes to H265NalUnitTypes (same constants)
  and no longer brings aspectj. HevcDecoderConfigurationRecord uses the new name; two unused imports removed.

## 13.2.2 (202611141530) — 2026-11-14 — first Android release (source 672347f)

### Biometrics on the modern API
- Removed the API 36 FingerprintManager stub jar, the compileOnly line and the R8 `-dontwarn`; deleted
  `org.telegram.messenger.support.fingerprint` (only `PasscodeView.hasFingerprint` and `FingerprintController` used it).
- `FingerprintController.isBiometricAvailable()` = `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)`, replaces
  isHardwareDetected + hasEnrolledFingerprints. The passcode keystore key (RSA, user-auth-required, invalidated on new
  enrolment) is unchanged, so stored settings and keys stay valid. BIOMETRIC_STRONG kept for passcode, bot biometry
  and wallet: Class 3 face/iris now qualify (Android 11+), weak face does not.
- Wallet unlock (`GramWalletStorageAuth`): R+ framework BiometricPrompt (device credential inside); Android 6-10
  AndroidX BiometricPrompt (BIOMETRIC_STRONG, so P-Q stay fingerprint/strong-only) with a "Use device PIN" button. The hand-rolled FingerprintManager
  dialog is gone.
- `androidx.biometric` stays at 1.1.0 (newest stable, Jan 2021; 1.4 is still beta).
- `BotBiometry` creates a new AndroidX prompt per request (AndroidX keeps one client callback per activity).
- Removed unused strings WalletTouchFingerprint and WalletFingerprintRetry.
### Diagnostics and log collection
- New `org.telegram.messenger.nanogram` package and `NanogramDiagnosticsActivity` (Settings > Help > Send diagnostics,
  also Debug menu): builds `Nanogram-diagnostics-<versionCode>-<time>.zip` (device/power/network/permission/memory report,
  process exit history with ANR traces and native tombstones, own crash records, logs, call logs) and shares it through
  the system share sheet (FileProvider `cache-path`) or Nanogram's own chat picker (`DialogsActivity` select mode,
  `SendMessagesHelper.prepareSendingDocuments`); verbose-log switch, clear logs, "closed unexpectedly" list.
- Release builds keep a rolling quiet log: `FileLog.e/w/fatal` and new `FileLog.i` write without `LOGS_ENABLED`
  (5 MB, 3 days, 30 files, 1 MB per file, 40 lines per 10 s burst cap); `FileLog.d` and heap dumps stay verbose-only;
  the TL dump file is created only in verbose mode. Key events logged: start, foreground/background, push received,
  call state, connection state, memory trim, and exits/crashes of the previous run on the next start.
- Uncaught exceptions are recorded synchronously by `NanogramCrashHandler` (chained before upstream's handler).
- Collected through `ActivityManager.getHistoricalProcessExitReasons` (API 30+), `getHistoricalProcessStartReasons`
  (API 35+), thermal status/headroom, standby bucket, background restriction, data saver, exact-alarm and
  full-screen-intent state, notification channel states (per-chat channels only counted), `/proc/self` memory and fds.
- Docs: `docs/nanogram/diagnostics.md`.
- Commits: 00a159d (quiet log, crash records, exit history, system report), 5a4c01a (screen), c1efa35 (key events),
  389cfcc and 955b4d4 (docs); review fixes in the commits after them: send-to-chat path exception in `isInternalUri`,
  `getStartComponent` only on API 36, default notification channel matching, update owner API 34, LOW_MEMORY/FREEZER only
  when not cached, separate budget for `FileLog.i`, synchronized `FileLog.init`, quiet file reuse, zips kept for an hour,
  verbose logs retained 3 days after switch-off with a privacy warning in README and screen.
### In-app updates and release tooling
- Updates come from GitHub: the app fetches `update.json` from `klaipher/nanogram-android-releases` (HTTPS, at most
  every 6 hours, or from Settings > Check for updates), downloads the APK to its cache with progress, verifies SHA-256,
  package name, versionCode and that the signing certificate equals the installed app's, then installs through a
  PackageInstaller session (asks for "install unknown apps" first). `NanogramUpdater` plus copies of the beta update UI
  (`NanogramUpdateLayout`, `NanogramUpdateAlertDialog`) are wired through upstream's custom-update hooks in
  `ApplicationLoaderImpl`, so the existing "Update" bar and sheet are reused and `help.getAppUpdate` is never sent.
  Off in debug builds unless the debug-menu toggle is on.
- `ng release`: release build, `update.json` (versionCode from aapt2), private release (APK, mapping, native symbols,
  CHANGELOG section) then public release marked latest; refuses dirty or unpushed trees; `--dry-run`. Helpers and tests in
  `Tools/dev/ngrelease.py`, `Tools/dev/tests`. Guide: `docs/nanogram/releases.md`.
- Review fixes: update download uses `getContentLength` (the Long variant needs API 24); archive signers are read with
  GET_SIGNATURES as well on Android 9; the installing state always ends (missing confirm intent, 10 minute timeout);
  Settings status clears stale errors and throttles progress refresh; `ng release` requires package, versionCode,
  versionName and minSdk in aapt2 output, zips release-variant native symbols (newest), and resumes when the private
  release already exists.

### Infrastructure
- Repository `klaipher/nanogram-android` created from a history-free import of upstream release 13.2.2 (7303),
  DrKLO/Telegram 21f7d109 (commit 406cbeb).
- Telegram-specific submodules now come from klaipher forks (tlottie, wallet-engine, media, jlatexmath-android,
  libprisma, twidget, widget-sandbox); tgcalls reuses the shared `klaipher/tgcalls` fork, branch `nanogram-android`.
- `Tools/dev/ng` developer tool, pinned toolchain (`Tools/dev/toolchain.properties`), pre-push gate, agent guide.

### Identity (2ea238e)
- applicationId `org.klaipher.nanogram`, app name Nanogram; own api_id/api_hash through BuildConfig (ng passes them
  through the environment); own release key when present; Firebase only from our own config in the app module.
- Fork flags: Play billing unavailable, passkeys off, no Play Store URL.

### Build speed and size (2ea238e)
- arm64-v8a only by default; ccache for native compiles; no LTO in debug native builds; Gradle configuration cache
  (parallel) and build cache; non-transitive R; Jetifier off; upstream's extra app modules only on request;
  16 KB flexible page sizes; uncompressed native libraries.
- Measured (arm64 debug): upstream cold 306 s; ours no-op 3 s, clean rebuild with warm caches 54 s.

### Toolchain and dependencies (25fadc9)
- Gradle 9.9.1, AGP 9.5.1 (new DSL + androidComponents API), Kotlin 2.4.21, NDK r30, CMake 4.1.2, build-tools 37,
  compileSdk 37, Java 17; minSdk 23 and targetSdk 36 as upstream.
- versionCode = minutes since 2020-01-01 of the build number × 10 + ABI digit.
- Only the 11 androidx/media modules in use; media fork on branch `nanogram-android` (klaipher/media 07b5247).
- Libraries updated to the newest releases that still support minSdk 23 (list in the commit); kept: palette 1.0.0,
  isoparser 1.0.6, stripe 2.0.2, Firebase BoM 34.19.0, wallet 19.5.0 (each blocked by minSdk 24 or an API rewrite).
- SDK 37 dropped FingerprintManager: compile-only API 36 stubs, behaviour unchanged.
- WebRTC `Candidate::set_type` lifetimebound removed for NDK r30 clang (same as Mac tg_owt).
- Emulator smoke test (arm64, Android 16, 16 KB pages): launches, native libs load, MTProto handshakes complete.

### Native dependencies (93d06ca)
- TDLib = iOS pin 0e88653d (1.8.69); abseil LTS 20260817.0 (WebRTC moved to absl_nonnull/absl_nullable, 80 uses);
  BoringSSL 0.20261105.0 (archive paths fixed in build_boringssl.sh); OpenH264 2.7.0; boost-regex 1.92.0.
- ng prebuild keys on checked-out submodule commits, uses ccache, removes its scratch tree.

### Build fixes (d972313)
- CMake 4.1.2 now really used (version set where AGP reads it); R8 rule for framework fingerprint classes;
  v1+v2+v3 release signing; ng prune removes failed prebuilds.

### Branding (91e91be)
- Comet launcher icon (adaptive, themed, legacy, round), alternate icons = iOS Nanogram designs (Light, Orbit, Glass,
  Speed, Night), Comet notification and caller icons, intro Comet on navy + "Nanogram" title, Nanogram wordmark in
  the chat list and stories header, ~50 app-name strings say Nanogram in every language (NanogramBranding).

### Default icon = Sphere, like iOS/Mac
- The launcher default is now the Sphere (the iOS/Mac default since 2026-11-04), built from the same layers as the
  iOS Liquid Glass icon: starry background as the adaptive background, light/sphere/sparkle as the foreground
  (launcher parallax), the iOS mono layers as the themed icon. Comet moves to the alternates (Vintage slot); intro and
  caller icon use the Sphere. The renderer now draws SVGs with AppKit (keeps transparency).

### minSdk 24 and the held-back libraries
- minSdk 23 -> 24 (owner decision), native API level 24 (CMake and prebuilts).
- cast 23.0.0, maps 20.1.0, auth 22.0.0, webkit 1.18.0, palette 1.1.0, reCAPTCHA 18.9.4, Firebase BoM 35.2.0.
- play-services-auth 22 removed GoogleSignIn: e-mail login uses Credential Manager "Sign in with Google"
  (NanogramGoogleSignIn, googleid 1.2.1); same e-mail + ID token to the server.
- Emulator: launches, MTProto handshakes complete.

### Integration fixes (main session)
- Login screen: a diagnostics button (top right) opens Nanogram diagnostics before sign-in; the diagnostics screen has
  "Check for updates" too, so testers who cannot sign in can still send logs and update.
- Exit history counts problems only for the main process (the isolated widget engine is reclaimed routinely and was
  reported as "closed unexpectedly").
- Settings: diagnostics (id 28) and update check (id 29) rows no longer share an id; update status text lives in
  NanogramUpdater.statusText().

### Measurements
- Release arm64: 57.5 MB APK (R8 full mode, full LTO), 182 s; debug 98.7 MB. Details in docs/nanogram/build.md.
