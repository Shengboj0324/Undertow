#!/usr/bin/env python3
"""Validate the exported Worldsfall entity models and textures against art/SPEC_MODELS.md.

Checks: JSON parses; every required part exists; parents exist and form a tree; cube sizes are whole texels;
box-UV face rectangles lie inside texture_size and do not overlap (identical shared/mirrored islands are allowed);
PNG sizes equal texture_size; emissive maps exist where required (and are transparent except glowing texels);
texture-only assets (wild shulker, elytra upgrades, sprites) exist with the right sizes.  Exit code 1 on failure.
"""
import json
import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from mclib.png import read_png  # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
A = os.path.join(REPO, "src", "main", "resources", "assets", "worldsfall")
JD = os.path.join(A, "models", "entity")
TD = os.path.join(A, "textures", "entity")

WYRM = ["body", "neck", "head", "horn_left", "horn_right", "jaw", "left_wing", "left_wing_tip", "right_wing",
        "right_wing_tip", "tail_1", "tail_2", "tail_3", "leg_front_left", "leg_front_right", "leg_back_left",
        "leg_back_right", "crest"]
MODELS = {
    "void_skate": (["body", "left_wing", "left_wing_tip", "right_wing", "right_wing_tip", "tail_1", "tail_2", "tail_3",
                    "left_fin", "right_fin", "harness"],
                   ["void_skate/void_skate", "void_skate/void_skate_dusk"], True, (128, 128)),
    "chorus_grazer": (["body", "neck_1", "neck_2", "neck_3", "head", "frill", "leg_front_left", "leg_front_right",
                       "leg_mid_left", "leg_mid_right", "leg_back_left", "leg_back_right", "tail"],
                      ["chorus_grazer/chorus_grazer", "chorus_grazer/chorus_grazer_sheared"], True, (128, 64)),
    "underside_lurker": (["body", "crown"] + ["leg_%d" % i for i in range(7)] + ["leg_%d_lower" % i for i in range(7)],
                         ["underside_lurker/underside_lurker"], True, (64, 64)),
    "hoarder": (["head", "body", "cloak", "pouch", "right_arm", "left_arm", "right_leg", "left_leg"],
                ["hoarder/hoarder"], True, (64, 64)),
    "spire_warden": (["head", "crest", "body", "right_arm", "right_forearm", "left_arm", "left_forearm", "right_leg",
                      "left_leg", "plate_shoulder_right", "plate_shoulder_left", "plate_chest", "plate_forearm_right",
                      "plate_forearm_left"],
                     ["spire_warden/spire_warden", "spire_warden/spire_warden_cracked",
                      "spire_warden/spire_warden_shattered"], True, (128, 128)),
    "echo": (["head", "body", "right_arm", "left_arm", "right_leg", "left_leg"], ["echo/echo"], True, (64, 32)),
    "wyrm_hatchling": (WYRM, ["wyrm/wyrm_hatchling"], True, None),
    "wyrm_fledgling": (WYRM, ["wyrm/wyrm_fledgling"], True, None),
    "wyrm_drake": (WYRM, ["wyrm/wyrm_drake"], True, None),
    "shardling": (["core", "shard", "splinter_a", "splinter_b"], ["shardling/shardling"], True, (32, 32)),
    "unmade_shard": (["shard"], ["the_unmade/shard"], True, (32, 32)),
    "void_capsule": (["cap_top", "glass", "cap_bottom", "strand"], ["void_capsule/void_capsule"], True, (64, 32)),
    "rift_hook": (["claw", "pearl", "prong_0", "prong_1", "prong_2"], ["rift_hook/rift_hook"], True, (32, 32)),
    "tether_pylon": (["crystal"], ["tether_pylon/tether_pylon"], True, (64, 64)),
}
TEXTURE_ONLY = {
    "wild_shulker/wild_shulker.png": (64, 64), "wild_shulker/wild_shulker_molted.png": (64, 64),
    "tidewisp/tidewisp.png": (32, 32), "the_unmade/core.png": (64, 64), "the_unmade/tendril.png": (16, 64),
    "equipment/wings/elytra_gale.png": (64, 32), "equipment/wings/elytra_still.png": (64, 32),
    "equipment/wings/elytra_hover.png": (64, 32),
}


def png_size(p):
    with open(p, "rb") as f:
        d = f.read(24)
    assert d[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    return struct.unpack(">II", d[16:24])


def face_rects(uv, size):
    u, v = uv
    w, h, d = size
    return [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h),
            (u + 2 * d + w, v + d, w, h)]


