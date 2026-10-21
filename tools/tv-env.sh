# Sourced by the other scripts: points adb at the TV and stops early with a clear message if it can't reach it.
# The TV's address lives in .tv-ip at the repo root (git-ignored); tools/find-tv.py writes it.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -z "${ANDROID_SERIAL:-}" ]; then
  if [ ! -s "$ROOT/.tv-ip" ]; then
    echo "No TV address yet. Run: python3 tools/find-tv.py   (or: echo <tv-ip> > .tv-ip)" >&2
    exit 1
  fi
  export ANDROID_SERIAL="$(tr -d '[:space:]' < "$ROOT/.tv-ip"):5555"
fi
# adb connect waits over a minute on a dead address; a 2-second socket check fails fast instead.
if ! python3 -c "import socket,sys; h,p=sys.argv[1].rsplit(':',1); s=socket.socket(); s.settimeout(2); sys.exit(s.connect_ex((h,int(p))))" "$ANDROID_SERIAL"; then
  echo "Can't reach the TV at $ANDROID_SERIAL. Is it on? If its IP changed, run: python3 tools/find-tv.py" >&2
  exit 1
fi
adb connect "$ANDROID_SERIAL" >/dev/null 2>&1 || true
case "$(adb -s "$ANDROID_SERIAL" get-state 2>&1)" in
  device) ;;
  *unauthorized*)
    echo "The TV is asking to 'Allow USB debugging?'. Accept it on the TV (tick 'Always allow'), then run this again." >&2
    exit 1 ;;
  *)
    echo "Can't reach the TV at $ANDROID_SERIAL. Is it on? If its IP changed, run: python3 tools/find-tv.py" >&2
    exit 1 ;;
esac
