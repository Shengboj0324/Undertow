#!/usr/bin/env python3
"""Worldsfall procedural audio synthesizer.

Renders every sound file stem referenced by ``sound_spec.py`` (block sounds,
events, ambience and music) to Ogg Vorbis under
``src/main/resources/assets/worldsfall/sounds/<stem>.ogg``.

Fully deterministic: every stem gets its own RNG seeded from a CRC32 of the stem
name, so re-running produces identical audio.

Dependencies: python3, numpy, scipy, ffmpeg (with libvorbis) on PATH.

Usage:
    python3 tools/audio/synth.py --tmp /path/to/scratch [--only PREFIX ...] [--jobs N]
    python3 tools/audio/synth.py --stems music/ladders event/maw_open ...   (exact stems)
    python3 tools/audio/synth.py --missing                (only stems with no .ogg yet)

Techniques used (see the instrument sections below):
  * modal synthesis (sums of exponentially decaying, slightly inharmonic
    partials) for glass, crystal, bells, stone and wood resonances
  * additive felt-piano model (stiff-string inharmonicity, detuned unison
    strings, two-stage decay, felt hammer noise, damper release)
  * PADsynth (spectral-bandwidth wavetables) for pads, drones and choir
  * STFT-domain spectral shaping of noise for wind, whooshes, breath, rumble
  * source/filter voice model (band-limited glottal-ish source + formants)
    for whale calls, creature vocalisations, groans
  * FM for metallic and energy tones
  * 16-line feedback-delay-network reverb (rendered to an impulse response and
    applied by FFT convolution), damped per line
  * BS.1770-style K-weighted gated loudness for music/ambience normalisation
"""

import argparse
import math
import os
import shutil
import subprocess
import sys
import tempfile
import time
import zlib
from concurrent.futures import ProcessPoolExecutor, as_completed

import numpy as np
from scipy import signal
from scipy.io import wavfile
from scipy.linalg import hadamard
from scipy.ndimage import minimum_filter1d, uniform_filter1d

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import sound_spec as spec  # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT_DIR = os.path.join(REPO, "src", "main", "resources", "assets", "worldsfall", "sounds")

SR = 44100
NYQ = SR / 2.0
TWO_PI = 2.0 * np.pi


# =============================================================================
# Basic helpers
# =============================================================================

def ns(d):
    """Seconds -> sample count (>= 1)."""
    return max(1, int(round(d * SR)))


def tt(d):
    return np.arange(ns(d)) / SR


def dbg(x):
    return 10.0 ** (x / 20.0)


def mtof(m):
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


_NOTE_OFS = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def nm(name):
    """Note name -> MIDI number. 'C4' = 60, 'F#3', 'Bb2'."""
    letter = name[0].upper()
    rest = name[1:]
    acc = 0
    while rest and rest[0] in "#b":
        acc += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    octave = int(rest)
    return 12 * (octave + 1) + _NOTE_OFS[letter] + acc


def nf(name):
    return mtof(nm(name))


def seed_of(name):
    return zlib.crc32(name.encode("utf-8")) & 0xFFFFFFFF


def pad_to(x, n):
    if len(x) >= n:
        return x[:n]
    shape = (n - len(x),) + x.shape[1:]
    return np.concatenate([x, np.zeros(shape)], axis=0)


def mixdown(*xs):
    """Sum signals of different lengths (all mono or all stereo)."""
    n = max(len(x) for x in xs)
    out = np.zeros((n,) + xs[0].shape[1:])
    for x in xs:
        out[: len(x)] += x
    return out


def add_at(buf, x, t, gain=1.0):
    """Add x into buf starting at time t (seconds). Handles bounds and mono->stereo."""
    i = int(round(t * SR))
    if i >= len(buf):
        return buf
    j0 = 0
    if i < 0:
        j0 = -i
        i = 0
    seg = x[j0: j0 + (len(buf) - i)]
    if len(seg) < len(x) - j0:
        seg = fade(seg, 0.0, 0.005)
    if buf.ndim == 2 and seg.ndim == 1:
        seg = np.stack([seg, seg], axis=1)
    buf[i: i + len(seg)] += gain * seg
    return buf


def pan(x, p):
    """Constant-power pan of mono x; p in [-1, 1]."""
    a = (p + 1) * np.pi / 4
    return np.stack([x * np.cos(a), x * np.sin(a)], axis=1)


def at(t, pts, log=False):
    """Interpolate breakpoint curve [(time, value), ...] at times t."""
    p = np.asarray(pts, dtype=float)
    if log:
        return np.exp(np.interp(t, p[:, 0], np.log(p[:, 1])))
    return np.interp(t, p[:, 0], p[:, 1])


def curve(d, pts, log=False):
    return at(tt(d), pts, log)


def env_ad(d, a=0.005, tau=0.1, hold=0.0):
    """Raised-cosine attack, optional hold, exponential decay."""
    t = tt(d)
    e = np.ones_like(t)
    if a > 0:
        m = t < a
        e[m] = 0.5 - 0.5 * np.cos(np.pi * t[m] / a)
    m2 = t > a + hold
    e[m2] *= np.exp(-(t[m2] - a - hold) / tau)
    k = min(len(e) // 8, ns(0.006))
    if k > 1:
        e[-k:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, k))
    return e


def env_swell(d, a, r, shape=1.0):
    """Raised-cosine attack over a, sustain, raised-cosine release over the last r."""
    t = tt(d)
    e = np.ones_like(t)
    if a > 0:
        m = t < a
        e[m] = (0.5 - 0.5 * np.cos(np.pi * t[m] / a)) ** shape
    if r > 0:
        m = t > d - r
        e[m] *= (0.5 + 0.5 * np.cos(np.pi * (t[m] - (d - r)) / r)) ** shape
    return e


def fade(x, fin=0.002, fout=0.02):
    x = x.copy()
    n_in = min(len(x) // 2, int(fin * SR))
    n_out = min(len(x) // 2, int(fout * SR))
    if n_in > 0:
        w = 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, n_in))
        x[:n_in] = (x[:n_in].T * w).T
    if n_out > 0:
        w = 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, n_out))
        x[-n_out:] = (x[-n_out:].T * w).T
    return x


def norm_peak(x, p=1.0):
    m = np.max(np.abs(x))
    return x * (p / m) if m > 0 else x


def softclip(x, drive=1.0):
    return np.tanh(drive * x) / np.tanh(drive)


def reverse(x):
    return x[::-1].copy()


def resample_speed(x, speed):
    """Varispeed playback: speed < 1 -> slower and lower."""
    n_out = int(len(x) / speed)
    idx = np.arange(n_out) * speed
    return np.interp(idx, np.arange(len(x)), x)


# =============================================================================
# Filters
# =============================================================================

def _butter(x, kind, f, order=2):
    f = np.atleast_1d(np.asarray(f, dtype=float))
    f = np.clip(f, 5.0, NYQ * 0.97)
    wn = f if len(f) > 1 else f[0]
    sos = signal.butter(order, wn, btype=kind, fs=SR, output="sos")
    return signal.sosfilt(sos, x, axis=0)


def lp(x, f, order=2):
    return _butter(x, "lowpass", f, order)


def hp(x, f, order=2):
    return _butter(x, "highpass", f, order)


def bp(x, lo, hi, order=2):
    return _butter(x, "bandpass", [lo, hi], order)


def reson(x, f, q):
    """Resonant band-pass (unity gain at f)."""
    f = min(max(f, 10.0), NYQ * 0.97)
    b, a = signal.iirpeak(f, q, fs=SR)
    return signal.lfilter(b, a, x, axis=0)


def stft_shape(x, gfn, nfft=1024):
    """Time-varying spectral shaping. gfn(f[:,None], t[None,:]) -> gain matrix."""
    n = len(x)
    if n < nfft:
        x = pad_to(x, nfft)
    hop = nfft // 4
    f, t, Z = signal.stft(x, fs=SR, window="hann", nperseg=nfft, noverlap=nfft - hop)
    G = gfn(f[:, None], t[None, :])
    _, y = signal.istft(Z * G, fs=SR, window="hann", nperseg=nfft, noverlap=nfft - hop)
    return pad_to(y, n)[:n]


def band(f, fc, bw_oct):
    """Gaussian band in log-frequency."""
    return np.exp(-0.5 * (np.log2(np.maximum(f, 1.0) / fc) / bw_oct) ** 2)


def tilt(f, db_per_oct, ref=1000.0):
    return (np.maximum(f, 20.0) / ref) ** (db_per_oct / 6.0206)


# =============================================================================
# Noise and modulation sources
# =============================================================================

def white(d, rng):
    return rng.standard_normal(ns(d))


def colored(d, rng, slope=-3.0, n=None):
    """Noise with spectral slope in dB/octave (-3 pink, -6 brown)."""
    n = n or ns(d)
    X = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1]
    X *= (f / 1000.0) ** (slope / 6.0206)
    X[0] = 0
    y = np.fft.irfft(X, n)
    return y / (np.std(y) + 1e-12)


def smooth_noise(d, rate, rng):
    """Smooth random control signal (approx. unit std) with ~rate Hz bandwidth."""
    n = ns(d)
    k = max(4, int(d * rate) + 4)
    pts = rng.standard_normal(k)
    xs = np.linspace(0, n, k)
    y = np.interp(np.arange(n), xs, pts)
    if n > 64:
        y = lp(y, max(rate, 0.05) * 1.5, 2)
    s = np.std(y)
    return y / s if s > 0 else y


