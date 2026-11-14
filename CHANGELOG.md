# Changelog (private, detailed)

Newest first: version, build number, date, commit; what changed and how.


## Unreleased

### Release optimization settings (AND-OPT-2, AND-OPT-3)
- AND-OPT-2: gram-wallet `[profile.release]` now `opt-level = 3`, `lto = "fat"` (was `"z"`, no LTO); codegen-units=1,
  panic=abort, debug=false, incremental=false unchanged. Rust objects are native, so the app's ThinLTO never covered them.
- AND-OPT-3: `-Wl,--pack-dyn-relocs=android` (APS2) for libtmessages and libtwidget, non-Debug builds only (generator expression, like the LTO flags); size only, API 23+ loaders, not relr.
- Prebuild key now includes `TMessagesProj/jni/gram-wallet` (Cargo.toml profile, Cargo.lock, src, patch). Before, edits
  there never rebuilt the wallet engine, so this change would have linked the old opt-level z archive. The next build
  rebuilds the prebuilts once. The committed fallback `jni/prebuild/lib/*/libwallet_engine.a` (only used by builds outside
  ng) is still opt-level z.
- AND-OPT-1 (generated baseline and startup profile) NOT done here: needs `Tools/dev/ng profile` on the emulator, a
  build/run that this change set forbids. Run it and commit `src/main/generated/baselineProfiles/`.

### Tester round (Android 13.2.2 build 202611141530)
- TESTER-2 (no easy way to choose the microphone): Settings > Chat Settings > 'Microphone for voice messages and round videos' now lists Automatic plus the inputs connected right now (phone microphone, wired, USB, Bluetooth headset, LE Audio headset). `SharedConfig.recordInputDevice` (0 = automatic, else an `AudioDeviceInfo.TYPE_*`) replaces the `record_via_sco` boolean; a stored `true` migrates to Bluetooth headset. New `AudioInputPreference`: `applyTo(AudioRecord)` calls `setPreferredDevice` right after each `new AudioRecord` (voice messages x2 in `MediaController`, legacy and experimental round recorders, `CameraView`, `AudioCapture`); Bluetooth choices use `setCommunicationDevice` on API 31+ (LE Audio works) and `startBluetoothSco` below, wait up to 300 ms off the main thread for the route before recording, and stop through one idempotent `setBluetoothRouting(false)` (both old duplicated `setBluetoothScoOn` bodies now call it). No routing while a call is active, when the chosen device is gone (falls back to automatic) or when BLUETOOTH_CONNECT is missing; Automatic changes nothing (default source). Not done: output (playback) picker and a button on the recording screen. The default V2 round recorder (`RoundVideoCodecRecorder`) now also routes Bluetooth through `setBluetoothRouting` (prepare/release); the routing wait runs outside the lock; the settings row shows a saved but disconnected device as '<name> (not connected)'. JVM test: `AudioInputPreferenceTest`.
- TESTER-3 (round video sound runs ahead of the picture), recording side, legacy recorder `InstantCameraView`: (1) camera frame timestamps are converted to the audio clock (CLOCK_MONOTONIC) when they use CLOCK_BOOTTIME, so the old '> 10 s means desync' shortcut, which started audio at the first buffer, never fires; (2) the first audio chunk is searched from the oldest held buffer, buffers captured before the first encoded video frame are dropped (they used to be written untrimmed, after the 200 ms skipped-video window), with a 500 ms safety net; (3) audio buffer stamps use `AudioRecord.getTimestamp` only when it returns SUCCESS and are corrected by the frame position, then follow the sample count and re-anchor only on a jump above 30 ms (`RoundAudioTimeline.AudioClock`); (4) after pause/resume the audio start and the stop test use the raw (unshifted) video clock, the clock offset is re-read when the camera is switched; audio PTS 0 is the first video frame time and the first encoded video frame has PTS 0 instead of one frame interval; (5) audio stops at the last video frame (the stop test compared microseconds with nanoseconds). Kill switch: pref `round_av_sync_off` in `nanogram_experimental`. Playback uses stock ExoPlayer and needs no change. JVM test: `RoundAudioTimelineTest`. Not done: AAC priming (20-40 ms constant) and sample-exact trimming of the first buffer (within 21 ms now).
- TESTER-1 (round video far worse than the camera app): the legacy round recorder (`InstantCameraView`, only used when V2 is disabled by the server flag `android_round_video_recorder_v2_disabled` or the local choice; the default recorder in 13.2.2 is V2 = `InstantCameraView2`, already 480 px / 1.2 Mbps, so this fix may not reach a tester on V2 - ask for the recorder in use and an ffprobe of a circle) encoded 384x384 at about 1 Mbps from a 640x480 Camera2 stream. New `RoundVideoEncodingPolicy`: on AVERAGE/HIGH performance class the output is max(server `round_video_encoding` size, 480) up to 640 (TDLib video-note limit; even, square; LOW keeps the server value), bitrate = max(server value, 0.2 bit/px at 30 fps) capped at 2 Mbps (480 px: 1.38 Mbps, a 60 s circle about 10 MB), the Camera2 stream is requested at about 2x the output (up to 960; sizes with more than 3x the requested area fall back to the old request, `Camera2Session.create(.., fallbackSize)`), the encoder is asked for H.264 High with no B-frames (API 29+) (lowest level >= 3.1 the codec lists) and VBR when `CodecCapabilities` supports them, and `configure()` falls back to the plain format on any failure. A trimmed circle is re-encoded at the recorded size, bitrate and 30 fps instead of 360 px / 1 Mbps / 25 fps. Kill switch: `nanogram_experimental` pref `round_quality_off` (`NanogramSystemFlags.ROUND_QUALITY_OFF`). Still square H.264 up to 720 px (official clients play it). JVM test: `RoundVideoEncodingPolicyTest`.
- TESTER-5 (new photo missing in the attach picker for 1-2 s): `MediaController.setGalleryPickerVisible` (called from `ChatAttachAlert.show/dismiss`) reloads the album at once when the picker opens and a MediaStore change was seen since the last load (no time-based rescan); while the picker is visible and the app is in the foreground both MediaStore observers debounce 200 ms instead of 2000 ms (paused app: still only a dirty flag, BATTERY-5 unchanged; closed picker: 2000 ms); overlapping scans are dropped when a newer one has started (generation counter per guid) and the picker-visible flag is tracked per attach sheet; `checkGallery()` compares the image count with the cached image album and the video count with the cached video album (it compared images plus videos with images only, so it reloaded on every resume on devices with videos) and runs without the 2 s wait. Policy in `NanogramGalleryReload`, JVM test `NanogramGalleryReloadTest`. Pending (IS_PENDING) rows stay hidden; the camera's final save notification is picked up within about 200 ms.
- TESTER-4 (PiP round video could be dragged but not closed): `PipRoundVideoView` close is idempotent (closing/closed state, `PipRoundCloseState`): an animated close without a texture parent removes the window at once, a cancelled hide animation (`showTemporary`) still finishes the removal, `show()` closes an older instance and reports an addView failure; new small close button (top-right corner) next to the edge swipe. `MediaController` gets `showPip()`/`closePip()` (one place for the 5 create blocks and the 4 close blocks; the controller field is cleared by an on-closed listener) and `cleanupPlayer(.., stopService=true)` now closes the PiP outside the notify branch (history cleared while the circle plays) and before the restore-music early return. JVM test: `PipRoundCloseStateTest`.
### Build-system modernization (prebuild caching, Rust pin, ThinLTO cache)
- ccache now also fronts FFmpeg, libvpx (make-time `CC=`/`CXX=` override, so configure output, config.h and vpx_config
  stay identical; the prebuild runs without CCACHE_BASEDIR so object bytes match a no-ccache build too) and dav1d (meson cross-file `c = ['ccache', clang]`); openh264 unchanged.
