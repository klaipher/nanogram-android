#!/usr/bin/env bash
# Regenerate only the grammar asset; the submodule remains unchanged.
# Uses the generator of the shared klaipher/libprisma submodule. PRISMA_JOIN_ALIASES=1 keeps every Prism alias
# (space-separated) in the alias field, which PrismaTokenStyles needs; without it the output is the iOS grammars.dat.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNI_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PRISMA_SRC="${PRISMA_SOURCE_DIR:-$JNI_DIR/third_party/libprisma}"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/prisma-grammars.XXXXXX")"
trap 'rm -rf -- "$WORK_DIR"' EXIT
cp "$PRISMA_SRC/generate.js" "$PRISMA_SRC/package.json" \
    "$PRISMA_SRC/package-lock.json" "$WORK_DIR/"
cp -R "$PRISMA_SRC/components" "$WORK_DIR/"
mkdir -p "$WORK_DIR/libprisma"
cd "$WORK_DIR"
npm ci --ignore-scripts --no-audit --no-fund
PRISMA_JOIN_ALIASES=1 npm start
ASSET_DIR="$JNI_DIR/../src/main/assets"
mkdir -p "$ASSET_DIR"
cp "$WORK_DIR/libprisma/grammars.dat" "$ASSET_DIR/grammars.dat"
echo "Updated $ASSET_DIR/grammars.dat"
