# Nanogram shared libraries: one source, three apps

Execution plan, 2026-11-14. This file is committed in klaipher/nanogram (iOS), klaipher/nanogram-macos and klaipher/nanogram-android under `docs/shared-libraries-plan.md`.

## 1. Rule (owner, 2026-11-14)

Code is never duplicated across Nanogram iOS, macOS and Android, and submodules never get separate per-platform branches unless strictly needed.

- **One source per library.** Each shared library comes from exactly one place: either a klaipher fork with ONE branch, or one pinned official release.
- **One version.** All three apps use that source at the same commit, so a fix lands once and reaches every app.
- **Nothing is lost.** Every local change in any copy (vendored file edits, local patches, fork commits) is carried into the single source before the copy is deleted.

## 2. Baseline refs

| Repo | Ref | Commit |
|---|---|---|
| iOS klaipher/nanogram | origin/main | 466d8350 |
| Mac klaipher/nanogram-macos | origin/nanogram/integration | c489f3fd (core pin = iOS 466d8350) |
| Android klaipher/nanogram-android | main | **fdd4407** (FFmpeg fork already merged, d09e500) |

## 3. Principles for every step

1. **Consolidation, not upstream intake.** The target version is the newest version already shipped by one of our three apps. Newer upstream code waits for the Telegram release sync (owner rule "upstream releases only") unless the owner opts in (section 9).
2. **Fork shape.** New forks get exactly one branch, named `nanogram`, based on an official tag. klaipher/tgcalls keeps `nanogram-shared` because three repos and the tooling already use that name.
   - Each carried change is its own commit, and the message names its source (`repo@commit`, VENDOR.md edit number, or Android patch name).
   - Each fork branch keeps `NANOGRAM-CHANGES.md` and `CHANGELOG.md`.
   - Forks of public repos are public: records there say what changed, not internal details.
3. **No-loss proof before any delete.** For every copy being removed:
   - write a ledger entry: copy path, pristine base, and the diff of the copy against that base (file plus what the change does);
   - prove that each hunk is in the new source: blob identity, or `git apply --reverse --check` on the fork tree;
   - or list the hunk as intentionally dropped, with the reason.
   Ledgers live in `klaipher/nanogram-deps/ledger/<component>.md`.
4. **Never orphan a historical gitlink.** Before deleting a fork branch, tag its tip as `archive/<branch>-<sha>`. Archive repos; never delete them.
5. **Parallel lanes do not share files.**
   - Fork-side work runs in parallel, one lane per library.
   - App-repo edits go through ONE integrator lane per repo per wave, applied as one commit per library.
   - Hot files that only the integrator edits:
     - iOS: `.gitmodules`, `MODULE.bazel`, `docs/mac-core-paths.txt`, the manifest, `CHANGELOG.md`, `NANOGRAM-CHANGES.md`, `AGENTS.md`.
     - Mac: `.gitmodules`, the core pin, `scripts/configure_frameworks.sh`, `Telegram.xcodeproj`.
     - Android: `.gitmodules`, `jni/CMakeLists.txt`, `jni/voip/CMakeLists.txt`, `CHANGELOG.md`, `NANOGRAM-CHANGES.md`.
6. **Builds.** Fixes are checked with tests while they are made. Each wave then ends with ONE combined build per platform (batch-validation rule): iOS Bazel app build, Mac app build, Android `ng check` (arm64, NDK r30), plus the runtime checks listed.
   - Android runtime checks run on the arm64 Android 16 emulator (16 KB pages), which commit 93d06ca already used.
7. **Mac core lag.** `docs/mac-core-paths.txt` has `lag commits=20 days=7 block-from=2026-11-18`. The Mac integrator bumps the core pin inside every wave, right after the iOS integrator's commit.
8. **Recursive clones.** Mac clones every iOS submodule because its setup runs `--init --recursive`. Every new submodule gets `shallow = true`, and test-only nested modules (test262 and similar) get `update = none`.
9. **Records.** In each repo, CHANGELOG.md and NANOGRAM-CHANGES.md are updated by the integrator in the same commit as the change.

## 4. Inventory and targets

Legend:
- **S**: git submodule.
- **V**: vendored files.
- **I**: Android in-tree copy.
- **T**: tarball, download, or clone in a build script.

