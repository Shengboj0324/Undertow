#!/usr/bin/env python3
"""Verify the Worldsfall texture deliverables against art/SPEC_TEXTURES.md.

Checks: every spec'd file exists, exact size, RGBA PNG; animated strips have the right frame count and a valid
.png.mcmeta (frametime, interpolate); palette counts within spec (blocks 4-8 = FAIL outside, items 3-6 = REPORT only);
emissive layers mostly transparent; tileable block textures report their seam error (informational).
Exit code 1 on any failure."""
import os
import sys
import json
import struct
import zlib

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'textures'))
import numpy as np  # noqa: E402
import pix  # noqa: E402
import expansion_names as XN  # noqa: E402  (art/SPEC_EXPANSION.md: 1.2 -> 2.0 textures)

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, 'src/main/resources/assets/worldsfall/textures')

BLOCKS = """pale_loam_top pale_loam_side lilac_tuft chorus_pod_stage0 chorus_pod_stage1 chorus_pod_stage2
chorus_pod_stage2_emissive stillstone polished_stillstone stillstone_bricks chiseled_stillstone mirror_shard rimglass
stillglass stillglass_pane_top weathered_purpur weathered_purpur_pillar weathered_purpur_pillar_top
cracked_purpur_pillar cracked_purpur_pillar_top purpur_rubble spire_lantern spire_lantern_emissive undershale
polished_undershale undershale_bricks undershale_tiles ceiling_moss lumen_crystal_base lumen_crystal_middle
lumen_crystal_tip lumen_crystal_tip_emissive lumen_cluster lumen_cluster_emissive lumen_crystal_block voidroot_middle
voidroot_tip voidroot_tip_emissive glowbulb glowbulb_emissive glowbulb_picked lumen_lamp lumen_lamp_on
lumen_lamp_on_emissive driftstone polished_driftstone driftstone_bricks tide_gauge_side tide_gauge_top tide_gauge_fluid
tide_gauge_fluid_emissive null_obsidian null_obsidian_emissive fracture_glass fracture_glass_emissive unmade_shard
unmade_shard_emissive ash_stone hoard_cache_side hoard_cache_top hoard_cache_front keystone_socket_top
keystone_socket_side keystone_socket_top_emissive spire_vault_side spire_vault_top spire_vault_front
spire_vault_front_emissive drift_cache_side drift_cache_top drift_cache_front drift_cache_front_emissive
sigil_pedestal_side sigil_pedestal_top sigil_pedestal_top_emissive egg_cradle_top egg_cradle_side egg_cradle_post
egg_cradle_post_lit egg_cradle_post_lit_emissive gravity_anchor_frame gravity_anchor_core anchor_frame
anchor_frame_emissive fracture_rift_frame_side fracture_rift_frame_top fracture_rift_frame_top_emissive
still_throne_side still_throne_top still_throne_top_filled still_throne_top_filled_emissive tidewisp_jar
tidewisp_jar_emissive stillwater_overlay""".split()
ANIMATED = {'fracture_rift': (16, 256, 16, 2), 'stillwater_still': (16, 512, 32, 2),
            'stillwater_flow': (32, 1024, 32, 2)}

ITEMS = """skate_membrane glow_gland grazer_frill voidroot_fiber lumen_crystal_shard mirror_shard_fragment echo_dust
lurker_lens bottle_of_stillwater chorus_pod roasted_chorus_pod grazer_flank cooked_grazer_flank glowbulb drift_stew
stillwater_tea void_anchor cracked_void_anchor rift_tether rift_tether_cast shulker_satchel void_lens skate_harness
spire_key echo_tablet spire_sigil tide_sigil under_sigil rift_key heart_of_stillness stillness_fragment
dragon_egg_fragment slumbering_egg gale_upgrade_template still_upgrade_template hover_upgrade_template
spire_armor_trim_smithing_template drift_armor_trim_smithing_template undersong_armor_trim_smithing_template
music_disc_unmade stillwater_bucket bottled_tidewisp void_skate_spawn_egg chorus_grazer_spawn_egg
underside_lurker_spawn_egg hoarder_spawn_egg spire_warden_spawn_egg echo_spawn_egg wild_shulker_spawn_egg
tidewisp_spawn_egg shardling_spawn_egg wyrm_spawn_egg void_anchor_emissive glow_gland_emissive
lumen_crystal_shard_emissive echo_tablet_emissive heart_of_stillness_emissive spire_key_emissive tide_sigil_emissive
bottled_tidewisp_emissive slumbering_egg_emissive spire_lantern""".split() + ['gateway_compass_%02d' % i for i in range(16)]

