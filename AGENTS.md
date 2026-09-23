# Pocket — Agent Working Rules

Mandatory for every code change, no exceptions. Production ships tomorrow;
nothing broken may land.

## Verify-every-change pipeline

Run this full pipeline after ANY app-code change, before reporting done:

1. Build: `./gradlew assembleDebug` from repo root — must end `BUILD SUCCESSFUL`.
2. Uninstall device copy: `adb -s emulator-5554 uninstall in.marxen.pocket`.
3. Install + launch: `./gradlew installDebug`, then
   `adb -s emulator-5554 shell am start -n in.marxen.pocket/.MainActivity`.
4. Drive every touched screen with scripted taps
   (`adb -s emulator-5554 shell input tap X Y` / `input text` /
   `input keyevent 4` for back), locating targets via fresh
   `uiautomator dump` + bounds parsing each time — never reuse stale coordinates.
5. Assert state from dump text/bounds after each action (screen reached, values
   correct, no crash/blank). Fresh installs start at Welcome — pass it with
   Skip (540,1790) and re-seed test data when the change needs it.
6. Rendering check on touched screens: `dumpsys gfxinfo in.marxen.pocket reset`,
   interact, confirm `Total frames rendered` freezes within a few frames and
   `Slow UI thread` stays 0. Unfrozen counters = investigate before finishing.

## Environment notes

- Emulator: Pixel_6 AVD, usually `emulator-5554`. If absent, start it and wait
  for `getprop sys.boot_completed` = 1. Boot: `nohup emulator -avd Pixel_6`.
- On Git Bash, prefix adb file commands with `MSYS_NO_PATHCONV=1` and quote
  Windows paths, or remote/local paths get mangled.
- Bottom tabs (1080x2400): Home (135,2278), Calendar (405,2278),
  Insights (675,2278), Settings (945,2278). Re-dump if layout shifts.

## Safety

- Never commit, push, tag, or create PRs/releases unless explicitly asked.
- Never force-push, never `--no-verify`.
- Never confirm destructive dialogs (e.g. Manage Data delete) — open and cancel.
- Never log or commit secrets; `*.jks` / `*.apk` are gitignored (APKs ship via
  GitHub releases, not the repo).
- Evidence before claims: no "fixed" without the dump or build output proving it.
