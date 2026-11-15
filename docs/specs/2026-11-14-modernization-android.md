# Android specs

Repo: `klaipher/nanogram-android` (local `~/Projects/nanogram-android`). Base: `main` (fdd4407; `perf-core`, `perf-ui`, `profiles`, `ffmpeg-9` and `native-opt` are merged). `nanogram/build-modernize` is still unmerged and owns `TMessagesProj/build.gradle`, `jni/CMakeLists.txt` and `jni/prebuild/*`: any spec that adds a Gradle dependency waits for it. minSdk is 31 in the Gradle files (owner decision 2026-11-14, was 24). targetSdk 36. The team has no Android device, so device-only measurements are owner-dependent; such changes ship behind default-off flags.

---

### ANDROID-IO-1: Save to gallery or Downloads copies 4 KB per transferFrom call
- **Severity:** medium
- **Evidence:** `MediaController.java:6012-6016` and `6220-6224` loop `transferFrom(source, a, min(4096, size - a))`. `docs/nanogram/performance.md` IO-8 claims 128 KB, which is only true for `AndroidUtilities.copyFile` and `FileLoader:1787`.
- **Change:** one extracted helper: `FileUtils.copy` with CancellationSignal and progress on API 29+, chunked `transferFrom` (4-8 MB) below; keep the cancel flag, 500 ms progress throttle and partial-file deletion. Correct IO-8 in performance.md (integrator).
- **Docs:** https://developer.android.com/reference/android/os/FileUtils#copy(java.io.InputStream,%20java.io.OutputStream,%20android.os.CancellationSignal,%20java.util.concurrent.Executor,%20android.os.FileUtils.ProgressListener)
- **Risk:** low.
- **Measurement:** save a 1 GB video: wall time and syscall count (`strace -c`).
- **Effort:** small
- **Status:** open

### ANDROID-VIDEO-2: Seek preview allocates a full-resolution bitmap per scrub step
- **Severity:** low
- **Evidence:** `AnimatedFileDrawable.java:551` `Bitmap.createBitmap(w, h, ARGB_8888)` per call; `VideoSeekPreviewImage.java:268` draws it down to ~200 px. Non-precise path is seek plus one frame.
- **Change:** reuse one destination bitmap per scrub session, or scale with libyuv inside `writeFrameToBitmap` (`jni/gifvideo.cpp`, app code) straight into a small bitmap. Not MediaMetadataRetriever (same full-size cost, no partial files). Skip when the storyboard path is active.
- **Risk:** medium (stream-aware path for files still downloading).
- **Measurement:** scrub a 4K clip: decode-thread CPU, meminfo peak, previews per second (Perfetto).
- **Effort:** medium
- **Status:** open

### ANDROID-DISPLAY-1: No adaptive-refresh hints; max refresh pinned on overlays (merged duplicate)
- **Severity:** low (owner decision: reverses UI-3)
- **Evidence:** no `setRequestedFrameRate`, `setFrameContentVelocity` or frame-rate category use; `RefreshRateController` commented out (`LaunchActivity.java:402/855`); `AndroidUtilities.setPreferredMaxRefreshRate` (2938/2949, 22 call sites incl. StoryViewer:1780, StoryRecorder, ReactionsEffectOverlay:789); `Choreographer60FpsContent` ticks at 60 fps.
- **Change:** API 35+ only, behind a flag that defaults off: frame-rate category votes on views driven by the 60 fps clock; in StoryViewer a `Surface.setFrameRate(videoFps, FIXED_SOURCE)` vote for video and a low category for still stories; keep `preferredRefreshRate=max` only while a gesture or animation runs (as UI-3 does for the main window); `setFrameContentVelocity` (API 35) during fling in the forked RecyclerListView; delete RefreshRateController. Flip only after an LTPO device A/B.
- **Docs:** https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate ; https://developer.android.com/media/optimize/performance/frame-rate
- **Risk:** low to medium (choppy animation on a wrong low vote).
- **Measurement:** SurfaceFlinger render rate and display power during a 2-minute still-story session and idle sticker pack (Pixel 8/9 Pro).
- **Effort:** medium
- **Status:** open

