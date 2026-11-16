# Shared by the tools/ scripts: finds the SDK and Java on the Linux server or in Git Bash on Windows,
# and runs Gradle (in its own systemd unit where there is one, so a big build can't take the caller down)
SDK="${ANDROID_HOME:-}"
if [ -z "$SDK" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then SDK="$HOME/Android/Sdk"; else SDK="${LOCALAPPDATA:-}/Android/Sdk"; fi
fi
SDK="${SDK//\\//}"
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)"
if [ -f "${BUILD_TOOLS}apksigner.bat" ]; then APKSIGNER="${BUILD_TOOLS}apksigner.bat"; else APKSIGNER="${BUILD_TOOLS}apksigner"; fi
if [ -f "${BUILD_TOOLS}zipalign.exe" ]; then ZIPALIGN="${BUILD_TOOLS}zipalign.exe"; else ZIPALIGN="${BUILD_TOOLS}zipalign"; fi
if [ -f "$SDK/platform-tools/adb.exe" ]; then ADB="$SDK/platform-tools/adb.exe"; else ADB="$SDK/platform-tools/adb"; fi
# Android Studio's JDK on Windows, where java on PATH is too old
if [ -z "${JAVA_HOME:-}" ] && [ -d "/c/Program Files/Android/Android Studio/jbr" ]; then
    export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
fi
JARSIGNER="jarsigner"
[ -n "${JAVA_HOME:-}" ] && JARSIGNER="$JAVA_HOME/bin/jarsigner"

gradle() {
    if command -v systemd-run > /dev/null; then
        systemd-run --user --collect --wait --pipe --working-directory="$PWD" --unit=mahjong-build "$PWD/gradlew" "$@"
    else
        ./gradlew "$@"
    fi
}
