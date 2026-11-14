# Building Nanogram for Android

Everything goes through `Tools/dev/ng`. First time on a Mac:

```sh
brew install openjdk@21 ccache rustup meson nasm pkg-config
brew install --cask android-commandlinetools
Tools/dev/ng setup       # pinned SDK/NDK/CMake, local.properties, hooks, shallow submodules
Tools/dev/ng prebuild    # native static libraries into ~/.cache/nanogram-android/prebuild/<key>
Tools/dev/ng build       # arm64 debug APK into build-output/<build>/
Tools/dev/ng install     # adb install -r
```

Release: `Tools/dev/ng signing init` once (keystore in `~/.config/nanogram-android/signing`, back it up),
then `Tools/dev/ng build release`. Push notifications need our Firebase config at
`~/.config/nanogram-android/google-services.json`.

## What makes it fast and small

| Technique | Where | Effect |
|---|---|---|
| Pinned, current toolchain | `Tools/dev/toolchain.properties` (read by ng and Gradle) | JDK 21, Gradle 9.9, AGP 9.5, Kotlin 2.4, NDK r30, CMake 4.1, build-tools 37 |
| arm64-v8a only | root `build.gradle` `nanogramAbiFilters` | one native build instead of four; `-PnanogramAbis=all` for all ABIs |
| ccache for every native compile, including the FFmpeg, libvpx and dav1d prebuilds | `jni/CMakeLists.txt` `NANOGRAM_CCACHE`; `prebuild/common.sh` `CCACHE_LAUNCHER` (make-time `CC=`, meson cross-file list), cap 8 GB | clean native rebuilds become cache hits; shared across worktrees (`CCACHE_BASEDIR`) |
| Shared ThinLTO cache for release links | `NANOGRAM_THINLTO_CACHE_DIR` = `~/.cache/nanogram-android/thinlto`, `jni/CMakeLists.txt` | the lld cache (2 GB, self-pruning) survives new `.cxx/<hash>` trees, prebuild-key changes and worktrees |
| One Rust 1.99.1 for tlottie and wallet-engine, persistent wallet target dir | `toolchain.properties` `rust=`, `ng prebuild` | same compiler as iOS/macOS; cargo skips unchanged wallet crates (`~/.cache/nanogram-android/prebuild-work/<worktree hash>`) |
| Host tool versions in the prebuild key | `ng` `build_tool_versions` | a Homebrew or `rustup` upgrade rebuilds the prebuilts instead of reusing stale archives; the values are recorded in the cache entry's `COMPLETE` |
| Wallet engine release profile: `opt-level = 3`, fat LTO | `jni/gram-wallet/Cargo.toml` | speed over size for PBKDF2/mnemonic work. Verify: `CARGO_TERM_VERBOSE=true Tools/dev/ng prebuild --force` logs `-C opt-level=3 -C lto=fat`; `$BUILD_DIR/bindings` unchanged; `llvm-nm libwallet_engine.a \| grep eh_personality` shows no unrenamed `rust_eh_personality` (only `wallet_engine_rust_eh_personality`, or nothing if LTO dropped it, both pass); libtmessages links and `llvm-nm -D libtmessages.49.so` shows no `rust_eh_personality` conflict with tlottie |
| Packed dynamic relocations (APS2) for libtmessages and libtwidget (release only, not Debug) | `jni/CMakeLists.txt` `-Wl,--pack-dyn-relocs=android` | smaller `.rela.dyn` in the page-aligned libraries mapped from the APK. Verify: `llvm-readelf -S` shows `.rela.dyn` with type `ANDROID_RELA` and a much smaller size than before (lld keeps the section name), `llvm-readelf -d` shows `ANDROID_RELA`/`ANDROID_RELASZ` instead of `RELA`/`RELASZ`; APK size drops; cold start on an API 24 emulator |
| Debug native without LTO | `jni/CMakeLists.txt` | fast links in debug; release uses ThinLTO (see the shared ThinLTO cache above) |
| Native prebuilts from source, cached by content | `ng prebuild` | rebuilt with our NDK/flags only when a submodule, script or the NDK changes; no new binaries in git |
| Configuration cache (parallel) + build cache | `gradle.properties` | no-op builds in seconds; tasks restored from cache after `clean` |
| Only the shipped modules | `settings.gradle` | 11 of 40 androidx/media modules; upstream's Huawei/HockeyApp/Standalone/test apps only with `-Pnanogram.allModules=true` |
| Non-transitive R, no Jetifier | `gradle.properties` | smaller R classes, no bytecode rewriting of dependencies |
| Uncompressed, 16 KB-aligned native libraries | app `packaging.jniLibs`, `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES` | no extracted copy on the device, ready for 16 KB-page devices |
| Shallow submodules | `ng setup` | pinned commits only |

