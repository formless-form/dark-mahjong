#!/usr/bin/env bash
# Build a small minified APK, sign it with the local debug key, install it on the connected
# phone and launch it. Usage: tools/deploy-to-phone.sh [adb-serial]
# The build runs memory-capped outside the calling process (Gradle peaks around 5.5 GB).
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)apksigner"
SERIAL="${1:-100.116.67.56:5555}"
OUT=app/build/outputs/apk/release

systemd-run --user --collect --wait --pipe -p MemoryMax=6G -p MemorySwapMax=1G \
    --working-directory="$PWD" --unit=mahjong-build "$PWD/gradlew" assembleRelease --no-daemon --console=plain -q

"$APKSIGNER" sign --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/app-release-debugsigned.apk" "$OUT/app-release-unsigned.apk"
ls -l "$OUT/app-release-debugsigned.apk"

"$ADB" -s "$SERIAL" install -r "$OUT/app-release-debugsigned.apk"
"$ADB" -s "$SERIAL" logcat -c
"$ADB" -s "$SERIAL" shell monkey -p com.formlessform.darkmahjong -c android.intent.category.LAUNCHER 1 > /dev/null