- Rust: `rust=1.99.1` in toolchain.properties drives tlottie (was floating `stable`) and wallet-engine (was 1.96.1) via
  RUSTUP_TOOLCHAIN; (rust-toolchain.toml left as upstream; RUSTUP_TOOLCHAIN overrides it); wallet cargo target dir persists in
  `<cache>/prebuild-work/<worktree hash>`; dev-profile debuginfo off for bindgen; `ng setup` installs the toolchain and Android targets.
- `ng prebuild` installs the pinned Rust and Android targets before the C phase; an empty `NANOGRAM_CCACHE` disables ccache.
- Prebuild cache key now includes cmake/ninja/meson/(nasm)/rustc/cargo versions, recorded in COMPLETE.
- Prebuild scripts default NDK/API to toolchain.properties instead of r27/API 21 (ng still passes both).
- Release ThinLTO cache moved to `<cache>/thinlto` (`NANOGRAM_THINLTO_CACHE_DIR`), shared across .cxx trees.
- Every prebuild-script change moves the prebuild key: one full prebuild, then cache hits.


### Android media, database and search (modernization review, branch nanogram/mod-media)
- SHARED-SQLITE-2 (same flag as ANDROID-DB-1, default OFF): reviewed and found to have NO effect on the bundled SQLite
  3.39.3, so removed: `PRAGMA optimize=0x10002` on open (bit 0x10000 exists only from 3.46; 3.39.3 analyzes only tables
  used by queries on that connection, none at open), the `file_paths.db` light profile (`cache_size -2048` is about the
  compiled default of -2000), and `PRAGMA optimize` before close (all callers delete the file or retry a malformed DB,
  and a bare optimize runs an unbounded ANALYZE since nothing creates sqlite_stat1). Revisit after the SQLCipher/SQLite
  3.46+ swap: run optimize at a real idle point on storageQueue with `PRAGMA analysis_limit=400` first; note that query
  plans change once sqlite_stat1 exists. `cache_size`/`mmap_size` stay the ANDROID-DB-1 settings. Deferred: opening with
  `SQLITE_OPEN_NOMUTEX` (getDatabase() has ~25 callers; needs a thread audit; `jni/sqlite` is owned by the shared-codebase
  migration). JVM test: `NanogramDbTuningTest`.
