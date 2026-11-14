# AGENTS.md

Guidance for coding agents working in Nanogram for Android (fork of Telegram for Android).

## Hard rules

- Marketing version follows upstream (`APP_VERSION_NAME`); only the build number changes. Sync upstream only at
  released versions ("update to X.Y.Z (N)" commits on DrKLO/Telegram), never mid-development master, and only when the
  owner asks. The `upstream` remote has push disabled; never push upstream history to our repos.
- A fix in shared code lands on every app that has it (td, tgcalls, webrtc, tlottie, libprisma, wallet-engine, ...). Shared
  native code has ONE source (an official pin or ONE klaipher fork branch) listed in `shared/shared-libraries.json`
  (submodule `shared/` = klaipher/nanogram-deps, the same commit as Nanogram iOS and macOS). A fix in shared native code
  lands in that single source, then all three pins (iOS, Mac, Android) move the same day. No vendored copies and no
  per-platform branches; tgcalls is the fork branch `nanogram-shared` for all three apps. The pre-push hook runs
  `python3 shared/check_shared.py --side android`.
- Never `--no-verify`. Never read or print `~/.config/telegram-ios`, `~/.config/nanogram-android/signing`, keystores or
  passwords; `Tools/dev/ng` reads them and passes them to Gradle through the environment. No scheduled jobs.
- Agents do not build or launch the app (`ng build/check`, `./gradlew assemble*`); one combined build at the end of a
  round. Focused JVM unit tests are fine.
- Commit with explicit paths. Never stage submodule pointer changes by accident (`git add -A` / `commit -a`).
- Builds need at least 25 GB free; `ng prune` first.

## Development pipeline (same as iOS and macOS)

1. Research and review in parallel lenses, with evidence. 2. Adversarial verification of every finding.
3. Fix confirmed findings on branches, tests first. 4. No builds by agents. 5. One combined build at the end.
6. Fix what the build shows. 7. Push through the hooks (`Tools/git-hooks/pre-push` runs `ng check`).
8. Report: findings with evidence, decisions with trade-offs, open questions.

## Change records

Every change that alters behaviour updates `CHANGELOG.md` (`## Unreleased`: what and how, with commit) and, when it
changes how we differ from upstream, `NANOGRAM-CHANGES.md`. Public release notes are short and say which part got
better, never how.

## Commands

Everything goes through `Tools/dev/ng` (`ng --help`): `setup`, `doctor`, `prebuild`, `build [debug|release]`,
`install`, `signing init`, `check`, `profile`, `prune`, `metrics`. Build design and measurements:
[docs/nanogram/build.md](docs/nanogram/build.md).

## Layout

- `TMessagesProj/` — the app library: Java UI and logic (`src/main/java/org/telegram`), native code (`jni/`: tgnet,
  voip/tgcalls, tlottie, sqlite, wallet; prebuilt static libraries are built by `ng prebuild` from the scripts in `jni/prebuild` into `~/.cache/nanogram-android/prebuild/<key>`; nothing is committed, and CMake fails without `nanogramPrebuildDir`). WebRTC (C++ and `org.webrtc` Java) is the
  submodule `jni/voip/webrtc` (klaipher/webrtc `nanogram`, shared with iOS/macOS); its nested abseil, libsrtp and crc32c
  are the only copies of those libraries.
- `TMessagesProj_App/` — the application module we ship (`afat` flavor, arm64 by default).
- `TMessagesProj_Modules/media` — androidx/media fork (submodule).
- `buildSrc/` — Gradle plugins that generate strings, TL schema code and assets.
- Upstream's Huawei, HockeyApp, Standalone and instrumentation-test modules stay in the tree but are not part of the
  default build (`-Pnanogram.allModules=true` includes them).

## Code style

- Follow the surrounding upstream style; keep diffs against upstream small and grouped so release syncs stay easy.
- No comments except task markers, or a short why where the code cannot say it.
