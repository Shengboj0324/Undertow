"""Void Lens scope overlay (§9.6): black outside the lens, a teal crystal rim with a soft inner glow, faint
refraction rings and range ticks.  Writes assets/worldsfall/textures/misc/void_lens_scope.png (512x512)."""
import math
import os

from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src/main/resources/assets/worldsfall/textures/misc/void_lens_scope.png")
N = 512
c = (N - 1) / 2
R = N * 0.5 - 6
img = Image.new("RGBA", (N, N))
px = img.load()
for y in range(N):
    for x in range(N):
        dx, dy = x - c, y - c
        d = math.hypot(dx, dy) / R
        a = math.atan2(dy, dx)
        if d >= 1.0:
            px[x, y] = (0, 0, 0, 255)
            continue
        # inner glow toward the rim, slightly brighter at the top-left like light catching crystal
        rim = max(0.0, (d - 0.80) / 0.20) ** 2.2
        catch = 0.55 + 0.45 * max(0.0, math.cos(a + 2.35))
        alpha = rim * 230 * (0.75 + 0.25 * catch)
        r, g, b = 20 + 50 * rim * catch, 150 + 90 * rim, 140 + 80 * rim
        # faint refraction rings
        ring = max(0.0, 1 - abs(d - 0.62) / 0.006) * 34 + max(0.0, 1 - abs(d - 0.34) / 0.005) * 20
        alpha += ring
        # range ticks on the 0.62 ring every 15 degrees, longer every 45
        deg = (math.degrees(a) + 360) % 360
        k = deg % 15
        if min(k, 15 - k) < 0.45 and 0.585 < d < (0.66 if round(deg / 15) % 3 == 0 else 0.635):
            alpha += 90
        # a fine centre reticle: four short strokes with a gap
        if (abs(dx) < 0.8 and 0.04 < abs(dy) / R < 0.12) or (abs(dy) < 0.8 and 0.04 < abs(dx) / R < 0.12):
            alpha += 140
        px[x, y] = (int(min(255, r)), int(min(255, g)), int(min(255, b)), int(min(255, alpha)))
# soften the hard lens edge a touch without blurring the black surround
soft = img.filter(ImageFilter.GaussianBlur(1.2))
mask = Image.new("L", (N, N))
mp = mask.load()
for y in range(N):
    for x in range(N):
        d = math.hypot(x - c, y - c) / R
        mp[x, y] = 255 if 0.985 < d < 1.02 else 0
img.paste(soft, (0, 0), mask)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("wrote", OUT)