- SHARED-FFMPEG-ANDROID-GIF-1 (flag, default OFF): H.264 clips up to 320 px (small GIFs and old 240 px round videos; current 384 px round videos are NOT covered) decode with the loop
  filter skipped (`skip_loop_filter = AVDISCARD_ALL`, same policy as iOS); larger clips are untouched. The policy goes by clip size, not tile size, so it also applies to full-screen GIFs, previews and seek decoders. The 320 threshold lives only in Java (`NanogramGifDecodeFlags.nativeMaxSide`) and is passed to native (`nSetSmallTileSkipLoopFilter(int)`), so the JVM test covers the real value; the native H.264 codec check is not JVM-testable and needs a device check that the toggle reaches the decoder. The dead
  `refcounted_frames` option is dropped and `thread_count` is explicitly 1 (many tiles decode at once). Switch: Debug menu >
  "Lighter decoding for GIFs up to 320 px". MediaCodec route deferred. JVM test: `NanogramGifDecodeFlagsTest`.
- ANDROID-SEARCH-1: local dialog search (`MessagesStorage.localSearch`) tracks already-collected users, chats and secret
  chats in hash sets instead of `ArrayList.contains` (up to 600x600/2 boxed compares), and transliterates every user and
  chat name through a 4096-entry LRU (`NanogramTranslitCache`; transliteration is a pure function of the string, so no
  invalidation). The cache is cleared on storage cleanup (logout/account removal). Result order is unchanged (list order still drives it). Deferred: narrowing from the previous
  query and fixed-size IN-list chunking (pairs with ANDROID-DB-2). JVM test: `NanogramTranslitCacheTest`.
- ANDROID-DB-2 (same flag as ANDROID-DB-1, default OFF): per-connection prepared-statement LRU (64, `NanogramStatementCache`)
  behind a new `SQLiteDatabase.queryCached()`. A cached statement is reset (not finalized) on `dispose()`, a statement
  still in use is never handed out twice (nested or concurrent callers get a fresh uncached one), cursor `dispose()`
  is idempotent, and `close()` finalizes the cache before `sqlite3_close`. First users: `messages_v2 WHERE uid = ? AND
  mid = ?` and `dialogs WHERE did = ?` folder lookups. IN-list chunking and the debug prepare counter are deferred. Slow-query log lines time each use of a cached
  statement, not the time since its creation.
  JVM test: `NanogramStatementCacheTest`.
- ANDROID-DB-1 (flag, default OFF): optional cache4.db tuning after the existing PRAGMAs, also on the recovery re-open:
  `cache_size = -8192` (average/high class), `mmap_size = 64 MB` (high class, 64-bit only), explicit
  `wal_autocheckpoint = 1000`. Switch: Debug menu > "Database tuning: cache, mmap, statements (restart)" (`NanogramDbTuning`, JVM
  test `NanogramDbTuningTest`). Deferred: SQLite 3.39.3 -> current amalgamation and the compile options
  (`SQLITE_DEFAULT_MEMSTATUS=0`, `SQLITE_OMIT_*`, ...) live in `jni/CMakeLists.txt`/`jni/sqlite`, owned by the build
  stream; `SQLITE_DQS=0` needs a SQL audit first.
- ANDROID-IMG-2: the in-memory image cache scales with the device instead of a fixed 30 MB cap: performance class HIGH
  with memoryClass >= 256 and >= 6 GB RAM gets 48 MB, other AVERAGE/HIGH devices with memoryClass >= 192 and >= 4 GB get
  32 MB (both capped at memoryClass/5); low class, `isLowRamDevice()`, small RAM and memoryClass < 192 keep upstream's
  size. Thresholds compare against the RAM Android reports (3.5 GiB and 5.5 GiB), since a "4 GB" phone reports about
  3.7 GiB and a "6 GB" phone about 5.6 GiB. Deliberately below the review's 64/40 MB until the low-memory kill rate is measured (`NanogramImageCacheSize`,
  JVM test `NanogramImageCacheSizeTest`). onTrimMemory handling unchanged.
