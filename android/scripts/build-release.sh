#!/usr/bin/env bash
# Builds the release APK, checks that it is signed with the Chrapak key (the fingerprint in
# signing/release-cert.sha256) and that it carries the YAMNet model, then copies it to
# dist/chrapak-<version>.apk. Refuses to produce an APK that would not install over the app.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew -q :app:assembleRelease
APK=app/build/outputs/apk/release/app-release.apk

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
TOOLS="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"

EXPECTED="$(tr -d ' \n' < signing/release-cert.sha256 | tr 'a-f' 'A-F')"
ACTUAL="$("$TOOLS/apksigner" verify --print-certs "$APK" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1 | tr 'a-f' 'A-F')"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "Wrong signing key: expected $EXPECTED, got $ACTUAL" >&2
  exit 1
fi
LISTING="$(unzip -l "$APK")"
case "$LISTING" in *assets/yamnet.tflite*) ;; *) echo "The APK has no YAMNet model" >&2; exit 1 ;; esac

VERSION="$("$TOOLS/aapt2" dump badging "$APK" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
mkdir -p dist
cp "$APK" "dist/chrapak-$VERSION.apk"
echo "dist/chrapak-$VERSION.apk (signed with the Chrapak key, model included)"
