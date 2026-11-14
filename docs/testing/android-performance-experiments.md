# A/B test of the Nanogram performance experiments (Pixel phones)

For testers with a **Pixel** (or another Android phone). No computer, no developer tools. Plan about 2 hours in
total, spread over a day or two. Each experiment is tested twice: first with its switch **off** (A), then **on** (B).
Please say which phone you used: Pixels differ a lot (Pixel 6 and 7 Tensor, Pixel 8 and 9 newer Tensor, Pixel 10).

Six experiments, each with its own switch:

| Switch | What it changes | What you test |
|---|---|---|
| Adaptive refresh rate | Android 15 or newer: the screen may go below its top refresh rate when little moves | Test 1: scrolling |
| System call integration | Android 14 or newer: calls are also registered with the phone's call system | Test 2: calls (extra checks) |
| Camera2 capture | Stories and the chat camera use the newer camera interface | Test 3: camera (extra checks) |
| Early image upload to the GPU | Pictures are prepared for the screen while they load | Test 4: photos |
| Lighter decoding for small GIFs | Small GIFs decode faster | Test 5: GIFs |
| Database tuning | Bigger cache for the local message database | Test 6: big chats and search |

If a switch says "not available on this phone", your Android is too old for it. Skip that test and say so.

## 0. Before you start

1. **Install the newest Nanogram** from the GitHub release page the project owner sent you (open the link in
   Chrome, download the `.apk`, open it, allow "install unknown apps" for Chrome if asked). It updates the
   existing app and keeps your chats.
2. **Open the switches:** Nanogram > Settings (profile tab) > scroll to the very bottom > tap the **version line**
   (for example "Nanogram 13.2.2 (...)") **twice**. A **Debug Menu** list opens. Scroll down to the section
   **Performance experiments** (below "Contacts and chats" and "Data"). Open it once before your first run: the
   frame numbers are only collected after that.
3. In that section you see **Device:** (for example "Google Pixel 8, SoC Tensor G3, Android 15"). Check that
   it is your phone. Below it every experiment has a switch (all off at first), a short description, and grey
   numbers measured since that switch was last changed.
4. Charge above 60% and do not charge during a test. Turn off Battery Saver and Adaptive Battery tricks you added
   yourself; keep Wi-Fi on. Keep the screen brightness the same for A and B (set it by hand, not automatic).
5. Let the phone cool down before each run (not warm at the top back). Close other apps.

## How to switch between A and B

1. Debug Menu > Performance experiments > flip **one** switch. Flipping a switch clears the numbers of that experiment, so the zip of run B holds only B. Only one switch is on at a time.
2. Under the switch it says how it applies. When it says "Restart the app": open the recent-apps screen, swipe
   Nanogram away, open it again. "Applies to the next call": nothing to restart. "Close and reopen the camera": leave the
   camera screen and open it again.
3. Open the Debug Menu again and check the switch shows what you want.

Always do **A (off) first, then B (on)**, back to back, same content and same place in the chat. Write down your
result of A before you start B.

## Test 1: scrolling (switch "Adaptive refresh rate", Android 15 or newer)

Content: a busy chat or channel with photos, stickers, GIFs and video stickers (thousands of messages). Your
phone must be set to smooth display (Settings > Display > Smooth display ON), otherwise the experiment has nothing to adjust.

Script (about 5 minutes per run):
1. Open the chat at the newest message.
2. Scroll up steadily for 2 minutes: slowly, then flick fast.
3. Open the sticker panel, scroll two sticker packs, leave it open on an animated sticker for 30 seconds without touching.
4. Open a story and swipe through 5 stories.

Watch for: stutter or lag when a fling starts or ends, animations that look "steppy" (lower fps than before),
the picture feeling less smooth after the screen has been still for a moment.
The numbers `frameTime` (time to draw one frame) and `frameInterval` (time between frames: 8.3 ms means 120 Hz,
16.7 ms means 60 Hz) fill while you do this.

## Test 2: calls (switch "System call integration", Android 14 or newer)

Needs a second person (or a second phone with Nanogram) for real calls. If you have nobody, skip it and say so.

Script, with the switch off (A) and again on (B), at least 3 calls each:
1. Outgoing call to your friend. Answer on their side. Talk 1 minute. Hang up from your side.
2. Incoming call from your friend with the screen on: answer from the notification. Talk 1 minute. Hang up from their side.
3. Incoming call with the phone **locked**: answer from the lock screen. Check the sound comes out of the right place
   (earpiece or speaker as before).

Extra checks (write down for A and B: works / wrong / not tested):
- **Bluetooth headset** (or earbuds): call goes to the headset; the headset button answers and hangs up; sound switches back to the phone when you disconnect.
- **Lock screen:** ringing shows, answering works, no second call screen pops up.
- **Another app playing music** while a call comes in: the music pauses or lowers, comes back after the call.
- **Do Not Disturb** with calls allowed for your friend: it still rings.
- Your **phone dialer's recent calls** must not show a Nanogram call with a name or number. (It may appear as a generic entry in B; say what you see.)
- **Missed call:** let one ring out. One missed-call notification, no stuck "ongoing call".

The numbers `telecomRegister` (time to register the call, only in B) and `callSetup` (time from key exchange to the call being connected) fill after each call.

## Test 3: camera (switch "Camera2 capture")