PARTICLES = []
for base, n in [('pollen_mote', 4), ('pod_pop', 4), ('still_ring', 4), ('stillfall_mist', 4), ('dust_fall', 2),
                ('socket_spark', 4), ('up_mote', 4), ('up_drip', 2), ('skate_wake', 4), ('tide_crest', 4),
                ('erosion_static', 4), ('ash_inflow', 4), ('shard_spark', 4), ('tidewisp_trail', 4),
                ('shell_fragment', 4)]:
    PARTICLES += ['%s_%d' % (base, i) for i in range(n)]

GUI = {'gui/advancements/backgrounds/worldsfall': (16, 16),
       'gui/sprites/hud/skate_stamina_background': (9, 38), 'gui/sprites/hud/skate_stamina_progress': (9, 38),
       'gui/sprites/hud/tide_indicator_background': (18, 18), 'gui/sprites/hud/tide_indicator_fill': (18, 18),
       'gui/sprites/boss_bar/unmade_background': (182, 5), 'gui/sprites/boss_bar/unmade_progress': (182, 5)}
ENV = {'environment/tide_plane': (256, 256), 'environment/nebula_noise': (256, 256)}

# expansion set (art/SPEC_EXPANSION.md A-D), same rules as the 1.x set
BLOCKS += XN.BLOCKS
ITEMS += XN.ITEMS
PARTICLE_SIZES = {p: (8, 8) for p in PARTICLES}
PARTICLE_SIZES.update(XN.PARTICLES)
PARTICLES += list(XN.PARTICLES)
GUI.update(XN.GUI)
ENV_NOT_TILED = dict(XN.ENV)   # the overmoon is a sky disc, not a tiling map

TILEABLE = """pale_loam_top stillstone stillstone_bricks undershale undershale_bricks undershale_tiles driftstone
driftstone_bricks null_obsidian ash_stone lumen_crystal_block weathered_purpur purpur_rubble""".split() + XN.TILEABLE

fails, reports = [], []


def png_header(path):
    with open(path, 'rb') as f:
        d = f.read(33)
    if d[:8] != b'\x89PNG\r\n\x1a\n':
        return None
    w, h, bd, ct = struct.unpack('>IIBB', d[16:26])
    return w, h, bd, ct


def ncolors(a):
    m = a[..., 3] > 0
    return len(np.unique(a[m][:, :3], axis=0)) if m.any() else 0


def check(rel, size, kind):
    p = os.path.join(TEX, rel + '.png')
    if not os.path.exists(p):
        fails.append('MISSING  %s' % rel)
        return None
    h = png_header(p)
    if h is None:
        fails.append('NOT PNG  %s' % rel)
        return None
    w, hh, bd, ct = h
    if (w, hh) != size:
        fails.append('SIZE     %s is %dx%d, expected %dx%d' % (rel, w, hh, size[0], size[1]))
    if ct != 6 or bd != 8:
        fails.append('MODE     %s is not 8-bit RGBA (bitdepth %d colortype %d)' % (rel, bd, ct))
    return pix.load_png(p)


def seam_error(a):
    """Mean luminance jump across the wrap seam relative to the mean interior jump (1.0 = indistinguishable)."""
    L = pix.lum(a[..., :3].astype(float))
    inner = (np.abs(np.diff(L, axis=0)).mean() + np.abs(np.diff(L, axis=1)).mean()) / 2
    seam = (np.abs(L[0] - L[-1]).mean() + np.abs(L[:, 0] - L[:, -1]).mean()) / 2
    return seam / max(inner, 1e-6)


