"""Blender entry point:  blender -b --factory-startup --python main.py -- [group|rel-prefix ...]
groups: blocks items particles gui environment (default: all)."""
import sys
import os
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from textures import tex, bk  # noqa: E402

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
groups = [a for a in argv if a in ('blocks', 'items', 'particles', 'gui', 'environment')]
prefixes = [a for a in argv if a not in groups]
tex.ONLY = set(prefixes) if prefixes else None
if not groups:
    groups = ['blocks', 'items', 'particles', 'gui', 'environment']
    if prefixes:  # infer groups from prefixes
        m = {'block/': 'blocks', 'item/': 'items', 'particle/': 'particles', 'gui/': 'gui', 'environment/': 'environment'}
        groups = sorted({g for p in prefixes for k, g in m.items() if p.startswith(k)}) or groups

t0 = time.time()
bk.reset_scene()
for g in groups:
    t = time.time()
    if g == 'blocks':
        from textures import blocks as mod
    elif g == 'items':
        from textures import items as mod
    elif g == 'particles':
        from textures import particles as mod
    else:
        from textures import misc as mod
    if g in ('gui', 'environment'):
        mod.build(g)
    else:
        mod.build()
    print('[wf] %-12s done in %.1fs' % (g, time.time() - t))
print('[wf] wrote %d textures in %.1fs' % (len(tex.WRITTEN), time.time() - t0))