- ANDROID-IMG-1 (flag, default OFF): decoded chat/thumb bitmaps get `prepareToDraw()` on the decode thread so the first
  draw does not upload the texture on RenderThread. Only for bitmaps up to 4 MB, with a waiting receiver (no
  prefetches), never hardware bitmaps. Switch: Debug menu > "Upload decoded images to the GPU early"
  (`mainconfig.nanogramGpuPreupload`, `NanogramImageFlags`). Every ImageLoader display path (stripped, SVG, file, cache decode) ends in `CacheOutTask.run`, which is
  the one hand-off covered; `ThumbGenerateTask` bitmaps (file thumbnails, ~812) are not preuploaded because they are
  compressed to disk and shown once, and the Lottie/animated frame paths (~1110) use their own bitmaps (status: not
  needed). The ImageDecoder/hardware-bitmap and decode-pool options are
  deferred (blur/getPixels paths, shared `header` buffers). JVM test: `NanogramImageFlagsTest`.
- ANDROID-VIDEO-2: the seek-bar preview decodes into one reused bitmap per scrub session (`NanogramScratchBitmap`,
  `AnimatedFileDrawable.getFrameAtTime(ms, precise, scratch)`) instead of allocating a full-resolution ARGB_8888 bitmap
  per step; released when the preview hides and again on close. Retriever path rejected (cannot read partial streams). JVM test:
  `NanogramScratchBitmapTest`.
- ANDROID-IO-1: "Save to gallery/Downloads" copies in 4 MB `FileChannel.transferTo` chunks (`NanogramFileCopy`) instead
  of 4 KB `transferFrom` calls (about 256k map/unmap round trips per GB); cancel and the 500 ms progress throttle kept.
  JVM test: `NanogramFileCopyTest`.

