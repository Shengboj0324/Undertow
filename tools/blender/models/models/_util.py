"""Shared helpers for the per-model build scripts."""
import numpy as np

_share_counter = [0]


def mirror_part(model, src, dst, parent=None):
    """Create `dst` as the X-mirror of part `src` (pivot x negated, Y/Z rotations negated, cubes mirrored and
    sharing the source UV islands with vanilla `mirror` semantics)."""
    s = model[src]
    par = parent if parent is not None else s.parent
    p = model.part(dst, par, (-s.pivot[0], s.pivot[1], s.pivot[2]), (s.rotation[0], -s.rotation[1], -s.rotation[2]))
    for c in s.cubes:
        if c.share is None:
            _share_counter[0] += 1
            c.share = "m%d_%s" % (_share_counter[0], src)
        p.cube((-(c.origin[0] + c.size[0]), c.origin[1], c.origin[2]), c.size, inflate=c.inflate, mirror=not c.mirror,
               mat=c.mat, share=c.share, bake=False, tag=c.tag)
    return p


def mirror_tree(model, pairs):
    """pairs: [(src, dst, parent_dst_or_None)] in parent-first order."""
    for src, dst, par in pairs:
        mirror_part(model, src, dst, par)


def disk(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def near_points(px_, py_, pts, r=0.75):
    m = np.zeros(np.shape(px_), bool)
    for (x, y) in pts:
        m |= (np.abs(px_ - x) < r) & (np.abs(py_ - y) < r)
    return m
