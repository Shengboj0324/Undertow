"""Glimmer - a tiny fish of light (about 4 x 2 x 6 px incl. fins and tail) that schools in the Undertow; almost entirely emissive pale teal.
Parts: body, tail.  (expansion spec E)"""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat

F = np.floor
TEAL_HI, TEAL, TEAL_MID, TEAL_DIM = "#e6fffb", "#b8fff7", "#7ff2e2", "#3ae0d0"


def build():
    m = Model("glimmer", (32, 32))
    b = m.part("body", None, (0, 22, 0))
    b.cube((-1, -1, -2), (2, 2, 3), mat="light", tag="body")
    b.cube((-0.5, -0.5, -3), (1, 1, 1), mat="light", tag="nose")  # tapered snout
    b.cube((-0.5, -1.5, -1), (1, 1, 2), mat="light", tag="dorsal")  # tiny raised back
    b.cube((-2, 0, -2), (4, 0, 2), mat="fin", tag="fins")  # pectoral fins: one flat plane through the body
    t = m.part("tail", "body", (0, 0, 1))
    t.cube((-0.5, -0.5, 0), (1, 1, 1), mat="light", tag="stalk")
    t.cube((0, -1.5, 1), (0, 3, 2), mat="fin", tag="fin")  # vertical forked tail fin
    m.pack()
    return dict(model=m, mats={
        "light": Mat(["#2fc9bb", "#7ff2e2", "#b8fff7", "#e6fffb"], base=2, feature=2.0, lo=0.15, hi=0.15,
                     shade={"top": 1, "bottom": -1}),
        "fin": Mat(["#2fc9bb", "#5fe3d4", "#7ff2e2", "#b8fff7"], base=2, feature=2.0, lo=0.1, hi=0.1, rim=False,
                   shade={"top": 0, "bottom": 0}),
    }, textures=[dict(path="glimmer/glimmer", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-50, el=24, lens=85, margin=0.62), emit_strength=1.4)


def paint(sk, m):
    body = [c for c in m["body"].cubes if c.tag == "body"][0]
    # every texel glows; the belly half a step dimmer, the back brightest
    sk.glow(sk.used, TEAL, 210)
    sk.glow(sk.mask(face="bottom"), TEAL_DIM, 170)
    sk.glow(sk.mask(face="top"), TEAL_HI, 235)
    # head: the snout is the brightest point (a fish of pure light: no eyes)
    nose = [c for c in m["body"].cubes if c.tag == "nose"][0]
    sk.set(sk.mask(cube=nose), TEAL_HI, emit="#ffffff", emit_alpha=255)
    # belly row of the flanks a little dimmer so the body keeps a readable form when it glows
    sk.set(sk.mask(cube=body, face=["left", "right"], cond=lambda t: t.fy == 1), TEAL_MID, emit=TEAL_MID, emit_alpha=190)
    # fins: soft, half-transparent, a little dimmer than the body
    fins = [c for c in m.cubes() if c.tag in ("fins", "fin")]
    for c in fins:
        mk = sk.mask(cube=c)
        sk.set(mk, TEAL_MID, alpha=190)
        sk.glow(mk, TEAL_MID, 170)
    # forked tail: notch out the middle of the trailing edge
    fin = [c for c in m["tail"].cubes if c.tag == "fin"][0]
    sk.cut_shape(fin, lambda x, y, z: ~((z > 1.0) & (np.abs(y - 1.5) < 0.6)))
    # pectoral plane: only the two outer texels (the fins) stay, the middle hides inside the body anyway
    pf = [c for c in m["body"].cubes if c.tag == "fins"][0]
    sk.cut_shape(pf, lambda x, y, z: (x < 1.0) | (x > 3.0))
    # a darker spine line along the back reads as the tiny body inside the light
    sk.set(sk.mask(cube=body, face="top", cond=lambda t: t.fx == 1), TEAL_MID, emit=TEAL_MID, emit_alpha=200)
