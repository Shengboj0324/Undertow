"""Armor trim textures (§9.11): the three patterns drawn onto the vanilla humanoid armor UV layout, plus the three
material palettes.  Patterns are greyscale in the vanilla trim_base ramp (224..0) and get recoloured by the palette
at runtime through each texture's `palette` mcmeta section.

Run: python3 tools/gen/gen_trims.py <path to an extracted 26.3 client jar's assets/minecraft/textures>
(the vanilla iron armor textures are only read as coverage masks; nothing vanilla is copied)."""
import json
import math
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src/main/resources/assets/worldsfall/textures")
VAN = sys.argv[1] if len(sys.argv) > 1 else None
BASE = [224, 192, 160, 128, 96, 64, 32, 0]

# Faces of the 64x32 humanoid layout: (x0, y0, w, h, kind, height_offset). kind picks how a pattern runs over the face;
# height_offset places the face on a continuous "body height" axis (0 = top of helmet) so vertical lines line up.
HEAD = [(8, 8, 8, 8, "front", 0), (0, 8, 8, 8, "side", 0), (16, 8, 8, 8, "side", 0), (24, 8, 8, 8, "back", 0),
        (8, 0, 8, 8, "top", 0), (16, 0, 8, 8, "top", 0)]
BODY = [(20, 20, 8, 12, "front", 8), (32, 20, 8, 12, "back", 8), (16, 20, 4, 12, "side", 8), (28, 20, 4, 12, "side", 8),
        (20, 16, 8, 4, "top", 8), (28, 16, 8, 4, "top", 8)]
ARM = [(44, 20, 4, 12, "front", 8), (40, 20, 4, 12, "side", 8), (48, 20, 4, 12, "side", 8), (52, 20, 4, 12, "back", 8),
       (44, 16, 4, 4, "top", 8), (48, 16, 4, 4, "top", 8)]
LEG = [(4, 20, 4, 12, "front", 20), (0, 20, 4, 12, "side", 20), (8, 20, 4, 12, "side", 20), (12, 20, 4, 12, "back", 20),
       (4, 16, 4, 4, "top", 20), (8, 16, 4, 4, "top", 20)]
FACES = HEAD + BODY + ARM + LEG


def mask(name):
    if VAN:
        path = os.path.join(VAN, "entity/equipment", name, "iron.png")
        if os.path.exists(path):
            im = Image.open(path).convert("RGBA")
            return lambda x, y: im.getpixel((x, y))[3] > 0
    return lambda x, y: True


def spire(u, v, w, h, kind, hy):
    """Vertical tower lines rising from the boots to the helmet, with stepped battlements every 6 px of height."""
    if kind == "top":
        return None
    y = hy + v
    cols = (1, w - 2) if w >= 8 else (w // 2,)
    if kind == "side" and w < 8:
        cols = (w // 2,) if (y // 6) % 2 == 0 else ()
    for c in cols:
        if u == c:
            return 0 if y % 6 == 0 else (1 if y % 6 < 3 else 2)
        if abs(u - c) == 1 and y % 6 == 0:
            return 3
    if kind == "front" and w >= 8 and u in (3, 4) and y % 12 in (5, 6):
        return 1
    return None


def drift(u, v, w, h, kind, hy, ox):
    """Horizontal flowing wave lines around the body, phase-continuous between neighbouring faces."""
    if kind == "top":
        return None
    y = hy + v
    for band in range(2, 40, 7):
        wave = band + round(1.3 * math.sin((u + ox) * 0.75))
        if y == wave:
            return 0 if (u + ox) % 5 else 1
        if y == wave + 1:
            return 4
    return None


def undersong(u, v, w, h, kind, hy):
    """Branching lines hanging downward from the shoulders, thinning and darkening as they fall."""
    y = hy + v
    if hy == 0:
        return 2 if kind == "front" and v == 7 and 1 <= u <= 6 else None  # a thin brow band only
    if kind == "top":
        return 0 if hy == 8 and v == h - 1 else None  # shoulder seam the roots hang from
    depth = max(0, y - 8)
    roots = (2, w - 3) if w >= 8 else (w // 2,)
    for i, r in enumerate(roots):
        sway = round(math.sin(depth * 0.45 + i * 2.1) * 0.9)
        x = r + sway
        if u == x and depth < 24:
            return min(6, depth // 5)
        if depth % 5 == 3 and 0 < abs(u - x) <= 1 + depth // 10 and depth < 20:
            return min(6, 2 + depth // 6)
    return None


def draw(pattern, layer):
    im = Image.new("L", (64, 32), 0)
    alpha = Image.new("L", (64, 32), 0)
    ok = mask(layer)
    leggings = layer == "humanoid_leggings"
    for x0, y0, w, h, kind, hy in FACES:
        part = (x0, y0) in [(f[0], f[1]) for f in LEG] or (x0, y0) in [(f[0], f[1]) for f in BODY]
        if leggings and not part:
            continue
        ox = {"front": 0, "side": 8, "back": 12}.get(kind, 0)
        for u in range(w):
            for v in range(h):
                x, y = x0 + u, y0 + v
                if not ok(x, y):
                    continue
                if pattern == "drift":
                    s = drift(u, v, w, h, kind, hy, ox)
                elif pattern == "spire":
                    s = spire(u, v, w, h, kind, hy)
                else:
                    s = undersong(u, v, w, h, kind, hy)
                if s is not None:
                    im.putpixel((x, y), BASE[s])
                    alpha.putpixel((x, y), 255)
    out = Image.merge("LA", (im, alpha))
    d = os.path.join(OUT, "trims/entity", layer)
    os.makedirs(d, exist_ok=True)
    out.save(os.path.join(d, pattern + ".png"))
    with open(os.path.join(d, pattern + ".png.mcmeta"), "w") as f:
        json.dump({"palette": {"base_palette": "trim_base"}}, f, indent=2)


def ramp(stops):
    """8-entry palette, brightest first, interpolated through (t, rgb) stops."""
    out = []
    for i in range(8):
        t = i / 7
        for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
            if t0 <= t <= t1:
                k = (t - t0) / (t1 - t0) if t1 > t0 else 0
                out.append(tuple(round(a + (b - a) * k) for a, b in zip(c0, c1)))
                break
    return out


PALETTES = {
    # teal with a bright highlight
    "lumen": ramp([(0, (196, 255, 246)), (0.2, (78, 232, 214)), (0.55, (24, 150, 142)), (1, (6, 44, 50))]),
    # near-black with a silver edge
    "still": ramp([(0, (226, 230, 240)), (0.18, (128, 134, 156)), (0.35, (40, 40, 58)), (1, (6, 6, 12))]),
    # soft lavender
    "frill": ramp([(0, (244, 228, 255)), (0.35, (200, 170, 236)), (0.7, (140, 108, 190)), (1, (70, 50, 108))]),
}

for p in ("spire", "drift", "undersong"):
    for layer in ("humanoid", "humanoid_leggings"):
        draw(p, layer)
d = os.path.join(OUT, "palettes/trim")
os.makedirs(d, exist_ok=True)
for name, cols in PALETTES.items():
    im = Image.new("RGB", (8, 1))
    for i, c in enumerate(cols):
        im.putpixel((i, 0), c)
    im.save(os.path.join(d, name + ".png"))
print("trim textures and palettes written")
