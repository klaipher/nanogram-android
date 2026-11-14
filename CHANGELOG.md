# Changelog (private, detailed)

Newest first: version, build number, date, commit; what changed and how.


## Unreleased

### ANDROID-MEDIA-1: system Photo Picker for the "open in" entries (experimental, off)
- Flag `photo_picker` (default off), API 33+ with `MediaStore.isPhotoPickerAvailable()`: the attach "open in" entry (`ChatAttachAlertPhotoLayout`) and `ChatActivity.startPhotoSelectActivity` start `ACTION_PICK_IMAGES` (image only for avatars and single-image pickers) instead of the `ACTION_PICK` chooser; the old intent stays as fallback. The result handler detects picker video URIs by MIME type (`NanogramPhotoPicker.isVideo`) because they have no "video" in the URI. Check on a device that `AndroidUtilities.getPath` copes with picker URIs before turning the flag on.
- Not done: `READ_MEDIA_VISUAL_USER_SELECTED` and the partial-grant UI ("Select more" / "Allow all"). Android 14 compatibility mode already offers partial selection, and declaring the permission ends it, so the UI needs a device pass first.
- Tests: `NanogramPhotoPickerTest` (JVM).
### ANDROID-PREFS-1: apply() instead of commit() on UI-thread callbacks
- 34 synchronous `SharedPreferences.commit()` calls became `apply()`: every single-statement `edit()...commit()` in `ChatActivity` (23 hint/pinned/dialog-bar/translate-count/join-request keys) and `DialogsActivity` (archive hints, askAboutContacts, story hint, lock-screen asks), plus `MessagesController` removeSuggestion (5634), hidePeerSettingsBar (7976, 7996) and the notification-settings update path in `processUpdateArray` (20883). None reads the result, and none of these files is read by another process (the `:widgets` engine and `NotificationsDisabledReceiver` use other keys/files). Memory is updated at once; the write flushes in the background (QueuedWork at onPause/onStop).
- Not done (about 220 sites left): other `commit()` calls in `MessagesController`, `NotificationsSettingsActivity`, `NotificationsController`, `Theme` and shared helpers. Each needs a check for cross-process readers or ordering with a following read; follow-up. `StrictMode` (ANDROID-DESIGN-4) will list the remaining main-thread writes.
### ANDROID-NOTIF-1: progress-centric notifications for story upload and video conversion (experimental, off)
- Flag `progress_notifications` (default off), API 36 only: `NanogramProgressNotification.promote` rebuilds the existing compat notification with `Notification.ProgressStyle` (one 0-100 segment, progress, indeterminate at 0), `setRequestPromotedOngoing(true)` and a short critical text ("42%"), and uses the app's `R.drawable.notification` instead of `stat_sys_upload`. Applied in `StoryUploadingService` (id 33) and `VideoEncodingService` (id 4); with the flag off or below API 36 the notifications are byte-for-byte what they were. Manifest: `POST_PROMOTED_NOTIFICATIONS` (normal permission, ignored below API 36).
- Tests: `NanogramProgressNotificationTest` (percent helpers, JVM). Check on an Android 16 device: upload a story, see the status-bar chip and lock-screen progress.
### ANDROID-MANIFEST-1: drop dead permissions and legacy storage flags (cleartext kept)
- `READ_EXTERNAL_STORAGE` now `maxSdkVersion=32` (Android 13+ uses READ_MEDIA_*). Removed `MANAGE_ACCOUNTS`, `READ_PROFILE`, `AUTHENTICATE_ACCOUNTS` (no-ops at minSdk 24), `USE_FINGERPRINT` (BiometricPrompt + `USE_BIOMETRIC` stay; no FingerprintManager use left), `requestLegacyExternalStorage` and `preserveLegacyExternalStorage` (ignored at targetSdk 30+). `GET_ACCOUNTS` stays for the sync adapter.
- Owner decision: global `usesCleartextTraffic` stays as upstream (in-app http links); no network_security_config. `INSTALL_SHORTCUT` stays (not in the verified change list).
- Check: `aapt dump permissions` before/after on the release APK; smoke test contact sync, biometric unlock and gallery on API 28 and 35.
### ANDROID-LOCALE-1: system App languages entry (Android 13+)
- `res/xml/locales_config.xml` (the bundled UI languages: en, ar, de, es, it, ko, nl, pt-BR, ru, uk) and `android:localeConfig`, so Settings > System > Languages > App languages lists Nanogram. When the system locale changes on API 33+ and the user has not picked an in-app language, `LocaleController.onDeviceConfigurationChange` maps it to a language pack (`NanogramLocaleMap`: language_script_region, language_script, language_region, language) and applies it without saving an override; server language packs and the in-app choice are untouched. Downloaded packs are not listed in the system screen.
- Tests: `NanogramLocaleMapTest` (JVM).
### ANDROID-PROFILING-1: ProfilingManager system traces in diagnostics
- Nanogram diagnostics (API 35+): "Record a system trace" calls `ProfilingManager.requestProfiling(PROFILING_TYPE_SYSTEM_TRACE)`; the result is copied to `cache/ng_profiles` (6 files, 64 MB, newest kept). On API 36 a switch "Record traces on ANR and cold start" (off by default, persisted) registers ANR and COLD_START triggers and stores their results the same way (the API 36 calls go through reflection so a signature change fails soft). Traces go into the diagnostics zip as `profiles/` (whole files, never truncated) only when shared by the user; Clear logs deletes them. Nothing is uploaded automatically.
- Tests: `NanogramProfilingTest` (retention rule, JVM).
### ANDROID-DESIGN-4: StrictMode thread and VM policies in debug builds
- `NanogramStrictMode.install()` runs first in `ApplicationLoader.onCreate` (after the widget-engine-process return) when `BuildVars.DEBUG_VERSION` or the `strictmode` experimental flag is on: ThreadPolicy `detectAll` + log; VmPolicy leaked closables, registrations, SQLite objects, activity leaks, file URI exposure, and (API 31+) unsafe intent launch and content URI without permission. Release builds are unchanged. No `penaltyListener`: it would run FileLog (disk) from inside a violation. Expect upstream log noise; triage into docs/nanogram/performance.md.
- No JVM test: the class only wraps framework calls.
### ANDROID-DESIGN-1: no executor per link-parse call
- `AndroidUtilities.doSafe` runs on one shared bounded daemon pool (`NanogramSafeRunner`: 2 core, 4 max threads, idle timeout) instead of a new single-thread executor per call. `addLinksSafe` parses text up to 1000 chars inline on the caller's thread (on the same working copy, so the original keeps its spans if nothing is found); longer text keeps the 200 ms timeout guard. Result semantics unchanged. Not done: the interruptible CharSequence wrapper (Linkify works on a Spannable, so a wrapper changes span handling).
- Tests: `NanogramSafeRunnerTest` (JVM).
### ANDROID-NET-1: network state from the default NetworkCallback (experimental, off)
- Flag `net_callback` (`NanogramSystemFlags`, default off): the default `NetworkCallback` feeds `NanogramNetworkState` (internet capability, metered, roaming, transport), `isNetworkOnline*` answers from that cache after the first callback (legacy NetworkInfo path before it), and the CONNECTIVITY_ACTION receiver is not registered. Account checks (`checkConnection`, `FileLoader.onNetworkChanged`) run only when online/transport/metered/roaming changes, and a stale `onLost` of a replaced network is ignored. VALIDATED is deliberately not required (captive portals, VPN), as before.
- Not changed: `isConnectionSlow` and the Wi-Fi/roaming helpers still read `currentNetworkInfo` (refreshed once per network change instead of by every broadcast); they need the telephony subtype.
- Tests: `NanogramNetworkStateTest` (JVM).
### ANDROID-POWER-1: Lite mode follows system Battery Saver and thermal status
- LiteMode's power saver now also applies while the system Battery Saver is on or the thermal status is SEVERE or worse (`NanogramPowerPolicy`, fed by a POWER_SAVE_MODE_CHANGED receiver and `PowerManager.addThermalStatusListener`, API 29+). Previously only the battery percent threshold applied. On by default (owner decision); flips go through the existing `onPowerSaverApplied` path.
- Experimental flag `power_skip_charging` (`NanogramSystemFlags`, default off): ignore the percent trigger while charging.
- Tests: `NanogramPowerPolicyTest` (JVM).
### Native speed flags (-O2 for hot code, ARMv8.2 kernels in libvpx, FFmpeg/opus for speed)
- `-Oz` -> `-O2` for tgnet (MTProto, per-message/per-chunk AES-IGE glue), sqlite (message database, every chat load),
  and the main library sources (image/blur, gifvideo, lottie glue, ffmpeg_jni, opusfile/ogg, audio), which were -Os, not
  -Oz: tgcalls_tp's PUBLIC -Os (and rnnoise's PUBLIC -O3) was appended last to their command line and won over the
  target's -O2, so -O2 is now also set per source (set_property SOURCE ... COMPILE_OPTIONS, which comes last). voip,
  rnnoise and twidget were already -O2/-O3; tgcalls_tp itself stays -Os (usrsctp, cold). No -O3: no loop kernel of ours is large enough
  to pay for the growth. `-ffast-math` stays exactly where upstream had it; ThinLTO + gc-sections + icf stay on.
