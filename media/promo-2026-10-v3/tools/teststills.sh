#!/usr/bin/env bash
# Quick review stills: tools/teststills.sh OUTDIR COMPOSITION SCALE t1 t2 ...  (then tiled with sheet.sh)
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
[[ -z "${NODE_BIN_DIR:-}" ]] || export PATH="$NODE_BIN_DIR:$PATH"
B=(); [[ -z "${BROWSER_EXECUTABLE:-}" ]] || B=(--browser-executable="$BROWSER_EXECUTABLE")
dir="$1"; comp="$2"; scale="$3"; shift 3
mkdir -p "$dir"; dir="$(cd "$dir" && pwd)"
cd "$HERE/remotion"
files=()
for t in "$@"; do
  f="$dir/$comp-$(printf %05.2f "$t").png"
  npx remotion still "$comp" "$f" --frame="$(python3 -c "print(round($t*60))")" "${B[@]}" --log=error --scale="$scale"
  files+=("$f")
done
case "$comp" in *16x9*) a=1.7778; tw=${TW:-900} ;; *) a=0.5625; tw=${TW:-330} ;; esac
"$HERE/tools/sheet.sh" "$dir/sheet-$comp.png" "$comp" "${COLS:-2}" "$tw" "$a" "${files[@]}"
