"""Tide Eel - a long pale serpent of the Undertow (~6 blocks), white-teal and slightly translucent-looking, glowing teal
ring markings (emissive), a narrow head with a hinged jaw.
Parts: head (root) -> jaw; body_1 (child of head) -> body_2 -> ... -> body_8; tail_fin (child of body_8).  (spec E)"""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat

F = np.floor
RING, RING_HI, RING_DIM = "#7ff2e2", "#c8fff8", "#3ae0d0"
# (half width, height, length) of each body segment, tapering to the tail; dorsal fin height per segment
SEGS = [(3, 5, 10), (3, 5, 10), (3, 5, 10), (3, 4, 10), (2, 4, 10), (2, 4, 10), (2, 3, 10), (1, 2, 10)]
FIN = [1, 2, 2, 2, 2, 2, 1, 1]


def build():
    m = Model("tide_eel", (128, 64))
    h = m.part("head", None, (0, 18, -40))
    h.cube((-3, -3, -9), (6, 5, 9), mat={"*": "skin", "bottom": "belly"}, tag="skull")
    h.cube((-2, -2, -13), (4, 3, 4), mat={"*": "skin", "bottom": "belly"}, tag="snout")
    h.cube((0, -5, -6), (0, 2, 6), mat="fin", tag="crest")  # small head crest, continues into the dorsal fin
    j = m.part("jaw", "head", (0, 2, -1))
    j.cube((-2.5, 0, -11), (5, 2, 11), mat={"*": "skin", "top": "mouth", "bottom": "belly"}, tag="jaw")
    prev = "head"
    for i, ((hw, hh, ln), fh) in enumerate(zip(SEGS, FIN)):
        p = m.part("body_%d" % (i + 1), prev, (0, 0, 0 if i == 0 else SEGS[i - 1][2]))
        w = 2 * hw
        y0 = -0.5 - hh / 2.0  # segments stay centred on the head's mid-line as they taper
        p.cube((-w / 2, y0, 0), (w, hh, ln), mat={"*": "skin", "bottom": "belly"}, tag="seg")
        p.cube((0, y0 - fh, 0), (0, fh, ln), mat="fin", tag="dorsal")
        prev = p.name
    t = m.part("tail_fin", "body_8", (0, -0.5, 10))
    t.cube((0, -4, 0), (0, 8, 9), mat="fin", tag="tailfin")  # tall vertical paddle (leaf-shaped cut below)
    m.pack()
    return dict(model=m, mats=mats(), textures=[
        dict(path="tide_eel/tide_eel", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-58, el=34, lens=70, margin=0.9,
                     pose=dict(**{"body_%d" % (i + 1): (0, a, 0) for i, a in enumerate((-10, -14, 8, 18, 16, 6, -14, -22))},
                               jaw=(18, 0, 0), tail_fin=(0, -10, 0))),
        emit_strength=3.0)


def mats():
    return {
        # pale white-teal skin; the dark-ish low levels read as the body seen through translucent flesh
        "skin": Mat(["#6fa8a2", "#9ccdc6", "#bfe4dd", "#dcf3ee", "#f0fbf8"], base=2, kind="chitin", feature=3.0,
                    pattern_scale=2.0, lo=0.14, hi=0.16, params={"band_dir": "Z", "distortion": 3.0, "line_frac": 0.1}),
        "belly": Mat(["#bfe4dd", "#dcf3ee", "#f0fbf8"], base=1, feature=2.0, lo=0.1, hi=0.1, shade={"bottom": 0}),
        "fin": Mat(["#7fbcb6", "#a9e0d6", "#cff3ec"], base=1, feature=2.0, lo=0.1, hi=0.1, rim=False,
                   shade={"top": 0, "bottom": 0}),
        "mouth": Mat(["#1a2f33", "#2a4448", "#3b5a5e"], base=1, feature=2.0, lo=0.1, hi=0.1),
    }


def paint(sk, m):
    sides = ["left", "right", "top", "bottom"]
    # translucent look: a darker "spine shadow" down the middle of the back, as if the spine shows through
    for i in range(8):
        seg = [c for c in m["body_%d" % (i + 1)].cubes if c.tag == "seg"][0]
        if seg.size[0] >= 4:
            sk.shift(sk.mask(cube=seg, face="top", cond=lambda t: np.abs(t.fx - (t.fw - 1) / 2.0) < 0.6), -1)
        # flank lateral line, faint
        sk.set(sk.mask(cube=seg, face=["left", "right"], cond=lambda t: (t.fy == t.fh // 2) & (F(t.cz) % 3 == 1)),
               "#9ccdc6")
        # glowing teal ring marking around each segment (two texels wide on thick segments)
        ring = sk.mask(cube=seg, face=sides + ["front", "back"], cond=lambda t: (F(t.cz) == 6) | (F(t.cz) == 7))
        sk.set(ring, RING_DIM, emit=RING, emit_alpha=220)
        core = sk.mask(cube=seg, face=["left", "right", "top"], cond=lambda t: F(t.cz) == 6)
        sk.set(core, RING, emit=RING_HI, emit_alpha=255)
        # a pair of small glow dots ahead of every ring on the flanks
        dots = sk.mask(cube=seg, face=["left", "right"], cond=lambda t: (F(t.cz) == 2) & (t.fy == 1))
        sk.set(dots, RING, emit=RING, emit_alpha=200)
        # dorsal fin: ragged soft edge
        fin = [c for c in m["body_%d" % (i + 1)].cubes if c.tag == "dorsal"][0]
        ln = fin.size[2]
        fh = fin.size[1]
        # a soft wave: full height mid-segment, dipping to one texel where the segments meet
        sk.cut_shape(fin, lambda x, y, z, ln=ln, fh=fh: y >= (fh - 1) * (1 - np.sin(np.pi * np.clip(z / ln, 0, 1)) ** 0.6) - 0.01)
        sk.set(sk.mask(cube=fin, cond=lambda t: (F(t.cz) == 6) | (F(t.cz) == 7)), RING_DIM, emit=RING, emit_alpha=170)
    # tail paddle: leaf shape, glowing veins fanning back
    tf = [c for c in m["tail_fin"].cubes if c.tag == "tailfin"][0]
    sk.cut_shape(tf, lambda x, y, z: np.abs(y - 4.0) <= 4.0 * np.sin(np.pi * np.clip((z + 0.5) / 9.5, 0, 1)) ** 0.8)
    sk.set(sk.mask(cube=tf, cond=lambda t: (np.abs(t.cy - 4.0) < 0.6)), RING_DIM, emit=RING, emit_alpha=200)
    sk.set(sk.mask(cube=tf, cond=lambda t: np.abs(np.abs(t.cy - 4.0) - t.cz * 0.33) < 0.5), "#a9e0d6",
           emit=RING_DIM, emit_alpha=110)
    # head: narrow, pale, small dark eyes ringed in glow, glowing gill marks, dark mouth line
    skull = [c for c in m["head"].cubes if c.tag == "skull"][0]
    for face in ("left", "right"):
        sk.set(sk.mask(cube=skull, face=face, cond=lambda t: (F(t.cz) == 2) & (t.fy == 1)), "#0d1c1f")
        sk.set(sk.mask(cube=skull, face=face, cond=lambda t: (F(t.cz) == 3) & (t.fy == 1)), RING, emit=RING_HI,
               emit_alpha=255)
        sk.set(sk.mask(cube=skull, face=face, cond=lambda t: (F(t.cz) >= 6) & (F(t.cz) % 2 == 0) & (t.fy >= 2) &
                       (t.fy <= 3)), RING_DIM, emit=RING, emit_alpha=200)
        sk.set(sk.mask(cube=skull, face=face, cond=lambda t: t.fy == t.fh - 1), "#3b5a5e")
    snout = [c for c in m["head"].cubes if c.tag == "snout"][0]
    sk.set(sk.mask(cube=snout, face=["left", "right", "front"], cond=lambda t: t.fy == t.fh - 1), "#3b5a5e")
    sk.set(sk.mask(cube=snout, face="top", cond=lambda t: (F(t.cz) == 0) & ((t.fx == 0) | (t.fx == t.fw - 1))),
           "#6fa8a2")
    jaw = [c for c in m["jaw"].cubes if c.tag == "jaw"][0]
    # needle teeth along the jaw rim (inner top edge), pale
    sk.set(sk.mask(cube=jaw, face="top", cond=lambda t: ((t.fx == 0) | (t.fx == t.fw - 1)) & (F(t.cz) % 2 == 0)),
           "#e6fffb")
    crest = [c for c in m["head"].cubes if c.tag == "crest"][0]
    sk.cut_shape(crest, lambda x, y, z: y >= 2.0 - z * 0.45)