### ANDROID-POWER-1: Lite-mode power saver ignores Battery Saver, charging and thermal state
- **Severity:** medium
- **Evidence:** `LiteMode.java:108-120` uses only `powerSaverLevel > 0 && isBatteryLow(level)`; `shouldEnableAnimation` returns early on SDK 28+; thermal APIs only in NanogramSystemInfo.
- **Change:** `addThermalStatusListener` (API 29) and `isPowerSaveMode` (poll on the existing 60 s battery query or a POWER_SAVE_MODE_CHANGED receiver); treat power-save or thermal >= SEVERE as power-saver applied; skip the percent trigger while charging; route changes through `onPowerSaverApplied`. A new setting under the Power Saver level (owner decides the default).
- **Docs:** https://developer.android.com/reference/android/os/PowerManager#addThermalStatusListener(android.os.PowerManager.OnThermalStatusChangedListener)
- **Risk:** low.
- **Measurement:** Battery Saver on drops FLAG_* effects; `cmd thermalservice override-status 3` during a 10 min compression.
- **Effort:** small
- **Status:** implemented (nanogram/mod-system f84d087); the experimental percent-trigger skip-while-charging was removed on 2026-11-15 (no measurement path). Setting "Follow system Battery Saver and heat" under the Power Saver slider, default on, takes precedence over the system signals

### ANDROID-IMG-1: All non-thumbnail disk-cache decodes run on one thread
- **Severity:** medium
- **Evidence:** `org/telegram/DispatchQueuePriority.java` is `ThreadPoolExecutor(1, 1)` with a PriorityBlockingQueue; every non-thumb CacheOutTask goes to `cacheOutQueue` (thumbs use `cacheThumbOutQueue`). `ImageLoader` static `header`/`headerThumb` byte[12] (139-140, used 1006-1008) assume one thread per queue. `onPostExecute` (~1610) hands bitmaps over without `prepareToDraw()`.
- **Change:** behind a flag: 2 threads on AVERAGE, 3 on HIGH, 1 on LOW performance class, sharing the priority queue, via a ThreadFactory with background priority; per-thread header buffers; keep cancel and pause latch semantics and `Thread.interrupted()` checks. Call `prepareToDraw()` for on-screen results (not prefetches, not bitmaps that go to blur or filters).
- **Docs:** https://developer.android.com/reference/java/util/concurrent/ThreadPoolExecutor
- **Risk:** medium (native memory peaks; ordering in ImageReceiver).
- **Measurement:** fling a 3000-photo grid: jank %, tile fill time, meminfo peak.
- **Effort:** medium
- **Status:** open

### ANDROID-NET-1: Connectivity uses deprecated NetworkInfo and CONNECTIVITY_ACTION (merged duplicate)
- **Severity:** low
- **Evidence:** receivers in `ApplicationLoader.java:207-224` and `DownloadController.java:339`; `isNetworkOnlineRealtime` (614-630) makes up to 3 binder calls; the lazy default NetworkCallback (466-490) only resets `lastKnownNetworkType`.
- **Change:** register one default NetworkCallback in `onCreate`; keep volatile online, transport, metered and roaming fields; call `checkConnection` and `FileLoader.onNetworkChanged` from it; remove both receivers and NetworkInfo use; 2G detection via TelephonyCallback (API 31) or subtype below. Do not use VALIDATED in the online test (proxies).
- **Docs:** https://developer.android.com/training/monitoring-device-state/connectivity-status-type
- **Risk:** low to medium (VPN, Wi-Fi without internet).
- **Measurement:** Wi-Fi to cellular during a download: time to state flip; binder calls.
- **Effort:** medium
- **Status:** implemented behind flag `net_callback` (cb13a57), removed on 2026-11-15 (no measurement path; the legacy receiver stays)

