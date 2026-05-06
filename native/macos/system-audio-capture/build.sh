#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT="$SCRIPT_DIR/../system-audio-capture"

cd "$SCRIPT_DIR"
swift build -c release
cp ".build/release/system-audio-capture" "$OUTPUT"
chmod +x "$OUTPUT"
echo "Built and copied to $OUTPUT"
