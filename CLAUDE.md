# Working on this repo

This repo drives one real TV: a Sony BRAVIA KD-43X80L (Google TV, Android 12, API 31, armeabi-v7a, ~3 GB RAM,
UI at 1920x1080). It holds a debloat setup and a custom tvOS-style launcher (`launcher/`, package `com.viraj.tvhome`).
The user's own instructions are in `~/.claude/CLAUDE.md`; this file adds what is specific to this repo.

## Every change must reach the TV

A change is not done until it is running on the TV and checked there:

1. Make the change.
2. Deploy it (below). ADB stays on, so this works any time the TV is on. A launcher deploy restarts the
   launcher and brings the TV to the home screen, so first check what's in front (see Verifying). If it isn't
   `com.viraj.tvhome`, someone may be watching: ask the user before deploying.
3. Verify on the TV with key presses and a screenshot you actually look at (below).
4. Update `README.md` if the user-facing behaviour changed.
5. When the user is happy, commit and push (the repo is public; see "Never commit").

## Connecting

All scripts find the TV through `.tv-ip` (git-ignored) via `tools/tv-env.sh`. You don't need the IP.

- Script says "No TV address yet" or "Can't reach the TV": run `python3 tools/find-tv.py`. It scans the local
  network for port 5555, writes `.tv-ip`, and prints the model (expect `BRAVIA 4K VH22`). If nothing is found,
  ask the user whether the TV is on.
- Script says the TV is asking to "Allow USB debugging?": ask the user to accept it on the TV and tick
  "Always allow". This happens on a new computer, after `adb kill-server` with a new key, or when this Mac
  hasn't connected for 7 days (Android's default expiry).