| Component | iOS | Mac | Android | Target single source | Wave |
|---|---|---|---|---|---|
| FFmpeg | V ffmpeg-9.0.2 (fork minus 2 Android fixes) | from iOS tree | S klaipher/FFmpeg nanogram d84c50bc | klaipher/FFmpeg `nanogram` d84c50bc | W1 |
| boost-regex | V (= boost-1.92.0) | from iOS | S 7760ef2a | official boost-1.92.0 | W1 |
| WAMR | V 145 files (= WAMR-2.4.5) | not used | S 25bd7eb6 | official WAMR-2.4.5 (bytecodealliance URL) | W1 |
| ogg | V (= v1.3.6 + config_types.h) | from iOS | S be05b13e | official v1.3.6, overlay header in iOS | W1 |
| opusfile | V (= v0.12 + 3 include edits) | from iOS | S a55c164e | official v0.12, include mapping in BUILD | W1 |
| libwebp | V (= v1.6.0) | T own tarball | not used | official v1.6.0 submodule in iOS | W1 |
| libjpeg-turbo | http_file 3.2.1 | T 2.5 MB blob | not used | official 3.2.1 url + sha256 | W1 |
| OpenSSL | none | T openssl-3.5.9 (dead patch file) | none | official 3.5.9, Mac only by design | W1 |
| opus | T 1.6.1 tarball | iOS tarball | S v1.6.1 | official 1.6.1 (already single) | W0 manifest |
| libvpx / dav1d | S 6df3ec34 / 54706fc6 | from iOS | S same | official (already single); align dav1d URL | W1 |
| td / tde2e | S 0e88653d + 793 SharedHeaders | from iOS | S 0e88653d | official tdlib/td; delete SharedHeaders | W1 |
| tlottie | S 92df98dc | S 92df98dc | S klaipher 31f1b542 (ancestor) | official dkaraush v1.0.6 92df98dc | W1 |
| mtproto-engine | S da4b2170 | from iOS | n/a | official (already single) | none |
| flatc | iOS BUILD | committed binary | n/a | one flatbuffers tag | W1 |
| OpenH264 | V v2.7.0 + level 6.x + subcodec | T clone v2.7.0 | S v2.7.0 | klaipher/openh264 `nanogram` | W2 |
| rnnoise (xiph) | V 0.2 + 3 patches | from iOS | I voip/rnnoise (2018) | klaipher/rnnoise `nanogram` | W2 |
| libprisma + Syntaxer glue | V (+ stats) and Syntaxer.mm | own CodeSyntax/Syntaxer.mm (47 lines differ) | S + in-tree overrides | klaipher/libprisma `nanogram` (incl. apple/Syntaxer.mm) | W2 |
| BoringSSL | V 2ea3200c | (OpenSSL) | S 0.20261105.0 | official 0.20261105.0 | W2 |
| libyuv | V 0b8dd231 (1977) | tg_owt nested 04821d1e | S 28ce69c2 | official 0b8dd231 | W2 (Mac in W5) |
| SQLite/SQLCipher | V amalgamation 3.54.1/4.20.0 | from iOS | I 3.39.3 | klaipher/sqlcipher-amalgamation `nanogram` | W2 |
| wallet-engine | S i582 e59e0d89 | from iOS | S klaipher 5c65e47c (diverged 1/1) | klaipher/wallet-engine `nanogram` (owner decision) | W2 |
| Watch rlottie | V Samsung v0.2 + 17 | n/a | n/a | klaipher/rlottie `nanogram` (Option A) | W2 |
| quickjs | V quickjs-ng 0.17 + edits | nested, local pin b526a817 | nested ca3594da | klaipher/quickjs `nanogram` | W3 |
| widget-sandbox | V v0.5.0 subset | S 015392fd (Mac branch) | S c498cef | klaipher/widget-sandbox `nanogram` | W3 |
| twidget + gumbo | V b5878f9 + 91 edits; gumbo V + 13 | S cc044e7b (Mac branch) | S 4769d72 | klaipher/twidget `nanogram` | W3 |
| tgcalls | S nanogram-shared 9bfb76f8 | S same | S nanogram-android 545f5ae | klaipher/tgcalls `nanogram-shared` | W4 |
| tgcalls ObjC bridge | iOS Sources/ (3417 lines) | fork macos/ (2116 lines) | n/a | fork: ios/ + macos/ (merge needs owner go) | W4 |
| wasm core blob | genrule | n/a | I ReferenceCoreEmbedded.cpp (443 KB) | fork tools/build_wasm_core.sh | W4 |
| WebRTC C++ | S klaipher/webrtc 779c4aa7 | S tg_owt 991142f9 | I voip/webrtc 4267 files | klaipher/webrtc `nanogram` | W5 |
| WebRTC Java/JNI, jni_zero | in fork (unused) | n/a | I src/main/java/org/webrtc, voip/third_party/jni_zero | klaipher/webrtc `nanogram` | W5 |
| abseil | V Chromium snapshot (pre-2025) | tg_owt nested macosswift LTS 20240116 | S LTS 20260817 | official LTS 20260817.0 | W5 |
| libsrtp | V 2.2.0-pre | tg_owt nested cisco v2.5 | I 2.2.0-pre (21/51 differ) | decided by ledger (official tag or in webrtc fork) | W5 |
| crc32c | V | tg_owt nested 21fc8ef3 | I (14/25 differ) | decided by ledger | W5 |
| usrsctp | V | none | I (18/61 differ) | decided by ledger | W5 |
| libevent | V Chromium 1.4.15 | none | I (19/39 differ) | in klaipher/webrtc third_party/ | W5 |
| pffft, rnnoise-vad, fft4g | V | tg_owt in-tree | I (identical to iOS) | in klaipher/webrtc third_party/ | W5 |
| Mac copies of iOS modules | iOS modules | packages/{HLSPlayback,SwiftMath,VP9Hardware,RingBuffer,EDSunriseSet,Svg,QrStudioUI,SceneEffects}, submodules/Stripe | n/a | iOS sources referenced by the Mac | W7 |

**Platform-only (recorded in the manifest, no action):**
- Android: breakpad, tgnet, media fork (klaipher/media nanogram-android), jlatexmath.
- iOS: yasm, wpt, recaptcha, subcodec, XMLCoder, ZipArchive (minizip-ng), Swift2D, SwiftMath, CompanionRenderer third-party (cgltf, ltc, opensubdiv), lottie-ios, AsyncDisplayKit, libjxl (keeps its upstream-Bazel-file deletions and highway CMake hunk, documented in docs/vendored-upgrades.md).
- Mac: Sparkle, AppCenter, minizip (zlib minizip 1.x, a different library from minizip-ng), Zip.

