# Performance notes

## ui

Measure on the emulator (or a device) with `adb shell dumpsys gfxinfo org.klaipher.nanogram reset`, then `framestats`,
and Perfetto/`atrace -t 10 gfx view sched` for wakeups.

| Id | Fix | How to measure |
|---|---|---|
| UI-1 | Choreographer60FpsContent posts its vsync callback only while something is subscribed | Open a chat with an animated sticker, leave it, sit on a static screen. Perfetto: `Choreographer#doFrame` slices per second should drop to about 0 on the idle list (before: 60+). |
| UI-3 | No max refresh rate request on the main window after a configuration change, nor on long-lived PiP windows | Rotate the device, then `adb shell dumpsys display` / `dumpsys SurfaceFlinger` (active mode, `preferredRefreshRate` of the window); with the overlay in Developer options the rate should fall at rest. Device dependent. |
| UI-6 | `apply()` instead of `commit()` for keyboard height and drafts | StrictMode disk-write violations / Perfetto main-thread `fsync` slices when opening the keyboard or sending a message. |
| UI-8 | Wordmark baseline computed once | Allocation profiler on the chat list: no `Paint$FontMetrics` allocations while scrolling. |
| BATTERY-7 | Light sensor released while paused (Automatic night mode) | Settings > Appearance > Auto-night > Automatic; background the app; `adb shell dumpsys sensorservice` should list no active connection for the light sensor from this package. |
# Performance work

Sections are per work area (`## core` here; other areas add their own). Emulator measurements: `adb shell` commands
below assume `-s emulator-5554` and package `org.klaipher.nanogram`.

## core

| Id | Fix | How to measure |
|---|---|---|
| UI-2 | `FileLog.init` only creates the queue; log dir listing, pruning and opening run on `logQueue`. The app label and the start line are written from the diagnostics executor; the crash handler resolves the label when it writes a record. | `adb shell cmd activity start-activity -W` cold start (`TotalTime`) before/after; StrictMode or `atrace -a org.klaipher.nanogram` shows no DiskRead/DiskWrite from `Application.onCreate`. The start line must still be the first line of the new log. |
| UI-4 | The `:widgets` engine is prewarmed only if a widget session started in the last 14 days (`nanogram_widget_last_use` in the main prefs, written at most daily). | Fresh install: `adb shell ps -A \| grep widgets` 10 s after start shows no process; open a chat with an HTML widget, restart, the process appears after 3 s. `dumpsys meminfo org.klaipher.nanogram:widgets`. |
| MEMORY-2 | `WidgetRemoteEngine.release()` on background; the existing 30 s idle unbind ends the process, foreground prewarms again. A background or push-woken process never takes the hold (prewarm checks `ForegroundDetector`). Returning after more than 30 s cold-starts the engine; resume cost is not measured yet. | After a widget session, press Home, wait 40 s: `ps -A \| grep widgets` is empty. Foreground: process is back. |
| BATTERY-1 | `ConnectionsManager.onUpdate` posts `updateTimerProc` at most every 250 ms per account. | Simpleperf or `atrace` on `stageQueue` during a large download: `updateTimerProc` calls drop from the packet rate to at most 4 per second. Typing indicators and queue timeouts still fire (1 s epoll ceiling). |
| BATTERY-2 | With the screen off and audio-only playback, the progress timer posts to the UI thread once a second (round-video PiP keeps 60 Hz). | Play music, screen off, `dumpsys batterystats` / `atrace` main-thread wakeups: about 1 per second instead of 60. |
| BATTERY-6 | Long-audio position (`media_saved_pos`) saved with `apply()` every 5 s, and on pause/stop. | `strace`/`atrace` disk writes during a 10+ min voice message: one write per 5 s instead of per second; pause and resume returns to the exact second. |
| BATTERY-3 | A closed poll result is applied to the queued `MessageObject`, so an expired poll leaves `pollsToCheck`. | Open a chat with a timed poll past its deadline, close the chat, count `messages.getPollResults` in the verbose log: one request, not one per 1-5 s. |
| BATTERY-5 | MediaStore observers only set a flag while `mainInterfacePaused`; `checkGallery()` reloads once on return. | Background the app, take a screenshot, watch `logcat` / CPU of the process for 5 s: no gallery rescan; open the attach picker: the new screenshot is there. |
| MEMORY-1 | `ImageLoader.onTrimMemory`: UI hidden halves the bitmap cache, background also halves small images and drops lottie, moderate/complete/running-critical clear everything. | `dumpsys meminfo` (Native/Graphics) before and 20 s after Home, and with `adb shell am send-trim-memory org.klaipher.nanogram COMPLETE`. |
| MEMORY-3 | `VACUUM` in "Clear local database" runs with `temp_store = FILE`, MEMORY restored after. | Clear the database of a large account and watch `dumpsys meminfo` peak (no spike the size of the remaining data). |
| IO-1 | Plain downloads open the temp file with `rw`; a range is recorded only after `force(false)` on `filesQueue`; one sync before the rename. Encrypted downloads, iv, key, parts and preload files stay `rws`. | Download a large file and scroll a media chat: `strace -f -e trace=fsync,fdatasync -p <pid>` shows syncs on `writeFileQueue` only; stageQueue no longer blocks per chunk. Kill the app mid-download and resume: size matches. `cleanup()` forces the data file before closing it so the newest ranges are recorded on pause. Downloads without a parts file resume from the temp file length: after a power loss (not a process kill) the last chunk may be zero-filled on f2fs; accepted. |
| IO-2 | `PRAGMA synchronous = NORMAL` after WAL in `MessagesStorage` (both open paths). | `strace -e trace=fsync,fdatasync` during a busy group or `getDifference`: fsyncs drop to checkpoints. Accepted trade-off against upstream's FULL: after a power loss or kernel crash the last commits can be rolled back, which for a secret chat can reuse a sequence number or lose a rotated key. Ordinary chats recover through getDifference. |
| IO-3 | `FilePathDatabase` in WAL with `synchronous = NORMAL`; `putPath` in one transaction; checkpoint before the backup copy; `-wal`/`-shm` removed before a restore. | Auto-download a few media files and count fsyncs; `files/accountN/file_to_path.db-wal` appears and is truncated at the next launch. |
| IO-4 | `MP4Builder` no longer syncs every 32 KB; `finishMovie` syncs once. | Send a 20 MB video: conversion time and fsync count (`strace -e fsync`). |
| IO-5 | `FileStreamLoadOperation.read` reuses a 512 KB available window instead of one `stageQueue` round trip per read. | Stream a video, count `stageQueue` posts from `getDownloadedLengthFromOffset` (atrace) per second of playback. |
| IO-6 | tgnet coalesces `saveConfig()` after new session, init connection and bad salt into one write per second (timer on the network thread). | Cold start with `strace -e trace=fsync,rename -p <pid>` on the network thread: a few writes at startup instead of 5-12. |
| IO-7 | No `fsync` of the data-usage counter file every 2 s. | `strace -e fsync` during a download: none from `StatsController`. |
| IO-8 | Copy loops (save to gallery, gunzip) use 128 KB buffers without `Thread.yield`; `FileLoader.copyFile` keeps its `maxSize` exact. | Save a 500 MB video to the gallery: time and CPU of the app and `MediaProvider`. |
| IO-9 | Files in `<cache>/sharing/` older than 30 days (and not in use) are removed by the keep-media task. | Put an old file there (`touch -d`), run the task (set `lastKeepMediaCheckTime` back a day), the file is gone. |

Not done here: BATTERY-4 (tgcalls port, see CHANGELOG).