- Prebuilts: opus `-Oz` -> `-O2` (voice messages and calls); FFmpeg `ENABLE_SMALL` 1 -> 0 with `--optflags=-O2`
  (--enable-small forces -Os and size-first paths); libvpx `LIBVPX_SMALL` 1 -> 0 (-O3 flags unchanged). Any script
  change changes the `ng prebuild` cache key, so prebuilts rebuild once.
- libvpx arm64: run-time CPU detection on and `neon_dotprod`/`neon_i8mm` kernels built next to the ARMv8.0 ones
  (picked through getauxval, so ARMv8.0 phones run the baseline code). SVE/SVE2 stay off (`LIBVPX_ARM_SVE=1` to try).
- Already covered, nothing added: BoringSSL (AES/SHA/PMULL by getauxval), libyuv (NEON dotprod/i8mm dispatch), dav1d and
  FFmpeg aarch64 asm (dotprod/i8mm via cpu flags), webrtc and opus (NEON intrinsics, baseline), outline atomics (NDK r30
  clang enables `+outline-atomics` by default; LSE chosen at run time). Global `-march` stays ARMv8.0-A and no
  `-mtune` is added: little cores are in-order, a big-core schedule can slow them.
- Measure: see docs/nanogram/build.md "Native speed flags".
### Shared FFmpeg fork (klaipher/FFmpeg, branch nanogram)
- One FFmpeg source for Android, iOS and macOS: official n9.0.2 plus our commits — mov stsd_count reset and
  VideoToolbox attachment skip (from the iOS copy), encryption_info stack exhaustion and HEVC slice layer check
  (Telegram Android's former patches), VERSION 9.0.2. Android's submodule points at it (d84c50b); the two patch files
  moved into the fork; build_ffmpeg.sh reads the fork's VERSION, so the library reports 9.0.2.
- Android gains the mov.c security fix that iOS already had.

### FFmpeg 8.1 (release/8.1 @ 8dca158) -> 9.0.2 (f927fb8 submodule, 6ee351a build_ffmpeg.sh)
- Submodule `third_party/ffmpeg` moved to tag n9.0.2 (libavcodec 63, libavformat 63, libavutil 61, libswscale 10,
  libswresample 7), the version iOS and macOS already ship. Not built or run yet.
- Component set unchanged: every `--enable-*` name in build_ffmpeg.sh exists in 9.0.2 (decoders, demuxers, parsers,
  bsfs, libvpx/libdav1d/libopus detection unchanged); both local patches (encryption_info recursion, HEVC layer
  index) still apply to 9.0.2 and are not in it.
- The committed fallback prebuilts in jni/prebuild are still FFmpeg 8.1 (ABI differs from the 9.0.2 headers). CMake now
  stops with an error when NANOGRAM_PREBUILD_DIR is empty and the committed ffversion.h does not match the submodule's
  RELEASE, so only the ng path (own prebuild cache) builds until the prebuilts are refreshed from an ng prebuild.
- Added `--disable-unstable`: 9.0 builds a new opt-in swscale backend (generated AArch64 kernels from a host tool,
  selected only with SWS_UNSTABLE, which we never pass). Output of sws_scale is unchanged.
- Call sites (ffmpeg_jni.cc, gifvideo.cpp, audio.c, webm_encoder.c, WebRTC h264 decoder) use no API removed in 9.0
  (checked with -fsyntax-only against the 9.0.2 headers); av_init_packet in webm_encoder.c stays deprecated until 64.
### ART baseline profile and startup profile for sideloaded installs
- Sideloaded APKs get no Play cloud profile, so installs ran interpreted until background dexopt. The release APK now
  carries `assets/dexopt/baseline.prof` and the app depends on `androidx.profileinstaller` 1.4.1, which installs it on
  first launch (API 24+). ART compiles it at the next background dexopt (idle and charging), not at install; the gain is a
  better-chosen method set there, and no `.dm` is shipped (the in-app updater and `adb install` carry only the APK).
- `androidx.baselineprofile` 1.5.0 plugin in the app (`mergeIntoMain`, `saveInSrc`, no generation during builds,
  `dexLayoutOptimization` = R8 startup-profile dex layout). New `:baselineprofile` module (macrobenchmark 1.5.0,
  uiautomator 2.4.1) with `BaselineProfileGenerator`: startup test (also the startup profile) and a logged-out flow test
  (intro swipes, Start Messaging, "Don't allow" on permission dialogs, phone login, country picker, diagnostics screen).
  The module is included only with `-Pnanogram.baselineProfile=true`; then only the afat release, nonMinifiedRelease and
  benchmarkRelease variants are enabled (`beforeVariants`), and the nonMinified/benchmark source sets use the release
  manifest.
- Hand-written `src/main/baselineProfiles/startup-prof.txt` lists the logged-in cold-start classes (LaunchActivity,
  MainTabsActivity, DialogsActivity, DialogCell, DialogsAdapter, storage and image core) explicitly, because the
  generated startup profile only covers the logged-out start and R8 reads only `startup-prof.txt` files.
- Profile-only variants (nonMinifiedRelease, benchmarkRelease) are disabled unless `-Pnanogram.baselineProfile=true`.
  `ng build` takes the APK and mapping only from its own variant directory.
- `TMessagesProj_App/src/main/baseline-prof.txt`: hand-written wildcard rules for post-login paths (chat list and cells,
  chat and message cells, ImageReceiver/ImageLoader, MessagesController/MessagesStorage reads, SQLite wrapper,
  ConnectionsManager callbacks, NotificationCenter, RecyclerListView, emoji/theme drawing, TL (de)serialization only, MainTabsActivity, narrowed AndroidUtilities).
- `Tools/dev/ng profile` runs `:TMessagesProj_App:generateBaselineProfile` on the running emulator and prints line counts;
  docs in docs/nanogram/build.md (Profiles). Not yet built or measured. Commit: see git log of nanogram/profiles.
### Performance: UI (branch nanogram/perf-ui)
- UI-1: Choreographer60FpsContent no longer posts a frame callback on every vsync from creation. It schedules on the first
  subscription/invalidation and stops when no group, once-list or invalidation list has work (`hasWork()`); a restart
  keeps the timing accumulators unless the last tick is older than two frame intervals, so callers that re-post a
  one-shot invalidation from draw() (spoilers, typing/recording status drawables) keep their old rate.
- UI-5: EditTextBoldCursor (API 24-28) registers its 2 fps blink invalidation only while focused and attached.
- UI-3: removed setPreferredMaxRefreshRate from LaunchActivity.onConfigurationChanged and from the long-lived PiP windows
  (VoIPPiPView x2, PipRoundVideoView, FloatingHost). Short-lived overlays keep it.
- UI-6: kbd_height/kbd_height_land3 (all 8 sites) and MediaDataController draft save/clear use apply() instead of commit().
- UI-8: Nanogram wordmark computes its baseline offset once, no FontMetrics allocation per draw.
- BATTERY-7: Theme.onAppPaused()/onAppResumed() release and re-register the ambient light sensor (Automatic night mode)
  from LaunchActivity.onPause/onResume; lastBrightnessValue is kept to avoid a flicker. Re-registration is direct, not via
  checkAutoNightThemeConditions, so no theme change is evaluated on resume.
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
