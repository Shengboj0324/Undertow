"""Wild Shulker skins (8.6): vanilla shulker geometry + UV layout (64x64) rebuilt in Blender, texture only.
Weathered, lichen-dusted shell in muted purpur / pale end-stone tones; molted = thin, pale shell."""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from mclib.png import read_png
from mclib.paths import vanilla


def build():
    m = Model("wild_shulker", (64, 64))
    lid = m.part("lid", None, (0, 18, 0))  # peeking pose for the preview (vanilla rest is y = 24)
    lid.cube((-8, -16, -8), (16, 12, 16), uv=(0, 0), mat={"*": "shell", "bottom": "inner"})
    base = m.part("base", None, (0, 24, 0))
    base.cube((-8, -8, -8), (16, 8, 16), uv=(0, 28), mat={"*": "shell", "top": "inner"})
    head = m.part("head", None, (0, 12, 0))
    head.cube((-3, 0, -3), (6, 6, 6), uv=(0, 52), mat="head")
    m.pack()
    van = read_png(vanilla("textures/entity/shulker/shulker.png"))
    return dict(model=m, json=False, mats=mats(False), textures=[
        dict(path="wild_shulker/wild_shulker", paint=lambda sk: paint(sk, m, van, False)),
        dict(path="wild_shulker/wild_shulker_molted", paint=lambda sk: paint(sk, m, van, True), mats=mats(True)),
    ], preview=dict(az=-32, el=16, lens=85, margin=0.7, extra=[dict(suffix="_molted", texture=1)]))


def mats(molted):
    if not molted:
        shell = ["#4e3f55", "#65536c", "#7b6782", "#917c95", "#a895a8"]
        inner = ["#3a2d40", "#4e3f55", "#65536c"]
    else:
        shell = ["#a99db0", "#bdb2c4", "#d0c7d6", "#e2dbe6", "#f0ebf2"]
        inner = ["#8f8398", "#a99db0", "#bdb2c4"]
    return {
        "shell": Mat(shell, base=2, kind="lichen", feature=2.5, pattern_scale=3.0, lo=0.15, hi=0.15,
                     extra={"lichen": ["#a7ad86", "#d8d2a0"] if not molted else ["#e8e4cf", "#f4f1e2"]},
                     params={"cover": 0.16 if not molted else 0.05, "b_scale": 1.3}),
        "inner": Mat(inner, base=1, feature=2.0, lo=0.15, hi=0.15),
        "head": Mat(["#a8a46c", "#c9c48c", "#dcd8a6", "#ebe8c2"], base=2, feature=2.0, lo=0.15, hi=0.1),
    }


def paint(sk, m, van, molted):
    # keep the exact vanilla alpha layout; reuse vanilla's dark panel lines (the "plates" of the shell)
    a = van[:, :, 3].astype(np.int32)
    lum = van[:, :, :3].astype(np.float64) @ np.array([0.3, 0.59, 0.11])
    shell = sk.mask(mat="shell")
    lines = shell & (lum < np.quantile(lum[shell & (a > 0)], 0.22))
    sk.shift(lines, -1 if not molted else -1)
    hi = shell & (lum > np.quantile(lum[shell & (a > 0)], 0.85))
    sk.shift(hi, 1)
    # base underside spiral, like vanilla, but worn
    inner = sk.mask(mat="inner")
    spiral = inner & (lum < np.quantile(lum[inner & (a > 0)], 0.5))
    sk.shift(spiral, -1)
    # head: the creature inside is unchanged - keep vanilla's face but in pale end-stone tones
    head = sk.mask(mat="head")
    dark = head & (lum < 70)
    sk.set(dark, "#2a2430")
    if molted:
        # thin shell: faint vertical growth lines, nearly no lichen
        sk.set(shell & (sk.t_fx % 5 == 2) & (sk.t_face >= 2) & (sk.R > 0.5), "#c6bccc")
    sk.alpha[:] = np.where(sk.used, a, 0)