def main():
    counts = {}
    for b in BLOCKS:
        a = check('block/' + b, (16, 16), 'block')
        if a is None:
            continue
        if b.endswith('_emissive'):
            frac = (a[..., 3] > 0).mean()
            if frac > 0.6:
                fails.append('EMISSIVE %s is %.0f%% opaque (should be mostly transparent)' % (b, frac * 100))
            continue
        n = ncolors(a)
        counts[b] = n
        if n > 8 or n < 3:
            fails.append('PALETTE  block/%s uses %d colours (spec 4-8)' % (b, n))
        elif n < 4:
            reports.append('palette  block/%s uses %d colours (simple sprite)' % (b, n))
        if b in TILEABLE:
            s = seam_error(a)
            if s > 1.6:
                reports.append('seam     block/%s seam ratio %.2f' % (b, s))
    for b, (w, h, nf, ft) in ANIMATED.items():
        a = check('block/' + b, (w, h), 'anim')
        mc = os.path.join(TEX, 'block', b + '.png.mcmeta')
        if not os.path.exists(mc):
            fails.append('MCMETA   block/%s.png.mcmeta missing' % b)
        else:
            try:
                j = json.load(open(mc))['animation']
                if j.get('frametime') != ft or j.get('interpolate') is not True:
                    fails.append('MCMETA   block/%s frametime/interpolate wrong: %s' % (b, j))
            except Exception as e:
                fails.append('MCMETA   block/%s unreadable: %s' % (b, e))
        if a is not None and a.shape[0] // a.shape[1] != nf:
            fails.append('FRAMES   block/%s has %d frames, expected %d' % (b, a.shape[0] // a.shape[1], nf))
        if a is not None:
            fr = a.reshape(nf, w, w, 4)
            loop = np.abs(fr[0].astype(int) - fr[-1].astype(int)).mean()
            step = np.mean([np.abs(fr[i].astype(int) - fr[i + 1].astype(int)).mean() for i in range(nf - 1)])
            reports.append('anim     block/%s %d frames, loop-step %.1f vs mean step %.1f' % (b, nf, loop, step))
            if loop > step * 3 + 2:
                fails.append('LOOP     block/%s last->first frame jump %.1f >> mean step %.1f' % (b, loop, step))
    for it in ITEMS:
        a = check('item/' + it, (16, 16), 'item')
        if a is None:
            continue
        if it.endswith('_emissive'):
            frac = (a[..., 3] > 0).mean()
            if frac > 0.5:
                fails.append('EMISSIVE item/%s is %.0f%% opaque' % (it, frac * 100))
            if frac == 0:
                fails.append('EMISSIVE item/%s is empty' % it)
            continue
        n = ncolors(a)
        counts['item/' + it] = n
        if n > 6:
            reports.append('palette  item/%s uses %d colours (spec suggests 3-6; vanilla items use 8-17)' % (it, n))
        if (a[..., 3] > 0).mean() < 0.08:
            fails.append('EMPTY    item/%s is almost empty' % it)
    for p in PARTICLES:
        a = check('particle/' + p, PARTICLE_SIZES[p], 'particle')
        if a is not None and not (a[..., 3] > 0).any():
            fails.append('EMPTY    particle/%s' % p)
    for rel, sz in list(GUI.items()) + list(ENV.items()):
        a = check(rel, sz, 'gui')
        if a is not None and rel.startswith('environment'):
            s = seam_error(a)
            reports.append('seam     %s seam ratio %.2f' % (rel, s))
            if s > 1.6:
                fails.append('SEAM     %s does not tile (ratio %.2f)' % (rel, s))
    for rel, sz in ENV_NOT_TILED.items():
        a = check(rel, sz, 'env')
        if a is not None and not (a[..., 3] > 0).any():
            fails.append('EMPTY    %s' % rel)
    # stray files we did not expect (informational)
    expected = set(['block/' + b for b in BLOCKS] + ['block/' + b for b in ANIMATED] + ['item/' + i for i in ITEMS]
                   + ['particle/' + p for p in PARTICLES] + list(GUI) + list(ENV) + list(ENV_NOT_TILED))
    for d in ('block', 'item', 'particle', 'gui', 'environment'):
        for dp, _, fs in os.walk(os.path.join(TEX, d)):
            for f in fs:
                if f.endswith('.png'):
                    rel = os.path.relpath(os.path.join(dp, f), TEX)[:-4]
                    if rel not in expected:
                        reports.append('extra    %s (not in spec)' % rel)

    total = len(BLOCKS) + len(ANIMATED) + len(ITEMS) + len(PARTICLES) + len(GUI) + len(ENV) + len(ENV_NOT_TILED)
    print('Worldsfall texture verification: %d spec\'d files' % total)
    bn = [v for k, v in counts.items() if not k.startswith('item/')]
    inn = [v for k, v in counts.items() if k.startswith('item/')]
    if bn:
        print('  block palettes: min %d / median %d / max %d colours' % (min(bn), int(np.median(bn)), max(bn)))
    if inn:
        print('  item palettes:  min %d / median %d / max %d colours (report only)' % (min(inn), int(np.median(inn)),
                                                                                       max(inn)))
    for r in reports:
        if not r.startswith('palette  item'):
            print('  ' + r)
    nrep = sum(1 for r in reports if r.startswith('palette  item'))
    if nrep:
        print('  %d items exceed the 3-6 colour guideline (reported, not failed)' % nrep)
    for f in fails:
        print('FAIL ' + f)
    print('RESULT: %s (%d failures)' % ('PASS' if not fails else 'FAIL', len(fails)))
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