## Native speed flags

Hot code (tgnet, sqlite, image/gif/lottie glue, opus, FFmpeg, libvpx) is built for speed (-O2, no `--enable-small`);
cold code (tgcalls_tp/usrsctp) stays -Os. libvpx arm64 adds dotprod/i8mm kernels chosen at run time. Compare before and
after with `ng build release` on both commits: `ls -l` of `libtmessages.49.so` and APK size, and
`unzip -l` for per-library sizes. Quick speed check on a device or emulator: `adb shell am start -W` cold start and
`dumpsys gfxinfo` jank on a long chat list (sqlite), voice-message playback CPU (`top -H`), a VP9 .webm sticker
(libvpx). Verify with `compile_commands.json` that tgnet/sqlite sources, `jni.c` and `image.cpp` end with `-O2` (the last `-O` on
the line wins; a trailing `-Os` or `-O3` means an interface option of a linked target overrode it).
## Profiles (ART baseline profile, startup profile)

Sideloaded APKs get no Play cloud profile, so without help ART compiles nothing ahead of time and picks methods only
from what it sees at runtime. The APK therefore carries its own profile and hands it to ART itself. This does not make
first runs fast: ART compiles the profile at the next background dexopt (device idle and charging), so the gain is a
better-chosen set of methods at that first dexopt. Install-time compilation would need a `.dm` dex-metadata file in the
install session (not done: the in-app updater and `adb install` ship only the APK). To test the effect right away:
`adb shell cmd package compile -m speed-profile -f org.klaipher.nanogram`.

| Piece | Where | Effect |
|---|---|---|
| Profile in the APK | AGP packs `assets/dexopt/baseline.prof` (+ `.profm`) from `TMessagesProj_App/src/main/baseline-prof.txt` and `src/main/generated/baselineProfiles/baseline-prof.txt` in release builds; R8 rewrites it to the obfuscated names | known-hot classes and methods are compiled ahead of time at the next background dexopt |
| `androidx.profileinstaller` 1.4.1 | app dependency | writes the profile to ART's profile location on first launch (API 24+), no Play Store needed |
| Startup profile | `src/main/generated/baselineProfiles/startup-prof.txt` (logged-out start) plus hand-written `src/main/baselineProfiles/startup-prof.txt` (logged-in start: LaunchActivity, MainTabsActivity, DialogsActivity, chat list, storage), `dexLayoutOptimization = true` | R8 groups startup classes at the front of the dex files: fewer page faults on cold start. Only files named `startup-prof.txt` feed it; the S flags in `baseline-prof.txt` do not |
| Hand-written rules | `TMessagesProj_App/src/main/baseline-prof.txt` (wildcards) | post-login paths the emulator cannot reach: chat list, open chat, images, storage, connection callbacks, emoji and theme drawing |
| Generator | `:baselineprofile` (macrobenchmark `BaselineProfileRule`) | records the logged-out start: cold start, intro swipes, Start Messaging, permission dialogs ("Don't allow"), phone login, country picker, diagnostics screen |

Note: the generated profiles (`src/main/generated/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`) are not committed yet, so only the hand-written `baseline-prof.txt` and the logged-in `src/main/baselineProfiles/startup-prof.txt` are in effect until `Tools/dev/ng profile` is run on `nanogram-api36-16k` and its output committed (AND-OPT-1).