def check_model(name, req, texs, emissive, want_size, errs, notes):
    jp = os.path.join(JD, name + ".json")
    if not os.path.exists(jp):
        errs.append("%s: missing %s" % (name, jp))
        return
    try:
        doc = json.load(open(jp))
    except Exception as e:
        errs.append("%s: JSON parse error %s" % (name, e))
        return
    W, H = doc["texture_size"]
    if want_size and (W, H) != tuple(want_size):
        errs.append("%s: texture_size %s != spec %s" % (name, (W, H), want_size))
    parts = {p["name"]: p for p in doc["parts"]}
    if len(parts) != len(doc["parts"]):
        errs.append("%s: duplicate part names" % name)
    for r in req:
        if r not in parts:
            errs.append("%s: required part '%s' missing" % (name, r))
    seen = set()
    for p in doc["parts"]:
        par = p["parent"]
        if par is not None and par not in seen:
            errs.append("%s: part %s has parent %s that is missing or defined later" % (name, p["name"], par))
        seen.add(p["name"])
        for k in ("pivot", "rotation"):
            if len(p[k]) != 3:
                errs.append("%s: %s.%s malformed" % (name, p["name"], k))
    owner = {}
    ncubes = 0
    for p in doc["parts"]:
        for c in p["cubes"]:
            ncubes += 1
            size = c["size"]
            if any(abs(s - round(s)) > 1e-6 or s < 0 for s in size):
                errs.append("%s: %s cube size %s not whole texels" % (name, p["name"], size))
            key = (tuple(c["uv"]), tuple(size))
            for (x, y, w, h) in face_rects(c["uv"], size):
                if w <= 0 or h <= 0:
                    continue
                if x < 0 or y < 0 or x + w > W or y + h > H:
                    errs.append("%s: %s cube uv %s size %s exceeds texture %sx%s" % (name, p["name"], c["uv"], size, W, H))
                for yy in range(int(y), int(y + h)):
                    for xx in range(int(x), int(x + w)):
                        o = owner.get((xx, yy))
                        if o is None:
                            owner[(xx, yy)] = key
                        elif o != key:
                            errs.append("%s: UV overlap at texel (%d,%d) between %s and %s/%s" % (name, xx, yy, o, p["name"], key))
                            break
                    else:
                        continue
                    break
    for t in texs:
        tp = os.path.join(TD, t + ".png")
        if not os.path.exists(tp):
            errs.append("%s: missing texture %s" % (name, tp))
            continue
        if png_size(tp) != (W, H):
            errs.append("%s: %s is %s, expected %s" % (name, t, png_size(tp), (W, H)))
        if emissive:
            ep = os.path.join(TD, t + "_emissive.png")
            if not os.path.exists(ep):
                errs.append("%s: missing emissive map %s" % (name, ep))
            elif png_size(ep) != (W, H):
                errs.append("%s: emissive %s wrong size" % (name, ep))
            else:
                em = read_png(ep)
                glow = int((em[:, :, 3] > 0).sum())
                if glow == 0 and not t.endswith("_sheared"):
                    errs.append("%s: emissive map %s has no glowing texels" % (name, ep))
                if glow > 0.9 * W * H:
                    errs.append("%s: emissive map %s is not mostly transparent" % (name, ep))
    notes.append("%-17s %2d parts %3d cubes  tex %dx%d  %s" % (name, len(parts), ncubes, W, H, ", ".join(texs)))


def main():
    errs, notes = [], []
    for name, (req, texs, em, size) in MODELS.items():
        check_model(name, req, texs, em, size, errs, notes)
    for rel, size in TEXTURE_ONLY.items():
        p = os.path.join(TD, rel)
        if not os.path.exists(p):
            errs.append("missing texture %s" % p)
        elif png_size(p) != size:
            errs.append("%s is %s, expected %s" % (rel, png_size(p), size))
        else:
            notes.append("%-40s %dx%d ok" % (rel, size[0], size[1]))
    # the wild shulker skins must keep vanilla's alpha layout exactly (same opaque texels)
    try:
        from mclib.paths import vanilla
        van = read_png(vanilla("textures/entity/shulker/shulker.png"))
        for rel in ("wild_shulker/wild_shulker.png", "wild_shulker/wild_shulker_molted.png"):
            w = read_png(os.path.join(TD, rel))
            if ((w[:, :, 3] > 0) != (van[:, :, 3] > 0)).any():
                errs.append("%s does not match the vanilla shulker UV/alpha layout" % rel)
    except FileNotFoundError:
        notes.append("(vanilla shulker not available - layout comparison skipped)")
    for n in notes:
        print("  " + n)
    if errs:
        print("FAILED (%d problems):" % len(errs))
        for e in errs:
            print("  - " + e)
        return 1
    print("OK - %d models, %d texture-only assets verified" % (len(MODELS), len(TEXTURE_ONLY)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
