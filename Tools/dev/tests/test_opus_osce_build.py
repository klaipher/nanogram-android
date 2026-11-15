"""Guards for the Opus OSCE build (Enhance voice messages): one shared source for the options, float, no deep PLC."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
JNI = ROOT / "TMessagesProj" / "jni"
BUILD_OPUS = (JNI / "prebuild" / "build_opus.sh").read_text()


class OpusBuildScript(unittest.TestCase):
    def test_sources_the_shared_options_and_does_not_copy_them(self):
        self.assertIn('. "$SHARED_OPUS_DIR/opus-options.sh"', BUILD_OPUS)
        self.assertIn('fetch-models.sh" "$OPUS_BUILD_SRC"', BUILD_OPUS)
        self.assertIn("shared/opus", BUILD_OPUS)
        # The values live in nanogram-deps; a literal copy here would let the apps drift.
        for literal in ("-DOPUS_OSCE=ON", "-DENABLE_OSCE_BWE", "-UENABLE_DEEP_PLC", "OPUS_FIXED_POINT=OFF", "a5177ec6"):
            self.assertNotIn(literal, BUILD_OPUS)
        self.assertIn("$OPUS_COMMON_ARGS", BUILD_OPUS)
        self.assertIn("$OPUS_OSCE_ARGS", BUILD_OPUS)
        self.assertIn("$OPUS_OSCE_C_FLAGS", BUILD_OPUS)

    def test_floating_point_and_no_fast_math(self):
        code = "\n".join(l for l in BUILD_OPUS.splitlines() if not l.lstrip().startswith("#"))
        self.assertNotIn("FIXED_POINT", code)
        self.assertNotIn("-ffast-math", code)

    def test_osce_flags_go_to_the_release_flags(self):
        self.assertRegex(BUILD_OPUS, r'-DCMAKE_C_FLAGS_RELEASE="[^"]*\$OPUS_OSCE_C_FLAGS')

    def test_the_submodule_is_never_written(self):
        self.assertIn("--exclude=.git", BUILD_OPUS)
        self.assertNotIn('fetch-models.sh" "$OPUS_SRC"', BUILD_OPUS)

    def test_main_library_no_longer_defines_fixed_point(self):
        cmake = (JNI / "CMakeLists.txt").read_text()
        self.assertIsNone(re.search(r"\bFIXED_POINT\b", cmake))


class JniRegistration(unittest.TestCase):
    def test_native_table_matches_the_java_class(self):
        cc = (JNI / "ffmpeg_jni.cc").read_text()
        java = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/nanogram/NanogramOpusDecoder.java").read_text()
        self.assertIn('"org/telegram/messenger/nanogram/NanogramOpusDecoder"', cc)
        for name, sig in (
            ("nativeCreate", "([BIZ)J"),
            ("nativeDecode", "(JLjava/nio/ByteBuffer;ILjava/nio/ByteBuffer;I)I"),
            ("nativeReset", "(J)Z"),
            ("nativeRelease", "(J)V"),
        ):
            self.assertIn('{"%s", "%s"' % (name, sig), cc)
            self.assertRegex(java, r"private static native \w+ %s\(" % name)

    def test_the_existing_exoplayer_signature_is_untouched(self):
        cc = (JNI / "ffmpeg_jni.cc").read_text()
        self.assertIn('{"ffmpegInitialize", "(Ljava/lang/String;[BZII)J"', cc)


if __name__ == "__main__":
    unittest.main()
