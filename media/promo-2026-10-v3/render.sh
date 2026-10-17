#!/usr/bin/env bash
# Reproducible renders for the AI Voice Notes promo "Say it messy" (2026-10).
# Usage: ./render.sh [typecheck|audio|stills|proof|film|website|posters|sheets|measure|all] [dark|light|both]
#
# Setup and optional runtime overrides are documented in README.md.
# Audio: python3 standard library only (audio/generate_audio.py) + the ElevenLabs voice take.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
[[ -z "${NODE_BIN_DIR:-}" ]] || export PATH="$NODE_BIN_DIR:$PATH"
BROWSER_EXECUTABLE="${BROWSER_EXECUTABLE:-}"
B=()
[[ -x "$BROWSER_EXECUTABLE" ]] && B=(--browser-executable="$BROWSER_EXECUTABLE")
OUT="$HERE/out"
FFDIR="${FFMPEG_DIR:-$HERE/remotion/node_modules/@remotion/compositor-darwin-arm64}"
FF() { if [[ -x "$FFDIR/ffmpeg" ]]; then DYLD_LIBRARY_PATH="$FFDIR" "$FFDIR/ffmpeg" "$@"; else ffmpeg "$@"; fi; }
FFPROBE() { if [[ -x "$FFDIR/ffprobe" ]]; then DYLD_LIBRARY_PATH="$FFDIR" "$FFDIR/ffprobe" "$@"; else ffprobe "$@"; fi; }
VIDEO=(--codec=h264 --crf=10 --x264-preset=slow --image-format=png --pixel-format=yuv420p --color-space=bt709 --audio-codec=aac --audio-bitrate=192k)
case "${2:-both}" in dark) MODES=(dark) ;; light) MODES=(light) ;; both) MODES=(dark light) ;; *) echo "unknown mode" >&2; exit 2 ;; esac
cap() { [[ $1 == dark ]] && echo Dark || echo Light; }
cd "$HERE/remotion"
mkdir -p "$OUT/stills" "$OUT/review" "$OUT/proof" "$OUT/master"
f() { python3 -c "print(round($1*60))"; }
SKIP916=1.1 # the 9:16 cut starts 1.1 s into the film (on the voice)
# still COMP FILM_TIME OUT — film time is converted to the composition's own frame
still() { local t="$2"; [[ $1 == *9x16* ]] && t=$(python3 -c "print(max(0,$2-$SKIP916))"); npx remotion still "$1" "$3" --frame="$(f "$t")" "${B[@]}" --log=error; }

typecheck() { npx tsc --noEmit -p .; }
audio() { python3 -I "$HERE/audio/generate_audio.py"; }
STILL_TIMES=(0.40 2.60 5.40 8.60 9.60 10.40 11.10 11.60 12.40 13.40 14.60 16.20 17.00 18.40 19.30 20.60 21.60 23.40)
stills() {
  for m in "${MODES[@]}"; do for fmt in 16x9 9x16; do
    for t in "${STILL_TIMES[@]}"; do still "Stills$fmt$(cap "$m")" "$t" "$OUT/stills/$m-$fmt-$(printf %05.2f "$t").png"; done
  done; done
}
proof() {
  # Motion proof, no audio: $3/$4 = start/end seconds (default the clean-up hero, 8.6–12.2 s)
  local a="${3:-8.6}" b="${4:-12.2}"
  for m in "${MODES[@]}"; do for fmt in 16x9 9x16; do
    npx remotion render "Stills$fmt$(cap "$m")" "$OUT/proof/proof-$m-$fmt.mp4" --frames="$(f "$a")-$(f "$b")" --codec=h264 --crf=18 --pixel-format=yuv420p --muted "${B[@]}" --log=error
  done; done
}
film() {
  for m in "${MODES[@]}"; do for fmt in 16x9 9x16; do
    npx remotion render "Film$fmt$(cap "$m")" "$OUT/ai-voice-notes-v3-$m-$fmt.mp4" "${VIDEO[@]}" "${B[@]}" --log=error
  done; done
}
website() {
  # Native 9:19 canvas for the portfolio gallery, with the 9:16 foreground centred.
  for m in "${MODES[@]}"; do
    npx remotion render "Film9x19$(cap "$m")" "$OUT/ai-voice-notes-v3-$m-9x19.mp4" "${VIDEO[@]}" --concurrency=4 "${B[@]}" --log=error
    npx remotion still "Stills9x19$(cap "$m")" "$OUT/ai-voice-notes-v3-$m-9x19-poster.png" --frame="$(f 8.3)" "${B[@]}" --log=error
    echo "Rendered website 9:19: $m"
  done
}
master() {
  # Light-compression masters for re-encoding: ProRes 422 HQ, 10-bit 4:2:2, PCM audio (.mov)
  for m in "${MODES[@]}"; do for fmt in 16x9 9x16; do
    npx remotion render "Film$fmt$(cap "$m")" "$OUT/master/ai-voice-notes-v3-$m-$fmt.mov" --codec=prores --prores-profile=hq --image-format=png "${B[@]}" --log=error
  done; done
}
posters() {
  # Poster = 11.80 s: the clean note, "AI cleans it up."
  for m in "${MODES[@]}"; do for fmt in 16x9 9x16; do still "Stills$fmt$(cap "$m")" 11.80 "$OUT/ai-voice-notes-v3-$m-$fmt-poster.png"; done; done
}
SHEET_TIMES=(0.40 2.60 5.40 8.40 9.40 10.10 10.70 11.40 12.40 13.30 14.60 16.10 17.10 18.40 19.80 23.40)
sheets() {
  for m in "${MODES[@]}"; do
    "$HERE/tools/decode_sheet.sh" "$OUT/ai-voice-notes-v3-$m-16x9.mp4" "$OUT/review/Sheet-$m-16x9.png" "Say it messy · $m 16×9 · decoded MP4 frames" 4 455 0 "${SHEET_TIMES[@]}"
    local T916=(); for x in "${SHEET_TIMES[@]}"; do T916+=("$(python3 -c "print(max(0,round($x-$SKIP916,2)))")"); done
    "$HERE/tools/decode_sheet.sh" "$OUT/ai-voice-notes-v3-$m-9x16.mp4" "$OUT/review/Sheet-$m-9x16.png" "Say it messy · $m 9×16 · decoded MP4 frames (film time)" 8 225 "$SKIP916" "${T916[@]}"
  done
}
measure() {
  for m in "${MODES[@]}"; do for fmt in 16x9 9x16; do
    v="$OUT/ai-voice-notes-v3-$m-$fmt.mp4"; echo "== $(basename "$v")"
    FFPROBE -v error -show_entries stream=codec_name,width,height,r_frame_rate,nb_frames,duration -of compact "$v"
    FF -hide_banner -nostats -i "$v" -vn -af loudnorm=print_format=json -f null - 2>&1 | grep -E '"input_(i|tp|lra)"'
    python3 -I "$HERE/tools/frame_stats.py" "$v"
  done; done
}
case "${1:-all}" in
  typecheck) typecheck ;; audio) audio ;; stills) stills ;; proof) proof "$@" ;; film) film ;;
  website) website ;; posters) posters ;; master) master ;; sheets) sheets ;; measure) measure ;;
  all) typecheck; audio; film; posters; sheets; measure ;;
  *) echo "unknown target: $1" >&2; exit 2 ;;
esac
