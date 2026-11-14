# Diagnostics and log collection

Testers send us their own logs; nobody on the team owns an Android phone, so a problem on a friend's device must be
explainable from one zip. This mirrors Nanogram iOS (Settings > Debug log export, MetricKit crash/hang reports) with
what Android provides.

## For testers

Settings > Help > **Send report** (right under Ask a Question; also the first item of the Debug menu). One button, one
zip with everything. A dialog states what is included, then:

- **Share…** builds the zip and opens the system share sheet: a Nanogram chat, email, Telegram, Drive...
- **Send to a Nanogram chat** builds the zip and opens Nanogram's own chat picker; the file goes to the chosen chat
  (or topic). No bot or username is built in.
- The old Settings entry "Send diagnostics" is gone. The Debug menu keeps "Diagnostics (developer)" with the verbose
  switch, system traces, clear logs and the same Send report button.
- The README in the zip lists every part and names the folders that are missing or empty, with the reason.
- **Verbose logs**: only when asked. Much more detail, more storage and battery. Network logs switch after a restart.
- **Clear logs** removes logs, crash records, call logs and old zips.
- The "Recent problems" list shows "Nanogram closed unexpectedly at <time>: <reason>" for the newest crash, ANR or
  memory kill; entries not seen before are marked New. Nothing intrusive is shown at launch.

## What the zip contains

`Nanogram-report-<versionCode>-<yyyyMMdd-HHmmss>.zip`

| Entry | Content | Android version |
|---|---|---|
| `README.txt` | What is inside and what is excluded | all |
| `device-info.txt` | App (version, versionCode, install source, signing cert SHA-256, first install/update, WebView), device (model, SoC, Android and patch level, ABIs, 16 KB page size, low-RAM, memory class, GL ES, display, locale, timezone), storage free, app storage sizes, power (battery level/charging/temperature, power save, doze, thermal status and headroom, battery-optimization exemption, background restriction, app standby bucket, data saver), network (transport, validated, metered, VPN, roaming, bandwidth, airplane, proxy in use), permissions (notifications, POST_NOTIFICATIONS, exact alarms, full-screen intent, overlay, audio/camera/contacts/Bluetooth), notification channel states, memory (system RAM, Java/native heap, PSS breakdown, RSS/threads, process disk IO counters, open file descriptors), messenger state (accounts count, push type/status, connection state per account, push registered) | each item says "needs API N" when the device is older |
| `exit-history.txt` | `ActivityManager.getHistoricalProcessExitReasons`: reason, importance, PSS/RSS, status, system description, time; then `getHistoricalProcessStartReasons`: start type, reason, component, launch-relative timings | exits 11+, starts 15+ |
| `crashes/crash_<ms>.txt` | Own uncaught-exception records (also in release): time, thread, uptime, heap, stack traces with causes | all |
| `traces/` | ANR traces (text) and native crash tombstones (`.tombstone.pb`, protobuf; read with `tombstone_symbolize`/`stack` or `adb`-style tools) of the latest 4 problems, 512 KB each | 11+ |
| `logs/` | Nanogram log files of the last 3 days (7 with verbose logs), tail of 8 MB per file, 24 MB total | all |
| `voip/` | Call logs (tgcalls) of the last 3 days, 8 MB total | all |

Not collected: message text, contacts, phone numbers, account/session data, keys, push tokens, databases, media,
heap dumps, TL request dumps, chat names and ids (per-chat notification channels are only counted). Exception messages
and the system's exit descriptions are copied as written, so they can contain identifiers the app put there; native
tombstones contain register and stack snippets. The zip is only created when the tester asks, and stays on the device
until they send it.

## Logs in release builds

Upstream writes logs only when `BuildVars.LOGS_ENABLED` (debug builds or the debug toggle). Nanogram keeps a small
always-on log:

- `FileLog.e/w/fatal` and the new `FileLog.i` write in release builds; `FileLog.d` and everything guarded by
  `BuildVars.LOGS_ENABLED` stay verbose-only.
- Cap: 5 MB total, 3 days, 30 files; 1 MB per file in quiet mode (a new `_r` file continues); pruned at each start
  and on every roll. A burst limit of 40 lines per 10 s keeps an error loop from costing CPU or disk (a "dropped N
  lines" note is written afterwards). Writes go through FileLog's existing queue thread.
- Key events (`FileLog.i`): process start (version, device, pid), app foreground/background, push received (delay
  since the server sent it), call state changes, connection state changes, memory trim levels/low memory, and on the
  next start every problem exit and Java crash of the previous run.
- Privacy: the "no message text, phone numbers or ids" promise holds for the quiet log only. Upstream `FileLog.d` lines can
  carry transcripts, phone numbers, push tokens and ids, so while verbose logs are on, and for 3 days after, the README and
  the screen warn to send the zip only to the developer. Those 3 days also lift the 5 MB / 30 file caps (age cap only).
- Quiet log: key events (`FileLog.i`) have their own burst budget; a process restart reopens the newest small file of the day.
- Sending to a chat: `AndroidUtilities.isInternalUri` allows `cache/nanogram_diagnostics/Nanogram-(report|diagnostics)-*.zip`, like
  upstream's `voip_logs` exception. A new export removes only zips older than an hour.
- Verbose logs: Debug menu > Diagnostics (developer) > Verbose logs (same preference as upstream's `logsEnabled`, so the debug
  menu switch is the same switch). Heap dumps (`FileLog.dumpMemory`) happen only in verbose mode.

## Reading it

1. `device-info.txt` first: Android version, vendor, power and background limits (battery optimization, standby
   bucket, data saver, full-screen intent) explain most "no notifications / late messages / call does not ring" reports.
2. `exit-history.txt`: the newest `[problem]` lines. `CRASH` has a matching file in `crashes/`; `ANR` has a text
   trace in `traces/`; `CRASH_NATIVE` a tombstone; `LOW_MEMORY` / `SIGNALED` while the importance was above CACHED is a
   system kill; compare PSS/RSS with the memory section.
3. `logs/*.txt`: grep for `previous process exit`, `previous Java crash`, `push received`, `connection state`
   (1 connecting, 2 waiting for network, 3 connected, 4 connecting to proxy, 5 updating), `call state changed`.
4. Native crashes: `traces/*.tombstone.pb`; symbolicate against the build's unstripped libraries.

## Code

`org.telegram.messenger.nanogram`: `NanogramLog` (policy, pruning, verbose switch), `NanogramCrashHandler`,
`NanogramExitInfo`, `NanogramSystemInfo`, `NanogramDiagnostics` (start hook, zip, problem list);
`org.telegram.ui.NanogramDiagnosticsActivity` (screen). Hooks in upstream files: `FileLog` (quiet mode, `i`),
`ApplicationLoader`, `PushListenerController`, `VoIPService`, `ConnectionsManager`, `SettingsActivity`,
`DebugSettingsActivity`, `provider_paths.xml` (`cache-path` for the zip).
