<h1 align="center">Lector</h1>

<p align="center">
  <strong>Press a shortcut, tap where to start, and your phone reads the screen aloud.</strong><br>
  An accessibility service for Android 13 and newer, with no permissions at all, not even the network.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-13%2B-3a3a3a" alt="Android 13 or newer">
  <img src="https://img.shields.io/badge/permissions-none-3a3a3a" alt="No permissions">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-3a3a3a" alt="GPL-3.0 license"></a>
</p>

## How it works

1. Press Lector's accessibility shortcut, for example by holding both volume keys.
2. The screen dims and every piece of text on it gets an outline.
3. Tap the text to start from. Lector reads from there to the end of the screen and highlights each part as it reads.
4. Tap anywhere, or press the shortcut again, to stop.

It speaks with its own voice, [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M), generated on the phone: no
text-to-speech app needed. It tells English from Spanish by itself and reads each with its own voice; open Lector to
pick a voice for each language and the speed.

It reads the text, not the controls around it: buttons, text fields, a web page's tabs and footnote marks are skipped.

The app is available in English and Spanish.

## Install

1. Download `lector-<version>.apk` from the latest release. It is big, about 390 MB, because it carries the voice.
   It runs on 64-bit ARM phones, as nearly all recent ones are, not on 32-bit or x86 devices.
2. Open it on the phone and allow installs from that app when Android asks. Play Protect may warn you, because the app
   does not come from Google Play.
3. Open Lector and tap **Open Accessibility settings**. Find Lector there, turn on its shortcut and pick how to
   trigger it: both volume keys, the accessibility button or the gesture.
   If Android greys the switch out as a restricted setting, open Lector's app info, then ⋮ → Allow restricted
   settings, and try again.

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
- **The text never leaves the app.** The voice runs inside it, and so does telling English from Spanish.

## Limits

- It reads what was on screen when you pressed the shortcut. Scrolling while it reads does not change what it reads.
- Apps that draw their own text, such as some games and apps built with Flutter, give accessibility services no text,
  and there is nothing to read.
- It reads text only. Images and buttons that only have an icon stay silent.
- It reads English and Spanish. Other languages come out with one of those accents.
- The voice takes a lot of processing. On a recent phone it speaks faster than it is generated; on a mid-range one it
  may pause a little between sentences, and it takes about 600 MB of memory.
- Password fields are skipped.

## Build from source

Needs JDK 21 and the Android SDK. The voice is too big for git: `scripts/fetch-voice.sh` downloads it (350 MB) from the
sherpa-onnx releases and checks its SHA-256 before the build packs it.

```sh
scripts/fetch-voice.sh
./gradlew testDebugUnitTest assembleRelease
```

Without the release key, release builds are signed with your machine's debug key, so an APK you build cannot update
one from the releases page: uninstall that one first.

## License

Lector is free software under the [GNU GPL, version 3 or later](LICENSE). Its APK also carries:

| Component | What for | License |
|---|---|---|
| [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) v1.0 | The voice model and its voices | Apache-2.0 |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Runs the model | Apache-2.0 |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | Inside sherpa-onnx | MIT |
| [espeak-ng](https://github.com/espeak-ng/espeak-ng) | Turns text into the model's phonemes, inside sherpa-onnx | GPL-3.0 |
