#!/usr/bin/env bash
# Builds the release APK and installs it on the USB-connected phone.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew -q assembleRelease
adb install -r build/outputs/apk/release/lector-release.apk
