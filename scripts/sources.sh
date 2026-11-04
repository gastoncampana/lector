#!/usr/bin/env bash
# Packs the third-party source code a release offers under the GPL into build/lector-<version>-sources.tar. espeak-ng
# is built into sherpa-onnx's JNI library, so that library's whole source goes along: sherpa-onnx v1.13.8 and every
# library its build pins, with the URLs and SHA-256s of its cmake files (kaldifst's and KISS FFT's come from
# kaldi-decoder's and kaldi-native-fbank's). Updating sherpa-onnx means updating this list and NOTICE.txt.
set -euo pipefail
cd "$(dirname "$0")/.."
version=$1
out=build/third-party-sources
mkdir -p "$out"
while read -r name sha url; do
  file="$out/$name"
  [ "$(sha256sum "$file" 2>/dev/null | cut -d' ' -f1)" = "$sha" ] && continue
  curl -fsSL --retry 3 -o "$file" "$url"
  echo "$sha  $file" | sha256sum -c --quiet -
done <<'EOF'
sherpa-onnx-1.13.8.tar.gz b0374cc56dbc186d442ae73d5de743bb092470b640c4c50ce7b029044c0c4fa8 https://github.com/k2-fsa/sherpa-onnx/archive/refs/tags/v1.13.8.tar.gz
espeak-ng-ed530aa.zip e4e262cbe34f7fe21f91f1ba3397f2728e1f30eafbae7853f2b753a9ed13f0dd https://github.com/csukuangfj/espeak-ng/archive/ed530aa113046142eb5115cf2fc9157854d0ffe1.zip
piper-phonemize-f3ff95a.zip d9cca4e2bdc7d6dd8dffb96a4668283dbd3f77a9c194a3e530c1e8eba9406a5d https://github.com/csukuangfj/piper-phonemize/archive/f3ff95afc03640bc1399e113e83361192a2fafb4.zip
kaldi-native-fbank-1.22.3.tar.gz 9176cc66fc7ce1edf85cf355b06e320c57db6297df74277f575183468893cf61 https://github.com/csukuangfj/kaldi-native-fbank/archive/refs/tags/v1.22.3.tar.gz
kissfft-febd4ca.zip 497103e664168ebe39580b757adbe616f6cf85a16572af581ca7bc42d0ab13fd https://github.com/mborgerding/kissfft/archive/febd4caeed32e33ad8b2e0bb5ea77542c40f18ec.zip
kaldi-decoder-0.3.0.tar.gz b9f34cfb4fd3b1344100eead79ef4d37aa15962274b9e3056de345021f76a1b0 https://github.com/k2-fsa/kaldi-decoder/archive/refs/tags/v0.3.0.tar.gz
kaldifst-1.8.0.tar.gz 3f247b7e5a2409071202f5e2bc6200060f66728c0a3443c03923ad2723e040b3 https://github.com/k2-fsa/kaldifst/archive/refs/tags/v1.8.0.tar.gz
openfst-1.8.5-2026-07-09.tar.gz 2ff712a32952fcb01d351121a6bc8ccf4fdc6b2aa06ce8df2b3095dedd518c0e https://github.com/csukuangfj/openfst/archive/refs/tags/v1.8.5-2026-07-09.tar.gz
eigen-5.0.1.tar.gz e9c326dc8c05cd1e044c71f30f1b2e34a6161a3b6ecf445d56b53ff1669e3dec https://gitlab.com/libeigen/eigen/-/archive/5.0.1/eigen-5.0.1.tar.gz
simple-sentencepiece-0.7.tar.gz 1748a822060a35baa9f6609f84efc8eb54dc0e74b9ece3d82367b7119fdc75af https://github.com/pkufool/simple-sentencepiece/archive/refs/tags/v0.7.tar.gz
json-3.12.0.tar.gz 4b92eb0c06d10683f7447ce9406cb97cd4b453be18d7279320f7b2f025c10187 https://github.com/nlohmann/json/archive/refs/tags/v3.12.0.tar.gz
hclust-cpp-2026-02-25.tar.gz 8f14e024c709d73afb40ae69cb22de4b73dba67cbce40f2e518813da8139ab56 https://github.com/csukuangfj/hclust-cpp/archive/refs/tags/2026-02-25.tar.gz
EOF
tar cf "build/lector-$version-sources.tar" -C build third-party-sources
