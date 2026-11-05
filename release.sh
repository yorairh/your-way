#!/usr/bin/env bash
# Builds a signed release APK and publishes it as a GitHub release.
# Usage: ./release.sh "Release notes"      (version comes from android/app/build.gradle.kts; commit and push first)
set -euo pipefail
cd "$(dirname "$0")"
[ -f "$HOME/.yourway-release/signing.properties" ] || { echo "Missing ~/.yourway-release/signing.properties (the signing key)"; exit 1; }
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Android/Android Studio/jbr}"
version=$(grep -o 'versionName = "[^"]*"' android/app/build.gradle.kts | cut -d'"' -f2)
(cd android && ./gradlew testDebugUnitTest assembleRelease -q)
apk="$HOME/.yourway-build/app/outputs/apk/release/app-release.apk"
out="$(mktemp -d)/YourWay-$version.apk"
cp "$apk" "$out"
gh release create "v$version" "$out" --prerelease --title "Your Way $version" --notes "${1:-Your Way $version}"
echo "https://github.com/yorairh/your-way/releases/download/v$version/YourWay-$version.apk"
