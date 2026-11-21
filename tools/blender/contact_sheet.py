#!/usr/bin/env python3
"""Contact sheets for texture review (system python + PIL).

For every PNG in a folder: 8x nearest-neighbour upscale on a dark and a light swatch, and for block textures a
3x3 tiling (seam check). Animated strips show their frames in a row. Optional vanilla references next to them.

usage: contact_sheet.py <folder: block|item|particle|gui|environment> [out.png] [--filter substr] [--anim]
       contact_sheet.py expansion [blocks|items|particles|gui ...]
           -> art/previews/expansion_<group>.png for the art/SPEC_EXPANSION.md textures only
"""
import os
import sys
import glob
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, 'src/main/resources/assets/worldsfall/textures')
VAN = os.environ.get('WF_VANILLA', '/private/tmp/claude-501/-Users-jiangshengbo-Undertow/'
                     '887845a4-3236-4149-9e5f-f22849f5c113/scratchpad/src/assets/minecraft/textures')
DARK = (24, 22, 30, 255)
LIGHT = (205, 205, 210, 255)


def font():
    for p in ['/System/Library/Fonts/Menlo.ttc', '/System/Library/Fonts/Monaco.ttf']:
        if os.path.exists(p):
            return ImageFont.truetype(p, 11)
    return ImageFont.load_default()


def frames_of(path, im):
    mc = path + '.mcmeta'
    if os.path.exists(mc) and im.height > im.width:
        n = im.height // im.width
        return [im.crop((0, i * im.width, im.width, (i + 1) * im.width)) for i in range(n)]
    return [im]