## 5. Waves

Waves run in order. Inside a wave, fork lanes run in parallel, and integrators follow their dependencies (iOS, then Mac, with Android in parallel with iOS).

### W0: manifest, parity, rules, audit (no build or behaviour change)

**W0.1 Create klaipher/nanogram-deps** (single branch `main`, may be private). It holds the only copy of:
- `shared-libraries.json` (schema 1). For each library:
  - `name` and `kind` (`official` | `fork` | `platform-only`);
  - `url`, plus `branch` (forks) or `tag` (official);
  - `commit`, the full sha and the only pin;
  - `consumers.{ios,mac,android}`: path, how it is stored (`submodule` | `bazel-http` | `via-ios-core`), and a version probe;
  - `ledger`.
- `check_shared.py` (stdlib Python) with `--side ios|mac|android` and `--android-repo`. It fails when a consumer gitlink differs from `commit`, when a fork pin is not on the declared branch, or when a vendored copy exists for a library that is not platform-only.
- `parity.py` (moved from iOS/Mac), `feature-parity.md`, `development-pipeline.md`, and the cachegc policy.

**W0.2 iOS integrator.**
- Add the submodule `shared/` (shallow).
- Change parity.py first: it must accept `android=` on `pin` lines (today the option is silently ignored) and a `lib` directive (today it raises "unknown directive"). Only then edit `docs/mac-core-paths.txt`.
- `tools/dev/native/*` and `tools/dev/parity/parity.py` become thin wrappers around `shared/`; `tglint` pre-push calls `check_shared.py --side ios`.
- Tests: `tools/dev/tests/test_parity.py`, `tg parity`.

**W0.3 Mac integrator.**
- Bump the core pin to W0.2.
- Delete the Mac copies of parity.py, native tooling, feature-parity.md, development-pipeline.md and cache-policy.json. Wrappers call `submodules/telegram-ios/shared/`.
- Mac adds no submodule of its own, so only two gitlinks exist: iOS and Android.

**W0.4 Android integrator.**
- Add the submodule `shared/` at the iOS sha.
- `Tools/git-hooks/pre-push` runs `shared/check_shared.py --side android`.
- Docs:
  - AGENTS.md lines 10-11 and NANOGRAM-CHANGES.md line 7: tgcalls is branch `nanogram-shared` and the `tgcalls-port` wording goes.
  - Add the shared-code rule text to all three AGENTS.md files: "a fix in shared native code lands in the single source, then all three pins move the same day".
- Test: a deliberately skewed gitlink makes the hook fail.

