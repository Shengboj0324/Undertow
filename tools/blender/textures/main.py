"""Blender entry point:  blender -b --factory-startup --python main.py -- [group|rel-prefix ...]
groups: blocks items particles gui environment (the 1.x set) and the expansion set (art/SPEC_EXPANSION.md):
xblocks xitems xparticles xgui xenvironment, or `expansion` for all five. Default: everything."""
import sys
import os
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from textures import tex, bk  # noqa: E402

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
LEGACY = ['blocks', 'items', 'particles', 'gui', 'environment']
EXPANSION = ['xblocks', 'xitems', 'xparticles', 'xgui', 'xenvironment']
argv = [g for a in argv for g in (EXPANSION if a == 'expansion' else [a])]
groups = [a for a in argv if a in LEGACY + EXPANSION]
prefixes = [a for a in argv if a not in groups]
tex.ONLY = set(prefixes) if prefixes else None
if not groups:
    groups = LEGACY + EXPANSION
    if prefixes:  # infer groups from prefixes: expansion names go to the x-groups, everything else to the 1.x set
        from textures import expansion_names
        m = {'block/': 'blocks', 'item/': 'items', 'particle/': 'particles', 'gui/': 'gui', 'environment/': 'environment'}
        inferred = set()
        for p in prefixes:
            for k, g in m.items():
                if p.startswith(k):
                    if any(p.startswith(n) for n in expansion_names.ALL):
                        inferred.add('x' + g)                      # names one expansion texture
                    elif any(n.startswith(p) for n in expansion_names.ALL):
                        inferred.update(('x' + g, g))              # ambiguous prefix: run both sets
                    else:
                        inferred.add(g)
        groups = [g for g in LEGACY + EXPANSION if g in inferred] or groups

t0 = time.time()
bk.reset_scene()
for g in groups:
    t = time.time()
    if g == 'xblocks':
        from textures import expansion_blocks as mod
    elif g == 'xitems':
        from textures import expansion_items as mod
    elif g in ('xparticles', 'xgui', 'xenvironment'):
        from textures import expansion_misc as mod
    elif g == 'blocks':
        from textures import blocks as mod
    elif g == 'items':
        from textures import items as mod
    elif g == 'particles':
        from textures import particles as mod
    else:
        from textures import misc as mod
    if g in ('gui', 'environment', 'xparticles', 'xgui', 'xenvironment'):
        mod.build(g)
    else:
        mod.build()
    print('[wf] %-12s done in %.1fs' % (g, time.time() - t))
print('[wf] wrote %d textures in %.1fs' % (len(tex.WRITTEN), time.time() - t0))
