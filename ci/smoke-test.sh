#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK, adds a station by its stream link (a
# test stream served from the CI machine, so the test doesn't depend on any real station being
# on air), and checks that it plays, keeps playing in the background, and answers media buttons.
# Also takes screenshots of Home, which shows real stations from the directory when it's reachable.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.radio
HERE="$(cd "$(dirname "$0")" && pwd)"
FIND="$HERE/find_text.py"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o 'text="[^"]\+"' "$OUT/failure.xml" | head -40 || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  exit 1
}
dump() {
  local i
  for i in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null && adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null
    # The emulator's own apps sometimes freeze while it warms up; wave the "isn't responding"
    # popup away so it doesn't cover the app.
    grep -q "isn&apos;t responding\|isn't responding" "$OUT/$1.xml" || return 0
    # "Close app" ends the frozen app (usually the launcher) so the popup doesn't keep coming
    # back; Android restarts it when it's needed. "Wait" is the fallback.
    echo "(closing a system app that isn't responding)"
    local button
    for button in "Close app" "Wait"; do
      if python3 "$FIND" "$OUT/$1.xml" "$button" exact > /dev/null 2>&1; then
        adb shell input tap $(python3 "$FIND" "$OUT/$1.xml" "$button" exact)
        break
      fi
    done
    sleep 3
  done
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
on_screen() { python3 "$FIND" "$OUT/$1.xml" "$2" "${3:-first}" > /dev/null 2>&1; }
tap() { # tap <dump name> <text> [first|last|exact]
  local xy
  xy=$(python3 "$FIND" "$OUT/$1.xml" "$2" "${3:-first}") || fail "couldn't find '$2' on screen"
  adb shell input tap $xy
}
session() { adb shell dumpsys media_session > "$OUT/session.txt"; }
playing() { session; grep -Eq "\{state=(PLAYING|3)" "$OUT/session.txt"; }
wait_playing() { # wait_playing <seconds>
  local i
  for i in $(seq 1 "$1"); do playing && return 0; sleep 1; done
  return 1
}

echo "--- Serving a test stream to the emulator (10.0.2.2 is the CI machine)"
STREAM_DIR="$OUT/stream"
mkdir -p "$STREAM_DIR"
ffmpeg -hide_banner -loglevel error -f lavfi -i "sine=frequency=440:duration=900" -ac 2 -b:a 96k "$STREAM_DIR/test.mp3"
# Like a real station, it also sends the name of the song that's on.
SONG="Daft Punk - Get Lucky"
python3 "$HERE/icy_server.py" "$STREAM_DIR/test.mp3" 8765 "$SONG" > "$OUT/http.log" 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT

adb wait-for-device
adb install -r "$APK"
# Answer the notification question up front, so its popup doesn't cover the screen.
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
# The emulator's launcher often freezes while it warms up and covers the app with an "isn't
# responding" popup. It isn't needed while the app is on screen (Android restarts it on Home).
adb shell am force-stop com.google.android.apps.nexuslauncher || true
adb logcat -c

echo "--- Opening the app"
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 8
dump home
on_screen home "Vaanalai" || fail "the app didn't open on Home"
shot 1-home
# The directory is on the internet, so it's only checked, not required.
for i in 1 2 3 4 5 6; do
  if on_screen home "Popular" && ! on_screen home "Couldn't load stations"; then break; fi
  sleep 3; dump home
done
if on_screen home "Couldn't load stations"; then
  echo "::warning::The station directory couldn't be reached from the emulator; skipping that part."
else
  shot 1-home
fi

echo "--- Browsing a genre"
tap home "Tamil" exact
sleep 6
dump genre
if ! on_screen genre "Back"; then
  # A system "isn't responding" popup can swallow the tap; dump() has waved it away by now.
  echo "(the tap didn't reach the app; trying once more)"
  dump home-retry
  tap home-retry "Tamil" exact
  sleep 6
  dump genre
fi
on_screen genre "Back" || fail "the genre page didn't open"
shot 2-genre
adb shell input keyevent KEYCODE_BACK
sleep 2

echo "--- Adding a station by its stream link"
dump home2
tap home2 "Favourites" exact
sleep 2
dump favs
tap favs "Add a station"
sleep 2
dump add
tap add "Station name"
sleep 1
adb shell input text "Smoke%sTest%sFM"
dump add2
tap add2 "Stream link"
sleep 1
adb shell input text "http://10.0.2.2:8765/test.mp3"
sleep 1
# Hide the keyboard so Save is on screen (only if it is up: otherwise Back would close the dialog).
if adb shell dumpsys input_method | grep -q "mInputShown=true"; then adb shell input keyevent KEYCODE_BACK; fi
sleep 1
dump add3
shot 3-add-station
tap add3 "Save" exact
sleep 3
dump favs2
on_screen favs2 "Smoke Test FM" exact || fail "the added station isn't in Favourites"

echo "--- Playing it"
tap favs2 "Smoke Test FM" exact
wait_playing 30 || fail "the station didn't start playing"
grep -q "test.mp3" "$OUT/http.log" || fail "the app didn't fetch the test stream"
requests_at_start=$(grep -c "GET /test.mp3" "$OUT/http.log" || true)
sleep 2
for i in $(seq 1 10); do dump playing; on_screen playing "Get Lucky" && break; sleep 2; done
if ! on_screen playing "Get Lucky"; then
  echo "Stream server log:"; tail -5 "$OUT/http.log" || true
  session; echo "Media session:"; grep -iE "metadata|description" "$OUT/session.txt" | head -5 || true
  fail "the song the station sent isn't shown"
fi
tap playing "Get Lucky" # the mini player; opens the full player
sleep 2
shot 4-player
dump player
on_screen player "Close player" || fail "the full player didn't open"
# The song's album cover comes from Apple's iTunes search on the internet, so it's only checked.
for i in $(seq 1 10); do on_screen player "Album cover" && break; sleep 2; dump player; done
if on_screen player "Album cover"; then
  echo "PASS: the song's album cover is shown"
  [ "$(grep -c "GET /test.mp3" "$OUT/http.log" || true)" -eq "$requests_at_start" ] || fail "showing the cover restarted the stream"
  shot 4-player
else
  echo "::warning::No album cover appeared for '$SONG' (is iTunes search reachable from the emulator?)"
  adb logcat -d -s Vaanalai:I | tail -10 || true
fi

echo "--- Tapping LIVE jumps back to the live stream"
before=$(grep -c "GET /test.mp3" "$OUT/http.log" || true)
tap player "● LIVE" exact
wait_playing 20 || fail "the station stopped after tapping LIVE"
sleep 2
after=$(grep -c "GET /test.mp3" "$OUT/http.log" || true)
[ "$after" -gt "$before" ] || fail "tapping LIVE didn't reconnect to the stream ($before → $after requests)"

echo "--- Keeps playing in the background"
adb shell input keyevent KEYCODE_HOME
sleep 8
playing || fail "playback stopped when the app went to the background"
adb shell dumpsys notification --noredact > "$OUT/notifications.txt"
grep -q "$PKG" "$OUT/notifications.txt" || fail "no media notification"

echo "--- Media buttons"
adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 3
playing && fail "the pause button didn't pause"
before=$(grep -c "GET /test.mp3" "$OUT/http.log" || true)
adb shell input keyevent KEYCODE_MEDIA_PLAY
wait_playing 20 || fail "the play button didn't resume"
after=$(grep -c "GET /test.mp3" "$OUT/http.log" || true)
[ "$after" -gt "$before" ] || fail "resuming after a pause didn't go back to live"

echo "--- Back in the app after being closed and reopened"
adb shell am force-stop "$PKG"
sleep 2
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 6
dump reopen
on_screen reopen "Smoke Test FM" || fail "the last station wasn't remembered"
shot 5-reopened

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  fail "the app crashed (see logcat.txt)"
fi
echo "SMOKE TEST PASSED"