### ANDROID-ML-1: QR scanning and face detection use retired Mobile Vision
- **Severity:** low
- **Evidence:** `play-services-vision:20.1.3` (build.gradle:67); BarcodeDetector in CameraScanActivity (56-58, 260), QrScanOverlay (45-47), QRScanner; FaceDetector in PhotoViewer (155-157), PhotoFace, LPhotoPaintView, PaintView, MrzRecognizer. The app already ships other ML Kit modules.
- **Change:** `play-services-mlkit-barcode-scanning` and `-face-detection` with the ZXing fallback kept; pre-download via `com.google.mlkit.vision.DEPENDENCIES`. Waits for build-modernize (build.gradle).
- **Docs:** https://developers.google.com/ml-kit/migration/android
- **Risk:** medium (first-use model download; de-Googled devices).
- **Measurement:** QR success rate and time to first decode at 3 distances and in dim light.
- **Effort:** medium
- **Status:** open

### ANDROID-CAMERA-1: Camera sheet and story recorder are hard-wired to Camera1
- **Severity:** low (long-term opt-in experiment)
- **Evidence:** `CameraView.java:125` `useCamera2 = false && ...`; CameraSession uses android.hardware.Camera.
- **Change:** re-enable Camera2 behind the existing `useCamera2Force_2` setting (default off) in StoryRecorder and the chat camera, fix known Camera2Session issues, add diagnostics counters; later stabilization and 10-bit HLG. Default flip only with device data.
- **Docs:** https://developer.android.com/media/camera/camera2
- **Risk:** medium; large device matrix; upstream disabled it.
- **Measurement:** 1-minute 1080p30 story on two vendors: preview latency, dropped frames, battery.
- **Effort:** large
- **Status:** open

### ANDROID-MEDIA-1: All-or-nothing media permission UX; system Photo Picker unused
- **Severity:** low (Android 14 compatibility mode already offers partial selection)
- **Evidence:** manifest 38-40 without `READ_MEDIA_VISUAL_USER_SELECTED`; `ChatActivity.java:14468`, `DialogsActivity.java:10313`, `CameraScanActivity.java:694`, `QrScanOverlay.java:212`; `ACTION_PICK` at `ChatAttachAlertPhotoLayout.java:3408/3419` and `ChatActivity.java:14510`.
- **Change:** declare and request VISUAL_USER_SELECTED on API 34+; treat partial grant as a state in PhotoAttachPermissionCell with 'Select more' and 'Allow all'; use `ACTION_PICK_IMAGES` when `isPhotoPickerAvailable()` for the 'open in' entries, old intent as fallback; copy picked files before the grant lapses. Keep the in-app gallery. Do not edit MediaController (the MediaStore queries already return the selected items).
- **Docs:** https://developer.android.com/about/versions/14/changes/partial-photo-video-access ; https://developer.android.com/training/data-storage/shared/photopicker
- **Risk:** medium (partial-grant UI in several pickers).
- **Measurement:** Android 14/15: grant 'Select photos', only those show, sending works.
- **Effort:** medium
- **Status:** partly implemented as the Debug experiment "System photo picker" (default off): system Photo Picker for the "open in" entries. Not done: READ_MEDIA_VISUAL_USER_SELECTED and the partial-grant UI (declaring it ends compatibility mode, so it needs the PhotoAttachPermissionCell, DialogsActivity, CameraScanActivity and QrScanOverlay states and a device test first)

