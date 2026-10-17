#!/usr/bin/env bash
# Review sheet of frames decoded from an encoded MP4 (not from source):
# tools/decode_sheet.sh VIDEO.mp4 OUT.png "Title" COLS THUMB_W OFFSET t1 t2 ...   (labels = OFFSET + t)
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
FFDIR="${FFMPEG_DIR:-$HERE/remotion/node_modules/@remotion/compositor-darwin-arm64}"
FF() { if [[ -x "$FFDIR/$1" ]]; then local binary="$1"; shift; DYLD_LIBRARY_PATH="$FFDIR" "$FFDIR/$binary" "$@"; else "$@"; fi; }
video="$1"; out="$2"; title="$3"; cols="$4"; tw="$5"; off="$6"; shift 6
tmp="${TMPDIR:-/tmp}/dec-$$"; mkdir -p "$tmp"
read -r w h < <(FF ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0 "$video" | tr ',' ' ')
files=()
for t in "$@"; do
  lab=$(python3 -c "print(f'{$off+$t:05.2f}s')")
  FF ffmpeg -hide_banner -loglevel error -y -ss "$t" -i "$video" -frames:v 1 "$tmp/$lab.png"
  files+=("$tmp/$lab.png")
done
aspect=$(python3 -c "print($w/$h)")
"$HERE/tools/sheet.sh" "$out" "$title" "$cols" "$tw" "$aspect" "${files[@]}"
rm -rf "$tmp"
