"""Scaled elytra trims (expansion spec F): 64x32 skins in the vanilla elytra layout, like models/elytra_upgrades.py.

  elytra_scaled.png         vanilla elytra base with overlapping obsidian-and-gold scales over the membrane
  elytra_gale_scaled.png    the same scales on the Gale wing, its teal veins left showing through
  elytra_still_scaled.png   ... on the Still wing, keeping its dark mirror tip edge
  elytra_hover_scaled.png   ... on the Hover wing, keeping its two teal shoulder nodes
The upgrade's identifying texels are found by comparing the shipped upgrade skin with vanilla (read-only) and are kept
on top of the scales.  Preview: the vanilla ElytraModel geometry, rebuilt only for the render (no JSON)."""
import os

import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from mclib.png import read_png
from mclib.paths import vanilla

HERE = os.path.dirname(os.path.abspath(__file__))
WINGS = os.path.abspath(os.path.join(HERE, "..", "..", "..", "..", "src", "main", "resources", "assets", "worldsfall",
                                     "textures", "entity", "equipment", "wings"))
OBS = ["#0c0812", "#16101e", "#221a2c", "#2f273b", "#40364e"]
GOLD, GOLD_DIM, GOLD_HI = "#c9a24a", "#8a6d32", "#e8cd7a"


def elytra_model(name):
    m = Model(name, (64, 32))
    lw = m.part("left_wing", None, (5, 4, 2), (15, 0, -15))
    lw.cube((-10, 0, 0), (10, 20, 2), uv=(22, 0), inflate=1.0, mat="base", share="w")
    rw = m.part("right_wing", None, (-5, 4, 2), (15, 0, 15))
    rw.cube((0, 0, 0), (10, 20, 2), uv=(22, 0), inflate=1.0, mirror=True, mat="base", share="w", bake=False)
    m.pack()
    return m


def build():
    m = elytra_model("elytra_scaled")
    van = read_png(vanilla("textures/entity/equipment/wings/elytra.png"))
    T = [dict(path="equipment/wings/elytra_scaled", paint=lambda sk: paint(sk, van, van))]
    for kind in ("gale", "still", "hover"):
        up = read_png(os.path.join(WINGS, "elytra_%s.png" % kind))
        T.append(dict(path="equipment/wings/elytra_%s_scaled" % kind,
                      paint=(lambda u: lambda sk: paint(sk, van, u))(up)))
    return dict(model=m, json=False, mats={"base": Mat(["#000000"], base=0, lo=0, hi=0, rim=False)}, textures=T,
                preview=dict(az=160, el=12, lens=85, margin=0.8, res=1024,
                             extra=[dict(suffix="_gale", texture=1, res=1024), dict(suffix="_still", texture=2, res=1024),
                                    dict(suffix="_hover", texture=3, res=1024)]))


def wing_mask(img):
    """The outer membrane surface of the elytra sheet: the wing's outer edge column pair and its back face."""
    a = img[:, :, 3] > 0
    yy, xx = np.mgrid[0:img.shape[0], 0:img.shape[1]]
    return a & (xx >= 34) & (xx < 48) & (yy >= 2) & (yy < 24)


def scales(img, keep=None, seed=0):
    """Paint overlapping obsidian scales with gold rims over the membrane of `img` (RGBA int array, modified)."""
    out = img.copy()
    mk = wing_mask(img)
    if keep is not None:
        mk &= ~keep
    yy, xx = np.mgrid[0:img.shape[0], 0:img.shape[1]]
    lum = img[:, :, :3].mean(2)
    row = (yy - 2) // 3
    u = (xx - 34 + 2 * (row % 2)) % 4
    v = (yy - 2) % 3
    # body shade follows the vanilla elytra's own light falloff (dark at the shoulder, lighter towards the tip)
    q = np.quantile(lum[mk], [0.35, 0.7]) if mk.any() else (0, 0)
    lvl = np.where(lum < q[0], 1, np.where(lum > q[1], 3, 2))
    pal = np.array([[int(c[i:i + 2], 16) for i in (1, 3, 5)] for c in OBS])
    out[mk, :3] = pal[lvl[mk]]
    # each 4x3 scale: a lit top-left texel, a dark gap between neighbours, a two-texel gold rim along its lower edge
    hi = mk & (v == 0) & (u == 1)
    out[hi, :3] = pal[np.minimum(lvl[hi] + 1, 4)]
    gap = mk & (u == 0) & (v < 2)
    out[gap, :3] = pal[0]
    gold = np.array([int(GOLD[i:i + 2], 16) for i in (1, 3, 5)])
    dim = np.array([int(GOLD_DIM[i:i + 2], 16) for i in (1, 3, 5)])
    ghi = np.array([int(GOLD_HI[i:i + 2], 16) for i in (1, 3, 5)])
    rim = mk & (v == 2) & ((u == 1) | (u == 2))
    out[rim, :3] = dim
    lit = rim & (u == 1)
    out[lit, :3] = gold
    spark = lit & (row % 3 == 0)
    out[spark, :3] = ghi
    # the outer edge column (wing thickness) stays a plain gold trim
    edge = mk & (xx < 36) & (yy >= 2)
    out[edge, :3] = np.where(((yy[edge] // 3) % 2 == 0)[:, None], gold, dim)
    return out


def paint(sk, van, upgrade):
    """Scales over the upgrade wing; the upgrade's own feature texels (where it differs from vanilla) stay on top."""
    up = upgrade.astype(np.int32)
    feat = (up != van.astype(np.int32)).any(-1) & (up[:, :, 3] > 0)
    img = scales(up, keep=feat)
    sk.override[:] = img
    sk.has_override[:] = True
    sk.alpha[:] = img[:, :, 3]