### ANDROID-MEDIA-1: system Photo Picker for the "open in" entries (experimental, off)
- Flag `photo_picker` (default off), API 33+ (`ACTION_PICK_IMAGES` is always present there): the attach "open in" entry (`ChatAttachAlertPhotoLayout`) and `ChatActivity.startPhotoSelectActivity` start `ACTION_PICK_IMAGES` (image only for avatars and single-image pickers) instead of the `ACTION_PICK` chooser; the old intent stays as fallback. The result handler detects picker video URIs by MIME type (`NanogramPhotoPicker.isVideo`) because they have no "video" in the URI. Check on a device that `AndroidUtilities.getPath` copes with picker URIs before turning the flag on.
- Not done: `READ_MEDIA_VISUAL_USER_SELECTED` and the partial-grant UI ("Select more" / "Allow all"). Android 14 compatibility mode already offers partial selection, and declaring the permission ends it, so the UI needs a device pass first.
- Tests: `NanogramPhotoPickerTest` (JVM).
### ANDROID-PREFS-1: apply() instead of commit() on UI-thread callbacks
- 34 synchronous `SharedPreferences.commit()` calls became `apply()`: every single-statement `edit()...commit()` in `ChatActivity` (23 hint/pinned/dialog-bar/translate-count/join-request keys) and `DialogsActivity` (archive hints, askAboutContacts, story hint, lock-screen asks), plus `MessagesController` removeSuggestion (5634), hidePeerSettingsBar (7976, 7996) and the notification-settings update path in `processUpdateArray` (20883). None reads the result, and none of these files is read by another process (the `:widgets` engine and `NotificationsDisabledReceiver` use other keys/files). Memory is updated at once; the write flushes in the background (QueuedWork at onPause/onStop).
- Not done (about 220 sites left): other `commit()` calls in `MessagesController`, `NotificationsSettingsActivity`, `NotificationsController`, `Theme` and shared helpers. Each needs a check for cross-process readers or ordering with a following read; follow-up. `StrictMode` (ANDROID-DESIGN-4) will list the remaining main-thread writes.
### ANDROID-NOTIF-1: progress-centric notifications for story upload and video conversion (experimental, off)
- Flag `progress_notifications` (default off), API 36 only: `NanogramProgressNotification.promote` rebuilds the existing compat notification with `Notification.ProgressStyle` (one 0-100 segment, progress, indeterminate at 0), `setRequestPromotedOngoing(true)` only on API 36.1+ (`SDK_INT_FULL`; Android 16.0 gets the progress style without promotion) and a short critical text ("42%"), and uses the app's `R.drawable.notification` instead of `stat_sys_upload`. Applied in `StoryUploadingService` (id 33) and `VideoEncodingService` (id 4); with the flag off or below API 36 the notifications are what they were. Manifest: `POST_PROMOTED_NOTIFICATIONS` is added for every build (normal permission, unknown and ignored below API 36.1), so the merged manifest is not unchanged even with the flag off.
- Tests: `NanogramProgressNotificationTest` (percent helpers, JVM). Check on an Android 16 device: upload a story, see the status-bar chip and lock-screen progress.
### ANDROID-MANIFEST-1: drop dead permissions and legacy storage flags (cleartext kept)
- `READ_EXTERNAL_STORAGE` now `maxSdkVersion=32` (Android 13+ uses READ_MEDIA_*). Removed `MANAGE_ACCOUNTS`, `READ_PROFILE`, `AUTHENTICATE_ACCOUNTS` (no-ops at minSdk 24), the app's own `USE_FINGERPRINT` line (the merged APK still has it: androidx.biometric 1.1.0 declares it for API < 28, so the aapt check shows no change there) and `preserveLegacyExternalStorage`. `requestLegacyExternalStorage` stays: Android 10 (API 29) honours it whatever the target SDK, and `BuildVars.NO_SCOPED_STORAGE` (SDK <= 29) code paths depend on legacy storage there. `GET_ACCOUNTS` stays for the sync adapter.
- Owner decision: global `usesCleartextTraffic` stays as upstream (in-app http links); no network_security_config. `INSTALL_SHORTCUT` stays (not in the verified change list).
- Check: `aapt dump permissions` before/after on the release APK; smoke test contact sync, biometric unlock and gallery on API 28 and 35.
### ANDROID-LOCALE-1: system App languages entry (Android 13+)
- `res/xml/locales_config.xml` (the bundled UI languages: en, ar, de, es, it, ko, nl, pt-BR, ru, uk) and `android:localeConfig`, so Settings > System > Languages > App languages lists Nanogram. When the system locale changes on API 33+ and the user has not picked an in-app language, `LocaleController.onDeviceConfigurationChange` maps it to a language pack (`NanogramLocaleMap`: language_script_region, language_script, language_region, language, then the old locale string) and applies it without saving an override. The same lookup is used at startup (it used to try the bare language first) and the mapping on configuration change runs only when the system locale tag actually changed (not on rotation, theme or font changes), so a cold start and a later rotation pick the same pack; server language packs and the in-app choice are untouched. Downloaded packs are not listed in the system screen.
- Tests: `NanogramLocaleMapTest` (JVM).
### ANDROID-PROFILING-1: ProfilingManager system traces in diagnostics
- Nanogram diagnostics (API 35+): "Record a system trace" calls `ProfilingManager.requestProfiling(PROFILING_TYPE_SYSTEM_TRACE)`; the result is copied to `cache/ng_profiles` (6 files, 64 MB, newest kept). On API 36 a switch "Record traces on ANR" (off by default, persisted; "ANR and cold start" where the platform has `TRIGGER_TYPE_COLD_START`, which is API 37 only) registers the triggers that exist, each built on its own so a missing constant skips only that one, and stores their results the same way; results tagged `manual` are skipped by the global consumer (the manual request stores its own). The API 36 calls go through reflection so a signature change fails soft. Traces go into the diagnostics zip as `profiles/` (whole files, never truncated) only when shared by the user; Clear logs deletes them. Nothing is uploaded automatically.
- Tests: `NanogramProfilingTest` (retention rule, JVM).
### ANDROID-DESIGN-4: StrictMode thread and VM policies in debug builds
- `NanogramStrictMode.install()` runs first in `ApplicationLoader.onCreate` (after the widget-engine-process return) when `BuildVars.DEBUG_VERSION` or the `strictmode` experimental flag is on: ThreadPolicy `detectAll` + log; VmPolicy leaked closables, registrations, SQLite objects, activity leaks, file URI exposure, and (API 31+) unsafe intent launch and content URI without permission. Release builds are unchanged and do not read the flag file at startup: the `strictmode` flag only counts in debuggable builds (`FLAG_DEBUGGABLE`). No `penaltyListener`: it would run FileLog (disk) from inside a violation. Expect upstream log noise; triage into docs/nanogram/performance.md.
- No JVM test: the class only wraps framework calls.
### ANDROID-DESIGN-1: no executor per link-parse call
- `AndroidUtilities.doSafe` runs on one shared bounded daemon pool (`NanogramSafeRunner`: 0 core, 4 max threads, `SynchronousQueue`, idle timeout; when all four are stuck in uninterruptible Linkify a call gets a one-off daemon thread instead of queueing behind them) instead of a new single-thread executor per call. `addLinksSafe` parses text up to 1000 chars inline on the caller's thread (on the same working copy, so the original keeps its spans if nothing is found); longer text keeps the 200 ms timeout guard. Result semantics unchanged. Not done: the interruptible CharSequence wrapper (Linkify works on a Spannable, so a wrapper changes span handling).
- Risk: text of 1000 chars or less now runs Linkify on the UI thread with no timeout (as the spec asked). Upstream added `doSafe` for crafted payloads that backtrack badly; a short crafted text is bounded by its length but not by time.
- Tests: `NanogramSafeRunnerTest` (JVM, includes stuck workers not blocking new calls).
### ANDROID-NET-1: network state from the default NetworkCallback (experimental, off)
- Flag `net_callback` (`NanogramSystemFlags`, default off): the default `NetworkCallback` feeds `NanogramNetworkState` (internet capability, metered, roaming, transport), `isNetworkOnline*` answers from that cache after the first callback (legacy NetworkInfo path before it), and the CONNECTIVITY_ACTION receiver is not registered. Account checks (`checkConnection`, `FileLoader.onNetworkChanged`) run only when online/transport/metered/roaming changes, and a stale `onLost` of a replaced network is ignored. VALIDATED is deliberately not required (captive portals, VPN), as before.
- Not changed: `isConnectionSlow` and the Wi-Fi/roaming helpers still read `currentNetworkInfo` (refreshed once per network change instead of by every broadcast); they need the telephony subtype.
- Review fixes: the callback field is set only after `registerDefaultNetworkCallback` succeeds (a throw leaves the broadcast receiver in place); both paths call one `checkConnectionForAccounts()` helper, so a per-slot rule added to it later covers both. Not done: a cellular subtype change (2G to LTE) with the flag on does not call `FileLoader.onNetworkChanged(isSlow)` (capabilities do not carry the subtype; polling it from `onCapabilitiesChanged` would run on every bandwidth update). Flag is off by default; revisit with `TelephonyCallback` display-info events.
- Tests: `NanogramNetworkStateTest` (JVM).
### ANDROID-POWER-1: Lite mode follows system Battery Saver and thermal status
- LiteMode's power saver now also applies while the system Battery Saver is on or the thermal status is SEVERE or worse (`NanogramPowerPolicy`, fed by a POWER_SAVE_MODE_CHANGED receiver and `PowerManager.addThermalStatusListener`, API 29+). Previously only the battery percent threshold applied. On by default (owner decision); flips go through the existing `onPowerSaverApplied` path. A switch under the Power Saver slider, "Follow system Battery Saver and heat" (pref `lite_mode_follow_system`, default on), opts out and takes precedence over the system signals even when the slider is off. Hot path: `LiteMode.getValue` skips the battery query and the flag lookup when the slider is off (the charging flag is cached at preference load).
- Experimental flag `power_skip_charging` (`NanogramSystemFlags`, default off): ignore the percent trigger while charging.
- Tests: `NanogramPowerPolicyTest` (JVM).

