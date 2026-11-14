# Android: copies replaced by the single shared sources (no-loss ledger)

For each in-tree copy that was deleted in favour of a shared source (plan: docs/shared-libraries-plan.md, rule: one
source per library for iOS, macOS and Android): what the copy was, how it differs from its pristine upstream, and why
nothing is lost. The manifest of sources is `shared/shared-libraries.json`.

## rnnoise (jni/voip/rnnoise, deleted) -> klaipher/rnnoise `nanogram` (submodule jni/third_party/xiph/rnnoise)

- Copy: 22 files (AUTHORS, COPYING, README, include/rnnoise.h, 18 files in src/): xiph/rnnoise from before v0.1 (2018 model:
  `rnn_data.c`, `rnn_reader.c`, `celt_lpc.c`, KissFFT with opus names).
- Local changes: none. Every one of the 22 blobs exists unchanged in the xiph/rnnoise history
  (`git log --all --find-object=<blob>` in the klaipher/rnnoise clone finds each one: e.g. `src/denoise.c` 5a628440 at
  eca42a4, `src/rnn_data.c` 22c53165 at 231b9c0, `include/rnnoise.h` c4215d96 at 085d8f4). The only Android-side edits
  were in `jni/voip/CMakeLists.txt`: a symbol-rename list (`rnnoise_` prefixes) and Opus-style compile definitions.
- New source: xiph v0.2 (904a876d) + 3 Nanogram commits (portability headers, `denoise.c` hardening, trimmed built-in
  model), the same tree iOS and macOS build. v0.2 prefixes its internals with `rnn_`, so only `compute_rnn`, `eband20ms`
  and `init_rnnoise` need the `rnnoise_` rename (same three as iOS).
- Behaviour change (owner decision 2026-11-14): voice-message noise suppression (`audio.c`: `rnnoise_create(NULL)`,
  `rnnoise_process_frame`) uses the v0.2 built-in model instead of the 2018 model. The C API used is unchanged.

## libprisma (jni/prisma/libprisma/*, jni/prisma/generate.js, deleted) -> klaipher/libprisma `nanogram` (submodule jni/third_party/libprisma, c061944d)

- Copy: four full-source override files (`Highlight.{cpp,h}`, `SyntaxHighlighter.{cpp,h}`: Android's `BudgetedPosition`,
  `weak_ptr` guard, `search()` and `searchesToGiveUp` regex-budget work, copied over the submodule's files by CMake
  `configure_file` at configure time) and `generate.js` (upstream plus `alias.join(' ')`).
- Base: Telegram libprisma 94640c63 (the previous Android pin).
- Carried: the fork (base 94640c63) has the Android budget/search changes reconciled with upstream's per-byte budget
  (both limits kept) and with iOS's `TokenizeStats`/`TokenizeLimits`; identifiers present in both the old overrides and
  the fork: `BudgetedPosition` (28 vs 27 occurrences, the difference is the reconciled iOS code), `weak_ptr` 2/2,
  `searchesToGiveUp` 2/2, `SyntaxHighlighter::search` 1/1. `generate.js` alias joining is the fork's
  `PRISMA_JOIN_ALIASES=1` (default output = upstream/iOS), which `generate_prisma_grammars.sh` now sets.
- Not proven by blob identity (the files were hand-reconciled in the fork, wave A): the emulator attack cases (regex
  budget, HTML-tag) are the arbiter.

## SQLite (jni/sqlite/sqlite3.{c,h}, deleted) -> klaipher/sqlcipher-amalgamation `nanogram` (submodule jni/third_party/sqlite, 9948d5af)

