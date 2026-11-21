"""Tempest elytra (expansion spec F): the stronger Gale variant - Gale's teal veins become bright silver-white storm
streaks (jagged, branching, brightest at the shoulder) over a storm-darkened membrane.  64x32, vanilla elytra layout.
  elytra_tempest.png          the Tempest wing
  elytra_tempest_scaled.png   with the obsidian-and-gold scale trim, the storm streaks left showing through
Preview: vanilla ElytraModel geometry rebuilt for the render only (no JSON)."""
import numpy as np

from mclib.mat import Mat
from mclib.png import read_png
from mclib.paths import vanilla
from models.elytra_scaled import elytra_model, scales, wing_mask

WHITE, SILVER_HI, SILVER, SILVER_DIM = (250, 252, 255), (223, 232, 255), (174, 182, 200), (120, 130, 156)
# the Gale vein fan (same root and directions as elytra_upgrades.py), redrawn as forked lightning
STREAKS = [((37, 3), (39, 21)), ((37, 3), (42, 20)), ((37, 3), (44, 16)), ((37, 3), (45, 11))]
FORKS = [((40, 9), (43, 7)), ((39, 13), (37, 16)), ((42, 14), (44, 19))]


def build():
    m = elytra_model("elytra_tempest")
    van = read_png(vanilla("textures/entity/equipment/wings/elytra.png"))
    return dict(model=m, json=False, mats={"base": Mat(["#000000"], base=0, lo=0, hi=0, rim=False)}, textures=[
        dict(path="equipment/wings/elytra_tempest", paint=lambda sk: paint(sk, van, False)),
        dict(path="equipment/wings/elytra_tempest_scaled", paint=lambda sk: paint(sk, van, True)),
    ], preview=dict(az=160, el=12, lens=85, margin=0.8, res=1024, extra=[dict(suffix="_scaled", texture=1, res=1024)]))


def jagged(p0, p1, amp=1):
    """Pixel line from p0 to p1 that kinks sideways by +-amp every few texels (a lightning stroke)."""
    n = int(max(abs(p1[0] - p0[0]), abs(p1[1] - p0[1]))) + 1
    pts = []
    for i in range(n):
        t = i / max(n - 1, 1)
        x = p0[0] + (p1[0] - p0[0]) * t
        y = p0[1] + (p1[1] - p0[1]) * t
        k = (i // 3) % 4
        off = (0, amp, 0, -amp)[k] if 2 < i < n - 1 else 0
        pts.append((int(round(x + off)), int(round(y))))
    return pts


def storm(van):
    img = van.astype(np.int32).copy()
    mk = wing_mask(img)
    # storm-darkened membrane: pull the grey toward a deep slate-blue so the streaks read as light
    tint = np.array([40, 46, 74])
    img[mk, :3] = (img[mk, :3] * 0.4 + tint * 0.6).astype(np.int32)
    op = img[:, :, 3] > 0
    streak = np.zeros(op.shape, bool)

    def put(pts, col):
        for x, y in pts:
            if 0 <= y < op.shape[0] and 0 <= x < op.shape[1] and op[y, x]:
                img[y, x, :3] = col
                streak[y, x] = True
    for p0, p1 in STREAKS:
        pts = jagged(p0, p1)
        n = len(pts)
        put(pts[n // 2:], SILVER)
        put(pts[3:n // 2], SILVER_HI)
        put(pts[:3], WHITE)
    for p0, p1 in FORKS:
        put(jagged(p0, p1), SILVER_DIM)
    put([(37, 3), (38, 3)], WHITE)
    return img, streak


def paint(sk, van, scaled):
    img, streak = storm(van)
    if scaled:
        img = scales(img, keep=streak)
    sk.override[:] = img
    sk.has_override[:] = True
    sk.alpha[:] = img[:, :, 3]
