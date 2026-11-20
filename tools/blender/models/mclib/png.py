"""Minimal, dependency-free RGBA8 PNG writer/reader (numpy), so pixel values are written exactly."""
import struct
import zlib

import numpy as np


def write_png(path, rgba):
    a = np.ascontiguousarray(np.asarray(rgba, dtype=np.uint8))
    if a.ndim == 2:
        a = np.stack([a, a, a, np.full_like(a, 255)], -1)
    if a.shape[2] == 3:
        a = np.concatenate([a, np.full(a.shape[:2] + (1,), 255, np.uint8)], -1)
    h, w, _ = a.shape
    raw = b"".join(b"\x00" + a[y].tobytes() for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(data)


def read_png(path):
    """Read 8-bit PNG (gray, gray+alpha, RGB, RGBA or palette, non-interlaced) -> uint8 (h, w, 4)."""
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    pos = 8
    idat = b""
    plte = None
    trns = None
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        t = data[pos + 4:pos + 8]
        d = data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if t == b"IHDR":
            w, h, bd, ct, _, _, il = struct.unpack(">IIBBBBB", d)
        elif t == b"PLTE":
            plte = np.frombuffer(d, np.uint8).reshape(-1, 3)
        elif t == b"tRNS":
            trns = np.frombuffer(d, np.uint8)
        elif t == b"IDAT":
            idat += d
    assert il == 0, "interlaced PNG unsupported"
    raw = zlib.decompress(idat)
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    if ct == 3:
        assert bd in (1, 2, 4, 8)
        bpp = 1
        stride = (w * bd + 7) // 8
    else:
        assert bd == 8, "only 8-bit supported"
        bpp = ch
        stride = w * ch
    out = np.zeros((h, stride), np.uint8)
    prev = np.zeros(stride, np.int32)
    p = 0
    for y in range(h):
        ft = raw[p]
        line = np.frombuffer(raw[p + 1:p + 1 + stride], np.uint8).astype(np.int32)
        p += 1 + stride
        cur = np.zeros(stride, np.int32)
        if ft == 0:
            cur = line
        elif ft == 2:
            cur = (line + prev) & 255
        else:
            for i in range(stride):
                a = cur[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                if ft == 1:
                    v = line[i] + a
                elif ft == 3:
                    v = line[i] + ((a + b) >> 1)
                else:
                    pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                    pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                    v = line[i] + pr
                cur[i] = v & 255
        out[y] = cur
        prev = cur
    if ct == 3:
        bits = np.unpackbits(out, axis=1) if bd < 8 else out
        if bd < 8:
            bits = bits.reshape(h, -1, bd)[:, :w]
            idx = np.zeros((h, w), np.int32)
            for k in range(bd):
                idx = idx * 2 + bits[:, :, k]
        else:
            idx = out[:, :w].astype(np.int32)
        rgb = plte[idx]
        alpha = np.full((h, w), 255, np.uint8)
        if trns is not None:
            ta = np.full(len(plte), 255, np.uint8)
            ta[:len(trns)] = trns
            alpha = ta[idx]
        return np.dstack([rgb, alpha]).astype(np.uint8)
    img = out[:, :w * ch].reshape(h, w, ch)
    if ch == 1:
        return np.dstack([img, img, img, np.full((h, w, 1), 255, np.uint8)])
    if ch == 2:
        return np.dstack([img[:, :, :1]] * 3 + [img[:, :, 1:]])
    if ch == 3:
        return np.dstack([img, np.full((h, w, 1), 255, np.uint8)])
    return img.copy()


def upscale(rgba, k):
    return np.repeat(np.repeat(np.asarray(rgba), k, 0), k, 1)