### ANDROID-DESIGN-2, -6, -7: deferred, no code in this stream
- DESIGN-2 (CountDownLatch waits on the UI thread in ChatActivity/ProfileActivity and others): the async rewrite
  changes onFragmentCreate semantics and touches heavily merged upstream files; needs a design pass of its own.
- DESIGN-6 (SafetyNet removal, play-services-vision to ML Kit): needs owner sign-off (server-visible login reasons)
  and TMessagesProj/build.gradle (build stream); the ML Kit part belongs to the ML work.
- DESIGN-7 (JVM unit tests): needs `testImplementation` in TMessagesProj/build.gradle (build stream) and Android-free
  seams (clock injection in NanogramLog, parsers out of NanogramUpdater/ProxySettings); `ng check` runs
  `:TMessagesProj:testDebugUnitTest`, which tests nothing today.

### Narrower R8 keep for tgnet, prepared and off by default (ANDROID-R8-1)
- `TMessagesProj/proguard-rules-tgnet-narrow.pro`: `-keepnames` plus `-keepclassmembernames { <fields>; }` for
  org.telegram.tgnet.**, and a full keep for tgnet.tl.TL_wallet** (WalletEngineBridge.cpp resolves its fields by name).
  The blanket `-keep class org.telegram.tgnet.** { *; }` in proguard-rules.pro stays until a release build with the
  narrow file passes the smoke test; enabling needs a proguardFiles entry in TMessagesProj_App/build.gradle (owned by the
  build stream). TL classes are built through TLClassStore factories, so only Gson in FileLog (verbose dumps) and the
  wallet JNI need names. Tools/r8_keep_check.py and proguard-checklist.md, which the rules file cites, are not in the tree.

