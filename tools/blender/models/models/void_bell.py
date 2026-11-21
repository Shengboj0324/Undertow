"""Void Bell - the player-built diving bell (~3 blocks wide, 3 tall): a dark iron-and-purpur frame with Stillglass window
panels (dark, translucent-looking), a Gravity Anchor core glowing teal on top, a lamp underneath, a hatch.
Parts: shell (root), core (child), lamp (child), hatch (child).  (spec E)

The four walls are identical, so they are four extra child parts of the shell (side_n/e/s/w, yaw 0/90/180/270) that
share one set of UV islands; corner pieces are laid out as a pinwheel so no two walls overlap at the corners."""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat

F = np.floor
TEAL, TEAL_HI, TEAL_DIM = "#3ae0d0", "#c8fff8", "#1f8f88"
GLASS_A = 150


def build():
    m = Model("void_bell", (128, 128))
    sh = m.part("shell", None, (0, 21, 0))  # raised so the lamp underneath rests on the ground (y 24)
    sh.cube((-12, -42, -12), (24, 2, 24), mat="iron", tag="roof")
    for i, (name, yaw) in enumerate((("side_n", 0), ("side_e", 90), ("side_s", 180), ("side_w", -90))):
        p = m.part(name, "shell", (0, 0, 0), (0, yaw, 0))
        own = i == 0

        def cube(o, s, mat, tag, share=None):
            p.cube(o, s, mat=mat, tag=tag, share=share or tag, bake=own)
        cube((-24, -4, -24), (44, 4, 4), {"*": "purpur", "bottom": "iron"}, "rim", "beam")
        cube((-24, -24, -24), (4, 20, 4), "iron", "post")
        cube((-20, -24, -23), (40, 20, 1), "glass", "glass")
        cube((-24, -28, -24), (44, 4, 4), {"*": "purpur", "bottom": "iron"}, "belt", "beam")
        cube((-20, -36, -20), (36, 8, 4), "purpur", "wall")
        cube((-16, -40, -16), (28, 4, 4), "iron", "ubelt")
        cube((-20, -2, -20), (20, 2, 20), {"*": "iron", "top": "deck"}, "floor")
    core = m.part("core", "shell", (0, -42, 0))
    core.cube((-5, -2, -5), (10, 2, 10), mat="iron", tag="socket")
    core.cube((-3, -8, -3), (6, 6, 6), mat="anchor", tag="anchor")
    for k, (x, z) in enumerate(((-4.5, -4.5), (3.5, -4.5), (3.5, 3.5), (-4.5, 3.5))):
        core.cube((x, -10, z), (1, 8, 1), mat="iron", tag="cage", share="cage", bake=(k == 0))
    core.cube((-2, -11, -2), (4, 1, 4), mat="iron", tag="cap")
    core.cube((-0.5, -13, -0.5), (1, 2, 1), mat="iron", tag="ring")  # winch eye on top
    lamp = m.part("lamp", "shell", (0, 0, 0))
    lamp.cube((-3, 0, -3), (6, 2, 6), mat="iron", tag="housing")
    lamp.cube((-2, 2, -2), (4, 1, 4), mat="lens", tag="lens")
    hatch = m.part("hatch", "shell", (-8, -4, 23))  # hinged on its left edge, on the back (south) wall
    hatch.cube((0, -20, 0), (16, 20, 1), mat="iron", tag="door")
    hatch.cube((11, -12, 1), (2, 3, 1), mat="brass", tag="handle")
    m.pack()
    return dict(model=m, mats=mats(), textures=[
        dict(path="void_bell/void_bell", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-34, el=20, lens=85, margin=0.86), emit_strength=3.0)


def mats():
    return {
        "iron": Mat(["#101016", "#1a1a22", "#262630", "#353542", "#4a4b5c"], base=2, feature=3.0, lo=0.15, hi=0.15),
        "purpur": Mat(["#3a2840", "#4f3858", "#684a72", "#83608e", "#9c7aa6"], base=2, kind="cracks", feature=3.0,
                      pattern_scale=3.0, lo=0.15, hi=0.15, extra={"crack": "#2c1e32"},
                      params={"crack_cover": 0.35, "line_frac": 0.08}),
        "glass": Mat(["#060c0f", "#0b161a", "#122228", "#1b3036"], base=1, feature=6.0, lo=0.15, hi=0.15,
                     alpha=GLASS_A, rim=False, shade={"top": 0, "bottom": 0}),
        "deck": Mat(["#2a2a34", "#353542", "#424252"], base=1, feature=2.0, lo=0.15, hi=0.15),
        "anchor": Mat(["#1f8f88", "#2fc9bb", "#5fe3d4", "#94f2e6", "#c8fff8"], base=2, kind="crystal", feature=2.0,
                      pattern_scale=2.0, lo=0.1, hi=0.1, params={"line_frac": 0.12}),
        "lens": Mat(["#94f2e6", "#c8fff8", "#e6fffb"], base=1, feature=2.0, lo=0.1, hi=0.1),
        "brass": Mat(["#6b4a12", "#a8781e", "#e0b040", "#f7dd85"], base=2, feature=2.0, lo=0.1, hi=0.1),
    }


def _cube(m, part, tag):
    return [c for c in m[part].cubes if c.tag == tag][0]


def paint(sk, m):
    sides = ["front", "back", "left", "right"]
    # --- Stillglass panels: dark teal-black, translucent; mullions and a frame of iron; diagonal sheen streaks
    g = _cube(m, "side_n", "glass")
    front = sk.mask(cube=g, face=["front", "back"])
    fx, fy = sk.t_fx, sk.t_fy
    frame = front & ((fx == 0) | (fx == 39) | (fy == 0) | (fy == 19) | (fx == 13) | (fx == 26))
    sk.set(frame, "#262630", alpha=255)
    sk.set(front & ((fx == 13) | (fx == 26)) & (fy % 5 == 2), "#a8781e", alpha=255)  # rivets on the mullions
    sheen = front & ~frame & ((np.abs((fx % 13) - (19 - fy) * 0.55) < 0.6) | (np.abs((fx % 13) - (19 - fy) * 0.55 - 3) < 0.5))
    sk.set(sheen, "#2f4c54", alpha=GLASS_A + 20)
    sk.set(front & ~frame & (fy == 1), "#1b3036", alpha=GLASS_A)
    sk.set(sk.mask(cube=g, face=["top", "bottom", "left", "right"]), "#262630", alpha=255)
    # --- frame beams: iron bands at the beam ends, brass rivet rows
    for tag in ("rim",):
        c = _cube(m, "side_n", tag)
        sk.set(sk.mask(cube=c, face=sides, cond=lambda t: (t.fx % 6 == 3) & (t.fy == t.fh // 2) & (t.fw >= 8)),
               "#e0b040")
    rim = _cube(m, "side_n", "rim")
    sk.set(sk.mask(cube=rim, face=["front", "back", "top"], cond=lambda t: (t.fx < 2) | (t.fx >= t.fw - 2)), "#262630")
    wall = _cube(m, "side_n", "wall")
    sk.shift(sk.mask(cube=wall, face="front", cond=lambda t: (t.fx % 9 == 0)), -2)  # purpur plate seams
    sk.set(sk.mask(cube=wall, face="front", cond=lambda t: (t.fy == 0)), "#9c7aa6")
    post = _cube(m, "side_n", "post")
    sk.set(sk.mask(cube=post, face=sides, cond=lambda t: (t.fy % 5 == 2) & (t.fx == 1)), "#a8781e")
    sk.shift(sk.mask(cube=post, face=sides, cond=lambda t: t.fx == 0), 1)
    # --- floor deck: plank seams
    fl = _cube(m, "side_n", "floor")
    sk.shift(sk.mask(cube=fl, face="top", cond=lambda t: t.fy % 4 == 0), -1)
    # --- roof: iron plate with a ring of rivets around the core socket
    roof = [c for c in m["shell"].cubes if c.tag == "roof"][0]
    sk.set(sk.mask(cube=roof, face="top", cond=lambda t: ((t.fx == 1) | (t.fx == 22) | (t.fy == 1) | (t.fy == 22)) &
                   ((t.fx + t.fy) % 3 == 0)), "#a8781e")
    # --- Gravity Anchor core: glowing teal crystal, white-hot centre on every face
    an = _cube(m, "core", "anchor")
    sk.glow(sk.mask(cube=an), TEAL, 200)
    c2 = sk.mask(cube=an, cond=lambda t: (np.abs(t.fx - 2.5) < 1.1) & (np.abs(t.fy - 2.5) < 1.1))
    sk.set(c2, TEAL_HI, emit=TEAL_HI, emit_alpha=255)
    sock = _cube(m, "core", "socket")
    sk.set(sk.mask(cube=sock, face="top", cond=lambda t: (np.abs(t.fx - 4.5) < 3.6) & (np.abs(t.fy - 4.5) < 3.6) &
                   ~((np.abs(t.fx - 4.5) < 2.6) & (np.abs(t.fy - 4.5) < 2.6))), TEAL_DIM, emit=TEAL, emit_alpha=150)
    sk.set(sk.mask(cube=sock, face=sides, cond=lambda t: t.fy == 0), "#4a4b5c")
    # --- lamp underneath: iron housing with a glowing teal-white lens and a glowing rim slit
    lens = _cube(m, "lamp", "lens")
    sk.set(sk.mask(cube=lens), "#c8fff8", emit="#e6fffb", emit_alpha=255)
    hs = _cube(m, "lamp", "housing")
    sk.set(sk.mask(cube=hs, face=sides, cond=lambda t: t.fy == t.fh - 1), "#5fe3d4", emit=TEAL, emit_alpha=200)
    # --- hatch: riveted iron door with a round dark porthole (gold rim) and a brass wheel handle
    door = _cube(m, "hatch", "door")
    for face in ("front", "back"):
        d = sk.mask(cube=door, face=face)
        r = np.hypot(sk.t_fx - 7.5, sk.t_fy - 7.0)
        sk.set(d & (r < 4.6), "#c9a24a")
        sk.set(d & (r < 3.6), "#0b161a", alpha=GLASS_A + 30)
        sk.set(d & (r < 3.6) & (np.abs((sk.t_fx - 7.5) + (sk.t_fy - 7.0)) < 0.7), "#2f4c54", alpha=GLASS_A + 40)
        sk.set(d & ((sk.t_fx == 0) | (sk.t_fx == 15) | (sk.t_fy == 0) | (sk.t_fy == 19)), "#4a4b5c")
        sk.set(d & ((sk.t_fx == 1) | (sk.t_fx == 14)) & (sk.t_fy % 3 == 1), "#a8781e")
        sk.set(d & (sk.t_fy >= 14) & (sk.t_fy <= 17) & (sk.t_fx >= 3) & (sk.t_fx <= 12) & (sk.t_fy % 2 == 0), "#1a1a22")
