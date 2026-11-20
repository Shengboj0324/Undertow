"""Shared helpers for the Worldsfall resource generators."""
import json, os
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/worldsfall")
DATA = os.path.join(RES, "data")
VANILLA = "/private/tmp/claude-501/-Users-jiangshengbo-Undertow/887845a4-3236-4149-9e5f-f22849f5c113/scratchpad/src"
NS = "worldsfall"
WRITTEN = []

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, sort_keys=False)
        f.write("\n")
    WRITTEN.append(path)

def vanilla_json(rel):
    with open(os.path.join(VANILLA, rel)) as f:
        return json.load(f)

def rid(path):
    return f"{NS}:{path}"

def title(s):
    small = {"of", "the", "a", "to", "in", "on"}
    words = s.split("_")
    out = []
    for i, w in enumerate(words):
        out.append(w if (i > 0 and w in small) else w.capitalize())
    return " ".join(out)
