"""Material definitions shared by the Blender bake (procedural shader) and the numpy pixel post-process."""


def hex2rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


class Mat:
    """A creature material.

    ramp   : colours dark -> light (hex).  Pixels sit on `base` +- 1 step (chunky clustered noise),
             then the face light offsets are added (top +1, bottom -1, side rim highlights).
    kind   : procedural shader family baked in Blender (drives the G/B channels):
             plain | scales | veins | chitin | crystal | cracks | lichen | membrane
    feature: noise feature size in model pixels (bigger = chunkier clusters)
    lo, hi : fraction of texels pushed one ramp step darker / lighter by the fBm tone
    shade  : per-face ramp offsets; rim: top-row highlight / bottom-row shadow on side faces
    extra  : named extra colours used by the pattern (vein, crack, lichen, ...)
    params : shader parameters (origin for radial veins, count, axis, ...)
    """

    def __init__(self, ramp, base=None, kind="plain", feature=4.0, lo=0.18, hi=0.18, shade=None, rim=True,
                 extra=None, params=None, alpha=255, pattern_scale=None, cleanup=True):
        self.ramp = [hex2rgb(c) for c in ramp]
        self.base = (len(ramp) - 1) // 2 if base is None else base
        self.kind = kind
        self.feature = float(feature)
        self.lo = lo
        self.hi = hi
        self.shade = {"top": 1, "bottom": -1}
        if shade:
            self.shade.update(shade)
        self.rim = rim
        self.extra = {k: (hex2rgb(v) if isinstance(v, str) else [hex2rgb(x) for x in v]) for k, v in (extra or {}).items()}
        self.params = dict(params or {})
        self.alpha = alpha
        self.pattern_scale = pattern_scale if pattern_scale is not None else self.feature
        self.cleanup = cleanup
