# Changelog (private, detailed)

Newest first: version, build number, date, commit; what changed and how.

## Unreleased

### Infrastructure
- Repository `klaipher/nanogram-android` created from a history-free import of upstream release 13.2.2 (7303),
  DrKLO/Telegram 21f7d109 (commit 406cbeb).
- Telegram-specific submodules now come from klaipher forks (tlottie, wallet-engine, media, jlatexmath-android,
  libprisma, twidget, widget-sandbox); tgcalls reuses the shared `klaipher/tgcalls` fork, branch `nanogram-android`.
- `Tools/dev/ng` developer tool, pinned toolchain (`Tools/dev/toolchain.properties`), pre-push gate, agent guide.

### Identity (2ea238e)
- applicationId `org.klaipher.nanogram`, app name Nanogram; own api_id/api_hash through BuildConfig (ng passes them
  through the environment); own release key when present; Firebase only from our own config in the app module.
- Fork flags: Play billing unavailable, passkeys off, no Play Store URL.

### Build speed and size (2ea238e)
- arm64-v8a only by default; ccache for native compiles; no LTO in debug native builds; Gradle configuration cache
  (parallel) and build cache; non-transitive R; Jetifier off; upstream's extra app modules only on request;
  16 KB flexible page sizes; uncompressed native libraries.
- Measured (arm64 debug): upstream cold 306 s; ours no-op 3 s, clean rebuild with warm caches 54 s.

### Toolchain and dependencies (25fadc9)
- Gradle 9.9.1, AGP 9.5.1 (new DSL + androidComponents API), Kotlin 2.4.21, NDK r30, CMake 4.1.2, build-tools 37,
  compileSdk 37, Java 17; minSdk 23 and targetSdk 36 as upstream.
- versionCode = minutes since 2020-01-01 of the build number × 10 + ABI digit.
- Only the 11 androidx/media modules in use; media fork on branch `nanogram-android` (klaipher/media 07b5247).
- Libraries updated to the newest releases that still support minSdk 23 (list in the commit); kept: palette 1.0.0,
  isoparser 1.0.6, stripe 2.0.2, Firebase BoM 34.19.0, wallet 19.5.0 (each blocked by minSdk 24 or an API rewrite).
- SDK 37 dropped FingerprintManager: compile-only API 36 stubs, behaviour unchanged.
- WebRTC `Candidate::set_type` lifetimebound removed for NDK r30 clang (same as Mac tg_owt).
- Emulator smoke test (arm64, Android 16, 16 KB pages): launches, native libs load, MTProto handshakes complete.

### Native dependencies (93d06ca)
- TDLib = iOS pin 0e88653d (1.8.69); abseil LTS 20260817.0 (WebRTC moved to absl_nonnull/absl_nullable, 80 uses);
  BoringSSL 0.20261105.0 (archive paths fixed in build_boringssl.sh); OpenH264 2.7.0; boost-regex 1.92.0.
- ng prebuild keys on checked-out submodule commits, uses ccache, removes its scratch tree.

### Build fixes (d972313)
- CMake 4.1.2 now really used (version set where AGP reads it); R8 rule for framework fingerprint classes;
  v1+v2+v3 release signing; ng prune removes failed prebuilds.

### Branding (91e91be)
- Comet launcher icon (adaptive, themed, legacy, round), alternate icons = iOS Nanogram designs (Light, Orbit, Glass,
  Speed, Night), Comet notification and caller icons, intro Comet on navy + "Nanogram" title, Nanogram wordmark in
  the chat list and stories header, ~50 app-name strings say Nanogram in every language (NanogramBranding).

### Default icon = Sphere, like iOS/Mac
- The launcher default is now the Sphere (the iOS/Mac default since 2026-11-04), built from the same layers as the
  iOS Liquid Glass icon: starry background as the adaptive background, light/sphere/sparkle as the foreground
  (launcher parallax), the iOS mono layers as the themed icon. Comet moves to the alternates (Vintage slot); intro and
  caller icon use the Sphere. The renderer now draws SVGs with AppKit (keeps transparency).

### minSdk 24 and the held-back libraries
- minSdk 23 -> 24 (owner decision), native API level 24 (CMake and prebuilts).
- cast 23.0.0, maps 20.1.0, auth 22.0.0, webkit 1.18.0, palette 1.1.0, reCAPTCHA 18.9.4, Firebase BoM 35.2.0.
- play-services-auth 22 removed GoogleSignIn: e-mail login uses Credential Manager "Sign in with Google"
  (NanogramGoogleSignIn, googleid 1.2.1); same e-mail + ID token to the server.
- Emulator: launches, MTProto handshakes complete.

### Measurements
- Release arm64: 57.5 MB APK (R8 full mode, full LTO), 182 s; debug 98.7 MB. Details in docs/nanogram/build.md.
