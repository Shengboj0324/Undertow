"""Smithing template icons in the vanilla convention: a tilted, worn stone tablet with thickness, lit from the top
left, carrying one glowing teal glyph. Three armor trim templates (Spire, Drift, Undersong, §9.11) and the three elytra
upgrade templates (Gale, Still, Hover, §9.7), each in its own stone.
Run: python3 tools/gen/gen_templates.py [preview.png]"""
import math
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src/main/resources/assets/worldsfall/textures/item")
THETA = math.radians(31)           # tablet tilt
HALF_U, HALF_V = 4.7, 6.3          # half extents of the tablet face
CENTER = (8.0, 7.6)
DEPTH = (1.2, 1.1)                 # thickness offset toward the bottom right (screen space)


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


# ramps: highlight, face, face shade, side, side dark, outline
STONES = {
    "spire": ["#d6aed4", "#bf92bd", "#a77aa6", "#8c5c8c", "#744775", "#55305a"],       # weathered purpur
    "drift": ["#b4ccc9", "#93b0ae", "#789695", "#5d7b7d", "#466267", "#2d434b"],       # driftstone
    "undersong": ["#8c889b", "#726f84", "#5d5a6f", "#49475b", "#38364a", "#232133"],   # undershale
    "gale_upgrade": ["#a3abd6", "#848ec1", "#6c76aa", "#565f91", "#434a78", "#2c3156"],  # skate membrane navy
    "still_upgrade": ["#d4d7e2", "#5e6074", "#4b4c60", "#3a3b4e", "#2b2c3e", "#18182a"],  # mirror black, silver lip
    "hover_upgrade": ["#c4bddf", "#a49dc9", "#8a82b1", "#6f6898", "#58517e", "#3b355e"],  # pale lilac stone
}
GLYPH_COLORS = {"hi": hexrgb("#83f8f8"), "core": hexrgb("#4bc9c9"), "mid": hexrgb("#209ab8"), "dark": hexrgb("#077995")}

# glyphs drawn in screen pixels and centred on the tablet ('#' = glowing groove): crisp at 16x16
GLYPHS = {
    "spire": ["..#..", "..#..", ".###.", ".#.#.", ".#.#.", "##.##", "#####"],                    # a tower rising to a point
    "drift": [".##....", "#..#..#", "....##.", ".......", ".##....", "#..#..#", "....##."],        # flowing waves
    "undersong": ["#######", "...#...", "..#.#..", ".#..#..", ".#.#.#.", "#..#..#", "...#..."],  # roots hanging down
    "gale_upgrade": [".####.", "#....#", "#..#.#", "#.#..#", "#..##.", ".#...."],                # a gust curling back
    "still_upgrade": [".###.", "#...#", "#.#.#", "#...#", ".###."],                             # a calm ring, a still point
    "hover_upgrade": ["##.##", "##.##", ".....", "..#..", ".#.#.", "..#.."],                     # two nodes, light beneath
}


def to_local(x, y):
    px, py = x + 0.5 - CENTER[0], y + 0.5 - CENTER[1]
    c, s = math.cos(-THETA), math.sin(-THETA)
    return px * c - py * s, px * s + py * c


def in_face(x, y):
    u, v = to_local(x, y)
    if abs(u) > HALF_U or abs(v) > HALF_V:
        return False
    # chipped corners (top right and bottom left worn away a little, like an old tablet)
    if u > HALF_U - 1.1 and v < -HALF_V + 1.1:
        return False
    if u < -HALF_U + 0.9 and v > HALF_V - 0.9:
        return False
    return True


def in_body(x, y):
    """Face plus the thickness extruded toward the bottom right."""
    return in_face(x, y) or in_face(x - DEPTH[0], y - DEPTH[1]) or in_face(x - DEPTH[0] / 2, y - DEPTH[1] / 2)


def noise(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h & 0xFFFF) / 65535.0


def glyph_at(name, x, y):
    """Glyph pixel at (x, y), with the glyph centred on the tablet face."""
    g = GLYPHS[name]
    h, w = len(g), len(g[0])
    gx, gy = x - int(round(CENTER[0] - w / 2)), y - int(round(CENTER[1] - h / 2))
    return 0 <= gx < w and 0 <= gy < h and g[gy][gx] == "#"


def render(name):
    ramp = [hexrgb(h) for h in STONES[name]]
    seed = sum(map(ord, name))
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    glyph = {(x, y) for x in range(16) for y in range(16) if in_face(x, y) and glyph_at(name, x, y)}
    for y in range(16):
        for x in range(16):
            if not in_body(x, y):
                continue
            n = noise(x, y, seed)
            if in_face(x, y):
                u, v = to_local(x, y)
                light = (-u / HALF_U - v / HALF_V) * 0.5  # +1 top-left .. -1 bottom-right
                edge_tl = not in_face(x - 1, y) or not in_face(x, y - 1)
                if (x, y) in glyph:
                    # groove: bright where light falls, darker toward the bottom right of each stroke
                    lit = (x - 1, y) not in glyph or (x, y - 1) not in glyph
                    shade = (x + 1, y) not in glyph and (x, y + 1) not in glyph
                    col = GLYPH_COLORS["hi"] if lit and n > 0.45 else GLYPH_COLORS["core"]
                    if shade and not lit:
                        col = GLYPH_COLORS["mid"]
                elif (x - 1, y - 1) in glyph or (x - 1, y) in glyph and (x, y - 1) in glyph:
                    col = GLYPH_COLORS["dark"] if n < 0.35 else ramp[2]  # the groove's shadow lip
                elif edge_tl:
                    col = ramp[2] if n < 0.6 else ramp[3]
                else:
                    t = light + (n - 0.5) * 0.7
                    col = ramp[0] if t > 0.38 else ramp[1] if t > -0.25 else ramp[2]
            else:
                outer = not in_body(x + 1, y) or not in_body(x, y + 1)
                col = ramp[5] if outer else (ramp[3] if n > 0.4 else ramp[4])
            img.putpixel((x, y), col)
    return img


def main():
    imgs = {}
    for name in GLYPHS:
        img = render(name)
        fname = f"{name}_armor_trim_smithing_template.png" if name in ("spire", "drift", "undersong") else f"{name}_template.png"
        img.save(os.path.join(OUT, fname))
        imgs[name] = img
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (len(imgs) * 136, 136), (58, 58, 68, 255))
        for i, im in enumerate(imgs.values()):
            big = im.resize((128, 128), Image.NEAREST)
            sheet.paste(big, (i * 136 + 4, 4), big)
        sheet.save(sys.argv[1])
    print("templates written:", ", ".join(imgs))


if __name__ == "__main__":
    main()
