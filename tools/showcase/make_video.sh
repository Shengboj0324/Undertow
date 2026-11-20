#!/usr/bin/env bash
# Assembles the frame sequences written by ShowcaseDirector (build/showcase/frames/<NN_scene>/f_####.png) into
# one H.264 showcase video with short cross-fades, plus a per-scene clip for each sequence.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IN="${1:-$ROOT/build/showcase}"
OUT="${2:-$IN/video}"
FPS=20
mkdir -p "$OUT"
clips=()
for dir in "$IN"/frames/*/; do
  [ -d "$dir" ] || continue
  name="$(basename "$dir")"
  n=$(ls "$dir" | grep -c '^f_.*\.png$' || true)
  [ "$n" -ge 10 ] || continue
  ffmpeg -loglevel error -y -framerate "$FPS" -start_number 1 -i "$dir/f_%04d.png" \
    -vf "minterpolate=fps=40:mi_mode=blend,scale=1920:-2:flags=lanczos,format=yuv420p" \
    -c:v libx264 -preset slow -crf 17 -movflags +faststart "$OUT/$name.mp4"
  clips+=("$OUT/$name.mp4")
done
[ "${#clips[@]}" -gt 0 ] || { echo "no frame sequences in $IN/frames" >&2; exit 1; }

# Chain clips with 0.6 s cross-fades.
args=(); filter=""; prev="[0:v]"; offset=0
for i in "${!clips[@]}"; do args+=(-i "${clips[$i]}"); done
for i in "${!clips[@]}"; do
  d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "${clips[$i]}")
  if [ "$i" -eq 0 ]; then offset=$(echo "$d - 0.6" | bc -l); continue; fi
  filter+="${prev}[$i:v]xfade=transition=fade:duration=0.6:offset=${offset}[v$i];"
  prev="[v$i]"
  offset=$(echo "$offset + $d - 0.6" | bc -l)
done
if [ -z "$filter" ]; then
  cp "${clips[0]}" "$OUT/worldsfall_showcase.mp4"
else
  ffmpeg -loglevel error -y "${args[@]}" -filter_complex "${filter%;}" -map "$prev" \
    -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p -movflags +faststart "$OUT/worldsfall_showcase.mp4"
fi
echo "wrote $OUT/worldsfall_showcase.mp4"