**W0.5 Audit (read-only, parallel).** Write ledgers for what the research did not cover:
- abseil, libsrtp, crc32c, usrsctp, libevent, pffft, rnnoise-vad and fft4g: three-way pristine diffs (iOS third-party/webrtc/*, Android jni/third_party/* and voip/third_party/*, tg_owt src/third_party/*), using README.chromium revisions as bases;
- the Mac copies of iOS modules (93 byte-identical files plus drifted ones);
- Mac `submodules/CodeSyntax/.../Syntaxer.mm` against iOS `third-party/libprisma/Sources/Syntaxer.mm`.

### W1: mechanical swaps with no code changes

**W1.1 iOS integrator.** One commit per item. Each runs its blob-identity proof first, then deletes the copy, adds the submodule (shallow) and updates BUILD, the manifest and `docs/mac-core-paths.txt`.

1. **FFmpeg.** `git rm -r submodules/ffmpeg/Sources/FFMpeg/ffmpeg-9.0.2`, then add the submodule `-b nanogram` at the same path, pinned at d84c50bc.
   - No script change is needed: the BUILD glob excludes dotfiles, FFMPEG_VERSION stays 9.0.2, Mac `build.sh` autodetects the directory, and the RELEASE file is present in the fork.
   - Add a `tg doctor` check for an empty submodule.
   - Proof: `git ls-tree -r` blob ids of the old tree against d84c50bc differ only in `libavcodec/hevc/hevcdec.c` and `libavutil/encryption_info.c` (the Android fixes iOS now gains).
2. **boost-regex.** Submodule `third-party/boost_regex/regex` at boost-1.92.0 (7760ef2a); `includes = ["regex/include"]`; glob only headers.
   - Proof: tree hash 831656cc of the old `include/` equals `HEAD:include`.
3. **WAMR.** Submodule `third-party/wamr/wamr` at WAMR-2.4.5 (25bd7eb6), from github.com/bytecodealliance.
   - BUILD paths go from `core/` to `wamr/core/` and must list exactly the old 145-file subset (no JIT or AOT sources).
4. **ogg.** Submodule `third-party/ogg/ogg` at v1.3.6; keep `overlay/ogg/config_types.h`; srcs `ogg/src/{bitwise,framing}.c` plus `crctable.h`.
5. **opusfile.** Submodule `third-party/opusfile/opusfile` at v0.12 (a55c164e).
   - srcs: `src/{info,internal,opusfile,stream}.c`, the same set Android builds. Today's glob also compiles `http.c` and `wincerts.c`, which are empty without OP_ENABLE_HTTP and `_WIN32`.
   - Map `<opusfile/opusfile.h>` with `include_prefix`/`strip_include_prefix`, and add the opus public include dir for `<opus_multistream.h>`.
6. **libwebp.** Submodule `third-party/webp/libwebp` at v1.6.0, same path, so the BUILD is unchanged.
7. **td.** Delete `third-party/td/TdBinding/SharedHeaders` (787 files identical to td@0e88653d, plus 6 generated). TdBinding/Package.swift `headerSearchPath` becomes `../td/tde2e`.
8. **aes_ige.** Delete the dead `third-party/boringssl/aes_ige.c` (no BUILD references it).
9. **Docs.** `docs/mac-core-paths.txt`: add the share lines and remove `share submodules/openssl`.

Build: one combined iOS app build, plus FFMpegVideoToolboxAttachmentTests, FFMpegFileReaderPixelBufferReuseTests, FFMpegHardwareFallbackTests, OpusBinding, TgVoipWebrtc and the libprisma tests.

**W1.2 Mac integrator** (after W1.1).
- Core bump.
- `core-xprojects/libwebp/libwebp/build-cmake.sh` builds from `submodules/telegram-ios/third-party/webp/libwebp` (rsync with `--exclude .git` into build/). Delete `libwebp-1.6.0.tar.gz` and the stale `build.sh`.
- `core-xprojects/Mozjpeg/Mozjpeg/build.sh` downloads libjpeg-turbo 3.2.1 by url and checks sha256 c27ad72a…; delete the blob; update the pbxproj shellScript.
- Delete `core-xprojects/openssl/OpenSSLEncryption/patch-conf.diff` and the commented patch lines; drop the stale `submodules/telegram-ios/submodules/openssl` argument.
- `tools/tests/audio-trim-store/run.py`: point at the new ogg include and overlay paths.
- flatc: check `scripts/flatc --version` against the iOS pin, then build or fetch it in `prepare-flat-serialization.py` and delete the committed binary.
- Build: Mac app build (ffmpeg, libwebp, Mozjpeg, tde2e libs).

**W1.3 Android integrator.**
- tlottie: `.gitmodules` url → `https://github.com/dkaraush/tlottie.git`; checkout 92df98dc (v1.0.6); pin `TLOTTIE_RUST_TOOLCHAIN` to the channel in Mac's `core-xprojects/rust-toolchain.toml`; rebuild the prebuilt.
- `.gitmodules` urls (same commits): dav1d → code.videolan.org, wamr → bytecodealliance.
- Remove the dead `rlottie/src/lottie` include at `jni/voip/CMakeLists.txt:1700`.
- Build: `ng check`. Emulator: sticker corpus frames before and after.

**W1 runtime checks:**
- HEVC and CENC playback (the new FFmpeg fixes);
- voice messages and audio trimming;
- WebP stickers;
- code highlighting;
- iOS v18/v19 calls (WAMR);
- Android stickers (tlottie +6 commits).

### W2: forks that carry local patches, and Bazel nested-package swaps

**Fork lanes** (parallel; each produces a ledger and a tag):

- **F1 klaipher/openh264 `nanogram`** = v2.7.0 (1bca6231) + "H.264 level 6.0-6.2" + "subcodec sprite mode (bSubcodecMode, default off)".
  - Proof: `git diff v2.7.0..nanogram --stat` equals the 12 files; `diff -r` of the old vendored `src/` against the fork tree is empty, except test and doc paths.
- **F2 klaipher/rnnoise `nanogram`** = v0.2 (904a876d) + portability headers + denoise.c hardening + trimmed `rnnoise_data.c/h`. `cmp` against the iOS files.
- **F3 klaipher/libprisma `nanogram`** = 94640c63 + three changes:
  - iOS TokenizeStats/TokenizeLimits;
  - Android BudgetedPosition, weak_ptr guard, `search()` and `searchesToGiveUp`, reconciled with upstream's per-byte budget (both limits kept);
  - `apple/Syntaxer.mm` + `Syntaxer.h`, one copy that merges the 47 differing lines of the iOS and Mac glue.
  - `generate.js` keeps upstream `alias[0]` unless Android's Java reads joined aliases.
  - Proof: every hunk of the old Android override files and the iOS diff appears in the fork.
- **F4 klaipher/sqlcipher-amalgamation `nanogram`** = iOS `sqlite3.c` and the three headers byte-for-byte, tag v4.20.0-nanogram1, plus a generation script.
- **F5 klaipher/wallet-engine `nanogram`** (owner decision). Base: the iOS pin e59e0d89, not i582 master. Add the 5c65e47c features: key rotation preparation and preview, NFT transfer preparation, and TON Connect envelope encrypt/decrypt.
  - The recipient-key check follows the owner decision.
  - Proof: apply `jni/gram-wallet/wallet-engine.patch` to the pre-port tree and diff the result against the fork; `cargo test`.
- **S1 iOS Bazel spike.** Prove that `new_local_repository(build_file=…)` hides the upstream root `BUILD.bazel`/`MODULE.bazel` of boringssl and libyuv under Bazel 9. If it does not, use a one-commit klaipher fork that deletes the Bazel files.

**W2.I1 iOS integrator** (after F1-F4 and S1).
- **openh264**: submodule `third-party/openh264/openh264` (fork `nanogram`). Rewrite BUILD paths from `third_party/openh264/src/` to `openh264/`; the public headers keep `include_prefix = "third_party/openh264/src"`. Update subcodec BUILD, CMake, Package.swift and CLAUDE.md, and the manifest probe.
- **rnnoise**: submodule `third-party/rnnoise/rnnoise`; BUILD globs `src/`, header `include/rnnoise.h`, the 3 renames.
- **libprisma**: submodule `third-party/libprisma/libprisma`; BUILD points at the submodule core, grammars.dat and `apple/Syntaxer.mm`; keep the tests in the repo.
- **BoringSSL**: → 0.20261105.0 (3dc4b638, Android's pin) through the S1 mechanism; load `gen/sources.bzl` from it; keep the `:crypto` and `:ssl` targets.
- **libyuv**: → submodule at 0b8dd231 through the S1 mechanism; keep the NEON +dotprod+i8mm target.
- **sqlcipher**: → `submodules/sqlcipher/amalgamation`; keep the `<sqlcipher/sqlite3.h>` include path; keep Package.swift defines in sync.
- **Watch rlottie (Option A)**: klaipher/rlottie gets branch `nanogram` = Samsung v0.2 + the 16 cherry-picks + kMaxModelTreeDepth 65; submodule at `RLottieKit/Sources/RLottieCore/rlottie`; `config.h` stays as an overlay. First tag the old branches as `archive/*`.
- Build: combined iOS build; subcodec 11 tests; SyntaxHighlighterTests; Postbox tests; AES-IGE self-check; Watch build.

**W2.I2 Mac integrator** (after W2.I1).
- Core bump.
- `core-xprojects/OpenH264/OpenH264/build.sh` builds from the iOS openh264 submodule (rsync into build/; no clone; optional sha assertion against the gitlink). Update the manifest probe.
- rnnoise: pbxproj group paths become `…/third-party/rnnoise/rnnoise/{include,src}`; update the `configure_frameworks.sh` symlink.
- Telegram.xcodeproj grammars.dat path. CodeSyntax compiles the shared `apple/Syntaxer.mm`; delete the Mac copy.
- Wallet: regenerate `ffi-availability.txt` and the bindings (`scripts/check-wallet-engine-bindings.sh`).
- Build: Mac app.

**W2.I3 Android integrator** (after F1-F5).
- **openh264**: `.gitmodules` url → klaipher/openh264, branch `nanogram`; rebuild `jni/prebuild/lib/*/libopenh264.a`.
- **rnnoise**: `git rm -r jni/voip/rnnoise`; add the submodule `jni/third_party/xiph/rnnoise`. `jni/third_party/rnnoise` is already taken by Chromium's rnn_vad copy until W5. Rewrite the CMake `rnnoise` target with the 3 iOS renames.
- **libprisma**: url and branch → klaipher/libprisma `nanogram`. Delete `jni/prisma/libprisma/*` and the `configure_file` loops; `generate_prisma_grammars.sh` uses the submodule's `generate.js`; regenerate `assets/grammars.dat`.
- **libyuv**: 28ce69c2 → 0b8dd231 (keep CAN_COMPILE_SME FALSE).
- **SQLite**: `git rm jni/sqlite/sqlite3.{c,h}`; add the submodule `jni/third_party/sqlite` (amalgamation). Compile with the SQLCipher codec on against BoringSSL (SQLCIPHER_CRYPTO_OPENSSL) and never set a key, so DB files stay plain SQLite. Do not enable SQLITE_DQS=0 or OMIT_DEPRECATED.
- **wallet-engine**: branch `nanogram`; delete `gram-wallet/wallet-engine.patch`; regenerate Cargo.lock.
- Build: `ng check`. Emulator: cache4.db migration from a 3.39 database, FTS search, group-call noise suppression, a video call (openh264), code blocks, wallet flows.

### W3: widget stack (one sequential lane)

1. **W3.1 klaipher/quickjs `nanogram`** = TelegramMessenger/quickjs b526a817 (the Mac already runs it) + iOS changes 3-10 and 12, one commit each, inert unless the host API is used.
   - Proof: the iOS quickjs.c diff against quickjs-ng v0.17.0, minus upstream deltas, equals the fork's diff against b526a817.
2. **W3.2 klaipher/widget-sandbox `nanogram`** = Android's c498cef (v0.6.3 line; v0.6.5 only on owner opt-in) + the Mac commits in 015392fd that it lacks (from `git cherry`). The nested quickjs points at klaipher/quickjs `nanogram`.
3. **W3.3 klaipher/twidget `nanogram`** = Android's 4769d72 + the Mac branch commits up to cc044e7b, mapped onto the reorganised layout + the 91 iOS VENDOR.md edits (one commit each; the stricter cap wins) + the 13 gumbo changes in `litehtml/src/gumbo`.
4. **W3.4 iOS integrator.**
   - Delete the vendored `quickjs-ng/quickjs` and `include/quickjs.h`, the upstream files in `widget-sandbox/`, `twidget/LiteHTML` and `TwidgetAssets`, and `gumbo/src` and `include`.
   - Add the submodules (nested test262 set to `update = none`); keep our own tests, BUILD files and shim.
   - Re-baseline the conformance and Twidget counts.
   - Build: iOS build, the 10 twidget/gumbo cc_tests, the WidgetRuntime tests.
5. **W3.5 Mac integrator.** Core bump; `.gitmodules` twidget and widget-sandbox → klaipher forks, branch `nanogram`; delete `WIDGET_QUICKJS_*` from `tools/dev/nanogram/localfixes.py` and its tests; adapt WidgetUI to `apple/`. Build: Mac app plus widget tests.
6. **W3.6 Android integrator.** Branch `nanogram` for both; bump the gitlinks; regenerate `assets/WidgetSandbox/manifest.txt`. Emulator: widget corpus, attacks and render sets.

### W4: tgcalls single branch

**W4.1 Fork klaipher/tgcalls** (scratch rehearsal exists).

Merge `origin/nanogram-android` (545f5ae) into `nanogram-shared` (9bfb76f).
- Textual conflicts (8 files): shared wins. Android wins only for `platform/android/**` and the ea0f24e `OnRenegotiationNeeded`/`requestRenegotiation` logic.
- Fix the silent conflicts in the same merge commit:
  - `makeVideoSource(thread, thread, bool screencapture)` everywhere (darwin, desktop, fake, VideoCaptureInterfaceImpl);
  - the Descriptor keeps both `audioLevelUpdated` and `audioLevelsUpdated` (additive);
  - `EncryptionKey` stays const;
  - re-check `_currentUncroppedSink`;
  - explicit `std::weak_ptr<bool>{}`.

Add to the fork:
- `tools/wasm_to_source.py` (from iOS 466d8350);
- `tools/build_wasm_core.sh` with the iOS genrule flags (-Oz, -flto, strip-all);
- `tools/check_android_cmake.sh`, which compares the Android CMake list with `git ls-files '*.cpp'`.

Proofs before the fast-forward push:
- `git merge-base --is-ancestor 9bfb76f HEAD` and `… 545f5ae HEAD`;
- `git rev-list HEAD ^9bfb76f ^545f5ae` lists only the merge and fix-up commits;
- `git diff --stat 9bfb76f HEAD`, excluding `platform/android` and docs, shows only the signature, audio-level and renegotiation hunks;
- `git diff --exit-code 9bfb76f HEAD -- macos tgcalls/platform/darwin Package.swift`, except the 3 overrides.

Tag the result `shared-2026-11-<dd>-android`.

**W4.2 iOS integrator.**
- Bump the pin.
- The genrule uses `tgcalls/tools/wasm_to_source.py`; delete the iOS copy.
- Delete `tools/dev/tgcalls-port` and its mentions.
- Move the iOS ObjC bridge (`Sources/`, `PublicHeaders/`, `tests/TestNegotiation.mm`) into the fork as `ios/`; BUILD points there; `module_name` stays TgVoipWebrtc. Merging `ios/` and `macos/` into `apple/` waits for the owner's go.
- Build: TgVoipWebrtc, all policy cc_tests, mtproto_dtls_transport_test, content_negotiation_test, signaling_sctp_options_test, group_frame_transformer_test, tgcalls_cli `run-negotiation-tests.sh` (v7-14, 18, 19, group).

**W4.3 Mac integrator.**
- Core bump; `submodules/tgcalls` goes to the same commit.
- Check that `Package.swift` excludes still match: new files live only under `platform/android`.
- Build: `swift build` of the fork target, the fork `tests/*/run.sh`, Mac app.

**W4.4 Android integrator.**
- `.gitmodules` tgcalls branch → `nanogram-shared`; bump the pin.
- Set `-std=c++20` in `voip/CMakeLists.txt:6` if the shared code needs it.
- Install wasi-sdk in prebuild and regenerate `voip/ReferenceCoreEmbedded.cpp` with the fork script. The sha256 of the wasm must equal the iOS genrule output.
- Build: `ng check`. Emulator: 1:1 calls to iOS and Mac, a group call, a livestream.

**W4.5 Fork cleanup** (owner approval). Tag `archive/*` at bdda8ef, a598e27, 545f5ae and 3a85cd9, then delete `telegram-ios-dev`, `nanogram-mac`, `nanogram-android` and the stale `development` mirror. Upstream syncs fetch TelegramMessenger/tgcalls directly.

### W5: WebRTC single source

**W5.1 Dependencies, decided from the W0.5 ledgers:**
- **abseil**: official LTS 20260817.0 for all three.
  - iOS: a Bazel module or submodule.
  - Android: already on it.
  - Mac: replaces the macosswift fork once Apple Clang builds 20260817 (the lifetime-annotation workaround may no longer be needed; verify).
- **crc32c, libsrtp, usrsctp**: official tag submodules if each pristine diff is build glue only; otherwise carried in klaipher/webrtc under `third_party/` with their hunks as commits.
- **pffft, rnnoise-vad, fft4g, libevent**: carried in klaipher/webrtc under `third_party/` in Chromium layout, because they are Chromium snapshots with Chromium patches.

**W5.2 Fork klaipher/webrtc**: branch `nanogram` from 779c4aa7. `nanogram-security-fixes` stays as a fast-forward alias until all pins move. Commits:
1. drop the debug-log hunks (`video_receive_stream2.cc`, `generic_decoder.cc`, `webrtc_video_engine.cc`);
2. the abseil unification plus Android's 93d06ca `absl_nonnull`/`absl_nullable` edits (21 files);
3. fold in the 57 tg_owt-only platform files. The macOS `RTCMTLRenderer` change goes under `TARGET_OS_OSX`. Review `physical_socket_server`/`ip_address`, because `WEBRTC_MAC` is also defined on iOS. Hand-merge the 5 files edited on both sides, keeping the iOS FFmpeg 7+ pts fix and the H.264 decoder gate;
4. the Mac audio-device changes (`audio_device_mac_options.h`, HID polling, guid fix, buffer clamp);
5. move the tg_owt CMake build to the fork root (`webrtc_loc = ${CMAKE_CURRENT_SOURCE_DIR}`, `TG_OWT_THIRD_PARTY_DIR`, include shims, `-DWEBRTC_USE_H264_DECODER`);
6. Android platform code under `WEBRTC_ANDROID`:
   - `audio_device_impl` and the screen-audio layers, `jvm_android` with FileLog replaced by RTC_LOG;
   - legacy `modules/audio_device/android/**`, `isac/**`, `common_types.h`;
   - generated JNI headers and `jni_zero`;
   - H.265 parser bounds checks; the `rtp_payload_params` temporal-layer check, merged with the iOS fix;
   - `openssl_stream_adapter` permute-extensions: re-enable if BoringSSL 0.20261105 builds it, else guard it;
7. Android Java: the 28 Telegram-modified files onto the upstream paths, plus 9 Android-only files under `sdk/android/src/java` and the voiceengine directory;
8. the W5.1 `third_party/` dependencies.

Out of scope by default: fa833523 and the desktop-app SSL-root commits (owner opt-in, section 9).

Proof:
- per copy (iOS 779c4aa7, tg_owt 991142f9 with the `src/` path map, Android voip/webrtc at fdd4407), every differing blob appears in the ledger;
- `comm -23` of Android files against fork files is empty;
- the 173 Android Java files map 1:1 (136 identical, 28 merged, 9 added).

**W5.3 iOS integrator.**
- `.gitmodules` branch `nanogram`; bump.
- `third-party/webrtc/BUILD`: abseil and dependency labels.
- Delete `third-party/webrtc/{absl,crc32c,libsrtp,pffft,rnnoise,fft4g,dependencies}` once replaced.
- Delete the about 75 `allow webrtc` lines and the `webrtc_differences` gate; webrtc becomes a three-app pin.
- Update `upstream-sync.json`.
- Build: iOS build, `neteq_normal_test`, TgVoipWebrtc tests.

**W5.4 Mac integrator.**
- `git rm submodules/tg_owt`; add `submodules/webrtc` → klaipher/webrtc `nanogram`.
- `core-xprojects/webrtc/webrtc/build.sh` and the pbxproj point at `submodules/webrtc`, with third-party sources from the single sources (libyuv from the iOS libyuv submodule, abseil, libsrtp, crc32c).
- Remove the abseil `attributes.h` patch in `scripts/setup_build_env.sh`.
- Update `framework-inputs.sh`, the manifest probes, INSTALL.md and AGENTS.md.
- Build: `libtg_owt.a` (arm64), Mac app, tgcalls host tests.
- Later, optional: the Mac enables the seams (InstanceV2CompatImpl) in the fork's Package.swift.

**W5.5 Android integrator.**
- `git rm -r jni/voip/webrtc`, plus the in-tree `jni/third_party/{libsrtp,usrsctplib,pffft,rnnoise}` and `jni/voip/third_party/{crc32c,libevent,jni_zero}` that W5.1 replaced.
- Add the submodule `jni/voip/webrtc` (fork `nanogram`, shallow).
- Delete `src/main/java/org/webrtc`; add the fork Java `srcDirs` in `TMessagesProj/build.gradle` (excluding tests).
- CMake: include shims, generated field trials, `-DWEBRTC_USE_H264_DECODER`.
- Build: `ng check`; the dex class list is identical before and after. Emulator: calls and screen-share audio.

**W5.6 Call matrix.** iOS, Mac, Android and official Telegram clients: 1:1 v7-19, group, video, screen share, reflector candidates on ports under 1024 (the Mac bug fixed by unification).

### W6: cleanup and enforcement

- Archive klaipher/tg_owt (read-only).
- Tag and delete the stale fork branches: rlottie `nanogram-mac` and `telegram-ios-dev`; webrtc `nanogram-security-fixes`.
- In the manifest, every library has one source and one commit (or is platform-only); `check_shared.py` runs in all three pre-push hooks.
- Grep all three repos for dead names (tg_owt, nanogram-security-fixes, nanogram-mac, nanogram-android, telegram-ios-dev, tgcalls-port, removed vendored paths): no hits outside CHANGELOG history.

### W7: iOS/Mac app-code dedupe

- Mac SwiftPM packages reference iOS sources (`submodules/telegram-ios/...`) instead of copies: Stripe, HLSPlayback, SwiftMath, VP9Hardware, RingBuffer, EDSunriseSet, Svg LegacyImpl. Each drifted file is reconciled into iOS first, so the shared fix lands on both apps.
- QrStudioUI and SceneEffects: shared logic moves into one iOS module; AppKit and UIKit views stay per app.
- Remove the parity `mirror` lines.
- Build: Mac app plus HLSPlaybackTests and VP9HardwareTests; iOS build.

## 6. Build-system risks

| Risk | Where | Mitigation |
|---|---|---|
| Submodule with upstream BUILD.bazel/MODULE.bazel becomes its own Bazel package | iOS: boringssl, libyuv, abseil, libjxl | W2.S1 spike: `new_local_repository` with `build_file`; fallback is a one-commit fork. libjxl stays vendored. |
| Glob over a submodule picks up non-source files | iOS: WAMR, boost, FFmpeg | Explicit lists (WAMR 145 files), header-only globs, dotfile excludes |
| Fresh clone without `--recursive` gives an empty genrule input | iOS, Mac | `tg doctor` check for empty submodules |
| Mac Xcode projects hard-code iOS paths | rnnoise, openh264, libwebp, libprisma, wallet | Integrator edits pbxproj in the same commit as the iOS path change; build scripts copy sources (rsync) so the build never dirties a submodule |
| Android committed prebuilts drift from submodules | openh264, libyuv, boringssl, ffmpeg | `ng prebuild` keys on the submodule commit; CMake fail-fast check (exists for FFmpeg; extend) |
| Android explicit CMake source lists | tgcalls, webrtc | Fork check scripts compare lists with `git ls-files` |
| C++17 (Android) vs C++20 (iOS, Mac) | tgcalls, webrtc | Raise Android to c++20 if the first build fails on shared code |
| Abseil API change (`absl::Nonnull` removed in 20260817) | webrtc on iOS and Mac | Land Android's 93d06ca edits together with the abseil unification (W5) |
| Mac WebRTC without Telegram seams | tgcalls | `TGCALLS_WEBRTC_WITHOUT_TELEGRAM_SEAMS` until W5.4 |
| Shallow submodules need the pin reachable from the branch tip | all forks | Fast-forward pushes only; tags for archived tips |
| parity.py rejects unknown directives | iOS, Mac | Change the code before the config (W0.2) |
| Mac core lag gate from 2026-11-18 | Mac | Core bump in every wave |

## 7. Call-specific risks

- Android gets all 63 Nanogram engine commits at once in W4. Apple-only code is compiled out, but LogSinkImpl, MediaManager and group-audio changes apply to Android.
- A stale wasm blob against new host code breaks v18/v19 calls. Regenerate it in W4.4 and check its sha against the iOS genrule output.
- The Mac moves to the same WebRTC as iOS and Android in W5. It gains the reflector-port fix, the FFmpeg 7+ decoder fix and the seams. The Mac audio-device changes must be kept exactly (HID polling), or call CPU regresses.
- Android loses nothing in W5 and gains 12 security fixes. The legacy ADM, screen-share audio and voiceengine Java must survive, or calls lose audio.

## 8. Proving that nothing was lost (per component)

1. **Before deleting a copy**: `git ls-tree -r <ref> <copy>` against the pristine base gives the local diff, which is written into the ledger.
2. **After the swap**:
   - a new submodule is a blob-identity match with the old tree, except ledger-listed paths;
   - a fork: `git diff <base-tag>..nanogram --stat` equals the ledger file list, and every old hunk passes `git apply --reverse --check` on the fork, or is marked dropped with a reason.
3. **Pins**: `check_shared.py` reports one commit per library across `git ls-tree` in iOS, Android and the Mac core (plus Mac's own submodules for tgcalls and webrtc).
4. **Builds and tests**: the wave's combined builds and listed runtime checks.

## 9. Owner decisions

1. **wallet-engine recipient-key check.** iOS verifies the key; Android removed the check. Recommended: verify by default, with an explicit opt-in for rotated keys. Blocks W2.F5 only.
2. **Behaviour jumps to approve:**
   - Android: SQLite 3.39.3 → 3.54.1, rnnoise model, tgcalls engine, tlottie, libyuv, WebRTC security fixes;
   - iOS: BoringSSL +134 commits;
   - iOS and Mac: two FFmpeg fixes.
3. **Upstream intake opt-in** (default: wait for the release sync): webrtc fa833523, desktop-app SSL roots, wallet-engine a396c520, twidget 7249ce71, widget-sandbox v0.6.5, tgcalls development (51 commits).
4. **Mac widget commits**: upstream PRs, or carry them in klaipher forks (default: carry them).
5. **tgcalls ObjC bridge**: full `apple/` merge or keep `ios/` and `macos/` side by side (default: side by side).
6. **Watch rlottie**: Option A, a fork submodule (default), or Option B, move to tlottie.
7. **Remote ref removals and archiving**: approve after the archive tags exist.
8. **New repos**: approve klaipher/openh264, rnnoise, quickjs (public as forks), sqlcipher-amalgamation and nanogram-deps.
9. **Hands-on call tests** with real peers after W4 and W5. These are skipped if there is no answer (don't-block rule).

## 10. Decisions applied by the main session (2026-11-14, owner: "do all, fix everything")

- Watch: Option B. The Apple Watch app moves to tlottie (no_std, pinned nightly + -Zbuild-std=core,alloc for
  arm64_32-apple-watchos, aarch64-apple-watchos and the simulator targets); rlottie is deleted from the watch.
- Mac: the rlottie fallback (LottieBackend force/kill switch, RLottie_Xcode project, submodules/rlottie) is removed;
  tlottie always renders; the macos_killswitch_disable_tlottie server flag is ignored (documented in NANOGRAM-CHANGES).
- iOS app: the dead submodules/rlottie (no BUILD dependents) is removed.
- wallet-engine: recipient-key check stays on by default (iOS behaviour); Android's key-rotation path becomes an explicit
  opt-in parameter.
- Behaviour changes Android takes from the single sources (SQLite via SQLCipher amalgamation, xiph rnnoise 0.2, the 63
  Nanogram tgcalls commits, tlottie, libyuv): accepted. SQLCipher on Android: codec enabled against the existing
  crypto provider (safer default), no key unless upstream used one.
- Upstream intake beyond versions one of our apps already ships: no (releases-only rule).
- Mac widget commits: carried in our forks (no upstream PRs now).
- tgcalls ObjC bridges: the iOS bridge moves into the fork as ios/; merging iOS+Mac bridges deferred (needs builds).
- Old fork branches: tag archive/<name> at their heads, then delete (tgcalls telegram-ios-dev, nanogram-mac,
  nanogram-android, development; rlottie nanogram-mac, telegram-ios-dev; webrtc nanogram-security-fixes once unused).
- New forks are public (forks of public repos); klaipher/nanogram-deps is private.
- No builds and no pushes to the three app repos until every requested task is done (owner rule). Fork branches are
  prepared as local commits in ~/Projects/wt/forks/<name> and pushed by the main session.
