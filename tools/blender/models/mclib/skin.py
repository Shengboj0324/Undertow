"""Pixel-skin post-process (numpy).  Runs inside Blender after the Cycles bake of the procedural materials.

Turns the baked procedural channels (R = fBm tone, G = distance-to-pattern-line, B = cell / secondary noise) into a
clean low-palette vanilla-style skin: quantise to each material's colour ramp, add top-left face light, remove pixel
static, then lets the model script hand-place crisp details at known texel coordinates.  Also builds the emissive map.
"""
import numpy as np

from .geom import FACES, face_rects, texel_to_local, part_world_matrices
from .mat import hex2rgb


def _rgb(c):
    if isinstance(c, str):
        return hex2rgb(c)
    return tuple(int(v) for v in c[:3])


class View:
    """Arrays restricted to a mask, handed to user predicates."""

    def __init__(self, sk):
        self.sk = sk

    def __getattr__(self, k):
        return getattr(self.sk, "t_" + k)


class Skin:
    def __init__(self, model, mats, bake=None):
        self.model = model
        self.mats = mats
        self.mat_keys = list(mats.keys())
        W, H = model.tex_size
        self.W, self.H = W, H
        shp = (H, W)
        self.t_cube = np.full(shp, -1, np.int32)
        self.t_face = np.full(shp, -1, np.int32)
        self.t_mat = np.full(shp, -1, np.int32)
        for k in ("fx", "fy", "fw", "fh", "cx", "cy", "cz", "lx", "ly", "lz", "mx", "my", "mz"):
            setattr(self, "t_" + k, np.zeros(shp, np.float64))
        self.cubes = model.cubes()
        self.part_index = {p.name: i for i, p in enumerate(model.parts)}
        self.t_part = np.full(shp, -1, np.int32)
        wm = part_world_matrices(model)
        for c in self.cubes:
            if c.owner is not None:
                continue
            m = np.array(wm[c.part.name], dtype=np.float64)
            for fi, (face, (x, y, w, h)) in enumerate(face_rects(c.uv, c.size).items()):
                if w <= 0 or h <= 0:
                    continue
                x, y, w, h = int(x), int(y), int(w), int(h)
                jj, ii = np.mgrid[0:h, 0:w]
                fx = ii + 0.5
                fy = jj + 0.5
                loc = texel_to_local(face, c.size, fx, fy)
                cx = np.broadcast_to(np.asarray(loc[0], np.float64), (h, w))
                cy = np.broadcast_to(np.asarray(loc[1], np.float64), (h, w))
                cz = np.broadcast_to(np.asarray(loc[2], np.float64), (h, w))
                lx, ly, lz = cx + c.origin[0], cy + c.origin[1], cz + c.origin[2]
                sl = (slice(y, y + h), slice(x, x + w))
                assert (self.t_cube[sl] == -1).all(), "UV overlap at %s %s" % (c.part.name, face)
                self.t_cube[sl] = c.index
                self.t_part[sl] = self.part_index[c.part.name]
                self.t_face[sl] = FACES.index(face)
                self.t_fx[sl] = ii; self.t_fy[sl] = jj; self.t_fw[sl] = w; self.t_fh[sl] = h
                self.t_cx[sl] = cx; self.t_cy[sl] = cy; self.t_cz[sl] = cz
                self.t_lx[sl] = lx; self.t_ly[sl] = ly; self.t_lz[sl] = lz
                self.t_mx[sl] = m[0, 0] * lx + m[0, 1] * ly + m[0, 2] * lz + m[0, 3]
                self.t_my[sl] = m[1, 0] * lx + m[1, 1] * ly + m[1, 2] * lz + m[1, 3]
                self.t_mz[sl] = m[2, 0] * lx + m[2, 1] * ly + m[2, 2] * lz + m[2, 3]
                self.t_mat[sl] = self.mat_keys.index(c.mat_for(face))
        self.used = self.t_cube >= 0
        if bake is None:
            rng = np.random.default_rng(1)
            bake = {k: rng.random(shp) for k in ("R", "G", "Gmin", "B")}
        self.R, self.G, self.Gmin, self.B = bake["R"], bake["G"], bake["Gmin"], bake["B"]
        self.level = np.zeros(shp, np.int32)
        self.override = np.zeros((H, W, 4), np.int32)
        self.has_override = np.zeros(shp, bool)
        self.alpha = np.where(self.used, 255, 0).astype(np.int32)
        self.emit = np.zeros((H, W, 4), np.uint8)
        self.v = View(self)
        self._quantise()

    # ------------------------------------------------------------------ procedural -> levels
    def _quantise(self):
        for mi, key in enumerate(self.mat_keys):
            mat = self.mats[key]
            m = self.t_mat == mi
            if not m.any():
                continue
            t = self.R[m]
            lvl = np.full(t.shape, mat.base, np.int32)
            if mat.lo > 0:
                lvl -= (t < np.quantile(t, mat.lo)).astype(np.int32)
            if mat.hi > 0:
                lvl += (t > np.quantile(t, 1 - mat.hi)).astype(np.int32)
            g = self.Gmin[m]
            b = self.B[m]
            p = mat.params
            kind = mat.kind
            if kind in ("scales", "chitin"):
                line = g <= np.quantile(g, p.get("line_frac", 0.18))
                lvl -= line.astype(np.int32)
            elif kind == "crystal":
                facet = np.digitize(b, [1 / 3, 2 / 3]) - 1  # -1, 0, +1 per voronoi facet
                lvl += facet
                edge = g <= np.quantile(g, p.get("line_frac", 0.12))
                lvl += edge.astype(np.int32)
            self.level[m] = lvl
            # face light: top lighter, bottom darker, side-face rims (light from the top-left)
            face = self.t_face[m]
            add = np.zeros(t.shape, np.int32)
            for fname, off in mat.shade.items():
                add[face == FACES.index(fname)] += off
            if mat.rim:
                fy = self.t_fy[m]; fh = self.t_fh[m]; fx = self.t_fx[m]; fw = self.t_fw[m]
                side = (face >= 2)
                add[side & (fy == 0) & (fh >= 4)] += 1
                add[side & (fy == fh - 1) & (fh >= 6)] -= 1
                # top faces: a darker back/right edge reads as the shadowed side of the form
                top = face == 0
                add[top & (fx == fw - 1) & (fw >= 6) & (self.t_fh[m] >= 6)] -= 1
            self.level[m] += add
            # pattern overlays that use extra colours
            if kind in ("veins", "membrane", "cracks", "lichen"):
                over = np.zeros((t.shape[0], 4), np.int32)
                hit = np.zeros(t.shape, bool)
                if kind in ("veins", "membrane"):
                    hit = g <= np.quantile(g, p.get("line_frac", 0.14))
                    col = mat.extra.get("vein", mat.ramp[-1])
                elif kind == "cracks":
                    gate = b > np.quantile(b, 1 - p.get("crack_cover", 0.5))
                    hit = (g <= np.quantile(g, p.get("line_frac", 0.10))) & gate
                    col = mat.extra.get("crack", mat.ramp[0])
                else:  # lichen
                    hit = b > np.quantile(b, 1 - p.get("cover", 0.2))
                    cols = mat.extra.get("lichen", [mat.ramp[-1]])
                    col = cols[0] if isinstance(cols[0], int) else cols[0]
                if isinstance(col, list):
                    col = col[0]
                over[hit, :3] = col
                over[hit, 3] = mat.alpha
                ys, xs = np.nonzero(m)
                self.override[ys[hit], xs[hit]] = over[hit]
                self.has_override[ys[hit], xs[hit]] = True
                if kind == "lichen" and isinstance(mat.extra.get("lichen"), list) and len(mat.extra["lichen"]) > 1:
                    hi2 = hit & (t > np.quantile(t, 0.5))
                    self.override[ys[hi2], xs[hi2], :3] = mat.extra["lichen"][1]
            self.alpha[m] = mat.alpha
        if True:
            self._cleanup()

    def _cleanup(self):
        """Remove isolated single texels (pixel static): a texel whose 4 in-face neighbours agree on another level."""
        L = self.level
        H, W = L.shape
        key = self.t_cube * 8 + self.t_face
        out = L.copy()
        nb = []
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            sl = np.full((H, W), -999, np.int64)
            same = np.zeros((H, W), bool)
            ys = slice(max(dy, 0), H + min(dy, 0)); yd = slice(max(-dy, 0), H + min(-dy, 0))
            xs = slice(max(dx, 0), W + min(dx, 0)); xd = slice(max(-dx, 0), W + min(-dx, 0))
            sl[yd, xd] = L[ys, xs]
            same[yd, xd] = key[ys, xs] == key[yd, xd]
            nb.append((sl, same))
        cnt = sum(s.astype(np.int32) for _, s in nb)
        for cand_l, cand_s in nb:
            agree = np.zeros((H, W), np.int32)
            differ_self = np.ones((H, W), bool)
            for l2, s2 in nb:
                agree += (s2 & (l2 == cand_l)).astype(np.int32)
                differ_self &= ~(s2 & (l2 == L))
            fix = cand_s & (agree == cnt) & (cnt >= 3) & differ_self & self.used
            clean_ok = np.zeros((H, W), bool)
            for mi, key_ in enumerate(self.mat_keys):
                if self.mats[key_].cleanup:
                    clean_ok |= self.t_mat == mi
            fix &= clean_ok
            out[fix] = cand_l[fix]
        self.level = out

    # ------------------------------------------------------------------ selection helpers
    def mask(self, part=None, cube=None, face=None, cond=None, mat=None):
        m = self.used.copy()
        if part is not None:
            names = [part] if isinstance(part, str) else list(part)
            m &= np.isin(self.t_part, [self.part_index[n] for n in names])
        if cube is not None:
            cs = [cube] if not isinstance(cube, (list, tuple)) else cube
            m &= np.isin(self.t_cube, [c.index if hasattr(c, "index") else c for c in cs])
        if face is not None:
            fs = [face] if isinstance(face, str) else list(face)
            m &= np.isin(self.t_face, [FACES.index(f) for f in fs])
        if mat is not None:
            ms = [mat] if isinstance(mat, str) else list(mat)
            m &= np.isin(self.t_mat, [self.mat_keys.index(k) for k in ms])
        if cond is not None:
            m &= np.asarray(cond(self.v), bool)
        return m

    def shift(self, mask, k):
        self.level[mask] += k

    def set(self, mask, color, alpha=None, emit=None, emit_alpha=255):
        r, g, b = _rgb(color)
        self.override[mask] = (r, g, b, 255 if alpha is None else alpha)
        self.has_override[mask] = True
        if alpha is not None:
            self.alpha[mask] = alpha
        if emit is not None:
            self.glow(mask, emit if emit is not True else color, emit_alpha)

    def glow(self, mask, color, alpha=255):
        r, g, b = _rgb(color)
        self.emit[mask] = (r, g, b, alpha)

    def cut(self, mask):
        self.alpha[mask] = 0
        self.emit[mask] = 0

    def cut_shape(self, cube, keep):
        """Alpha-cut a plate/fin cube to an outline.  keep(x, y, z) -> bool in cube-local pixels.
        Side faces keep a texel only where the adjacent outline texel is kept, so cut edges don't float."""
        c = cube
        m = self.mask(cube=c)
        w, h, d = c.size

        def clampc(a, s):
            return np.clip(a, 0.5, s - 0.5) if s >= 1 else np.zeros_like(a)

        x = clampc(self.t_cx[m], w); y = clampc(self.t_cy[m], h); z = clampc(self.t_cz[m], d)
        k = np.asarray(keep(x, y, z), bool)
        ys, xs = np.nonzero(m)
        self.alpha[ys[~k], xs[~k]] = 0
        self.emit[ys[~k], xs[~k]] = 0
        return k

    def outline_edge(self, cube, faces=("top", "bottom"), borders=False):
        """Mask of kept texels on the given faces of `cube` that border a cut texel (and, if borders, the region edge)."""
        m = self.mask(cube=cube, face=faces)
        kept = m & (self.alpha > 0)
        H, W = kept.shape
        edge = np.zeros_like(kept)
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            sh = np.zeros_like(kept)
            ys = slice(max(dy, 0), H + min(dy, 0)); yd = slice(max(-dy, 0), H + min(-dy, 0))
            xs = slice(max(dx, 0), W + min(dx, 0)); xd = slice(max(-dx, 0), W + min(-dx, 0))
            same = np.zeros_like(kept)
            same[yd, xd] = (self.t_face[ys, xs] == self.t_face[yd, xd]) & (self.t_cube[ys, xs] == self.t_cube[yd, xd])
            sh[yd, xd] = kept[ys, xs]
            sh = (sh & same) | (~same & (not borders))
            edge |= kept & ~sh
        return edge

    # ------------------------------------------------------------------ output
    def finish(self):
        H, W = self.H, self.W
        rgba = np.zeros((H, W, 4), np.int32)
        for mi, key in enumerate(self.mat_keys):
            mat = self.mats[key]
            m = self.t_mat == mi
            if not m.any():
                continue
            ramp = np.array(mat.ramp, np.int32)
            lv = np.clip(self.level[m], 0, len(ramp) - 1)
            rgba[m, :3] = ramp[lv]
        rgba[self.has_override] = self.override[self.has_override]
        rgba[:, :, 3] = self.alpha
        rgba[~self.used] = 0
        rgba[rgba[:, :, 3] == 0] = 0
        em = self.emit.copy()
        em[rgba[:, :, 3] == 0] = 0
        return np.clip(rgba, 0, 255).astype(np.uint8), em

    def palette_count(self, rgba):
        a = rgba[rgba[:, :, 3] > 0][:, :3]
        return len({tuple(x) for x in a})


def shade_hex(h, f):
    r, g, b = hex2rgb(h)
    return "#%02x%02x%02x" % tuple(int(max(0, min(255, round(c * f)))) for c in (r, g, b))
