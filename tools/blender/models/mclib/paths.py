"""Locations of external inputs.  Vanilla textures are only needed for the wild shulker alpha mask and the elytra
base; point WF_VANILLA_ASSETS at an extracted client jar's assets/minecraft directory."""
import os

VANILLA = os.environ.get(
    "WF_VANILLA_ASSETS",
    "/private/tmp/claude-501/-Users-jiangshengbo-Undertow/887845a4-3236-4149-9e5f-f22849f5c113/scratchpad/src/assets/minecraft")


def vanilla(rel):
    p = os.path.join(VANILLA, rel)
    if not os.path.exists(p):
        raise FileNotFoundError("vanilla asset %s not found - set WF_VANILLA_ASSETS" % p)
    return p