### ANDROID-TELECOM-1: Telecom integration is hard-disabled
- **Severity:** medium (impact) with high correctness risk
- **Evidence:** `VoIPService.java:5862-5877` `isDeviceCompatibleWithConnectionServiceAPI()` returns false; no core-telecom dependency; incoming UI via CallStyle and full-screen intent (5439-5458).
- **Change:** opt-in `androidx.core:core-telecom` path (CallsManager, self-managed, CallControlScope, endpoint flow) for 1:1 calls on API 34+, behind a setting that defaults off; tgcalls keeps audio; only routing and call-state ownership move. Waits for build-modernize (Gradle dependency).
- **Docs:** https://developer.android.com/develop/connectivity/telecom/voip-app/telecom
- **Risk:** high (tgcalls audio device module and SCO; OEM quirks). Needs a device matrix.
- **Measurement:** headset, car, watch, DnD exceptions, call during media playback.
- **Effort:** large
- **Status:** open

### ANDROID-LOCALE-1: No localeConfig for the system App languages screen
- **Severity:** low
- **Evidence:** no `android:localeConfig`, no `locales_config.xml`, no LocaleManager use.
- **Change:** `res/xml/locales_config.xml` with the bundled UI languages; on API 33+ map the system per-app locale to the Telegram language pack in LocaleController (zh-hans and similar mapped carefully); no server language-pack change.
- **Docs:** https://developer.android.com/guide/topics/resources/app-languages
- **Risk:** low.
- **Measurement:** App languages lists Nanogram; switching changes the UI without a restart loop.
- **Effort:** small
- **Status:** implemented (09ac2c2)

### ANDROID-PROFILING-1: No on-device system traces for ANR, cold start or jank
- **Severity:** low
- **Evidence:** `NanogramExitInfo.java` uses exit reasons only; no ProfilingManager.
- **Change:** manual 'Record trace' in the diagnostics screen (`requestProfiling`, API 35) and ANR and COLD_START triggers on API 36; files stored next to the exit-info report, exported only through the existing share flow.
- **Docs:** https://developer.android.com/reference/android/os/ProfilingManager
- **Risk:** low.
- **Measurement:** artificial ANR on Android 16 produces a trace that opens in Perfetto.
- **Effort:** small
- **Status:** implemented (ad5a0c7), opt-in, local only; cold-start trigger exists only on API 37, ANR trigger on API 36

### ANDROID-NOTIF-1: Transfer notifications use stat_sys_upload and a plain bar
- **Severity:** low
- **Evidence:** `StoryUploadingService.java:98/106`, `VideoEncodingService.java:90/133/165`.
- **Change:** API 36 `Notification.ProgressStyle` with stage points and `setRequestPromotedOngoing(true)`; NotificationCompat below; app drawable instead of the platform icon.
- **Docs:** https://developer.android.com/about/versions/16/features/progress-centric-notifications
- **Risk:** low.
- **Measurement:** Android 16: status-bar chip and lock-screen progress during a story upload.
- **Effort:** small
- **Status:** implemented as the Debug experiment "Progress notifications", default off (943022a)

### ANDROID-MANIFEST-1: Obsolete permissions and legacy storage flags
- **Severity:** low
- **Evidence:** `AndroidManifest.xml:37-57, 105-109`.
- **Change:** READ_EXTERNAL_STORAGE `maxSdkVersion=32`; drop MANAGE_ACCOUNTS, READ_PROFILE, AUTHENTICATE_ACCOUNTS, USE_FINGERPRINT, requestLegacyExternalStorage, preserveLegacyExternalStorage after checking merged-manifest needs; keep GET_ACCOUNTS (sync adapter). Cleartext narrowing via network_security_config only if http links in the in-app browser keep working (owner decision).
- **Docs:** https://developer.android.com/privacy-and-security/security-config
- **Risk:** low.
- **Measurement:** `aapt dump permissions` before and after; contact sync, biometrics, gallery on API 28 and 35.
- **Effort:** small
- **Status:** implemented (d7289b6), global cleartext kept by owner decision; `requestLegacyExternalStorage` kept (Android 10 honours it at any target SDK, minSdk 24); `USE_FINGERPRINT` still merged in from androidx.biometric 1.1.0

