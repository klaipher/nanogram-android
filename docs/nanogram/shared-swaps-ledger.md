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
