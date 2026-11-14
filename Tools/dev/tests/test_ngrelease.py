import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ngrelease as r  # noqa: E402

BADGING = """package: name='org.klaipher.nanogram' versionCode='33000009' versionName='13.2.2' compileSdkVersion='37'
sdkVersion:'24'
targetSdkVersion:'36'
"""

CHANGELOG = """# Changelog

## Unreleased

### Updates
- in progress

## 13.2.2 (202611141200)
- shipped thing

## 13.2.1 (202610010000)
- old
"""


class Naming(unittest.TestCase):
    def test_tag_title_asset(self):
        self.assertEqual(r.tag_name("13.2.2", "202611141200"), "android-v13.2.2-202611141200")
        self.assertEqual(r.release_title("13.2.2", "202611141200"), "Nanogram for Android 13.2.2 (202611141200)")
        self.assertEqual(r.apk_asset_name("13.2.2", "202611141200"), "Nanogram-13.2.2-202611141200.apk")

    def test_apk_url(self):
        self.assertEqual(
            r.apk_url("13.2.2", "202611141200"),
            "https://github.com/klaipher/nanogram-android-releases/releases/download/"
            "android-v13.2.2-202611141200/Nanogram-13.2.2-202611141200.apk")

    def test_bad_build_number(self):
        for bad in ("", "2026", "abcdefghijkl", "2026111412001"):
            with self.assertRaises(ValueError):
                r.tag_name("1.0", bad)


class VersionCode(unittest.TestCase):
    def test_formula(self):
        self.assertEqual(r.version_code("202001010000"), 9)
        self.assertEqual(r.version_code("202001010001"), 19)
        self.assertEqual(r.version_code("202001020000"), 1440 * 10 + 9)

    def test_monotonic(self):
        self.assertLess(r.version_code("202611141200"), r.version_code("202611141201"))
        self.assertLess(r.version_code("202612312359"), 2**31)

    def test_abi_digit(self):
        self.assertEqual(r.version_code("202001010000", 1), 1)


class Manifest(unittest.TestCase):
    def test_fields(self):
        m = r.build_manifest(version_code=33000009, version_name="13.2.2", build="202611141200", sha256="ab" * 32,
                             size=57_500_000, notes="  Faster chats.\nFixed a crash.  \n", min_sdk=24,
                             published_at="2026-11-14T12:00:00Z")
        self.assertEqual(sorted(m), sorted(["versionCode", "versionName", "buildNumber", "apkUrl", "apkSha256",
                                            "apkSize", "notes", "minSdk", "publishedAt"]))
        self.assertEqual(m["notes"], "Faster chats.\nFixed a crash.")
        self.assertEqual(m["minSdk"], 24)
        self.assertTrue(m["apkUrl"].startswith("https://github.com/klaipher/nanogram-android-releases/releases/download/"))
        self.assertEqual(json.loads(r.manifest_json(m)), m)

    def test_notes_capped(self):
        self.assertLessEqual(len(r.clean_notes("x" * 5000)), r.MAX_NOTES)

    def test_sha256_file(self):
        with tempfile.TemporaryDirectory() as d:
            f = Path(d, "a.bin")
            f.write_bytes(b"abc")
            self.assertEqual(r.sha256_file(f), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")

    def test_badging(self):
        b = r.parse_badging(BADGING)
        self.assertEqual(b, {"package": "org.klaipher.nanogram", "versionCode": "33000009", "versionName": "13.2.2", "minSdk": "24"})


class Changelog(unittest.TestCase):
    def test_exact_section(self):
        self.assertEqual(r.extract_changelog(CHANGELOG, "13.2.2", "202611141200"), "- shipped thing")

    def test_unreleased_fallback(self):
        self.assertIn("in progress", r.extract_changelog(CHANGELOG, "13.2.2", "202699999999"))

    def test_missing(self):
        with self.assertRaises(ValueError):
            r.extract_changelog("# Changelog\n", "1", "202611141200")


class GhCommands(unittest.TestCase):
    def test_public(self):
        c = r.gh_public_release(Path("a.apk"), Path("update.json"), Path("n.md"), "13.2.2", "202611141200")
        self.assertEqual(c[:4], ["gh", "release", "create", "android-v13.2.2-202611141200"])
        self.assertIn("--latest", c)
        self.assertEqual(c[c.index("--repo") + 1], "klaipher/nanogram-android-releases")

    def test_private(self):
        c = r.gh_private_release([Path("a.apk"), Path("mapping.txt")], Path("n.md"), "13.2.2", "202611141200", "abc123")
        self.assertEqual(c[c.index("--repo") + 1], "klaipher/nanogram-android")
        self.assertEqual(c[c.index("--target") + 1], "abc123")
        self.assertIn("--latest=false", c)


if __name__ == "__main__":
    unittest.main()