def periodic_smooth_noise(n, cycles, rng):
    """Smooth random signal of length n that is exactly periodic (for loops)."""
    k = max(2, int(cycles))
    X = np.zeros(n // 2 + 1, dtype=complex)
    X[1:k + 1] = rng.standard_normal(k) + 1j * rng.standard_normal(k)
    y = np.fft.irfft(X, n)
    return y / (np.std(y) + 1e-12)


def noise_shaped(d, rng, gfn, nfft=1024, base=None):
    x = base if base is not None else white(d, rng)
    return stft_shape(x, gfn, nfft)


# =============================================================================
# Oscillators and resonators
# =============================================================================

def phase_of(f):
    """Integrated phase (radians) of an instantaneous frequency array (Hz)."""
    return TWO_PI * np.cumsum(f) / SR


def sine(f, d=None, ph=0.0):
    if np.ndim(f) == 0:
        return np.sin(TWO_PI * f * tt(d) + ph)
    return np.sin(phase_of(f) + ph)


def harmonic(f0, amps, rng=None, ratios=None):
    """Band-limited additive tone; f0 is an array (Hz)."""
    ph = phase_of(f0)
    out = np.zeros_like(f0)
    for k, a in enumerate(amps):
        r = ratios[k] if ratios is not None else (k + 1)
        if a == 0:
            continue
        fk = r * f0
        lim = np.clip((NYQ * 0.9 - fk) / (NYQ * 0.05), 0, 1)
        if not lim.any():
            continue
        p0 = rng.uniform(0, TWO_PI) if rng is not None else 0.0
        out += a * lim * np.sin(r * ph + p0)
    return out


def modes(d, freqs, taus, amps, rng=None):
    """Sum of exponentially decaying sinusoids (modal impulse response)."""
    n = ns(d)
    out = np.zeros(n)
    for f, tau, a in zip(freqs, taus, amps):
        if f <= 0 or f >= NYQ * 0.95 or a == 0:
            continue
        L = min(n, ns(min(d, tau * 7.0)))
        t = np.arange(L) / SR
        p0 = rng.uniform(0, TWO_PI) if rng is not None else 0.0
        seg = a * np.exp(-t / tau) * np.sin(TWO_PI * f * t + p0)
        k = min(L // 5, ns(0.03))
        if k > 1:
            seg[-k:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, k))
        out[:L] += seg
    return out


def excite(exc, ir):
    """Drive a resonator (impulse response) with an excitation signal."""
    y = signal.oaconvolve(exc, ir)
    return y[: max(len(exc), len(ir))]


def click(rng, ms=1.5, lpf=9000.0, hpf=200.0):
    n = ns(ms / 1000.0) + 8
    t = np.arange(n) / SR
    x = rng.standard_normal(n) * np.exp(-t / (ms / 3000.0))
    x[0] += 2.0
    return hp(lp(x, lpf), hpf)


def thump(d, f0, f1, tau, glide=0.03, a=0.002):
    """Pitched body thump (sine with downward pitch glide)."""
    t = tt(d)
    f = f1 + (f0 - f1) * np.exp(-t / glide)
    return np.sin(phase_of(f)) * env_ad(d, a, tau)


def bubble(d, f0, f1, tau, rng):
    t = tt(d)
    f = f0 * (f1 / f0) ** np.clip(t / d, 0, 1)
    return np.sin(phase_of(f) + rng.uniform(0, 6)) * env_ad(d, 0.002, tau)


def fm_tone(d, fc, ratio, index, amp=None, fb_ratio=None):
    """Two-operator FM. fc, index, amp may be arrays of len ns(d)."""
    n = ns(d)
    fc = np.broadcast_to(np.asarray(fc, dtype=float), (n,))
    index = np.broadcast_to(np.asarray(index, dtype=float), (n,))
    pc = phase_of(fc)
    pm = phase_of(fc * ratio)
    y = np.sin(pc + index * np.sin(pm))
    if amp is not None:
        y = y * amp
    return y


# =============================================================================
# Reverb: 16-line FDN rendered to an impulse response, applied by convolution
# =============================================================================

_IR_CACHE = {}


def fdn_ir(rt60=2.0, damp=5000.0, size=1.0, seed=1, length=None):
    key = (round(rt60, 3), round(damp), round(size, 3), seed, length)
    if key in _IR_CACHE:
        return _IR_CACHE[key]
    r = np.random.default_rng(9000 + seed)
    base = np.array([1031, 1153, 1277, 1399, 1523, 1663, 1789, 1931,
                     2063, 2203, 2341, 2477, 2621, 2767, 2909, 3061])
    D = np.maximum((base * size).astype(int), 128)
    nl = len(D)
    B = int(D.min())
    L = ns(length if length else min(rt60 * 1.25 + 0.2, 10.0))
    H = hadamard(nl) / np.sqrt(nl)
    g = 10.0 ** (-3.0 * D / (rt60 * SR))
    c = math.exp(-TWO_PI * damp / SR)
    buf = np.zeros((nl, L + int(D.max()) + B + 16))
    ex_len = ns(0.012)
    ex = r.standard_normal((nl, ex_len)) * np.exp(-np.arange(ex_len) / SR / 0.004)
    for i in range(nl):
        buf[i, D[i]: D[i] + ex_len] += ex[i]
    zi = np.zeros((nl, 1))
    cL = r.choice([-1.0, 1.0], nl)
    cR = r.choice([-1.0, 1.0], nl)
    cR[: nl // 2] *= -1
    outL = np.zeros(L)
    outR = np.zeros(L)
    for s in range(0, L, B):
        e = min(s + B, L)
        y = buf[:, s:e]
        yf, zi = signal.lfilter([1 - c], [1, -c], y, axis=1, zi=zi)
        fb = (H @ yf) * g[:, None]
        for i in range(nl):
            buf[i, s + D[i]: e + D[i]] += fb[i]
        outL[s:e] = cL @ y
        outR[s:e] = cR @ y
    ir = np.stack([outL, outR], axis=1)
    ir = hp(ir, 50.0)
    ir = fade(ir, 0.0, min(0.5, rt60 * 0.2))
    ir /= np.sqrt(np.sum(ir ** 2, axis=0, keepdims=True)) + 1e-12
    _IR_CACHE[key] = ir
    return ir


def reverb(x, wet=0.25, rt60=2.0, damp=5000.0, size=1.0, seed=1, stereo=None, dry=1.0,
           predelay=0.0):
    """Convolve with an FDN impulse response. Output is longer than input (tail)."""
    x = np.asarray(x, dtype=float)
    mono_in = x.ndim == 1
    st = (not mono_in) if stereo is None else stereo
    ir = fdn_ir(rt60, damp, size, seed)
    if predelay > 0:
        ir = np.concatenate([np.zeros((ns(predelay), 2)), ir], axis=0)
    if mono_in and not st:
        w = signal.oaconvolve(x, ir[:, 0])
        out = wet * w
        out[: len(x)] += dry * x
        return out
    if mono_in:
        xl = xr = x
    else:
        xl, xr = x[:, 0], x[:, 1]
    wl = signal.oaconvolve(0.75 * xl + 0.25 * xr, ir[:, 0])
    wr = signal.oaconvolve(0.25 * xl + 0.75 * xr, ir[:, 1])
    out = wet * np.stack([wl, wr], axis=1)
    out[: len(x), 0] += dry * xl
    out[: len(x), 1] += dry * xr
    return out


# =============================================================================
# Loudness (ITU-R BS.1770-style K-weighting with gating)
# =============================================================================

def _kweight_sos():
    G, Q, fc = 4.0, 1 / np.sqrt(2), 1500.0
    A = 10 ** (G / 40)
    w0 = TWO_PI * fc / SR
    al = np.sin(w0) / (2 * Q)
    cw = np.cos(w0)
    sa = 2 * np.sqrt(A) * al
    b1 = [A * ((A + 1) + (A - 1) * cw + sa), -2 * A * ((A - 1) + (A + 1) * cw),
          A * ((A + 1) + (A - 1) * cw - sa)]
    a1 = [(A + 1) - (A - 1) * cw + sa, 2 * ((A - 1) - (A + 1) * cw), (A + 1) - (A - 1) * cw - sa]
    fc2, Q2 = 38.0, 0.5
    w0 = TWO_PI * fc2 / SR
    al = np.sin(w0) / (2 * Q2)
    cw = np.cos(w0)
    b2 = [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2]
    a2 = [1 + al, -2 * cw, 1 - al]
    s1 = np.r_[np.array(b1) / a1[0], np.array(a1) / a1[0]]
    s2 = np.r_[np.array(b2) / a2[0], np.array(a2) / a2[0]]
    return np.vstack([s1, s2])


_KSOS = _kweight_sos()


def limiter(x, thr_db=-1.5, win=0.012, circular=False):
    """Look-ahead peak limiter (min-filter + moving-average gain; never exceeds thr)."""
    a = np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=1)
    g = np.minimum(1.0, dbg(thr_db) / np.maximum(a, 1e-12))
    if g.min() >= 1.0:
        return x
    W = ns(win) | 1
    mode = "wrap" if circular else "nearest"
    g = uniform_filter1d(minimum_filter1d(g, W, mode=mode), W, mode=mode)
    return x * (g if x.ndim == 1 else g[:, None])


def lufs(x):
    x2 = x if x.ndim == 2 else x[:, None]
    y = signal.sosfilt(_KSOS, x2, axis=0)
    e = np.sum(y ** 2, axis=1)
    blk, hop = ns(0.4), ns(0.1)
    if len(e) <= blk:
        z = np.array([np.mean(e)])
    else:
        c = np.concatenate([[0.0], np.cumsum(e)])
        starts = np.arange(0, len(e) - blk + 1, hop)
        z = (c[starts + blk] - c[starts]) / blk
    lk = -0.691 + 10 * np.log10(z + 1e-20)
    z = z[lk > -70]
    if len(z) == 0:
        return -100.0
    rel = -0.691 + 10 * np.log10(np.mean(z)) - 10
    z2 = z[(-0.691 + 10 * np.log10(z + 1e-20)) > rel]
    if len(z2) == 0:
        z2 = z
    return float(-0.691 + 10 * np.log10(np.mean(z2)))


# =============================================================================
# Instruments
# =============================================================================

_NOTE_CACHE = {}


def piano(m, vel=0.5, hold=2.0, rel=None, bright=0.5, seed=0):
    """Felt piano note (MIDI m). Additive stiff-string model.

    hold: seconds until the damper falls; rel: damper release time.
    """
    key = ("pno", m, round(vel, 2), round(hold, 2), rel, round(bright, 2), seed % 4)
    if key in _NOTE_CACHE:
        return _NOTE_CACHE[key]
    rng = np.random.default_rng(int(m * 131 + seed * 7 + vel * 1000))
    f = mtof(m)
    if rel is None:
        rel = 0.25 + 0.6 * (261.6 / f) ** 0.4
    d = hold + rel * 5 + 0.05
    t = tt(d)
    B = 1.0e-4 * 2.0 ** ((m - 60) / 12.0 * 0.85)
    nmax = int(max(1, min(22, 7000.0 / f)))
    tau0 = float(np.clip(4.5 * (261.6 / f) ** 0.6, 0.6, 9.0))
    fc = 700.0 + 3200.0 * vel * bright        # felt: soft upper partials
    strike = 1.0 / 7.3                        # hammer position
    out = np.zeros_like(t)
    for n in range(1, nmax + 1):
        fn = n * f * math.sqrt(1 + B * n * n)
        if fn > NYQ * 0.9:
            break
        a = n ** -1.0 * math.exp(-(fn - f) / fc) * (0.35 + abs(math.sin(math.pi * n * strike)))
        tau_n = tau0 / (1 + 0.45 * (n - 1) ** 1.15)
        L = min(len(t), ns(min(d, tau_n * 7.5)))
        tl = t[:L]
        env = 0.5 * np.exp(-tl / (tau_n * 0.3)) + 0.5 * np.exp(-tl / tau_n)
        for det, w in ((-1, 0.62), (1, 0.38)):          # unequal unison strings: gentle beating
            cents = det * rng.uniform(0.05, 0.4)
            fs = fn * 2 ** (cents / 1200)
            out[:L] += w * a * env * np.sin(TWO_PI * fs * tl + rng.uniform(0, TWO_PI))
    # felt hammer: soft, short, low-passed knock
    hn = ns(0.03)
    ham = rng.standard_normal(hn) * np.exp(-np.arange(hn) / SR / 0.006)
    ham = lp(ham, 400 + 900 * vel) * 0.07 * vel
    out[:hn] += ham
    # soft attack (felt) and damper release
    att = 1 - np.exp(-t / (0.0025 + 0.004 * (1 - vel)))
    damp = np.where(t > hold, np.exp(-(t - hold) / rel), 1.0)
    out = out * att * damp * (vel ** 1.4)
    out = fade(out, 0.0, 0.05)
    _NOTE_CACHE[key] = out
    return out


def glass_chime(f, vel=0.6, decay=2.5, rng=None, mallet=0.15):
    """Struck glass / crystal bar: few strongly inharmonic modes + mallet tick."""
    rng = rng or np.random.default_rng(int(f))
    ratios = [1.0, 1.0016, 2.756, 5.404, 8.933]
    amps = [1.0, 0.5, 0.28, 0.1, 0.04]
    taus = [decay, decay * 0.9, decay * 0.32, decay * 0.12, decay * 0.06]
    x = modes(decay * 3.5, [f * r for r in ratios], taus, amps, rng)
    c = click(rng, 0.6, 12000, 2500) * mallet
    x[: len(c)] += c
    x *= 1 - np.exp(-tt(decay * 3.5) / 0.0008)
    return x * vel


def glass_harm(f, d, attack=0.6, release=1.0, rng=None, trem=0.04):
    """Glass-harmonica / bowed-glass sustained tone. f may be an array."""
    rng = rng or np.random.default_rng(7)
    n = ns(d)
    if np.ndim(f) == 0:
        f = np.full(n, float(f))
    jit = 1 + 0.0015 * smooth_noise(d, 3, rng)
    ff = f * jit
    x = harmonic(ff, [1.0, 0.13, 0.035, 0.012], rng)
    x += 0.4 * np.sin(phase_of(ff * 1.0012) + 1.0)   # slight beating pair
    tr = 1 + trem * np.sin(TWO_PI * 5.3 * tt(d)) + 0.05 * smooth_noise(d, 1.5, rng)
    # faint friction noise around the fundamental
    nz = stft_shape(white(d, rng), lambda F, T: band(F, np.mean(f), 0.15), 2048) * 0.02
    y = (x * tr + nz) * env_swell(d, attack, release)
    return y


BELL_RATIOS = [1.0, 2.0, 2.0047, 2.76, 3.91, 5.40, 6.82]


def bell(f, decay=2.0, rng=None, soft=0.5, ratios=None, amps=None):
    """Soft metallic bell / lantern ring."""
    rng = rng or np.random.default_rng(int(f))
    ratios = ratios or BELL_RATIOS
    amps = amps or [1.0, 0.55, 0.45, 0.35, 0.22, 0.12, 0.06]
    taus = [decay * (1.0 / (1 + 0.55 * k)) for k in range(len(ratios))]
    ir = modes(decay * 4, [f * r for r in ratios], taus, amps, rng)
    exc_n = ns(0.004 + 0.006 * soft)
    exc = np.hanning(exc_n * 2)[exc_n:] * (1 + 0.3 * rng.standard_normal(exc_n))
    exc = lp(exc, 2500 + 6000 * (1 - soft))
    y = excite(exc, ir)
    return y / (np.max(np.abs(y)) + 1e-9)


SINGING_BOWL = [1.0, 1.0042, 2.32, 2.325, 4.25, 6.63, 9.38]


def bowed_metal(f, d, rng, attack=2.5, release=3.0, bright=0.5, ratios=None):
    """Bowed metal (plate/bowl): slow bow swell, inharmonic beating partials, rosin noise."""
    ratios = ratios or SINGING_BOWL
    amps = [1.0, 0.7, 0.45 * bright + 0.1, 0.3 * bright + 0.05, 0.2 * bright, 0.1 * bright, 0.05 * bright]
    n = ns(d)
    t = tt(d)
    out = np.zeros(n)
    for r, a in zip(ratios, amps):
        fk = f * r
        if fk > NYQ * 0.85 or a <= 0:
            continue
        am = 1 + 0.25 * smooth_noise(d, 0.7, rng)
        fj = fk * (1 + 0.0008 * smooth_noise(d, 0.5, rng))
        out += a * np.clip(am, 0.2, 2) * np.sin(phase_of(fj) + rng.uniform(0, TWO_PI))
    bow = stft_shape(white(d, rng), lambda F, T: band(F, f * 2.3, 0.35) + 0.5 * band(F, f, 0.2), 2048)
    out += 0.06 * bow * (1 + 0.5 * smooth_noise(d, 3, rng))
    out = lp(out, 3500 + 4000 * bright)
    return out * env_swell(d, attack, release, 1.5)


# ---------------------------------------------------------------- PADsynth

_PAD_CACHE = {}

VOWELS = {
    "a": [(800, 90, 1.0), (1150, 100, 0.5), (2900, 140, 0.22), (3900, 160, 0.08)],
    "o": [(450, 70, 1.0), (800, 90, 0.45), (2830, 120, 0.1), (3800, 140, 0.04)],
    "u": [(325, 60, 1.0), (700, 70, 0.22), (2530, 150, 0.04)],
    "e": [(450, 70, 1.0), (1850, 110, 0.4), (2600, 130, 0.22), (3300, 150, 0.08)],
    "i": [(290, 60, 1.0), (2100, 120, 0.3), (2950, 140, 0.18)],
    "m": [(260, 60, 1.0), (1000, 100, 0.08), (2200, 150, 0.03)],
}


def formant_gain(freq, vowel, scale=1.0, floor=0.02, bw_scale=1.0):
    g = np.full(np.shape(freq), floor, dtype=float)
    for F, B, A in VOWELS[vowel]:
        g = g + A * np.exp(-0.5 * ((freq - F * scale) / (B * 1.6 * bw_scale)) ** 2)
    return g


def padsynth(f0, kind="warm", seed=0, N=2 ** 18):
    """PADsynth wavetable (stereo, perfectly periodic in N samples)."""
    key = (round(f0, 3), kind, seed, N)
    if key in _PAD_CACHE:
        return _PAD_CACHE[key]
    rng = np.random.default_rng(seed + int(f0 * 10))
    nb = N // 2 + 1
    df = SR / N
    freqs = np.arange(nb) * df
    A = np.zeros(nb)
    if kind == "warm":
        bw, bws = 22.0, 0.9
        amp = lambda k, fk: k ** -1.1 * math.exp(-fk / 2200.0) * (1.0 if k % 2 else 0.8)
    elif kind == "cold":
        bw, bws = 9.0, 0.7
        amp = lambda k, fk: k ** -1.4 * math.exp(-fk / 3000.0) * (1.0 if k % 2 else 0.45)
    elif kind == "teal":
        bw, bws = 30.0, 1.0
        amp = lambda k, fk: k ** -0.8 * math.exp(-fk / 4000.0) * (0.6 + 0.4 * math.exp(-0.5 * ((fk - 1800) / 900) ** 2))
    elif kind == "deep":
        bw, bws = 16.0, 1.1
        amp = lambda k, fk: k ** -1.3 * math.exp(-fk / 1100.0)
    elif kind == "glass":
        bw, bws = 4.0, 0.5
        amp = lambda k, fk: [1.0, 0.18, 0.06, 0.03, 0.015][k - 1] if k <= 5 else 0.0
    elif kind.startswith("choir"):
        vowel = kind.split("_")[1] if "_" in kind else "a"
        bw, bws = 38.0, 1.0
        amp = lambda k, fk: (k ** -0.35) * float(formant_gain(fk, vowel, 1.0, 0.015)) * math.exp(-fk / 5000.0)
    else:
        raise ValueError(kind)
    out = []
    for ch in range(2):
        A[:] = 0
        for k in range(1, 400):
            fk = f0 * k
            if fk > NYQ * 0.85:
                break
            a = amp(k, fk)
            if a < 1e-4:
                continue
            bwhz = (2 ** (bw / 1200.0) - 1) * f0 * k ** bws
            sig = max(bwhz * 0.5, df * 0.7)
            lo = int(max(0, (fk - 4 * sig) / df))
            hi = int(min(nb - 1, (fk + 4 * sig) / df) + 1)
            xx = (freqs[lo:hi] - fk) / sig
            A[lo:hi] += a * np.exp(-xx * xx) / math.sqrt(sig)
        X = A * np.exp(1j * rng.uniform(0, TWO_PI, nb))
        X[0] = 0
        y = np.fft.irfft(X, N)
        out.append(y / (np.std(y) + 1e-12))
    smp = np.stack(out, axis=1) * 0.25
    _PAD_CACHE[key] = smp
    return smp


def pad_note(f, d, kind="warm", attack=2.0, release=3.0, seed=0, width=1.0, offset=None):
    """Sustained PADsynth note of duration d (stereo)."""
    smp = padsynth(f, kind, seed)
    n = ns(d)
    N = len(smp)
    off = offset if offset is not None else (seed * 7919 + int(f * 13)) % N
    idx = (np.arange(n) + off) % N
    y = smp[idx]
    if width != 1.0:
        mid = 0.5 * (y[:, 0] + y[:, 1])
        side = 0.5 * (y[:, 0] - y[:, 1]) * width
        y = np.stack([mid + side, mid - side], axis=1)
    e = env_swell(d, attack, release, 1.3)
    return y * e[:, None]


def pad_chord(notes, d, kind="warm", attack=2.0, release=3.0, seed=0, gains=None):
    out = np.zeros((ns(d), 2))
    for i, m in enumerate(notes):
        g = gains[i] if gains else 1.0
        out += g * pad_note(mtof(m), d, kind, attack, release, seed + i)
    return out


# ---------------------------------------------------------------- voices

def voice(d, f0, rng, vowel="a", vowel2=None, tilt_exp=1.2, breath=0.05, fscale=1.0,
          fscale2=None, jitter=0.004, bw_scale=1.0, nmax=80, nfft=2048):
    """Source/filter voice: band-limited harmonic source -> time-varying formants."""
    n = ns(d)
    f0 = np.broadcast_to(np.asarray(f0, dtype=float), (n,)).copy()
    if jitter > 0:
        f0 *= 1 + jitter * smooth_noise(d, 25, rng)
    nh = int(min(nmax, NYQ * 0.9 / max(np.min(f0), 20)))
    amps = [k ** -tilt_exp for k in range(1, nh + 1)]
    src = harmonic(f0, amps, rng)
    src = src / (np.std(src) + 1e-9)
    br = white(d, rng) * breath * 3
    v2 = vowel2 or vowel
    fs2 = fscale2 if fscale2 is not None else fscale

    def g(F, T):
        w = np.clip(T / d, 0, 1)
        sc = fscale * (1 - w) + fs2 * w
        return (1 - w) * formant_gain(F / sc, vowel, 1.0, 0.03, bw_scale) + \
            w * formant_gain(F / sc, v2, 1.0, 0.03, bw_scale)

    return stft_shape(src + br, g, nfft)


def whale(d, fpts, rng, vib=4.0, vib_depth=0.012, vowel="u", vowel2="o", fscale=0.55,
          tilt_exp=1.5, breath=0.04, sub=0.35):
    """Deep whale-like call: gliding pitch with delayed vibrato, dark formants, sub octave."""
    t = tt(d)
    f = curve(d, fpts, log=True)
    ve = np.clip(t / (0.4 * d), 0, 1) ** 2
    f = f * (1 + vib_depth * ve * np.sin(TWO_PI * vib * t + 0.3 * np.sin(TWO_PI * 0.7 * t)))
    y = voice(d, f, rng, vowel, vowel2, tilt_exp, breath, fscale, fscale * 1.15, 0.003)
    y += sub * np.sin(phase_of(f * 0.5)) * np.std(y) * 1.4
    y = lp(y, 2000, 4)
    return y * env_swell(d, min(0.35, d * 0.3), min(0.6, d * 0.4), 1.2)


# =============================================================================
# Material primitives (block sounds and impacts)
# =============================================================================

def grain_burst(rng, dur, lo, hi):
    n = ns(dur)
    x = rng.standard_normal(n) * np.exp(-np.arange(n) / (n * 0.3 + 1))
    return bp(x, lo, hi, 2) if hi > lo else x


def crumble(d, rng, count, lo, hi, gdur=(0.002, 0.012), shape=2.5, start=0.0, decay_amp=True,
            bands=None):
    """Granular crumble: many short noise grains with a decaying density."""
    buf = np.zeros(ns(d) + ns(0.05))
    times = start + (d - start) * rng.beta(1.0, shape, count)
    for tm in np.sort(times):
        g = rng.uniform(*gdur)
        if bands:
            l2, h2 = bands[rng.integers(len(bands))]
        else:
            c = math.exp(rng.uniform(math.log(lo), math.log(hi)))
            l2, h2 = c / 1.6, min(c * 1.6, NYQ * 0.9)
        amp = rng.uniform(0.3, 1.0) * ((1 - (tm - start) / max(d - start, 1e-3)) ** 1.5 if decay_amp else 1)
        add_at(buf, grain_burst(rng, g, l2, h2), tm, amp)
    return fade(buf[: ns(d)], 0.0, 0.01)


def stone_hits(d, rng, count, f_lo, f_hi, tau=(0.008, 0.03), span=None, shape=2.0, start=0.0):
    """Modal stone/ceramic clacks: excitation clicks into small mode sets."""
    span = span if span is not None else d
    buf = np.zeros(ns(d) + ns(0.2))
    times = start + span * rng.beta(1.0, shape, count)
    for tm in np.sort(times):
        base = math.exp(rng.uniform(math.log(f_lo), math.log(f_hi)))
        fr = [base * r for r in (1.0, 1.58, 2.37, 3.1)]
        ta = [rng.uniform(*tau) * k for k in (1.0, 0.7, 0.5, 0.35)]
        am = [1.0, 0.6, 0.4, 0.25]
        ir = modes(max(ta) * 6, fr, ta, am, rng)
        x = excite(click(rng, rng.uniform(0.5, 2.0), 9000, 150), ir)
        add_at(buf, x, tm, rng.uniform(0.35, 1.0) * (1 - 0.6 * (tm - start) / max(span, 1e-3)))
    return fade(buf[: ns(d)], 0.0, 0.01)


def glass_pings(d, rng, count, f_lo, f_hi, tau=(0.05, 0.25), span=None, shape=2.0, start=0.0,
                ratios=(1.0, 2.32, 4.25, 6.63)):
    span = span if span is not None else d
    buf = np.zeros(ns(d) + ns(1.5))
    times = start + span * rng.beta(1.0, shape, count)
    for tm in np.sort(times):
        base = math.exp(rng.uniform(math.log(f_lo), math.log(f_hi)))
        tb = rng.uniform(*tau)
        fr = [base * r for r in ratios]
        ta = [tb, tb * 0.5, tb * 0.3, tb * 0.2]
        am = [1.0, 0.45, 0.25, 0.12]
        x = modes(tb * 5, fr, ta, am, rng) * (1 - np.exp(-tt(tb * 5) / 0.0005))
        add_at(buf, x, tm, rng.uniform(0.3, 1.0) * (1 - 0.5 * (tm - start) / max(span, 1e-3)))
    return fade(buf[: ns(d)], 0.0, 0.01)


def creak(d, rng, rate_pts, mode_f, mode_tau=0.02, jitter=0.25, amp_pts=None):
    """Stick-slip friction creak: jittered pulse train exciting wood-like resonances."""
    n = ns(d)
    rate = curve(d, rate_pts, log=True)
    rate = rate * np.clip(1 + jitter * 2 * smooth_noise(d, 40, rng), 0.2, 3)
    cyc = np.floor(np.cumsum(rate) / SR)
    idx = np.nonzero(np.diff(cyc) > 0)[0] + 1
    pulses = np.zeros(n)
    pulses[idx] = rng.uniform(0.5, 1.0, len(idx))
    ir = modes(mode_tau * 7, mode_f, [mode_tau * (1 - 0.15 * k) for k in range(len(mode_f))],
               [1.0 / (1 + 0.5 * k) for k in range(len(mode_f))], rng)
    y = excite(pulses, ir)[:n]
    if amp_pts:
        y *= curve(d, amp_pts)
    return y


def squelch(d, rng, f_pts, bw=0.5, amp_pts=None):
    """Wet squelch: noise through a moving resonant band."""
    fp = np.asarray(f_pts, dtype=float)

    def g(F, T):
        fc = np.exp(np.interp(T, fp[:, 0], np.log(fp[:, 1])))
        return band(F, fc, bw) * 1.0 + 0.15 * band(F, fc * 2.1, bw)
    y = noise_shaped(d, rng, g, 512)
    if amp_pts:
        y *= curve(d, amp_pts)
    return y


def whoosh(d, rng, f_pts, amp_pts, bw=0.8, lowcut=60):
    fp = np.asarray(f_pts, dtype=float)
    ap = np.asarray(amp_pts, dtype=float)

    def g(F, T):
        fc = np.exp(np.interp(T, fp[:, 0], np.log(fp[:, 1])))
        a = np.interp(T, ap[:, 0], ap[:, 1])
        return a * band(F, fc, bw) * (F > lowcut)
    return noise_shaped(d, rng, g, 1024)


# =============================================================================
# Recipe registry
# =============================================================================
# kind: 'sfx'      mono positional effect, trimmed, peak-normalised to `level` dBFS
#       'sfxloop'  mono loopable effect (no fades/trim), peak-normalised
#       'add'      mono ambient addition, peak-normalised (quiet)
#       'loop'     stereo seamless ambience loop, loudness-normalised to `level` LUFS
#       'music'    stereo music, loudness-normalised to `level` LUFS
#       'musicloop' stereo seamless music loop with gain applied by the recipe

RECIPES = {}


def reg(stems, kind="sfx", level=-3.0):
    if isinstance(stems, str):
        stems = [stems]

    def deco(fn):
        for i, s in enumerate(stems):
            RECIPES[s] = (fn, i, kind, level)
        return fn
    return deco


# =============================================================================
# Block sounds
# =============================================================================

def _pv(i, spread=0.05):
    return 1.0 + spread * ((i % 3) - 1)


def blk_pale_loam(kind, i, rng):
    p = _pv(i)
    if kind == "break":
        x = lp(crumble(0.45, rng, 60, 250 * p, 1800 * p, (0.003, 0.015), 2.2), 2400)
        x += 0.5 * thump(0.45, 150 * p, 90 * p, 0.05)
        rust = lp(bp(white(0.45, rng), 300, 2000), 1600) * env_ad(0.45, 0.01, 0.09) * 0.35
        return lp(x + rust, 1700, 4)
    if kind == "step":
        x = lp(crumble(0.22, rng, 14, 300 * p, 2000 * p, (0.004, 0.014), 1.6), 2600)
        rust = bp(white(0.22, rng), 300, 1500) * env_ad(0.22, 0.015, 0.05) * 0.5
        return lp(x + rust, 1900, 4)
    x = 0.7 * thump(0.3, 170 * p, 100 * p, 0.06)
    x += lp(crumble(0.3, rng, 20, 250 * p, 1500 * p, (0.003, 0.012), 3.0), 2000)
    return lp(x, 1800, 4)


def blk_stillstone(kind, i, rng):
    p = _pv(i)
    if kind == "break":
        x = stone_hits(0.45, rng, 24, 250 * p, 1100 * p, (0.006, 0.02), span=0.35, shape=2.5)
        x += 0.5 * crumble(0.45, rng, 45, 300, 2500, (0.001, 0.006), 2.2)
        x += 0.8 * thump(0.45, 115 * p, 70 * p, 0.06)
        return lp(x, 3200, 4)
    if kind == "step":
        x = stone_hits(0.2, rng, 3, 300 * p, 900 * p, (0.005, 0.014), span=0.04)
        x += 0.4 * crumble(0.2, rng, 8, 400, 2500, (0.001, 0.004), 1.5)
        x += 0.45 * thump(0.2, 125 * p, 80 * p, 0.03)
        return lp(x, 3000, 4)
    x = thump(0.35, 120 * p, 70 * p, 0.07)
    x += stone_hits(0.35, rng, 6, 300 * p, 1000 * p, (0.006, 0.02), span=0.03)
    x += 0.4 * crumble(0.35, rng, 16, 300, 2500, (0.001, 0.005), 3)
    return lp(x, 3200, 4)


def blk_mirror_shard(kind, i, rng):
    p = _pv(i, 0.07)
    if kind == "break":
        x = glass_pings(1.2, rng, 26, 2400 * p, 6500 * p, (0.04, 0.25), span=0.5, shape=2.0)
        cr = hp(white(0.04, rng), 2500) * env_ad(0.04, 0.0005, 0.008)
        x[: len(cr)] += 0.8 * cr
        return x
    if kind == "step":
        x = glass_pings(0.35, rng, 2, 3000 * p, 6000 * p, (0.03, 0.08), span=0.03)
        c = click(rng, 0.5, 12000, 3000)
        x[: len(c)] += 0.6 * c
        return x
    x = glass_pings(0.9, rng, 3, 2500 * p, 5000 * p, (0.15, 0.35), span=0.02)
    c = click(rng, 0.7, 12000, 2000)
    x[: len(c)] += 0.7 * c
    x += 0.2 * thump(0.9, 320, 260, 0.02)
    return x


def blk_lumen_crystal(kind, i, rng):
    p = _pv(i, 0.06)
    base = 720 * p
    scale = [1.0, 1.1892, 1.3348, 1.5, 1.7818, 2.0]   # cold minor-pentatonic-ish ratios
    if kind == "break":
        x = np.zeros(ns(2.5))
        for k in range(6):
            f = base * scale[rng.integers(len(scale))] * (2 if rng.random() < 0.3 else 1)
            add_at(x, glass_chime(f, rng.uniform(0.4, 1.0), rng.uniform(0.6, 1.2), rng, 0.25),
                   0.25 * rng.beta(1, 2.5) if k else 0.0)
        x += 0.3 * pad_to(hp(crumble(0.3, rng, 25, 2500, 7000, (0.001, 0.004), 2), 2000), len(x))
        return x
    if kind == "step":
        f = base * scale[i % len(scale)]
        x = glass_chime(f, 0.5, 0.45, rng, 0.35)
        return x
    x = glass_chime(base, 0.8, 0.9, rng, 0.3)
    add_at(x, glass_chime(base * 1.5, 0.5, 0.7, rng, 0.2), 0.04)
    x += pad_to(0.25 * thump(0.2, 230, 180, 0.03), len(x))
    return x


WOOD_MODES = [190, 410, 730, 1260, 2100]


def blk_voidroot(kind, i, rng):
    p = _pv(i, 0.06)
    mf = [f * p for f in WOOD_MODES]
    if kind == "break":
        d = 0.7
        x = np.zeros(ns(d))
        add_at(x, creak(0.32, rng, [(0, 35), (0.32, 120)], mf, 0.02, 0.3,
                        [(0, 0.1), (0.25, 1.0), (0.32, 0.5)]) * 0.6, 0.0)
        snap = click(rng, 1.0, 9000, 700) * 1.6
        add_at(x, snap, 0.3)
        add_at(x, crumble(0.3, rng, 40, 1500, 6500, (0.0005, 0.003), 3.0), 0.3, 0.8)
        add_at(x, thump(0.2, 190, 120, 0.035), 0.3, 0.5)
        return lp(x, 6500, 4)
    if kind == "step":
        x = creak(0.17, rng, [(0, 60 * p), (0.17, 95 * p)], mf, 0.015, 0.3,
                  [(0, 0), (0.03, 1), (0.17, 0)]) * 0.6
        x += 0.5 * crumble(0.17, rng, 6, 1500, 5000, (0.0005, 0.002), 1.5)
        return lp(x, 6000, 4)
    x = thump(0.25, 210 * p, 140 * p, 0.04)
    k = excite(click(rng, 1.5, 6000, 100), modes(0.2, mf, [0.03, 0.02, 0.015, 0.01, 0.006],
                                                   [1, 0.6, 0.4, 0.2, 0.1], rng))
    x += 0.6 * pad_to(k, len(x))
    x += 0.35 * pad_to(creak(0.15, rng, [(0, 80), (0.15, 50)], mf, 0.015, 0.3,
                             [(0, 0), (0.02, 1), (0.15, 0)]), len(x))
    return x


def _glow_hum(d, rng):
    t = tt(d)
    h = np.sin(TWO_PI * 220 * t) + 0.4 * np.sin(TWO_PI * 440.7 * t) + 0.15 * np.sin(TWO_PI * 661 * t)
    return h * env_swell(d, 0.08, d * 0.6) * (1 + 0.2 * np.sin(TWO_PI * 6 * t))


def blk_glowbulb(kind, i, rng):
    p = _pv(i, 0.08)
    if kind == "break":
        x = squelch(0.45, rng, [(0, 250 * p), (0.12, 950 * p), (0.45, 380 * p)], 0.45,
                    [(0, 0), (0.02, 1), (0.2, 0.6), (0.45, 0)])
        for k in range(3):
            add_at(x, bubble(0.07, 300 * p * rng.uniform(0.8, 1.3), 900 * p, 0.02, rng), 0.03 + 0.12 * k, 0.5)
        x += 0.07 * _glow_hum(0.45, rng) * np.std(x) / 0.3
        return x
    if kind == "step":
        x = squelch(0.2, rng, [(0, 300 * p), (0.08, 700 * p), (0.2, 350 * p)], 0.5,
                    [(0, 0), (0.02, 1), (0.2, 0)])
        add_at(x, bubble(0.05, 350 * p, 800 * p, 0.015, rng), 0.05, 0.35)
        return x
    x = squelch(0.3, rng, [(0, 220 * p), (0.1, 650 * p), (0.3, 300 * p)], 0.45,
                [(0, 0), (0.015, 1), (0.3, 0)])
    add_at(x, bubble(0.08, 200 * p, 520 * p, 0.03, rng), 0.02, 0.7)
    x += 0.07 * _glow_hum(0.3, rng) * np.std(x) / 0.3
    return x


def blk_driftstone(kind, i, rng):
    p = _pv(i)
    if kind == "break":
        x = lp(crumble(0.42, rng, 60, 1800 * p, 8000, (0.0005, 0.003), 2.0), 7000, 4)
        x += 0.6 * stone_hits(0.42, rng, 8, 800 * p, 2500 * p, (0.003, 0.01), span=0.3)
        x += 0.25 * thump(0.42, 230 * p, 150 * p, 0.02)
        return hp(x, 250)
    if kind == "step":
        x = lp(crumble(0.18, rng, 13, 2000 * p, 8000, (0.0005, 0.0025), 1.5), 6500, 4)
        x += 0.4 * stone_hits(0.18, rng, 2, 900 * p, 2000 * p, (0.003, 0.008), span=0.03)
        return hp(x, 300)
    x = stone_hits(0.3, rng, 5, 700 * p, 2200 * p, (0.004, 0.012), span=0.03)
    x += 0.8 * lp(crumble(0.3, rng, 22, 1800, 7000, (0.0005, 0.003), 3), 6500, 4)
    x += 0.3 * thump(0.3, 250 * p, 160 * p, 0.025)
    return hp(x, 200)


def blk_null_obsidian(kind, i, rng):
    p = _pv(i, 0.04)
    if kind == "break":
        x = thump(0.65, 72 * p, 38 * p, 0.18, 0.05)
        x += 0.4 * thump(0.65, 150 * p, 90 * p, 0.05)
        x += 0.35 * lp(crumble(0.65, rng, 28, 150, 1200, (0.002, 0.01), 2.5), 1300)
        x += 0.4 * stone_hits(0.65, rng, 8, 200 * p, 600 * p, (0.01, 0.03), span=0.3)
        return lp(x, 2800)
    if kind == "step":
        x = thump(0.28, 82 * p, 48 * p, 0.07, 0.03)
        x += 0.3 * stone_hits(0.28, rng, 2, 250 * p, 600 * p, (0.008, 0.02), span=0.02)
        return lp(x, 1800)
    x = thump(0.5, 78 * p, 40 * p, 0.13, 0.04)
    k = excite(click(rng, 2.0, 3000, 60), modes(0.3, [180 * p, 310 * p, 520 * p], [0.04, 0.03, 0.02],
                                                  [1, 0.6, 0.3], rng))
    x += 0.5 * pad_to(k, len(x))
    return lp(x, 2500)


def _crack(rng, d=0.1, n=25, lo=800, hi=5000, amp=1.0):
    x = crumble(d, rng, n, lo, hi, (0.0003, 0.0015), 1.5)
    c = click(rng, 0.4, 12000, 400) * 1.5
    x[: len(c)] += c
    return x * amp


def blk_fracture_glass(kind, i, rng):
    p = _pv(i, 0.06)
    if kind == "break":
        x = glass_pings(1.1, rng, 18, 600 * p, 2200 * p, (0.08, 0.35), span=0.35, shape=1.8,
                        ratios=(1.0, 2.756, 5.404, 8.933))
        add_at(x, _crack(rng, 0.12, 30), 0.0, 1.2)
        x += 0.5 * pad_to(thump(0.3, 125 * p, 70 * p, 0.06), len(x))
        return lp(x, 4500, 4)
    if kind == "step":
        x = glass_pings(0.3, rng, 1, 900 * p, 1800 * p, (0.04, 0.08), span=0.005,
                        ratios=(1.0, 2.756, 5.404, 8.933))
        add_at(x, click(rng, 0.6, 7000, 800), 0.0, 0.35)
        return lp(x, 4500, 4)
    x = glass_pings(0.9, rng, 2, 700 * p, 1500 * p, (0.2, 0.4), span=0.02,
                    ratios=(1.0, 2.756, 5.404, 8.933))
    add_at(x, _crack(rng, 0.06, 10), 0.0, 0.8)
    x += 0.4 * pad_to(thump(0.25, 140 * p, 90 * p, 0.04), len(x))
    return lp(x, 4500, 4)


def blk_spire_lantern(kind, i, rng):
    p = _pv(i, 0.05)
    f = 980 * p
    if kind == "break":
        x = np.zeros(ns(2.2))
        add_at(x, stone_hits(0.4, rng, 10, 2500, 6000, (0.01, 0.04), span=0.3), 0.0, 0.7)
        add_at(x, bell(f, 0.7, rng, 0.5), 0.0, 0.9)
        add_at(x, bell(f * 1.33, 0.5, rng, 0.6), 0.09, 0.4)
        add_at(x, thump(0.2, 300, 220, 0.03), 0.0, 0.3)
        return x
    if kind == "step":
        x = bell(f * 1.5, 0.25, rng, 0.85) * 0.5
        add_at(x, stone_hits(0.2, rng, 2, 3000, 6000, (0.008, 0.02), span=0.04), 0.0, 0.35)
        return x
    x = bell(f, 0.9, rng, 0.6)
    add_at(x, stone_hits(0.25, rng, 2, 1500, 3000, (0.02, 0.05), span=0.02), 0.0, 0.6)
    return x


BLOCK_FUNCS = {
    "pale_loam": blk_pale_loam, "stillstone": blk_stillstone, "mirror_shard": blk_mirror_shard,
    "lumen_crystal": blk_lumen_crystal, "voidroot": blk_voidroot, "glowbulb": blk_glowbulb,
    "driftstone": blk_driftstone, "null_obsidian": blk_null_obsidian,
    "fracture_glass": blk_fracture_glass, "spire_lantern": blk_spire_lantern,
}

# footsteps: ringing materials sit lower, dry granular ones a little higher, so
# all step sounds land at a similar perceived loudness
STEP_LEVEL = {"lumen_crystal": -11.0, "spire_lantern": -10.0, "mirror_shard": -9.0,
              "fracture_glass": -9.0, "stillstone": -4.0, "driftstone": -4.0}

for _t in spec.BLOCK_TYPES:
    _fn = BLOCK_FUNCS[_t]
    for _kind, _cnt, _lvl in (("break", 2, -3.0), ("step", 3, STEP_LEVEL.get(_t, -6.0)), ("place", 2, -3.5)):
        def _mk(fn=_fn, kind=_kind):
            return lambda rng, i: fn(kind, i, rng)
        reg([f"block/{_t}/{_kind}{k + 1}" for k in range(_cnt)], "sfx", _lvl)(_mk())


# =============================================================================
# Event sounds: fluids
# =============================================================================

@reg(["fluid/stillwater_swim1", "fluid/stillwater_swim2"], level=-5.0)
def sfx_swim(rng, i):
    d = 1.1 + 0.15 * i
    f0 = 160 if i == 0 else 190
    x = whoosh(d, rng, [(0, f0), (d * 0.45, 520), (d, 180)], [(0, 0), (d * 0.35, 1), (d, 0)], 0.7, 40)
    x = lp(x, 900)
    sw = 1 + 0.4 * np.sin(TWO_PI * (1.6 + 0.3 * i) * tt(d))
    x *= sw
    for k in range(4):
        add_at(x, bubble(0.12, rng.uniform(110, 160), rng.uniform(240, 320), 0.04, rng),
               rng.uniform(0.15, d - 0.3), 0.35 * np.std(x) / 0.1)
    return lp(x, 1100)


def _glug(rng, f0, f1, d=0.11, body=300.0):
    x = bubble(d, f0, f1, d * 0.35, rng)
    nz = reson(white(d, rng), body, 8) * env_ad(d, 0.004, d * 0.3) * 0.5
    return x + nz


@reg("fluid/bucket_fill_stillwater", level=-3.0)
def sfx_bucket_fill(rng, i):
    d = 0.9
    x = np.zeros(ns(d))
    times = [0.0, 0.17, 0.33, 0.52]
    for k, tm in enumerate(times):
        add_at(x, _glug(rng, 170 + 40 * k, 330 + 50 * k, 0.13, 320 + 60 * k), tm, 1.0 - 0.15 * k)
    bed = lp(reson(white(d, rng), 380, 3), 900) * env_swell(d, 0.05, 0.3) * 0.15
    return lp(x + bed, 2200)


@reg("fluid/bucket_empty_stillwater", level=-3.0)
def sfx_bucket_empty(rng, i):
    d = 1.0
    pour = whoosh(d, rng, [(0, 500), (0.5, 380), (d, 260)], [(0, 0), (0.08, 1), (0.7, 0.7), (d, 0)], 0.5)
    pour = lp(reson(pour, 330, 2.5) + 0.4 * pour, 1500)
    x = pour * 0.8
    for k, tm in enumerate([0.08, 0.3, 0.55, 0.75]):
        add_at(x, _glug(rng, 330 - 40 * k, 180 - 20 * k, 0.12, 300 - 30 * k), tm, 0.8)
    return lp(x, 2000)


@reg("fluid/xp_drain", level=-5.0)
def sfx_xp_drain(rng, i):
    d = 0.9
    x = np.zeros(ns(d))
    notes = np.geomspace(3600, 1150, 10)
    for k, f in enumerate(notes):
        add_at(x, glass_chime(f, 1.0 - 0.07 * k, 0.12, rng, 0.05), 0.055 * k)
    sh = whoosh(0.6, rng, [(0, 6000), (0.6, 2500)], [(0, 0.6), (0.6, 0)], 0.4)
    add_at(x, sh, 0.0, 0.15)
    return x


# =============================================================================
# Void skate (whale-like flyer)
# =============================================================================

@reg(["mob/void_skate/call1", "mob/void_skate/call2", "mob/void_skate/call3"], level=-3.0)
def sfx_skate_call(rng, i):
    shapes = [
        (1.8, [(0, 170), (0.5, 260), (1.3, 240), (1.8, 185)]),
        (2.3, [(0, 210), (0.4, 195), (1.2, 290), (1.9, 270), (2.3, 200)]),
        (1.6, [(0, 150), (0.8, 230), (1.6, 140)]),
    ]
    d, pts = shapes[i]
    x = whale(d, pts, rng, vib=3.6 + 0.4 * i, vib_depth=0.012)
    return reverb(x, 0.28, 2.4, 3500, 1.2, seed=3)


@reg(["mob/void_skate/flap1", "mob/void_skate/flap2"], level=-4.0)
def sfx_skate_flap(rng, i):
    d = 0.45
    x = whoosh(d, rng, [(0, 150), (0.12, 480 + 60 * i), (d, 140)], [(0, 0), (0.1, 1), (d, 0)], 0.9, 30)
    x = lp(x, 900)
    x += 0.6 * np.std(x) / 0.2 * thump(d, 75 + 8 * i, 50, 0.08, 0.04) * 0.25
    return x


@reg("mob/void_skate/chime", level=-3.0)
def sfx_skate_chime(rng, i):
    notes = ["A5", "C#6", "E6", "A6", "E7"]
    x = np.zeros(ns(3.0))
    for k, n_ in enumerate(notes):
        add_at(x, glass_chime(nf(n_), 0.9 - 0.08 * k, 1.1, rng, 0.12), 0.075 * k)
    return reverb(x, 0.3, 2.0, 7000, 0.8, seed=4)


@reg(["mob/void_skate/hurt1", "mob/void_skate/hurt2"], level=-3.0)
def sfx_skate_hurt(rng, i):
    d = 0.42
    pts = [(0, 420 + 40 * i), (0.08, 640 + 50 * i), (d, 360)]
    x = whale(d, pts, rng, vib=9, vib_depth=0.02, vowel="o", vowel2="a", fscale=0.75, tilt_exp=1.2, sub=0.15)
    return reverb(x, 0.15, 1.2, 4000, 0.8, seed=3)


@reg("mob/void_skate/death", level=-3.0)
def sfx_skate_death(rng, i):
    d = 3.2
    x = whale(d, [(0, 320), (0.4, 340), (1.6, 190), (3.2, 85)], rng, vib=4.5, vib_depth=0.02,
              vowel="o", vowel2="u", fscale=0.6)
    x *= curve(d, [(0, 1), (2.0, 0.8), (3.2, 0.0)])
    return reverb(x, 0.35, 3.0, 3000, 1.3, seed=3)


# =============================================================================
# Chorus grazer
# =============================================================================

@reg(["mob/chorus_grazer/hum1", "mob/chorus_grazer/hum2", "mob/chorus_grazer/hum3"], level=-6.0)
def sfx_grazer_hum(rng, i):
    d = [1.3, 1.6, 1.1][i]
    f = [138.0, 155.0, 123.0][i]
    t = tt(d)
    breath = smooth_noise(d, 6, rng)
    amp = env_swell(d, 0.25, 0.45) * np.clip(1 + 0.15 * breath, 0.5, 1.5)
    ff = f * (1 + 0.004 * smooth_noise(d, 4, rng)) * (1 + 0.01 * np.clip(t / 0.2, 0, 1))
    tone = np.sin(phase_of(ff)) + 0.12 * np.sin(2 * phase_of(ff) + 0.4) + 0.05 * np.sin(3 * phase_of(ff))
    nz = white(d, rng)
    res = reson(nz, f, 45)
    air = bp(nz, 300, 2500)
    air = lp(air, 1500)
    x = (tone + 0.3 * res / (np.std(res) + 1e-9) + 0.035 * air / (np.std(air) + 1e-9) * (1 + 0.5 * breath)) * amp
    return reverb(lp(x, 1600, 4), 0.18, 1.2, 3000, 0.7, seed=5)


@reg("mob/chorus_grazer/eat", level=-5.0)
def sfx_grazer_eat(rng, i):
    x = np.zeros(ns(1.2))
    for k in range(3):
        m = lp(crumble(0.09, rng, 10, 300, 1600, (0.002, 0.01), 1.5), 2000)
        m += 0.4 * thump(0.09, 180, 120, 0.02)
        add_at(x, m, 0.17 * k, 1.0 - 0.1 * k)
    add_at(x, glass_chime(2640, 0.25, 0.4, rng, 0.05), 0.55)
    return x


def _bleat(d, rng, pts, trill=10.0, depth=0.45):
    t = tt(d)
    f = curve(d, pts, log=True) * (1 + 0.025 * np.sin(TWO_PI * trill * t))
    x = voice(d, f, rng, "e", "a", 1.3, 0.35, 1.25, 1.15, 0.006)
    am = 1 - depth * (0.5 + 0.5 * np.sin(TWO_PI * trill * t))
    return x * am * env_swell(d, 0.03, d * 0.4)


@reg(["mob/chorus_grazer/hurt1", "mob/chorus_grazer/hurt2"], level=-3.0)
def sfx_grazer_hurt(rng, i):
    d = 0.38
    x = _bleat(d, rng, [(0, 400 + 30 * i), (0.1, 450), (d, 360)], 10 + i)
    return reverb(x, 0.12, 0.9, 5000, 0.6, seed=5)


@reg("mob/chorus_grazer/death", level=-3.0)
def sfx_grazer_death(rng, i):
    d = 1.3
    x = _bleat(d, rng, [(0, 430), (0.2, 440), (d, 170)], 9, 0.5)
    x *= curve(d, [(0, 1), (0.8, 0.7), (d, 0)])
    return reverb(x, 0.2, 1.5, 4500, 0.8, seed=5)


# =============================================================================
# Underside lurker
# =============================================================================

def _chitin_click(rng, f_lo=2200, f_hi=4500, tau=0.003, amp=1.0):
    f = rng.uniform(f_lo, f_hi)
    ir = modes(tau * 8, [f, f * 1.47, f * 2.2], [tau, tau * 0.7, tau * 0.5], [1, 0.6, 0.3], rng)
    x = excite(click(rng, 0.3, 12000, 1000), ir)
    return x * amp


@reg(["mob/lurker/click1", "mob/lurker/click2", "mob/lurker/click3"], level=-12.0)
def sfx_lurker_click(rng, i):
    n_ = [2, 3, 4][i]
    x = np.zeros(ns(0.35))
    tm = 0.0
    for k in range(n_):
        add_at(x, _chitin_click(rng, 1800, 4200, rng.uniform(0.002, 0.004), rng.uniform(0.6, 1)), tm)
        tm += rng.uniform(0.03, 0.08)
    return hp(x, 900)


@reg("mob/lurker/eyes_open", level=-1.5)
def sfx_lurker_eyes(rng, i):
    d = 1.4
    x = np.zeros(ns(d + 0.2))
    rate = lambda tm: 14 * (60 / 14) ** (tm / d)
    tm = 0.0
    while tm < d:
        frac = tm / d
        fl = 1300 + 2600 * frac
        add_at(x, _chitin_click(rng, fl, fl * 1.5, 0.003 + 0.002 * (1 - frac), 0.25 + 0.75 * frac ** 1.3), tm)
        tm += (1.0 / rate(tm)) * rng.uniform(0.7, 1.3)
    rise = whoosh(d, rng, [(0, 1500), (d, 4500)], [(0, 0), (d * 0.8, 0.5), (d, 0.8)], 0.5)
    add_at(x, rise, 0.0, 0.25 * np.max(np.abs(x)) / (np.max(np.abs(rise)) + 1e-9))
    x = hp(x, 600)
    return reverb(x, 0.12, 0.8, 6000, 0.5, seed=6)


@reg("mob/lurker/snare", level=-2.0)
def sfx_lurker_snare(rng, i):
    x = np.zeros(ns(0.6))
    sw = whoosh(0.16, rng, [(0, 500), (0.16, 4500)], [(0, 0), (0.16, 1)], 0.6)
    add_at(x, sw / (np.max(np.abs(sw)) + 1e-9), 0.0, 0.3)
    c = crumble(0.06, rng, 30, 1500, 8000, (0.0003, 0.0015), 1.2)
    c = c / (np.max(np.abs(c)) + 1e-9)
    n_ = ns(0.0015)
    nwave = lp(np.concatenate([np.linspace(1, -1, n_), np.zeros(32)]), 6000)
    add_at(x, nwave / (np.max(np.abs(nwave)) + 1e-9), 0.16, 0.8)
    add_at(x, c, 0.16, 0.9)
    return reverb(x, 0.15, 0.9, 7000, 0.6, seed=6)


@reg("mob/lurker/bite", level=-2.0)
def sfx_lurker_bite(rng, i):
    x = np.zeros(ns(0.3))
    add_at(x, _chitin_click(rng, 1200, 2800, 0.006, 1.0), 0.0)
    add_at(x, _chitin_click(rng, 1400, 3200, 0.005, 0.9), 0.016)
    add_at(x, thump(0.12, 170, 110, 0.03), 0.0, 0.5)
    add_at(x, click(rng, 0.5, 10000, 500), 0.016, 0.7)
    return x


@reg("mob/lurker/retreat", level=-4.0)
def sfx_lurker_retreat(rng, i):
    d = 0.85
    x = np.zeros(ns(d + 0.1))
    tm = 0.0
    k = 0
    while tm < d:
        frac = tm / d
        fl = 1500 + 2500 * frac
        add_at(x, _chitin_click(rng, fl, fl * 1.4, 0.003, (1 - frac) ** 1.2 * rng.uniform(0.5, 1)), tm)
        tm += rng.uniform(0.012, 0.03) * (1 - 0.4 * frac)
        k += 1
    x = stft_shape(x, lambda F, T: np.where(F > 2000 + 6000 * (1 - T / d), 0.3, 1.0), 512)
    return hp(x, 700)


@reg("mob/lurker/hurt", level=-3.0)
def sfx_lurker_hurt(rng, i):
    d = 0.42
    t = tt(d)
    am = 0.5 + 0.5 * np.tanh(3 * np.sin(TWO_PI * 42 * t))
    nz = bp(white(d, rng), 2000, 5500) * am
    f = curve(d, [(0, 2200), (0.1, 2900), (d, 2000)], log=True) * (1 + 0.03 * np.sin(TWO_PI * 23 * t))
    sq = fm_tone(d, f, 1.5, 1.2) * am
    x = (0.6 * nz / (np.std(nz) + 1e-9) + 0.5 * sq) * env_swell(d, 0.01, 0.15)
    return lp(x, 7000)


@reg("mob/lurker/death", level=-3.0)
def sfx_lurker_death(rng, i):
    d = 1.6
    x = np.zeros(ns(d + 0.3))
    t1 = 0.9
    tt_ = tt(t1)
    rate = 45 * (1 - 0.7 * tt_ / t1)
    am = 0.5 + 0.5 * np.tanh(3 * np.sin(phase_of(rate)))
    f = curve(t1, [(0, 2600), (t1, 900)], log=True)
    ch = (bp(white(t1, rng), 1200, 4500) * 0.5 + fm_tone(t1, f, 1.5, 1.0)) * am * env_swell(t1, 0.01, 0.3)
    add_at(x, ch, 0.0, 0.8)
    for k in range(8):
        add_at(x, _chitin_click(rng, 1200, 3000, 0.005, rng.uniform(0.4, 0.9)), 0.85 + rng.uniform(0, 0.5))
    add_at(x, thump(0.3, 160, 90, 0.06), 1.0, 0.6)
    return reverb(x, 0.12, 1.0, 5000, 0.6, seed=6)


# =============================================================================
# Hoarder (warm, pitched-down enderman-like warble)
# =============================================================================

def _warble(d, rng, pts, warble=0.6, rate=7.0, vowels=("o", "a"), fscale=0.85, breath=0.08,
            drive=1.0, reverse_env=False):
    t = tt(d)
    base = curve(d, pts, log=True)
    wob = warble * (np.sin(TWO_PI * rate * t + 2 * smooth_noise(d, 2, rng)) + 0.6 * smooth_noise(d, 12, rng))
    f = base * 2 ** (wob / 12)
    x = voice(d, f, rng, vowels[0], vowels[1], 1.25, breath, fscale, fscale * 1.1, 0.008)
    if drive > 1:
        x = softclip(x / (np.max(np.abs(x)) + 1e-9), drive)
    if reverse_env:
        e = np.clip(t / d, 0, 1) ** 1.6 * env_swell(d, 0.0, 0.05)
    else:
        e = env_swell(d, 0.04, d * 0.35)
    return lp(x * e, 3000)


@reg(["mob/hoarder/ambient1", "mob/hoarder/ambient2"], level=-4.0)
def sfx_hoarder_ambient(rng, i):
    if i == 0:
        segs = [(0.0, 0.45, [(0, 130), (0.45, 165)], ("o", "a"), False),
                (0.5, 0.35, [(0, 150), (0.35, 115)], ("a", "u"), True),
                (0.9, 0.55, [(0, 120), (0.3, 140), (0.55, 100)], ("u", "o"), False)]
    else:
        segs = [(0.0, 0.6, [(0, 110), (0.6, 150)], ("u", "a"), True),
                (0.65, 0.5, [(0, 160), (0.5, 120)], ("a", "o"), False)]
    x = np.zeros(ns(1.6))
    for t0, d, pts, vw, rev in segs:
        add_at(x, _warble(d, rng, pts, 0.7, 6.5 + rng.uniform(0, 2), vw, reverse_env=rev), t0)
    return reverb(x, 0.25, 1.6, 3000, 0.9, seed=8)


def _shimmer(d, rng, lo=3000, hi=9000, rate=14.0):
    t = tt(d)
    flick = 0.5 + 0.5 * np.sin(TWO_PI * rate * t + 3 * smooth_noise(d, 3, rng))
    s = bp(white(d, rng), lo, hi) * flick
    return s


@reg("mob/hoarder/accept", level=-3.0)
def sfx_hoarder_accept(rng, i):
    x = np.zeros(ns(1.8))
    add_at(x, _warble(0.7, rng, [(0, 120), (0.5, 190), (0.7, 175)], 0.8, 8, ("o", "a")), 0.0)
    sh = _shimmer(1.2, rng, 2500, 8000, 11) * env_swell(1.2, 0.3, 0.7) * 0.25
    for k, f in enumerate([1320, 1760, 2217, 2640]):
        add_at(sh, glass_chime(f, 0.3, 0.6, rng, 0.05), 0.12 * k + 0.1, 1.0)
    add_at(x, sh, 0.35, 0.8)
    return reverb(x, 0.3, 1.8, 6000, 0.9, seed=8)


@reg("mob/hoarder/refuse", level=-3.0)
def sfx_hoarder_refuse(rng, i):
    x = np.zeros(ns(0.9))
    add_at(x, _warble(0.5, rng, [(0, 135), (0.5, 85)], 0.5, 9, ("a", "u"), 0.8), 0.0)
    hmph = lp(white(0.15, rng), 900) * env_ad(0.15, 0.005, 0.04) * 0.15
    add_at(x, hmph, 0.48)
    return reverb(x, 0.18, 1.2, 3000, 0.8, seed=8)


@reg("mob/hoarder/anger", level=-2.0)
def sfx_hoarder_anger(rng, i):
    d = 1.3
    x = _warble(d, rng, [(0, 220), (0.25, 420), (0.9, 380), (d, 260)], 1.4, 13, ("a", "e"), 1.0,
                breath=0.2, drive=3.0)
    x = lp(x, 5000) + 0.1 * bp(white(d, rng), 1500, 5000) * env_swell(d, 0.05, 0.5)
    return reverb(x, 0.22, 1.5, 5000, 0.9, seed=8)


@reg("mob/hoarder/hurt", level=-3.0)
def sfx_hoarder_hurt(rng, i):
    x = _warble(0.5, rng, [(0, 200), (0.1, 260), (0.5, 140)], 1.0, 12, ("a", "o"), 0.9, drive=1.5)
    return reverb(x, 0.15, 1.0, 4000, 0.8, seed=8)


@reg("mob/hoarder/death", level=-3.0)
def sfx_hoarder_death(rng, i):
    d = 2.4
    x = np.zeros(ns(d))
    w = _warble(1.5, rng, [(0, 210), (0.5, 160), (1.5, 70)], 1.2, 9, ("a", "u"), 0.9)
    add_at(x, w, 0.0)
    t = tt(d)
    st = white(d, rng)
    crack = (rng.random(ns(d)) < 0.004) * rng.standard_normal(ns(d)) * 6
    st = bp(st + crack, 1500, 9000) * curve(d, [(0, 0), (0.7, 0.0), (1.2, 0.35), (d, 0)])
    st *= 0.6 + 0.4 * (rng.random(ns(d)) < 0.6)
    x += st * 0.6 * np.std(w) / (np.std(st) + 1e-9)
    return reverb(x, 0.2, 1.6, 4000, 0.9, seed=8)


# =============================================================================
# Spire warden
# =============================================================================

def _grind(d, rng, f_lo=120, f_hi=900, rate=28):
    nz = white(d, rng)
    t = tt(d)
    slip = np.clip(smooth_noise(d, rate, rng), 0, None) ** 1.5
    body = 0
    for f in np.geomspace(f_lo, f_hi, 5):
        body = body + reson(nz, f * rng.uniform(0.9, 1.1), 6)
    body = body / (np.std(body) + 1e-9)
    grit = crumble(d, rng, int(d * 60), 600, 4000, (0.001, 0.006), 1.0, decay_amp=False)
    x = body * (0.3 + slip) + 0.6 * grit / (np.std(grit) + 1e-9) * (0.2 + slip)
    rum = lp(colored(d, rng, -6), 90) * 0.8
    return lp(x + rum, 3000)


@reg(["mob/spire_warden/grind1", "mob/spire_warden/grind2"], level=-4.0)
def sfx_warden_grind(rng, i):
    d = 1.6 + 0.3 * i
    x = _grind(d, rng, 110 + 20 * i, 850, 22 + 6 * i) * env_swell(d, 0.25, 0.5)
    return reverb(x, 0.2, 1.8, 2500, 1.2, seed=9)


def _heavy_step(rng, scale=1.0):
    x = np.zeros(ns(0.8))
    add_at(x, thump(0.6, 62 * scale, 36 * scale, 0.14, 0.04), 0.0, 1.0)
    add_at(x, stone_hits(0.4, rng, 9, 180, 800, (0.01, 0.03), span=0.12), 0.0, 0.7)
    add_at(x, lp(crumble(0.5, rng, 35, 200, 2200, (0.002, 0.01), 2.5), 2500), 0.01, 0.4)
    return lp(x, 3500)


@reg(["mob/spire_warden/step1", "mob/spire_warden/step2"], level=-2.0)
def sfx_warden_step(rng, i):
    x = _heavy_step(rng, 1.0 - 0.06 * i)
    return reverb(x, 0.3, 2.5, 2000, 1.4, seed=9)


@reg("mob/spire_warden/slam", level=-1.5)
def sfx_warden_slam(rng, i):
    d = 2.2
    x = np.zeros(ns(d))
    add_at(x, thump(1.6, 75, 27, 0.45, 0.06), 0.0, 1.2)
    add_at(x, _crack(rng, 0.15, 40, 300, 6000), 0.0, 0.9)
    add_at(x, stone_hits(1.6, rng, 40, 150, 1500, (0.01, 0.04), span=1.4, shape=2.2), 0.0, 0.8)
    add_at(x, lp(crumble(1.6, rng, 120, 150, 3000, (0.002, 0.012), 2.0), 3000), 0.0, 0.7)
    x = lp(x, 6000)
    return reverb(x, 0.35, 3.5, 2200, 1.6, seed=9)


@reg("mob/spire_warden/teleport_rise", level=-1.5)
def sfx_warden_teleport(rng, i):
    d = 1.0
    t = tt(d)
    f = 140 * (8.0 ** ((t / d) ** 1.6))     # 140 -> 1120 Hz, accelerating
    x = np.zeros(ns(d))
    for det in (-0.012, 0.0, 0.009):
        ff = f * (1 + det)
        x += harmonic(ff, [1.0, 0.5, 0.35, 0.2, 0.12, 0.07], rng)
    x = lp(x, 3500)
    trem = 1 - 0.35 * (0.5 + 0.5 * np.sin(phase_of(6 + 30 * (t / d) ** 2)))
    nz = whoosh(d, rng, [(0, 800), (d, 6000)], [(0, 0), (d, 1)], 0.6)
    y = (x / (np.std(x) + 1e-9) * trem + 0.6 * nz / (np.std(nz) + 1e-9)) * curve(d, [(0, 0.05), (0.8, 0.7), (0.97, 1.0), (d, 0)])
    return reverb(y, 0.2, 1.4, 6000, 0.8, seed=10)


@reg("mob/spire_warden/plate_shield", level=-3.0)
def sfx_warden_shield(rng, i):
    d = 1.6
    t = tt(d)
    idx = 2.2 * np.exp(-t / 0.4) + 0.6
    x = fm_tone(d, 880, 1.5, idx) + 0.7 * fm_tone(d, 1323, 1.5, idx * 0.7) + 0.5 * fm_tone(d, 662, 2.0, idx * 0.5)
    x *= (1 + 0.25 * np.sin(TWO_PI * 7 * t)) * env_swell(d, 0.06, 0.9)
    sh = _shimmer(d, rng, 3000, 8000, 9) * env_swell(d, 0.05, 1.0) * 0.08
    return reverb(lp(x + sh, 7000), 0.25, 1.6, 7000, 0.9, seed=10)


def _groan(d, rng, pts, drive=1.5):
    x = voice(d, curve(d, pts, log=True), rng, "o", "u", 0.9, 0.1, 0.7, 0.6, 0.01, nmax=90)
    x = softclip(x / (np.max(np.abs(x)) + 1e-9), drive)
    return lp(x, 1400) * env_swell(d, 0.08, d * 0.5)


@reg("mob/spire_warden/hurt", level=-2.0)
def sfx_warden_hurt(rng, i):
    x = np.zeros(ns(1.3))
    add_at(x, _crack(rng, 0.12, 30, 400, 5000), 0.0, 1.0)
    add_at(x, stone_hits(0.3, rng, 6, 300, 1200, (0.008, 0.02), span=0.1), 0.0, 0.6)
    add_at(x, _groan(1.0, rng, [(0, 62), (0.3, 66), (1.0, 46)]), 0.05, 0.9)
    return reverb(x, 0.25, 2.2, 2500, 1.3, seed=9)


@reg("mob/spire_warden/death", level=-2.0)
def sfx_warden_death(rng, i):
    d = 5.5
    x = np.zeros(ns(d))
    add_at(x, _groan(2.2, rng, [(0, 66), (0.6, 60), (2.2, 34)], 2.0), 0.0, 0.8)
    for k, tm in enumerate([0.9, 1.6, 2.1, 2.9, 3.4]):
        hit = np.zeros(ns(1.5))
        add_at(hit, thump(1.2, 70 - 4 * k, 30, 0.3, 0.05), 0.0, 0.8)
        add_at(hit, stone_hits(1.2, rng, 18, 150, 1300, (0.01, 0.035), span=0.8), 0.0, 0.6)
        add_at(hit, _crack(rng, 0.08, 15, 300, 4000), 0.0, 0.4)
        add_at(x, hit, tm + rng.uniform(-0.05, 0.05), 0.85 - 0.08 * k)
    g = _grind(3.0, rng, 100, 700, 18) * env_swell(3.0, 0.4, 1.5)
    add_at(x, g, 0.6, 0.3)
    rum = lp(colored(d, rng, -6), 120) * curve(d, [(0, 0), (1.0, 0.5), (3.5, 0.6), (d, 0)])
    x += rum * 0.4
    return reverb(lp(x, 5000), 0.35, 3.5, 2000, 1.6, seed=9)


def _breath(d, rng, pts_amp, vowel="a", fscale=0.6, f_lo=60):
    x = noise_shaped(d, rng, lambda F, T: formant_gain(F, vowel, fscale, 0.06, 1.6) * (F > f_lo), 2048)
    x = x / (np.std(x) + 1e-9) * curve(d, pts_amp)
    return x


@reg("mob/spire_warden/breathe", level=-5.0)
def sfx_warden_breathe(rng, i):
    d = 4.0
    inh = _breath(1.5, rng, [(0, 0), (1.1, 0.8), (1.5, 0)], "o", 0.5)
    exh = _breath(2.2, rng, [(0, 0), (0.25, 1.0), (2.2, 0)], "a", 0.45)
    x = np.zeros(ns(d))
    add_at(x, inh, 0.0, 0.6)
    add_at(x, exh, 1.7, 1.0)
    chest = reson(white(d, rng), 95, 6) * 0.3
    x += chest * np.abs(x) / (np.max(np.abs(x)) + 1e-9) * 3
    return reverb(lp(x, 2500), 0.2, 1.8, 2500, 1.2, seed=9)


# =============================================================================
# Echo (glitch creature)
# =============================================================================

def _bitcrush(x, bits=5, hold=3):
    q = 2.0 ** (bits - 1)
    y = np.round(x * q) / q
    if hold > 1:
        y = np.repeat(y[::hold], hold)[: len(x)]
    return y


@reg("mob/echo/hum", level=-12.0)
def sfx_echo_hum(rng, i):
    d = 2.0
    t = tt(d)
    hum = sum((1.0 / k) * np.sin(TWO_PI * 60 * k * t) for k in (1, 3, 5, 7))
    hum = lp(hum, 600) * (1 + 0.2 * smooth_noise(d, 4, rng))
    hiss = bp(white(d, rng), 2000, 7000) * 0.15
    pops = (rng.random(ns(d)) < 0.0015) * rng.standard_normal(ns(d)) * 2
    pops = hp(pops, 1500)
    return (0.6 * hum + hiss + pops) * env_swell(d, 0.4, 0.6)


def _stutter(rng, src, n_slices, slice_len, pitch_up=True):
    out = []
    L = ns(slice_len)
    for k in range(n_slices):
        st = int(rng.integers(0, max(1, len(src) - L * 2)))
        seg = src[st: st + L * 2]
        speed = 1.0 + (0.12 * k if pitch_up else -0.05 * k)
        seg = resample_speed(seg, max(speed, 0.4))[:L]
        out.append(fade(pad_to(seg, L), 0.001, 0.003))
    return np.concatenate(out)


@reg("mob/echo/unfold", level=-3.0)
def sfx_echo_unfold(rng, i):
    d = 0.6
    src = fm_tone(d, 330, 1.41, 2.0) + 0.6 * fm_tone(d, 495, 2.01, 1.5)
    st = _stutter(rng, src, 8, 0.035)
    st = _bitcrush(st * 0.7, 6, 2)
    x = np.zeros(ns(0.8))
    add_at(x, st * curve(len(st) / SR, [(0, 0.4), (len(st) / SR, 1.0)])[: len(st)], 0.0)
    add_at(x, _crack(rng, 0.05, 15, 1000, 8000), len(st) / SR, 1.2)
    add_at(x, thump(0.12, 220, 120, 0.03), len(st) / SR, 0.6)
    return lp(x, 9000)


@reg("mob/echo/scramble", level=-7.0)
def sfx_echo_scramble(rng, i):
    x = np.zeros(ns(0.6))
    scale = [nm(n_) for n_ in ("C5", "D#5", "F5", "G5", "A#5", "C6", "D#6")]
    for k in range(12):
        f = mtof(scale[rng.integers(len(scale))])
        dd = 0.028
        b = harmonic(np.full(ns(dd), f), [1.0, 0.0, 0.3, 0.0, 0.12])
        b = _bitcrush(b * 0.8, 5, 2) * env_swell(dd, 0.002, 0.008)
        add_at(x, b, 0.04 * k, rng.uniform(0.5, 1.0))
        add_at(x, click(rng, 0.3, 12000, 3000), 0.04 * k + 0.03, 0.25)
    return lp(x, 8000)


@reg("mob/echo/hurt", level=-8.0)
def sfx_echo_hurt(rng, i):
    d = 0.42
    steps = rng.uniform(1600, 3200, 7)
    f = np.repeat(steps, ns(d) // 7 + 1)[: ns(d)]
    x = fm_tone(d, f, 1.5, 1.4) * env_swell(d, 0.005, 0.12)
    x = _bitcrush(x * 0.8, 4, 3)
    return lp(x, 6500)


@reg("mob/echo/death", level=-3.0)
def sfx_echo_death(rng, i):
    d = 2.2
    t = tt(d)
    dens = curve(d, [(0, 0.05), (d, 0.001)])
    grains = (rng.random(ns(d)) < dens) * rng.standard_normal(ns(d))
    st = bp(grains, 800, 8000) * 4 + bp(white(d, rng), 2000, 8000) * curve(d, [(0, 0.4), (d, 0)])
    tone = fm_tone(d, curve(d, [(0, 440), (d, 180)], True), 1.41, 1.5) * np.exp(-t / 0.6)
    bits = 8
    tone_c = np.concatenate([_bitcrush(seg, max(2, bits - k), 1 + k) for k, seg in
                             enumerate(np.array_split(tone, 6))])
    x = 0.5 * tone_c + st * curve(d, [(0, 1), (d, 0)]) * 0.5
    return lp(x, 8000)


# =============================================================================
# Wild shulker, tidewisp, shardling
# =============================================================================

@reg("mob/wild_shulker/molt", level=-3.0)
def sfx_shulker_molt(rng, i):
    x = np.zeros(ns(0.8))
    for k in range(6):
        f = rng.uniform(1000, 2500)
        ir = modes(0.15, [f, f * 1.73, f * 2.61], [0.025, 0.015, 0.01], [1, 0.5, 0.25], rng)
        add_at(x, excite(click(rng, 1.0, 9000, 300), ir), rng.uniform(0, 0.35), rng.uniform(0.4, 1.0))
    pop = bubble(0.08, 420, 150, 0.02, rng) + lp(white(0.08, rng), 800) * env_ad(0.08, 0.002, 0.015) * 0.5
    add_at(x, pop, 0.45, 1.2)
    return x


@reg(["mob/tidewisp/pulse1", "mob/tidewisp/pulse2"], level=-6.0)
def sfx_tidewisp(rng, i):
    d = 0.7
    f0 = [1660.0, 1865.0][i]
    t = tt(d)
    f = f0 * (1 - 0.02 * np.exp(-t / 0.08))
    x = np.sin(phase_of(f)) + 0.15 * np.sin(phase_of(f * 2.756)) + 0.4 * np.sin(phase_of(f * 1.0021))
    x *= env_ad(d, 0.06, 0.15)
    return reverb(x, 0.25, 1.2, 8000, 0.6, seed=11)


@reg("mob/shardling/hum", level=-5.0)
def sfx_shardling_hum(rng, i):
    d = 1.8
    x = np.zeros(ns(d))
    for f, a in ((1180, 1.0), (1252, 0.7), (1771, 0.4), (2362, 0.15)):
        x += a * glass_harm(f, d, 0.35, 0.7, rng, 0.06)
    return reverb(x, 0.2, 1.2, 8000, 0.6, seed=11)


@reg("mob/shardling/dive", level=-3.0)
def sfx_shardling_dive(rng, i):
    d = 0.75
    w = whoosh(d, rng, [(0, 3200), (0.4, 1600), (d, 700)], [(0, 0), (0.35, 1), (d, 0)], 0.6)
    t = tt(d)
    f = curve(d, [(0, 2600), (d, 1300)], True)
    sh = (np.sin(phase_of(f)) + 0.5 * np.sin(phase_of(f * 1.5))) * curve(d, [(0, 0), (0.3, 1), (d, 0)])
    x = w / (np.std(w) + 1e-9) + 0.4 * sh * (1 + 0.5 * np.sin(TWO_PI * 18 * t))
    return reverb(x, 0.12, 0.8, 7000, 0.6, seed=11)


@reg("mob/shardling/burst", level=-2.0)
def sfx_shardling_burst(rng, i):
    x = glass_pings(1.3, rng, 28, 1200, 5200, (0.05, 0.3), span=0.4, shape=2.0)
    add_at(x, _crack(rng, 0.1, 35, 1500, 9000), 0.0, 1.3)
    nb = hp(white(0.25, rng), 1500) * env_ad(0.25, 0.001, 0.04)
    add_at(x, nb, 0.0, 0.5)
    return reverb(x, 0.15, 1.0, 8000, 0.6, seed=11)


# =============================================================================
# Wyrm (small friendly dragon)
# =============================================================================

def _chirp(rng, d, pts, vib=0.0, bright=0.3):
    t = tt(d)
    f = curve(d, pts, True) * (1 + vib * np.sin(TWO_PI * 22 * t))
    x = np.sin(phase_of(f)) + bright * np.sin(2 * phase_of(f) + 0.5) + 0.25 * bright * np.sin(3 * phase_of(f))
    return lp(x * env_swell(d, 0.012, d * 0.5), 6000)


@reg(["mob/wyrm/chirp1", "mob/wyrm/chirp2", "mob/wyrm/chirp3"], level=-4.0)
def sfx_wyrm_chirp(rng, i):
    x = np.zeros(ns(0.6))
    if i == 0:
        add_at(x, _chirp(rng, 0.17, [(0, 1100), (0.07, 1750), (0.17, 1450)], 0.01), 0.0)
    elif i == 1:
        add_at(x, _chirp(rng, 0.11, [(0, 1300), (0.11, 1700)]), 0.0)
        add_at(x, _chirp(rng, 0.15, [(0, 1500), (0.06, 1900), (0.15, 1600)]), 0.14, 0.9)
    else:
        add_at(x, _chirp(rng, 0.22, [(0, 1600), (0.08, 1350), (0.22, 1800)], 0.015, 0.4), 0.0)
    return reverb(x, 0.1, 0.7, 7000, 0.5, seed=12)


@reg("mob/wyrm/warn", level=-5.0)
def sfx_wyrm_warn(rng, i):
    x = np.zeros(ns(1.2))
    for k in range(8):
        add_at(x, _chirp(rng, 0.085, [(0, 1900), (0.03, 2400), (0.085, 2000)], 0.0, 0.45), 0.12 * k,
               0.8 + 0.2 * (k % 2))
    return reverb(x, 0.1, 0.7, 7000, 0.5, seed=12)


@reg("mob/wyrm/flap", level=-5.0)
def sfx_wyrm_flap(rng, i):
    x = np.zeros(ns(0.6))
    for k in range(6):
        p = lp(bp(white(0.06, rng), 300, 1600), 1800) * env_ad(0.06, 0.008, 0.015)
        add_at(x, p, 0.075 * k, 1.0 - 0.12 * k)
    return x


@reg("mob/wyrm/hurt", level=-5.0)
def sfx_wyrm_hurt(rng, i):
    d = 0.6
    x = _chirp(rng, d, [(0, 1350), (0.1, 1450), (d, 700)], 0.02, 0.35)
    x *= 1 + 0.3 * np.sin(TWO_PI * 7 * tt(d))
    x += 0.15 * bp(white(d, rng), 1500, 5000) * env_swell(d, 0.02, 0.3)
    return reverb(x, 0.12, 0.8, 6000, 0.5, seed=12)


@reg("mob/wyrm/slumber", level=-5.0)
def sfx_wyrm_slumber(rng, i):
    d = 2.4
    x = np.zeros(ns(d))
    wh = _chirp(rng, 1.6, [(0, 1000), (0.4, 920), (1.6, 500)], 0.006, 0.15) * curve(1.6, [(0, 1), (1.6, 0.3)])
    add_at(x, wh, 0.0)
    t = tt(1.2)
    purr = lp(white(1.2, rng), 400) * (0.5 + 0.5 * np.sin(TWO_PI * 24 * t)) * env_swell(1.2, 0.3, 0.6) * 0.5
    add_at(x, purr, 1.1)
    return reverb(x, 0.2, 1.2, 5000, 0.6, seed=12)


# =============================================================================
# World / system events
# =============================================================================

@reg("event/tide_stir", level=-1.5)
def sfx_tide_stir(rng, i):
    d = 4.0
    t = tt(d)
    f = 55.0
    low = np.sin(TWO_PI * f * t) + 0.8 * np.sin(TWO_PI * 58.3 * t) + 0.5 * np.sin(TWO_PI * 110.4 * t)
    # presence band so it cuts through on small speakers
    pres = harmonic(np.full(ns(d), 110.0), [0, 0.5, 0.45, 0.4, 0.3, 0.25, 0.2, 0.15, 0.1, 0.08], rng)
    pres += harmonic(np.full(ns(d), 116.5), [0, 0.4, 0.35, 0.3, 0.2, 0.15, 0.1], rng)
    pres = lp(pres, 1400) * (1 + 0.3 * np.sin(TWO_PI * 3.3 * t))
    nz = whoosh(d, rng, [(0, 300), (d, 900)], [(0, 0), (d * 0.85, 0.5), (d, 0.3)], 0.7)
    x = 0.8 * low + 0.6 * pres + 0.4 * nz / (np.std(nz) + 1e-9)
    x *= curve(d, [(0, 0), (3.0, 0.85), (3.6, 1.0), (d, 0)]) ** 1.3
    return reverb(x, 0.25, 2.5, 3000, 1.3, seed=13)


@reg("event/tide_rise", level=-3.0)
def sfx_tide_rise(rng, i):
    d = 5.0
    t = tt(d)
    pitch = 2 ** (curve(d, [(0, 0), (d, 2)]) / 12)
    x = np.zeros(ns(d))
    for m in (nm("D2"), nm("A2"), nm("D3"), nm("F3")):
        x += harmonic(mtof(m) * pitch, [1, 0.4, 0.2, 0.1, 0.05], rng)
    x = lp(x, 1500)
    nz = whoosh(d, rng, [(0, 200), (d, 2500)], [(0, 0), (d * 0.9, 1), (d, 0.5)], 0.9)
    y = (x / (np.std(x) + 1e-9) * 0.7 + 0.5 * nz / (np.std(nz) + 1e-9)) * curve(d, [(0, 0), (4.3, 1), (d, 0)]) ** 1.5
    return reverb(y, 0.3, 2.5, 4000, 1.3, seed=13)


@reg("event/tide_fall", level=-3.0)
def sfx_tide_fall(rng, i):
    d = 5.0
    br = _breath(d, rng, [(0, 0), (0.6, 1.0), (d, 0)], "a", 0.55)
    br = stft_shape(br, lambda F, T: band(F, 1200 * 0.25 ** (T / d), 1.3), 2048)
    pitch = 2 ** (curve(d, [(0, 0), (d, -2)]) / 12)
    x = np.zeros(ns(d))
    for m in (nm("D2"), nm("A2"), nm("F3")):
        x += harmonic(mtof(m) * pitch, [1, 0.35, 0.15, 0.06], rng)
    x = lp(x, 1200) * curve(d, [(0, 0), (0.6, 1), (d, 0)])
    y = br / (np.std(br) + 1e-9) * 0.8 + 0.5 * x / (np.std(x) + 1e-9)
    return reverb(y, 0.3, 2.5, 3500, 1.3, seed=13)


@reg("event/erosion_static", level=-3.0)
def sfx_erosion(rng, i):
    d = 1.5
    t = tt(d)
    wh = noise_shaped(d, rng, lambda F, T: 0.4 * formant_gain(F, "i", 1.2, 0.05) + band(F, 5000, 0.6), 1024)
    wh = wh / (np.std(wh) + 1e-9)
    e = np.clip(t / d, 0, 1) ** 2.2 * env_swell(d, 0.0, 0.04)       # reversed envelope
    hollow = reson(wh, 700, 5) + reson(wh, 1400, 7)
    st = (rng.random(ns(d)) < 0.01) * rng.standard_normal(ns(d)) * 3
    st = bp(st + 0.3 * white(d, rng), 3000, 9000)
    x = (0.6 * wh + 0.5 * hollow / (np.std(hollow) + 1e-9) + 0.5 * st) * e
    return reverb(x, 0.2, 1.2, 6000, 0.8, seed=14)


@reg("event/sundering_rumble", level=-2.0)
def sfx_sundering_rumble(rng, i):
    d = 6.0
    t = tt(d)
    br = lp(colored(d, rng, -6), 130)
    br = br / (np.std(br) + 1e-9) * (1 + 0.5 * smooth_noise(d, 2, rng))
    sub = np.sin(phase_of(38 + 6 * smooth_noise(d, 0.5, rng))) * 0.6
    grit = lp(crumble(d, rng, 160, 150, 1200, (0.003, 0.02), 1.0, decay_amp=False), 1500)
    grit = grit / (np.std(grit) + 1e-9) * 0.35
    x = (br + sub + grit) * curve(d, [(0, 0), (1.5, 0.8), (3.5, 1.0), (d, 0)])
    return reverb(x, 0.25, 3.0, 1500, 1.6, seed=15)


@reg("event/sundering_crack", level=-1.0)
def sfx_sundering_crack(rng, i):
    d = 4.5
    x = np.zeros(ns(d))
    n_ = ns(0.004)
    add_at(x, np.linspace(1, -1, n_), 0.0, 1.0)
    add_at(x, _crack(rng, 0.35, 120, 300, 8000), 0.0, 1.0)
    add_at(x, thump(2.5, 80, 28, 0.7, 0.08), 0.0, 1.3)
    tail = lp(crumble(3.5, rng, 200, 120, 2500, (0.002, 0.015), 1.8), 2500)
    add_at(x, tail, 0.05, 0.6)
    rum = lp(colored(d, rng, -6), 110) * curve(d, [(0, 0), (0.2, 1), (d, 0)])
    x += 0.5 * rum / (np.std(rum) + 1e-9) * 0.3
    return reverb(x, 0.4, 4.0, 2500, 1.8, seed=15)


@reg("event/reassembly_chord", level=-3.0)
def sfx_reassembly_chord(rng, i):
    d = 4.5
    ch = pad_chord([nm("D2"), nm("A2"), nm("F#3"), nm("A3"), nm("E4")], d, "warm", 0.8, 2.5, seed=21)
    bm = bowed_metal(nf("D3"), d, rng, 1.0, 2.5, 0.3)
    x = ch.mean(axis=1) + 0.25 * bm / (np.std(bm) + 1e-9) * np.std(ch)
    return reverb(x, 0.3, 3.0, 4000, 1.3, seed=16)


@reg(["event/reassembly_fly1", "event/reassembly_fly2"], level=-4.0)
def sfx_reassembly_fly(rng, i):
    d = 0.9 + 0.1 * i
    w = whoosh(d, rng, [(0, 400 + 100 * i), (d * 0.5, 1400), (d, 500)], [(0, 0), (d * 0.5, 1), (d, 0)], 0.9)
    r = lp(white(d, rng), 200) * curve(d, [(0, 0), (d * 0.5, 1), (d, 0)]) * 0.5
    g = stone_hits(d, rng, 6, 600, 2500, (0.003, 0.01), span=d * 0.8, shape=1.0) * 0.4
    return w / (np.std(w) + 1e-9) + r / (np.std(r) + 1e-9) * 0.4 + g / (np.max(np.abs(g)) + 1e-9)


@reg(["event/reassembly_click1", "event/reassembly_click2"], level=-4.0)
def sfx_reassembly_click(rng, i):
    x = stone_hits(0.25, rng, 1, 700 + 150 * i, 900 + 150 * i, (0.01, 0.018), span=0.001)
    add_at(x, stone_hits(0.2, rng, 1, 1400, 1800, (0.006, 0.01), span=0.001), 0.012, 0.5)
    x += 0.4 * thump(0.25, 190, 140, 0.03) * np.max(np.abs(x))
    return x


def _ignite(rng, d=0.5):
    return lp(whoosh(d, rng, [(0, 300), (d, 1200)], [(0, 0), (d * 0.7, 1), (d, 0.3)], 1.0), 2000)


@reg("event/reassembly_lantern", level=-3.0)
def sfx_reassembly_lantern(rng, i):
    x = np.zeros(ns(3.0))
    ig = _ignite(rng, 0.35)
    add_at(x, ig / (np.max(np.abs(ig)) + 1e-9), 0.0, 0.4)
    add_at(x, bell(1040, 1.0, rng, 0.75), 0.3, 0.9)
    add_at(x, bell(1560, 0.8, rng, 0.8), 0.3, 0.3)
    return reverb(x, 0.25, 1.8, 6000, 1.0, seed=16)


@reg("event/egg_pulse", level=-4.0)
def sfx_egg_pulse(rng, i):
    x = np.zeros(ns(1.3))
    for tm, a in ((0.0, 1.0), (0.24, 0.7)):
        h = thump(0.4, 75, 52, 0.09, 0.03) + 0.3 * thump(0.4, 150, 104, 0.06, 0.03)
        add_at(x, lp(h, 400), tm, a)
    glow = glass_harm(nf("D5"), 1.0, 0.15, 0.7, rng, 0.0) * 0.05
    add_at(x, glow, 0.05)
    return reverb(x, 0.15, 1.2, 3000, 0.7, seed=17)


@reg("event/egg_crack", level=-3.0)
def sfx_egg_crack(rng, i):
    x = np.zeros(ns(2.0))
    for k in range(4):
        add_at(x, _crack(rng, 0.05, 12, 1500, 7000), 0.08 * k + rng.uniform(0, 0.03), 1.0 - 0.15 * k)
        add_at(x, stone_hits(0.15, rng, 2, 1500, 4000, (0.004, 0.01), span=0.02), 0.08 * k, 0.5)
    t = tt(1.5)
    sh = sum(glass_harm(f, 1.5, 0.3, 0.8, rng, 0.0) * a for f, a in ((1760, 1.0), (2640, 0.5), (3520, 0.3)))
    sh *= 1 + 0.4 * np.sin(TWO_PI * 9 * t)
    add_at(x, sh / (np.max(np.abs(sh)) + 1e-9), 0.25, 0.35)
    return reverb(x, 0.2, 1.5, 7000, 0.8, seed=17)


@reg("event/egg_hatch", level=-2.0)
def sfx_egg_hatch(rng, i):
    x = np.zeros(ns(4.5))
    add_at(x, _crack(rng, 0.2, 60, 1000, 8000), 0.0, 1.0)
    add_at(x, hp(white(0.3, rng), 800) * env_ad(0.3, 0.001, 0.06), 0.0, 0.5)
    add_at(x, thump(0.4, 120, 70, 0.08), 0.0, 0.6)
    add_at(x, stone_hits(0.8, rng, 14, 1400, 4500, (0.004, 0.012), span=0.6), 0.02, 0.5)
    for k, n_ in enumerate(["D5", "F#5", "A5", "E6", "A6"]):
        add_at(x, glass_chime(nf(n_), 0.45, 1.8, rng, 0.1), 0.35 + 0.16 * k)
    return reverb(x, 0.3, 2.2, 7000, 1.0, seed=17)


@reg("event/gravity_anchor_hum", kind="sfxloop", level=-6.0)
def sfx_gravity_anchor(rng, i):
    d = 3.0
    n = ns(d)
    t = np.arange(n) / SR
    # all frequencies have an integer number of cycles in 3 s -> seamless loop
    x = (np.sin(TWO_PI * 55.0 * t) + 0.9 * np.sin(TWO_PI * (55 + 2 / 3) * t)
         + 0.45 * np.sin(TWO_PI * 110 * t + 0.4) + 0.25 * np.sin(TWO_PI * 165 * t + 1.1)
         + 0.12 * np.sin(TWO_PI * 220.333333 * t) + 0.06 * np.sin(TWO_PI * 330 * t))
    x *= 1 + 0.15 * np.sin(TWO_PI * (1 / 1.5) * t)
    nz = np.fft.irfft(np.fft.rfft(rng.standard_normal(n)) * band(np.fft.rfftfreq(n, 1 / SR), 220, 0.4), n)
    x += 0.4 * nz / (np.std(nz) + 1e-9) * 0.2
    return x


@reg("event/rift_open", level=-2.0)
def sfx_rift_open(rng, i):
    d = 3.0
    w = whoosh(d, rng, [(0, 150), (1.2, 600), (d, 250)], [(0, 0), (0.9, 1), (d, 0)], 1.0)
    w = lp(w, 3000)
    t = tt(d)
    dens = curve(d, [(0, 0.002), (0.8, 0.03), (2.0, 0.01), (d, 0)])
    tear = (rng.random(ns(d)) < dens) * rng.standard_normal(ns(d))
    tear = bp(tear, 400, 4000) * (0.5 + 0.5 * np.abs(smooth_noise(d, 15, rng)))
    sub = np.sin(phase_of(curve(d, [(0, 30), (1.0, 55), (d, 40)]))) * curve(d, [(0, 0), (1.0, 1), (d, 0)])
    x = w / (np.std(w) + 1e-9) + 0.8 * tear / (np.std(tear) + 1e-9) * curve(d, [(0, 0), (0.7, 1), (d, 0)]) + 0.8 * sub
    return reverb(x, 0.3, 2.5, 3000, 1.3, seed=18)


@reg("event/rift_close", level=-3.0)
def sfx_rift_close(rng, i):
    d = 2.0
    w = whoosh(d, rng, [(0, 2500), (d, 200)], [(0, 0), (d * 0.95, 1), (d, 0)], 0.9)
    t = tt(d)
    tone = np.sin(phase_of(curve(d, [(0, 400), (d, 80)], True))) * (t / d) ** 2
    x = w / (np.std(w) + 1e-9) * (t / d) ** 1.5 + 0.6 * tone
    x = np.concatenate([x * env_swell(d, 0, 0.01), np.zeros(ns(0.6))])
    add_at(x, thump(0.4, 120, 60, 0.07), d - 0.01, 1.5)
    add_at(x, click(rng, 1.0, 4000, 100), d - 0.01, 0.6)
    return reverb(x, 0.25, 1.8, 3000, 1.0, seed=18)


@reg("event/socket_hum", level=-8.0)
def sfx_socket_hum(rng, i):
    d = 2.0
    t = tt(d)
    x = fm_tone(d, 220, 2.0, 0.8) + 0.8 * fm_tone(d, 221.6, 2.0, 0.6) + 0.35 * np.sin(TWO_PI * 660.5 * t)
    x += 0.15 * np.sin(TWO_PI * 1320 * t) * (1 + np.sin(TWO_PI * 6 * t)) * 0.5
    x *= env_swell(d, 0.25, 0.7)
    return reverb(lp(x, 4000), 0.2, 1.5, 5000, 0.8, seed=19)


# =============================================================================
# Items
# =============================================================================

@reg("item/tether_fire", level=-3.0)
def sfx_tether_fire(rng, i):
    d = 0.55
    t = tt(d)
    f = curve(d, [(0, 300), (0.15, 620), (d, 520)], True) * (1 + 0.08 * np.sin(TWO_PI * 28 * t) * np.exp(-t / 0.12))
    spring = np.sin(phase_of(f)) * env_ad(d, 0.003, 0.12)
    w = whoosh(d, rng, [(0, 700), (0.2, 2500), (d, 1500)], [(0, 0), (0.1, 1), (d, 0)], 0.7)
    x = 0.6 * spring + w / (np.std(w) + 1e-9) * 0.5
    add_at(x, click(rng, 0.8, 8000, 300), 0.0, 1.0)
    return x


def _clank(rng, f=1300, decay=0.18):
    ratios = [1.0, 1.52, 2.17, 2.94, 3.71]
    ir = modes(decay * 5, [f * r for r in ratios], [decay * k for k in (1, 0.7, 0.5, 0.35, 0.25)],
               [1, 0.7, 0.5, 0.3, 0.2], rng)
    return excite(click(rng, 0.8, 10000, 300), ir)


@reg("item/tether_latch", level=-3.0)
def sfx_tether_latch(rng, i):
    x = np.zeros(ns(1.0))
    add_at(x, _clank(rng, 1250, 0.12), 0.0, 1.0)
    add_at(x, _clank(rng, 1700, 0.08), 0.022, 0.6)
    add_at(x, glass_chime(2093, 0.5, 0.45, rng, 0.05), 0.05)
    return x


@reg("item/tether_reel", level=-4.0)
def sfx_tether_reel(rng, i):
    d = 0.6
    x = np.zeros(ns(d + 0.1))
    tm = 0.0
    while tm < d:
        add_at(x, _clank(rng, rng.uniform(2200, 2600), 0.015), tm, rng.uniform(0.5, 0.8))
        tm += 1 / 28.0
    whir = bp(white(d, rng), 600, 2500) * env_swell(d, 0.03, 0.1) * 0.1
    add_at(x, whir, 0.0)
    return x


@reg("item/tether_detach", level=-3.0)
def sfx_tether_detach(rng, i):
    x = np.zeros(ns(0.5))
    add_at(x, click(rng, 0.6, 10000, 600), 0.0, 1.5)
    add_at(x, _clank(rng, 1800, 0.05), 0.0, 0.6)
    sp = np.sin(phase_of(curve(0.25, [(0, 520), (0.25, 180)], True))) * env_ad(0.25, 0.002, 0.06)
    add_at(x, sp, 0.005, 0.5)
    w = whoosh(0.3, rng, [(0, 2000), (0.3, 800)], [(0, 1), (0.3, 0)], 0.6)
    add_at(x, w / (np.std(w) + 1e-9) * 0.15, 0.0)
    return x


@reg("item/void_anchor_trigger", level=-6.0)
def sfx_void_anchor(rng, i):
    x = np.zeros(ns(2.0))
    w = whoosh(0.7, rng, [(0, 4000), (0.7, 900)], [(0, 0), (0.5, 1), (0.7, 0.2)], 0.6)
    add_at(x, w / (np.max(np.abs(w)) + 1e-9) * 0.6, 0.0)
    add_at(x, glass_chime(1568, 0.8, 0.6, rng, 0.4), 0.65)
    add_at(x, click(rng, 0.5, 12000, 2000), 0.65, 0.5)
    hum = np.sin(TWO_PI * 196 * tt(1.2)) * env_swell(1.2, 0.05, 1.0) * 0.15
    add_at(x, hum, 0.65)
    return reverb(x, 0.2, 1.4, 7000, 0.7, seed=20)


@reg("item/capsule_hum", kind="sfxloop", level=-8.0)
def sfx_capsule_hum(rng, i):
    d = 2.0
    n = ns(d)
    t = np.arange(n) / SR
    x = np.sin(TWO_PI * 330 * t) + 0.5 * np.sin(TWO_PI * 495 * t) + 0.25 * np.sin(TWO_PI * 660.5 * t)
    x *= 0.75 + 0.25 * np.cos(TWO_PI * 1.0 * t)   # 1 Hz beacon pulse, 2 whole cycles
    return x


@reg("item/compass_bind", level=-3.0)
def sfx_compass_bind(rng, i):
    x = np.zeros(ns(2.5))
    for k, n_ in enumerate(["E5", "B5", "E6"]):
        add_at(x, glass_chime(nf(n_), 0.5, 0.5, rng, 0.1), 0.07 * k)
    add_at(x, _clank(rng, 900, 0.06), 0.3, 0.7)
    add_at(x, thump(0.15, 260, 180, 0.03), 0.3, 0.5)
    for n_ in ("E5", "G#5", "B5"):
        add_at(x, glass_chime(nf(n_), 0.45, 1.4, rng, 0.05), 0.34)
    return reverb(x, 0.25, 1.6, 7000, 0.8, seed=20)


def _leather(rng, d=0.25):
    x = lp(bp(white(d, rng), 300, 3000), 2500) * env_ad(d, 0.02, d * 0.3)
    cr = crumble(d, rng, 12, 1000, 5000, (0.0005, 0.002), 1.5) * 0.4
    return x + cr


@reg("item/satchel_open", level=-4.0)
def sfx_satchel_open(rng, i):
    x = np.zeros(ns(0.6))
    add_at(x, _leather(rng, 0.25), 0.0, 1.0)
    add_at(x, stone_hits(0.15, rng, 2, 1600, 2600, (0.01, 0.02), span=0.03), 0.2, 0.7)
    return x


@reg("item/satchel_close", level=-4.0)
def sfx_satchel_close(rng, i):
    x = np.zeros(ns(0.5))
    add_at(x, stone_hits(0.15, rng, 2, 1400, 2400, (0.01, 0.02), span=0.03), 0.0, 0.8)
    add_at(x, _leather(rng, 0.15), 0.02, 0.5)
    add_at(x, _clank(rng, 2600, 0.02), 0.2, 0.6)
    return x


@reg("item/echo_tablet_read", level=-5.0)
def sfx_tablet_read(rng, i):
    d = 1.5
    sh = _shimmer(d, rng, 3000, 9000, 7) * env_swell(d, 0.3, 0.8) * 0.3
    x = np.zeros(ns(d + 1))
    add_at(x, sh, 0.0)
    for k in range(7):
        add_at(x, glass_chime(rng.uniform(2500, 5000), 0.3, 0.3, rng, 0.0), rng.uniform(0.1, 1.2))
    return reverb(x, 0.3, 1.5, 8000, 0.8, seed=20)


@reg("item/elytra_gale", level=-3.0)
def sfx_elytra_gale(rng, i):
    d = 1.1
    w = whoosh(d, rng, [(0, 400), (0.15, 1500), (d, 600)], [(0, 0), (0.06, 1), (d, 0)], 1.3)
    w *= 1 + 0.3 * smooth_noise(d, 8, rng)
    return w


@reg("item/elytra_still", level=-4.0)
def sfx_elytra_still(rng, i):
    d = 0.8
    w = whoosh(d, rng, [(0, 1800), (d, 400)], [(0, 0.2), (0.1, 1), (d, 0)], 0.9)
    w *= 1 + 0.6 * np.sin(TWO_PI * curve(d, [(0, 14), (d, 7)]) * tt(d))
    return w


@reg("item/elytra_hover", level=-6.0)
def sfx_elytra_hover(rng, i):
    d = 1.0
    t = tt(d)
    f = 120 * (1 + 0.01 * np.sin(TWO_PI * 3 * t))
    x = harmonic(f, [1, 0.4, 0.2, 0.1], rng) + 0.3 * bp(white(d, rng), 300, 1500)
    return lp(x, 2000) * env_swell(d, 0.15, 0.3)


@reg("item/glowbulb_harvest", level=-4.0)
def sfx_glowbulb_harvest(rng, i):
    x = np.zeros(ns(0.5))
    add_at(x, lp(click(rng, 1.0, 5000, 300), 3000), 0.0, 0.7)
    add_at(x, bubble(0.08, 250, 700, 0.025, rng), 0.02, 1.0)
    add_at(x, _glow_hum(0.35, rng) * 0.08, 0.02)
    return x


@reg("item/pod_pop", level=-4.0)
def sfx_pod_pop(rng, i):
    x = np.zeros(ns(0.8))
    add_at(x, bubble(0.06, 350, 900, 0.015, rng), 0.0, 1.0)
    add_at(x, lp(white(0.05, rng), 1500) * env_ad(0.05, 0.001, 0.01), 0.0, 0.4)
    for k in range(3):
        add_at(x, glass_chime(rng.uniform(3000, 5000), 0.25, 0.2, rng, 0.0), 0.05 + 0.05 * k)
    return x


# =============================================================================
# Boss: The Unmade
# =============================================================================

@reg("boss/core_pulse", level=-2.0)
def sfx_core_pulse(rng, i):
    x = np.zeros(ns(1.8))
    add_at(x, thump(1.0, 70, 38, 0.25, 0.05), 0.0, 1.0)
    add_at(x, lp(thump(0.5, 140, 80, 0.08), 600), 0.0, 0.4)
    add_at(x, glass_harm(1864, 1.2, 0.02, 1.0, rng, 0.0) * np.exp(-tt(1.2) / 0.35), 0.0, 0.25)
    return reverb(x, 0.2, 1.8, 4000, 1.0, seed=22)


@reg("boss/shard_volley", level=-2.0)
def sfx_shard_volley(rng, i):
    d = 1.3
    x = np.zeros(ns(d + 1.0))
    ch = 0.7
    t = tt(ch)
    charge = np.sin(phase_of(curve(ch, [(0, 300), (ch, 1400)], True))) * (t / ch) ** 2
    cn = whoosh(ch, rng, [(0, 800), (ch, 5000)], [(0, 0), (ch, 1)], 0.6)
    add_at(x, 0.4 * charge + 0.4 * cn / (np.std(cn) + 1e-9) * (t / ch), 0.0)
    for k in range(8):
        tm = ch + 0.07 * k
        w = whoosh(0.12, rng, [(0, 5000), (0.12, 2000)], [(0, 1), (0.12, 0)], 0.5)
        add_at(x, w / (np.max(np.abs(w)) + 1e-9) * 0.5, tm)
        add_at(x, glass_pings(0.4, rng, 1, 2000, 4500, (0.05, 0.12), span=0.001), tm, 0.8)
    return reverb(x, 0.2, 1.4, 7000, 0.8, seed=22)


@reg("boss/tendril_sweep", level=-3.0)
def sfx_tendril_sweep(rng, i):
    d = 1.0
    w = whoosh(d, rng, [(0, 250), (d * 0.5, 800), (d, 200)], [(0, 0), (d * 0.5, 1), (d, 0)], 0.9)
    wet = squelch(d, rng, [(0, 300), (0.3, 900), (0.6, 400), (d, 700)], 0.4,
                  [(0, 0), (0.4, 0.8), (d, 0)])
    wet *= 0.6 + 0.4 * np.abs(smooth_noise(d, 18, rng))
    return lp(w / (np.std(w) + 1e-9) + 0.5 * wet / (np.std(wet) + 1e-9), 3000)


@reg("boss/lens_hum", level=-4.0)
def sfx_lens_hum(rng, i):
    d = 1.0
    t = tt(d)
    f = 110 * (1 + 0.03 * np.sin(TWO_PI * 5 * t) + 0.01 * np.sin(TWO_PI * 1.3 * t))
    x = harmonic(f, [1, 0.6, 0.45, 0.3, 0.2, 0.12, 0.08], rng)
    x = stft_shape(x, lambda F, T: 0.3 + band(F, 300 * 4 ** (0.5 + 0.5 * np.sin(TWO_PI * 2.5 * T)), 0.5), 1024)
    return lp(x, 2500) * env_swell(d, 0.08, 0.3)


@reg("boss/void_pulse_inhale", level=-3.0)
def sfx_void_inhale(rng, i):
    d = 2.0
    t = tt(d)
    w = whoosh(d, rng, [(0, 3000), (d, 400)], [(0, 0.05), (d, 1)], 1.0)
    tone = np.sin(phase_of(curve(d, [(0, 80), (d, 160)], True))) + 0.4 * np.sin(phase_of(curve(d, [(0, 120), (d, 240)], True)))
    e = (t / d) ** 2.5
    x = (w / (np.std(w) + 1e-9) * 0.6 + tone * 0.6) * e * env_swell(d, 0, 0.02)
    return x


@reg("boss/void_pulse_exhale", level=-1.0)
def sfx_void_exhale(rng, i):
    d = 2.5
    x = np.zeros(ns(d))
    blast = whoosh(1.6, rng, [(0, 3000), (1.6, 300)], [(0, 1), (1.6, 0)], 1.4)
    blast = blast / (np.max(np.abs(blast)) + 1e-9) * env_ad(1.6, 0.003, 0.4)
    add_at(x, blast, 0.0, 1.0)
    add_at(x, thump(1.5, 70, 30, 0.4, 0.06), 0.0, 1.1)
    sh = sum(glass_harm(f, 1.8, 0.05, 1.4, rng, 0.0) for f in (2349, 3136, 3520))
    sh *= (1 + 0.5 * np.sin(TWO_PI * 11 * tt(1.8))) * np.exp(-tt(1.8) / 0.6)
    add_at(x, sh / (np.max(np.abs(sh)) + 1e-9), 0.02, 0.35)
    return reverb(x, 0.35, 3.0, 5000, 1.5, seed=23)


@reg("boss/swarm", level=-3.0)
def sfx_swarm(rng, i):
    d = 1.6
    x = np.zeros(ns(d + 0.6))
    tm = 0.0
    k = 0
    while tm < d:
        add_at(x, _crack(rng, 0.05, 10, 900, 6000), tm, 0.5 + 0.5 * tm / d)
        add_at(x, glass_pings(0.3, rng, 1, 1200, 3500, (0.04, 0.12), span=0.001), tm, 0.5)
        tm += 0.2 * (0.8 ** k) + 0.03
        k += 1
    cr = creak(d, rng, [(0, 30), (d, 140)], [1100, 1900, 3100], 0.01, 0.4, [(0, 0.1), (d, 1)])
    add_at(x, cr / (np.max(np.abs(cr)) + 1e-9), 0.0, 0.4)
    return reverb(x, 0.2, 1.5, 6000, 0.8, seed=23)


@reg("boss/tendril_grab", level=-2.0)
def sfx_tendril_grab(rng, i):
    d = 1.5
    t = tt(d)
    f = curve(d, [(0, 80), (d, 165)], True)
    x = harmonic(f, [1, 0.5, 0.3, 0.2, 0.12], rng) + 0.8 * harmonic(f * 1.4142, [1, 0.4, 0.2], rng)
    x = lp(x, 1800) * (1 + 0.3 * np.sin(TWO_PI * curve(d, [(0, 4), (d, 11)]) * t))
    cr = whoosh(d, rng, [(0, 400), (d, 1600)], [(0, 0.2), (d, 1)], 0.6)
    y = (x / (np.std(x) + 1e-9) + 0.4 * cr / (np.std(cr) + 1e-9)) * curve(d, [(0, 0), (d * 0.9, 1), (d, 0)])
    return reverb(y, 0.2, 1.5, 4000, 1.0, seed=23)


@reg("boss/phase_freeze", level=-8.0)
def sfx_phase_freeze(rng, i):
    d = 2.4
    t = tt(d)
    x = np.sin(TWO_PI * 1318.5 * t) + 0.04 * np.sin(TWO_PI * 2637 * t) + 0.3 * np.sin(TWO_PI * 1319.3 * t)
    x *= env_ad(d, 0.006, 0.9, 0.2)
    return reverb(x, 0.25, 2.5, 9000, 1.0, seed=24)


@reg(["boss/hurt1", "boss/hurt2"], level=-3.0)
def sfx_boss_hurt(rng, i):
    x = glass_pings(0.8, rng, 6, 700 + 100 * i, 2000, (0.1, 0.3), span=0.12,
                    ratios=(1.0, 2.756, 5.404, 8.933))
    add_at(x, _crack(rng, 0.08, 22, 900, 7000), 0.0, 1.0)
    return reverb(lp(x, 7000), 0.15, 1.2, 6000, 0.8, seed=24)


@reg("boss/final_silence", level=-4.0)
def sfx_final_silence(rng, i):
    d = 4.0
    t = tt(d)
    x = glass_harm(880, d, 0.01, 0.05, rng, 0.02) + 0.6 * glass_harm(1320, d, 0.01, 0.05, rng, 0.02)
    x *= np.exp(-t / 0.9) * curve(d, [(0, 1), (3.2, 1), (d, 0)])
    y = reverb(x, 0.3, 2.0, 6000, 1.0, seed=24)
    y = y[: ns(d)] * curve(d, [(0, 1), (2.6, 1), (d, 0)]) ** 2
    return y


@reg("boss/reform_chord", level=-3.0)
def sfx_reform_chord(rng, i):
    d = 8.0
    mix = np.zeros(ns(d + 4))
    sus = pad_chord([nm("D3"), nm("A3"), nm("D4"), nm("G4")], 4.0, "warm", 1.5, 1.2, seed=31)
    res = pad_chord([nm("D3"), nm("A3"), nm("D4"), nm("F#4"), nm("A4")], 4.8, "warm", 1.0, 2.5, seed=32)
    add_at(mix, sus.mean(axis=1), 0.0)
    add_at(mix, res.mean(axis=1), 3.2)
    for k, n_ in enumerate(["D3", "A3", "F#4", "A4", "D5"]):
        add_at(mix, piano(nm(n_), 0.45, 4.0, seed=k), 3.5 + 0.18 * k, 0.5)
    add_at(mix, glass_chime(nf("F#6"), 0.25, 2.0, rng, 0.05), 4.6)
    add_at(mix, glass_chime(nf("A6"), 0.2, 2.0, rng, 0.05), 5.2)
    y = reverb(mix, 0.35, 3.5, 5000, 1.4, seed=25)
    return y[: ns(d + 1.5)] * curve(d + 1.5, [(0, 1), (d, 1), (d + 1.5, 0)])


@reg("boss/pylon_break", level=-1.5)
def sfx_pylon_break(rng, i):
    x = glass_pings(1.5, rng, 30, 1000, 5000, (0.06, 0.35), span=0.5)
    add_at(x, _crack(rng, 0.15, 50, 800, 9000), 0.0, 1.3)
    d = 0.35
    zap = fm_tone(d, curve(d, [(0, 3000), (d, 180)], True), 1.41, curve(d, [(0, 4), (d, 0.5)]))
    zap = lp(zap * env_ad(d, 0.001, 0.1), 7000) + 0.3 * hp(white(d, rng), 2000) * env_ad(d, 0.001, 0.03)
    add_at(x, zap, 0.0, 0.8)
    return reverb(x, 0.25, 1.8, 6000, 1.0, seed=26)


# =============================================================================
# Seamless loop construction
# =============================================================================
# Loops are built from three kinds of material, each made periodic with period L:
#   * periodic sources (PADsynth tables of exactly L samples, integer-cycle tones)
#   * noise beds rendered L + XF long, tail equal-power cross-faded into the head
#   * discrete events, rendered with their tails and folded (wrapped) into the head
# The finished period is then reverberated by circular convolution, so the loop
# point is mathematically seamless.

LOOP_XF = 5.0


def xfade_bed(x, L):
    n, xf = ns(L), ns(LOOP_XF)
    assert len(x) >= n + xf
    y = x[:n].copy()
    w = np.linspace(0, 1, xf)
    fi, fo = np.sin(w * np.pi / 2), np.cos(w * np.pi / 2)
    if y.ndim == 2:
        fi, fo = fi[:, None], fo[:, None]
    y[:xf] = x[:xf] * fi + x[n: n + xf] * fo
    return y


def fold(x, L):
    n = ns(L)
    y = x[:n].copy()
    k = n
    while k < len(x):
        seg = x[k: k + n]
        y[: len(seg)] += seg
        k += n
    return y


def circ_reverb(x, wet, rt60, damp, size=1.0, seed=1):
    n = len(x)
    xx = np.concatenate([x, x], axis=0)
    y = reverb(xx, wet, rt60, damp, size, seed, stereo=True, dry=1.0)
    out = y[n: 2 * n].copy()
    # contributions of the first period's tail beyond 2n wrap again (IR < n so none)
    return out


def periodic_pad(f, L, kind, seed=0):
    """PADsynth table whose period is exactly the loop length."""
    n = ns(L)
    return padsynth(f, kind, seed, N=n)


def stereo_bed(d, rng, gfn, nfft=2048):
    return np.stack([noise_shaped(d, rng, gfn, nfft), noise_shaped(d, rng, gfn, nfft)], axis=1)


def gust(d, rng, rate=0.15, depth=0.6):
    return np.clip(1 + depth * smooth_noise(d, rate, rng), 0.15, 3)


def int_freq(f, L):
    """Round f so that it has an integer number of cycles in L seconds."""
    return max(1, round(f * L)) / L


# =============================================================================
# Ambient loops (stereo) and additions (mono)
# =============================================================================

AMB_LEVEL = -21.0   # LUFS: below vanilla-style ambience; no ambience is loud
ADD_LEVEL = -8.0    # dBFS peak for one-shot additions


def _wind_gain(rng, d, fc_lo, fc_hi, bw=1.0, tilt_db=-3.0, rate=0.08):
    fcs = np.exp(np.log(fc_lo) + (np.log(fc_hi) - np.log(fc_lo)) * (0.5 + 0.35 * smooth_noise(d, rate, rng)))
    gs = gust(d, rng, rate * 2, 0.5)
    tgrid = np.arange(len(fcs)) / SR

    def g(F, T):
        fc = np.interp(T, tgrid, fcs)
        a = np.interp(T, tgrid, gs)
        return a * band(F, fc, bw) * tilt(F, tilt_db, 800) * (F > 40)
    return g


@reg("ambient/chorus_reach_loop", kind="loop", level=AMB_LEVEL)
def amb_chorus_reach(rng, i):
    L = 32.0
    d = L + LOOP_XF
    wg = _wind_gain(rng, d, 900, 2400, 1.1, -2.0, 0.06)
    glints = [(1568.0, 0.0), (2349.3, 1.3), (3136.0, 2.1), (1975.5, 4.0)]

    def g(F, T):
        x = wg(F, T)
        for fq, ph in glints:
            x = x + 0.5 * band(F, fq, 0.012) * (0.5 + 0.5 * np.sin(TWO_PI * T / 9.0 + ph))
        return x
    wind = stereo_bed(d, rng, g, 4096)
    wind = xfade_bed(wind, L)
    wind /= np.std(wind) + 1e-9
    n = ns(L)
    pad = np.zeros((n, 2))
    for k, m in enumerate((nm("D2"), nm("A2"), nm("F#3"), nm("E4"))):
        p = periodic_pad(mtof(m), L, "warm", seed=40 + k)
        am = 1 + 0.25 * periodic_smooth_noise(n, 3, rng)
        pad += p * am[:, None] * (1.0 if k < 3 else 0.5)
    pad /= np.std(pad) + 1e-9
    x = 0.55 * wind + 0.7 * pad
    return circ_reverb(x, 0.35, 3.0, 5000, 1.2, seed=40)


@reg("ambient/stillwater_loop", kind="loop", level=AMB_LEVEL)
def amb_stillwater(rng, i):
    L = 30.0
    n = ns(L)
    t = np.arange(n) / SR
    am = 1 + 0.3 * periodic_smooth_noise(n, 4, rng)
    hum = np.zeros(n)
    for f, a in ((41.2, 1.0), (41.5, 0.7), (82.4, 0.45), (123.6, 0.22), (164.8, 0.12), (247.2, 0.05)):
        hum += a * np.sin(TWO_PI * int_freq(f, L) * t + rng.uniform(0, 6))
    hum *= am
    hum = np.stack([hum, np.roll(hum, ns(0.013))], axis=1)
    d = L + LOOP_XF
    bed = stereo_bed(d, rng, lambda F, T: band(F, 120, 0.9) * (F > 30), 4096)
    bed = xfade_bed(bed, L)
    x = hum / (np.std(hum) + 1e-9) + 0.35 * bed / (np.std(bed) + 1e-9)
    return circ_reverb(x, 0.08, 1.2, 1500, 0.8, seed=41)


@reg("ambient/fallen_spires_loop", kind="loop", level=AMB_LEVEL)
def amb_fallen_spires(rng, i):
    L = 34.0
    d = L + LOOP_XF
    wg = _wind_gain(rng, d, 300, 900, 1.2, -4.0, 0.07)
    tg = np.arange(ns(d)) / SR
    whistles = []
    for k in range(3):
        fc = np.exp(np.log(600 + 350 * k) + 0.12 * smooth_noise(d, 0.12, rng))
        amp = np.clip(smooth_noise(d, 0.1, rng) - 0.3, 0, None) ** 1.5
        whistles.append((fc, amp))

    def g(F, T):
        x = wg(F, T)
        for fc, amp in whistles:
            x = x + 0.9 * band(F, np.interp(T, tg, fc), 0.02) * np.interp(T, tg, amp)
        return x
    wind = xfade_bed(stereo_bed(d, rng, g, 4096), L)
    return circ_reverb(wind, 0.3, 2.5, 4000, 1.3, seed=42)


def _birdsong(rng, n_notes=8):
    x = np.zeros(ns(2.5))
    tm = 0.0
    base = rng.uniform(2200, 3400)
    for k in range(n_notes):
        dd = rng.uniform(0.05, 0.14)
        f0 = base * rng.uniform(0.8, 1.25)
        pts = [(0, f0), (dd * 0.5, f0 * rng.uniform(1.1, 1.5)), (dd, f0 * rng.uniform(0.7, 1.0))]
        add_at(x, _chirp(rng, dd, pts, 0.0, 0.1), tm, rng.uniform(0.5, 1.0))
        tm += dd + rng.uniform(0.02, 0.12)
    return x


def _moo(rng, d=2.0, f0=115.0):
    f = curve(d, [(0, f0), (0.4 * d, f0 * 1.06), (d, f0 * 0.85)], True)
    x = voice(d, f, rng, "o", "u", 1.4, 0.06, 0.9, 0.85, 0.006)
    return lp(x * env_swell(d, 0.25, 0.6), 2000)


@reg("ambient/hoard_loop", kind="loop", level=AMB_LEVEL)
def amb_hoard(rng, i):
    L = 32.0
    d = L + LOOP_XF
    wind = xfade_bed(stereo_bed(d, rng, _wind_gain(rng, d, 250, 700, 1.2, -4.0, 0.06), 4096), L)
    wind /= np.std(wind) + 1e-9
    ev = np.zeros((ns(L + 12), 2))
    for k, tm in enumerate([1.5, 7.0, 12.5, 19.0, 26.0]):
        b = resample_speed(_birdsong(rng, int(rng.integers(5, 10))), rng.uniform(0.35, 0.5))
        b = lp(b, 2500)
        add_at(ev, pan(b / (np.max(np.abs(b)) + 1e-9), rng.uniform(-0.7, 0.7)), tm, 0.35)
    for tm in (4.0, 21.5):
        mo = resample_speed(_moo(rng, 2.2, rng.uniform(105, 125)), 0.62)
        add_at(ev, pan(mo / (np.max(np.abs(mo)) + 1e-9), rng.uniform(-0.5, 0.5)), tm, 0.3)
    ev = fold(ev, L)
    x = 0.5 * wind + ev / (np.std(ev) + 1e-9) * 0.35
    return circ_reverb(x, 0.55, 4.0, 2500, 1.6, seed=43)


@reg("ambient/underside_loop", kind="loop", level=AMB_LEVEL)
def amb_underside(rng, i):
    L = 30.0
    n = ns(L)
    drone = np.zeros((n, 2))
    for k, (m, a) in enumerate(((nm("E1"), 1.0), (nm("B1"), 0.6), (nm("E2"), 0.5), (nm("G3"), 0.12))):
        p = periodic_pad(mtof(m), L, "deep", seed=50 + k)
        drone += a * p * (1 + 0.3 * periodic_smooth_noise(n, 2 + k, rng))[:, None]
    # hollow resonance sweep, periodic in L
    drone /= np.std(drone) + 1e-9
    ev = np.zeros((ns(L + 4), 2))
    for k in range(9):
        tm = (k + rng.uniform(0.1, 0.8)) * L / 9
        f = mtof(nm(["E6", "B6", "G6", "F#6", "D7"][rng.integers(5)]))
        c = glass_chime(f, rng.uniform(0.3, 0.8), rng.uniform(0.3, 0.8), rng, 0.2)
        add_at(ev, pan(c, rng.uniform(-0.8, 0.8)), tm)
    ev = fold(ev, L)
    x = drone + 0.25 * ev / (np.max(np.abs(ev)) + 1e-9) * 4
    return circ_reverb(x, 0.4, 3.5, 4000, 1.4, seed=44)


@reg("ambient/drift_loop", kind="loop", level=AMB_LEVEL)
def amb_drift(rng, i):
    L = 36.0
    d = L + LOOP_XF
    wind = xfade_bed(stereo_bed(d, rng, _wind_gain(rng, d, 150, 450, 1.0, -5.0, 0.05), 4096), L)
    wind /= np.std(wind) + 1e-9
    n = ns(L)
    t = np.arange(n) / SR
    sub = np.sin(TWO_PI * int_freq(36.0, L) * t) * (0.6 + 0.4 * periodic_smooth_noise(n, 2, rng))
    x = wind + 0.15 * np.stack([sub, sub], axis=1)
    return circ_reverb(x, 0.25, 3.0, 2500, 1.5, seed=45)


def _inhale_choir(rng, d=3.2, chord=("D3", "F3", "A3", "E4")):
    n = ns(d)
    ch = np.zeros((n, 2))
    for k, nn in enumerate(chord):
        smp = padsynth(nf(nn), "choir_a" if k % 2 else "choir_o", seed=60 + k)
        ch += smp[(np.arange(n) + k * 5000) % len(smp)]
    t = tt(d)
    e = (np.clip(t / d, 0, 1) ** 2.4) * env_swell(d, 0.0, 0.03)
    br = _breath(d, rng, [(0, 0), (d * 0.97, 1), (d, 0)], "u", 1.4, 300)
    br = hp(br, 500)
    y = ch / (np.std(ch) + 1e-9) * e[:, None] + 0.45 * np.stack([br, br], axis=1) * e[:, None]
    return y


@reg("ambient/fracture_loop", kind="loop", level=AMB_LEVEL)
def amb_fracture(rng, i):
    L = 30.0
    n = ns(L)
    t = np.arange(n) / SR
    sub = (np.sin(TWO_PI * int_freq(36.7, L) * t) + 0.8 * np.sin(TWO_PI * int_freq(37.1, L) * t)
           + 0.4 * np.sin(TWO_PI * int_freq(73.4, L) * t) + 0.15 * np.sin(TWO_PI * int_freq(110.0, L) * t))
    sub *= 1 + 0.2 * periodic_smooth_noise(n, 3, rng)
    drone = np.stack([sub, sub], axis=1) / (np.std(sub) + 1e-9)
    ev = np.zeros((ns(L + 6), 2))
    chords = [("D3", "F3", "A3", "E4"), ("C3", "F3", "G3", "D4"), ("D3", "G3", "A3", "F4"), ("Bb2", "F3", "A3", "D4")]
    for k in range(4):
        add_at(ev, _inhale_choir(rng, 3.4, chords[k]), 2.0 + 7.5 * k)
    ev = fold(ev, L)
    x = drone + 0.5 * ev / (np.std(ev) + 1e-9)
    return circ_reverb(x, 0.45, 4.0, 3000, 1.6, seed=46)


# --- additions (mono one-shots)

@reg("ambient/chorus_reach_add1", kind="add", level=ADD_LEVEL)
def add_chorus1(rng, i):
    x = np.zeros(ns(3.0))
    add_at(x, glass_chime(nf("A5"), 0.6, 1.2, rng, 0.05), 0.0)
    add_at(x, glass_chime(nf("E6"), 0.5, 1.2, rng, 0.05), 0.35)
    return reverb(x, 0.35, 2.5, 6000, 1.0, seed=47)


@reg("ambient/chorus_reach_add2", kind="add", level=ADD_LEVEL)
def add_chorus2(rng, i):
    d = 3.5
    x = harmonic(np.full(ns(d), 98.0), [1, 0.5, 0.3, 0.15, 0.08], rng) * env_swell(d, 1.2, 1.6)
    return reverb(lp(x, 600), 0.4, 3.0, 2000, 1.3, seed=47)


@reg("ambient/stillwater_add1", kind="add", level=ADD_LEVEL)
def add_stillwater1(rng, i):
    x = np.zeros(ns(0.3))
    add_at(x, bubble(0.05, 900, 1700, 0.01, rng), 0.0)
    add_at(x, lp(white(0.04, rng), 3000) * env_ad(0.04, 0.001, 0.006), 0.0, 0.2)
    return reverb(lp(x, 4000), 0.6, 3.5, 3000, 1.4, seed=48)


@reg("ambient/stillwater_add2", kind="add", level=ADD_LEVEL)
def add_stillwater2(rng, i):
    d = 3.5
    x = _breath(d, rng, [(0, 0), (2.0, 1.0), (d, 0)], "o", 0.5)
    return reverb(lp(x, 1500), 0.3, 2.5, 2000, 1.2, seed=48)


@reg("ambient/fallen_spires_add1", kind="add", level=ADD_LEVEL)
def add_spires1(rng, i):
    x = stone_hits(1.2, rng, 7, 200, 900, (0.01, 0.03), span=0.9, shape=1.5)
    x += 0.5 * lp(crumble(1.2, rng, 30, 300, 2000, (0.002, 0.01), 1.5), 2000)
    return reverb(lp(x, 2500), 0.45, 3.0, 2000, 1.4, seed=49)


@reg("ambient/fallen_spires_add2", kind="add", level=ADD_LEVEL)
def add_spires2(rng, i):
    x = bell(1040, 1.4, rng, 0.8)
    return reverb(lp(x, 3500), 0.6, 3.5, 3000, 1.5, seed=49)


@reg("ambient/hoard_add1", kind="add", level=ADD_LEVEL)
def add_hoard1(rng, i):
    r = crumble(1.2, rng, 120, 2500, 9000, (0.0005, 0.003), 1.0, decay_amp=False)
    r *= env_swell(1.2, 0.3, 0.4)
    r = resample_speed(reverse(r), 0.55)
    return reverb(bp(r, 300, 5000), 0.3, 2.0, 3000, 1.0, seed=50)


@reg("ambient/hoard_add2", kind="add", level=ADD_LEVEL)
def add_hoard2(rng, i):
    m = resample_speed(_moo(rng, 2.0, 118), 0.7)
    return reverb(m, 0.45, 3.5, 2000, 1.4, seed=50)


@reg("ambient/underside_add1", kind="add", level=ADD_LEVEL)
def add_underside1(rng, i):
    x = np.zeros(ns(0.3))
    add_at(x, bubble(0.06, 1100, 2000, 0.012, rng), 0.0)
    y = reverb(lp(x, 5000), 0.7, 2.0, 4000, 1.0, seed=51)
    y = reverse(y)
    return fade(y, 0.05, 0.003)


@reg("ambient/underside_add2", kind="add", level=ADD_LEVEL)
def add_underside2(rng, i):
    x = np.zeros(ns(0.5))
    for m in (nm("E3"), nm("B3"), nm("G4")):
        add_at(x, piano(m, 0.5, 0.4, seed=3), 0.0)
    y = reverb(x, 1.0, 3.0, 3000, 1.3, seed=51, dry=0.2)
    y = reverse(y)
    return fade(y, 0.2, 0.01)


@reg("ambient/drift_add1", kind="add", level=ADD_LEVEL)
def add_drift1(rng, i):
    x = whale(3.0, [(0, 140), (1.2, 210), (3.0, 160)], rng, vib=3.2, vib_depth=0.01)
    return reverb(lp(x, 1200), 0.7, 5.0, 2000, 1.8, seed=52)


@reg("ambient/drift_add2", kind="add", level=ADD_LEVEL)
def add_drift2(rng, i):
    d = 4.0
    t = tt(d)
    x = (np.sin(TWO_PI * 55 * t) + 0.6 * np.sin(TWO_PI * 110.3 * t) + 0.3 * np.sin(TWO_PI * 165 * t)
         + 0.15 * np.sin(TWO_PI * 220.5 * t)) * env_swell(d, 1.5, 2.0)
    return reverb(x, 0.3, 3.0, 2000, 1.4, seed=52)


@reg("ambient/fracture_add1", kind="add", level=ADD_LEVEL)
def add_fracture1(rng, i):
    d = 1.8
    nz = white(d, rng)
    body = sum(reson(nz, f, 40) for f in (2100, 2950, 3800, 4700))
    slip = np.clip(smooth_noise(d, 35, rng), 0, None) ** 2
    x = body / (np.std(body) + 1e-9) * slip * env_swell(d, 0.2, 0.5)
    x += 0.3 * glass_pings(d, rng, 5, 2000, 4500, (0.03, 0.08), span=d * 0.8)
    return reverb(x, 0.35, 2.5, 6000, 1.2, seed=53)


@reg("ambient/fracture_add2", kind="add", level=ADD_LEVEL)
def add_fracture2(rng, i):
    d = 3.0
    x = hp(white(d, rng), 4000) * env_swell(d, 1.0, 1.4) * 0.5
    cr = (rng.random(ns(d)) < 0.002) * rng.standard_normal(ns(d)) * 3
    x += hp(cr, 2500) * env_swell(d, 0.8, 1.0)
    return reverb(x, 0.25, 2.0, 6000, 1.0, seed=53)


# =============================================================================
# Music
# =============================================================================

class Song:
    """Stereo arrangement buffer with a dry bus and a reverb send bus."""

    def __init__(self, d, tail=10.0):
        self.d = d
        self.n = ns(d + tail)
        self.dry = np.zeros((self.n, 2))
        self.send = np.zeros((self.n, 2))

    def add(self, x, t, p=0.0, gain=1.0, send=0.35):
        if x.ndim == 1:
            x = pan(x, p)
        add_at(self.dry, x, t, gain)
        if send > 0:
            add_at(self.send, x, t, gain * send)

    def render(self, rt60=4.0, damp=4500.0, size=1.4, seed=1, wet=1.0):
        w = reverb(self.send, wet, rt60, damp, size, seed, stereo=True, dry=0.0)
        out = self.dry + w[: self.n]
        return out

    def render_loop(self, L, rt60=3.0, damp=4500.0, size=1.2, seed=1):
        dry = fold(self.dry, L)
        send = fold(self.send, L)
        n = len(send)
        w = reverb(np.concatenate([send, send]), 1.0, rt60, damp, size, seed, stereo=True, dry=0.0)
        return dry + w[n: 2 * n]


def roll_chord(song, notes, t, hold, vel=0.35, spread=0.07, p=0.0, gain=1.0, send=0.4, seed=0):
    for k, m in enumerate(sorted(notes)):
        x = piano(m, vel * (1 - 0.04 * k), hold - spread * k, seed=seed + k)
        song.add(x, t + spread * k, p + 0.15 * (k / max(1, len(notes) - 1) - 0.5), gain, send)


# --- A dorian helpers shared by "Ladders to Nowhere" and "Stillness"
DORIAN = [0, 2, 3, 5, 7, 9, 10]


def dor(idx, root=57):
    return root + 12 * (idx // 7) + DORIAN[idx % 7]


def ladder_cell(top):
    """Descending three-note cell: top, one step down, three steps down."""
    return [dor(top), dor(top - 1), dor(top - 3)]


CELL_RHYTHM = [1.0, 0.5, 2.5]   # beats; one 4-beat bar per cell
LADDER_P1 = [11, 10, 9, 8]      # E5 D5 B4 | D5 C5 A4 | C5 B4 G4 | B4 A4 F#4  (ends unresolved)
LADDER_P2 = [14, 13, 12, 11]    # A5 G5 E5 | G5 F#5 D5 | F#5 E5 C5 | E5 D5 B4
CH = {
    "Am9": ["A2", "E3", "G3", "B3", "C4"],
    "Cmaj7": ["C3", "G3", "B3", "D4"],
    "Gadd9": ["G2", "D3", "A3", "B3"],
    "D6/F#": ["F#2", "D3", "A3", "B3"],
    "Gmaj7": ["G2", "D3", "F#3", "B3"],
    "Cmaj7#11": ["C3", "G3", "B3", "E4", "F#4"],
    "Em9": ["E2", "B2", "D3", "F#3", "G3"],
    "Amaj": ["A2", "E3", "A3", "C#4", "E4"],
    "E": ["E2", "B2", "G#3", "B3"],
}
P1_CHORDS = ["Am9", "Cmaj7", "Gadd9", "D6/F#"]
P2_CHORDS = ["Am9", "Gmaj7", "Cmaj7#11", "Em9"]


def chord_m(name, shift=0):
    return [nm(n_) + shift for n_ in CH[name]]


def play_cells(song, cells, t0, spb, vel=0.5, shift=0, p=0.1, gain=1.0, rising=False, send=0.45,
               double=None):
    for b, top in enumerate(cells):
        notes = ladder_cell(top)
        if rising:
            notes = notes[::-1]
        tb = t0 + b * 4 * spb
        acc = 0.0
        for k, (m, du) in enumerate(zip(notes, CELL_RHYTHM)):
            v = vel * (1.0 if k == 0 else 0.85)
            song.add(piano(m + shift, v, du * spb + 0.5, seed=b), tb + acc * spb, p, gain, send)
            if double is not None:
                song.add(double(m + shift, du * spb), tb + acc * spb, -p, gain, send + 0.2)
            acc += du


@reg("music/ladders_to_nowhere", kind="music", level=-16.0)
def mus_ladders(rng, i):
    bpm = 46.0
    spb = 60.0 / bpm
    bar = 4 * spb
    song = Song(28 * bar, 12.0)
    # opening: bowed metal and a cold pad
    song.add(bowed_metal(nf("A3"), 18.0, rng, 4.0, 6.0, 0.4), 0.0, -0.3, 0.10, 0.6)
    song.add(pad_chord([nm("A2"), nm("E3")], 20.0, "cold", 5.0, 6.0, seed=70), 0.0, 0, 0.35, 0.5)

    def chords(names, b0, shift=0, vel=0.32):
        for k, nmx in enumerate(names):
            roll_chord(song, chord_m(nmx, shift), (b0 + k) * bar, bar + 0.6, vel, seed=k)

    # phrase 1
    play_cells(song, LADDER_P1, 2 * bar, spb, 0.52)
    chords(P1_CHORDS, 2)
    roll_chord(song, chord_m("Am9"), 6 * bar, 2 * bar, 0.28)
    song.add(bowed_metal(nf("E4"), 14.0, rng, 4.0, 5.0, 0.5), 6 * bar, 0.35, 0.07, 0.6)
    # phrase 2
    play_cells(song, LADDER_P2, 8 * bar, spb, 0.5)
    chords(P2_CHORDS, 8)
    # pinged interlude
    song.add(piano(nm("E6"), 0.35, 3.0), 12 * bar, 0.4, 1.0, 0.6)
    song.add(piano(nm("B5"), 0.3, 3.0), 12 * bar + 2.5 * spb, -0.3, 1.0, 0.6)
    song.add(pad_chord(chord_m("Cmaj7"), 2 * bar + 4, "cold", 3.0, 4.0, seed=71), 12 * bar, 0, 0.25, 0.5)
    # phrase 1, an octave lower: further down the ladder
    song.add(bowed_metal(nf("A2"), 22.0, rng, 5.0, 7.0, 0.35), 14 * bar, -0.2, 0.12, 0.6)
    play_cells(song, LADDER_P1, 14 * bar, spb, 0.42, -12)
    chords(P1_CHORDS, 14, 0, 0.28)
    # phrase 2 breaks off after three cells (the fourth rung is missing)
    play_cells(song, LADDER_P2[:3], 18 * bar, spb, 0.48)
    chords(P2_CHORDS[:3], 18)
    roll_chord(song, chord_m("Em9"), 21 * bar, bar + 1.0, 0.25)
    # distant echo of the first rungs, high and faint
    play_cells(song, LADDER_P1[:2], 22 * bar, spb, 0.3, 12, 0.35, 1.0, send=0.8)
    # ending: no tonic, just the hanging D6/F# and a lone B
    song.add(bowed_metal(nf("F#3"), 20.0, rng, 4.0, 8.0, 0.4), 23 * bar, 0.2, 0.09, 0.6)
    roll_chord(song, chord_m("D6/F#"), 24 * bar, 2.5 * bar, 0.26)
    song.add(piano(nm("B5"), 0.3, 4.0), 26 * bar, 0.3, 1.0, 0.7)
    return song.render(4.5, 4000, 1.5, seed=70)


@reg("music/stillness", kind="music", level=-16.0)
def mus_stillness(rng, i):
    bpm = 48.0
    spb = 60.0 / bpm
    bar = 4 * spb
    song = Song(23 * bar, 14.0)

    def gh(m, d):
        return glass_harm(mtof(m + 12), d + 0.6, 0.12, 0.6, rng, 0.03) * 0.12

    def chords(names, b0, vel=0.3):
        for k, nmx in enumerate(names):
            roll_chord(song, chord_m(nmx), (b0 + k) * bar, bar + 0.8, vel, seed=k + 3)

    song.add(glass_harm(nf("A4"), 3 * bar, 4.0, 4.0, rng, 0.03), 0.0, 0.0, 0.08, 0.6)
    song.add(pad_chord(chord_m("Am9"), 3 * bar + 3, "warm", 5.0, 4.0, seed=80), 0.0, 0, 0.25, 0.5)
    # R1: phrase 1 of the ladder, cells reversed in order and pitch -> rising
    play_cells(song, LADDER_P1[::-1], 3 * bar, spb, 0.48, rising=True)
    chords(P1_CHORDS[::-1], 3)
    song.add(glass_harm(nf("E5"), 2 * bar + 2, 2.0, 3.0, rng, 0.03), 7 * bar, 0.2, 0.07, 0.6)
    roll_chord(song, chord_m("Am9"), 7 * bar, 2 * bar, 0.24)
    # R2: phrase 2 rising, the last rung finally lands on A major
    def r2(b0, vel, dbl):
        cells = LADDER_P2[::-1]
        play_cells(song, cells[:3], b0 * bar, spb, vel, rising=True, double=gh if dbl else None)
        chords(["Em9", "Cmaj7#11", "Gmaj7"], b0, 0.3)
        tb = (b0 + 3) * bar
        for m, at_, du in ((nm("E5"), 0.0, 1.0), (nm("G#5"), 1.0, 0.5), (nm("A5"), 1.5, 4.0)):
            song.add(piano(m, vel, du * spb + 0.8, seed=9), tb + at_ * spb, 0.1, 1.0, 0.45)
            if dbl:
                song.add(gh(m, du * spb), tb + at_ * spb, -0.1, 1.0, 0.6)
        roll_chord(song, chord_m("E"), tb, 1.5 * spb + 0.3, 0.28)
        roll_chord(song, chord_m("Amaj"), tb + 1.5 * spb, 2.5 * spb + 3.0, 0.33)
    r2(9, 0.48, False)
    song.add(pad_chord(chord_m("Amaj"), bar + 3, "warm", 2.0, 3.0, seed=81), 12.4 * bar, 0, 0.2, 0.5)
    r2(14, 0.52, True)
    song.add(pad_chord(chord_m("Em9"), 4 * bar, "warm", 4.0, 3.0, seed=82), 14 * bar, 0, 0.18, 0.5)
    # coda: open A major, rising arpeggio, glass on the tonic
    arp = ["A2", "E3", "A3", "C#4", "E4", "A4", "C#5", "E5"]
    for k, n_ in enumerate(arp):
        song.add(piano(nm(n_), 0.36 - 0.015 * k, 5 * bar - k * spb * 0.5, seed=k), 18 * bar + k * spb * 0.5,
                 -0.3 + 0.08 * k, 1.0, 0.5)
    song.add(pad_chord(chord_m("Amaj"), 5 * bar + 2, "warm", 3.0, 7.0, seed=83), 18 * bar, 0, 0.28, 0.5)
    song.add(glass_harm(nf("A5"), 4 * bar + 2, 3.0, 6.0, rng, 0.03), 19 * bar, 0.0, 0.08, 0.7)
    return song.render(4.0, 5000, 1.4, seed=80)


@reg("music/pale_pasture", kind="music", level=-16.0)
def mus_pale_pasture(rng, i):
    bpm = 70.0
    spb = 60.0 / bpm
    slot = 8 * spb
    V = {
        "D": ["D2", "A2", "F#3", "A3", "E4"], "Bm": ["B1", "F#2", "A3", "D4"],
        "G": ["G1", "D3", "B3", "F#4"], "A": ["A1", "E3", "B3", "C#4"], "Em": ["E2", "B2", "G3", "D4"],
        "Asus": ["A1", "E3", "A3", "D4"], "G/D": ["D2", "G3", "B3", "D4"],
    }
    plan = (["D", "G/D"] + ["D", "Bm", "G", "A"] + ["Em", "G", "Bm", "A"] +
            ["D", "Bm", "G", "A"] + ["Em", "G", "Bm", "Asus"] + ["D", "G/D", "D", "D"])
    song = Song(len(plan) * slot, 12.0)
    mel_a = [(0, "A4", 1), (1, "D5", 1), (2, "E5", 1.5), (3.5, "F#5", 0.5), (4, "E5", 3),
             (8, "D5", 1), (9, "B4", 1), (10, "D5", 1.5), (11.5, "E5", 0.5), (12, "F#5", 3),
             (16, "A5", 1.5), (17.5, "G5", 0.5), (18, "F#5", 1), (19, "E5", 1), (20, "D5", 3),
             (24, "B4", 1), (25, "C#5", 1), (26, "E5", 2), (28, "A4", 3)]
    mel_b = [(0, "G5", 1.5), (1.5, "F#5", 0.5), (2, "E5", 2), (4, "B4", 2), (6, "D5", 2),
             (8, "D5", 1), (9, "E5", 1), (10, "F#5", 2), (12, "B5", 1.5), (13.5, "A5", 0.5), (14, "F#5", 2),
             (16, "F#5", 1.5), (17.5, "E5", 0.5), (18, "D5", 2), (20, "F#5", 1), (21, "E5", 1), (22, "D5", 2),
             (24, "C#5", 1), (25, "E5", 1), (26, "A5", 2), (28, "E5", 4)]
    for s, name in enumerate(plan):
        t0 = s * slot
        notes = sorted(nm(x) for x in V[name])
        # warm pad (upper chord tones)
        song.add(pad_chord(notes[1:], slot + 2.5, "warm", 2.0, 2.5, seed=90 + s % 4), t0 - 0.5, 0, 0.22, 0.4)
        # left hand: slow broken chord over two bars
        if 1 <= s <= len(plan) - 2:
            pat = [(0, 0), (1, 1), (2, 2), (3, 3), (4, 0), (5, 2), (6, 1), (7, 3)]
            for beat, k in pat:
                k = min(k, len(notes) - 1)
                m = notes[k] + (12 if notes[k] < nm("E2") and k == 0 else 0)
                song.add(piano(m, 0.27 if beat % 4 else 0.33, 2.2 * spb + (1.5 if beat in (3, 7) else 0), seed=beat),
                         t0 + beat * spb, -0.25 + 0.08 * k, 1.0, 0.35)
    sections = [(2, mel_a, 0.5, False), (6, mel_b, 0.48, False), (10, mel_a, 0.52, True), (14, mel_b, 0.42, True)]
    for s0, mel, vel, chimes in sections:
        for beat, n_, du in mel:
            if mel is mel_b and s0 == 14 and beat in (1.5, 13.5, 17.5):
                continue      # sparser final statement
            t = s0 * slot + beat * spb
            song.add(piano(nm(n_), vel * (1.05 if beat % 4 == 0 else 0.92), du * spb + 0.4, seed=int(beat)),
                     t, 0.15, 1.0, 0.4)
            if chimes and beat % 8 == 0:
                song.add(glass_chime(mtof(nm(n_) + 12), 0.18, 2.0, rng, 0.05), t, -0.35, 1.0, 0.6)
    # glass chimes in intro and outro
    for s, n_ in ((0, "F#6"), (0.5, "A6"), (1, "D7"), (1.5, "E6"), (18, "A6"), (18.75, "F#6"),
                  (19.5, "D6"), (20.25, "E6"), (21, "A6"), (21.6, "D7")):
        song.add(glass_chime(nf(n_), 0.22, 2.2, rng, 0.05), s * slot + 0.3, rng.uniform(-0.6, 0.6), 1.0, 0.65)
    song.add(piano(nm("D5"), 0.35, 6.0), 20 * slot, 0.1, 1.0, 0.5)
    song.add(piano(nm("A4"), 0.3, 6.0), 20 * slot + 1.5 * spb, -0.1, 1.0, 0.5)
    return song.render(3.2, 5500, 1.3, seed=90)


@reg("music/undersong", kind="music", level=-16.0)
def mus_undersong(rng, i):
    bpm = 72.0
    spb = 60.0 / bpm
    slot = 8 * spb
    prog = ["Em9", "Cmaj7", "Am9", "Bsus4"]
    V = {"Em9": ["E2", "B2", "F#3", "G3", "D4"], "Cmaj7": ["C2", "G2", "B2", "E3", "B3"],
         "Am9": ["A1", "E2", "B2", "C3", "G3"], "Bsus4": ["B1", "F#2", "E3", "A3"]}
    swells = {"Em9": ["B4", "F#5"], "Cmaj7": ["G4", "E5"], "Am9": ["C5", "B4"], "Bsus4": ["F#4", "E5"]}
    nslots = 22
    song = Song(nslots * slot, 12.0)
    for s in range(nslots):
        name = prog[s % 4]
        t0 = s * slot
        notes = [nm(x) for x in V[name]]
        g = 0.3 if 1 <= s <= nslots - 2 else 0.2
        song.add(pad_chord(notes[1:], slot + 3.0, "teal", 2.5, 3.0, seed=100 + s % 4), t0 - 0.5, 0, g, 0.5)
        # soft pulses: lub-dub on each bar, from slot 2 to slot 19
        if 2 <= s <= 19:
            for b in range(2):
                for off, a in ((0.0, 1.0), (0.32, 0.6)):
                    p = lp(thump(0.6, mtof(notes[0] + 12) * 1.3, mtof(notes[0] + 12), 0.16, 0.02), 300)
                    song.add(p, t0 + b * 4 * spb + off, 0.0, 0.45 * a, 0.15)
        # reversed piano notes swelling into themselves
        if 1 <= s <= 20:
            picks = swells[name] if s % 3 == 0 else swells[name][:1]
            for k, n_ in enumerate(picks):
                m = nm(n_)
                tn = t0 + (4 + 2 * k) * spb
                fwd = piano(m, 0.45, 2.5, seed=s)
                rev = reverse(fwd[: ns(2.2)])
                rev = rev * np.linspace(0.0, 1.0, len(rev)) ** 1.5
                pp = 0.35 * (1 if (s + k) % 2 else -1)
                song.add(rev, tn - len(rev) / SR, pp, 0.8, 0.6)
                song.add(piano(m, 0.32, 2.0, seed=s), tn, pp, 1.0, 0.6)
    return song.render(4.5, 4500, 1.5, seed=100)


@reg("music/below_the_bottom", kind="music", level=-16.0)
def mus_below(rng, i):
    seg = 15.0
    chords = [["C2", "G2", "D3", "Eb3", "G3"], ["Ab1", "Eb2", "C3", "G3"], ["F1", "C2", "Ab2", "Eb3", "G3"],
              ["G1", "D2", "C3", "G3"], ["C2", "G2", "Eb3", "Bb3"], ["Bb1", "Eb2", "G2", "D3"],
              ["Ab1", "Eb2", "C3", "D3", "G3"], ["G1", "D2", "C3", "D3"], ["C2", "G2", "D3", "Eb3"],
              ["C2", "G2", "C3"]]
    song = Song(len(chords) * seg, 12.0)
    for k, ch in enumerate(chords):
        t0 = k * seg - (3.0 if k else 0.0)
        d = seg + 6.0 if k < len(chords) - 1 else seg + 4
        for j, n_ in enumerate(ch):
            kind = "deep" if j < 2 else "cold"
            x = pad_note(nf(n_), d, kind, 6.0, 6.0, seed=110 + k * 7 + j, width=1.5)
            song.add(x, t0, 0, 0.32 if j < 2 else 0.22, 0.5)
        if k in (0, 4, 8):
            t = tt(seg + 4)
            sub = np.sin(TWO_PI * nf("C1") * t) * env_swell(seg + 4, 6, 6)
            song.add(sub, t0, 0, 0.25, 0.0)
    calls = [(10, [(0, "G3"), (1.5, "C4"), (3.5, "Bb3")]), (31, [(0, "Eb4"), (2.0, "D4"), (4.0, "C4")]),
             (50, [(0, "C4"), (1.2, "G4"), (3.0, "F4"), (4.5, "Eb4")]), (70, [(0, "D4"), (2.5, "G3")]),
             (88, [(0, "Bb3"), (1.5, "Eb4"), (3.8, "D4")]), (106, [(0, "G3"), (2.0, "Ab3"), (4.0, "G3")]),
             (124, [(0, "C4"), (1.8, "Eb4"), (4.5, "C4")])]
    for k, (t0, pts) in enumerate(calls):
        d = pts[-1][0] + 1.2
        fp = [(a, nf(b) * 0.5) for a, b in pts] + [(d, nf(pts[-1][1]) * 0.5 * 0.97)]
        x = whale(d, fp, rng, vib=3.3, vib_depth=0.009, fscale=0.75, breath=0.015, sub=0.1)
        song.add(lp(x, 1500), t0, 0.55 if k % 2 else -0.55, 0.5, 0.9)
    return song.render(6.0, 3000, 1.8, seed=110)


@reg("music/hold_still", kind="music", level=-21.0)
def mus_hold_still(rng, i):
    d = 116.0
    pts = [(0, "E5"), (24, "E5"), (30, "F#5"), (50, "F#5"), (56, "D5"), (76, "D5"), (82, "C#5"),
           (100, "C#5"), (106, "B4"), (d, "B4")]
    f = curve(d, [(a, nf(b)) for a, b in pts], log=True)
    x = glass_harm(f, d, 8.0, 10.0, rng, 0.025)
    amp = curve(d, [(0, 1), (36, 1), (42, 0.12), (48, 0.9), (86, 0.9), (92, 0.1), (97, 0.85), (d, 1)])
    x *= amp
    song = Song(d, 10.0)
    song.add(x, 0.0, 0.0, 1.0, 0.8)
    # a faint lower fifth appears once, like an afterimage
    song.add(glass_harm(nf("A4"), 22.0, 8.0, 10.0, rng, 0.02), 58.0, -0.4, 0.25, 0.9)
    return song.render(6.0, 6000, 1.8, seed=120)


@reg("music/nothing_holds", kind="music", level=-16.0)
def mus_nothing_holds(rng, i):
    d = 60.0
    song = Song(d, 8.0)
    crack = np.zeros(ns(4.0))
    add_at(crack, _crack(rng, 0.3, 90, 300, 7000), 0.0, 0.7)
    add_at(crack, thump(3.0, 70, 30, 0.8, 0.08), 0.0, 1.0)
    add_at(crack, lp(crumble(3.0, rng, 120, 150, 2000, (0.002, 0.01), 1.8), 2000), 0.05, 0.4)
    song.add(crack, 0.3, 0.0, 0.55, 0.5)
    entries = [("D2", 2.0, "deep"), ("A2", 8.0, "cold"), ("F3", 14.0, "cold"), ("C4", 20.0, "cold"),
               ("E4", 26.0, "cold"), ("A4", 32.0, "glass")]
    for k, (n_, t0, kind) in enumerate(entries):
        dd = 58.0 - t0
        x = pad_note(nf(n_), dd, kind, 6.0, 9.0, seed=130 + k)
        song.add(x, t0, 0, 0.3 if kind != "glass" else 0.18, 0.5)
    song.add(bowed_metal(nf("A2"), 40.0, rng, 8.0, 10.0, 0.35), 9.0, -0.3, 0.08, 0.6)
    song.add(bowed_metal(nf("E4"), 28.0, rng, 6.0, 9.0, 0.5), 27.0, 0.3, 0.05, 0.6)
    for t0, n_ in ((36.0, "D5"), (40.5, "E5"), (44.5, "F5"), (48.0, "A5")):
        song.add(piano(nm(n_), 0.35, 5.0), t0, 0.2, 1.0, 0.55)
    y = song.render(5.0, 4000, 1.6, seed=130)
    return y * curve(len(y) / SR, [(0, 1), (50, 1), (len(y) / SR, 0.0)])[:, None] ** 0.5


# ---------------------------------------------------------------- The Unmade (loop layers)

UNMADE_BPM = 90.0
UNMADE_L = 64.0                     # 24 bars of 4/4 at 90 bpm
_UNMADE_CACHE = {}


def _unmade_layers():
    if "layers" in _UNMADE_CACHE:
        return _UNMADE_CACHE["layers"]
    rng = np.random.default_rng(seed_of("music/unmade"))
    spb = 60.0 / UNMADE_BPM
    bar = 4 * spb
    L = UNMADE_L
    n = ns(L)
    roots = ["D", "D", "Bb", "A", "D", "Eb"]           # one per 4-bar block
    root_hz = {"D": nf("D2"), "Bb": nf("Bb1"), "A": nf("A1"), "Eb": nf("Eb2")}
    choir = {"D": ["D3", "F3", "A3"], "Bb": ["Bb2", "D3", "F3", "A3"], "A": ["A2", "E3", "C#4"],
             "Eb": ["Eb3", "G3", "Bb3", "D4"]}
    high = {"D": "A5", "Bb": "Bb5", "A": "A5", "Eb": "Bb5"}

    # ---- layer 1: pulsing sub-bass, ticking glass, faint cold drone
    s1 = Song(L, 8.0)
    for b in range(24):
        r = roots[b // 4]
        f = root_hz[r]
        for beat, a in ((0, 1.0), (1, 0.55), (2, 0.75), (3, 0.55)):
            p = thump(0.7, f * 1.25, f, 0.24, 0.015, 0.008) + 0.7 * thump(0.7, f * 0.6, f * 0.5, 0.3, 0.015, 0.01)
            p += 0.25 * thump(0.7, f * 2.4, f * 2, 0.12, 0.015, 0.008)
            s1.add(lp(p, 400), b * bar + beat * spb, 0.0, 0.55 * a, 0.05)
        if b % 2 == 1:
            p = thump(0.5, f * 1.25, f, 0.18, 0.015, 0.008)
            s1.add(lp(p, 400), b * bar + 3.5 * spb, 0.0, 0.3, 0.05)
    tick_notes = [nm(x) for x in ("D6", "A5", "Eb6", "F6", "C6", "Bb5")]
    for blk in range(6):
        k_hits = [5, 7, 5, 6, 7, 5][blk]
        patt = [(j * k_hits) % 16 < k_hits for j in range(16)]
        rot = blk * 3
        patt = patt[rot:] + patt[:rot]
        for b in range(4):
            for j in range(16):
                if not patt[j]:
                    continue
                tm = (blk * 4 + b) * bar + j * spb / 4
                m = tick_notes[int(rng.integers(len(tick_notes)))]
                acc = 1.0 if j % 4 == 0 else 0.6
                c = glass_chime(mtof(m), 0.2 * acc, 0.1, rng, 0.4)
                s1.add(c, tm, 0.4 if j % 2 else -0.4, 0.9, 0.35)
    drone = periodic_pad(nf("D2"), L, "cold", seed=140) + 0.6 * periodic_pad(nf("A2"), L, "cold", seed=141)
    l1 = s1.render_loop(L, 2.5, 4500, 1.2, seed=140) + 0.06 * drone

    l1 = l1 * dbg(-19.0 - lufs(l1))

    # ---- layer 2: breathing choir and falling-stone percussion
    s2 = Song(L, 8.0)
    s2b = Song(L, 8.0)
    for blk in range(6):
        r = roots[blk]
        d = 4 * bar
        ch = np.zeros((ns(d + 1.0), 2))
        for k, n_ in enumerate(choir[r]):
            smp = padsynth(nf(n_), "choir_a" if k % 2 else "choir_o", seed=150 + k)
            ch += smp[(np.arange(len(ch)) + 4000 * k) % len(smp)]
        t = tt(d + 1.0)
        breath_env = np.where(t < d / 2, 0.15 + 0.85 * (t / (d / 2)) ** 1.5,
                              0.15 + 0.85 * np.exp(-(t - d / 2) / (d / 5)))
        breath_env *= env_swell(d + 1.0, 0.3, 1.0)
        ch = ch / (np.std(ch) + 1e-9) * breath_env[:, None]
        br = _breath(d + 1.0, rng, [(0, 0.1), (d / 2, 1.0), (d, 0.1), (d + 1.0, 0)], "o", 1.0, 200)
        ch += 0.35 * np.stack([br, br], axis=1)
        s2.add(ch, blk * d, 0, 0.35, 0.6)
    for b in range(24):
        hits = [1.75, 3.0] if b % 2 == 0 else [1.0, 2.5, 3.25]
        for hb in hits:
            tm = b * bar + hb * spb
            st = np.zeros(ns(1.2))
            add_at(st, whoosh(0.18, rng, [(0, 900), (0.18, 300)], [(0, 0), (0.18, 1)], 0.8) * 0.15, 0.0)
            add_at(st, stone_hits(0.3, rng, 1, 250, 600, (0.015, 0.03), span=0.001), 0.18, 1.0)
            add_at(st, thump(0.3, 110, 70, 0.06), 0.18, 0.6)
            tb = 0.18
            gap = 0.14
            for k in range(3):
                tb += gap
                gap *= 0.65
                add_at(st, stone_hits(0.2, rng, 1, 400, 1100, (0.006, 0.015), span=0.001), tb, 0.45 * 0.6 ** k)
            s2b.add(lp(st, 5000), tm - 0.18, rng.uniform(-0.5, 0.5), 0.5, 0.4)
    la = s2.render_loop(L, 3.0, 4000, 1.4, seed=150)
    lb = s2b.render_loop(L, 2.5, 4000, 1.2, seed=151)
    la = la * dbg(-21.0 - lufs(la))
    lb = limiter(lb * dbg(-23.0 - lufs(lb)), -10.0, circular=True)
    l2 = la + lb

    # ---- layer 3: distorted high sustained tone
    seg = 4 * bar
    tpts = []
    for blk in range(6):
        fz = nf(high[roots[blk]])
        tpts += [(blk * seg + 0.0, fz), (blk * seg + seg - 0.35, fz)]
    tpts.append((L, nf(high[roots[0]])))
    t = np.arange(n) / SR
    f = at(t, tpts, log=True)
    f = f * (1 + 0.006 * np.sin(TWO_PI * 5.0 * t))     # 320 whole vibrato cycles per loop
    cyc = np.sum(f) / SR
    f = f * (round(cyc) / cyc)                             # integer cycles -> seamless loop
    ph = phase_of(f) - TWO_PI * f[0] / SR
    tone = np.sin(ph) + 0.5 * np.sin(2 * ph + 0.3) + 0.3 * np.sin(3 * ph + 1.1)
    tone = softclip(0.9 * tone, 3.0)
    tone = np.concatenate([tone, tone])
    tone = lp(lp(tone, 4500), 6000)[n:]
    trem = 1 + 0.12 * np.sin(TWO_PI * 1.5 * t)           # 96 cycles per loop
    tone = tone * trem
    tone_st = np.stack([tone, np.roll(tone, ns(0.011))], axis=1)
    l3 = circ_reverb(tone_st / (np.std(tone_st) + 1e-9), 0.4, 3.0, 5000, 1.2, seed=160)
    l3 = l3 * dbg(-20.5 - lufs(l3))

    _UNMADE_CACHE["layers"] = (l1, l2, l3)
    return l1, l2, l3


def _unmade_mix(k):
    l1, l2, l3 = _unmade_layers()
    mixes = [l1, l1 + l2, l1 + l2 + l3]
    g = dbg(-16.0 - lufs(mixes[1]))
    peak = max(np.max(np.abs(m)) for m in mixes) * g
    if peak > dbg(-1.5 + 4.0):        # allow at most ~4 dB of limiting
        g *= dbg(-1.5 + 4.0) / peak
    return limiter(mixes[k] * g, -1.5, circular=True)


for _k in range(3):
    reg(f"music/unmade_{_k + 1}", kind="musicloop", level=None)(lambda rng, i, k=_k: _unmade_mix(k))


# =============================================================================
# Expansion 1.2-2.0: the Warden Cycle, the Gap, cities, living systems,
# the Zenith, the Undertow and the Restoration
# =============================================================================

def _u(x):
    """Unit-RMS copy, for mixing layers by ratio."""
    return x / (np.std(x) + 1e-9)


def _pk(x):
    """Unit-peak copy."""
    return x / (np.max(np.abs(x)) + 1e-9)


def _tame(x, k=2.5):
    """Unit-RMS copy with its spikes soft-limited to about k x RMS (crackles, grit), so a
    transient layer can sit at a useful level without dictating the peak normalisation."""
    return k * np.tanh(_u(x) / k)


def _whisper(d, rng, v1="a", v2=None, fscale=1.0, fscale2=None, amp_pts=None, lo=250.0, hiss=0.5):
    """Whispered (unvoiced) vowel: noise through time-varying formants plus a little sibilance."""
    v2 = v2 or v1
    fs2 = fscale if fscale2 is None else fscale2

    def g(F, T):
        w = np.clip(T / d, 0, 1)
        sc = fscale * (1 - w) + fs2 * w
        fg = (1 - w) * formant_gain(F / sc, v1, 1.0, 0.03, 1.5) + w * formant_gain(F / sc, v2, 1.0, 0.03, 1.5)
        return (fg + hiss * 0.25 * band(F, 5500, 0.45)) * (F > lo)
    x = _u(noise_shaped(d, rng, g, 1024))
    if amp_pts:
        x = x * curve(d, amp_pts)
    return x


def _dragon_voice(d, rng, pts, v1="a", v2="o", fscale=0.55, rough=0.3, drive=1.6, breath=0.15,
                  vib=0.0, vib_rate=4.0):
    """Large-creature call: low voiced source with period-doubling roughness, dark formants."""
    t = tt(d)
    f = curve(d, pts, log=True)
    if vib:
        f = f * (1 + vib * np.clip(t / (0.5 * d), 0, 1) * np.sin(TWO_PI * vib_rate * t))
    x = voice(d, f, rng, v1, v2, 1.05, breath, fscale, fscale * 1.12, 0.008, nmax=120)
    am = 1 + rough * np.sin(0.5 * phase_of(f) + 0.3) + 0.5 * rough * smooth_noise(d, 28, rng)
    x = softclip(_pk(x * np.clip(am, 0, None)), drive)
    return x * env_swell(d, min(0.18, 0.2 * d), 0.45 * d)


BRONZE_RATIOS = [0.5, 1.0, 1.183, 1.506, 2.0, 2.514, 2.662, 3.011, 4.166, 5.433]
BRONZE_AMPS = [0.18, 0.45, 0.4, 0.3, 1.0, 0.5, 0.4, 0.32, 0.2, 0.1]
BRONZE_TAUS = [1.3, 1.0, 0.8, 0.5, 0.75, 0.42, 0.36, 0.27, 0.18, 0.11]


def _bronze_bell(f, decay, rng, bright=0.6, split=0.0022):
    """Cast bronze bell (f = prime; the strike note is the nominal an octave up).

    Every partial is a slightly split pair, so old bronze warbles as it rings."""
    fr, ta, am = [], [], []
    for r, a, tau in zip(BRONZE_RATIOS, BRONZE_AMPS, BRONZE_TAUS):
        a = a * (1.0 if r <= 2.0 else bright)
        for s, w in ((-1, 0.6), (1, 0.4)):
            fr.append(f * r * (1 + s * split * rng.uniform(0.4, 1.0)))
            ta.append(decay * tau)
            am.append(a * w)
    ir = modes(decay * 6.0, fr, ta, am, rng)
    n = ns(0.003)
    exc = np.hanning(2 * n)[n:] * (1 + 0.4 * rng.standard_normal(n))
    exc = lp(exc, 3000 + 7000 * bright)
    y = _pk(excite(exc, ir))
    k = _pk(_clank(rng, f * 5.1, 0.012)) * 0.12
    y[: len(k)] += k
    return y


def _screech(d, rng, pts, rasp=0.5, noise=0.6, hpf=1200.0):
    """Raptor screech: bright harmonic tone and pitch-tracking noise band, rasped."""
    t = tt(d)
    f = curve(d, pts, log=True) * (1 + 0.01 * smooth_noise(d, 18, rng))
    x = _u(harmonic(f, [1.0, 0.75, 0.5, 0.3, 0.18, 0.1], rng))

    def g(F, T):
        fc = np.interp(T, t, f)
        return band(F, fc, 0.12) + 0.5 * band(F, 2 * fc, 0.1) + 0.08 * band(F, 1.5 * fc, 0.6)
    nz = _u(noise_shaped(d, rng, g, 512))
    am = np.clip(1 + rasp * smooth_noise(d, 110, rng), 0.0, None)
    y = (x + noise * nz) * am
    return hp(y, hpf) * env_swell(d, 0.025, 0.5 * d, 1.2)


def _fork(f, d, rng, beat=0.0, tau=1.0, clang=0.35):
    """Tuning fork: near-pure tone (two tines may beat), brief clang mode at ~6.27 f."""
    t = tt(d)
    p = rng.uniform(0, TWO_PI)
    x = np.sin(TWO_PI * f * t + p)
    if beat:
        x = x + 0.55 * np.sin(TWO_PI * (f + beat) * t + p + 0.5)
    x = x + 0.012 * np.sin(TWO_PI * 2 * f * t) + 0.004 * np.sin(TWO_PI * 3 * f * t)
    x = x * np.exp(-t / tau)
    x = x + clang * np.sin(TWO_PI * 6.27 * f * t) * np.exp(-t / 0.035)
    return x * (1 - np.exp(-t / 0.0015))


def _metal_creak(d, rng, rate_pts, scale=1.0, tau=0.07, amp_pts=None):
    """Stick-slip creak through stiff metal-plate modes (high Q, inharmonic)."""
    mf = [m * scale for m in (310, 523, 871, 1340, 2050, 2890)]
    x = creak(d, rng, rate_pts, mf, tau, 0.35, amp_pts)
    hull = reson(x, 95 * scale, 6)
    return x + 0.6 * _u(hull) * np.std(x)


def _spiral(d, rng, s_pts, f0=73.42, octs=6, centre=3.0, width=1.1, noise=0.5):
    """Shepard-Risset spiral: octave-spaced partials and noise bands gliding forever.

    s_pts: breakpoints of the spiral position in octaves (falling = sinking spiral)."""
    t = tt(d)
    s = curve(d, s_pts)
    x = np.zeros(len(t))
    for k in range(octs):
        oc = np.mod(k + s, octs)
        f = f0 * 2.0 ** oc
        a = np.exp(-0.5 * ((oc - centre) / width) ** 2)
        x += a * (np.sin(phase_of(f) + k) + 0.3 * np.sin(phase_of(f * 1.5) + 2 * k))
    sg = np.interp(np.linspace(0, d, 400), t, s)
    tg = np.linspace(0, d, 400)

    def g(F, T):
        sv = np.interp(T, tg, sg)
        out = 0
        for k in range(octs):
            oc = np.mod(k + sv, octs)
            out = out + np.exp(-0.5 * ((oc - centre) / width) ** 2) * band(F, f0 * 2.0 ** oc, 0.12)
        return out
    nz = noise_shaped(d, rng, g, 2048)
    return _u(x) + noise * _u(nz)


# ---------------------------------------------------------------- 1.2 the Warden Cycle

@reg(["event/warden/crystal_echo1", "event/warden/crystal_echo2"], level=-4.0)
def sfx_warden_crystal_echo(rng, i):
    d = 1.5
    f0, f1 = [(nf("E5"), nf("D5")), (nf("B4"), nf("A4"))][i]
    f = curve(d, [(0, f0 * 1.004), (0.45, f0), (d, f1)], log=True)      # the sigh: settle, then fall a step
    g = glass_harm(f, d, 0.25, 0.8, rng, 0.05) + 0.35 * glass_harm(f * 1.5, d, 0.4, 0.8, rng, 0.03)
    v1, v2 = [("a", "o"), ("e", "u")][i]
    wh = lp(_whisper(d, rng, v1, v2, 1.0 + 0.1 * i, 0.9, [(0, 0), (0.35, 1.0), (0.9, 0.7), (d, 0)]), 6500)
    glint = glass_pings(d, rng, 5, 2600, 5200, (0.05, 0.15), span=0.7, shape=1.5)
    x = (0.8 * _u(g) + 0.7 * wh) * env_swell(d, 0.05, 0.4) + 0.6 * _pk(glint)
    return reverb(x, 0.38, 3.0, 6000, 1.3, seed=200)


@reg("event/warden/cracks", level=-2.5)
def sfx_warden_cracks(rng, i):
    d = 2.0
    groan = creak(d, rng, [(0, 16), (0.8, 34), (1.4, 26), (d, 12)], [82, 141, 233, 377, 590], 0.06, 0.35,
                  [(0, 0), (0.25, 1), (1.4, 0.8), (d, 0)])
    groan = lp(_u(groan), 1100)
    nz = white(d, rng)
    body = lp(_u(reson(nz, 55, 10) + reson(nz, 92, 10) + reson(nz, 150, 8)), 300)
    body *= curve(d, [(0, 0), (0.4, 1), (1.6, 0.7), (d, 0)])
    crk = np.zeros(ns(d))
    tm = 0.12
    while tm < d - 0.25:
        add_at(crk, _crack(rng, 0.04, 8, 1500, 9000), tm, rng.uniform(0.25, 1.0) * (1 - 0.4 * tm / d))
        tm += rng.exponential(0.09) + 0.01
    fine = crumble(d, rng, 260, 2500, 10000, (0.0003, 0.0015), 1.0, decay_amp=False)
    fine *= curve(d, [(0, 0.2), (0.5, 1), (1.6, 0.6), (d, 0)])
    x = 0.8 * groan + 0.7 * body + 0.6 * _tame(crk) + 0.25 * _tame(fine)
    return reverb(lp(x, 9000), 0.25, 2.2, 2500, 1.4, seed=201)


@reg("event/warden/portal_hum", level=-3.0)
def sfx_warden_portal_hum(rng, i):
    d = 3.2
    t = tt(d)
    br = 0.5 - 0.5 * np.cos(TWO_PI * t / 1.6)          # two slow breaths
    br = 0.3 + 0.7 * br ** 1.3
    f = nf("D1") * (1 + 0.003 * smooth_noise(d, 0.8, rng))
    h = harmonic(f, [k ** -0.55 for k in range(1, 26)], rng) + 0.8 * harmonic(f * 1.0136, [1, 0.5, 0.3], rng)
    dark, bright = lp(h, 200), lp(h, 1100)
    x = dark + (bright - dark) * br ** 2                # brighter on each inhale
    grit = lp(colored(d, rng, -6), 120) * 0.5
    y = (_u(x) + 0.4 * _u(grit)) * br * env_swell(d, 0.35, 0.6)
    return reverb(y, 0.25, 2.0, 2500, 1.3, seed=202)


@reg("event/warden/tide_creep", level=-3.0)
def sfx_warden_tide_creep(rng, i):
    d = 2.2
    pitch = 2 ** (curve(d, [(0, -1.0), (d, 1.0)]) / 12)
    x = np.zeros(ns(d))
    for m, a in ((nm("D3"), 1.0), (nm("A3"), 0.8), (nm("D4"), 0.5), (nm("F4"), 0.4)):
        for det in (0.0, 0.004):                          # chorused pair: teal, watery
            x += a * harmonic(mtof(m) * pitch * (1 + det), [1, 0.45, 0.25, 0.12, 0.06], rng)
    x = lp(x, 2200)
    sw = whoosh(d, rng, [(0, 300), (d, 3200)], [(0, 0.1), (d * 0.85, 1), (d, 0.6)], 0.9)
    sh = _shimmer(d, rng, 3500, 9000, 13) * curve(d, [(0, 0), (d * 0.4, 0.3), (d * 0.9, 1), (d, 0.5)])
    pings = glass_pings(d, rng, 10, 2200, 5000, (0.05, 0.15), span=d * 0.9, shape=0.6)
    e = curve(d, [(0, 0), (d * 0.85, 1), (d, 0)]) ** 1.4
    y = (0.7 * _u(x) + 0.55 * _u(sw) + 0.25 * _u(sh)) * e + 0.5 * _pk(pings)
    return reverb(y, 0.3, 2.2, 5000, 1.2, seed=203)


@reg("event/warden/tide_recede", level=-4.0)
def sfx_warden_tide_recede(rng, i):
    d = 1.6
    pitch = 2 ** (curve(d, [(0, 0), (d, -1.5)]) / 12)
    x = np.zeros(ns(d))
    for m, a in ((nm("D3"), 1.0), (nm("A3"), 0.8), (nm("D4"), 0.5)):
        x += a * harmonic(mtof(m) * pitch, [1, 0.45, 0.25, 0.12], rng)
    third = mtof(nm("F4")) * pitch * 2 ** (curve(d, [(0, 0), (0.8, 0), (1.25, 1)]) / 12)   # minor -> major: relief
    x += 0.45 * harmonic(third, [1, 0.3, 0.1], rng)
    x = lp(x, 1800)
    sw = whoosh(d, rng, [(0, 2800), (d, 280)], [(0, 1), (d, 0)], 0.9)
    br = _breath(d, rng, [(0, 0.6), (0.3, 1), (d, 0)], "a", 0.6)
    e = curve(d, [(0, 0), (0.08, 1), (d, 0)])
    y = (0.75 * _u(x) + 0.45 * _u(sw) + 0.3 * _u(lp(br, 2500))) * e
    return reverb(y, 0.3, 2.0, 4500, 1.2, seed=203)


@reg("event/warden/shard_charge", level=-3.0)
def sfx_warden_shard_charge(rng, i):
    d = 1.0
    t = tt(d)
    f = 520 * 4.6 ** ((t / d) ** 1.5)
    tone = fm_tone(d, f, 2.0, 0.5 + 0.8 * t / d) + 0.4 * np.sin(phase_of(f * 1.5))
    trem = 1 - 0.4 * (0.5 + 0.5 * np.sin(phase_of(8 + 36 * (t / d) ** 2)))
    nz = whoosh(d, rng, [(0, 1500), (d, 7000)], [(0, 0), (d, 1)], 0.5)
    e = (t / d) ** 1.6 * env_swell(d, 0.0, 0.03)
    x = np.zeros(ns(d + 1.2))
    add_at(x, (0.7 * _u(tone) * trem + 0.4 * _u(nz)) * e, 0.0)
    add_at(x, 0.5 * glass_pings(d, rng, 14, 2500, 6000, (0.03, 0.08), span=0.6, shape=0.7, start=0.35), 0.0)
    add_at(x, glass_chime(f[-1] * 1.0, 0.9, 0.35, rng, 0.3), d - 0.02, 1.2)
    return reverb(x, 0.18, 1.2, 8000, 0.7, seed=204)


@reg("event/warden/learn", level=-2.5)
def sfx_warden_learn(rng, i):
    x = np.zeros(ns(3.2))
    roar = _dragon_voice(1.25, rng, [(0, 92), (0.25, 124), (0.8, 112), (1.25, 82)], "a", "o", 0.55, 0.35, 2.2, 0.25)
    roar = reverb(lp(roar, 1500), 0.55, 2.2, 2000, 1.4, seed=205, predelay=0.03)   # distant
    add_at(x, roar * curve(len(roar) / SR, [(0, 1), (0.9, 1), (1.6, 0.25), (len(roar) / SR, 0)]), 0.0, 1.0)
    d = 1.6
    gl = glass_harm(curve(d, [(0, nf("A4")), (1.0, nf("A5")), (d, nf("A5"))], log=True), d, 0.5, 0.6, rng, 0.04)
    add_at(x, 0.35 * _pk(gl), 0.45)
    for k, n_ in enumerate(["D6", "E6", "A6", "D7"]):
        add_at(x, glass_chime(nf(n_), 0.55 - 0.05 * k, 1.2, rng, 0.12), 0.85 + 0.11 * k)
    return reverb(x, 0.28, 1.8, 7000, 1.0, seed=205)


@reg("event/warden/gale", level=-1.5)
def sfx_warden_gale(rng, i):
    d = 1.6
    mid = whoosh(d, rng, [(0, 220), (0.15, 900), (0.6, 700), (d, 280)], [(0, 0), (0.08, 1), (0.5, 0.8), (d, 0)], 1.4, 40)
    mid *= np.clip(1 + 0.35 * smooth_noise(d, 14, rng), 0.2, 2)
    high = whoosh(d, rng, [(0, 1500), (0.2, 4200), (d, 2000)], [(0, 0), (0.1, 0.6), (d, 0)], 0.9)
    low = lp(colored(d, rng, -6), 180) * np.clip(1 + 0.5 * smooth_noise(d, 9, rng), 0.1, 3)
    low *= curve(d, [(0, 0), (0.06, 1), (0.7, 0.6), (d, 0)])
    x = _u(mid) + 0.35 * _u(high) + 0.7 * _u(low)
    add_at(x, thump(0.5, 62, 36, 0.12, 0.05), 0.0, 1.6)          # the wingbeat behind it
    return x


@reg("event/warden/dive_warning", level=-1.5)
def sfx_warden_dive_warning(rng, i):
    d = 1.5
    t = tt(d)
    rum = noise_shaped(d, rng, lambda F, T: band(F, 60 * 2.6 ** (T / d), 0.9) * (F > 25), 2048)
    sub = np.sin(phase_of(curve(d, [(0, 35), (d, 70)], log=True)))
    pf = curve(d, [(0, 110), (d, 220)], log=True)
    pres = lp(harmonic(pf, [0, 0.5, 0.4, 0.35, 0.25, 0.2, 0.15, 0.1], rng), 1500)   # cuts through small speakers
    mf = curve(d, [(0, 1200), (d, 2600)], log=True)
    mag = harmonic(mf, [1, 0.4, 0.2], rng) * (0.5 + 0.5 * np.sin(phase_of(10 + 22 * t / d)))
    sh = _shimmer(d, rng, 3000, 8000, 18)
    e = (t / d) ** 1.6 * env_swell(d, 0.0, 0.06)
    x = (0.9 * _u(rum) + 0.6 * sub + 0.55 * _u(pres) + 0.6 * _u(mag) + 0.3 * _u(sh)) * e
    return reverb(x, 0.2, 1.6, 4000, 1.2, seed=206)


@reg("event/warden/breach", level=-1.0)
def sfx_warden_breach(rng, i):
    d = 2.2
    x = np.zeros(ns(d))
    up = whoosh(d, rng, [(0, 150), (0.35, 1200), (1.0, 3600), (d, 2500)], [(0, 0), (0.12, 1), (0.6, 0.7), (d, 0)], 1.3, 40)
    add_at(x, _u(up), 0.0, 1.0)
    low = lp(colored(d, rng, -6), 300) * curve(d, [(0, 0), (0.1, 1), (1.2, 0.3), (d, 0)])
    add_at(x, _u(low), 0.0, 0.7)
    add_at(x, thump(1.0, 72, 32, 0.3, 0.06), 0.02, 2.2)
    spl = hp(crumble(0.9, rng, 220, 1500, 11000, (0.0004, 0.004), 1.8), 900)
    spl += 0.5 * hp(white(0.9, rng), 2500) * env_ad(0.9, 0.003, 0.12)
    add_at(x, _pk(spl), 0.06, 1.4)
    add_at(x, glass_pings(1.8, rng, 40, 1500, 6000, (0.05, 0.25), span=0.7, shape=1.6), 0.08, 1.8)
    gd = 1.6
    rise = 2 ** (curve(gd, [(0, -5), (0.6, 0), (gd, 0)]) / 12)
    light = sum(glass_harm(nf(n_) * rise, gd, 0.08, 1.0, rng, 0.05) for n_ in ("D6", "F#6", "A6"))
    add_at(x, 0.5 * _pk(light) * np.max(np.abs(x[: ns(1.0)])) / 3, 0.1)
    return reverb(x, 0.3, 2.4, 6000, 1.4, seed=207)


@reg("event/warden/dive", level=-2.0)
def sfx_warden_dive(rng, i):
    d = 2.0
    t = tt(d)
    w = whoosh(d, rng, [(0, 3500), (0.6, 1500), (d, 150)], [(0, 0), (0.5, 0.6), (1.4, 1), (d, 0)], 1.0, 40)
    wf = curve(d, [(0, 1400), (d, 230)], log=True)

    def g(F, T):
        return band(F, np.interp(T, t, wf), 0.08)
    whis = _u(noise_shaped(d, rng, g, 1024)) + 0.6 * np.sin(phase_of(wf))
    whis *= curve(d, [(0, 0), (0.6, 0.8), (1.3, 1), (d, 0)]) * (1 + 0.25 * np.sin(TWO_PI * 11 * t))
    rum = lp(colored(d, rng, -6), 160) * (t / d) ** 2 * env_swell(d, 0, 0.15)
    return _u(w) + 0.3 * _u(whis) + 0.6 * _u(rum)


@reg("event/warden/release_chime", level=-3.0)
def sfx_warden_release_chime(rng, i):
    d = 7.0
    x = np.zeros(ns(d))
    for k, (n_, tm) in enumerate((("A5", 0.0), ("F#5", 0.5), ("D6", 1.0), ("D5", 1.5))):  # four crystals
        b = mixdown(glass_chime(nf(n_), 0.75, 3.0, rng, 0.2), 0.25 * glass_harm(nf(n_), 5.5 - tm, 0.05, 2.5, rng, 0.03))
        add_at(x, b, tm, 1.0 - 0.06 * k)
    ch = pad_chord([nm("D3"), nm("A3"), nm("D4"), nm("F#4"), nm("A4")], 5.6, "warm", 1.6, 2.6, seed=210).mean(axis=1)
    add_at(x, ch * 0.75 * np.std(x[: ns(2.0)]) / (np.std(ch) + 1e-9), 1.2)
    x *= curve(d, [(0, 1), (5.2, 1), (d, 0)])
    return reverb(x, 0.35, 3.0, 6000, 1.4, seed=210)


@reg("event/warden/bow", level=-4.0)
def sfx_warden_bow(rng, i):
    d = 2.7
    x = np.zeros(ns(d))
    ex = lp(_breath(2.4, rng, [(0, 0), (0.25, 1), (1.0, 0.7), (2.4, 0)], "o", 0.38, 40), 1300)
    chest = reson(white(2.4, rng), 68, 7) * np.abs(ex)
    add_at(x, ex + 0.8 * _u(chest) * np.std(ex), 0.0)
    hm = voice(1.8, curve(1.8, [(0, 62), (1.8, 52)], log=True), rng, "u", "u", 1.6, 0.05, 0.5, 0.5, 0.004)
    add_at(x, 0.25 * _u(lp(hm, 500)) * env_swell(1.8, 0.3, 1.0) * np.std(ex), 0.1)
    add_at(x, 0.3 * glass_chime(nf("D6"), 0.6, 1.6, rng, 0.05) * np.max(np.abs(ex)), 0.75)
    add_at(x, 0.18 * glass_chime(nf("A6"), 0.6, 1.4, rng, 0.05) * np.max(np.abs(ex)), 0.95)
    return reverb(x, 0.3, 2.5, 4000, 1.4, seed=211)


@reg(["mob/elder_warden/call1", "mob/elder_warden/call2"], level=-3.0)
def sfx_elder_warden_call(rng, i):
    d = 3.0
    pts = [[(0, 82), (0.6, 110), (1.6, 123), (2.4, 104), (d, 92)],
           [(0, 98), (0.5, 92), (1.3, 131), (2.2, 117), (d, 87)]][i]
    v = _dragon_voice(d, rng, pts, "o", "u", 0.5, 0.12, 1.2, 0.06, vib=0.012, vib_rate=3.2)
    v = lp(v, 2200)
    x = np.zeros(ns(d + 2.0))
    add_at(x, v, 0.0)
    fe = pts[-1][1]
    tail = pad_note(fe * 2, 2.8, "warm", 0.9, 1.6, seed=212 + i).mean(axis=1)    # the warm tail
    tail += 0.6 * pad_note(fe * 3, 2.8, "warm", 1.1, 1.6, seed=214 + i).mean(axis=1)
    add_at(x, 0.35 * _u(tail) * np.std(v), 1.7)
    return reverb(x, 0.45, 3.5, 2500, 1.6, seed=212, predelay=0.04)


@reg("mob/lumen_heart/pulse", level=-5.0)
def sfx_lumen_heart_pulse(rng, i):
    d = 1.1
    t = tt(d)
    x = lp(thump(0.6, 82, 55, 0.12, 0.03), 300)
    x = pad_to(x, ns(d))
    sw = np.clip(t / 0.12, 0, 1) ** 1.5 * np.exp(-np.maximum(t - 0.12, 0) / 0.3)   # the throb
    cr = (glass_harm(740, d, 0.01, 0.2, rng, 0.0) + 0.5 * glass_harm(1110, d, 0.01, 0.2, rng, 0.0)) * sw
    sh = _shimmer(d, rng, 2500, 6000, 9) * sw * 0.12
    y = _pk(x) + 0.55 * _pk(cr) + _u(sh) * 0.06
    return reverb(y, 0.25, 1.4, 6000, 0.8, seed=213)


@reg("mob/lumen_heart/break", level=-2.0)
def sfx_lumen_heart_break(rng, i):
    x = np.zeros(ns(2.6))
    add_at(x, glass_pings(1.6, rng, 30, 700, 3000, (0.08, 0.4), span=0.35, shape=1.8,
                          ratios=(1.0, 2.756, 5.404, 8.933)), 0.0, 1.0)
    add_at(x, _crack(rng, 0.12, 40, 1000, 9000), 0.0, 1.2)
    add_at(x, thump(0.3, 150, 80, 0.06), 0.0, 0.5)
    for k, f in enumerate(np.geomspace(4200, 1300, 12)):          # falling sparkle
        add_at(x, glass_chime(f, 0.45 - 0.025 * k, 0.22, rng, 0.05), 0.28 + 0.085 * k)
    return reverb(x, 0.22, 1.6, 7000, 0.9, seed=214)


# ---------------------------------------------------------------- 1.3 the Gap

@reg("event/causeway_restore", level=-3.0)
def sfx_causeway_restore(rng, i):
    x = np.zeros(ns(4.2))
    gd = 1.9
    add_at(x, 0.8 * _tame(_grind(gd, rng, 100, 800, 18)) * env_swell(gd, 0.3, 0.5), 0.0, 1.0)
    for tm, a in ((1.72, 1.0), (1.98, 0.7)):
        add_at(x, 1.3 * _pk(stone_hits(0.4, rng, 3, 300, 900, (0.012, 0.03), span=0.02)), tm, a)
        add_at(x, thump(0.35, 95, 55, 0.08), tm, 1.3 * a)
    ig = _ignite(rng, 0.3)
    add_at(x, 0.4 * _pk(ig), 2.05)
    add_at(x, bell(1040, 1.0, rng, 0.75), 2.25, 1.5)
    add_at(x, bell(1560, 0.8, rng, 0.8), 2.25, 0.5)
    return reverb(x, 0.25, 2.0, 5000, 1.2, seed=220)


@reg("block/drift_buoy_pop", level=-4.0)
def sfx_drift_buoy_pop(rng, i):
    x = np.zeros(ns(1.0))
    add_at(x, bubble(0.07, 260, 620, 0.02, rng), 0.0, 1.0)
    tube = modes(0.3, [340, 694, 1061], [0.06, 0.04, 0.025], [1.0, 0.4, 0.2], rng)
    add_at(x, excite(lp(white(0.01, rng), 1500) * env_ad(0.01, 0.001, 0.003), tube) * 0.5, 0.0)
    add_at(x, glass_chime(3136, 0.22, 0.5, rng, 0.05), 0.03)
    return reverb(x, 0.15, 1.0, 6000, 0.6, seed=221)


# ---------------------------------------------------------------- 1.4 cities and navigation

@reg("mob/shulker_archivist/read", level=-5.0)
def sfx_archivist_read(rng, i):
    d = 1.6
    x = np.zeros(ns(d + 1.0))
    cr = creak(1.2, rng, [(0, 12), (0.6, 30), (1.2, 10)], [870, 1430, 2210, 3050], 0.012, 0.35,
               [(0, 0), (0.15, 1), (1.0, 0.6), (1.2, 0)])
    dust = crumble(1.2, rng, 60, 3000, 9000, (0.0003, 0.0015), 1.0, decay_amp=False)
    add_at(x, _pk(cr) + 0.25 * _pk(dust), 0.0)
    sh = _shimmer(1.3, rng, 3000, 9000, 7) * env_swell(1.3, 0.4, 0.7)
    add_at(x, 0.12 * _pk(sh), 0.35)
    for k in range(5):
        add_at(x, glass_chime(rng.uniform(2500, 4500), 0.09, 0.18, rng, 0.0), 0.45 + 0.2 * k + rng.uniform(0, 0.1))
    return reverb(x, 0.25, 1.4, 7000, 0.8, seed=222)


@reg("mob/shulker_archivist/trade", level=-4.0)
def sfx_archivist_trade(rng, i):
    x = np.zeros(ns(1.4))
    for tm, a in ((0.0, 1.0), (0.07, 0.7)):
        f = rng.uniform(1100, 1800)
        ir = modes(0.15, [f, f * 1.73, f * 2.61], [0.025, 0.015, 0.01], [1, 0.5, 0.25], rng)
        add_at(x, excite(click(rng, 1.0, 9000, 300), ir), tm, a)
    ref = np.max(np.abs(x))
    for t0, dd, pts in ((0.2, 0.28, [(0, 128), (0.28, 142)]), (0.52, 0.55, [(0, 150), (0.2, 168), (0.55, 152)])):
        hm = voice(dd, curve(dd, pts, log=True), rng, "m", "o", 1.4, 0.03, 0.9, 0.9, 0.004)
        add_at(x, 0.5 * ref * _pk(lp(hm, 1800) * env_swell(dd, 0.05, dd * 0.5)), t0)
    return reverb(x, 0.18, 1.0, 5000, 0.7, seed=223)


@reg("block/ships_bell", level=-2.0)
def sfx_ships_bell(rng, i):
    x = np.zeros(ns(6.5))
    for tm, a in ((0.0, 1.0), (0.42, 0.85)):          # ding-ding
        add_at(x, _bronze_bell(nf("D5"), 2.2, rng, 0.7), tm, a)
    x *= curve(6.5, [(0, 1), (5.0, 1), (6.5, 0)])
    return reverb(x, 0.16, 1.8, 6000, 1.0, seed=224)


@reg("event/lattice_jump", level=-3.0)
def sfx_lattice_jump(rng, i):
    d = 1.6
    x = np.zeros(ns(d + 1.5))
    w = whoosh(d, rng, [(0, 200), (0.5, 1800), (d, 600)], [(0, 0), (0.35, 1), (d, 0)], 1.1)
    t = tt(d)
    warb = fm_tone(d, curve(d, [(0, 300), (0.4, 700), (d, 420)], log=True), 1.5, 1.2) * \
        (1 + 0.4 * np.sin(TWO_PI * 9 * t)) * env_swell(d, 0.3, 0.8)
    add_at(x, _u(w) + 0.25 * _u(warb), 0.0)
    fk = _fork(880.0, 2.6, rng, 0.0, 0.9, 0.25)
    fk += 0.25 * _fork(1760.0, 2.6, rng, 0.0, 0.5, 0.0) + 0.12 * _fork(2640.0, 2.6, rng, 0.0, 0.35, 0.0)
    add_at(x, 1.2 * _pk(fk) * np.max(np.abs(_u(w))), 0.28)
    return reverb(x, 0.25, 1.8, 7000, 1.0, seed=225)


@reg("item/tuning_fork", level=-8.0)
def sfx_tuning_fork(rng, i):
    x = _fork(440.0, 3.0, rng, 1.1, 0.8, 0.4)
    add_at(x, click(rng, 0.4, 9000, 1500), 0.0, 0.3)
    return reverb(x, 0.12, 1.0, 7000, 0.6, seed=226)


# ---------------------------------------------------------------- 1.5 living systems

@reg("event/tide_bite", level=-3.0)
def sfx_tide_bite(rng, i):
    x = np.zeros(ns(1.4))
    spl = hp(crumble(0.5, rng, 90, 1500, 10000, (0.0005, 0.004), 1.8), 800)
    add_at(x, _pk(spl), 0.0, 1.0)
    add_at(x, thump(0.15, 210, 120, 0.03), 0.0, 0.5)
    for k in range(4):
        add_at(x, bubble(0.06, rng.uniform(600, 900), rng.uniform(1200, 1700), 0.015, rng), 0.03 + 0.07 * k, 0.4)
    add_at(x, glass_pings(1.0, rng, 8, 1800, 5000, (0.05, 0.2), span=0.25), 0.0, 0.8)
    d = 0.9
    sh = (glass_harm(nf("D6"), d, 0.1, 0.6, rng, 0.0) + 0.6 * glass_harm(nf("A6"), d, 0.1, 0.6, rng, 0.0))
    sh *= 1 + 0.5 * np.sin(TWO_PI * 9 * tt(d))
    add_at(x, 0.3 * _pk(sh), 0.08)
    return reverb(x, 0.2, 1.3, 6000, 0.8, seed=230)


@reg("event/courier_deliver", level=-3.0)
def sfx_courier_deliver(rng, i):
    x = np.zeros(ns(1.3))
    wb = _warble(0.55, rng, [(0, 170), (0.2, 260), (0.55, 140)], 1.0, 11, ("o", "a"), 1.0, 0.1)
    add_at(x, _pk(wb), 0.0, 1.0)
    pf = fm_tone(0.5, curve(0.5, [(0, 900), (0.5, 400)], log=True), 1.41, 2.0) * env_swell(0.5, 0.1, 0.3)
    add_at(x, 0.15 * pf, 0.05)
    add_at(x, thump(0.3, 140, 80, 0.05), 0.62, 0.8)
    add_at(x, lp(crumble(0.2, rng, 15, 200, 1500, (0.002, 0.008), 2.0), 1800), 0.62, 0.4)
    return reverb(x, 0.18, 1.2, 4000, 0.8, seed=231)


@reg("event/starfall_streak", level=-3.0)
def sfx_starfall_streak(rng, i):
    x = np.zeros(ns(3.8))
    for k, n_ in enumerate(["E6", "A6", "C#7", "E7"]):
        add_at(x, glass_chime(nf(n_), 0.5, 0.8, rng, 0.1), 0.08 * k)
    d = 2.9
    t = tt(d)
    wf = curve(d, [(0, 3400), (d, 520)], log=True) * (1 + 0.004 * np.sin(TWO_PI * 6 * t))

    def g(F, T):
        return band(F, np.interp(T, t, wf), 0.1)
    air = _u(noise_shaped(d, rng, g, 1024))
    tone = np.sin(phase_of(wf)) + 0.2 * np.sin(2 * phase_of(wf))
    e = curve(d, [(0, 0), (0.4, 1), (1.8, 0.8), (d, 0)])
    rush = whoosh(d, rng, [(0, 2500), (d, 400)], [(0, 0), (0.6, 1), (d, 0)], 1.0)
    add_at(x, (0.5 * _u(tone) + 0.5 * air + 0.3 * _u(rush)) * e * 0.5, 0.3)
    return reverb(x, 0.3, 2.5, 6000, 1.3, seed=232)


@reg("event/starfall_land", level=-2.0)
def sfx_starfall_land(rng, i):
    x = np.zeros(ns(3.0))
    add_at(x, thump(0.8, 95, 42, 0.18, 0.05), 0.0, 1.2)
    add_at(x, _crack(rng, 0.1, 35, 900, 9000), 0.0, 1.0)
    add_at(x, glass_pings(1.4, rng, 22, 1000, 4500, (0.06, 0.3), span=0.2), 0.0, 1.0)
    d = 2.6
    t = tt(d)
    sh = sum(glass_harm(f, d, 0.02, 1.6, rng, 0.0) * a for f, a in ((nf("E6"), 1.0), (nf("G#6"), 0.7), (nf("B6"), 0.6)))
    sh = _pk(sh) * np.exp(-t / 0.9) * (1 + 0.4 * np.sin(TWO_PI * 10 * t))
    hiss = _shimmer(d, rng, 4000, 10000, 12) * np.exp(-t / 0.6)
    add_at(x, 0.45 * sh + 0.06 * _u(hiss), 0.03)
    return reverb(x, 0.28, 2.2, 7000, 1.2, seed=233)


@reg("event/king_tide", level=-2.0)
def sfx_king_tide(rng, i):
    d = 4.3
    t = tt(d)
    sw = curve(d, [(0, 0), (2.7, 1), (d, 0)])
    pitch = 2 ** ((2.0 * sw - 0.5) / 12)
    x = np.zeros(ns(d))
    for m, a in ((nm("D2"), 1.0), (nm("A2"), 0.8), (nm("D3"), 0.6), (nm("F3"), 0.45)):
        x += a * harmonic(mtof(m) * pitch, [1, 0.4, 0.2, 0.1, 0.05], rng)
    x = lp(x, 1400) + 0.6 * np.sin(phase_of(nf("D1") * pitch)) * np.std(x) * 1.4
    wave = 0.55 + 0.45 * np.sin(TWO_PI * 0.55 * t - 1.2)
    surf = noise_shaped(d, rng, lambda F, T: band(F, 250 * 3.0 ** np.interp(T, t, sw), 1.4) * (F > 30), 2048)
    surf = _u(surf) * wave
    light = pad_chord([nm("D5"), nm("A5"), nm("D6")], d, "glass", 1.5, 1.2, seed=234).mean(axis=1)
    light = _u(light) * (1 + 0.3 * np.sin(TWO_PI * 7 * t))
    y = (0.8 * _u(x) + 0.6 * surf + 0.22 * light * sw) * sw ** 1.3
    return reverb(y, 0.35, 3.0, 4000, 1.6, seed=234)


@reg("event/eclipse_begin", level=-6.0)
def sfx_eclipse_begin(rng, i):
    d = 4.2
    ch = pad_chord([nm("D3"), nm("A3"), nm("F#4"), nm("E5")], d, "warm", 0.25, 0.3, seed=235).mean(axis=1)
    air = bp(white(d, rng), 2000, 9000) * 0.2
    full = _u(ch) + _u(air) * 0.3
    full = stft_shape(full, lambda F, T: 1.0 / (1 + (F / (6000 * 0.025 ** np.clip(T / 3.2, 0, 1))) ** 4), 2048)
    full *= curve(d, [(0, 0.8), (0.3, 1), (3.4, 0.0), (d, 0)]) ** 1.2
    tone = glass_harm(nf("A5"), d, 1.6, 0.7, rng, 0.012)
    tone *= curve(d, [(0, 0), (0.8, 0.3), (2.4, 0.75), (d, 0.75)])
    return reverb(_u(full) + 0.4 * _u(tone), 0.3, 2.8, 5000, 1.4, seed=235)


@reg("event/eclipse_end", level=-5.0)
def sfx_eclipse_end(rng, i):
    d = 3.3
    tone = glass_harm(nf("A5"), d, 0.05, 1.2, rng, 0.012)
    ch = pad_chord([nm("D3"), nm("A3"), nm("D4"), nm("F#4"), nm("E5")], d - 0.4, "warm", 1.4, 1.2, seed=236).mean(axis=1)
    ch = stft_shape(ch, lambda F, T: 1.0 / (1 + (F / (200 * 30 ** np.clip(T / 2.0, 0, 1))) ** 4), 2048)
    x = np.zeros(ns(d))
    add_at(x, 0.8 * _u(tone), 0.0)
    add_at(x, _u(ch), 0.4)
    add_at(x, 0.4 * glass_chime(nf("F#6"), 0.6, 1.2, rng, 0.05) * np.max(np.abs(_u(tone))), 1.3)
    return reverb(x, 0.32, 2.8, 5000, 1.4, seed=236)


@reg("event/migration_calls", level=-4.0)
def sfx_migration_calls(rng, i):
    x = np.zeros(ns(6.5))
    calls = [(0.0, 2.2, [(0, 150), (0.8, 215), (2.2, 170)], 1.0), (0.9, 1.8, [(0, 230), (0.6, 262), (1.8, 192)], 0.55),
             (2.0, 2.6, [(0, 118), (1.2, 176), (2.6, 132)], 0.85), (3.3, 1.6, [(0, 198), (0.7, 250), (1.6, 212)], 0.5),
             (4.0, 2.3, [(0, 166), (1.0, 140), (2.3, 104)], 0.7)]
    for k, (t0, dd, pts, a) in enumerate(calls):
        w = whale(dd, pts, rng, vib=3.2 + 0.25 * k, vib_depth=0.011)
        add_at(x, a * _pk(lp(w, 1300)), t0)
    bed = whoosh(6.5, rng, [(0, 250), (6.5, 400)], [(0, 0), (1.5, 1), (5.0, 1), (6.5, 0)], 1.0)
    x += 0.05 * _u(bed)
    return reverb(x, 0.55, 4.0, 2000, 1.8, seed=237)


@reg("block/tide_bell", level=-3.0)
def sfx_tide_bell(rng, i):
    x = _bronze_bell(nf("A5"), 0.8, rng, 0.85)
    add_at(x, 0.25 * glass_chime(nf("A6") * 1.5, 0.8, 0.6, rng, 0.05), 0.0)   # the teal glassy edge
    return reverb(x, 0.2, 1.4, 7000, 0.8, seed=238)


@reg("block/gravity_plate", level=-5.0)
def sfx_gravity_plate(rng, i):
    d = 1.0
    t = tt(d)
    w = whoosh(d, rng, [(0, 250), (d * 0.7, 1400), (d, 1800)], [(0, 0), (0.5, 1), (d, 0)], 0.8)
    f = curve(d, [(0, 180), (d, 360)], log=True) * (1 + 0.03 * np.sin(TWO_PI * 8 * t))
    tone = (np.sin(phase_of(f)) + 0.3 * np.sin(2 * phase_of(f))) * env_swell(d, 0.15, 0.4)
    return reverb(_u(w) + 0.45 * _u(tone), 0.15, 1.0, 5000, 0.7, seed=239)


@reg("mob/lantern_moth/flutter", level=-9.0)
def sfx_lantern_moth(rng, i):
    d = 0.45
    x = np.zeros(ns(d + 0.05))
    tm = 0.0
    while tm < d:
        frac = tm / d
        b = bp(white(0.012, rng), 1800, 7000) * env_ad(0.012, 0.001, 0.003)
        add_at(x, b, tm, rng.uniform(0.4, 1.0) * np.sin(np.pi * min(1.0, frac * 1.1)) ** 0.6)
        tm += 1 / 26.0 * rng.uniform(0.8, 1.2)
    puff = lp(white(d, rng), 500) * env_swell(d, 0.05, 0.2) * 0.04
    add_at(x, puff, 0.0)
    return x


@reg("mob/mirrorfish_flop", level=-5.0)
def sfx_mirrorfish_flop(rng, i):
    x = np.zeros(ns(0.8))
    for tm, a in ((0.0, 1.0), (0.18, 0.5)):
        add_at(x, thump(0.12, 320, 190, 0.025), tm, a)
        add_at(x, bp(white(0.05, rng), 600, 3000) * env_ad(0.05, 0.001, 0.01), tm, 0.6 * a)
        add_at(x, glass_chime(2489 * (1 + 0.05 * tm), 0.35, 0.35, rng, 0.1), tm + 0.005, 0.5 * a)
        add_at(x, glass_chime(3729, 0.2, 0.25, rng, 0.05), tm + 0.012, 0.5 * a)
    return x


# ---------------------------------------------------------------- 1.6 the Zenith

def _leviathan_song(d, rng, pts):
    w = whale(d, pts, rng, vib=2.6, vib_depth=0.01, vowel="u", vowel2="o", fscale=0.42, tilt_exp=1.4,
              breath=0.03, sub=0.45)
    ov = whale(d, [(a, b * 1.5) for a, b in pts], rng, vib=2.6, vib_depth=0.008, vowel="o", vowel2="u",
               fscale=0.5, tilt_exp=1.6, breath=0.02, sub=0.0)
    return lp(_u(w) + 0.25 * _u(ov), 1800)


@reg("mob/leviathan/song", level=-2.0)
def sfx_leviathan_song(rng, i):
    d = 6.0
    x = _leviathan_song(d, rng, [(0, 205), (0.8, 220), (2.2, 196), (4.5, 130), (d, 108)])
    y = reverb(x, 0.45, 4.0, 2500, 1.9, seed=240)
    return y * curve(len(y) / SR, [(0, 1), (8.5, 1), (len(y) / SR, 0)])


@reg(["mob/leviathan/ambient1", "mob/leviathan/ambient2"], level=-4.0)
def sfx_leviathan_ambient(rng, i):
    d = [3.2, 3.8][i]
    f = curve(d, [(0, 38 + 3 * i), (d, 34 + 2 * i)], log=True)
    sub = np.sin(phase_of(f))
    rum = lp(colored(d, rng, -6), 110) * np.clip(1 + 0.4 * smooth_noise(d, 1.5, rng), 0.2, 2)
    ov = whale(d, [(0, f[0] * 5), (d * 0.5, f[0] * 5.3), (d, f[-1] * 5)], rng, vib=2.0, vib_depth=0.006,
               vowel="u", vowel2="u", fscale=0.45, tilt_exp=1.8, breath=0.02, sub=0.0)
    x = (sub + 0.6 * _u(rum)) * env_swell(d, 0.8, 1.2) + 0.9 * _u(lp(ov, 1200)) * env_swell(d, 1.0, 1.2)
    return reverb(x, 0.3, 3.0, 2000, 1.6, seed=241)


@reg(["mob/gale_harrier/screech1", "mob/gale_harrier/screech2"], level=-3.0)
def sfx_harrier_screech(rng, i):
    d, pts = [(0.75, [(0, 2600), (0.08, 3300), (0.3, 3100), (0.75, 2100)]),
              (0.6, [(0, 2900), (0.1, 3550), (0.6, 2300)])][i]
    x = np.zeros(ns(1.2))
    add_at(x, _screech(d, rng, pts, 0.5, 0.6), 0.0)
    wind = whoosh(1.1, rng, [(0, 800), (0.4, 2500), (1.1, 1200)], [(0, 0), (0.3, 1), (1.1, 0)], 1.0)
    add_at(x, 0.25 * _u(wind) * np.std(x[: ns(d)]), 0.0)
    return reverb(x, 0.2, 1.6, 7000, 1.0, seed=242)


@reg("mob/gale_harrier/dive", level=-3.0)
def sfx_harrier_dive(rng, i):
    d = 1.1
    t = tt(d)
    wf = curve(d, [(0, 3200), (d, 900)], log=True)

    def g(F, T):
        return band(F, np.interp(T, t, wf), 0.09)
    whis = 0.8 * np.sin(phase_of(wf)) + 0.6 * _u(noise_shaped(d, rng, g, 512))
    w = whoosh(d, rng, [(0, 2500), (d, 600)], [(0, 0), (0.75, 1), (d, 0)], 1.0)
    e = curve(d, [(0, 0), (0.8, 1), (d, 0)]) ** 1.4
    return reverb((0.6 * whis + 0.6 * _u(w)) * e, 0.15, 1.2, 7000, 0.8, seed=243)


@reg("mob/gale_harrier/hurt", level=-3.0)
def sfx_harrier_hurt(rng, i):
    x = _screech(0.32, rng, [(0, 3000), (0.05, 3800), (0.32, 2600)], 0.8, 0.7)
    return reverb(x, 0.12, 0.9, 7000, 0.7, seed=244)


@reg("mob/gale_harrier/death", level=-3.0)
def sfx_harrier_death(rng, i):
    x = np.zeros(ns(2.4))
    d = 1.1
    s = _screech(d, rng, [(0, 3200), (0.15, 3500), (d, 900)], 0.7, 0.8, 700) * curve(d, [(0, 1), (0.5, 0.8), (d, 0)])
    add_at(x, s, 0.0)
    w = whoosh(1.8, rng, [(0, 1800), (1.8, 400)], [(0, 0), (0.4, 1), (1.8, 0)], 1.1)
    add_at(x, 0.45 * _u(w) * np.std(s), 0.5)
    return reverb(x, 0.2, 1.6, 6000, 1.0, seed=245)


@reg("event/sky_current", level=-4.0)
def sfx_sky_current(rng, i):
    d = 1.6
    x = np.zeros(ns(d + 1.5))
    rush = whoosh(d, rng, [(0, 500), (0.7, 2200), (d, 1200)], [(0, 0), (0.8, 1), (d, 0)], 1.2)
    body = lp(whoosh(d, rng, [(0, 200), (d, 400)], [(0, 0), (0.8, 1), (d, 0)], 1.0), 600)
    add_at(x, _u(rush) + 0.4 * _u(body), 0.0)
    for k, n_ in enumerate(["E6", "G#6", "B6", "E7"]):            # gold: a warm major glint
        add_at(x, 0.5 * glass_chime(nf(n_), 0.45, 0.9, rng, 0.05), 0.4 + 0.11 * k)
    sh = _shimmer(1.2, rng, 4000, 10000, 15) * env_swell(1.2, 0.3, 0.7)
    add_at(x, 0.2 * _u(sh), 0.3)
    return reverb(x, 0.22, 1.8, 7000, 1.0, seed=246)


@reg("block/orrery_turn", level=-3.0)
def sfx_orrery_turn(rng, i):
    d = 3.2
    x = np.zeros(ns(d + 0.5))
    g = _grind(d, rng, 70, 600, 10) * env_swell(d, 0.4, 0.8)
    ring = creak(d, rng, [(0, 9), (d * 0.5, 12), (d, 8)], [61, 97, 155, 236], 0.25, 0.3)
    add_at(x, _u(g) + 0.6 * _u(lp(ring, 500)) * env_swell(d, 0.4, 0.8), 0.0)
    tm = 0.2
    while tm < d - 0.3:
        add_at(x, _pk(_clank(rng, rng.uniform(1900, 2300), 0.03)), tm, 0.9)
        add_at(x, click(rng, 0.4, 8000, 800), tm, 0.4)
        tm += 0.36
    return reverb(x, 0.25, 2.2, 3500, 1.5, seed=247)


@reg("block/orrery_align", level=-2.5)
def sfx_orrery_align(rng, i):
    d = 5.5
    x = np.zeros(ns(d + 0.5))
    deep = pad_chord([nm("D2"), nm("A2"), nm("D3")], d, "warm", 1.2, 1.5, seed=248).mean(axis=1)
    deep = _u(deep) + 0.3 * _u(bowed_metal(nf("D3"), d, rng, 1.0, 1.5, 0.3))
    add_at(x, deep, 0.0)
    for k, (n_, tm) in enumerate((("A3", 0.8), ("D4", 1.4), ("F#4", 2.0), ("A4", 2.5), ("D5", 3.0))):
        gh = glass_harm(nf(n_), d - tm, 0.5, 1.3, rng, 0.02)
        add_at(x, 0.45 * _u(gh), tm)
    for k, n_ in enumerate(["D6", "F#6", "A6", "D7"]):           # the star
        add_at(x, 2.2 * glass_chime(nf(n_), 0.7, 1.4, rng, 0.12), 3.4 + 0.07 * k)
    add_at(x, 1.4 * bell(nf("D7"), 1.5, rng, 0.8), 3.4)
    sh = _shimmer(1.8, rng, 4000, 10000, 14) * env_swell(1.8, 0.05, 1.2)
    add_at(x, 0.25 * _u(sh), 3.4)
    return reverb(x, 0.3, 3.0, 6000, 1.5, seed=248)


# ---------------------------------------------------------------- 2.0 the Undertow and the Restoration

def _eel_hiss(d, rng, lo=3500, hi=7000):
    return noise_shaped(d, rng, lambda F, T: band(F, math.sqrt(lo * hi), 0.7) + 0.6 * band(F, 2800, 0.04)
                        + 0.4 * band(F, 4150, 0.03), 1024)


@reg(["mob/tide_eel/ambient1", "mob/tide_eel/ambient2"], level=-5.0)
def sfx_tide_eel_ambient(rng, i):
    d = [2.0, 2.4][i]
    t = tt(d)
    hiss = _u(_eel_hiss(d, rng)) * env_swell(d, 0.6, 0.9) * (1 + 0.25 * np.sin(TWO_PI * 1.2 * t))
    wet = squelch(d, rng, [(0, 400), (d * 0.5, 900), (d, 500)], 0.5, [(0, 0), (0.5 * d, 1), (d, 0)])
    x = hiss + 0.35 * _u(wet)
    for k in range(2 + i):
        add_at(x, bubble(0.08, rng.uniform(500, 800), rng.uniform(1000, 1500), 0.03, rng), rng.uniform(0.3, d - 0.4), 0.8)
    add_at(x, 0.3 * glass_pings(d, rng, 3, 2500, 4500, (0.1, 0.25), span=d * 0.6, start=0.3), 0.0)
    return reverb(x, 0.25, 1.6, 4000, 0.9, seed=250)


@reg("mob/tide_eel/drain", level=-4.0)
def sfx_tide_eel_drain(rng, i):
    d = 1.1
    t = tt(d)
    f = curve(d, [(0, 102), (d, 86)], log=True)
    amps = [(1.0 if k % 2 else 0.35) / k for k in range(1, 41)]
    buzz = lp(harmonic(f, amps, rng), 3500) * (1 + 0.4 * np.sin(TWO_PI * 13 * t))
    suck = whoosh(d, rng, [(0, 2600), (d, 350)], [(0, 0.2), (d * 0.9, 1), (d, 0)], 0.7)
    e = np.clip(t / d, 0, 1) ** 0.8 * env_swell(d, 0.02, 0.08)
    return reverb((_u(buzz) + 0.7 * _u(suck)) * e, 0.15, 1.0, 4000, 0.7, seed=251)


@reg("mob/tide_eel/hurt", level=-3.0)
def sfx_tide_eel_hurt(rng, i):
    d = 0.4
    x = bp(white(d, rng), 2500, 9000) * env_ad(d, 0.002, 0.08)
    add_at(x, 0.6 * glass_pings(d, rng, 5, 2500, 6000, (0.04, 0.12), span=0.05), 0.0)
    sq = glass_harm(curve(d, [(0, 2400), (d, 1800)], log=True), d, 0.01, 0.25, rng, 0.0)
    add_at(x, 0.4 * _pk(sq) * np.max(np.abs(x)), 0.0)
    return reverb(x, 0.15, 1.0, 6000, 0.7, seed=252)


@reg("mob/tide_eel/death", level=-3.0)
def sfx_tide_eel_death(rng, i):
    d = 2.6
    hiss = noise_shaped(d, rng, lambda F, T: band(F, 3000 * 2.6 ** (T / d), 0.6), 1024)
    hiss = _u(hiss) * curve(d, [(0, 0), (0.05, 1), (d, 0)]) ** 1.5
    dens = curve(d, [(0, 0.04), (d, 0.0005)])
    gr = bp((rng.random(ns(d)) < dens) * rng.standard_normal(ns(d)), 2000, 9000)
    x = hiss * (0.4 + 0.6 * np.clip(curve(d, [(0, 1), (d, 0.2)]), 0, 1)) + 0.8 * _u(gr) * curve(d, [(0, 1), (d, 0)])
    add_at(x, 0.35 * np.max(np.abs(x)) * _pk(glass_pings(d, rng, 10, 2000, 5000, (0.05, 0.2), span=d * 0.9, shape=1.5)), 0.0)
    return reverb(x, 0.25, 1.8, 5000, 1.0, seed=253)


def _whisper_line(d, rng, n_syl, fs=1.0):
    """Unintelligible whispered phrase: consonant puffs and whispered vowels."""
    x = np.zeros(ns(d + 0.3))
    tm = 0.05
    vowels = ["a", "e", "i", "o", "u"]
    for k in range(n_syl):
        if tm > d - 0.2:
            break
        cd = rng.uniform(0.03, 0.08)
        lo, hi = [(3500, 8000), (1800, 4500), (1000, 6000)][rng.integers(3)]
        add_at(x, bp(white(cd, rng), lo, hi) * env_swell(cd, 0.008, cd * 0.6) * rng.uniform(0.25, 0.6), tm)
        vd = rng.uniform(0.14, 0.3)
        v1, v2 = vowels[rng.integers(5)], vowels[rng.integers(5)]
        w = _whisper(vd, rng, v1, v2, fs * rng.uniform(0.92, 1.12), fs * rng.uniform(0.92, 1.12),
                     [(0, 0), (0.25 * vd, 1), (vd, 0)], 300, 0.3)
        add_at(x, 0.5 * w, tm + cd * 0.7)
        tm += cd + vd + rng.uniform(0.02, 0.12)
    return x


@reg(["mob/drowned_ascendant/whisper1", "mob/drowned_ascendant/whisper2"], level=-6.0)
def sfx_ascendant_whisper(rng, i):
    d = [2.1, 2.5][i]
    x = _whisper_line(d, rng, [7, 9][i], 1.0 - 0.05 * i)
    t = tt(len(x) / SR)
    muff = lp(x, 900) + (lp(x, 2200) - lp(x, 900)) * (0.5 + 0.5 * np.sin(TWO_PI * 0.7 * t + i))   # under water
    return reverb(muff, 0.5, 3.0, 1800, 1.4, seed=254)


@reg(["mob/void_bell/creak1", "mob/void_bell/creak2"], level=-3.0)
def sfx_void_bell_creak(rng, i):
    d = [1.8, 2.2][i]
    pts = [[(0, 10), (0.6, 45), (1.2, 22), (d, 8)], [(0, 14), (0.5, 9), (1.3, 50), (d, 10)]][i]
    x = _metal_creak(d, rng, pts, 1.0 + 0.06 * i, 0.07, [(0, 0), (0.2, 1), (d * 0.8, 0.7), (d, 0)])
    x = _pk(x)
    add_at(x, 0.5 * _pk(_clank(rng, 690 + 60 * i, 0.12)), rng.uniform(0.4, d - 0.6))     # a stress ping
    return reverb(lp(x, 4000), 0.2, 1.2, 3500, 0.6, seed=255)


@reg("mob/void_bell/move", kind="sfxloop", level=-8.0)
def sfx_void_bell_move(rng, i):
    d = 2.0
    n = ns(d)
    t = np.arange(n) / SR
    # every frequency completes a whole number of cycles in 2 s -> seamless loop
    x = (np.sin(TWO_PI * 55.0 * t) + 0.25 * np.sin(TWO_PI * 55.5 * t + 0.7) + 0.55 * np.sin(TWO_PI * 110 * t + 0.4)
         + 0.4 * np.sin(TWO_PI * 165 * t + 1.1) + 0.25 * np.sin(TWO_PI * 220.5 * t + 2.0)
         + 0.12 * np.sin(TWO_PI * 330 * t) + 0.05 * np.sin(TWO_PI * 440.5 * t))
    x *= 1 + 0.08 * np.cos(TWO_PI * 1.0 * t)
    nz = np.fft.irfft(np.fft.rfft(rng.standard_normal(n)) * band(np.fft.rfftfreq(n, 1 / SR), 160, 0.5), n)
    return x + 0.5 * nz / (np.std(nz) + 1e-9) * 0.15


@reg("mob/void_bell/lamp", level=-8.0)
def sfx_void_bell_lamp(rng, i):
    x = np.zeros(ns(1.6))
    add_at(x, click(rng, 0.5, 9000, 800), 0.0, 1.0)
    add_at(x, 0.4 * _pk(_clank(rng, 3200, 0.008)), 0.0)
    add_at(x, click(rng, 0.4, 7000, 600), 0.014, 0.6)
    add_at(x, glass_chime(nf("E6"), 0.55, 0.6, rng, 0.05), 0.02)
    add_at(x, 0.1 * glass_harm(nf("E6"), 0.8, 0.05, 0.6, rng, 0.0), 0.02)
    return reverb(x, 0.18, 1.0, 6000, 0.6, seed=256)


@reg("event/maw_open", level=-2.5)
def sfx_maw_open(rng, i):
    d = 4.2
    sp = _spiral(d, rng, [(0, 0.0), (d, -(0.12 * d + 0.06 * d * d))], nf("D2"), 6, 3.0, 1.1, 0.6)
    whirl = lp(colored(d, rng, -6), 140) * (1 + 0.5 * np.sin(phase_of(curve(d, [(0, 0.5), (d, 3.0)]))))
    e = curve(d, [(0, 0), (2.8, 1), (3.6, 0.9), (d, 0)]) ** 1.5
    return reverb((_u(sp) + 0.5 * _u(whirl)) * e, 0.35, 2.8, 4000, 1.5, seed=257)


@reg("event/maw_pull", level=-2.0)
def sfx_maw_pull(rng, i):
    d = 2.1
    w = whoosh(d, rng, [(0, 2600), (1.2, 500), (d, 110)], [(0, 0.2), (0.4, 1), (1.7, 0.9), (d, 0)], 1.2, 30)
    sub = np.sin(phase_of(curve(d, [(0, 90), (d, 28)], log=True))) * curve(d, [(0, 0), (0.8, 0.7), (1.8, 1), (d, 0)])
    rush = lp(colored(d, rng, -6), 250) * curve(d, [(0, 0), (0.3, 1), (d, 0)])
    return reverb(_u(w) + 0.8 * sub + 0.6 * _u(rush), 0.25, 2.0, 2500, 1.4, seed=258)


@reg("block/archive_read", level=-5.0)
def sfx_archive_read(rng, i):
    d = 2.1
    x = np.zeros(ns(d + 1.0))
    sh = _shimmer(d, rng, 3000, 9000, 7) * env_swell(d, 0.4, 0.9)
    add_at(x, 0.25 * _u(sh), 0.0)
    for k in range(6):
        add_at(x, 2.0 * glass_chime(rng.uniform(2500, 5000), 0.3, 0.3, rng, 0.0), rng.uniform(0.15, 1.6))
    md = 1.6
    mur = voice(md, curve(md, [(0, 122), (0.5, 136), (1.0, 118), (md, 110)], log=True), rng, "m", "o",
                1.6, 0.15, 0.9, 0.95, 0.006)
    mur = lp(mur, 1400) * env_swell(md, 0.3, 0.6)
    add_at(x, 0.6 * _u(mur), 0.25)
    return reverb(x, 0.35, 2.2, 6000, 1.2, seed=259)


@reg("event/restoration_stage", level=-3.0)
def sfx_restoration_stage(rng, i):
    d = 5.6
    x = np.zeros(ns(d + 0.5))
    entries = (("D2", 0.0), ("A2", 0.4), ("F#3", 0.9), ("A3", 1.4), ("D4", 1.9), ("E4", 2.4), ("A4", 2.9))
    for k, (n_, tm) in enumerate(entries):
        p = pad_note(nf(n_), d - tm, "warm", 1.2, 1.8, seed=260 + k).mean(axis=1)
        add_at(x, (1.0 if k < 2 else 0.7) * _u(p), tm)
    for n_, tm in (("D5", 1.6), ("A5", 2.4), ("F#5", 3.2)):          # distant bells
        b = lp(bell(nf(n_), 1.6, rng, 0.8), 2500)
        add_at(x, 1.4 * b, tm)
    add_at(x, 0.5 * _u(glass_harm(nf("D6"), d - 3.2, 1.0, 1.4, rng, 0.02)), 3.2)
    return reverb(x, 0.35, 3.2, 5000, 1.5, seed=260)


# ---------------------------------------------------------------- ambience: the Zenith and the Undertow

def _circular_lp(x, f, order=2):
    n = len(x)
    y = lp(np.concatenate([x, x, x], axis=0), f, order)
    return y[n: 2 * n]


@reg("ambient/zenith_loop", kind="loop", level=AMB_LEVEL)
def amb_zenith(rng, i):
    L = 32.0
    d = L + LOOP_XF
    wg = _wind_gain(rng, d, 1400, 3800, 1.0, -1.0, 0.07)
    tg = np.arange(ns(d)) / SR
    glints = []
    for k, fq in enumerate((nf("B6"), nf("E7"), nf("F#7"), nf("A7"))):
        amp = np.clip(smooth_noise(d, 0.09, rng) - 0.2, 0, None) ** 1.5
        glints.append((fq, amp))

    def g(F, T):
        x = wg(F, T)
        for fq, amp in glints:
            x = x + 0.35 * band(F, fq, 0.008) * np.interp(T, tg, amp)
        return x
    wind = xfade_bed(stereo_bed(d, rng, g, 4096), L)
    wind /= np.std(wind) + 1e-9
    body = xfade_bed(stereo_bed(d, rng, _wind_gain(rng, d, 350, 800, 1.0, -3.0, 0.05), 4096), L)
    body /= np.std(body) + 1e-9
    n = ns(L)
    pad = np.zeros((n, 2))
    for k, (m, a) in enumerate(((nm("A4"), 1.0), (nm("E5"), 0.8), (nm("B5"), 0.5), (nm("F#6"), 0.25))):
        p = periodic_pad(mtof(m), L, "glass", seed=270 + k)
        pad += a * p * (1 + 0.35 * periodic_smooth_noise(n, 2 + k, rng))[:, None]
    pad /= np.std(pad) + 1e-9
    x = 0.6 * wind + 0.25 * body + 0.22 * pad
    return circ_reverb(x, 0.3, 3.0, 8000, 1.3, seed=270)


@reg("ambient/zenith_add1", kind="add", level=ADD_LEVEL)
def add_zenith1(rng, i):
    x = _leviathan_song(5.5, rng, [(0, 190), (0.9, 205), (2.4, 176), (5.5, 98)])
    return reverb(lp(x, 1100), 0.7, 4.0, 2000, 1.8, seed=271)


@reg("ambient/zenith_add2", kind="add", level=ADD_LEVEL)
def add_zenith2(rng, i):
    x = np.zeros(ns(3.5))
    for k, n_ in enumerate(["B6", "E7", "G#6", "B7", "E7", "G#7"]):
        add_at(x, glass_chime(nf(n_), 0.45 - 0.04 * k, 0.9, rng, 0.04), 0.14 * k + rng.uniform(0, 0.05))
    sh = _shimmer(2.2, rng, 4500, 11000, 11) * env_swell(2.2, 0.4, 1.2)
    add_at(x, 0.08 * _u(sh), 0.1)
    return reverb(x, 0.4, 2.5, 8000, 1.2, seed=271)


@reg("ambient/undertow_loop", kind="loop", level=AMB_LEVEL)
def amb_undertow(rng, i):
    L = 30.0
    n = ns(L)
    t = np.arange(n) / SR
    # the tide's hum (as in event/tide_stir) heard from inside: A1 and its beating partner
    hum = (np.sin(TWO_PI * int_freq(55.0, L) * t) + 0.7 * np.sin(TWO_PI * int_freq(58.3, L) * t + 1.0)
           + 0.45 * np.sin(TWO_PI * int_freq(110.4, L) * t + 0.3) + 0.22 * np.sin(TWO_PI * int_freq(165.0, L) * t)
           + 0.12 * np.sin(TWO_PI * int_freq(220.6, L) * t + 2.0) + 0.06 * np.sin(TWO_PI * int_freq(330.0, L) * t))
    hum *= 1 + 0.25 * periodic_smooth_noise(n, 3, rng)
    hum = np.stack([hum, np.roll(hum, ns(0.017))], axis=1)
    hum /= np.std(hum) + 1e-9
    deep = np.zeros((n, 2))
    for k, (m, a) in enumerate(((nm("D2"), 1.0), (nm("A2"), 0.7), (nm("F3"), 0.35))):
        p = periodic_pad(mtof(m), L, "deep", seed=280 + k)
        deep += a * p * (1 + 0.3 * periodic_smooth_noise(n, 2 + k, rng))[:, None]
    deep /= np.std(deep) + 1e-9
    d = L + LOOP_XF
    wash = xfade_bed(stereo_bed(d, rng, _wind_gain(rng, d, 120, 380, 1.2, -6.0, 0.06), 4096), L)
    wash /= np.std(wash) + 1e-9
    light = np.zeros((n, 2))
    for k, m in enumerate((nm("A5"), nm("D6"))):
        light += periodic_pad(mtof(m), L, "glass", seed=283 + k) * (0.5 + 0.5 * periodic_smooth_noise(n, 3, rng))[:, None]
    light /= np.std(light) + 1e-9
    x = 0.8 * hum + 0.6 * deep + 0.45 * wash
    x = _circular_lp(x, 800, 2) + 0.035 * _circular_lp(light, 2500)     # heavy, muffled; a faint glow above
    return circ_reverb(x, 0.35, 3.0, 1200, 1.4, seed=280)


@reg("ambient/undertow_loop_add1", kind="add", level=ADD_LEVEL)
def add_undertow1(rng, i):
    d = 2.4
    x = _metal_creak(d, rng, [(0, 8), (0.8, 30), (1.6, 16), (d, 6)], 0.85, 0.08, [(0, 0), (0.4, 1), (1.8, 0.6), (d, 0)])
    return reverb(lp(x, 1500), 0.6, 3.5, 1500, 1.6, seed=281)


@reg("ambient/undertow_loop_add2", kind="add", level=ADD_LEVEL)
def add_undertow2(rng, i):
    x = np.zeros(ns(5.0))
    for c, (t0, chord) in enumerate(((0.0, ("D4", "F4", "A4", "E5")), (2.0, ("Bb3", "D4", "F4", "A4")))):
        for k in range(9):
            m = chord[k % len(chord)]
            f1 = nf(m)
            add_at(x, bubble(rng.uniform(0.08, 0.14), f1 * 0.72, f1, 0.05, rng), t0 + 0.08 * k + rng.uniform(0, 0.05),
                   rng.uniform(0.5, 1.0))
        hold = sum(glass_harm(nf(m), 2.4, 0.4, 1.4, rng, 0.02) for m in chord)
        add_at(x, 0.12 * _pk(hold), t0 + 0.1)
    return reverb(lp(x, 2200), 0.45, 3.0, 2000, 1.4, seed=282)


# ---------------------------------------------------------------- music: instruments

def _brass(m, d, rng, vel=0.5, attack=1.2, release=1.5, bright=0.5, vib=0.003, ens=3):
    """Low brass-like pad voice: buzzy harmonic ensemble whose brightness follows the swell."""
    f = mtof(m)
    t = tt(d)
    nh = int(max(1, min(28, NYQ * 0.85 / f)))
    amps = [k ** -0.95 for k in range(1, nh + 1)]
    out = np.zeros(len(t))
    ve = np.clip(t / 1.5, 0, 1)
    for e in range(ens):
        det = (e - (ens - 1) / 2) * 0.003
        ff = f * (1 + det) * (1 + vib * ve * np.sin(TWO_PI * (4.4 + 0.37 * e) * t + rng.uniform(0, TWO_PI))
                              + 0.0012 * smooth_noise(d, 1.5, rng))
        out += harmonic(ff, amps, rng)
    out = _u(out)
    env = env_swell(d, attack, release, 1.2)
    dark = lp(out, 260 + 300 * bright, 2)
    brt = lp(out, 700 + 1800 * bright, 2)
    return (dark + (brt - dark) * env ** 2) * env * vel


def _call_phrase(notes, rng, glide=0.32, vowels=("o", "a"), fscale=0.8, vib=0.011, bright=0.6):
    """Portamento lead (a horn crossed with a dragon's voice).

    notes: [(t, midi, dur, slur)] in seconds; slurred notes glide in without re-articulation."""
    t0 = notes[0][0]
    d = notes[-1][0] + notes[-1][2] - t0
    t = tt(d)
    fp, ap = [], [(0, 0.0)]
    since = np.zeros(len(t))
    prev = None
    for k, (tn, m, du, slur) in enumerate(notes):
        a = tn - t0
        f = mtof(m)
        if prev is None:
            fp.append((a, f))
            ap.append((min(0.25, du * 0.3), 1.0))
        else:
            g = min(glide, du * 0.5) if slur else 0.05
            fp += [(a, prev), (a + g, f)]
            if not slur:
                ap += [(a - 0.06, 1.0), (a, 0.55), (a + 0.14, 1.0)]
        i0 = ns(a)
        since[i0:] = t[: len(t) - i0]
        prev = f
    fp.append((d, prev))
    ap += [(d - min(1.2, 0.4 * notes[-1][2]), 1.0), (d, 0.0)]
    f = curve(d, fp, log=True)
    f = f * (1 + vib * np.clip((since - 0.35) / 0.8, 0, 1) * np.sin(TWO_PI * 4.6 * t + 0.4 * np.sin(TWO_PI * 0.3 * t)))
    f = f * (1 + 0.002 * smooth_noise(d, 3, rng))
    nh = int(min(40, NYQ * 0.8 / max(np.min(f), 30)))
    src = harmonic(f, [k ** -1.05 for k in range(1, nh + 1)], rng)
    src += 0.6 * harmonic(f * 1.004, [k ** -1.1 for k in range(1, nh + 1)], rng)
    v1, v2 = vowels

    def g(F, T):
        w = 0.5 + 0.5 * np.sin(TWO_PI * T / max(d, 1.0) * 1.5)
        return 0.35 + (1 - w) * formant_gain(F / fscale, v1, 1.0, 0.05) + w * formant_gain(F / fscale, v2, 1.0, 0.05)
    y = stft_shape(_u(src), g, 2048)
    y = lp(y, 900 + 2200 * bright, 2)
    amp = np.clip(curve(d, ap), 0, 1)
    return _u(y) * amp


def _war_drum(rng, f=55.0):
    d = 2.2
    x = thump(d, f * 1.6, f, 0.5, 0.04, 0.004) + 0.3 * thump(d, f * 2.7, f * 2.3, 0.12, 0.03)
    skin = lp(white(0.15, rng), 900) * env_ad(0.15, 0.002, 0.03) * 0.4
    x[: len(skin)] += skin
    return lp(x, 1200)


def _airbell(m, vel=0.5, decay=2.2, rng=None):
    """Airy glass bell for the Zenith: glass bar plus a faint bright octave."""
    f = mtof(m)
    x = glass_chime(f, vel, decay, rng, 0.08)
    x2 = bell(f * 2, decay * 0.45, rng, 0.9) * vel * 0.1
    return mixdown(x, x2)


def _play_cells_with(song, cells, t0, spb, inst, shift=0, p=0.1, gain=1.0, rising=False, send=0.45, double=None):
    for b, top in enumerate(cells):
        notes = ladder_cell(top)
        if rising:
            notes = notes[::-1]
        tb = t0 + b * 4 * spb
        acc = 0.0
        for k, (m, du) in enumerate(zip(notes, CELL_RHYTHM)):
            v = 1.0 if k == 0 else 0.85
            song.add(inst(m + shift, v, du * spb), tb + acc * spb, p, gain, send)
            if double is not None:
                song.add(double(m + shift, du * spb), tb + acc * spb, -p, gain, send + 0.2)
            acc += du


# ---------------------------------------------------------------- music: Warden (disc)

WARDEN_V = {   # bass first, then the upper brass voicing
    "D5": ["D2", "A2", "D3"], "Dm": ["D2", "A2", "D3", "F3"], "Bb": ["Bb1", "F2", "Bb2", "D3"],
    "Gm": ["G1", "G2", "Bb2", "D3"], "A": ["A1", "A2", "C#3", "E3"], "F": ["F1", "A2", "C3", "F3"],
    "C": ["C2", "G2", "C3", "E3"], "Dsus4": ["D2", "A2", "D3", "G3"], "D": ["D2", "A2", "D3", "F#3"],
}


@reg("music/warden", kind="music", level=-16.0)
def mus_warden(rng, i):
    bpm = 52.0
    spb = 60.0 / bpm
    bar = 4 * spb
    song = Song(32 * bar, 12.0)
    plan = [(0, "D5", 4), (4, "Dm", 2), (6, "Bb", 2), (8, "Gm", 2), (10, "A", 2),
            (12, "Dm", 2), (14, "F", 2), (16, "C", 2), (18, "A", 2),
            (20, "Bb", 2), (22, "C", 2), (24, "Dsus4", 1), (25, "D", 3), (28, "D", 4)]
    for k, (b0, name, nb) in enumerate(plan):
        notes = [nm(x) for x in WARDEN_V[name]]
        tb = b0 * bar - (0.6 if b0 else 0.0)
        dur = nb * bar + (2.0 if k < len(plan) - 1 else 4.0)
        att = 3.5 if b0 == 0 else 1.6
        rel = 6.0 if k == len(plan) - 1 else 2.2
        swell = 1.15 if 12 <= b0 < 28 else 1.0
        song.add(_brass(notes[0], dur, rng, 0.62 * swell, att, rel, 0.3), tb, 0.0, 1.0, 0.35)
        for j, m in enumerate(notes[1:]):
            song.add(_brass(m, dur, rng, 0.4 * swell, att + 0.2 * j, rel, 0.5), tb + 0.05 * j, -0.35 + 0.35 * j, 1.0, 0.45)
        song.add(pad_note(mtof(notes[0] - 12), dur, "deep", att, rel, seed=300 + k), tb, 0.0, 0.25, 0.2)
    song.add(bowed_metal(nf("D3"), 26.0, rng, 7.0, 8.0, 0.35), 0.0, 0.25, 0.07, 0.6)
    song.add(bowed_metal(nf("A3"), 20.0, rng, 6.0, 8.0, 0.4), 26 * bar, -0.25, 0.05, 0.6)
    # the dragon's call: (beat, note, beats, slur) -- a rising call answered by a stepwise fall
    phrases = [
        [(16.0, "A2", 1.5, 0), (17.5, "D3", 1.0, 1), (18.5, "A3", 4.5, 1), (23.0, "G3", 1.0, 0),
         (24.0, "F3", 1.5, 1), (25.5, "G3", 0.5, 1), (26.0, "D3", 4.0, 1)],
        [(32.0, "D3", 1.5, 0), (33.5, "G3", 1.0, 1), (34.5, "Bb3", 4.5, 1), (39.0, "A3", 1.0, 0),
         (40.0, "G3", 1.0, 1), (41.0, "F3", 1.0, 1), (42.0, "E3", 2.0, 1), (44.0, "A2", 3.0, 1)],
        [(48.0, "A3", 1.5, 0), (49.5, "D4", 1.0, 1), (50.5, "F4", 4.5, 1), (55.0, "E4", 1.0, 0),
         (56.0, "D4", 1.0, 0), (57.0, "C4", 3.0, 1), (60.0, "A3", 2.0, 1)],
        [(64.0, "G3", 1.5, 0), (65.5, "C4", 1.0, 1), (66.5, "E4", 4.5, 1), (71.0, "D4", 1.0, 0),
         (72.0, "C#4", 2.0, 1), (74.0, "E4", 1.0, 1), (75.0, "A3", 4.0, 1)],
        [(80.0, "D3", 1.5, 0), (81.5, "F3", 1.0, 1), (82.5, "D4", 4.5, 1), (87.0, "C4", 1.0, 0),
         (88.0, "G3", 1.5, 0), (89.5, "C4", 1.0, 1), (90.5, "E4", 3.5, 1), (94.0, "F4", 2.0, 1),
         (96.0, "G4", 4.0, 1), (100.0, "F#4", 6.0, 1)],
        [(107.0, "A3", 1.0, 0), (108.0, "D4", 6.0, 1)],
    ]
    pans = [0.15, -0.1, 0.12, -0.12, 0.05, 0.0]
    gains = [0.75, 0.75, 0.85, 0.85, 0.9, 0.75]
    for k, ph in enumerate(phrases):
        notes = [(b * spb, nm(n_), du * spb, bool(s)) for b, n_, du, s in ph]
        y = _call_phrase(notes, rng, 0.32, ("o", "a") if k % 2 == 0 else ("a", "o"), 0.78, 0.011, 0.55)
        song.add(y, notes[0][0], pans[k], gains[k], 0.55)
    echo = _call_phrase([(0.0, nm("A3"), spb, False), (spb, nm("D4"), 4 * spb, True)], rng, 0.32, ("o", "u"), 0.78)
    song.add(lp(echo, 1200), 120 * spb, -0.3, 0.2, 1.2)           # far away, the call once more
    for b, g in ((2, 0.35), (4, 0.5), (8, 0.5), (12, 0.6), (16, 0.6), (18, 0.4), (18.5, 0.45), (19, 0.5),
                 (19.25, 0.55), (19.5, 0.6), (19.75, 0.7), (20, 0.9), (24, 0.6), (25, 1.0), (28, 0.5), (30, 0.3)):
        song.add(_war_drum(rng, 55.0 if b < 25 else nf("D2")), b * bar, 0.0, g * 0.55, 0.4)
    for b, n_ in ((25, "A5"), (25.5, "D6"), (26.5, "F#5"), (28, "A5"), (29, "F#6"), (30, "D6")):   # the crystals, at peace
        song.add(glass_chime(nf(n_), 0.25, 2.5, rng, 0.05), b * bar + 0.3, rng.uniform(-0.6, 0.6), 1.0, 0.7)
    song.add(glass_harm(nf("A5"), 6 * bar, 4.0, 8.0, rng, 0.02), 25 * bar, 0.0, 0.05, 0.7)
    return song.render(5.0, 3500, 1.7, seed=300)


# ---------------------------------------------------------------- music: Ladders (the Zenith)

@reg("music/ladders", kind="music", level=-16.0)
def mus_ladders_zenith(rng, i):
    bpm = 50.0
    spb = 60.0 / bpm
    bar = 4 * spb
    nbars = 31
    song = Song(nbars * bar, 14.0)
    D = nbars * bar + 6.0
    air = stereo_bed(D, rng, lambda F, T: band(F, 2600, 1.0) * tilt(F, -1.0, 2600) * (F > 300), 4096)
    song.add(air / (np.std(air) + 1e-9) * env_swell(D, 8.0, 10.0)[:, None], 0.0, 0.0, 0.012, 0.3)

    def bellv(m, v, du):
        return _airbell(m, 0.5 * v, 1.6 + 0.4 * du, rng)

    def gh(m, du):
        return glass_harm(mtof(m + 12), du + 0.8, 0.15, 0.7, rng, 0.03) * 0.1

    def pads(name, b0, nb, shift=0, g=0.2, kind="cold"):
        notes = chord_m(name, shift)
        hi = [m + 12 for m in notes[1:]]
        song.add(pad_chord(hi, nb * bar + 3.0, kind, 2.0, 2.5, seed=310 + int(b0)), b0 * bar - 0.8, 0, g, 0.5)
        song.add(pad_note(mtof(notes[0]), nb * bar + 3.0, "warm", 2.0, 2.5, seed=320 + int(b0)), b0 * bar - 0.8, 0,
                 g * 0.9, 0.4)

    # intro: high pads and a few pings in the open air
    song.add(pad_chord([nm("A4"), nm("E5"), nm("B5")], 3 * bar + 3, "glass", 5.0, 4.0, seed=330), 0.0, 0, 0.3, 0.6)
    pads("Am9", 0, 3, 0, 0.16)
    for b, n_, p in ((0.5, "E6", 0.4), (1.3, "B6", -0.4), (2.2, "A6", 0.2), (2.7, "E7", -0.2)):
        song.add(_airbell(nm(n_), 0.25, 2.5, rng), b * bar, p, 1.0, 0.7)
    # S1: the first ladder, now climbing
    _play_cells_with(song, LADDER_P1[::-1], 3 * bar, spb, bellv, 0, 0.12, 1.0, True)
    for k, nmx in enumerate(P1_CHORDS[::-1]):
        pads(nmx, 3 + k, 1)
    pads("Am9", 7, 1, 0, 0.18)
    song.add(_airbell(nm("E6"), 0.3, 2.5, rng), 7 * bar + 2 * spb, 0.35, 1.0, 0.8)

    def landing(b0, shift, dbl):
        cells = LADDER_P2[::-1]
        _play_cells_with(song, cells[:3], b0 * bar, spb, bellv, shift, 0.1, 1.0, True, double=gh if dbl else None)
        for k, nmx in enumerate(["Em9", "Cmaj7#11", "Gmaj7"]):
            pads(nmx, b0 + k, 1, shift)
        tb = (b0 + 3) * bar
        for m, at_, du in ((nm("E5"), 0.0, 1.0), (nm("G#5"), 1.0, 0.5), (nm("A5"), 1.5, 4.5)):
            song.add(bellv(m + shift, 1.05, du), tb + at_ * spb, 0.1, 1.0, 0.5)
            song.add(gh(m + shift, du * spb), tb + at_ * spb, -0.1, 1.0 if dbl else 0.6, 0.6)
        song.add(pad_chord([x + 12 for x in chord_m("E", shift)[1:]], 1.5 * spb + 0.6, "cold", 0.3, 0.6,
                           seed=340 + shift), tb, 0, 0.2, 0.5)
        song.add(pad_chord([x + 12 for x in chord_m("Amaj", shift)[1:]], 2.5 * spb + 5.0, "warm", 0.8, 4.0,
                           seed=341 + shift), tb + 1.5 * spb, 0, 0.24, 0.5)
        song.add(pad_note(mtof(chord_m("Amaj", shift)[0]), 4 * spb + 5.0, "warm", 0.8, 4.0, seed=342 + shift),
                 tb + 1.5 * spb, 0, 0.2, 0.4)

    # S2: the second ladder lands on A major
    landing(8, 0, False)
    for k, n_ in enumerate(["A5", "C#6", "E6", "A6"]):
        song.add(_airbell(nm(n_), 0.3, 2.5, rng), 12 * bar + k * spb, -0.3 + 0.2 * k, 1.0, 0.6)
    pads("Amaj", 12, 1, 0, 0.18, "warm")
    song.add(pad_chord([nm(x) for x in ("D4", "F4", "A4", "C5", "E5")], bar + 3.0, "cold", 1.5, 2.5, seed=343),
             13 * bar - 0.6, 0, 0.2, 0.5)
    song.add(pad_note(nf("D3"), bar + 3.0, "warm", 1.5, 2.5, seed=344), 13 * bar - 0.6, 0, 0.18, 0.4)
    song.add(_airbell(nm("A6"), 0.25, 2.5, rng), 13 * bar + 2 * spb, 0.3, 1.0, 0.7)
    # S3/S4: the whole climb again, a fourth higher, and this time it resolves on D
    _play_cells_with(song, LADDER_P1[::-1], 14 * bar, spb, bellv, 5, 0.12, 1.0, True, double=gh)
    for k, nmx in enumerate(P1_CHORDS[::-1]):
        pads(nmx, 14 + k, 1, 5)
    pads("Am9", 18, 1, 5, 0.18)
    landing(19, 5, True)
    # coda: D major opening upward, bells climbing into the light
    arp = ["D5", "F#5", "A5", "D6", "F#6", "A6", "D7"]
    for k, n_ in enumerate(arp):
        song.add(_airbell(nm(n_), 0.42 - 0.03 * k, 3.0, rng), 23 * bar + 2 * spb + k * 0.5 * spb, -0.45 + 0.15 * k, 1.0, 0.6)
    song.add(pad_chord([nm(x) for x in ("D4", "F#4", "A4", "E5", "A5")], 7 * bar, "glass", 3.0, 8.0, seed=350),
             23 * bar, 0, 0.3, 0.6)
    song.add(pad_note(nf("D3"), 7 * bar, "warm", 3.0, 8.0, seed=351), 23 * bar, 0, 0.18, 0.4)
    for m, at_, du in ((nm("A6"), 0.0, 1.0), (nm("C#7"), 1.0, 0.5), (nm("D7"), 1.5, 4.0)):   # the landing, an octave up
        song.add(_airbell(m, 0.28, 3.0, rng), 26 * bar + at_ * spb, 0.2, 1.0, 0.85)
    song.add(glass_harm(nf("D6"), 5 * bar, 3.0, 7.0, rng, 0.02), 25 * bar, 0.0, 0.07, 0.7)
    return song.render(4.5, 7000, 1.5, seed=310)


# ---------------------------------------------------------------- music: Undertow

UNDERTOW_V = {
    "Dm/A": ["D3", "F3", "A3"], "Fmaj7/A": ["C3", "E3", "F3", "A3"], "Gm9/A": ["Bb2", "D3", "F3", "G3"],
    "Asus": ["D3", "E3", "A3"], "A": ["C#3", "E3", "A3"], "Bbmaj7": ["Bb2", "D3", "F3", "A3"],
    "Dm": ["D3", "F3", "A3", "E4"],
}
UNDERTOW_BASS = {"Dm/A": "D2", "Fmaj7/A": "F2", "Gm9/A": "G2", "Asus": "A1", "A": "A1", "Bbmaj7": "Bb1", "Dm": "D2"}


@reg("music/undertow", kind="music", level=-16.0)
def mus_undertow(rng, i):
    bpm = 40.0
    spb = 60.0 / bpm
    bar = 4 * spb
    nbars = 25
    D = nbars * bar
    song = Song(D, 12.0)
    t = tt(D)
    # the tide's hum, from inside: A1 against a partner a semitone up; the partner sinks
    # toward unison in the middle section, so the beating slows almost to stillness
    partner = curve(D, [(0, 58.27), (11 * bar, 58.27), (13 * bar, 56.0), (18 * bar, 55.6), (19 * bar, 58.27),
                        (D, 58.27)], log=True)
    hum = (np.sin(TWO_PI * 55.0 * t) + 0.6 * np.sin(phase_of(partner)) + 0.45 * np.sin(TWO_PI * 110.4 * t + 0.3)
           + 0.2 * np.sin(TWO_PI * 165.2 * t + 1.0) + 0.1 * np.sin(TWO_PI * 220.8 * t))
    hum = lp(hum, 400) * curve(D, [(0, 0), (14, 1), (D - 14, 1), (D, 0)])
    song.add(np.stack([hum, np.roll(hum, ns(0.019))], axis=1), 0.0, 0.0, 0.16, 0.25)
    wash = stereo_bed(D, rng, lambda F, T: band(F, 220, 1.1) * (F > 40), 4096)
    wash = wash / (np.std(wash) + 1e-9) * np.clip(1 + 0.5 * smooth_noise(D, 0.06, rng), 0.2, 2)[:, None]
    song.add(wash * env_swell(D, 10.0, 12.0)[:, None], 0.0, 0.0, 0.035, 0.2)

    def mpiano(m, vel, du):
        return lp(piano(m, vel, du, bright=0.35, seed=int(m)), 850, 2)

    plan = [(1, "Dm/A", 2), (3, "Dm/A", 2), (5, "Fmaj7/A", 2), (7, "Gm9/A", 2), (9, "Asus", 1), (10, "A", 1),
            (11, "Bbmaj7", 2), (13, "Fmaj7/A", 2), (15, "Gm9/A", 2), (17, "Asus", 1), (18, "A", 1),
            (19, "Dm/A", 2), (21, "Gm9/A", 2), (23, "Dm", 2)]
    for k, (b0, name, nb) in enumerate(plan):
        tc = b0 * bar
        notes = [nm(x) for x in UNDERTOW_V[name]]
        dur = nb * bar + 3.0 + (6.0 if k == len(plan) - 1 else 0.0)
        pd = pad_chord(notes, dur, "deep", 2.5, 3.0, seed=360 + k)
        song.add(lp(pd, 900), tc - 1.0, 0.0, 0.22, 0.4)
        song.add(pad_note(nf(UNDERTOW_BASS[name]), dur, "deep", 2.5, 3.0, seed=370 + k), tc - 1.0, 0.0, 0.2, 0.3)
        if k >= 1:
            # inverted swell: the chord's reverberant tail played backwards, cresting at the change
            fw = np.zeros(ns(0.6))
            for j, m in enumerate(notes):
                add_at(fw, piano(m + 12, 0.4, 0.5, bright=0.4, seed=j), 0.0)
            rv = reverse(reverb(fw, 1.0, 3.5, 2500, 1.4, seed=380, dry=0.25))
            rv = lp(fade(rv, 0.4, 0.004), 1200)
            song.add(rv, tc - len(rv) / SR, 0.25 * (1 if k % 2 else -1), 0.9, 0.3)
    # muffled piano melody (beat offsets from the phrase start)
    phA = [(0, "A4", 1), (1, "Bb4", 1), (2, "A4", 2), (4, "D4", 4), (8, "F4", 1), (9, "E4", 1), (10, "C4", 2),
           (12, "A3", 4), (16, "Bb3", 1), (17, "D4", 1), (18, "F4", 2), (20, "E4", 4), (24, "D4", 2), (26, "E4", 2),
           (28, "C#4", 4)]
    phB = [(0, "A4", 1), (1, "Bb4", 1), (2, "D5", 2), (4, "C5", 4), (8, "A4", 1), (9, "G4", 1), (10, "F4", 2),
           (12, "E4", 4), (16, "D4", 1), (17, "F4", 1), (18, "A4", 2), (20, "G4", 4), (24, "Bb4", 1), (25, "A4", 1),
           (26, "E4", 2), (28, "C#4", 4)]
    phC = [(0, "A4", 1), (1, "Bb4", 1), (2, "A4", 2), (4, "D4", 4), (8, "Bb3", 1), (9, "D4", 1), (10, "F4", 2),
           (12, "E4", 4), (16, "F4", 2), (18, "E4", 2), (20, "D4", 8)]
    for b0, ph, vel in ((3, phA, 0.5), (11, phB, 0.55), (19, phC, 0.48)):
        for beat, n_, du in ph:
            v = vel * (1.0 if beat % 4 == 0 else 0.88)
            song.add(mpiano(nm(n_), v, du * spb + 0.6), b0 * bar + beat * spb, 0.12, 1.0, 0.55)
    # a low octave answer under the middle section
    for b, n_ in ((11, "D3"), (13, "C3"), (15, "Bb2"), (17, "A2")):
        song.add(mpiano(nm(n_), 0.35, 2 * bar), b * bar + 2 * spb, -0.15, 1.0, 0.5)
    # slow bubble chords drifting up, far off
    for b, chord in ((9.5, ("D4", "F4", "A4", "E5")), (17.5, ("C#4", "E4", "A4")), (24.0, ("D4", "F4", "A4"))):
        bb = np.zeros(ns(3.0))
        for k in range(8):
            f1 = nf(chord[k % len(chord)])
            add_at(bb, bubble(rng.uniform(0.08, 0.14), f1 * 0.72, f1, 0.05, rng), 0.09 * k + rng.uniform(0, 0.05),
                   rng.uniform(0.5, 1.0))
        song.add(lp(bb, 1500), b * bar, rng.uniform(-0.5, 0.5), 0.12, 0.8)
    y = song.render(5.0, 1800, 1.6, seed=360)
    return lp(y, 2400)


# =============================================================================
# Finalisation, encoding, driver
# =============================================================================

STEREO_KINDS = ("loop", "music", "musicloop")
LOOP_KINDS = ("loop", "sfxloop", "musicloop")
OGG_Q = {"sfx": 3, "sfxloop": 3, "add": 3, "loop": 3, "music": 4, "musicloop": 4}
PEAK_CAP = {"loop": -3.0, "music": -1.5, "musicloop": -1.5}


def _circular_hp(x, f=20.0):
    n = len(x)
    y = hp(np.concatenate([x, x, x], axis=0), f)
    return y[n: 2 * n]


def _trim(x, floor_db=-66.0):
    a = np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=1)
    pk = a.max()
    if pk <= 0:
        return x
    win = ns(0.01)
    env = np.maximum.accumulate(a[::-1])[::-1]       # max of remaining samples
    thr = pk * dbg(floor_db)
    above = np.nonzero(env > thr)[0]
    end = above[-1] + win if len(above) else len(x)
    first = np.nonzero(a > pk * dbg(-60.0))[0]
    start = max(0, first[0] - ns(0.002)) if len(first) else 0
    return x[start: min(len(x), end)]


def finalize(x, kind, level):
    x = np.asarray(x, dtype=float)
    if kind in STEREO_KINDS:
        if x.ndim == 1:
            x = np.stack([x, x], axis=1)
    elif x.ndim == 2:
        x = x.mean(axis=1)
    if not np.all(np.isfinite(x)):
        raise ValueError("non-finite samples")
    if kind in LOOP_KINDS:
        x = _circular_hp(x, 20.0)
    else:
        x = hp(x, 20.0)
        x = _trim(x)
        if kind == "music":
            x = fade(x, 0.01, 3.0)
        else:
            x = fade(x, 0.0015, min(0.04, len(x) / SR * 0.2))
    if kind in ("sfx", "sfxloop", "add"):
        # tame isolated sample-level spikes (up to 4 dB) so clicky sounds are not
        # left far quieter than sustained ones at the same peak level
        x = limiter(x, 20 * np.log10(np.max(np.abs(x)) + 1e-12) - 4.0, 0.004, kind == "sfxloop")
        x = norm_peak(x, dbg(level))
    elif kind in ("loop", "music"):
        g = dbg(level - lufs(x))
        pk = np.max(np.abs(x)) * g
        cap = dbg(PEAK_CAP[kind])
        max_lim = dbg(4.0 if kind == "music" else 3.0)
        if pk > cap * max_lim:
            g *= cap * max_lim / pk
        x = limiter(x * g, PEAK_CAP[kind], circular=(kind == "loop"))
    else:   # musicloop: gain already applied, just guard the peak
        pk = np.max(np.abs(x))
        if pk > dbg(-1.0):
            x = x * dbg(-1.0) / pk
    return x.astype(np.float32)


def _decode(path):
    r = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-"],
                       capture_output=True, check=True)
    return np.frombuffer(r.stdout, dtype=np.float32)


def encode(x, wav_path, ogg_path, q):
    wavfile.write(wav_path, SR, x)
    try:
        for attempt in range(4):
            # bitexact: fixed Ogg serial numbers so re-runs produce identical files
            subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", wav_path, "-c:a", "libvorbis",
                            "-q:a", str(q), "-ar", str(SR), "-fflags", "+bitexact",
                            "-flags:a", "+bitexact", ogg_path], check=True)
            pk = float(np.max(np.abs(_decode(ogg_path))))
            if pk < dbg(-0.3):
                return pk
            # lossy overshoot: back off and re-encode
            x = (x * (dbg(-0.6) / pk)).astype(np.float32)
            wavfile.write(wav_path, SR, x)
        return pk
    finally:
        if os.path.exists(wav_path):
            os.remove(wav_path)


