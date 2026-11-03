# Lector

Android accessibility service that reads the screen aloud. Kotlin, no libraries, a single Gradle module at the repo root.
Public repo: never commit secrets, absolute paths or personal data.

## Invariant: no network

A screen reader sees everything on screen, so it must never be able to send data anywhere.
`src/main/AndroidManifest.xml` strips `android.permission.INTERNET` with `tools:node="remove"`, which also blocks
libraries from adding it. Never remove that line or add a feature that needs the network. After adding a dependency,
confirm with `apkanalyzer manifest permissions build/outputs/apk/release/lector-release.apk`. Cloud backup is off too
(`allowBackup="false"`). The app asks for no permissions at all; the README says so, so a new one changes it.

## Commands

- Build and install on the USB-connected phone: `scripts/install.sh`.
- Unit tests: `./gradlew testDebugUnitTest`
- Build a GitHub release APK: `scripts/release.sh` (tests, signature and no-INTERNET checks, SHA-256 for the notes).

Testing the shortcut on a phone over adb: `adb shell input keycombination -t 4000 KEYCODE_VOLUME_UP KEYCODE_VOLUME_DOWN`
presses it, and `screencap` shows the result. Never `uiautomator dump` (or anything else that connects a UiAutomation)
in between: it suspends the accessibility services, Android then drops Lector from the shortcut setting, and the
next press turns TalkBack on, Android's default target when the setting is empty.

Release builds are signed with the key in `~/.config/lector/` (`release.p12`, `password`), which never enters the repo;
losing it means users must uninstall to update. Without it they fall back to this machine's debug key, so anyone can
build from source, and `scripts/release.sh` refuses to ship that build.

## Layout

- `Reading.kt`: pure logic, unit-tested in `src/test`. `Block` (a piece of on-screen text and its bounds in screen
  pixels), `blockAt` (the smallest block under a touch) and `pieces` (text cut to the TTS engine's input limit).
- `ReaderService.kt`: `ReaderService`, the accessibility service. `flagRequestAccessibilityButton` in
  `res/xml/reader_service.xml` makes its shortcut call `onClicked` (`toggle`) instead of turning the service off. It
  asks for no events: `collect` walks `rootInActiveWindow` only when the shortcut is pressed, and a node with text stands
  for its subtree. `ReaderView` is a `TYPE_ACCESSIBILITY_OVERLAY` window over the whole screen: outlines until a tap
  picks a block, then fills the block being read (utterance ids are `block/piece`; the last one's `onDone` closes it).
  Speech goes through the default `TextToSpeech` engine, which the `<queries>` entry for `TTS_SERVICE` makes visible.
  An engine that failed to bind, or a `speak` that returns `ERROR`, goes through `restart` (a new `TextToSpeech`), so
  the next press can work. `close` drops the callbacks already posted, so none reaches the next reading's overlay.
  Picked in the chooser Android shows when several services share the shortcut, `onClicked` runs while the chooser is
  still the active window, so `toggle` looks again once, 500 ms later, when it finds no text or an `android` window.
  Messages go through `notice` (an accessibility overlay), never a Toast: Android drops a background app's toasts while
  it may not post notifications, and the app asks for no permissions.
  `SetupActivity` is the app icon: it opens Accessibility settings.

UI strings live in `src/main/res`: `values/strings.xml` is English (the default), `values-es/strings.xml` Spanish
(rioplatense, voseo); new UI text needs both. Code comments are English.
