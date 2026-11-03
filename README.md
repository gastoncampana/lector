<h1 align="center">Lector</h1>

<p align="center">
  <strong>Press a shortcut, tap where to start, and your phone reads the screen aloud.</strong><br>
  An accessibility service for Android 13 and newer, with no permissions at all, not even the network.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-13%2B-3a3a3a" alt="Android 13 or newer">
  <img src="https://img.shields.io/badge/permissions-none-3a3a3a" alt="No permissions">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-3a3a3a" alt="MIT license"></a>
</p>

## How it works

1. Press Lector's accessibility shortcut, for example by holding both volume keys.
2. The screen dims and every piece of text on it gets an outline.
3. Tap the text to start from. Lector reads from there to the end of the screen and highlights each part as it reads.
4. Tap anywhere, or press the shortcut again, to stop.

It speaks with the phone's text-to-speech voice, so it uses the voice, speed and pitch picked in Android's settings
(Settings → System → Languages → Text-to-speech output; the names vary by phone).

Available in English and Spanish.

## Install

1. Download `lector-<version>.apk` from the latest release.
2. Open it on the phone and allow installs from that app when Android asks. Play Protect may warn you, because the app
   does not come from Google Play.
3. Open Lector. It takes you to the Accessibility settings: turn Lector on there.
   If Android greys the switch out as a restricted setting, open Lector's app info, then ⋮ → Allow restricted
   settings, and try again.
4. In Lector's accessibility page, turn on its shortcut and pick how to trigger it: both volume keys, the
   accessibility button or the gesture.

Android gives each shortcut to one service at a time: if another service shares it, such as Select to Speak, Android
asks which one to use every time. Take the shortcut off the other service to skip that question.

**Verify the download (optional).** Every published APK is signed with the same key, whose certificate has this SHA-256
fingerprint:

```text
45:0B:F2:0D:E5:E8:8A:EB:78:01:6E:06:0C:FA:09:B2:B4:2F:C2:69:F9:DB:9F:10:01:9E:8A:CE:7A:31:F3:80
```

Compare it with `apksigner verify --print-certs lector-<version>.apk`, which prints it without the colons.

## Privacy

An accessibility service can read everything on the screen, so this one is built so that it cannot leak it:

- **No INTERNET permission.** `src/main/AndroidManifest.xml` declares `android.permission.INTERNET` with
  `tools:node="remove"`, which strips it from the merged manifest even when a library asks for it. Without that
  permission Android gives the app no network sockets.
- **No permissions at all.** Check any build yourself with `apkanalyzer manifest permissions lector-<version>.apk`
  (apkanalyzer comes with the Android SDK command-line tools): it lists nothing.
- **It reads the screen only when you press its shortcut**, and keeps nothing: the text lives in memory until the
  reading ends. The service asks Android for no accessibility events at all.
- **The text goes to your text-to-speech engine**, which is a separate app with its own privacy terms. Pick an engine
  and voice that run on the device if you want the whole path to stay offline.

## Limits

- It reads what was on screen when you pressed the shortcut. Scrolling while it reads does not change what it reads.
- Apps that draw their own text, such as some games and apps built with Flutter, give accessibility services no text,
  and there is nothing to read.
- It reads text only. Images and buttons that only have an icon stay silent.
- Password fields are skipped.

## Build from source

Needs JDK 21 and the Android SDK.

```sh
./gradlew testDebugUnitTest assembleRelease
```

Without the release key, release builds are signed with your machine's debug key, so an APK you build cannot update
one from the releases page: uninstall that one first.

## License

[MIT](LICENSE)
