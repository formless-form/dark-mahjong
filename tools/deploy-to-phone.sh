#!/usr/bin/env bash
# Build a small minified APK, sign it with the local debug key, install it on the connected
# phone and launch it. Usage: tools/deploy-to-phone.sh [--no-launch] [adb-serial]
# --no-launch installs without opening the app (e.g. while the phone is in use).
# The build runs memory-capped outside the calling process (Gradle peaks around 5.5 GB).
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)apksigner"
LAUNCH=1
if [ "${1:-}" = "--no-launch" ]; then LAUNCH=0; shift; fi
SERIAL="${1:-100.116.67.56:5555}"
OUT=app/build/outputs/apk/release

systemd-run --user --collect --wait --pipe -p MemoryMax=6G -p MemorySwapMax=1G \
    --working-directory="$PWD" --unit=mahjong-build "$PWD/gradlew" assembleRelease -PperfHooks=true --no-daemon --console=plain -q

"$APKSIGNER" sign --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/app-release-debugsigned.apk" "$OUT/app-release-unsigned.apk"
ls -l "$OUT/app-release-debugsigned.apk"

"$ADB" -s "$SERIAL" install -r "$OUT/app-release-debugsigned.apk"
if [ "$LAUNCH" = 1 ]; then
    "$ADB" -s "$SERIAL" logcat -c
    "$ADB" -s "$SERIAL" shell monkey -p com.formlessform.darkmahjong -c android.intent.category.LAUNCHER 1 > /dev/null
fi
