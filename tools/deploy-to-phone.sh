#!/usr/bin/env bash
# Builds the side-by-side test app ("Dark Mahjong Test", perf hooks on), signs it with the shared debug
# key so it installs over the Pixel's copy, installs it, and opens it unless --no-launch.
# Usage: tools/deploy-to-phone.sh [--no-launch] [--via-ssh HOST]
#   --via-ssh HOST: the phone is plugged into HOST, a Windows PC (adb.exe at its default SDK path)
# Runs on the Linux server and in Git Bash on Windows.
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/dm-env.sh
LAUNCH=1
VIA=""
while [ $# -gt 0 ]; do
    case "$1" in
        --no-launch) LAUNCH=0 ;;
        --via-ssh) VIA="$2"; shift ;;
        *) echo "Unknown option $1" >&2; exit 1 ;;
    esac
    shift
done
# The key the Pixel's test app is signed with (the server's debug key; on the desktop, its copy)
KEYSTORE="${DM_DEBUG_KEYSTORE:-}"
if [ -z "$KEYSTORE" ]; then
    if [ -f "$HOME/.android/dm-server-debug.keystore" ]; then KEYSTORE="$HOME/.android/dm-server-debug.keystore"; else KEYSTORE="$HOME/.android/debug.keystore"; fi
fi
OUT=app/build/outputs/apk/play/release
PKG=com.formlessform.darkmahjong.test

gradle assemblePlayRelease -PperfHooks=true -PsideBySide=true --console=plain -q
"$ZIPALIGN" -f -p 4 "$OUT/app-play-release-unsigned.apk" "$OUT/aligned.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/dm-test.apk" "$OUT/aligned.apk"
ls -l "$OUT/dm-test.apk"

if [ -n "$VIA" ]; then
    REMOTE_ADB="& \"\$env:LOCALAPPDATA\\Android\\Sdk\\platform-tools\\adb.exe\""
    scp -q "$OUT/dm-test.apk" "$VIA:dm-test.apk"
    ssh "$VIA" "$REMOTE_ADB install -r dm-test.apk; Remove-Item dm-test.apk"
    [ "$LAUNCH" = 1 ] && ssh "$VIA" "$REMOTE_ADB shell monkey -p $PKG -c android.intent.category.LAUNCHER 1" > /dev/null
else
    "$ADB" install -r "$OUT/dm-test.apk"
    [ "$LAUNCH" = 1 ] && "$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null
fi
exit 0
