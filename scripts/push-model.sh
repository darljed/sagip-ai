#!/usr/bin/env bash
# Copies the Gemma 4 E2B model onto a USB-connected Android phone where SAGIP looks for it.
#
#   1. Download gemma-4-E2B-it.litertlm (≈2.4 GiB) from
#      https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm   (accept the Gemma terms first)
#   2. ./scripts/push-model.sh /path/to/gemma-4-E2B-it.litertlm
#
# The app expects the lower-case file name below; the script renames it for you.
set -euo pipefail
SRC="${1:?usage: scripts/push-model.sh /path/to/gemma-4-E2B-it.litertlm}"
DEST_DIR=/data/local/tmp/llm
DEST_FILE=gemma-4-e2b-it.litertlm
ADB="${ADB:-adb}"

"$ADB" devices | grep -q "device$" || { echo "No Android device connected (enable USB debugging)." >&2; exit 1; }
"$ADB" shell mkdir -p "$DEST_DIR"
"$ADB" push "$SRC" "$DEST_DIR/$DEST_FILE"
"$ADB" shell chmod 644 "$DEST_DIR/$DEST_FILE"
"$ADB" shell ls -la "$DEST_DIR/$DEST_FILE"
echo "Done. Launch SAGIP — the loading screen shows 'Loading Gemma 4 E2B'."
