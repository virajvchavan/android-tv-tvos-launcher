#!/bin/bash
# Usage: tools/disable.sh <package> [<package> ...]
# Runs one pm call per package: chained pm commands can stop after the first.
set -u
DIR="$(cd "$(dirname "$0")/.." && pwd)"
source "$DIR/tools/tv-env.sh"
LOG="$DIR/disabled-by-us.txt"
touch "$LOG"
for p in "$@"; do
  out=$(adb shell pm disable-user --user 0 "$p" 2>&1)
  echo "$p: $out"
  if echo "$out" | grep -q "new state: disabled-user"; then
    grep -qx "$p" "$LOG" || echo "$p" >> "$LOG"
  fi
done
