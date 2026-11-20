#!/usr/bin/env python3
"""Verify the Worldsfall audio assets against sound_spec.py.

For every file stem referenced in sound_spec.py this checks that
  * src/main/resources/assets/worldsfall/sounds/<stem>.ogg exists
  * it is Ogg Vorbis at 44.1 kHz and decodes cleanly (ffprobe + ffmpeg)
  * the channel count is right: mono for everything except music and ambient
    loops (which may be mono or stereo)
  * the duration is sane for its category
  * it does not clip (decoded peak < 0 dBFS) and is not silent
  * ambient loops and the boss music loops have no discontinuity at the loop point
and that the whole sounds directory stays under the size budget.

Usage: python3 tools/audio/verify_audio.py [--max-mb 45]
Exit status is non-zero if any check fails.
"""

import argparse
import json
import os
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import sound_spec as spec  # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
SOUNDS = os.path.join(REPO, "src", "main", "resources", "assets", "worldsfall", "sounds")

LOOPED_MUSIC = {"music/unmade_1", "music/unmade_2", "music/unmade_3"}


def categorise():
    cats = {}
    for _, (_, files, _) in list(spec.block_events().items()) + list(spec.EVENTS.items()):
        for f in files:
            cats.setdefault(f, "sfx")
    for _, (loop, adds, _) in spec.AMBIENT.items():
        cats[loop] = "ambient_loop"
        for a in adds:
            cats.setdefault(a, "sfx")
    for _, (stem, _) in spec.MUSIC.items():
        cats[stem] = "music"
    return cats


DURATION = {"sfx": (0.03, 12.0), "ambient_loop": (20.0, 40.0), "music": (30.0, 240.0)}


def probe(path):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries",
                        "stream=codec_name,channels,sample_rate:format=duration,format_name",
                        "-of", "json", path], capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.strip() or "ffprobe failed")
    j = json.loads(r.stdout)
    st = j["streams"][0]
    return (st["codec_name"], int(st["channels"]), int(st["sample_rate"]),
            float(j["format"]["duration"]), j["format"]["format_name"])


def decode(path, channels):
    r = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-"],
                       capture_output=True)
    if r.returncode != 0 or r.stderr.strip():
        raise RuntimeError("decode error: " + r.stderr.decode(errors="replace").strip())
    return np.frombuffer(r.stdout, dtype=np.float32).reshape(-1, channels)


def seam_ok(x, pad=4096, win=64):
    """Detect a click at the loop point.

    The file is joined end-to-start; the short-time energy of the first difference
    (a high-frequency emphasis that makes discontinuities stand out) around the
    seam must not exceed 3x the 99.5th percentile of the same measure elsewhere.
    This tolerates the codec's tiny edge noise but catches real discontinuities.
    """
    x = x.astype(np.float64)
    xx = np.concatenate([x[-pad:], x[:pad]], axis=0)
    def energy(sig):
        d = np.diff(sig, axis=0)
        e = np.sum(d ** 2, axis=1)
        k = np.ones(win) / win
        return np.sqrt(np.convolve(e, k, mode="same"))
    seam = energy(xx)[pad - win: pad + win].max()
    ref = np.percentile(energy(x), 99.5)
    return seam <= max(3.0 * ref, 1e-4), seam, ref


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-mb", type=float, default=45.0)
    args = ap.parse_args()

    cats = categorise()
    fails = []
    rows = []
    for stem, cat in cats.items():
        path = os.path.join(SOUNDS, stem + ".ogg")
        problems = []
        if not os.path.isfile(path):
            fails.append((stem, ["missing file"]))
            continue
        try:
            codec, ch, sr, dur, fmt = probe(path)
            if codec != "vorbis" or "ogg" not in fmt:
                problems.append(f"not ogg/vorbis ({fmt}/{codec})")
            if sr != 44100:
                problems.append(f"sample rate {sr}")
            if cat == "sfx" and ch != 1:
                problems.append(f"{ch} channels (positional sounds must be mono)")
            if ch not in (1, 2):
                problems.append(f"{ch} channels")
            lo, hi = DURATION[cat]
            if not lo <= dur <= hi:
                problems.append(f"duration {dur:.2f}s outside [{lo}, {hi}]")
            x = decode(path, ch)
            peak = float(np.abs(x).max())
            rms = float(np.sqrt(np.mean(x.astype(np.float64) ** 2)))
            pk_db = 20 * np.log10(peak + 1e-12)
            rms_db = 20 * np.log10(rms + 1e-12)
            if peak >= 1.0:
                problems.append(f"clipping (peak {pk_db:.2f} dBFS)")
            if pk_db < -40 or rms_db < -65:
                problems.append(f"near-silent (peak {pk_db:.1f}, rms {rms_db:.1f} dBFS)")
            if cat == "ambient_loop" or stem in LOOPED_MUSIC:
                ok, jump, ref = seam_ok(x)
                if not ok:
                    problems.append(f"loop seam jump {jump:.4f} > typical {ref:.4f}")
            rows.append((stem, cat, ch, dur, pk_db, rms_db, os.path.getsize(path)))
        except Exception as e:  # noqa: BLE001
            problems.append(str(e))
        if problems:
            fails.append((stem, problems))

    total = 0
    for root, _, files in os.walk(SOUNDS):
        total += sum(os.path.getsize(os.path.join(root, f)) for f in files)
    extra = []
    for root, _, files in os.walk(SOUNDS):
        for f in files:
            if f.endswith(".ogg"):
                rel = os.path.relpath(os.path.join(root, f), SOUNDS)[:-4].replace(os.sep, "/")
                if rel not in cats:
                    extra.append(rel)

    by_cat = {}
    for r in rows:
        by_cat.setdefault(r[1], []).append(r)
    print(f"Checked {len(cats)} stems in {SOUNDS}")
    for cat, rs in sorted(by_cat.items()):
        durs = [r[3] for r in rs]
        pks = [r[4] for r in rs]
        print(f"  {cat:<13} {len(rs):4d} files  duration {min(durs):6.2f}-{max(durs):6.2f}s  "
              f"peak {min(pks):6.1f}..{max(pks):5.1f} dBFS  channels {sorted(set(r[2] for r in rs))}")
    print(f"  total size: {total / 1e6:.2f} MB (budget {args.max_mb:.0f} MB)")
    if extra:
        print(f"  note: {len(extra)} .ogg files not referenced by sound_spec.py: {', '.join(sorted(extra))}")
    if total > args.max_mb * 1e6:
        fails.append(("<sounds dir>", [f"total size {total / 1e6:.1f} MB exceeds {args.max_mb} MB"]))
    if fails:
        print(f"\nFAILED ({len(fails)}):")
        for stem, probs in fails:
            print(f"  {stem}: {'; '.join(probs)}")
        sys.exit(1)
    print("\nAll checks passed.")


if __name__ == "__main__":
    main()
