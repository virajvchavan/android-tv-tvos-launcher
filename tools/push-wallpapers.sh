#!/bin/bash
# Copies wallpapers to the TV, where the Wallpaper pill lists them by file name.
# Usage: ANDROID_SERIAL=<tv ip>:5555 tools/push-wallpapers.sh [file ...]   (no files = everything in wallpapers/)
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEST=/sdcard/Android/data/com.viraj.tvhome/files/wallpapers
adb shell mkdir -p "$DEST"
if [ $# -eq 0 ]; then
  set -- "$DIR"/wallpapers/*
fi
for f in "$@"; do
  adb push "$f" "$DEST/"
done
