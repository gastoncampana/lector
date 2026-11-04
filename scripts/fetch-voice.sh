#!/usr/bin/env bash
# Downloads Kokoro v1.0 (sherpa-onnx's build of it) into voice/, which the build packs as the app's assets. The model is
# too big for git, so it comes from the sherpa-onnx release, pinned by its SHA-256.
set -euo pipefail
cd "$(dirname "$0")/.."

url=https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2
sha=60703707bc05ccd48d60e1eb723f38b07d6e474795a0b6a588d7220400e0f7a4
# Written last, so an interrupted run starts over.
done=voice/kokoro/.sha256
[ "$(cat "$done" 2>/dev/null)" = "$sha" ] && exit 0

archive=voice.tar.bz2
trap 'rm -f "$archive"' EXIT
echo "Downloading the voice (350 MB)..."
curl -fL --retry 3 -o "$archive" "$url"
echo "$sha  $archive" | sha256sum -c --quiet -
mkdir -p voice/kokoro
d=kokoro-multi-lang-v1_0
tar xjf "$archive" -C voice/kokoro --strip-components=1 \
    "$d/model.onnx" "$d/voices.bin" "$d/tokens.txt" "$d/espeak-ng-data" "$d/LICENSE"
echo "$sha" > "$done"
