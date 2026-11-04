# Lector

Android accessibility service that reads the screen aloud with an on-device voice: Kokoro v1.0 through sherpa-onnx,
inside the APK. Kotlin, a single Gradle module at the repo root; Compose and Material 3 for the one screen.
Public repo: never commit secrets, absolute paths or personal data.

## Invariant: no network

A screen reader sees everything on screen, so it must never be able to send data anywhere.
`src/main/AndroidManifest.xml` strips `android.permission.INTERNET` with `tools:node="remove"`, which also blocks
libraries from adding it. Never remove that line or add a feature that needs the network. After adding a dependency,
confirm with `apkanalyzer manifest permissions build/outputs/apk/release/lector-release.apk`. Cloud backup is off too
(`allowBackup="false"`). The app asks for no permissions at all; the README says so, so a new one changes it.

## Commands

- Build and install on the USB-connected phone: `scripts/install.sh`.
- The voice (350 MB) is not in git: `scripts/fetch-voice.sh` downloads it into `voice/` (gitignored), checks its
  SHA-256, and the build packs it as assets. The install and release scripts run it; a build without it fails.
- Unit tests: `./gradlew testDebugUnitTest`
- Build a GitHub release APK: `scripts/release.sh` (tests, signature and no-INTERNET checks, SHA-256 for the notes).
  It also runs `scripts/sources.sh`, which packs `lector-<version>-sources.tar`: the GPL source of sherpa-onnx's native
  library (sherpa-onnx and every library its build pins, SHA-256 checked), to attach to the release next to the APK.

Testing the shortcut on a phone over adb: `adb shell input keycombination -t 4000 KEYCODE_VOLUME_UP KEYCODE_VOLUME_DOWN`
presses it, and `screencap` shows the result. Never `uiautomator dump` (or anything else that connects a UiAutomation)
in between: it suspends the accessibility services, Android then drops Lector from the shortcut setting, and the
next press turns TalkBack on, Android's default target when the setting is empty. Never force-stop the app either
(`am start -S`, `am force-stop`): Android turns off a force-stopped app's accessibility services and empties the same
setting. After `adb install -r` the service comes back on its own a few seconds later.

Release builds are signed with the key in `~/.config/lector/` (`release.p12`, `password`), which never enters the repo;
losing it means users must uninstall to update. Without it they fall back to this machine's debug key, so anyone can
build from source, and `scripts/release.sh` refuses to ship that build.

## Layout

- `Reading.kt`: pure logic, unit-tested in `src/test`. `Block` (a piece of on-screen text, its bounds in screen pixels
  and the nodes holding it), `blockAt` (the smallest block under a touch), `runs` (blocks read in one go: the pieces
  of a paragraph that its links split, which read one by one make the voice pause at every link), `readable` (drops
  runs made only of links: menus, tabs, link lists) and `language` (English or Spanish, from frequent words and
  Spanish-only letters; on the phone, nothing leaves it).
- `Speech.kt`: the voice. `Voice` lists the Kokoro speakers offered, three per language; each language has its pick
  and the speed is shared, all in preferences. `Speech` speaks: one thread generates audio with sherpa-onnx while
  another plays it on an `AudioTrack`, so the next sentence is ready before this one ends (the system TextToSpeech
  keeps only half a second ahead). Each text names its espeak-ng language per generation (`extra["lang"]`). The model
  loads from the assets in place, but espeak-ng reads files, so its data is copied to `filesDir` once per app update.
  A missing asset makes sherpa-onnx exit the process. The callback must be an `object`, not a lambda: the native side
  calls `invoke([F)Ljava/lang/Integer;`, which `proguard-rules.pro` keeps.
- `ReaderService.kt`: `ReaderService`, the accessibility service. `flagRequestAccessibilityButton` in
  `res/xml/reader_service.xml` makes its shortcut call `onClicked` (`toggle`) instead of turning the service off. It
  asks for no events: `collect` walks `rootInActiveWindow` only when the shortcut is pressed, and a node with text
  stands for its subtree. It skips controls (buttons, text fields such as a browser's address bar, and in Chrome, whose
  roles come in `extras` as `AccessibilityNodeInfo.chromeRole`, buttons and footnote marks: links in a superscript, and
  its page numbers and brackets) and marks link text unless a heading holds it, for `readable`. `ReaderView` is a
  `TYPE_ACCESSIBILITY_OVERLAY` window over the whole screen: outlines until a tap picks a block, then fills the run
  being read; the end of the reading closes it. It keeps the screen on: Android binds accessibility services as
  foreground only while the screen is on, so with it off the service's audio is muted (`muted ... source:opControlAudio`
  in `dumpsys audio`) and its threads drop to the `restricted` cpuset, the little cores. Chrome stops building a page's
  tree while nothing reads it, so `toggle` looks again 500 ms after opening and takes the longer list. Picked in the
  chooser Android shows when several services share the shortcut, `onClicked` runs while the chooser is still the active
  window, so `toggle` looks again once, 500 ms later, when it finds no text or an `android` window. Messages go through
  `notice` (an accessibility overlay), never a Toast: Android drops a background app's toasts while it may not post
  notifications, and the app asks for no permissions.
- `SettingsActivity.kt`: the app icon. A voice per language (tapping one plays a sample) and the speed; until the
  shortcut is on (Android enables the service exactly when a shortcut targets it), a card that opens Accessibility
  settings. At the bottom, the GPL notice and **Licenses**, which shows `src/main/assets/licenses/` with `NOTICE.txt`
  first.

## Licenses

The app is GPL-3.0-or-later. `src/main/assets/licenses/NOTICE.txt` lists every third-party component in the APK, down
to what sherpa-onnx's native libraries link in (inventoried from its v1.13.8 cmake files and the `.so` strings), and
says where to get the GPL source; the other files there hold the license texts. Updating sherpa-onnx, the voice or a
library means updating them, versions and commits included.

UI strings live in `src/main/res`: `values/strings.xml` is English (the default), `values-es/strings.xml` Spanish
(rioplatense, voseo); new UI text needs both. Code comments are English.
