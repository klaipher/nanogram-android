#!/usr/bin/env bash
# Build static libopus for Android: floating point, with Opus 1.6 speech enhancement (OSCE: LACE, NoLACE and bandwidth
# extension) compiled in. The decoder runs it only when a caller raises the decoder complexity (FFmpeg's libopus decoder
# options osce_complexity / osce_bwe, set by the opt-in "Enhance voice messages" switch), so every other decode, including
# calls, stays at complexity 0.
#
# Expected layout:
#
#   jni/
#     third_party/
#       xiph/
#         opus/
#     prebuild/
#       build_opus.sh
#   ../../shared/opus/   (the nanogram-deps submodule: opus-options.sh, fetch-models.sh, check-link-map.sh)
#
# Result:
#
#   jni/prebuild/lib/arm64-v8a/libopus.a
#   jni/prebuild/lib/armeabi-v7a/libopus.a
#   jni/prebuild/lib/x86_64/libopus.a
#   jni/prebuild/lib/x86/libopus.a
#
# build_opus/ is the temporary workspace and is removed after a successful build. The submodule checkout is never
# written: the build works on a COPY of the opus tree (the model weights are extracted into it).
#
# One shared source: the CMake options, the C flags and the pinned model archive are nanogram-deps opus/opus-options.sh
# (the same file iOS and macOS source); opus/fetch-models.sh downloads and sha256-verifies the weights (cache
# ~/.cache/nanogram-deps/opus-models). This script used to be a FIXED_POINT build from an explicit source list, because
# the fixed-point ARM path of opus's CMake references the asm-only celt_pitch_xcorr_neon; the float build has no such
# symbol (CMake builds the NEON intrinsics, OPUS_PRESUME_NEON=ON).
#
# Optional environment overrides:
#   OPUS_SOURCE_DIR=/path/to/opus
#   SHARED_OPUS_DIR=/path/to/nanogram-deps/opus
#   ANDROID_NDK_HOME=/path/to/ndk
#   ANDROID_SDK_ROOT=/path/to/sdk
#   NDK_VERSION=30.0.16248370
#   API=31
#   ABIS="arm64-v8a armeabi-v7a x86_64 x86"
#   JOBS=16

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

OPUS_SRC="${OPUS_SOURCE_DIR:-$SCRIPT_DIR/../third_party/xiph/opus}"
SHARED_OPUS_DIR="${SHARED_OPUS_DIR:-$JNI_DIR/../../shared/opus}"
BUILD_DIR="$SCRIPT_DIR/build/opus"
# The copy of the opus tree that the build reads (the models are extracted into it, never into the submodule).
OPUS_BUILD_SRC="$BUILD_DIR/src"
# The release tarball ships package_version; a git checkout would ask git, which fails in a copy without .git
# (iOS and macOS write the same constant).
OPUS_VERSION="1.6.1"

require_ndk
require_cmake_ninja

[[ -f "$OPUS_SRC/CMakeLists.txt" ]] || {
    echo "ERROR: missing $OPUS_SRC/CMakeLists.txt" >&2
    echo "       Set OPUS_SOURCE_DIR if the source tree is elsewhere." >&2
    exit 1
}
[[ -f "$SHARED_OPUS_DIR/opus-options.sh" && -f "$SHARED_OPUS_DIR/fetch-models.sh" ]] || {
    echo "ERROR: missing the shared opus options in $SHARED_OPUS_DIR" >&2
    echo "       Run: git submodule update --init shared   (or set SHARED_OPUS_DIR)" >&2
    exit 1
}

# OPUS_COMMON_ARGS, OPUS_OSCE_ARGS, OPUS_OSCE_C_FLAGS (see that file for what each flag does).
. "$SHARED_OPUS_DIR/opus-options.sh"

rm -rf "$BUILD_DIR"
mkdir -p "$OPUS_BUILD_SRC"

echo "==> Copying the opus tree to $OPUS_BUILD_SRC"
tar -C "$OPUS_SRC" --exclude=.git -cf - . | tar -C "$OPUS_BUILD_SRC" -xf -
echo "PACKAGE_VERSION=\"$OPUS_VERSION\"" > "$OPUS_BUILD_SRC/package_version"

sh "$SHARED_OPUS_DIR/fetch-models.sh" "$OPUS_BUILD_SRC"

for abi in $ABIS; do
    ABI_BUILD="$BUILD_DIR/$abi"
    ABI_OUT="$(abi_output_dir "$abi")"
    extra=()

    case "$abi" in
        arm64-v8a|x86_64|x86)
            ;;
        armeabi-v7a)
            extra+=(-DANDROID_ARM_NEON=ON)
            ;;
        *)
            echo "ERROR: unsupported ABI: $abi" >&2
            exit 1
            ;;
    esac

    echo
    echo "==> Building Opus $OPUS_VERSION (float, OSCE + BWE, no deep PLC) for $abi (API=$API)"

    # Appended to CMAKE_C_FLAGS_RELEASE (the shared file says so), not to CMAKE_C_FLAGS. -w: the vendor code's warnings
    # are not ours. -ffast-math is not used: the float build is the one iOS and macOS ship and the one the models were
    # verified against.
    # shellcheck disable=SC2086
    "$CMAKE_BIN" \
        -S "$OPUS_BUILD_SRC" \
        -B "$ABI_BUILD" \
        -G Ninja \
        -DCMAKE_MAKE_PROGRAM="$NINJA_BIN" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
        -DCMAKE_C_FLAGS="$CMAKE_REPRO_C_FLAGS" \
        -DCMAKE_C_FLAGS_RELEASE="-O2 -DNDEBUG -w -fvisibility=hidden -ffunction-sections -fdata-sections $OPUS_OSCE_C_FLAGS" \
        -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM="android-$API" \
        ${extra[@]+"${extra[@]}"} \
        $OPUS_COMMON_ARGS \
        $OPUS_OSCE_ARGS

    "$CMAKE_BIN" --build "$ABI_BUILD" --parallel "$JOBS"

    [ -f "$ABI_BUILD/libopus.a" ] || {
        echo "ERROR: libopus.a was not produced for $abi" >&2
        exit 1
    }

    copy_archive "$ABI_BUILD/libopus.a" "$ABI_OUT/libopus.a"
    finalize_archive "$ABI_OUT/libopus.a"

    echo "==> $ABI_OUT/libopus.a"
done

rm -rf "$BUILD_DIR"

echo
echo "Opus static build completed."
for abi in $ABIS; do
    echo "  $LIB_DIR/$abi/libopus.a"
done