Regenerate with the AVD `nanogram-api36-16k` running (API 36, not logged in):

```sh
Tools/dev/ng profile            # -> TMessagesProj_App/src/main/generated/baselineProfiles/, prints the line counts
git add TMessagesProj_App/src/main/generated   # review the diff first, then commit
```

`ng profile` builds the app's `nonMinifiedRelease` variant (arm64, release native code), installs it on the emulator,
runs the generator and merges the result into `src/main`. The `:baselineprofile` module is only part of the build with
`-Pnanogram.baselineProfile=true`, which `ng profile` sets, so normal builds do not include it. While it is
set only the `afat` release family exists; without it the profile-only `nonMinifiedRelease` and `benchmarkRelease`
variants are disabled. Regenerate after large UI or startup changes and at upstream release syncs
(class and method names move); the committed profile keeps working in between, stale rules are ignored. Check a release
APK with `unzip -l <apk> | grep dexopt` (expect `baseline.prof` and `baseline.profm`).

## Measurements

Apple M-series, 10 cores; arm64 debug unless noted. Recorded by `ng metrics` (`~/.cache/nanogram-android/logs/metrics.tsv`).

| Build | Time | APK | Notes |
|---|---|---|---|
| Upstream 13.2.2, AGP 8.13, NDK 27 (cold) | 306 s | 72 MB | native full LTO, compressed native libs |
| Nanogram, first build (empty ccache) | 323 s | 101 MB | uncompressed native libs, no LTO in debug |
| Nanogram, no-op rebuild | 3 s | | configuration cache |
| Nanogram, `clean` rebuild, warm caches | 54 s | | 100 % ccache hits, 83 tasks from build cache |
| Nanogram on Gradle 9.9/AGP 9.5/NDK r30, native rebuilt (new abseil/BoringSSL) | 333 s | 98.7 MB | libtmessages 39 MB (upstream debug 43 MB) |
| Native prebuilts from source, arm64 (`ng prebuild`) | 282 s cold, 234 s after a pin change | 56 MB cache | upstream commits 204 MB of prebuilts for 4 ABIs |
| Release arm64 (R8 full mode, resource shrinking, full LTO, v1+v2+v3 signed) | 182 s; 100 s after a resource change | 57.5 MB | 16 KB-aligned, uncompressed native libs |

Disk: one native configuration tree (`TMessagesProj/.cxx/<type>/<hash>`) is about 2.5 GB; `ng prune` keeps only the
newest per build type. ccache is capped at 8 GB (0.9 GB after a full arm64 build).

## Measuring the build-system changes

- Reproducibility gate (ccache must not change bytes): `NANOGRAM_CCACHE= Tools/dev/ng prebuild --force` (an explicitly empty value turns ccache off in ng; `CCACHE_DISABLE=1` also works), save
  `shasum -a 256 ~/.cache/nanogram-android/prebuild/<key>/lib/arm64-v8a/*.a`; then `ccache -z` and run
  `Tools/dev/ng prebuild --force` twice with ccache. The sha lists must be identical; the second ccache run should show
  high hit rates in `ccache -s` and a much shorter time in `Tools/dev/ng metrics`.
- Rust: `strings <lib>.a | grep 'rustc version'` on libtlottie.a and libwallet_engine.a shows 1.99.1; after touching a
  prebuild script, the wallet crates log as `Fresh` in the prebuild log.
- ThinLTO cache: `ng build release` twice with `rm -rf TMessagesProj/.cxx/RelWithDebInfo*` in between; the second link
  of libtmessages is much shorter and `ls ~/.cache/nanogram-android/thinlto` holds entries.
- The prebuild runs without `CCACHE_BASEDIR` (ccache would rewrite absolute paths and change `__FILE__` in FFmpeg objects), so
  ccache output is byte-identical to a build without it; hits are shared within one worktree path, not across worktrees.
  `ng prebuild` also installs the pinned Rust toolchain and the Android targets for the selected ABIs up front.
- Running a prebuild script by hand now defaults to the NDK and API pinned in `toolchain.properties` (env still wins).
