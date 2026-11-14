"""Pure helpers for `ng release` (no I/O besides reading the APK through aapt2). Python stdlib only."""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import re
import shlex
import subprocess
from pathlib import Path

PUBLIC_REPO = "klaipher/nanogram-android-releases"
PRIVATE_REPO = "klaipher/nanogram-android"
MAX_NOTES = 1000
BUILD_RE = re.compile(r"^\d{12}$")


def check_build_number(build: str) -> str:
    if not BUILD_RE.match(build):
        raise ValueError(f"build number must be YYYYMMDDHHMM, got {build!r}")
    return build


def version_code(build: str, abi_digit: int = 9) -> int:
    """Same formula as TMessagesProj_App/build.gradle: minutes since 2020-01-01 UTC of the build number, times 10, plus the ABI digit."""
    t = dt.datetime.strptime(check_build_number(build), "%Y%m%d%H%M")
    return int((t - dt.datetime(2020, 1, 1)).total_seconds() // 60) * 10 + abi_digit


def tag_name(version_name: str, build: str) -> str:
    return f"android-v{version_name}-{check_build_number(build)}"


def release_title(version_name: str, build: str) -> str:
    return f"Nanogram for Android {version_name} ({build})"


def apk_asset_name(version_name: str, build: str) -> str:
    return f"Nanogram-{version_name}-{check_build_number(build)}.apk"


def apk_url(version_name: str, build: str, repo: str = PUBLIC_REPO) -> str:
    return f"https://github.com/{repo}/releases/download/{tag_name(version_name, build)}/{apk_asset_name(version_name, build)}"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def parse_badging(text: str) -> dict[str, str]:
    """Reads package, versionCode, versionName and sdkVersion from `aapt2 dump badging` output."""
    out: dict[str, str] = {}
    m = re.search(r"^package: (.*)$", text, re.M)
    if m:
        for key, val in re.findall(r"(\w+)='([^']*)'", m.group(1)):
            if key in ("name", "versionCode", "versionName"):
                out["package" if key == "name" else key] = val
    m = re.search(r"^(?:min)?[sS]dkVersion:'(\d+)'", text, re.M)
    if m:
        out["minSdk"] = m.group(1)
    return out


def read_badging(aapt2: Path, apk: Path) -> dict[str, str]:
    res = subprocess.run([str(aapt2), "dump", "badging", str(apk)], capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"aapt2 failed: {res.stderr.strip()[:200]}")
    return parse_badging(res.stdout)


def clean_notes(text: str) -> str:
    text = "\n".join(line.rstrip() for line in text.strip().splitlines())
    return text[:MAX_NOTES].rstrip()


def build_manifest(*, version_code: int, version_name: str, build: str, sha256: str, size: int,
                   notes: str, min_sdk: int, published_at: str | None = None) -> dict[str, object]:
    return {
        "versionCode": int(version_code),
        "versionName": version_name,
        "buildNumber": check_build_number(build),
        "apkUrl": apk_url(version_name, build),
        "apkSha256": sha256,
        "apkSize": int(size),
        "notes": clean_notes(notes),
        "minSdk": int(min_sdk),
        "publishedAt": published_at or dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }


def manifest_json(manifest: dict[str, object]) -> str:
    return json.dumps(manifest, indent=2, ensure_ascii=False) + "\n"


def extract_changelog(text: str, version_name: str, build: str) -> str:
    """The CHANGELOG.md section for this release (`## <version> (<build>)...`), else `## Unreleased`."""
    sections = re.split(r"(?m)^(?=## )", text)
    for wanted in (lambda h: version_name in h and build in h, lambda h: h.lower().startswith("## unreleased")):
        for sec in sections:
            head = sec.split("\n", 1)[0]
            if head.startswith("## ") and wanted(head):
                body = sec.split("\n", 1)[1].strip() if "\n" in sec else ""
                if body:
                    return body
    raise ValueError("no matching CHANGELOG.md section")


def gh_public_release(apk: Path, manifest: Path, notes: Path, version_name: str, build: str) -> list[str]:
    return ["gh", "release", "create", tag_name(version_name, build), str(apk), str(manifest),
            "--repo", PUBLIC_REPO, "--title", release_title(version_name, build),
            "--notes-file", str(notes), "--latest"]


def gh_private_release(assets: list[Path], notes: Path, version_name: str, build: str, commit: str) -> list[str]:
    return ["gh", "release", "create", tag_name(version_name, build), *map(str, assets),
            "--repo", PRIVATE_REPO, "--title", release_title(version_name, build),
            "--notes-file", str(notes), "--target", commit, "--latest=false"]


def shell_line(cmd: list[str]) -> str:
    return " ".join(shlex.quote(c) for c in cmd)
