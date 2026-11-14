# Releasing Nanogram for Android

Android builds are distributed as APKs through GitHub releases. Friends install the APK once and the app updates itself.

| Where | What | Visibility |
|---|---|---|
| `klaipher/nanogram-android-releases` | release `android-v<versionName>-<build>` with the arm64 APK and `update.json`; short notes | public |
| `klaipher/nanogram-android` | same tag, `mapping.txt`, `native-symbols.zip`, detailed notes from `CHANGELOG.md` | private |

## Cut a release

1. Update `CHANGELOG.md` (`## Unreleased` or a `## <versionName> (<build>)` section): what changed and how, with commits.
2. Write the public notes in a file (a few lines, see rules below).
3. Commit and push; `ng release` refuses a dirty tree or an unpushed commit.
4. `Tools/dev/ng release --notes notes-public.md --dry-run` first: it builds (unless `--skip-build --build-number N`),
   writes `build-output/<build>/release/update.json` and prints the `gh` commands without running them.
5. `Tools/dev/ng release --notes notes-public.md` creates the private release first, then the public one (marked latest).
   Friends' apps see it at `.../releases/latest/download/update.json` within 6 hours or through
   Settings > Check for updates.

`update.json`: `versionCode`, `versionName`, `buildNumber`, `apkUrl`, `apkSha256`, `apkSize`, `notes`, `minSdk`, `publishedAt`.
`versionCode` is read from the built APK (`aapt2 dump badging`) and checked against the build-number formula
(minutes since 2020-01-01 x 10 + 9). The app only accepts APK URLs under the public releases repo, verifies the
SHA-256 and that the APK is signed with the same certificate as the installed app.

## Notes rules

- Public notes (release body and `update.json` notes): short and high level, which areas improved ("Faster chat list",
  "Calls: better picture quality", "Fixed a crash in X"); never how (no techniques, APIs, files, flags, commit lists).
- Private notes: detailed, in `CHANGELOG.md` (newest first: version, build number, date, commit; what and how).

## First install and updates on a device

- Install the APK ("install unknown apps" must be allowed for the browser or file manager).
- Updates download inside the app. On the first update Android asks to allow Nanogram to install apps; the app shows
  the system settings page and the next tap installs. Debug builds do not check unless the toggle in the debug menu is on.
- Telegram's own update call (`help.getAppUpdate`) is never used by Nanogram.