- For raw adb commands, connect first, from the repo root, in the same shell call (the working directory and
  env don't persist between calls):
  `cd <repo root> && export ANDROID_SERIAL=$(cat .tv-ip):5555 && adb connect $ANDROID_SERIAL >/dev/null && adb shell ...`

## Deploying

| What changed | Command |
| --- | --- |
| Launcher code or resources | `./build-and-install.sh` (build, `pm install -r`, keeps the user's layout and wallpaper) |
| Wallpapers | `tools/push-wallpapers.sh <file>` (no args = everything in `wallpapers/`) |
| Disable a package | `tools/disable.sh <pkg>` (one `pm` call per package; logs to `disabled-by-us.txt`, which `tools/restore.sh` reads), then add it to `debloat-list.txt` with a reason |
| Re-enable a package | `adb shell pm enable <pkg>`, and remove it from `disabled-by-us.txt` and `debloat-list.txt` |

Build needs JDK 17 and the Android SDK (platform 34). `launcher/local.properties` (git-ignored) points at the SDK:
`sdk.dir=/opt/homebrew/share/android-commandlinetools` on the user's Mac.

## Verifying

After every launcher deploy, check all of these:

1. `./build-and-install.sh` exits 0, prints two `Success` lines and ends with `Running on <ip>:5555`.
2. What's in front is `com.viraj.tvhome/.MainActivity`:
   `adb shell dumpsys activity activities | grep -o 'mFocusedApp=[^ ]* [^ ]* [^ ]*'`
3. The remote-key service is bound: `adb shell dumpsys accessibility | grep "Bound services"` lists "Home remote keys".
4. A screenshot looks like `docs/screenshots/home-dock.png`. The layout should match; the background won't if
   the user has a wallpaper selected (that shot uses "None": black with the focused app's colour glow).

Tools:

- Screenshot: `tools/shot.sh <name>` writes `shots/<name>.png` (git-ignored); then read the image.
- Keys: `adb shell input keyevent KEYCODE_DPAD_DOWN` (also `_UP/_LEFT/_RIGHT/_CENTER`, `KEYCODE_BACK`, `KEYCODE_HOME`).
  Hold OK: `adb shell input keyevent --longpress KEYCODE_DPAD_CENTER`. Pause ~0.7 s between keys.
- Navigate step by step and screenshot before pressing OK: a long blind key sequence can land on a tile and
  launch an app. If one opens by mistake, press Home and `adb shell am force-stop <pkg>`.
- After 10 idle minutes the Google screensaver (`dreamx`) covers everything and swallows arrow keys; press
  `KEYCODE_BACK` to dismiss it.
- Screenshots of HDMI, Live TV, Netflix and other DRM apps come back 0 bytes. That is the TV, not a bug.
- Smoothness: `adb shell dumpsys gfxinfo com.viraj.tvhome reset`, do the motion, then read `dumpsys gfxinfo`.
- Memory: `adb shell dumpsys meminfo com.viraj.tvhome | grep "TOTAL PSS"` (in KB; ~55-70 MB is normal).
- Remote buttons: an injected key is not proof for anything handled by `KeyService`; ask the user to press the real button.

## The user's state on the TV (don't clobber it)

The launcher keeps the user's choices in `shared_prefs/MainActivity.xml`
(read: `adb shell run-as com.viraj.tvhome cat shared_prefs/MainActivity.xml`):
`order` (app order), `hidden` (hidden apps), `wallpaper` (selected file name), and `zone` / `dock` / `grid`
(last focus, which changes with every key press and needn't be restored).

- If you change the wallpaper, app order or hidden apps to test something, put the user's choice back afterwards.
- `adb shell pm clear com.viraj.tvhome` or uninstalling the launcher wipes the layout AND the wallpapers on the TV.
  Ask first; afterwards run `tools/push-wallpapers.sh`.
- Wallpapers live in `/sdcard/Android/data/com.viraj.tvhome/files/wallpapers/` on the TV.

## Code map (`launcher/app/src/main/`)

- `MainActivity.java`: everything on screen. Focus zones (top bar / dock / grid) and D-pad rules in `moveFocus`;
  `render` places focus, glow, scroll; tile menu (Move / Hide / Hidden apps), Inputs and Wallpaper menus share
  `showMenu`; app order and hiding in `loadApps` / `showApps` / `saveLayout`; wallpapers in `loadWallpaper`,
  `startVideo`, `stopVideo`; scroll is a critically damped spring (`SMOOTH_STIFFNESS`).
- `CardDrawable` + `Squircle`: tiles drawn as anti-aliased continuous-curvature squircles, cached per app at focus size.
- `GlassDrawable`: the focus sheen, the focus ring (tinted with the app's colour, see `RING_WHITE_MIX`; its colour
  follows the glow animation in `applyGlow`) and the white move-mode ring.
- `Inputs.java`: HDMI / AV / Live TV list from the TV input framework.
- `KeyService.java`: accessibility service that opens Settings for the remote's gear button (scan code 646).
- Fonts (`assets/fonts`): Inter for the grid's app names, Outfit for the clock, greeting, pills and menus.
  The dock shows no names, like the tvOS top row.
- `res/values/config.xml`: starting dock apps, Settings-button scan codes, the TV app package.
- Dock = first 6 visible apps; `COLUMNS = 6`; `FOCUS_SCALE = 1.2`.

## Hard-won TV quirks (keep these in mind)

- The renderer crops a scaled view to its unscaled bounds even with `clipChildren=false`: scale the whole tile
  (with spare padding), never just the image.
- View outline clipping is jagged and can't take custom paths: draw shapes yourself (see `CardDrawable`).
- `GridLayoutManager` inside the page `ScrollView` lays out only one screen of rows unless extra layout space
  is requested (see `calculateExtraLayoutSpace`).
- RetroPal claims every VIEW intent, so input switches must target `com.sony.dtv.tvx` explicitly.
- Video wallpapers must release `MediaPlayer` in `onPause`, or apps opened from Home lose the hardware decoder.
- Keep it light: no blur, no network polling, no extra processes.
- Video wallpapers are 1080p60 HEVC to stay light; Apple's 4K 240 fps aerials are converted with the ffmpeg
  command in the README. 4K playback has not been tried on this TV.
- The first install sometimes drops the accessibility service from settings; `build-and-install.sh` retries enabling it.
  Check with `adb shell dumpsys accessibility | grep "Bound services"`.

## Debloat safety

- Never uninstall system packages; only `pm disable-user --user 0`, one package per shell call.
- Never launch anything named factory / fac / AT / factorymenu / servicemode to see what it does.
  Use `adb shell dumpsys package <pkg>` instead.
- Ask the user before disabling anything they might use (apps, voice, Cast, AirPlay, updates, DLNA).
- Must stay enabled: `com.sony.dtv.tvx` and `com.sony.dtv.tvinput.*` (HDMI, tuner), Bluetooth, remote services,
  keyboards, Play Store, Play Services, `com.android.tv.settings`.
- `com.google.android.apps.tv.launcherx` and `com.google.android.tungsten.setupwraith` are disabled on purpose:
  with either enabled, Home stops opening this launcher.

## Never commit

`backup/` (serial number, Android ID, Bluetooth address), `shots/` (shows the Wi-Fi name), `.tv-ip`,
Apple's aerial videos (`coast`, `peaks`, `ridge`, `sunrise` .mp4), build output. `.gitignore` covers these;
don't add the TV's IP, Wi-Fi name or IDs to tracked files either. Screenshots show the Wi-Fi name in the
top bar: don't quote it in commit messages or anything else that leaves this Mac.
