#!/bin/sh
# Two font files are stored in 2 MB pieces (single files over ~2.5 MB cannot be uploaded to this repo). Run once after cloning.
set -e
cd "$(dirname "$0")/.."
mkdir -p app/src/main/res/font
for f in google_sans_flex.ttf sans_flex.ttf; do
  cat app/fontparts/$f.part* > app/src/main/res/font/$f
  (cd app/src/main/res/font && echo "$(cut -d' ' -f1 ../../../../fontparts/$f.sha256)  $f" | sha256sum -c -)
done
