#!/usr/bin/env bash
# Builds the Android App Bundle for Google Play and signs it with the upload key.
#
# The upload key lives outside the repo, in ~/.config/dark-mahjong/upload-keystore.p12 (alias
# "upload"); its password is DARK_MAHJONG_UPLOAD_KEY_PASS in the server secrets store
# (~/server_secrets.py). Google Play re-signs the app with its own app signing key, so this key
# only proves an upload came from us; if it is ever lost, Play support can reset it.
#
# Usage: tools/build-play-bundle.sh   ->   dist/DarkMahjong-<version>.aab
set -euo pipefail
cd "$(dirname "$0")/.."

# The Imperial images ship in the Play build only and are never committed (they're kept out by
# .git/info/exclude), so a checkout can lack them: without them the game quietly leaves Imperial
# out, so a release must not be built without the full set: 12 bodies and 42 faces, each with
# its surface map, and the ready-made picture of its tiles for Settings (made by
# ImperialSettingsSamples, a Play-build test)
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
DARK_MAHJONG_UPLOAD_KEY_PASS="$(python3 "$HOME/server_secrets.py" get DARK_MAHJONG_UPLOAD_KEY_PASS)"
export DARK_MAHJONG_UPLOAD_KEY_PASS

# Memory-capped, as for every Gradle build on this server. Perf hooks are forced off, whatever
# gradle.properties says
systemd-run --user --collect --wait --pipe \
    --working-directory="$PWD" --unit=mahjong-build "$PWD/gradlew" bundlePlayRelease -PperfHooks=false --no-daemon --console=plain -q

VERSION=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
mkdir -p dist
OUT="dist/DarkMahjong-$VERSION.aab"
cp app/build/outputs/bundle/playRelease/app-play-release.aab "$OUT"
jarsigner -keystore "$KEYSTORE" -storetype PKCS12 -storepass:env DARK_MAHJONG_UPLOAD_KEY_PASS \
    -sigalg SHA256withRSA -digestalg SHA-256 "$OUT" upload >/dev/null
jarsigner -verify "$OUT" | tail -1
ls -la "$OUT"