### Empty catches in Nanogram-owned code now log (ANDROID-DESIGN-8)
- Localization.addResLocalization logs a failed asset load, ProxySettings logs an unparsable `port` in a proxy link,
  RoundVideoCodecRecorder logs a skipped `MediaCodec.stop()` (debug level, expected on a codec that never started).
  WebProxyTransport's four close/post catches now log too. Deferred (ANDROID-DESIGN-8 remainder): NanogramCrashHandler
  (logging inside the crash path can recurse), NanogramDiagnostics, twidget/* (WidgetSandboxSession, WidgetRemoteEngine,
  WidgetEngineBackend), round-video recorders (RoundVideo*), EnumSetting: files owned by other streams or hot
  close/cleanup paths, no behaviour gain.
  The `UnsupportedEncodingException` catch for UTF-8 is left as is (cannot happen). Upstream files (about 510 empty
  catches) untouched to keep merges small.
### Explicit main Looper for Handlers (ANDROID-DESIGN-10)
- CastSync's volume ContentObserver, the two PixelCopy callbacks in BottomSheetTabsOverlay and LocationSharingService
  use `new Handler(Looper.getMainLooper())` instead of the deprecated implicit-looper constructor (same thread as
  before). CastSync keeps the broad Settings.System observer: the music volume key differs per output device
  (speaker, headset, A2DP), so narrowing it to one key would miss changes. unregisterContentObserver was already in the
  stop path. WebRTC camera sessions untouched (shared upstream fork).
### DNS lookups on the DNS executor (ANDROID-DESIGN-5, ConnectionsManager part)
- The three DNS-config launches (lines moved from AsyncTask's pool) are wrapped in try/catch: on a rejected task the
  error is logged and `currentTask` stays null so the next request retries. Three call sites changed (not four).
- The Google and Mozilla DNS-config tasks in ConnectionsManager run on the existing `DNS_THREAD_POOL_EXECUTOR` (named
  threads) instead of the deprecated `AsyncTask.THREAD_POOL_EXECUTOR`. The task classes themselves still extend
  AsyncTask. Not done: ImageLoader, WebPlayerView, ui/web and JobIntentService tasks (upstream merge conflicts, no
  measured gain; since Android 10 the shared pool no longer rejects work).
### Startup: unused account slots not constructed at start, behind a flag (ANDROID-STARTUP-1)
- `StartupSettings.lazyAccountSlots` (key `startup_lazy_account_slots`, default OFF). When on, postInitApplication
  skips slots 1..3 that have no logged-in user: no MessagesController/MessagesStorage (storage thread and empty
  cache4.db), no ContactsController or DownloadController, and the network-state receiver (sticky, fires at
  registration and on every network change) skips those slots too, so their ConnectionsManager (native network thread,
  auth-key handshake, 19 s ping) and FileLoader are not built from there. Slot 0 and activated slots start as before; Add Account still builds its slot on first
  getInstance. Not done: deferring native_init inside the ConnectionsManager constructor and auditing the ~110
  `a < MAX_ACCOUNT_COUNT` loops (any loop that calls getInstance(a) still builds the slot, e.g. NanogramSystemInfo
  diagnostics). Server-visible change
  (no handshake for empty slots), hence the flag.
### Startup: Play Billing connection skipped when purchases are unavailable, behind a flag (ANDROID-STARTUP-3)
- `StartupSettings.skipPlayBillingAtStartup` (SharedPreferences `mainconfig`, key `startup_skip_play_billing`, default
  OFF). When on and `BuildVars.IS_BILLING_UNAVAILABLE`, postInitApplication no longer calls
  `BillingController.startConnection()` (currency JSON parse plus Play Store service bind on every process start,
  push wakes included). When skipped, BillingController is put in invoice mode (`deferConnection()`), so Stars
  top-up does not wait for store prices; `whenSetuped()` starts the connection lazily (and leaves invoice mode) for
  the LoginActivity SMS-fee flow. Default off; confirm Stars top-up and the SMS-fee login with the flag on.
### Startup: per-account constants computed once (ANDROID-STARTUP-2)
- ConnectionsManager keeps the app version string, install source, package id and the measured device performance
  class in static fields instead of recomputing them for each of the 4 account slots (the sysfs scan of
  cpuinfo_max_freq and the two PackageManager calls ran 4x per start and on every push wake). The measured class is
  cached separately from SharedConfig.getDevicePerformanceClass(), which applies the user override.
- AndroidUtilities.getCertificateSHA256Fingerprint() caches its result and reads signers through
  GET_SIGNING_CERTIFICATES on API 28+ (GET_SIGNATURES below), mirroring the legacy field: single-signer APKs use the
  oldest certificate of the signing history, which is what `PackageInfo.signatures` returned, so the value sent to the
  server is unchanged even with a v3 key-rotation lineage.

### ANDROID-TELECOM-1: system call integration (experiment, off by default) [a53d7a0, review fixes 47b014e..f8a6536]
- Debug settings > "Nanogram: system call integration (Telecom)" (`NanogramExperiments.selfManagedTelecom`, default OFF).
  On API 34+, for 1:1 calls only, `NanogramTelecomCall` registers a transactional self-managed Telecom call with the
  platform `TelecomManager.addCall` (no new dependency: core-telecom is a Kotlin/coroutines API, the platform call is
  what it wraps on 34+). `VoIPService.dispatchStateChanged` drives it: ringing/requesting -> addCall, key exchange on an
  incoming call -> answer, established -> setActive, ended/failed/busy -> disconnect with the matching cause. System
  answer/disconnect (Bluetooth, car, watch) call acceptIncomingCall()/hangUp(). Hold is refused.
- tgcalls keeps audio routing and the existing call notification; endpoint callbacks are ignored on purpose (one owner of
  state). The old ConnectionService path stays off (`USE_CONNECTION_SERVICE`), and the experiment does nothing if it is
  ever turned on. Group calls and API < 34 are unchanged.
- Review fixes: outgoing calls now register at STATE_REQUESTING (1:1 is decided by "no group call and a user", video from
  `videoCall`); disconnect causes are limited to LOCAL/REMOTE/MISSED/REJECTED (failed -> LOCAL, busy -> REMOTE; the
  platform throws on ERROR/BUSY); `onDestroy` ends the system call; answer/setActive are sent once. The call address is
  now an opaque `sip:nanogram-call`, so no Telegram user id reaches car/watch call logs.
- Counter `telecomCalls` in the `nanogram_experiments` preferences for the beta.
- Needs the device matrix before any default-on: Bluetooth headset, car head unit, watch, DnD with call exceptions, call
  while another app plays media; count audio-route glitches and missed rings. Tests: `NanogramTelecomCallTest`.

### ANDROID-CAMERA-1: Camera2 capture (experiment, off by default) [bc6e237, 8dedc68]
- Debug settings > "Nanogram: Camera2 for stories and chat camera" (`NanogramExperiments.camera2Capture`, default OFF)
  turns on the existing Camera2Session in `CameraView` (story recorder, chat camera, round video), which upstream had
  hard-disabled (`false && ...`). The QR/barcode scanners keep Camera1 (they read NV21 preview frames). If
  `Camera2Session.create` returns null the view falls back to Camera1 instead of a black preview.
- Video recording on Camera2 turns on video stabilization when the camera advertises it (recording request only: on EIS
  devices the field of view can crop when recording starts; compare preview vs recorded framing in the measure plan).
  The old "Use Camera 2 API" debug entry is relabelled "(round video only)" and the new one "(CameraView)".
- Counters `camera2Opened` / `camera2Unavailable` in the `nanogram_experiments` preferences for the beta.
- Not done: 10-bit HLG output, flipping the default (needs the device matrix); Camera2 for InstantCameraView stays on
  the old "Use Camera 2 API" switch.
- Measure: 1 minute 1080p30 story on a Pixel and a Samsung, flag off vs on: preview latency, dropped frames
  (`dumpsys media.camera`), battery, image quality. Tests: `NanogramCameraSupportTest`.

### ANDROID-DISPLAY-1: adaptive refresh rate hints (experiment, off by default) [03df1bc, 47b014e]
- Debug settings > "Nanogram: adaptive refresh rate hints (Android 15+)" (`NanogramExperiments.adaptiveRefreshRate`,
  default OFF). When on, on API 35+: views and drawables invalidated by the shared 60 fps clock vote
  `setRequestedFrameRate(60)`; overlay windows (story viewer/recorder, reactions, PiP-style windows) no longer pin
  `preferredRefreshRate` to the panel maximum (the system decides); the RecyclerView fork reports
  `setFrameContentVelocity` every fling frame (API 35+, like all the other hints). With the flag off nothing changes.
- Removed the unused `RefreshRateController` (commented out in LaunchActivity since before the fork).
- Not done: `Surface.setFrameRate` for the story video surface (needs a device to judge).
- Measure: `adb shell dumpsys SurfaceFlinger | grep -i 'refresh\|render rate'` with a sticker pack open (idle) on an
  LTPO phone, flag off vs on; test fling and story swipe on a 120 Hz panel. Tests: `NanogramDisplayTest`.

### ANDROID-ML-1: ML Kit replaces Mobile Vision [5ed14d0, f2e37a2, 5f72732]
- QR scan (camera scanner, QR overlay, story QR hint), document barcodes (MrzRecognizer driver licences) and face
  detection (photo viewer, paint, story paint) use ML Kit unbundled `play-services-mlkit-barcode-scanning` and
  `-face-detection` through one synchronous wrapper, `NanogramVision` (Tasks.await on the existing background threads).
  `play-services-vision` and its manifest meta-data are gone; the `com.google.mlkit.vision.DEPENDENCIES` meta-data
  pre-downloads the barcode and face models next to the subject segmentation one.
- If a model is missing (no Play services, de-Googled device) the wrapper reports not operational after the first failure
  and the existing ZXing path (QR) or the no-face result keeps working.
- Review fixes: the `com.google.mlkit.vision.DEPENDENCIES` meta-data now sits under `<application>` (ML Kit ignores it
  inside an activity). A detector that fails during a call hands the same bitmap/frame to ZXing. Transient failures
  (UNAVAILABLE while the model downloads, timeouts) pause ML Kit for 30 s process-wide and log once instead of marking
  the detector broken for the session. Tests: `NanogramVisionTest` (fallback decision, back-off window).
- Face rotation is now passed in degrees (the old code passed degrees where Mobile Vision expected 0..3).
- Test: `NanogramVisionTest` (rotation normalization). Needs a device check: scan one QR at 3 distances and in dim
  light, avatar face hint, driver licence PDF417.

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
