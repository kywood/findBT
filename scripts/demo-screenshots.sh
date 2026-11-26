#!/usr/bin/env bash
# Takes demo-mode screenshots (#47) from a debug build on a connected phone.
#
#   adb -s R59N300DBHH install -r app/build/outputs/apk/debug/app-debug.apk   (never `gradlew installDebug`)
#   ANDROID_SERIAL=R59N300DBHH scripts/demo-screenshots.sh [out-dir] [locales...]
#
# Default: out-dir shots/demo, locales "en ko". Files: <out>/<locale>/<screen>.png
# adb must be on PATH. In Git Bash on Windows, for example:
#   export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
# Demo mode shows fixed, generically named devices only; nothing is scanned or sent anywhere.
set -euo pipefail

OUT="${1:-shots/demo}"
shift || true
if [ "$#" -gt 0 ]; then LOCALES=("$@"); else LOCALES=(en ko); fi

PKG="com.gamenuri.btscanner24.debug"
ACTIVITY="$PKG/com.gamenuri.btscanner24.MainActivity"
FIND_STATES=(spin walk locked close very_close here found sound lost turn_around far waiting)
ONBOARDING_PAGES=(intro permission permission_legacy denied denied_legacy)
WAIT_SECONDS="${WAIT_SECONDS:-3}"

shot() { # shot <locale> <file-name> <am extras...>
  local locale="$1" name="$2"
  shift 2
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$ACTIVITY" --es demo_locale "$locale" "$@" > /dev/null
  sleep "$WAIT_SECONDS"
  adb exec-out screencap -p > "$OUT/$locale/$name.png"
  echo "$OUT/$locale/$name.png"
}

for locale in "${LOCALES[@]}"; do
  mkdir -p "$OUT/$locale"
  for page in "${ONBOARDING_PAGES[@]}"; do
    shot "$locale" "onboarding-$page" --es demo onboarding --es demo_onboarding "$page"
  done
  shot "$locale" nearby --es demo nearby
  shot "$locale" my-devices --es demo devices
  for screen in settings settings_premium paywall; do
    shot "$locale" "$screen" --es demo "$screen"
  done
  for state in "${FIND_STATES[@]}"; do
    shot "$locale" "find-$state" --es demo find --es demo_find "$state"
  done
done
adb shell am force-stop "$PKG"
