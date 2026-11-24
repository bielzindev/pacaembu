#!/usr/bin/env bash
# Installs the debug APK on a running emulator, walks through the main screens and saves
# screenshots in shots/. Used by .github/workflows/ui-check.yml.
set -u
P=com.pacaembu.launcher
mkdir -p shots
shot() { sleep "${2:-3}"; adb exec-out screencap -p > "shots/$1.png"; echo "shot $1"; }
start() { adb shell am start -n "$P/$1" >/dev/null; }

adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell wm size; adb shell wm density
adb shell cmd package set-home-activity "$P/com.pacaembu.home.HomeActivity"
adb shell input keyevent KEYCODE_HOME
shot 01-home 12
adb shell input swipe 900 1200 150 1200 250; shot 02-page2
adb shell input keyevent KEYCODE_HOME; shot 03-back-to-page1 2
adb shell input swipe 150 1200 950 1200 250; shot 04-spotlight
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input swipe 540 300 540 1700 500; shot 05-notification-center
adb shell input keyevent KEYCODE_BACK; sleep 2
# Pixel 6 grid: column centres 135/405/675/945, first row icon centre y=248, rows 312px apart.
touch() { adb shell input motionevent "$1" "$2" "$3"; }
glide() { # glide x1 y1 x2 y2: move the held finger in small steps
  for i in 1 2 3 4 5 6 7 8; do touch MOVE $(( $1 + ($3 - $1) * i / 8 )) $(( $2 + ($4 - $2) * i / 8 )); sleep 0.03; done
}
# long press Calendar -> edit mode with the icon lifted under the finger
touch DOWN 405 248; sleep 1.3
adb exec-out screencap -p > shots/06-edit-mode.png; echo "shot 06-edit-mode"
# drag it to the end of the first row and let go
glide 405 248 945 248; sleep 0.8; touch UP 945 248
shot 07-after-drag 2
# drag Clock onto the centre of Notes and hold -> folder
touch DOWN 405 560; sleep 1.3
glide 405 560 135 872; sleep 1.2
adb exec-out screencap -p > shots/08-folder-target.png; echo "shot 08-folder-target"
touch UP 135 872
shot 09-folder-open 2
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell input keyevent KEYCODE_HOME; shot 09b-edit-done 2
adb shell am start -n "$P/com.pacaembu.apps.SettingsActivity" >/dev/null; shot 10-settings
adb shell input keyevent KEYCODE_BACK
start com.pacaembu.apps.CalculatorActivity; shot 11-calculator
start com.pacaembu.apps.NotesActivity; shot 12-notes
start com.pacaembu.apps.ClockActivity; shot 13-clock
start com.pacaembu.apps.WeatherActivity; shot 14-weather 8
adb shell am start -n "$P/com.pacaembu.apps.PhoneActivity" -d tel:11987654321 >/dev/null; shot 15-phone-keypad
start com.pacaembu.lock.LockActivity; shot 16-lock
adb shell input keyevent KEYCODE_HOME; shot 17-home-final
adb logcat -d -b crash > shots/crash.txt || true
adb logcat -d | grep -iE "AndroidRuntime|pacaembu" | tail -200 > shots/logcat.txt || true
