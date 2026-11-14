# Building Nanogram for Android

Everything goes through `Tools/dev/ng`. First time on a Mac:

```sh
brew install openjdk@21 ccache rustup meson nasm pkg-config
brew install --cask android-commandlinetools
rustup toolchain install 1.99.1 --profile minimal
rustup target add --toolchain 1.99.1 aarch64-linux-android
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
| Pinned, current toolchain | `Tools/dev/toolchain.properties` (read by ng and Gradle) | JDK 21, Gradle 9.9, AGP 9.5, Kotlin 2.4, NDK r30, CMake 4.1, build-tools 37, Rust 1.99.1 |
| arm64-v8a only | root `build.gradle` `nanogramAbiFilters` | one native build instead of four; `-PnanogramAbis=all` for all ABIs |
| ccache for every native compile | `jni/CMakeLists.txt` `NANOGRAM_CCACHE`, cap 8 GB | clean native rebuilds become cache hits; shared across worktrees (`CCACHE_BASEDIR`) |
| Debug native without LTO | `jni/CMakeLists.txt` | fast links in debug; release keeps upstream's full LTO |
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