def render_stem(stem, tmp_dir):
    fn, idx, kind, level = RECIPES[stem]
    t0 = time.time()
    rng = np.random.default_rng(seed_of(stem))
    x = fn(rng, idx)
    x = finalize(x, kind, level)
    out = os.path.join(OUT_DIR, stem + ".ogg")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    wav = os.path.join(tmp_dir, stem.replace("/", "__") + ".wav")
    pk = encode(x, wav, out, OGG_Q[kind])
    ch = 1 if x.ndim == 1 else x.shape[1]
    return stem, kind, len(x) / SR, ch, 20 * math.log10(pk + 1e-12), os.path.getsize(out), time.time() - t0


def spec_stems():
    """Every file stem referenced anywhere in sound_spec.py (deduplicated, ordered)."""
    stems = []
    for _, (_, files, _) in list(spec.block_events().items()) + list(spec.EVENTS.items()):
        stems += files
    for _, (loop, adds, _) in spec.AMBIENT.items():
        stems += [loop] + list(adds)
    for _, (stem, _) in spec.MUSIC.items():
        stems.append(stem)
    seen = set()
    return [s for s in stems if not (s in seen or seen.add(s))]


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--tmp", default=None, help="directory for temporary WAV files")
    ap.add_argument("--only", nargs="*", default=None, help="only render stems starting with these prefixes")
    ap.add_argument("--stems", nargs="*", default=None, help="only render exactly these stems")
    ap.add_argument("--missing", action="store_true", help="only render stems whose .ogg does not exist yet")
    ap.add_argument("--jobs", type=int, default=max(1, min(8, (os.cpu_count() or 2) - 2)))
    args = ap.parse_args()

    for tool in ("ffmpeg",):
        if shutil.which(tool) is None:
            sys.exit(f"{tool} not found on PATH")
    stems = spec_stems()
    missing = [s for s in stems if s not in RECIPES]
    if missing:
        sys.exit("no recipe for: " + ", ".join(missing))
    if args.only:
        stems = [s for s in stems if any(s.startswith(p) for p in args.only)]
    if args.stems:
        unknown = [s for s in args.stems if s not in RECIPES]
        if unknown:
            sys.exit("unknown stem(s): " + ", ".join(unknown))
        wanted = set(args.stems)
        stems = [s for s in stems if s in wanted]
    if args.missing:
        stems = [s for s in stems if not os.path.isfile(os.path.join(OUT_DIR, s + ".ogg"))]
    own_tmp = args.tmp is None
    tmp_dir = args.tmp or tempfile.mkdtemp(prefix="worldsfall_audio_")
    os.makedirs(tmp_dir, exist_ok=True)

    # longest jobs first for better load balancing
    order = {"music": 0, "musicloop": 0, "loop": 1}
    stems.sort(key=lambda s: order.get(RECIPES[s][2], 2))
    t0 = time.time()
    results = []
    errors = []
    try:
        with ProcessPoolExecutor(max_workers=args.jobs) as ex:
            futs = {ex.submit(render_stem, s, tmp_dir): s for s in stems}
            for f in as_completed(futs):
                s = futs[f]
                try:
                    r = f.result()
                    results.append(r)
                    print(f"  {r[0]:<44} {r[1]:<9} {r[2]:6.2f}s ch={r[3]} peak={r[4]:6.2f}dBFS "
                          f"{r[5] / 1024:7.1f}KB ({r[6]:.1f}s)", flush=True)
                except Exception as e:  # noqa: BLE001
                    errors.append((s, repr(e)))
                    print(f"  FAILED {s}: {e!r}", flush=True)
    finally:
        if own_tmp:
            shutil.rmtree(tmp_dir, ignore_errors=True)
        else:
            for fn_ in os.listdir(tmp_dir):
                if fn_.endswith(".wav"):
                    os.remove(os.path.join(tmp_dir, fn_))
    total = sum(r[5] for r in results)
    print(f"\nrendered {len(results)}/{len(stems)} stems, {total / 1e6:.2f} MB, {time.time() - t0:.0f}s")
    if errors:
        sys.exit(f"{len(errors)} stems failed")


if __name__ == "__main__":
    main()
