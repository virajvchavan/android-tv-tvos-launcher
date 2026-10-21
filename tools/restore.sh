#!/bin/bash
# Undoes everything: re-enables every package in disabled-by-us.txt,
# gives Home back to the stock Google TV launcher and resets DNS.
# Usage: ANDROID_SERIAL=<tv ip>:5555 tools/restore.sh
set -u
DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG="$DIR/disabled-by-us.txt"
if [ -f "$LOG" ]; then
  while read -r p; do
    [ -z "$p" ] && continue
    echo "$p: $(adb shell pm enable --user 0 "$p" 2>&1)"
  done < "$LOG"
fi
adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity

SVC=com.viraj.tvhome/com.viraj.tvhome.KeyService
CUR=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
REST=$(echo "$CUR" | tr ':' '\n' | grep -vx "$SVC" | grep -v '^null$' | paste -sd: -)
if [ -z "$REST" ]; then
  adb shell settings delete secure enabled_accessibility_services
  adb shell settings put secure accessibility_enabled 0
else
  adb shell settings put secure enabled_accessibility_services "$REST"
fi
adb shell settings put global private_dns_mode opportunistic
adb shell settings delete global private_dns_specifier
echo "Done. To also remove the custom launcher: adb uninstall com.viraj.tvhome"
