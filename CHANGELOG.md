# Changelog (private, detailed)

Newest first: version, build number, date, commit; what changed and how.


## Unreleased

- **App-config values saved under the wrong preference key** (2026-11-14, not built; one-line key fixes in `MessagesController.applyAppConfig`, checked by a script that pairs every `editor.put*("k")` with `mainPreferences.get*("k")`; the only remaining unpaired keys are written or read elsewhere on purpose):
  - `channels_public_limit_default` was saved as `publicLinksLimit` but loaded as `publicLinksLimitDefault`, so the server value was lost on every restart. Now saved as `publicLinksLimitDefault`.
  - `authorization_autoconfirm_period` was saved as `authorizationAutoconfirmPeriod` but loaded as `authorization_autoconfirm_period`. Now saved under the loaded key.
  - `ton_stakedice_stake_suggested_amounts` was saved as `tonStakeddiceStakeSuggestedAmounts` (typo) but loaded as `tonStakediceStakeSuggestedAmounts`. Now saved under the loaded key. The min and max amounts already matched.
  - `pts_total_limit` (iOS reads it from app config): Android deliberately sends a fixed 5000 (Wi-Fi) or 1000 (mobile data) on the first `getDifference` and has no app-config read; left as is, see NANOGRAM-CHANGES.md.
- **On-device transcription and translation switches, tgcalls fork record** (2026-11-14, not built; JVM test `NanogramOnDeviceTest` passes on a stub JUnit runner, JDK 21; the new Java files parse and `NanogramOnDeviceTranscription` compiles against android-36 with stubs for the app classes; the rest is not compiled; spec section 10):
  - Settings > Nanogram features, section "On this phone", both default OFF, plus "Download language models on Wi-Fi only" (default ON).
  - Translation: `com.google.mlkit:translate:17.0.3` (latest on Google Maven; owner approved; no Firebase added, `language-id` was already there). When on, `TranslateController` translates message batches with `NanogramOnDeviceTranslation`: language per message by ML Kit language-id, one ML Kit translator per pair, models fetched with `downloadModelIfNeeded` (Wi-Fi condition when the Wi-Fi switch is on), line by line so blank lines stay. If one message of a batch fails (unsupported language, download failed, no Wi-Fi), the whole batch goes to Telegram's server translation, so nothing is answered twice. Turning the switch on fetches the model for the app language; the screen shows downloading, ready or failed under the switches (no percentage: ML Kit exposes none without internal APIs). Covers chat auto-translate and voice-transcript translation; the translate sheet, stories and photo translation still use the server.
  - Transcription: `NanogramOnDeviceTranscription` decodes the already-downloaded voice file (Ogg/Opus) to PCM with `MediaExtractor`/`MediaCodec` and gives it to `SpeechRecognizer.createOnDeviceSpeechRecognizer` through `EXTRA_AUDIO_SOURCE` (a pipe, with sample rate, channel count and 16-bit encoding), so nothing leaves the phone and the microphone is not used. `EXTRA_AUDIO_SOURCE` exists only from API 33 and needs `isOnDeviceRecognitionAvailable`; on API 31-32 or without an on-device recognizer the switch is not shown (the recognizer can only listen to the microphone there). The language is the app language; turning the switch on checks the language pack (`checkRecognitionSupport`) and asks the system to download it (`triggerModelDownload`, system progress UI). Not downloaded yet, a recognizer error other than "no match", or an unsupported language: Telegram's server transcription runs as before. The Premium/trial gate on the transcribe button is unchanged.
  - Needs the build to check: R8 release build with ML Kit translate (known-issues keep rule for the translate JNI is not added: add it if a release build crashes in translation); APK size delta; switch on, then a message in a foreign language translates and the model status line goes downloading to ready; Wi-Fi only on mobile data falls back to server translation; API 33+ device: voice message transcribes with airplane mode on after the language pack is installed; API 31-32 emulator shows no transcription switch.
  - tgcalls fork 4f251c4 (`nanogram` branch of klaipher/tgcalls, shared by all three apps; not yet pinned here): livestream decode guards a null audio decoder context (`AudioStreamingPartPersistentDecoder.cpp`), so a stream whose codec fails to open no longer reaches `avcodec_send_packet` with null. Same fix as Mercurygram ea4c440b6 (GPL-2.0), reimplemented. The pin moves in the tgcalls integration step.
  - Review leftovers: commit 7090e15 checked against the findings (compile blockers, grammar, `from:` paging, hostname-proxy early writes, tracking list, send-report README parts, crop off the UI thread, strings): all present; the tracking list matches `link-tracking-params.txt` entry for entry. Nothing left open.
- **Review fixes for the Android integration** (2026-11-14, not built; JVM tests `NanogramSearchOperatorsTest`, `NanogramLinkHygieneTest` pass on a stub JUnit runner, JDK 21; other changes not compiled):
  - Build blockers: `NanogramSendReport` no longer calls the protected `BaseFragment.isFinishing()`; the R8 rule for ThemeDescription fields is now `-keepclassmembernames,allowoptimization class org.telegram.ui.** { <fields>; }` (the `View+ *` form is not valid R8 syntax).
  - Search operators now follow the shared spec (ios-integration `docs/specs/search-operators.md`): `after:` starts after the named day, month or year, `before:` is the start of it, `date:` ends at the start of the next range (the last second of a day is no longer lost); `from:me`; `type:poll` and `type:pinned`; an unclosed quote stays plain text and no longer swallows the rest of the query; `date:` with `before:`/`after:` takes the tighter bound. The sender, dates and type now survive paging with the search arrows (before, the `from:` filter was dropped once the cached results ran out). Help text lists the new operators.
  - Link hygiene mirrors the shared list `docs/specs/link-tracking-params.txt` exactly: adds `__hssc`, `__hstc`, `__hsfp`, `hsctatracking`, `vero_conv`, `spm`, `scm` and the host entries (YouTube `feature`/`pp`, Amazon `ref`/`tag`/`pd_rd_*`/`pf_rd_*`, TikTok, Facebook), host entries cover subdomains; Android-only extras (`_ga`, `_gl`, `srsltid`, `pk_`, `mtm_`, `hsa_`, `piwik_`, `matomo_`, `fb_*`, `wickedid`, `rb_clickid`, `si` on x.com) are removed until they are added to the shared file. The Links help text no longer claims Telegram links are asked about by default.
  - Hostname proxies: `ConnectionSocket::isDisconnected()` stays false while the delegate lookup runs, so the first packets queue and flush once the socket exists (the Mercurygram d7f19a657 port had them dropped).
  - Send report: the chat picker's account fallback and the "sent" toast no longer use the picker's own fragment (null pointer risk, toast could never show). The README now also lists file parts whose collector failed, with the exception class.
  - Mark all as read marks muted chats read again (as upstream); only the request for already-read chats is skipped.
  - Photo viewer: confirming a crop uses the bitmap already in memory (no full-resolution decode on the UI thread); the full-resolution crop is rendered when sending (`rebuildPhoto`, off the UI thread).
  - Needs the build to check: release (R8) build parses the keep rule and theme colours refresh; `from:@x` with more than 20 results, step past result 20 with the arrows; a hostname MTProto proxy on a cold start (time to connected); send report to a chat; crop a 50 MP photo and confirm (no freeze), then send as high quality.
  - Not done: the on-device voice transcription and on-device translation switches (spec section 10). Both need new platform code in upstream files (`SpeechRecognizer.createOnDeviceSpeechRecognizer` in the transcription flow, ML Kit Translate `com.google.mlkit:translate` in `TranslateController` plus model download UI) that cannot be checked without a build. The switches are not added as dead controls. See `docs/nanogram/features-gap.md`.
