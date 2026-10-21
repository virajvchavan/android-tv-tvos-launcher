#!/bin/bash
# Builds the launcher, installs it on the TV and makes it the Home app.
# Usage: ANDROID_SERIAL=<tv ip>:5555 ./build-and-install.sh
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
PKG=com.viraj.tvhome

cd "$DIR/launcher"
./gradlew -q assembleDebug

# Push + pm install instead of `adb install`, which can hang on a confirmation screen.
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/tvhome.apk
adb shell pm install -r /data/local/tmp/tvhome.apk
adb shell rm /data/local/tmp/tvhome.apk

adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION

# Key service for remote buttons the stock launcher used to handle; added to any services already on.
# Retried because Android drops a just-installed service from the list until it has scanned the package.
SVC=$PKG/$PKG.KeyService
for attempt in 1 2 3 4 5; do
  CUR=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
  case "$CUR" in
    *"$SVC"*) break ;;
    null|"") adb shell settings put secure enabled_accessibility_services "$SVC" ;;
    *) adb shell settings put secure enabled_accessibility_services "$CUR:$SVC" ;;
  esac
  adb shell settings put secure accessibility_enabled 1
  sleep 2
done
adb shell cmd package set-home-activity $PKG/.MainActivity
adb shell am start -n $PKG/.MainActivity
