#!/usr/bin/env bash
# Saves ImperialSettingsSamples' picture as lossless WebP in the Imperial images' folder (keeping
# colour under transparent pixels), checking every pixel round-trips before replacing the old one.
set -euo pipefail
cd "$(dirname "$0")/.."
SRC="app/build/imperial-samples/settings-samples.png"
DEST="app/src/play/assets/imperial/settings-samples.webp"
[ -f "$SRC" ] || { echo "No $SRC: run ./gradlew testPlayDebugUnitTest --tests '*ImperialSettingsSamples*' first" >&2; exit 1; }
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
python3 - "$SRC" "$WORK/out.webp" <<'PY'
import sys
from PIL import Image, ImageChops
src = Image.open(sys.argv[1]).convert("RGBA")
src.save(sys.argv[2], "WEBP", lossless=True, quality=100, method=6, exact=True)
dec = Image.open(sys.argv[2]).convert("RGBA")
if src.size != dec.size or any(hi for _, hi in ImageChops.difference(src, dec).getextrema()):
    sys.exit("The WebP doesn't decode to the same pixels: not replacing")
PY
mv "$WORK/out.webp" "$DEST"
echo "$DEST: $(stat -c %s "$DEST") bytes, lossless, every pixel as drawn"
