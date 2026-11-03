#!/usr/bin/env bash
# Builds the APK for a GitHub release into build/, and prints its SHA-256 for the release notes. Refuses an APK that
# is not signed with the release key or that can reach the network. Needs apksigner and apkanalyzer on the PATH.
set -euo pipefail
cd "$(dirname "$0")/.."
cert=450bf20de5e88aeb78016e060cfa09b2b42fc269f9db9f10019e8ace7a31f380
version=$(grep -oP 'versionName = "\K[^"]+' build.gradle.kts)
./gradlew -q clean testDebugUnitTest assembleRelease
apk=build/outputs/apk/release/lector-release.apk
apksigner verify --print-certs "$apk" | grep -Fx "V2 Signer: certificate SHA-256 digest: $cert" > /dev/null ||
  { echo "Not signed with the release key" >&2; exit 1; }
# Apart from the check below: a failed apkanalyzer must stop the script, not read as "no INTERNET".
permissions=$(apkanalyzer manifest permissions "$apk")
if grep -Fx android.permission.INTERNET <<< "$permissions"; then
  echo "The APK asks for the network" >&2
  exit 1
fi
cp "$apk" "build/lector-$version.apk"
(cd build && sha256sum "lector-$version.apk")
