#!/usr/bin/env bash
# Raw captures for the store screenshots (#14): the five screens of the store set, in the six app
# languages, from the debug build's demo mode (#47). The designer's template
# (project files design/store-v3/screenshot-template.html) puts the headline and frame around them.
#
#   adb -s R59N300DBHH install -r app/build/outputs/apk/debug/app-debug.apk
#   scripts/store-captures.sh [out-dir]
#
# Default out-dir: shots/store. Files: <out>/<lang>/{find,mydevices,found,nearby,spin,map}.png, with the
# language folders named as in design/store-v3/copy.json (en ko ja es pt de).
#
# - Runs on the Galaxy A50 only and says so: every adb call names its serial. Never `gradlew installDebug`.
# - Changes nothing on the phone: no settings, no permissions, no app data. It only starts the debug app
#   with demo extras and reads the screen.
# - Demo mode shows fixed, generically named devices; nothing is scanned or sent anywhere, so the
#   pictures hold no real device names.
# adb must be on PATH. In Git Bash on Windows, for example:
#   export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
set -euo pipefail

SERIAL="R59N300DBHH" # Galaxy A50. The Z Flip3 is the owner's own phone and is never used.
OUT="${1:-shots/store}"
PKG="com.gamenuri.btscanner24.debug"
ACTIVITY="$PKG/com.gamenuri.btscanner24.MainActivity"
WAIT_SECONDS="${WAIT_SECONDS:-4}"

# Folder name (as in copy.json) and the language tag the app is started with.
LANGS=("en:en" "ko:ko" "ja:ja" "es:es" "pt:pt-BR" "de:de")

if ! adb devices | grep -q "^$SERIAL[[:space:]]*device$"; then
  echo "The Galaxy A50 ($SERIAL) is not connected or not authorised. Nothing was captured." >&2
  exit 1
fi

shot() { # shot <folder> <tag> <file-name> <am extras...>
  local folder="$1" tag="$2" name="$3"
  shift 3
  adb -s "$SERIAL" shell am force-stop "$PKG"
  adb -s "$SERIAL" shell am start -W -n "$ACTIVITY" --es demo_locale "$tag" "$@" > /dev/null
  sleep "$WAIT_SECONDS"
  adb -s "$SERIAL" exec-out screencap -p > "$OUT/$folder/$name.png"
  echo "$OUT/$folder/$name.png"
}

for entry in "${LANGS[@]}"; do
  folder="${entry%%:*}"
  tag="${entry##*:}"
  mkdir -p "$OUT/$folder"
  shot "$folder" "$tag" find --es demo find --es demo_find locked
  shot "$folder" "$tag" mydevices --es demo devices
  shot "$folder" "$tag" found --es demo find --es demo_find found
  shot "$folder" "$tag" nearby --es demo nearby
  shot "$folder" "$tag" spin --es demo find --es demo_find spin
  # Not in the first store set; taken along for the map follow-up shot (#86).
  shot "$folder" "$tag" map --es demo map
done
adb -s "$SERIAL" shell am force-stop "$PKG"
echo "Done: $OUT"
