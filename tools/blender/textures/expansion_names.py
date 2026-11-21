"""Every texture of the expansion (art/SPEC_EXPANSION.md sections A-D), as rel names without '.png'.
Pure python (no bpy, no relative imports): shared by main.py, verify_textures.py and contact_sheet.py."""

PACTS = ['tide', 'shards', 'echoes', 'hoard', 'memory', 'stillness', 'lone', 'depths']

BLOCKS = """wardens_lantern wardens_lantern_emissive causeway_socket_top causeway_socket_top_emissive causeway_socket_side
causeway_plaque drift_buoy drift_buoy_emissive drift_helm ships_bell courier_cache_front courier_cache_front_emissive
courier_cache_side courier_cache_top tide_bell roost_post_side roost_post_top gravity_plate gravity_plate_emissive
anchor_frame_lamp anchor_frame_lamp_emissive wyrm_roost_top wyrm_roost_side tidewrack lantern_moth_jar
lantern_moth_jar_emissive starfall_shard starfall_shard_emissive skystone polished_skystone skystone_bricks
skystone_tiles chiseled_skystone halo_glass halo_glass_emissive orrery_ring orrery_lens orrery_lens_emissive
orrery_socket_top orrery_socket_top_emissive orrery_socket_side null_coral null_coral_fan null_coral_block
null_coral_bricks polished_null_coral archive_shelf_front archive_shelf_front_emissive archive_shelf_side
archive_shelf_top bell_winch_top bell_winch_side mural_panel_dark mural_panel_lit mural_panel_lit_emissive
archive_tiles""".split()

ITEMS = ['pact_glyph_' + p for p in PACTS] + ['pact_crystal_' + p for p in PACTS] + """stillness_crystal
dragon_scale wyrm_barding tuning_fork warden_banner_pattern wardenborn_seal music_disc_warden music_disc_ladders
music_disc_undertow causeway_keystone city_chart void_chart void_chart_filled star_chart courier_token mirrorfish
cooked_mirrorfish driftsilt harrier_plume current_vane leviathan_barnacle eclipse_dust archive_codex
archive_codex_emissive abyssal_anchor undertow_pearl void_bell null_pigment""".split()

PARTICLES = {}  # name -> (w, h)
for base, n, size in [('wardensong_glyph', 4, (8, 8)), ('sky_current', 3, (16, 8)), ('downdraft_dust', 3, (8, 8)),
                      ('starfall_spark', 2, (8, 8)), ('maw_swirl', 4, (8, 8))]:
    for i in range(n):
        PARTICLES['%s_%d' % (base, i)] = size
PARTICLES['eclipse_mote'] = (8, 8)

GUI = {'gui/lattice': (256, 256), 'gui/void_chart': (256, 256)}
ENV = {'environment/overmoon': (64, 64)}

TILEABLE = """skystone skystone_bricks skystone_tiles null_coral_block null_coral_bricks archive_tiles tidewrack""".split()

ALL = (['block/' + b for b in BLOCKS] + ['item/' + i for i in ITEMS] + ['particle/' + p for p in PARTICLES]
       + list(GUI) + list(ENV))
