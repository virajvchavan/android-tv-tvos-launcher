#!/bin/bash
# Usage: tools/shot.sh <name>   -> shots/<name>.png
DIR="$(cd "$(dirname "$0")/.." && pwd)"
adb exec-out screencap -p > "$DIR/shots/$1.png"
ls -l "$DIR/shots/$1.png" | awk '{print $5" bytes  "$9}'