- **Mercurygram bug-fix and performance port** (2026-11-14, not built; JVM tests `NanogramCropMathTest`, `NanogramLiveLocationTest`, `NanogramFolderPinsTest`, `NanogramRecountThrottleTest` pass on a stub JUnit runner, every changed file parses with javac; ported from Mercurygram Android, GPL-2.0, per commit; full table in `docs/nanogram/mercurygram-port.md`):
  - Push latch (28fda5a3c): one `CountDownLatch` per push with a 20 s bounded await; before, every push after the first dropped its wakelock before the notification was built.
  - Ten upstream crashes (1c9dcb6fd), including `folder_id` -1 wiping the folder badges; large-account slowdowns (b1274170f): no admin-list re-fire for non-admins of a community, throttled folder badge recount (`NanogramRecountThrottle`), no dialog-list rebuild per deleted message, one TTL delete loop, Mark all as read only for unread chats; memory leaks (0545efe54), minus the sponsored-message hunks.
  - Media I/O (793d5745a): parts file `rw` with one sync, iv and encrypted temp `rwd`, two decode threads with per-task header buffers, cached stripped thumbnails.
  - Notifications: Android 16 heads-up names the chat (ede78cac5), the Sound screen no longer resets and syncs Default (231015942), the permission prompt no longer returns every launch (fd3d55126).
  - Live location "until stopped" no longer reads as expired (a2d04746f, `NanogramLiveLocation`); `PermissionRequest.hasPermission` asks the application context.
  - Secret chat delete for everyone sends the flush-history message first (01230d2c1); pin limit on folder tabs uses the chats-per-folder limit (3ac8b2bd1, `NanogramFolderPins`); legacy TL empty readParams (81011faf9); dice sticker cache (acd1861de); album layout reset (b02b89bd7); save to Downloads of fresh media (b569e7725).
  - Photo editing keeps full resolution and fixes several picker/editor bugs (e7019b3e8, 0dc808924: `NanogramPhotoCrop`, `NanogramCropMath`); watched videos with quality variants can be saved and shared (d19ba21d2: `NanogramLocalMedia`).
  - Proxies: no Google DoH lookup before resolving a proxy hostname, all resolved IPs, IPv6 proxy sockets (19fee5dae, 752c41b28, d7f19a657); R8 keep rule for ThemeDescription fields (84e783f3b, narrower, not reproduced).
  - Livestream null decoder context fixed in the shared tgcalls fork (4f251c4, pin bump pending).
