"""Short looping animated-WebP previews of each showcase clip for the README (docs/media/<scene>.webp).
Run after tools/showcase/make_video.sh: python3 tools/showcase/make_previews.py [build/showcase/video]"""
import glob
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build/showcase/video")
OUT = os.path.join(ROOT, "docs/media")
os.makedirs(OUT, exist_ok=True)
for clip in sorted(glob.glob(os.path.join(SRC, "[0-9][0-9]_*.mp4"))):
    name = os.path.basename(clip)[3:-4]
    dur = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", clip]).strip())
    start = max(0.0, dur / 2 - 2.5)
    out = os.path.join(OUT, name + ".webp")
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-ss", f"{start:.2f}", "-t", "5", "-i", clip,
                    "-vf", "fps=15,scale=640:-2:flags=lanczos", "-c:v", "libwebp_anim", "-lossless", "0", "-q:v", "62",
                    "-compression_level", "6", "-loop", "0", "-an", out], check=True)
    print(f"{name}: {os.path.getsize(out) / 1e6:.2f} MB")
