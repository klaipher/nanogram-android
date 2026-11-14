# FFmpeg patches

Nanogram's FFmpeg fixes live as commits on branch `nanogram` of https://github.com/klaipher/FFmpeg (official release
plus our patches), shared by Nanogram for Android, iOS and macOS. Telegram's two former patches here (encryption_info
stack exhaustion, HEVC slice layer check) are commits there.

Put a `*.patch` here only for an Android-only change that cannot go into the shared branch; `build_ffmpeg.sh` applies
it to a private copy and appends `-tgN` to the version.
