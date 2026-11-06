# Pacaembu

An Android launcher that recreates the look and feel of the classic iOS 6 home screen
(skeuomorphic SpringBoard), written in Kotlin + Jetpack Compose. Original code; no Apple
artwork is included — icons are drawn procedurally with glyphs from the open-source
[Ionicons](https://github.com/ionic-team/ionicons) repository (MIT), and any third-party
icon pack in the ADW/Nova format can be applied on top.

## What's in it

- **Home screen (SpringBoard):** 4-column pages, glass dock with reflections, page dots,
  Spotlight page to the left, wallpaper parallax, every app on the home screen (no drawer).
- **Edit mode:** hold an icon → icons jiggle; drag to rearrange (other icons slide, the screen
  turns pages at the edges, the dock accepts up to 4); drop on the centre of another icon to make
  a folder; ✕ badge uninstalls third-party apps. Home button exits edit mode.
- **Folders:** linen panel that opens under the folder row, pushing the icons around it, with an
  editable name; hold an app inside to take it out.
- **Notification Center:** pull down from the top half; linen sheet grouped by app.
- **Lock screen:** big clock bar, notifications, "slide to unlock" with shimmer, camera grabber.
- **Built-in apps:** Settings (grouped tables, pinstripes, ON/OFF switches), Phone (recents,
  contacts, keypad, +55 formatting), Calculator, Notes (legal pad), Clock (world clock, alarm,
  stopwatch, timer with picker wheels), Weather (Open-Meteo).
- **Shortcuts with iOS 6 icons** for Messages, Calendar (live date), Photos, Camera, Maps,
  Files, Contacts, Store, Mail, Browser and Music, opening the phone's default app of each kind.

## Build

Builds run on GitHub Actions (`.github/workflows/android.yml`) on every push and publish a
release `build-N` with `Pacaembu.apk`.

Regenerate glyphs: `python3 tools/ion2vd.py <ionicons>/dist/svg app/src/main/res/drawable <names…>`.
Regenerate strings: `python3 tools/strings.py app/src/main/res`.
