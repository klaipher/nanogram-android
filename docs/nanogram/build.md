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
(libvpx). Verify with `compile_commands.json` that tgnet/sqlite sources get `-O2`.

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