- Copy: `sqlite3.c` (git blob sha1 of the file content 4d8e9801ec20eef3a0a05c4991ccb45b0fcdbdbf) and `sqlite3.h` (afc995ee4c8230dc426d5c85b3048ebcef4b1a39).
- Local changes: none. Both files are byte-identical to the pristine `sqlite-amalgamation-3390300.zip` (SQLite 3.39.3,
  https://www.sqlite.org/2022/sqlite-amalgamation-3390300.zip; `shasum` of the committed files equals `shasum` of the zip's files).
  The old build had no SQLITE_* options at all (the Android edits were only the unrelated legacy `NULL=0 ...` definitions
  in CMake, which stay).
- New source: SQLCipher 4.20.0 on SQLite 3.54.1, the iOS amalgamation byte for byte. Android compiles the codec in against
  BoringSSL (`SQLCIPHER_CRYPTO_OPENSSL`, a BoringSSL that reports OPENSSL_VERSION_NUMBER 0x1010107f, so SQLCipher takes its
  OpenSSL 1.1 `HMAC()` path, not `EVP_MAC_*`) and never calls `sqlite3_key`, so database files remain plain SQLite.
- Flags Android sets: SQLITE_HAS_CODEC=1, SQLITE_EXTRA_INIT/SHUTDOWN (required), SQLITE_THREADSAFE=1, SQLITE_TEMP_STORE=2
  (required 2 or 3; 2 keeps `PRAGMA temp_store = FILE` working, which MessagesStorage uses), SQLITE_OMIT_LOAD_EXTENSION,
  HAVE_USLEEP=1 (the busy handler sleeps in microsecond steps instead of whole seconds), NDEBUG=1. Left off on purpose:
  SQLITE_DQS=0 and SQLITE_OMIT_DEPRECATED (legacy behaviour the Java code relies on), SQLITE_ENABLE_FTS5 (nothing in
  Android uses full-text tables), SQLITE_MAX_MMAP_SIZE=0, SQLITE_DEFAULT_MEMSTATUS=0, SQLITE_ENABLE_QPSG (behaviour
  changes iOS accepted, not needed here).

## wallet-engine (jni/gram-wallet/wallet-engine.patch, deleted; submodule pin 5c65e47c) -> klaipher/wallet-engine `nanogram` (44ae85c7)

- Copy: the reference patch `gram-wallet/wallet-engine.patch` (1018 lines, 12 files) and the submodule pinned at klaipher
  master 5c65e47c. The patch applies cleanly to i582/wallet-engine 38147c2 (the parent of e59e0d8) and the patched tree
  equals 5c65e47c except `WASM.md` (documentation only) (`git apply --check` then `diff -r` against `git archive 5c65e47c`).
  It holds Android's changes: encrypted-comment key used without checking, key rotation preparation and preview, NFT
  transfer preparation, TON Connect envelope encrypt/decrypt.
- Carried: every file of those features in the fork is blob-identical to 5c65e47c (`git diff --stat 5c65e47c nanogram`
  lists only records, the docs of the key check, `src/domain/encrypted_comment.rs`, `src/engine/encrypted_comment.rs`,
  `src/wallet/{encrypted_comment,mod,recipient_public_key}.rs`, the lifecycle scenario test and `web/`). The fork's base
  is i582 e59e0d89 (the iOS pin), which re-adds the recipient-key check.
- Intended difference (owner decision 2026-11-14): the fork VERIFIES a supplied recipient key by default, like iOS.
  Android's behaviour (use the supplied key as is) is the explicit opt-in `trust_recipient_public_key = true`, which the
  Android bridge now passes at both places where it supplies a comment recipient key (`nativePrepareSend`, `nativeSend`).
- Cargo.lock: `cargo metadata` (Rust 1.99.1) on a scratch copy of `gram-wallet` against the fork tree leaves `Cargo.lock`
  unchanged, so the committed lock file already satisfies the new engine (the `--locked` prebuild is the final arbiter).

## twidget and widget-sandbox (submodules, Android line) -> klaipher/twidget `nanogram` and klaipher/widget-sandbox `nanogram`

- Android's pins were the starting points of both fork branches (twidget 4769d72, widget-sandbox c498cef), so nothing in
  the Android line is lost: `git merge-base --is-ancestor 4769d72 nanogram` and `... c498cef nanogram` hold in the forks.
- widget-sandbox 1b21a48 differs from c498cef only in `packages/ios/**` (Swift), docs, records and `.gitmodules` (the
  nested quickjs now klaipher/quickjs `nanogram` f6fc68b9, which contains Android's old nested pin ca3594da as an ancestor;
  `git diff --stat c498cef nanogram -- packages/desktop packages/core packages/android` is empty).
- Android's `assets/WidgetSandbox/*` bundles and `manifest.txt` are therefore still exactly the bundles of the pinned
  widget-sandbox (`packages/desktop/WidgetJS/*` hashes equal the assets, which equal the manifest): no regeneration needed.
  Both QuickJS trees use bytecode version 29, so the committed `.qbc` files stay valid.

## tgcalls (submodule jni/tgcalls, branch nanogram-android 545f5ae) -> klaipher/tgcalls `nanogram-shared` (2a5daa0f)

- Nothing in the Android line is lost: the shared head is the merge of 545f5ae into the iOS/macOS line (`git merge-base
  --is-ancestor 545f5ae 2a5daa0` holds in the fork). Android-only code (`platform/android/**`, the renegotiation logic of
  ea0f24e) is kept as is.
- Android CMake: `tools/check_android_cmake.sh` (fork) against `voip/CMakeLists.txt` lists only the three host tests
  (`v2/ContentNegotiationTest.cpp`, `v2/MtProtoDtlsTransportTest.cpp`, `v2/SignalingSctpOptionsTest.cpp`).
- JNI bridge `org_telegram_messenger_voip_Instance.cpp`: source compatible with the merged `Instance.h` (const
  `EncryptionKey::value` accepts the moved non-const array; `audioLevelsUpdated(float,float)` kept). No edit.
- `voip/ReferenceCoreEmbedded.cpp` is regenerated with the fork's `tools/build_wasm_core.sh` (build step).

## WebRTC (jni/voip/webrtc, 4,267 files, and src/main/java/org/webrtc, 173 files) -> klaipher/webrtc `nanogram` (fa0b3c5f)

- C++: 4,090 files blob-identical in the fork; 92 generated JNI headers identical under `nanogram/android-generated`; 7 not
  carried (generated `registered_field_trials.h`, produced at build time; six protobuf outputs no compiled file includes);
  78 differ, each ledgered row by row in the fork (ledger-webrtc-fork.md section 5). The Android-only trees (legacy audio
  device, iSAC, `common_types.h`, 143 files) were added to the fork identical.
- Java: 173 of 173 old files blob-identical in the four fork source directories used by Gradle. The directories hold one
  extra upstream class, `EglThread`, which needs `EglBase.EglConnection` (absent from the Telegram-modified `EglBase.java`),
  so it cannot compile: `TMessagesProj/build.gradle` excludes it (`java.exclude '**/org/webrtc/EglThread.java'`). The compiled
  set is therefore exactly the old 173 files. Check in the combined build that the dex class list of `org.webrtc` is unchanged (plan W5.5).
- Dependencies: abseil (2065f4de, the pin Android already had), libsrtp v2.5.0 (a566a9cf) and crc32c (21fc8ef3) are the
  fork's nested submodules; pffft (11 files), rnnoise-vad (7) and jni_zero (30) are blob-identical to the fork's
  `third_party/` copies; libevent (Chromium snapshot, 40 files; 20 identical, 19 differ only by removed `HAVE_CONFIG_H` guards,
  `config.h` = the fork's `android/config.h`) and usrsctp (61 unreferenced files) are dropped (stdlib task queue; nothing
  includes usrsctp).
- Config glue the app keeps: `voip/crc32c_config/crc32c/crc32c_config.h`, include shims and the field-trials generation in
  `voip/CMakeLists.txt`.