def cell(im, S, tile, emissive_base=None):
    w, h = im.size
    big = im.resize((w * S, h * S), Image.NEAREST)
    parts = []
    for bg in (DARK, LIGHT):
        c = Image.new('RGBA', big.size, bg)
        c.alpha_composite(big)
        parts.append(c)
    if tile:
        t = Image.new('RGBA', (w * 3, h * 3), DARK)
        for i in range(3):
            for j in range(3):
                t.alpha_composite(im, (i * w, j * h))
        ts = max(1, S // 2)
        parts.append(t.resize((w * 3 * ts, h * 3 * ts), Image.NEAREST))
    W = sum(p.width for p in parts) + 6 * (len(parts) - 1)
    H = max(p.height for p in parts)
    out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    x = 0
    for p in parts:
        out.alpha_composite(p, (x, 0))
        x += p.width + 6
    return out


def sheet(files, out, S=8, tile=False, cols=4, refs=()):
    F = font()
    cells = []
    for f in list(refs) + files:
        im = Image.open(f).convert('RGBA')
        fr = frames_of(f, im)
        name = os.path.relpath(f, TEX) if f.startswith(TEX) else 'VANILLA ' + os.path.basename(f)
        if len(fr) > 1:
            name += ' [%d fr]' % len(fr)
            im = fr[0]
        s = S if im.width <= 32 else max(1, 128 // im.width * 2) if im.width < 128 else 1
        c = cell(im, s, tile and im.width <= 32)
        lab = Image.new('RGBA', (max(c.width, 10), c.height + 16), (40, 38, 48, 255))
        ImageDraw.Draw(lab).text((2, 1), name, fill=(230, 230, 230, 255), font=F)
        lab.alpha_composite(c, (0, 16))
        cells.append(lab)
    if not cells:
        return
    cw = max(c.width for c in cells) + 10
    ch = max(c.height for c in cells) + 10
    rows = (len(cells) + cols - 1) // cols
    img = Image.new('RGBA', (cw * cols, ch * rows), (52, 50, 60, 255))
    for i, c in enumerate(cells):
        img.alpha_composite(c, ((i % cols) * cw + 5, (i // cols) * ch + 5))
    img.save(out)
    print('wrote', out, img.size)


def anim_sheet(files, out, S=6):
    F = font()
    rows = []
    for f in files:
        im = Image.open(f).convert('RGBA')
        fr = frames_of(f, im)
        w = fr[0].width
        s = S if w <= 16 else S // 2
        strip = Image.new('RGBA', ((w * s + 2) * min(len(fr), 16), w * s * 2 + 20 if len(fr) > 16 else w * s + 18),
                          (40, 38, 48, 255))
        ImageDraw.Draw(strip).text((2, 1), os.path.relpath(f, TEX) + ' %d frames' % len(fr), fill=(230, 230, 230), font=F)
        for i, x in enumerate(fr[:32]):
            bg = Image.new('RGBA', x.size, DARK)
            bg.alpha_composite(x)
            strip.alpha_composite(bg.resize((w * s, w * s), Image.NEAREST),
                                  ((i % 16) * (w * s + 2), 16 + (i // 16) * (w * s + 2)))
        rows.append(strip)
    W = max(r.width for r in rows)
    H = sum(r.height + 6 for r in rows)
    img = Image.new('RGBA', (W, H), (52, 50, 60, 255))
    y = 0
    for r in rows:
        img.alpha_composite(r, (0, y))
        y += r.height + 6
    img.save(out)
    print('wrote', out, img.size)


def compare(out, S=4):
    """'Sits next to vanilla' check: a wall of 2x2-block patches mixing vanilla end stone / purpur / deepslate /
    obsidian with our stones and building blocks, all at the same scale, on top a grass-row-like strip."""
    rows = [
        ['V:end_stone', 'pale_loam_side', 'V:end_stone_bricks', 'ash_stone', 'V:end_stone', 'still_throne_side', 'V:end_stone'],
        ['V:purpur_block', 'weathered_purpur', 'V:purpur_pillar', 'weathered_purpur_pillar', 'cracked_purpur_pillar', 'purpur_rubble', 'V:purpur_block'],
        ['V:deepslate', 'undershale', 'polished_undershale', 'undershale_bricks', 'undershale_tiles', 'driftstone', 'V:deepslate_bricks'],
        ['V:obsidian', 'stillstone', 'polished_stillstone', 'stillstone_bricks', 'chiseled_stillstone', 'null_obsidian', 'V:crying_obsidian'],
        ['V:amethyst_block', 'lumen_crystal_block', 'lumen_lamp', 'lumen_lamp_on', 'driftstone_bricks', 'polished_driftstone', 'V:tinted_glass'],
    ]
    tiles = []
    for r in rows:
        rr = []
        for n in r:
            p = os.path.join(VAN, 'block', n[2:] + '.png') if n.startswith('V:') else os.path.join(TEX, 'block', n + '.png')
            if n == 'V:purpur_pillar':
                p = os.path.join(VAN, 'block', 'purpur_pillar_side.png')
            im = Image.open(p).convert('RGBA').crop((0, 0, 16, 16))
            rr.append(im)
        tiles.append(rr)
    B = 16 * S
    img = Image.new('RGBA', (len(rows[0]) * 2 * B, len(rows) * 2 * B), (0, 0, 0, 255))
    for j, r in enumerate(tiles):
        for i, im in enumerate(r):
            big = im.resize((B, B), Image.NEAREST)
            for a in range(2):
                for b in range(2):
                    img.alpha_composite(big, ((2 * i + a) * B, (2 * j + b) * B))
    img.save(out)
    print('wrote', out, img.size)


def expansion(groups):
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'textures'))
    import expansion_names as xn
    sets = {'blocks': ['block/' + b for b in xn.BLOCKS], 'items': ['item/' + i for i in xn.ITEMS],
            'particles': ['particle/' + p for p in xn.PARTICLES], 'gui': list(xn.GUI) + list(xn.ENV)}
    for g in groups or list(sets):
        files = [os.path.join(TEX, r + '.png') for r in sets[g]]
        files = [f for f in files if os.path.exists(f)]
        sheet(files, os.path.join(ROOT, 'art/previews/expansion_%s.png' % g), S=8 if g != 'gui' else 2,
              tile=(g == 'blocks'), cols={'blocks': 5, 'items': 8, 'particles': 8, 'gui': 3}[g])


if __name__ == '__main__':
    folder = sys.argv[1]
    if folder == 'expansion':
        expansion(sys.argv[2:])
        sys.exit(0)
    if folder == 'compare':
        compare(os.path.join(ROOT, 'art/previews/textures_vanilla_compare.png'))
        sys.exit(0)
    out = sys.argv[2] if len(sys.argv) > 2 and not sys.argv[2].startswith('--') else \
        os.path.join(ROOT, 'art/previews/textures_%s.png' % folder.replace('/', '_'))
    flt = None
    if '--filter' in sys.argv:
        flt = sys.argv[sys.argv.index('--filter') + 1].split(',')
    files = sorted(glob.glob(os.path.join(TEX, folder, '**/*.png'), recursive=True))
    if flt:
        files = [f for f in files if any(x in os.path.basename(f) for x in flt)]
    os.makedirs(os.path.dirname(out), exist_ok=True)
    refs = []
    if '--refs' in sys.argv:
        refs = [os.path.join(VAN, r) for r in sys.argv[sys.argv.index('--refs') + 1].split(',')]
    if '--anim' in sys.argv:
        anim_sheet([f for f in files if os.path.exists(f + '.mcmeta')], out)
    else:
        files = [f for f in files if not os.path.exists(f + '.mcmeta')] if folder == 'block' else files
        sheet(files, out, tile=(folder == 'block' or folder.startswith('gui/adv')), cols=int(os.environ.get('COLS', 4)),
              refs=refs)