### ANDROID-PREFS-1: Synchronous SharedPreferences commit() on the UI thread
- **Severity:** low
- **Evidence:** about 250 `commit()` calls across ChatActivity, DialogsActivity, MessagesController, NotificationsController, Theme and helpers; each blocks the caller until the file is written.
- **Change:** `apply()` where nothing reads the result and no other process reads the file.
- **Risk:** low; cross-process readers (`:widgets`, receivers) and ordering with a following read must be checked per site.
- **Measurement:** StrictMode (ANDROID-DESIGN-4) disk-write violations on the main thread.
- **Effort:** small per site
- **Status:** partly implemented (785eaa6): 34 sites (ChatActivity, DialogsActivity, three MessagesController paths). About 220 sites remain, each needing a cross-process and ordering check

### ANDROID-DESIGN-1: New ExecutorService per Linkify call; timed-out regex keeps running
- **Severity:** medium
- **Evidence:** `AndroidUtilities.java:1399-1422` `doSafe` creates `newSingleThreadExecutor()` per call and blocks in `future.get(200 ms)`; called from `addLinksSafe` when `containsUrls` is true (MessageObject, captions, previews, ProfileActivity, PhotoViewer, ArticleViewer).
- **Change:** inline for short text (conservative cut-off), one shared bounded daemon pool for long text, an interruptible CharSequence so a timed-out match stops. Same result semantics.
- **Docs:** https://developer.android.com/guide/background/threading
- **Risk:** low.
- **Measurement:** cold open of a 200-message chat with links: thread creations, time in `future.get`.
- **Effort:** small
- **Status:** implemented (d18e568); interruptible CharSequence wrapper not done. Pool has no queue (stuck Linkify threads get a one-off thread for new calls); the inline path (1000 chars or less) has no timeout

### ANDROID-DESIGN-2: Fragment creation blocks the UI thread on the storage queue
- **Severity:** medium
- **Evidence:** `ChatActivity.onFragmentCreate` (~2705, 2744, 2779, 2797) CountDownLatch on the storage queue; GroupCreateFinalActivity:169-176, ProfileActivity:2162-2168, ChatEditTypeActivity:181, ChatEditActivity:343/359, ChatReactionsEditActivity:77, ChatCustomReactionsEditActivity:157 via `getChatSync`/`getUserSync` (MessagesStorage:18305); 15 awaits under `ui/`. Only the cache-miss branch.
- **Change:** resolve missing peers before `presentFragment` at the call sites that know the id (load on the storage queue, then present on the UI thread), starting with ChatActivity and ProfileActivity; a helper that logs (and flags in StrictMode) when a fragment still has to wait. Touch only the cache-miss branch.
- **Docs:** https://developer.android.com/topic/performance/vitals/anr
- **Risk:** medium (fragments assume a non-null chat).
- **Measurement:** open a chat from a notification right after cold start during a big sync: main-thread time blocked.
- **Effort:** medium
- **Status:** open

### ANDROID-DESIGN-4: StrictMode has no ThreadPolicy
- **Severity:** low
- **Evidence:** the only StrictMode use is a debug VmPolicy with `detectLeakedClosableObjects` in `LaunchActivity.java:409`, set after Application.onCreate. (Verifier text was cut off in the input after this point.)
- **Change:** in `ApplicationLoader.onCreate` for debug builds or a `nanogram.strictmode` flag: ThreadPolicy `detectAll().penaltyLog()` plus VmPolicy leaked registrations, SQLite, activity leaks, content URI without permission, unsafe intent launch (API 31), file URI exposure; `penaltyListener` (API 28) into NanogramDiagnostics. Remove the late LaunchActivity VmPolicy.
- **Docs:** https://developer.android.com/reference/android/os/StrictMode
- **Risk:** low (debug only).
- **Measurement:** violation count by stack after cold start and chat open, ratcheted down.
- **Effort:** small
- **Status:** implemented (55600ed), debug builds or flag `strictmode` (flag only read in debuggable builds)
