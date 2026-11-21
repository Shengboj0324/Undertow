"""Lumen Heart - a Lumen crystal cluster hanging from a ceiling; heals the dragon in the Underside fight (replaces the
End Crystals).  Faceted teal crystal (#2FC9BB -> #C8FFF8), dark voidroot roots on top, a white-hot core seen through
the facets.  Parts: core (root, pivot y = 8; the main 6x10x6 crystal), shard_1..shard_4 (children of core, smaller
crystals angled outward), roots (child of core, hanging strands above).  (spec E)"""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat

F = np.floor
WHITE, HOT, TEAL_HI, TEAL, TEAL_DIM = "#ffffff", "#e6fffb", "#c8fff8", "#2fc9bb", "#1a8f88"


def build():
    m = Model("lumen_heart", (64, 64))
    core = m.part("core", None, (0, 8, 0))
    core.cube((-3, -5, -3), (6, 10, 6), mat="crystal", tag="main")  # the 6x10x6 heart crystal (y 3..13)
    core.cube((-2, -7, -2), (4, 2, 4), mat="crystal", tag="crown")  # stepped top where the roots grip it
    core.cube((-2, 5, -2), (4, 3, 4), mat="crystal", tag="lower")  # tapering point hanging down
    core.cube((-1, 8, -1), (2, 3, 2), mat="crystal", tag="tip")
    # four smaller crystals: hang down and splay outward (Y points down: +y = towards the floor)
    for i, (piv, rot, size, tip) in enumerate((((2.5, -1, -1), (12, 0, -38), (3, 6, 3), (1, 2, 1)),
                                               ((-2.5, 0, 1.5), (-14, 0, 34), (3, 7, 3), (1, 2, 1)),
                                               ((1, 1, 2.5), (36, 0, -10), (2, 5, 2), (1, 2, 1)),
                                               ((-1, -2, -2.5), (-40, 0, 12), (2, 5, 2), (1, 1, 1)))):
        s = m.part("shard_%d" % (i + 1), "core", piv, rot)
        w, h, d = size
        s.cube((-w / 2, 0, -d / 2), size, mat="crystal", tag="shard")
        s.cube((-tip[0] / 2, h, -tip[2] / 2), tip, mat="crystal", tag="shard_tip")
    # voidroot strands that hold it to the ceiling (above the crown, up to y = -24 in model space)
    r = m.part("roots", "core", (0, -7, 0))
    r.cube((-3, -1, -3), (6, 1, 6), mat="root", tag="cap")  # the grip over the crown
    # each strand climbs in short segments that step outward, so the bundle fans out towards the ceiling
    for strand in (((-2, -2, 6, 2), (-3, -2, 7, 1), (-3, -3, 9, 1)),
                   ((1, -2, 5, 2), (2, -3, 8, 1), (3, -3, 11, 1)),
                   ((-1, 1, 4, 1), (-2, 1, 7, 1), (-2, 2, 8, 1)),
                   ((2, 1, 5, 1), (3, 1, 6, 1), (3, 2, 9, 1)),
                   ((-1, -1, 9, 2), (0, 0, 9, 1), (0, -1, 6, 1))):
        y = -1
        for k, (x, z, h, w) in enumerate(strand):
            ov = 1 if k else 0  # overlap one texel with the segment below so the strand stays connected
            r.cube((x, y - h, z), (w, h + ov, w), mat="root", tag="strand")
            y -= h
    r.cube((-4, -2, -0.5), (2, 1, 1), mat="root", tag="tendril")  # small curls gripping the crown edge
    r.cube((2, -2, -1.5), (2, 1, 1), mat="root", tag="tendril2")
    r.cube((-1, -2, 3), (1, 1, 2), mat="root", tag="tendril3")
    m.pack()
    return dict(model=m, mats={
        "crystal": Mat(["#0f5a58", "#1a8f88", "#2fc9bb", "#5fe3d4", "#94f2e6", "#c8fff8"], base=2, kind="crystal",
                       feature=2.5, pattern_scale=2.2, lo=0.1, hi=0.1, params={"line_frac": 0.12}),
        "root": Mat(["#120c16", "#1e1526", "#2c2036", "#3d2e48"], base=1, kind="chitin", feature=2.0,
                    pattern_scale=1.6, lo=0.15, hi=0.15, params={"band_dir": "Y", "distortion": 3.0, "line_frac": 0.18}),
    }, textures=[dict(path="lumen_heart/lumen_heart", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-30, el=6, lens=85, margin=0.82), emit_strength=2.0)


def paint(sk, m):
    crys = [c for c in m.cubes() if c.owner is None and c.mat == "crystal"]
    sides = ["front", "back", "left", "right"]
    for c in crys:
        mk = sk.mask(cube=c)
        sk.glow(mk, TEAL, 35)  # lumen: the whole crystal breathes a faint light
        # lit corner edge (top-left light) and a dark crease down the middle of wider faces
        sk.set(sk.mask(cube=c, face=["front", "left"], cond=lambda t: (t.fx == 0) & (t.fw >= 2)), "#94f2e6",
               emit="#94f2e6", emit_alpha=150)
        sk.shift(sk.mask(cube=c, face=sides, cond=lambda t: (t.fw >= 5) & (t.fx == t.fw - 1)), -1)
        sk.shift(sk.mask(cube=c, face="bottom"), -1)
        if c.tag in ("tip", "shard_tip"):
            sk.set(mk, TEAL_HI, emit=HOT, emit_alpha=230)
    main = [c for c in m["core"].cubes if c.tag == "main"][0]
    # the white-hot core seen through the facets: a soft lozenge on every side face, white centre, teal falloff
    for face in sides:
        r = sk.mask(cube=main, face=face)
        dx = np.abs(sk.t_fx - 2.5)
        dy = np.abs(sk.t_fy - 4.5)
        d = dx / 2.0 + dy / 4.0
        sk.set(r & (d <= 1.25), "#5fe3d4", emit="#94f2e6", emit_alpha=200)
        sk.set(r & (d <= 0.8), TEAL_HI, emit=HOT, emit_alpha=240)
        sk.set(r & (d <= 0.35), WHITE, emit=WHITE, emit_alpha=255)
    # facet seams: a few darker diagonal fracture lines across the heart
    seam = sk.mask(cube=main, face=sides, cond=lambda t: (np.abs((t.fx - t.fy * 0.6) - (-1.0)) < 0.5) & (t.fy > 6))
    sk.set(seam, "#1a8f88")
    seam2 = sk.mask(cube=main, face=sides, cond=lambda t: (np.abs((t.fx + t.fy * 0.7) - 3.6) < 0.5) & (t.fy < 2.5))
    sk.set(seam2, "#1a8f88")
    # roots: darker towards the ceiling, a few faint teal sap veins near the grip (lumen soaking up the roots)
    for c in [c for c in m["roots"].cubes if c.owner is None]:
        if c.tag == "strand":
            sk.shift(sk.mask(cube=c, face=sides, cond=lambda t: t.fy < t.fh * 0.4), -1)
            if c.size[0] >= 2:  # the thick bases carry a little glowing sap
                sk.set(sk.mask(cube=c, face=sides, cond=lambda t: (t.fy == t.fh - 2) & (t.fx == 0)),
                       "#1f6f6c", emit=TEAL_DIM, emit_alpha=110)
    cap = [c for c in m["roots"].cubes if c.tag == "cap"][0]
    sk.set(sk.mask(cube=cap, face="bottom", cond=lambda t: (t.fx + 2 * t.fy) % 4 == 0), "#1f6f6c", emit=TEAL_DIM,
           emit_alpha=120)