Script (about 10 minutes per run), switch off (A) then on (B), reopen the camera after flipping:
1. Open the chat camera. Take a photo. Take a 10-second video. Record a 20-second **round video**.
2. Open the story camera. Take a photo. Record a 1-minute video (watch the framing: does the picture shift or zoom when recording starts?).
3. Flip front/back camera 5 times. Turn the flash on and off, take one photo with flash.
4. Pinch-zoom while recording. Tap to focus.
5. Open and close the camera 10 times in a row (watch for a black screen or a freeze).

Write down for A and B: first picture appears fast / slow / black screen; photo sharp and right colours; video sound in sync; framing the same
before and during recording; flash works; front/back flip works; the camera app of the phone still opens afterwards.
The QR scanner always keeps the old camera; you do not need to test it. The number `cameraFirstFrame` (time from opening to the first picture) fills each time you open the camera.

## Test 4: photos (switch "Early image upload to the GPU")

Script (about 5 minutes per run):
1. Open a chat with many photos (a group with photo posts), or Shared Media > Photos of such a chat.
2. Scroll quickly up for 2 minutes, then back down.
3. Open the attach menu (paperclip) > Gallery and scroll through your whole gallery grid.
4. Open 5 photos full screen, swipe between them (the full-screen viewer is not measured by `frameTime`; judge it by eye).

Watch for: grey or empty cells while scrolling, a short freeze when a new row of pictures appears.
The number `imageUpload` shows only when the switch is on (time spent preparing pictures for the screen). It is
normal that it says "no samples" in A. The main comparison for this test is smoothness and the `frameTime` line of the first experiment, which is also measured here.

## Test 5: GIFs (switch "Lighter decoding for small GIFs")

Script (about 3 minutes per run):
1. Open the GIF panel (smiley > GIF) and scroll through two pages of GIFs.
2. Open a chat with many GIFs (or save 10 GIFs to Saved Messages first) and scroll through them.
3. Watch a full-screen GIF for 30 seconds.

Watch for: GIFs that play choppy, edges of GIFs that look softer or blocky in B (compare the same GIF).
The number `gifFrameDecode` shows the time to decode one frame of clips up to 320 px (lower is better; it also includes small video stickers and emoji, which the switch does not change, so the gain shows smaller than it is).
The line must show n greater than 0, otherwise repeat the run.

## Test 6: big chats and search (switch "Database tuning")

This switch has no measured numbers.
1. Restart the app after flipping.
2. Close it fully and open it again: how long until the chat list is usable (count seconds, 3 times)?
3. Open your biggest chat and jump to the oldest message from the top bar or by scrolling for one minute.
4. Search in the chat list for a common word. Write down if results appear fast or after a pause.

## Battery and heat (every run)

For each experiment and state (A, B) do one 10-minute session that repeats that test's script:
- Write down the **battery % before and after** (whole percent: Settings > Battery).
- After the 10 minutes: is the phone **cool / warm / hot** at the top back?
- If Android shows an overheating warning: stop the run and say so.

## What to send

For each experiment, and for A and B separately, send the project owner (a message is enough):

- Phone model (from the Device line), Android version.
- **Smoothness 1 to 5** (5 = perfectly smooth).
- **Stutters:** yes or no (and where).
- **Warm / hot** after the 10 minutes: cool, warm or hot.
- **Battery drop** over the 10 minutes (e.g. 4%).
- The extra checks for calls and camera (works / wrong / not tested).
- The **diagnostics zip**, made right after finishing that run, **before** you flip the switch or restart: Settings > Help >
  **Send diagnostics** (or Debug Menu > Nanogram diagnostics) > **Share diagnostics**. It contains a file
  `performance-experiments.txt` with the state of every switch and the numbers since that switch last changed: count, average and
  longest time per frame, per GIF frame, per camera start, per call setup. Only durations and the phone model are in that file. The zip also holds
  the normal diagnostic logs (device state, error log), so send it only if you are fine with that.

The same numbers show in grey under the switches in the Debug Menu (close and reopen the screen to refresh). Numbers reset every time the app restarts or you flip that experiment's switch, so take the zip before either.

## The decision rule (project owner)

An experiment is turned on by default for a device class (this Pixel generation) only if, over the reports of that
phone (at least 3 testers or 3 runs on at least 2 phone models):

1. median smoothness in B is not lower than in A, and no more testers report stutter in B than in A;
2. its measured time in the export (`frameTime` and slow-frame counts for refresh rate, `cameraFirstFrame`, `callSetup`, `imageUpload`
   not worse than the cost it saves, `gifFrameDecode`) is not worse in B: average at most 5% higher and longest at most 25% higher,
   and for the experiments meant to be faster (images, GIFs, camera) at least 10% lower on average. For refresh rate `frameInterval` is only
   an observation: B is expected to be higher because content may draw below the top rate;
3. median battery drop in B is at most 1 percentage point higher than in A, and no tester reports hot in B while cool or warm in A;
4. for calls and camera: every extra check works in B. One wrong check (no sound, black camera, a call that cannot be hung up, framing that changes)
   keeps the experiment off.

"Not worse on the device" is the bar for adaptive refresh rate and database tuning (they save battery or time in
ways that are hard to see in one test): they go on if smoothness and battery are not worse. If a rule fails, the
experiment stays off and its switch is removed in a later build. With too few reports the test repeats in the next build.
