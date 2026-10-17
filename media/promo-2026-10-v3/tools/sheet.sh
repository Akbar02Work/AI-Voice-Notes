#!/usr/bin/env bash
# Contact sheet via the Remotion "Sheet" composition (bundled ffmpeg has no tile filter).
# tools/sheet.sh OUT.png "Title" COLS THUMB_W ASPECT FILE... (labels = file names without extension)
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
[[ -z "${NODE_BIN_DIR:-}" ]] || export PATH="$NODE_BIN_DIR:$PATH"
B=(); [[ -z "${BROWSER_EXECUTABLE:-}" ]] || B=(--browser-executable="$BROWSER_EXECUTABLE")
out="$1"; title="$2"; cols="$3"; tw="$4"; aspect="$5"; shift 5
out="$(cd "$(dirname "$out")" && pwd)/$(basename "$out")"
mkdir -p "$HERE/remotion/public/sheet"
items=""
for f in "$@"; do
  b="$(basename "$f")"; cp "$f" "$HERE/remotion/public/sheet/$b"
  items="$items{\"file\":\"$b\",\"label\":\"${b%.*}\"},"
done
n=$#
rows=$(( (n + cols - 1) / cols ))
W=$(( cols * (tw + 22) + 60 ))
H=$(python3 -c "print(int(120 + $rows * ($tw / $aspect + 60)))")
cd "$HERE/remotion"
npx remotion still Sheet "$out" --frame=0 --width=$W --height=$H --props="{\"title\":\"$title\",\"cols\":$cols,\"thumbW\":$tw,\"aspect\":$aspect,\"items\":[${items%,}]}" "${B[@]}" --log=error
rm -f "$HERE/remotion/public/sheet/"*