- **Nanogram features screen, search operators, link hygiene** (2026-11-14, not built; JVM tests `NanogramSearchOperatorsTest` and `NanogramLinkHygieneTest` pass with a stub JUnit runner on JDK 21, Android code not compiled). Settings gets a "Nanogram features" row (after Language) opening `NanogramFeaturesActivity` (upstream `UniversalFragment` cells: header, switch, button, info shadow). Sections: Search (Search operators, default ON), Links (Remove tracking from links, Ask before opening Telegram links; both default OFF), Recording (Microphone for voice messages and round videos: the same chooser as Chat settings, now `ThemeActivity.showMicrophoneDialog`, shared by both screens), Power (Follow system Battery Saver and heat: the same setting as Power Saving settings). Search operators `from: before: after: date: type:` follow `docs/nanogram/search-operators.md`; `MediaDataController.searchMessagesInChat` parses a fresh query and applies `from_id`, `min_date`, `max_date` and the message filter to the main and merge-dialog requests; unknown tokens and an unresolvable `from:` stay plain text. Link hygiene (`NanogramLinkHygiene`, list in `docs/nanogram/link-hygiene.md`) cleans `http(s)` links on open (`Browser.openUrl`) and copy (`ChatActivity` link menu, `PhotoViewer`, `CustomTabsCopyReceiver`, `EmbedBottomSheet`); Telegram links and other schemes are never changed, message text is never rewritten; the optional t.me / tg: confirm is a dialog in `Browser.openUrl`. Defaults unchanged apart from the new switches. Needs the build to check: compile (Browser lambda captures, `ThemeActivity` static dialog), the screen renders and each switch persists, `type:photo` and `from:@user after:2026-01-01` in chat search return filtered results and paging keeps the filters, a link with `?utm_source=x&id=1` opens and copies as `?id=1` only with the switch on, t.me confirm dialog opens the link on Open, microphone chooser works from both screens.
- **One "Send report" button** (2026-11-14, not built; JVM test `NanogramReportPartsTest` written, not run: no JDK in this session). Settings > Help > Send report sits right under Ask a Question and replaces "Send diagnostics"; the Debug menu has the same button as its first item (its old entry is now "Diagnostics (developer)", still holding verbose logs, system traces and clear logs). Tap shows what is included (no messages, contacts or phone numbers), then "Send to a Nanogram chat" or "Share…". The flow moved from `NanogramDiagnosticsActivity` to `NanogramSendReport`. The zip is `Nanogram-report-<build>-<time>.zip` (the chat-send allow-list in `AndroidUtilities.isInternalUri` accepts both names) and its README now ends with the folders that are missing or empty and why. Tester guide and `android-release-what-to-test.txt` point at the one button. Needs the build to check: Settings row, dialog, share sheet and chat send of a `Nanogram-report-*` zip; README missing-parts list on an emulator (no system traces, no crashes).
- **CPU and energy numbers: review fixes** (2026-11-14, not built; JVM tests added, not run): (1) Debug settings read the snapshot before it was taken, so the lines showed a span of about 0 s; the refresh moved from `fillItems` to `onResume` and `NanogramHealth.refreshAsync(Runnable)` redraws the list once when the reading is in (the redraw does not refresh again). (2) HealthStats counters only run on battery: `MEASUREMENT_REALTIME_BATTERY_MS` is read, per-minute CPU figures divide by that, and a span with less than 90% of its wall time on battery, or where it went down (reset), prints "phone was charging or the counters reset in this span: CPU numbers not comparable" instead of rates; energy keeps wall time (power monitors count plugged-in time too). Guide: keep the phone unplugged; emulator check needs `adb shell dumpsys battery unplug` (the emulator reports AC power by default). (3) `NanogramPerfExperiments.resetToAuto` now also resets the CPU and energy baseline. (4) Heat headroom text says "1.0 = severe throttling" (docs: 1.0 is the `THERMAL_STATUS_SEVERE` threshold). (5) Energy per minute and mW use the power-monitor reading time (`PowerMonitorReadings.getTimestampMillis`, max over monitors) stored as `Sample.energyElapsedMillis`, not the time taken before the stats sync. Tests in `NanogramHealthMathTest`. Docs: developer.android.com `UidHealthStats#MEASUREMENT_REALTIME_BATTERY_MS`, `PowerMonitorReadings#getConsumedEnergy` and `#getTimestampMillis(PowerMonitor)`, `PowerManager#getThermalHeadroom`.
- **twidget pin moved to 07845c84** (2026-11-14, not built): litehtml template and structure patches rebuild the render tree when it cannot be kept; Apple-only border clip fix.
- **CPU and energy numbers for the performance experiments** (2026-11-14, not built; JVM test written, not run: no JDK in this session). `NanogramHealth` (Android, one daemon thread `nanogram-health`) takes `SystemHealthManager.takeMyUidSnapshot()` at app start (`NanogramDiagnostics.onAppStart`), on every experiment switch change (`NanogramPerfExperiments.setEnabled`), when the Performance experiments screen opens (`refreshAsync`) and when the zip is built; nothing polls and nothing runs on the main thread. `NanogramHealthMath` is the pure part (delta, per-minute, formatting, SDK gate). Keys read from `UidHealthStats` (all present-checked with `hasMeasurement/hasTimer/hasTimers`, so a missing key is skipped): `MEASUREMENT_USER_CPU_TIME_MS`, `MEASUREMENT_SYSTEM_CPU_TIME_MS`, `TIMER_PROCESS_STATE_{TOP,FOREGROUND_SERVICE,TOP_SLEEPING,FOREGROUND,BACKGROUND,CACHED}_MS`, `TIMERS_WAKELOCKS_{PARTIAL,FULL,WINDOW}`, `TIMERS_JOBS`, `TIMERS_SYNCS` (summed count and time over the timer map; wakelock, job and sync names are never kept), `TIMER_MOBILE_RADIO_ACTIVE`, `MEASUREMENT_{MOBILE,WIFI}_{RX,TX}_{BYTES,PACKETS}`, and from `STATS_PROCESSES` the summed `ProcessHealthStats.MEASUREMENT_FOREGROUND_MS` and `MEASUREMENT_STARTS_COUNT`. Not used: `MEASUREMENT_CPU_POWER_MAMS` and the other `*_POWER_MAMS` (deprecated in API 26, vendor-dependent and not reliable). Energy: only when `SDK_INT >= 35` and `getSupportedPowerMonitors` (async, asked once per process) returns a non-empty list; then `getPowerMonitorReadings` gives `getConsumedEnergy` in microwatt-seconds since boot per monitor (`ENERGY_UNAVAILABLE` skipped), stored as `rail:<name>` (`POWER_MONITOR_TYPE_MEASUREMENT`) or `consumer:<name>`; deltas against the start sample and against each experiment's own baseline. The readings are whole-phone, not per app, and the report says so; rails and consumers are summed separately (they overlap). Without monitors the report says "Energy: not available" in the experiment lines and "NOT supported" in `health-energy.txt`. Battery context: `ACTION_BATTERY_CHANGED` (level, status, temperature), `BatteryManager` `CURRENT_NOW`, `CHARGE_COUNTER`, `ENERGY_COUNTER` (unsupported values printed as "not supported"), `PowerManager.getCurrentThermalStatus` (API 29) and `getThermalHeadroom(10)` (API 30), guarded by SDK checks. Wiring: new zip entry `health-energy.txt` (README line), `performance-experiments.txt` gets per-experiment CPU ms/min (also per minute on screen, from `TIMER_PROCESS_STATE_TOP_MS`) and energy per minute since the switch last changed (baseline resets on a switch change like the other stats; app restart resets everything), the same two lines show under each switch in Debug settings, tester guide has a "What the CPU and energy numbers mean" section and decision rule 3 uses CPU and energy, battery percent only as fallback. Under 30 s no per-minute figure. Test: `NanogramHealthMathTest`. Docs used: developer.android.com `SystemHealthManager`, `PowerMonitor`, `PowerMonitorReadings` (API 35), `UidHealthStats`, `ProcessHealthStats`, `HealthStats`. The `NanogramDiagnostics` hunks of this change landed in commit 92a7a2b together with the minSdk cleanup. Needs the build to check: emulator (no monitors) shows "not available" lines and a zip with `health-energy.txt` saying "NOT supported", CPU lines fill; Pixel on Android 15+ shows rail and consumer energy, numbers grow between snapshots and reset when a switch is flipped.
- **Heat forecast lowers Lite mode early during calls** (2026-11-14, not built; JVM tests written, not run, logic only checked by hand): while a call runs (`VoIPService` sets `LiteMode.setHeavyWorkActive`), `LiteMode` reads `PowerManager.getThermalHeadroom(10)` every 5 s on the global queue (the system stops sampling 10 s after the last query, so polling slower than that always reads a one-sample buffer and returns the current headroom, not a forecast; the documented limit is about one query per second). Nothing is polled while 'follow system Battery Saver and heat' is off, and turning it on during a call starts the poll. `NanogramPowerPolicy.ThermalForecast` turns it into a signal with hysteresis: on at 0.9 or more, off at 0.7 or less, held at least 30 s, NaN (no forecast on this phone) changes nothing, and the signal is released after the hold once the call ends; polling stops when the call is over and the signal is off, so no wakeups when idle. The signal joins the existing system power signals (`systemSignal(..., forecastHot)`), so it only applies when the user keeps 'follow system Battery Saver and heat' on, and it applies the same power-saver preset as severe thermal status, a step earlier. Video playback and uploads are not wired yet (only calls). Tests: `NanogramPowerPolicyTest`. Needs the build to check: Lite mode turns on during a long call on a warm phone and off after. Ref: developer.android.com/reference/android/os/PowerManager#getThermalHeadroom(int).
- **Hardware codecs and automatic experiments (ANDROID codec/GPU stream)** (2026-11-14, not built; pure decision logic has JVM tests `NanogramMediaCapabilitiesTest`, not run):
  - `NanogramMediaCapabilities`: one probe for the media performance class (`Build.VERSION.MEDIA_PERFORMANCE_CLASS`), hardware decoder/encoder lookup (`MediaCodecList` REGULAR_CODECS, `isHardwareAccelerated() && !isSoftwareOnly()`, `VideoCapabilities` size/rate check) and AV1 hardware decode; no device allowlists, unknown means false/0 (today's behaviour).
  - Video playback (B2, B4): `VideoPlayer` sets `NanogramVideoDecoderSelector` (video decoders hardware-first, software kept behind them; this also puts hardware `video/av01` decoders ahead of dav1d, which stays for devices without one and for the native GIF/sticker path) and `setEnableDecoderFallback(true)`. Asynchronous MediaCodec queueing is the media3 default on API 31+, not forced. The 4K/high-fps gate needs no extra code: media3 already filters decoders by `isFormatSupported` including performance points.
  - Encoders (B5): `MediaCodecVideoConvertor` (avc path), `InstantCameraView` and `RoundVideoCodecRecorder` keep the platform default encoder when it is already hardware (today's behaviour); only when the default is software do they take the first hardware encoder that supports the size (and frame rate) by name. If that encoder refuses `configure`, its name is dropped and the platform default is configured instead (`NanogramMediaCapabilities.configureEncoder`). Output stays H.264/AAC MP4; HEVC stays behind the `SharedConfig` probe. Log line: encoder name and hardware/default, no content. Round video already sets `KEY_MAX_B_FRAMES=0` in its quality hints; `KEY_LOW_LATENCY` was not added (no live preview encode exists to benefit).
  - Automatic experiments (B9): flags are tri-state (automatic, tester on, tester off; a stored value wins). Adaptive refresh rate is automatic only on Android 16+ (API 36) when `Display.hasArrSupport()` is true for the default display (it needs the display HAL and Android 15 QPR1+; API 35 stays tester-only); early GPU upload is automatic when the media performance class is 31 or higher and the phone is not low-RAM. Small-GIF fast decode, Camera2 and system Telecom stay off until a tester switches them (GIF: quality loss, measure first; Camera2 and Telecom depend on camera HAL and Bluetooth/car/watch behaviour, not on a performance class). Debug settings shows a Back to automatic button once a tester changed an automatic switch; the diagnostics report prints automatic or set by tester.
  - Not done in this stream: B6 (WebRTC hardware factories live in the `jni/voip/webrtc` fork submodule, not initialised in this worktree, shared with iOS/Mac), B7 (platform `BitmapFactory` already decodes AVIF/HEIC on API 31; hardware bitmaps need an audited allowlist), B8 (the remaining CPU blur callers are upstream files with existing RenderEffect branches).
- **Ongoing and outgoing calls use the system call notification** (2026-11-14, not built; JVM test written, not run): the foreground notification of `VoIPService.showNotification` (outgoing 1:1 call, active 1:1 call, group call, conference, live stream) is now `Notification.CallStyle.forOngoingCall(person, hangUp)`, same channel, same id, same content intent; on Android 12+ the system shows its call chip and status-bar call indicator, and the notification works with the system call registration experiment (ANDROID-TELECOM-1). The separate `Leave`/`End call` action is gone (CallStyle draws its own Hang up button; the group-call 'Leave' wording becomes the system's Hang up). The content text is the old label (Outgoing call, Voice chat, Live stream, Conference) and the person name is the title, so the name is no longer shown twice. Person name comes from the existing title (`NanogramCallNotification.personName` falls back to a placeholder because CallStyle rejects an empty name, as the incoming path already does); avatar is the existing round bitmap except in conferences. Incoming call and incoming group-call ring notifications unchanged. Test: `NanogramCallNotificationTest`. Needs the build to check: notification renders on Android 12, 13, 14+ and Hang up ends the call. Ref: developer.android.com/reference/android/app/Notification.CallStyle.
- **Own-code fallback cleanup for minSdk 31** (2026-11-14, not built): removed pre-API-31 branches in Nanogram-owned code: `NanogramUpdater` (`GET_SIGNING_CERTIFICATES` only, `getLongVersionCode`, no `canRequestPackageInstalls` guard, `setRequireUserAction` and `FLAG_MUTABLE` always), `NanogramDiagnostics`/`NanogramSystemInfo` (install source, WebView, SoC, thermal status and headroom, standby bucket, background restriction, exact alarms, `BLUETOOTH_CONNECT`, signing certificate; the `API n` hints stay only for 33+), `NanogramStrictMode` (unsafe-intent and content-URI checks), `NanogramFrameStats`, `NanogramExitInfo` (`isSupported` gone, history always read), `GramWalletStorageAuth` (framework BiometricPrompt only; AndroidX prompt, `onActivityStopped` and its `LaunchActivity` call removed). Upstream files and the widget painter keep their `SDK_INT` checks (merge conflicts, no cost; the painter's software-canvas path stays reachable).
- **Private invite links are copied as sensitive** (2026-11-14, not built; JVM test written, not run): `NanogramClipboard.copyLink` marks the clip with `ClipDescription.EXTRA_IS_SENSITIVE` on Android 13+ (the system clipboard preview and overlay readers show dots instead of the text; on Android 12 the extra is simply not set) when the link is a private invite (`/+` or `/joinchat/`); public links copy as before. Used by Copy link in `LinkActionView`, `ManageLinksActivity`, `GroupInviteActivity` and the Invite via link row of `InviteMembersBottomSheet` (both copies there). `NanogramClipboard.copySensitive` is there for any other secret. No code, password, 2FA or seed-phrase copy action exists in the app (the only clipboard writes are links, addresses and TonConnect text), so nothing else needed the flag. Test: `NanogramClipboardTest`. Ref: developer.android.com/privacy-and-security/risks/sensitive-clipboard.
- **Requires Android 12 or newer (minSdk 31)** (2026-11-14, owner decision; not built): `minSdk` 24 to 31 in `TMessagesProj`, `TMessagesProj_App` and `baselineprofile` (targetSdk 36, compileSdk 37 unchanged; the optional Huawei/Standalone/HockeyApp/Tests modules keep their own minSdk 23/26 and no longer build with `-Pnanogram.allModules=true`: manifest merging fails against the minSdk 31 library; they need `tools:overrideLibrary` and are not supported). Native API level 24 to 31: `prebuildApi=31` in `Tools/dev/toolchain.properties` (drives every prebuild script and the prebuild cache key, so the first build rebuilds all prebuilts), `-DANDROID_PLATFORM=android-31`, `ng` fallback and `common.sh` default 31. Packed relocations APS2 to `--pack-dyn-relocs=android+relr` (libtmessages and libtwidget, non-Debug; RELR for relative relocations, APS2 for the rest; the NDK driver already adds plain relr at API 31 for every configuration, so Debug builds get RELR without APS2). Removed `multiDexEnabled true` and `vectorDrawables.generatedDensities` (inert at API 21+). Removed the legacy `BLUETOOTH` permission (only needed to API 30; `BLUETOOTH_CONNECT` stays). `BuildVars.NO_SCOPED_STORAGE` is constant false so R8 drops the legacy-storage paths. The in-app updater already skips APKs whose update.json `minSdk` is above the device, so Android 7-11 devices are not offered this build. Build must verify: `llvm-readelf -d` shows `ANDROID_RELR*` and `ANDROID_RELA`/`ANDROID_RELASZ` tags, `llvm-readelf -S` shows `.relr.dyn` and the `.rela.dyn` size against the previous APS2 build, merged manifest minSdk 31, APK size, launch on an Android 12 device, link with no API 32+ symbol. Not done (owner decisions): `dataExtractionRules`, `gwpAsanMode`/`memtagMode`, removal of the `uses-sdk tools:overrideLibrary`, Nanogram-owned SDK_INT cleanups (another workflow edits those files).

- **Performance experiments review fixes** (2026-11-14, not built; JVM test written, not run): flipping an experiment's switch clears that experiment's numbers (`NanogramPerfStats.reset(String...)` from `NanogramPerfExperiments.setEnabled`), so the B zip no longer holds A samples for the no-restart experiments (Camera2, Telecom); UI, report, diagnostics README and guide now say "since the switch last changed". The `FrameMetrics` listener and its HandlerThread attach only after the tester opened Performance experiments once (pref `nanogramCollectFrameStats`, `LaunchActivity.attachNanogramFrameStats`), so other users pay nothing per frame. `gifFrameDecode` records only clips with a larger side of at most 320 px (`AnimatedFileNative.getVideoFrame`); codec is not visible from Java, so small VP9 stickers and emoji still count (guide says so). Guide: decision rule 2 treats `frameInterval` as an observation for adaptive refresh (B expected higher); screen is called "Debug Menu" with the section further down; full-screen photo viewer and other overlay windows are not covered by `frameTime` (the listener is on the LaunchActivity window only).
- **Fork pins moved to the repaired heads** (2026-11-14, not built; syntax-only compiles): twidget d7527b40 (merged Apple layer and litehtml compile for iOS and macOS) and webrtc c48d71d7 (jni_zero include fix; carried files checked); shared tooling 6e68c485 records both. Same pins on all three apps; the 'twidget shim/Cocoa' files can be deleted in a later change.
- **Performance experiments screen for testers** (Debug settings, open it by tapping the version line at the bottom of Settings twice): one section "Performance experiments" with the device line (`Build.MODEL`, `Build.SOC_MODEL` on API 31+, Android version), one switch per dark experiment with a plain description and how it applies (restart, next call, reopen camera), and the numbers measured since the app started. Switches (all default off, same stores as before so existing settings carry over): ANDROID-DISPLAY-1 (API 35+), ANDROID-TELECOM-1 (API 34+), ANDROID-CAMERA-1, ANDROID-IMG-1, SHARED-FFMPEG-ANDROID-GIF-1, ANDROID-DB-1 (cache_size/mmap_size and ANDROID-DB-2 statement cache; SHARED-SQLITE-2 is not listed because it was removed as having no effect on SQLite 3.39.3). The six old loose switches in the debug menu are replaced by this section. Numbers (`NanogramPerfStats`, in memory, durations only, no content or numbers): `frameTime` and `frameInterval` of the main window via `FrameMetrics` (passive listener, asks the system for nothing, so it cannot change the refresh-rate decision; frames over 16.7 / 33.4 ms counted), `gifFrameDecode` around `AnimatedFileNative.getVideoFrame` (non-preview frames), `imageUpload` around `prepareToDraw` (only when ANDROID-IMG-1 is on, so there is nothing to compare in A), `cameraFirstFrame` from `createCamera` to the first rendered preview frame, `telecomRegister` from `addCall` to its callback, `callSetup` from key exchange to established for 1:1 calls (both with the switch off and on). ANDROID-DB-1 has no number. The diagnostics zip has a new entry `performance-experiments.txt` (device, state of every switch, numbers) and the README lists it. Tester guide: `docs/testing/android-performance-experiments.md` (fixed script per experiment, A then B with restart, what to report, owner's decision rule, extra checks for calls and camera); release paragraph: `docs/testing/android-release-what-to-test.txt`. JVM tests `NanogramPerfStatsTest`, `NanogramPerfExperimentsTest` (written, not run: no JDK in this session; run with the combined build). Needs the build to check: debug menu section renders (multi-line info cells), `FrameMetrics` listener attaches in `LaunchActivity`, diagnostics zip contains the new entry.
- **Widget painter: at most 16 nested filters** (`WidgetSandboxPainter.MAX_NESTED_FILTERS`, `canNestFilter`): a FilterPush past 16 open filtered levels draws its content unfiltered; push/pop stay balanced (`State.filtered` counts levels that got a RenderNode or bitmap). widget-sandbox v0.6.5 `paint-filter-depth`: 2,000 nested RenderNodes overflowed hwui's RenderThread stack (SIGSEGV in the app). Test: `WidgetFilterDepthTest` (runs with `ng check`).
- **Push (FCM) config**: the owner's Firebase project `nanogram-3f206` (`org.klaipher.nanogram`) is in `~/.config/nanogram-android/google-services.json`. `ng` now copies it into `TMessagesProj_App/` for release builds only and removes it for debug, check and profile builds, because the file has no client for `org.klaipher.nanogram.beta` and the Google services plugin would fail those builds. Push delivery still depends on Telegram accepting our FCM credentials at my.telegram.org.
- widget-sandbox submodule 80818b2c -> 7dfed64b (not built or run by its author; widgets nested deeper than 1024 elements are refused instead of crashing the renderer): widget-sandbox 7dfed64b (fork `nanogram`, merges Telegram v0.6.5: the v0.6.4 QuickJS security release, the v0.6.5 1024 nesting-depth cap that stops a widget with elements nested deeper than 1024 before any renderer, loop-based deep-DOM walks, new validator, sandbox and renderer bundles). QuickJS does not move (Telegram's pin b526a817 is inside our 7613b6b7). `shared/` = nanogram-deps 3ada4356. Push order: klaipher/quickjs `nanogram` 7613b6b7 first (it is not on GitHub yet; without it a recursive submodule update fails, `ng setup`/`--depth 1` included), then klaipher/widget-sandbox `nanogram` 7dfed64b, then nanogram-deps `main` 3ada4356, then the app repositories. The five `assets/WidgetSandbox/WidgetJS` bundles are copied from the fork's `packages/desktop/WidgetJS` and `manifest.txt` is regenerated (the earlier note that no regeneration is needed holds no longer: v0.6.5 changes the bundles). `patches/README.md` rows updated. Build must check: the engine boots (no `bundle-mismatch`, stamp 0.6.5/1/1/1), the widget corpus and attack set (new `deep-nesting`, `paint-*` files) on the emulator.

- tgcalls pin 73cc1f8d -> 7afc0b25 (fork `nanogram-shared`): ports upstream defea4a, so a group-call incoming video description with an empty ssrc group no longer reads `ssrcs[0]` of an empty vector (possible crash). `shared/` moves to nanogram-deps a17304db. Same commit as iOS and Mac.
- Cleanup: removed the unused ndk-build `jni/Application.mk` (Gradle builds through CMake only) and the tracked, generated `jni/prebuild/build_env.txt` (now git-ignored; `ng prebuild` deletes it after the run instead of `git checkout`); dropped the stale `.gitignore` entry `jni/ffmpeg/build_android`.
- Removed the committed upstream prebuilt fallback `jni/prebuild/lib/**` (64 `.a`, 4 ABIs) and `jni/prebuild/include/**` (FFmpeg `avconfig.h`/`ffversion.h`, wallet-engine bindings; 78 files, 203.7 MiB of blobs). `Tools/dev/ng prebuild` / `build` is the only supported path: `jni/CMakeLists.txt` stops with "run Tools/dev/ng prebuild / build" when `nanogramPrebuildDir` is missing or has no `COMPLETE` marker, the stale-fallback FFmpeg/wallet checks are gone, and `ng build --upstream-prebuilts` is removed. The hand-run script output directories are git-ignored.

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

### BoringSSL: klaipher/boringssl nanogram (shared with iOS)
- `.gitmodules`: `jni/third_party/boringssl` now points at klaipher/boringssl `nanogram` (shallow), gitlink 1c764cec = official 0.20261105.0 (3dc4b638, the old pin) plus two commits: the upstream Bazel files are removed (iOS builds the same tree under Bazel) and the records. Source files are byte-identical, so the prebuilt libs do not change. `prebuild/build_boringssl.sh` and `build_tdlib.sh` use only `CMakeLists.txt` and `include/`, no deleted file. Shared pin: iOS uses the same commit.

### Review fixes for the shared-source swaps
- `TMessagesProj/build.gradle`: `java.exclude '**/org/webrtc/EglThread.java'`. The fork's `sdk/android/api` holds upstream
  `EglThread.java` (73323d59), which the app never carried and which needs `EglBase.EglConnection`; the fork's Telegram `EglBase.java`
  has no such type, so javac would fail. The compiled `org.webrtc` set is again exactly the old 173 files (all blob-identical).
  The 173-files proof above never covered the 174th file; now it is excluded and recorded. Combined-build check: dex class list of `org.webrtc` equals fdd4407.
- Recipient key: reversed the earlier bridge choice. `nativePrepareSend` and `nativeSend` take a `boolean trust_recipient_public_key`
  that goes into `create_encrypted_comment`; `WalletEngine2` passes `TRUST_RECIPIENT_PUBLIC_KEY = false`, so Android verifies the
  key like iOS (owner decision: check on by default, opt-in only for a confirmed rotation). The server supplies the key for every
  recipient, so "always true" was the old no-check behaviour on every send, not a rotation path. No rotation-confirmation path exists in
  the host yet; until one sets the flag, a recipient whose key rotated fails the check instead of being trusted. Explicit deviation from the previous Android behaviour; owner to confirm.
- `jni/CMakeLists.txt`: the fallback to the committed `jni/prebuild` (no `nanogramPrebuildDir`, e.g. plain Gradle or Android Studio) now stops
  with a clear error when the wallet bindings lack `trust_recipient_public_key`, instead of failing in the compiler or linking the old cisco `libopenh264.a`. Refresh `jni/prebuild` after the combined prebuild to re-enable that path.
- `.gitmodules`: `shallow = true` on the rnnoise and sqlcipher-amalgamation entries (plan principle 8).
- Open items, no code change: (1) libevent to stdlib task queue (b2ab0f4) deviates from plan W5.1 and changes the threading of every call thread; owner awareness needed, and the emulator call matrix must measure call start latency and CPU in a long call and a group call; the fork's unused libevent snapshot stays until then.
  (2) `ReferenceCoreEmbedded.cpp` regeneration (W4.4, wasi-sdk plus the fork's `tools/build_wasm_core.sh`, sha256 against the iOS genrule) is a documented one-off with no tooling in `ng`; the v2wasm sources and json11 are unchanged from 545f5ae to 73cc1f8d, so the committed blob stays ABI compatible.
  (3) Push gate: the `shared/` gitlink is still 7d5bd332 and must move to a nanogram-deps commit that sets the tgcalls commit to 73cc1f8d; `forks/android-A-manifest.patch` is obsolete (0e3f132 already carries the Android consumer paths).

### tgcalls pin moved to 73cc1f8d
- `TMessagesProj/jni/tgcalls` 2a5daa0f -> 73cc1f8d (fork `nanogram-shared`, fast-forward of 9bfb76f8). Over 2a5daa0f the fork adds only the macOS SwiftPM `ios/` exclude in `Package.swift`, host-test path defaults and doc fixes; no source Android compiles changes (`tools/check_android_cmake.sh` file set is untouched). The three apps pin one commit.

### Cleanup after the tgcalls and WebRTC swaps (W6)
- Dead-name grep over the repository (tg_owt, nanogram-security-fixes, nanogram-mac, telegram-ios-dev, tgcalls-port,
  usrsctp, libevent, jni_zero, `voip/rnnoise`, `voip/third_party`, `third_party/{absl,libsrtp,pffft,rnnoise}`): the only hits
  left are history (this file, the bootstrap plan, `shared-swaps-ledger.md`) and the live paths under `jni/voip/webrtc/third_party`
  and `jni/third_party/xiph/rnnoise`. The tgcalls fork branch `nanogram-android` appears only as the historical source of 545f5ae.
- `NANOGRAM-CHANGES.md`: the old "WebRTC" row (`Candidate::set_type`) became "WebRTC source" and "WebRTC dependencies"; the rnnoise
  row no longer says `jni/third_party/rnnoise` is kept. `AGENTS.md` layout names the WebRTC submodule. The shared manifest
  (`shared/shared-libraries.json`) must list webrtc fa0b3c5f once, with abseil, libsrtp and crc32c as its nested pins and the
  Android `absl` path removed; `shared/` is not checked out here, the main session updates nanogram-deps.

### WebRTC Java: org.webrtc from the fork, in-app copy deleted (W5.5)
- Deleted `TMessagesProj/src/main/java/org/webrtc` (173 files). `TMessagesProj/build.gradle` adds the fork's Java directories
  as `java.srcDirs` of the `main` source set: `jni/voip/webrtc/sdk/android/api`, `sdk/android/src/java`,
  `modules/audio_device/android/java/src` (the `org.webrtc.voiceengine` classes) and `rtc_base/java/src`. No tests are
  on those paths. The IDE exclusion `jni/voip/webrtc` became `jni/voip/webrtc/third_party`; the `jni/third_party/absl`
  exclusion went with the submodule.
- Proof: all 173 deleted files map by `org/webrtc/...` path onto the four fork directories and are blob-identical there
  (173 of 173, none missing, none different; the 28 Telegram-modified and 9 Android-only files are in the fork's
  commit 8cf9fad2). The fork has one more class, `sdk/android/api/org/webrtc/EglThread.java` (upstream, unused by the app),
  so the dex class list grows by exactly that class and is otherwise the same.
- Build steps (main session): `ng check` compiles the Java (the Telegram-modified classes import `org.telegram.*`); compare
  the class list of `org.webrtc.*` in the dex before and after (expect +1: EglThread); R8 keep rules in
  `proguard-rules.pro` (`org.webrtc.*`, `.audio.*`, `.voiceengine.*`) are unchanged. Emulator: calls and screen-share audio.

### WebRTC C++: klaipher/webrtc nanogram (fa0b3c5f) replaces the in-tree copy and its dependencies (W5.5)
- Deleted `TMessagesProj/jni/voip/webrtc` (4,267 files), `jni/voip/third_party/{crc32c,jni_zero}` (libevent went in the step
  above), `jni/third_party/{libsrtp,usrsctplib,pffft,rnnoise}` and the `jni/third_party/absl` submodule. Added the submodule
  `jni/voip/webrtc` = klaipher/webrtc branch `nanogram` at fa0b3c5f69209ab4eb1b1976bc31b5818bbae687 (shallow; same commit
  as iOS and macOS). Its nested submodules arrive with `git submodule update --init --recursive`, which `ng setup` already runs:
  abseil 2065f4de (20260817.0, the commit Android pinned itself), libsrtp a566a9cf (v2.5.0), crc32c 21fc8ef3.
- Nothing lost (blob proofs against the fork head): of the 4,267 in-tree files 4,090 are identical, 92 generated JNI headers
  live in the fork under `nanogram/android-generated` (identical), 7 are not carried (the generated
  `registered_field_trials.h`, now produced at build time, and six protobuf outputs no compiled source needs), and 78 differ
  row by row in the fork ledger (ledger-webrtc-fork.md section 5): about 30 files where Android takes the fork or Mac
  version (older Android form, security and crash fixes it lacked), about 35 where the fork's newer or include-prefix form is
  kept, 6 AVX2-dispatch edits that become compiler flags, 2 folded into the fork's Android commits and 4 FileLog
  debug-counter files dropped by design (tgnet cannot be called from a shared fork). The
  fork also carries the Android-only trees (`modules/audio_device/android`, iSAC, `common_types.h`). pffft (11), rnnoise-vad
  (7) and jni_zero (30 files) are blob-identical to the fork's `third_party/` copies.
- `voip/CMakeLists.txt`: every `voip/webrtc/...` source keeps its path (the submodule is mounted where the copy was), so only
  the dependency entries moved. `gen_build_lists.py android` against the new file differs from the fork's committed Android list
  only by the libevent to stdlib swap, the eight x86 AVX2 files and pffft/rnnoise-vad now being tracked paths.
  - libsrtp: v2.5.0 file list (adds `cipher_test_cases.c`, `auth_test_cases.c`; no `ekt.c`, `stat.c`, `ut_sim.c`), the fork's one
    `third_party/libsrtp_config/config.h` (`GCM`, `OPENSSL`, `CPU_CISC`, `PACKAGE_VERSION`) first on the include path with
    `-DHAVE_CONFIG_H`. Behaviour: SRTP 2.2.0 to 2.5.0, GCM ciphers now compiled in (the old Android config never defined `GCM`).
  - crc32c: `src/src/crc32c.cc` and `crc32c_portable.cc` from the nested submodule; the app owns `voip/crc32c_config/crc32c/
    crc32c_config.h` (same content as before: portable code only, no SSE4.2 or ARMv8 intrinsics).
  - usrsctp is gone and `WEBRTC_HAVE_USRSCTP`, `__Userspace__`, `SCTP_*` are no longer defined (nothing included usrsctp).
  - `WEBRTC_USE_H264_DECODER` is defined next to `WEBRTC_USE_H264` (the fork gates the FFmpeg H.264 decoder on it).
  - Include shims in the build tree (`<build>/nanogram_shims`, symlinks): `third_party/openh264/src` to the openh264
    submodule and `third_party/opus/src` to `third_party/xiph/opus`, because the fork's sources use the Bazel/GN prefixes.
    `voip/webrtc/nanogram/android-generated` is an include root for the generated JNI headers.
  - `experiments/registered_field_trials.h` is generated at build time with `python3 voip/webrtc/experiments/field_trials.py
    header` into `<build>/nanogram_gen` (custom command attached to the `tgcalls` target).
  - x86 and x86_64: eight `*_avx2.cc` files compile with `-mavx2 -mfma` (the app had commented the AVX2 dispatch out of six
    sources); arm64-v8a and armeabi-v7a are unaffected.
  - `tgcalls` include path: `voip/rnnoise/include` (deleted earlier with the 2018 copy, leftover) became
    `third_party/xiph/rnnoise/include`, where `GroupAudioCapturePostProcessor.cpp` finds `rnnoise.h`.
  - `jni/CMakeLists.txt`: `add_subdirectory` for abseil and the abseil include path point at `voip/webrtc/third_party/abseil-cpp`.
- C++ standard stays `-std=c++17` (see the tgcalls entry).
- Build steps (main session): `git submodule sync --recursive && git submodule update --init --recursive --depth 1`, then
  `Tools/dev/ng check` (arm64, NDK r30). Things only a build can show: BoringSSL accepts libsrtp v2.5.0's `aes_gcm_ossl.c`
  and `hmac_ossl.c`; BoringSSL builds `openssl_stream_adapter.cc` with permute extensions on (guard it if not); `-Wundef`
  style warnings from `WEBRTC_ENABLE_PROTOBUF`; `field_trials.py` runs on the build host (python3 required); every
  `third_party/...` include in the fork resolves (openh264, opus, ffmpeg, libyuv, vpx). Emulator: 1:1 call (SRTP, with an
  iOS or Mac peer), group call, screen-share audio, video call H.264 and VP8 fallback.

### WebRTC task queue: stdlib instead of libevent (W5.5, step 1)
- `voip/CMakeLists.txt` compiles `api/task_queue/default_task_queue_factory_stdlib.cc` instead of
  `default_task_queue_factory_libevent.cc` and no longer compiles `rtc_base/task_queue_libevent.cc` or any libevent file;
  `jni/voip/third_party/libevent` (Chromium libevent 1.4.15 snapshot, 40 files) is deleted. `rtc_base/task_queue_stdlib.cc`
  was already in the build, so the swap is one factory file. Decision: stdlib removes libevent cleanly (libevent had no
  other user in the voip build; PhysicalSocketServer uses epoll directly), it is what iOS (stdlib) and the desktop
  clients ship, and upstream WebRTC is moving to it (field trial `WebRTC-TaskQueue-ReplaceLibeventWithStdlib`).
  The fork keeps its `third_party/libevent` snapshot for now (no app compiles it any more); dropping it from the fork
  is a follow-up there.
- Behaviour: every WebRTC task queue thread is now a `std::condition_variable` loop instead of a libevent loop. Delayed
  task precision and ordering are the same by contract; watch call audio/video start latency and CPU in a long call.
- Build steps (main session): `Tools/dev/ng check`; emulator: a 1:1 call and a group call of several minutes.

### tgcalls: klaipher/tgcalls branch nanogram-shared at 2a5daa0f, the one call engine for iOS, macOS and Android (W4.4)
- `TMessagesProj/jni/tgcalls` moves from branch `nanogram-android` (545f5ae) to `nanogram-shared` at 2a5daa0f
  (`.gitmodules` branch). The shared head is the merge of 545f5ae into the iOS/macOS line, so every Android engine
  commit is in it; Android gains the 63 Nanogram engine commits that iOS/macOS carry (LogSinkImpl, MediaManager, group
  audio, V2 negotiation changes).
- `voip/CMakeLists.txt` needs no source-list change: `tools/check_android_cmake.sh` (fork) against it lists only
  `v2/ContentNegotiationTest.cpp`, `v2/MtProtoDtlsTransportTest.cpp` and `v2/SignalingSctpOptionsTest.cpp`, the three host
  tests that stay out. The four `platform/android/*.cpp` files exist at the same paths. The fork adds no new `#include`
  outside what the old tree already included (checked by diffing the include sets 545f5ae..2a5daa0).
- C++ standard stays `-std=c++17`: the shared code uses no C++20 library or language feature (grep over `tgcalls/`
  except platform/darwin), WebRTC's own style guide is C++17, and abseil is configured at C++17, so a switch would
  also have to move abseil (ODR of `absl::` ordering types). Revisit only if `ng check` reports a C++20 construct.
- Interface changes that reach `org_telegram_messenger_voip_Instance.cpp` are source compatible, so the file is
  unchanged: `EncryptionKey::value` is now `shared_ptr<const array>` (the JNI code builds a non-const array and moves it
  in), `Descriptor` keeps `audioLevelsUpdated(float,float)` that Android sets, and the new `audioLevelUpdated`,
  `onPlayoutFrame` and `supportsHardwareVideoDecoding` hooks default to off.
- Build steps (main session): regenerate `voip/ReferenceCoreEmbedded.cpp` with the fork script
  (`tgcalls/tools/build_wasm_core.sh <wasi-sdk> <out>`, wasi-sdk installed by prebuild); the wasm sha256 must equal the
  iOS genrule output; commit the regenerated file. A stale blob against the new host code breaks v18/v19 calls.
  Then `Tools/dev/ng check` and the emulator calls (1:1 to iOS and Mac, a group call, a livestream).

### twidget and widget-sandbox: klaipher forks on branch nanogram (a4f5391c, 1b21a48e), shared with iOS and macOS (W3.6)
- `TMessagesProj/jni/twidget/twidget` moves from 4769d72 to klaipher/twidget `nanogram` at a4f5391c and
  `TMessagesProj/jni/twidget/widget-sandbox` from c498cef to klaipher/widget-sandbox `nanogram` at 1b21a48e
  (`.gitmodules` branch `nanogram` for both). Android's old pins are the bases of both branches, so nothing is lost.
- twidget gains (behaviour change for Android widgets): the gumbo parse limits (linear time, bounded memory: 100,000 nodes,
  depth 512), the iOS litehtml edits (caps, budgets, external-DOM builder, host profile hooks, SVG `<use>` budget, many
  fidelity fixes), the macOS canvas-size and percent-height fixes, and `core/src/renderer.cpp` changes that go with them.
  `twidget.cmake` is unchanged: its `litehtml/src/*.cpp` glob now also compiles `telegram_external.cpp` and
  `telegram_master_css.cpp` (portable C++, unused by the Android renderer), and `core/src` lists the same ten files.
  Open follow-ups in the fork records: `calc()` ceiling (4,096 expressions per process), `:lang()`/`:empty` rule changes.
- widget-sandbox: only the iOS Swift package, docs and the nested quickjs change. The nested quickjs
  (`packages/ios/Sources/CQuickJS/quickjs`) becomes klaipher/quickjs `nanogram` f6fc68b9 = TelegramMessenger b526a817
  (contains Android's ca3594da) + the iOS host API commits (inert unless called) and `TELEGRAM_QJS_ENABLE_DUMPS` (dumps
  off unless defined; the Android build does not use them). Run `git submodule sync --recursive` so the nested url follows.
- `assets/WidgetSandbox/*` and `manifest.txt` need no regeneration: the desktop bundles in widget-sandbox are unchanged
  and equal the committed assets (proof in docs/nanogram/shared-swaps-ledger.md); QuickJS bytecode version stays 29.
- `check_shared` note: the manifest of nanogram-deps 7d5bd332 still says `pending` for twidget, widget-sandbox and quickjs
  with the old Android pins as transitional; the main session sets their commits to the pushed shas (a4f5391c, 1b21a48e,
  f6fc68b9) in nanogram-deps and moves `shared/` in all three apps.
- Build steps (main session): `Tools/dev/ng check`; emulator: widget corpus, attack and render sets.

### wallet-engine: klaipher/wallet-engine nanogram (44ae85c7), the single source for iOS and Android (W2.I3)
- `TMessagesProj/jni/wallet-engine` moves from klaipher master 5c65e47c to klaipher/wallet-engine branch `nanogram` at
  44ae85c7 (`.gitmodules` branch). The fork is the iOS pin (i582 e59e0d89) plus Android's features (key rotation preparation
  and preview, NFT transfer preparation, TON Connect envelope encrypt/decrypt), blob-identical to 5c65e47c for those files.
- Deleted `jni/gram-wallet/wallet-engine.patch` (a reference copy of Android's delta; proof in
  docs/nanogram/shared-swaps-ledger.md: it applies to i582 38147c2 and reproduces 5c65e47c, which the fork carries).
- Recipient key (owner decision): the fork verifies a supplied comment recipient key by default (iOS behaviour). Android
  supplies the key it got from the server for rotated recipients, so `WalletEngineBridge.cpp` passes
  `trust_recipient_public_key = true` in `nativePrepareSend` and `nativeSend` (`create_encrypted_comment({..., recipient_key, true})`),
  which keeps today's Android behaviour exactly.
- `gram-wallet/Cargo.lock` is unchanged: `cargo metadata` against the fork tree (Rust 1.99.1, scratch copy) needs no lock edit.
- Build steps (main session): `Tools/dev/ng prebuild` (regenerates the C++ bindings, which gain the new
  `trust_recipient_public_key` record field, and builds with `--locked`; if the lock is rejected run `cargo update -w` in
  `jni/gram-wallet` with Rust 1.99.1 and commit it) then `ng check`; emulator: wallet send with an encrypted comment to a
  contact, preview/emulate, key rotation, NFT transfer, TON Connect.

### SQLite: klaipher/sqlcipher-amalgamation nanogram (9948d5af, SQLCipher 4.20.0 / SQLite 3.54.1), shared with iOS and macOS (W2.I3)
- Deleted `TMessagesProj/jni/sqlite/sqlite3.{c,h}` (SQLite 3.39.3, byte-identical to the pristine sqlite.org amalgamation,
  see docs/nanogram/shared-swaps-ledger.md) and added the submodule `TMessagesProj/jni/third_party/sqlite` =
  klaipher/sqlcipher-amalgamation branch `nanogram` at 9948d5af, the amalgamation iOS ships. Android moves from SQLite
  3.39.3 to 3.54.1 (accepted behaviour jump; includes the 3.51.3 WAL-reset corruption fix).
- `jni/CMakeLists.txt` `sqlite` target: compiles `third_party/sqlite/sqlite3.c` with the SQLCipher codec enabled against
  the BoringSSL already linked (`SQLITE_HAS_CODEC=1`, `SQLCIPHER_CRYPTO_OPENSSL`, `SQLITE_EXTRA_INIT/SHUTDOWN`,
  `SQLITE_THREADSAFE=1`, `SQLITE_TEMP_STORE=2`; BoringSSL include path; links `crypto`). No key is ever set
  (`sqlite3_key` is never called), so cache4.db and the other databases stay plain SQLite and a 3.39 database opens
  as before. Also `SQLITE_OMIT_LOAD_EXTENSION`, `HAVE_USLEEP=1` (busy waits in microseconds, not whole seconds) and
  `NDEBUG=1`. Not set: `SQLITE_DQS=0`, `SQLITE_OMIT_DEPRECATED`, FTS5.
- `SqliteWrapper.cpp` includes `<sqlcipher/sqlite3.h>` (the fork's header layout, include dir `third_party/sqlite/headers`);
  `TMessagesProj/build.gradle` idea exclude follows the new path.
- Build steps (main session): `Tools/dev/ng check`; emulator: open an existing cache4.db written by the 3.39 build (WAL
  files included), chat list, search, message history, `PRAGMA temp_store = FILE` path (database clear), group-call noise
  suppression unaffected.

### libprisma: klaipher/libprisma nanogram (c061944d), the single source for iOS, macOS and Android (W2.I3)
- `TMessagesProj/jni/third_party/libprisma` moves from Telegram's 94640c63 to klaipher/libprisma branch `nanogram` at
  c061944d (`.gitmodules` url and branch). The fork carries Android's regex budget (`BudgetedPosition`, `weak_ptr` guard,
  `search()`/`searchesToGiveUp`) reconciled with upstream's per-byte budget and with iOS's `TokenizeStats`/`TokenizeLimits`.
- Deleted `jni/prisma/libprisma/*` (the four override files) and `jni/prisma/generate.js`. `prisma/CMakeLists.txt` no
  longer copies sources into the build dir with `configure_file`; the `prisma` target compiles the submodule's
  `libprisma/{Highlight,LanguageTree,SyntaxHighlighter,TokenList}.cpp` directly. `BOOST_REGEX_MAX_BLOCKS=1536`, the
  `PrismaIncludes.h` force-include, `-O2` and the JNI bridge are unchanged.
- `prisma/generate_prisma_grammars.sh` runs the submodule's `generate.js` with `PRISMA_JOIN_ALIASES=1` (every Prism CSS
  class stays in the alias field; plain `npm start` would give the iOS grammars.dat and drop them).
- Build steps (main session): `Tools/dev/ng check`; regenerate the asset with `TMessagesProj/jni/prisma/generate_prisma_grammars.sh`
  (needs npm and network for `npm ci`) and check that `src/main/assets/grammars.dat` is unchanged (the committed file is
  the output of the old Android generator at the same grammar version; a diff means the pin brought newer grammar
  components, in which case commit the regenerated file). Emulator: code blocks incl. the regex-budget and HTML-tag attack cases.

### rnnoise: klaipher/rnnoise nanogram (d2d79970), the single source for iOS, macOS and Android (W2.I3)
- Deleted the in-tree 2018 copy `TMessagesProj/jni/voip/rnnoise` (22 files, all unmodified xiph blobs, see
  docs/nanogram/shared-swaps-ledger.md) and added the submodule `TMessagesProj/jni/third_party/xiph/rnnoise` =
  klaipher/rnnoise branch `nanogram` at d2d79970 (xiph v0.2 + portability headers + `denoise.c` hardening + trimmed model).
  Not `jni/third_party/rnnoise`: that directory is Chromium's rnnoise-vad (`rnn_vad_weights`), a different library.
- `voip/CMakeLists.txt` `rnnoise` target: sources are now the ten v0.2 files iOS builds (celt_lpc, denoise, kiss_fft, nnet,
  nnet_default, parse_lpcnet_weights, pitch, rnn, rnnoise_data, rnnoise_tables; the dump/write tools with their own
  `main()` are not built); the long `_celt_*`/`opus_fft_*` rename list and the Opus-style definitions
  (`USE_KISS_FFT`, `NULL=0`, `restrict=`, `__EMX__`, ...) are gone, replaced by the three iOS renames `compute_rnn`,
  `eband20ms`, `init_rnnoise`. PUBLIC compile options/definitions and ARM mode are unchanged.
- Behaviour change (accepted): voice-message noise suppression uses the v0.2 built-in model (`audio.c` API unchanged).
- Build step (main session): `ng check`; emulator: record a voice message with noise suppression on.

### OpenH264: klaipher/openh264 nanogram (3583f617), the single source for iOS, macOS and Android (W2.I3)
- `TMessagesProj/jni/third_party/openh264` moves from cisco/openh264 v2.7.0 (1bca6231) to klaipher/openh264 branch `nanogram`
  at 3583f617 (`.gitmodules` url and branch): v2.7.0 plus the two commits iOS already ships, H.264 levels 6.0-6.2 and the
  subcodec sprite mode (`bSubcodecMode`, default off, so normal video calls encode exactly as before).
- No script change: `build_openh264.sh` still builds the tree with its Makefile. The submodule commit is part of the
  `ng prebuild` key, so `jni/prebuild/lib/*/libopenh264.a` is rebuilt (build step for the main session).

### libyuv: klaipher/libyuv nanogram (947f5326), the single source for iOS, macOS and Android (W1/W2)
- `TMessagesProj/jni/third_party/libyuv` moves from chromium.googlesource.com 28ce69c2 to klaipher/libyuv branch `nanogram`
  at 947f5326 (`.gitmodules` url and branch). The fork is upstream 0b8dd231 (version 1977, the version iOS ships) minus
  `BUILD.bazel`, `WORKSPACE.bazel` and `libyuv.bzl`; every source file is byte-identical to upstream, so nothing local is lost.
- No CMake change: `jni/CMakeLists.txt` still `add_subdirectory`s the upstream `CMakeLists.txt` and keeps
  `CAN_COMPILE_SME FALSE`. Android gains the libyuv commits between 28ce69c2 and 0b8dd231 (scaling and ARM kernels).
- Not built (agent rule): the main session's `ng check` confirms the new libyuv compiles with NDK r30 and the SME switch.

### Shared libraries: one manifest, one checker, one source per library (W0.4)
- New submodule `shared/` = klaipher/nanogram-deps (branch main, shallow), pinned at 7d5bd332, the same commit as Nanogram
  iOS and macOS. It holds `shared-libraries.json` (every shared library with its single source and commit) and
  `check_shared.py`. Android keeps no copy of either.
- `Tools/git-hooks/pre-push` runs `python3 shared/check_shared.py --side android --quiet` before anything else and stops the
  push when a pin differs from the manifest or when `shared/` is not checked out (`NG_PRE_PUSH_SHARED=0` skips it for one
  push; `--no-verify` stays forbidden). Verified on a scratch repo: a correct gitlink passes, a skewed gitlink exits 1.
- AGENTS.md: the shared-code rule now says one source per library and that all three pins move the same day; the
  `tgcalls-port` and per-platform tgcalls branch wording is gone (tgcalls is one branch, `nanogram-shared`, for all apps;
  the Android pin and `.gitmodules` branch move with the tgcalls integration step).

### Shared-library sources, no new repos (W1.3)
- `.gitmodules`: dav1d url -> https://code.videolan.org/videolan/dav1d.git (pin 54706fc, tag 1.5.4) and wamr url ->
  https://github.com/bytecodealliance/wasm-micro-runtime.git (pin 25bd7eb); same commits, so no rebuild. Both match the
  canonical upstream homes used by iOS and macOS.
- Removed the dead `rlottie/src/lottie` include from voipandroid (no rlottie directory exists in this tree).
- Rust toolchain pinned: `toolchain.properties` `rust=1.99.1` (the channel in Mac's `core-xprojects/rust-toolchain.toml`).
  `ng prebuild` exports it as `TLOTTIE_RUST_TOOLCHAIN` and `RUSTUP_TOOLCHAIN` (tlottie defaulted to `stable` and
  wallet-engine to its own rust-toolchain.toml, so each could build with a different compiler) and the value is part of the prebuild cache key. `ng doctor` reports it.
  Install once: `rustup toolchain install 1.99.1 --profile minimal` and `rustup target add --toolchain 1.99.1
  aarch64-linux-android` (the build script already prints that command when a target is missing).

### Shared tlottie (dkaraush/tlottie v1.0.6, same as iOS and macOS)
- `TMessagesProj/jni/tlottie` now points at the official dkaraush/tlottie v1.0.6 (92df98dc) instead of klaipher/tlottie
  31f1b542 (v1.0.4 line); `.gitmodules` url changed. klaipher/tlottie carried no local commits of ours, and 31f1b542 is
  an ancestor of 92df98dc, so nothing is lost. Android gains 6 commits: 8386793 limits overhaul (2000 px canvas, NFT
  model sticker fix, rlottie_2019 NFT pattern compatibility, faster asset id parsing), b8ed399 v1.0.5, 0e82666 merge,
  4ad34f6 empty JSON arrays for vectors (rlottie compatibility), a9164a0 v1.0.6, 92df98d v1.0.6 follow-up. The C API
  header is unchanged; the prebuilt rebuilds because the submodule commit is part of the `ng prebuild` key.
- Behaviour change on hostile input (same as iOS and macOS): v1.0.6 removes the cumulative parse and render work budgets
  (`max_parse_work`, `max_render_work`; `Limit::ParseWork` removed, `RenderWork` -> `RenderPixels`) and raises the memory
  caps (`max_parse_bytes` 13.0 MB -> 43.7 MB; `max_render_bytes` 36 MiB fixed -> 116 MB at 720x720, now scaled with output
  area); most per-layer caps rise 1.2x to 8x. A crafted sticker from any chat can use more CPU and memory per frame.
  Deferred: timing and peak-memory check of the heaviest corpus stickers in the emulator corpus run.

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
