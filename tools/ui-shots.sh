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
# long press the second icon of the first row -> edit mode
adb shell input swipe 405 420 405 420 1500; shot 06-edit-mode 1
# drag it to the end of the row
adb shell input swipe 405 420 945 420 2500; shot 07-after-drag 2
adb shell input keyevent KEYCODE_HOME; shot 08-edit-done 2
start .apps.SettingsActivity; shot 10-settings
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
