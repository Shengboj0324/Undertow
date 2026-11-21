"""Warden banner pattern (expansion spec F): textures/entity/banner/warden.png - a 64x64 pattern mask in the VANILLA
banner pattern format (near-white on transparent; the game tints it with the dye colour), drawn on the flag's front
face (1,1)-(20,40) and mirrored onto its back face (22,1)-(41,40) like vanilla's masks.  Motif: a dragon wing folded
over a pillar - solid bones, slightly translucent membrane (alpha 191, as vanilla uses for soft areas), a fluted pillar.
The shield version is models/warden_shield.py.  Only vanilla's layout was read; the motif is drawn here.
Preview: the vanilla banner geometry with the mask tinted warden gold over a black cloth (composite in the temp dir)."""
import os
import tempfile

import numpy as np

from mclib.geom import Model
from mclib.mat import Mat

TMP = os.environ.get("WF_MODEL_TMP", os.path.join(tempfile.gettempdir(), "worldsfall_model_tmp"))
BONE, MEMB, FLUTE = (243, 243, 243, 255), (227, 227, 227, 191), (227, 227, 227, 255)


def poly_mask(w, h, pts):
    """Even-odd fill of a polygon given in texel coordinates, sampled at texel centres."""
    yy, xx = np.mgrid[0:h, 0:w] + 0.5
    inside = np.zeros((h, w), bool)
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        cond = ((y1 > yy) != (y2 > yy))
        xint = (x2 - x1) * (yy - y1) / ((y2 - y1) if y2 != y1 else 1e-9) + x1
        inside ^= cond & (xx < xint)
    return inside


def line_mask(w, h, p0, p1, width=1):
    m = np.zeros((h, w), bool)
    n = int(max(abs(p1[0] - p0[0]), abs(p1[1] - p0[1])) * 2) + 1
    for i in range(n + 1):
        t = i / n
        x = p0[0] + (p1[0] - p0[0]) * t
        y = p0[1] + (p1[1] - p0[1]) * t
        for dx in range(width):
            xi, yi = int(np.floor(x)) + dx, int(np.floor(y))
            if 0 <= xi < w and 0 <= yi < h:
                m[yi, xi] = True
    return m


def dilate(m):
    o = m.copy()
    o[1:] |= m[:-1]; o[:-1] |= m[1:]; o[:, 1:] |= m[:, :-1]; o[:, :-1] |= m[:, 1:]
    return o


def motif(w, h, s):
    """The wing-over-pillar motif on a w x h face; s = design scale (1.0 = 20x40 banner)."""
    def P(x, y):
        return (x * s, y * s)
    img = np.zeros((h, w, 4), np.int32)
    # pillar (a little left of centre): plinth, base, fluted shaft, capital
    pil = np.zeros((h, w), bool)
    pil |= poly_mask(w, h, [P(2, 36), P(16, 36), P(16, 38), P(2, 38)])
    pil |= poly_mask(w, h, [P(4, 34), P(14, 34), P(14, 36), P(4, 36)])
    pil |= poly_mask(w, h, [P(6, 14), P(11, 14), P(11, 34), P(6, 34)])
    pil |= poly_mask(w, h, [P(4, 12), P(13, 12), P(13, 14), P(4, 14)])
    # wing perched on the capital and folded down over the pillar's right shoulder: membrane with a scalloped
    # trailing edge between the finger tips, then the bones (arm, three fingers, thumb claw) on top
    memb = poly_mask(w, h, [P(10.0, 4.4), P(18.2, 9.4), P(16.0, 11.4), P(17.6, 16.8), P(14.8, 16.2), P(14.4, 22.8),
                            P(12.6, 19.8), P(12.6, 13.2), P(7.0, 12.0)])
    bones = np.zeros((h, w), bool)
    bw = 2 if s >= 0.9 else 1
    bones |= line_mask(w, h, P(6.6, 11.6), P(10.0, 4.2), bw)  # arm: shoulder (on the capital) -> wrist
    for tip in ((18.0, 9.6), (17.2, 16.6), (14.2, 22.4)):
        bones |= line_mask(w, h, P(10.4, 4.6), P(*tip), 1)
    bones |= line_mask(w, h, P(10.6, 4.0), P(12.2, 2.0), 1)  # the thumb claw at the wrist
    wing = memb | bones
    # a one-texel gap between wing and pillar so the two shapes read separately once tinted
    pil &= ~dilate(wing) if s >= 0.9 else ~wing
    img[pil] = BONE
    if s >= 0.9:
        flute = pil & (np.mgrid[0:h, 0:w][1] == int(8 * s)) & (np.mgrid[0:h, 0:w][0] > int(15 * s))
        img[flute] = FLUTE
    img[memb] = MEMB
    img[bones] = BONE
    return img


def build():
    m = Model("warden_banner", (64, 64))
    flag = m.part("flag", None, (0, -29, 0))
    flag.cube((-10, 0, -2), (20, 40, 1), uv=(0, 0), mat="cloth")
    pole = m.part("pole", None, (0, -21, 0))
    pole.cube((-1, -10, -1), (2, 42, 2), uv=(44, 0), mat="wood")
    bar = m.part("bar", None, (0, -30, 0))
    bar.cube((-10, -1, -1), (20, 2, 2), uv=(0, 42), mat="wood")
    m.pack()
    return dict(model=m, json=False, mats=mats(), textures=[
        dict(path=os.path.join(TMP, "warden_banner_preview"), paint=lambda sk: preview(sk)),
        dict(path="banner/warden", paint=lambda sk: paint_mask(sk)),
    ], preview=dict(az=-24, el=8, lens=85, margin=0.84))


def mats():
    return {
        "cloth": Mat(["#141018", "#1d1824", "#272030"], base=1, feature=3.0, lo=0.2, hi=0.2, rim=False,
                     shade={"top": 0, "bottom": 0}),
        "wood": Mat(["#4a3520", "#6b5030", "#8a6a40", "#a8854f"], base=2, feature=2.0, lo=0.15, hi=0.15),
    }


def banner_mask():
    face = motif(20, 40, 1.0)
    out = np.zeros((64, 64, 4), np.int32)
    out[1:41, 1:21] = face
    out[1:41, 22:42] = face[:, ::-1]  # back face: mirrored, as in vanilla's masks
    return out


def paint_mask(sk):
    mk = banner_mask()
    sk.used = np.ones_like(sk.used)  # the mask is not a skin: write every texel as drawn
    sk.override[:] = mk
    sk.has_override[:] = True
    sk.alpha[:] = mk[:, :, 3]


def tint_over(sk, mk, dye):
    base = sk.finish()[0].astype(np.float64)
    a = mk[:, :, 3:4] / 255.0
    col = mk[:, :, :3] / 255.0 * np.array(dye, np.float64)
    base[:, :, :3] = col * a + base[:, :, :3] * (1 - a)
    sk.override[:] = np.round(base).astype(np.int32)
    sk.has_override[:] = True


def preview(sk):
    tint_over(sk, banner_mask(), (0xC9, 0xA2, 0x4A))
