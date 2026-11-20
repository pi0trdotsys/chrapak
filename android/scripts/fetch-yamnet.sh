#!/usr/bin/env bash
# Downloads the YAMNet TFLite model (Apache 2.0, from Google's TensorFlow Task Library samples)
# into the app's assets and verifies it. The model is not committed to the repository.
set -euo pipefail

URL="https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/audio_classification/android/lite-model_yamnet_classification_tflite_1.tflite"
SHA256="10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/yamnet.tflite"

mkdir -p "$(dirname "$DEST")"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

curl -fsSL -o "$TMP" "$URL"
ACTUAL="$(shasum -a 256 "$TMP" | cut -d' ' -f1)"
if [ "$ACTUAL" != "$SHA256" ]; then
  echo "Checksum mismatch: expected $SHA256, got $ACTUAL" >&2
  exit 1
fi
mv "$TMP" "$DEST"
trap - EXIT
echo "Saved $DEST ($(wc -c < "$DEST" | tr -d ' ') bytes)"
