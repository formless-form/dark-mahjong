#!/usr/bin/env bash
# Builds the Play App Bundle, signed with the upload key: ~/.config/dark-mahjong/upload-keystore.p12
# (alias "upload"). Its password comes from $DARK_MAHJONG_UPLOAD_KEY_PASS if set, else the server's
# ~/server_secrets.py, else ~/.config/dark-mahjong/upload-key-pass (a file only you can read). Play
# re-signs with its own key, so a lost upload key can be reset by Play support.
# Runs on the Linux server and in Git Bash on Windows.
#
# Usage: tools/build-play-bundle.sh   ->   dist/DarkMahjong-<version>.aab
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/dm-env.sh

# The Imperial images are never committed (.git/info/exclude) and the game quietly drops Imperial
# without them, so insist on the full set: 12 bodies and 42 faces, each with its surface map, and
# settings-samples.webp (ImperialSettingsSamples, then tools/imperial-samples-webp.sh)
IMPERIAL="app/src/play/assets/imperial"
pictures=$(find "$IMPERIAL" -maxdepth 1 -name '*.webp' ! -name '*-surface.webp' 2>/dev/null | wc -l)
maps=$(find "$IMPERIAL" -maxdepth 1 -name '*-surface.webp' 2>/dev/null | wc -l)
samples=$(find "$IMPERIAL" -maxdepth 1 -name 'settings-samples.webp' 2>/dev/null | wc -l)
[ "$pictures" -eq 55 ] && [ "$maps" -eq 54 ] && [ "$samples" -eq 1 ] || {
    echo "Imperial images incomplete in $IMPERIAL: $pictures of 55 pictures (with settings-samples.webp), $maps of 54 surface maps" >&2
    exit 1
}

KEYSTORE="$HOME/.config/dark-mahjong/upload-keystore.p12"
[ -f "$KEYSTORE" ] || { echo "No upload keystore at $KEYSTORE" >&2; exit 1; }
if [ -z "${DARK_MAHJONG_UPLOAD_KEY_PASS:-}" ]; then
    if [ -f "$HOME/server_secrets.py" ]; then
        DARK_MAHJONG_UPLOAD_KEY_PASS="$(python3 "$HOME/server_secrets.py" get DARK_MAHJONG_UPLOAD_KEY_PASS)"
    elif [ -f "$HOME/.config/dark-mahjong/upload-key-pass" ]; then
        DARK_MAHJONG_UPLOAD_KEY_PASS="$(tr -d '\r\n' < "$HOME/.config/dark-mahjong/upload-key-pass")"
    else
        echo "No upload key password: set DARK_MAHJONG_UPLOAD_KEY_PASS or create ~/.config/dark-mahjong/upload-key-pass" >&2
        exit 1
    fi
fi
export DARK_MAHJONG_UPLOAD_KEY_PASS

# Perf hooks forced off
gradle bundlePlayRelease -PperfHooks=false --no-daemon --console=plain -q

VERSION=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts)
mkdir -p dist
OUT="dist/DarkMahjong-$VERSION.aab"
cp app/build/outputs/bundle/playRelease/app-play-release.aab "$OUT"
# jarsigner reports errors on stdout: show them, and leave no unsigned bundle behind
if ! signed=$("$JARSIGNER" -keystore "$KEYSTORE" -storetype PKCS12 -storepass:env DARK_MAHJONG_UPLOAD_KEY_PASS \
    -sigalg SHA256withRSA -digestalg SHA-256 "$OUT" upload 2>&1); then
    echo "Signing failed: $signed" >&2
    rm -f "$OUT"
    exit 1
fi
"$JARSIGNER" -verify "$OUT" | tail -1
ls -la "$OUT"
