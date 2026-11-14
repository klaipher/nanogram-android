# Performance notes

## ui

Measure on the emulator (or a device) with `adb shell dumpsys gfxinfo org.klaipher.nanogram reset`, then `framestats`,
and Perfetto/`atrace -t 10 gfx view sched` for wakeups.

| Id | Fix | How to measure |
|---|---|---|
| UI-1 | Choreographer60FpsContent posts its vsync callback only while something is subscribed | Open a chat with an animated sticker, leave it, sit on a static screen. Perfetto: `Choreographer#doFrame` slices per second should drop to about 0 on the idle list (before: 60+). |
| UI-3 | No max refresh rate request on the main window after a configuration change, nor on long-lived PiP windows | Rotate the device, then `adb shell dumpsys display` / `dumpsys SurfaceFlinger` (active mode, `preferredRefreshRate` of the window); with the overlay in Developer options the rate should fall at rest. Device dependent. |
| UI-5 | Pre-Android-10 cursor blink only while the field is focused | API 24-28 emulator: open a chat, keyboard closed; `gfxinfo` frames drawn per second while idle should be 0 (before: 2). |
| UI-6 | `apply()` instead of `commit()` for keyboard height and drafts | StrictMode disk-write violations / Perfetto main-thread `fsync` slices when opening the keyboard or sending a message. |
| UI-8 | Wordmark baseline computed once | Allocation profiler on the chat list: no `Paint$FontMetrics` allocations while scrolling. |
| BATTERY-7 | Light sensor released while paused (Automatic night mode) | Settings > Appearance > Auto-night > Automatic; background the app; `adb shell dumpsys sensorservice` should list no active connection for the light sensor from this package. |
