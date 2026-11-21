"""Shulker Archivist skin (expansion spec F): textures/entity/shulker_archivist.png, 64x64 in the VANILLA shulker UV
layout (lid 16x12x16 @ 0,0; base 16x8x16 @ 0,28; head 6x6x6 @ 0,52).  A peaceful archivist: a pale, dusty, bone-white
shell (#D8D2C4 / #B8B0A0) with a faint carved glyph on the lid, a grey-violet head with calm pale eyes.

Only the vanilla texture's alpha mask is read (to match the layout exactly); every pixel is painted here."""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from mclib.png import read_png
from mclib.paths import vanilla

F = np.floor
# carved glyph for the lid top (16x16, '#' = carved line): an open book under a rising star - the archive's mark
GLYPH = [
    "................",
    "................",
    ".......#........",
    "......###.......",
    ".......#........",
    "................",
    "...####.####....",
    "..#....#....#...",
    "..#.##.#.##.#...",
    "..#....#....#...",
    "..#.##.#.##.#...",
    "..#....#....#...",
    "...####.####....",
    ".......#........",
    "................",
    "................",
]


def build():
    m = Model("shulker_archivist", (64, 64))
    lid = m.part("lid", None, (0, 13, 0))  # open pose for the preview (vanilla rest is y = 24)
    lid.cube((-8, -16, -8), (16, 12, 16), uv=(0, 0), mat={"*": "shell", "bottom": "inner"})
    base = m.part("base", None, (0, 24, 0))
    base.cube((-8, -8, -8), (16, 8, 16), uv=(0, 28), mat={"*": "shell", "top": "inner"})
    head = m.part("head", None, (0, 12, 0))
    head.cube((-3, 0, -3), (6, 6, 6), uv=(0, 52), mat="head")
    m.pack()
    van = read_png(vanilla("textures/entity/shulker/shulker.png"))
    return dict(model=m, json=False, mats=mats(), textures=[
        dict(path="shulker_archivist", paint=lambda sk: paint(sk, m, van))],
        preview=dict(az=-32, el=16, lens=85, margin=0.7))


def mats():
    return {
        "shell": Mat(["#8f8778", "#a89f8e", "#b8b0a0", "#c9c2b3", "#d8d2c4", "#e8e3d8"], base=3, kind="lichen",
                     feature=2.5, pattern_scale=3.0, lo=0.15, hi=0.18, extra={"lichen": ["#c4bba8", "#ddd6c6"]},
                     params={"cover": 0.12, "b_scale": 1.3}),
        "inner": Mat(["#5e584e", "#776f62", "#8f8778"], base=1, feature=2.0, lo=0.15, hi=0.15),
        "head": Mat(["#4a4458", "#5c566c", "#6e6880", "#847e96"], base=2, feature=2.0, lo=0.15, hi=0.12),
    }


def paint(sk, m, van):
    lidc = m["lid"].cubes[0]
    basec = m["base"].cubes[0]
    headc = m["head"].cubes[0]
    sides = ["front", "back", "left", "right"]
    # shell plates: our own seams - a ledge line 4 rows above the lid rim, vertical joints every 4 texels below it,
    # a lit top row; the base gets matching joints offset by 2 so the two halves interlock
    sk.shift(sk.mask(cube=lidc, face=sides, cond=lambda t: t.fy == 7), -2)
    sk.shift(sk.mask(cube=lidc, face=sides, cond=lambda t: (t.fy >= 8) & (t.fx % 4 == 3)), -1)
    sk.shift(sk.mask(cube=lidc, face=sides, cond=lambda t: t.fy == 8), 1)
    sk.shift(sk.mask(cube=lidc, face=sides, cond=lambda t: t.fy == t.fh - 1), -1)
    sk.shift(sk.mask(cube=basec, face=sides, cond=lambda t: (t.fx % 4 == 1)), -1)
    sk.shift(sk.mask(cube=basec, face=sides, cond=lambda t: t.fy == 0), 1)
    sk.shift(sk.mask(cube=basec, face=sides, cond=lambda t: t.fy == t.fh - 1), -1)
    # dust: pale speckles collected on the upper surfaces
    top = sk.mask(cube=lidc, face="top")
    sk.set(top & (sk.B > np.quantile(sk.B[top], 0.93)), "#eeeae2")
    # lid top: a rim, and the faint carved glyph (one step darker, with a lit lower edge = engraved)
    sk.shift(top & ((sk.t_fx == 0) | (sk.t_fx == 15) | (sk.t_fy == 0) | (sk.t_fy == 15)), -1)
    g = np.array([[ch == "#" for ch in r] for r in GLYPH])
    fx = np.clip(sk.t_fx.astype(int), 0, 15)
    fy = np.clip(sk.t_fy.astype(int), 0, 15)
    carved = top & g[fy, fx]
    sk.set(carved, "#a89f8e")
    below = top & ~carved & g[np.clip(fy - 1, 0, 15), fx]
    sk.set(below, "#e8e3d8")
    # inside of the shell: darker, with a soft spiral of growth rings
    for c, face in ((lidc, "bottom"), (basec, "top")):
        r = sk.mask(cube=c, face=face)
        rr = np.hypot(sk.t_fx - 7.5, sk.t_fy - 7.5)
        sk.shift(r & (F(rr) % 3 == 0), -1)
    # head: grey-violet, calm pale eyes (half-closed lids), a small mouth line
    hf = sk.mask(cube=headc, face="front")
    sk.set(hf & (sk.t_fy == 2) & ((sk.t_fx == 1) | (sk.t_fx == 4)), "#e8e6f4")
    sk.set(hf & (sk.t_fy == 1) & ((sk.t_fx == 1) | (sk.t_fx == 4)), "#4a4458")  # heavy, calm lids
    sk.set(hf & (sk.t_fy == 3) & ((sk.t_fx == 1) | (sk.t_fx == 4)), "#847e96")  # soft lower lids
    sk.set(hf & (sk.t_fy == 4) & (sk.t_fx >= 2) & (sk.t_fx <= 3), "#3a3448")
    sk.shift(sk.mask(cube=headc, face="top"), 1)
    # keep the vanilla layout exactly: opaque where vanilla is opaque, transparent elsewhere
    sk.alpha[:] = np.where(sk.used & (van[:, :, 3] > 0), 255, 0)
