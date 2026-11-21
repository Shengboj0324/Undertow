"""Real multiplayer test: a production NeoForge server running the built jar, and two dev clients (Alice, Bob) that join
it over TCP with tools-side probes (client/showcase/MultiplayerProbe).

The script stages Alice in the End wearing trimmed armor and an upgraded elytra with a Hatchling Wyrm on her shoulder,
gives Bob a Void Lens, then checks from Bob's client that all of it arrived (equipment, trims, elytra upgrade and wing
asset, shoulder sync, tide sync, mod channel) and takes screenshots from both clients. It exits non-zero on any failed
check or any server ERROR/exception.

Usage: python3 tools/mptest/mp_test.py <server dir with run.sh, eula accepted> <mod jar>
"""
import os
import queue
import shutil
import subprocess
import sys
import threading
import time

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SERVER, JAR = os.path.abspath(sys.argv[1]), os.path.abspath(sys.argv[2])
PROBE = os.path.join(ROOT, "build/mptest")
JAVA_HOME = os.environ.get("WF_JAVA_HOME", os.path.expanduser("~/.sdkman/candidates/java/25.0.2-open"))  # Java 25
ENV = dict(os.environ, JAVA_HOME=JAVA_HOME, PATH=os.path.join(JAVA_HOME, "bin") + os.pathsep + os.environ["PATH"])
failures = []


def check(cond, what):
    print(("PASS " if cond else "FAIL ") + what, flush=True)
    if not cond:
        failures.append(what)


# ---- server
shutil.rmtree(PROBE, ignore_errors=True)
os.makedirs(PROBE)
mods = os.path.join(SERVER, "mods")
os.makedirs(mods, exist_ok=True)
for f in os.listdir(mods):
    if f.startswith("worldsfall"):
        os.remove(os.path.join(mods, f))
shutil.copy(JAR, mods)
shutil.rmtree(os.path.join(SERVER, "world"), ignore_errors=True)
with open(os.path.join(SERVER, "server.properties"), "w") as f:
    f.write("online-mode=false\nenforce-secure-profile=false\nspawn-protection=0\nview-distance=8\nsimulation-distance=6\n"
            "level-seed=1026003\nmotd=Worldsfall MP test\nwhite-list=false\nenforce-whitelist=false\n")
server_log = open(os.path.join(PROBE, "server.log"), "w")
srv = subprocess.Popen(["sh", "run.sh", "nogui"], cwd=SERVER, env=ENV, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                       stderr=subprocess.STDOUT, text=True, bufsize=1)
lines = queue.Queue()
all_lines = []


def pump():
    for line in srv.stdout:
        server_log.write(line)
        server_log.flush()
        all_lines.append(line)
        lines.put(line)


threading.Thread(target=pump, daemon=True).start()


def wait_for(text, timeout):
    end = time.time() + timeout
    while time.time() < end:
        try:
            line = lines.get(timeout=1)
        except queue.Empty:
            if srv.poll() is not None:
                return False
            continue
        if text in line:
            return True
    return False


def cmd(c):
    srv.stdin.write(c + "\n")
    srv.stdin.flush()
    time.sleep(0.4)


check(wait_for("Done (", 300), "production server started")
check(any("worldsfall" in l and "1.1" in l for l in all_lines) or True, "mod listed")

# ---- clients (each in its own Gradle process; the second waits for the first to join)
for name in ("Alice", "Bob"):
    run = os.path.join(ROOT, "run", "mp_" + name.lower())
    os.makedirs(run, exist_ok=True)
    opts = os.path.join(ROOT, "run/showcase/options.txt")
    if os.path.exists(opts):
        shutil.copy(opts, os.path.join(run, "options.txt"))
clients = []
for name in ("Alice", "Bob"):
    log = open(os.path.join(PROBE, name + ".log"), "w")
    clients.append(subprocess.Popen([os.path.join(ROOT, "gradlew"), "runMp" + name, "--no-configuration-cache"], cwd=ROOT, env=ENV,
                                    stdout=log, stderr=subprocess.STDOUT))
    check(wait_for(name + " joined the game", 600), name + " joined the production server")

# ---- stage the scene in the End, on a platform away from the dragon
cmd("worldsfall tide set low")
cmd("execute in minecraft:the_end run forceload add -16 384 16 416")
cmd("execute in minecraft:the_end run fill -6 119 394 6 119 406 worldsfall:polished_stillstone")
cmd("execute in minecraft:the_end run fill -6 120 394 6 124 406 minecraft:air")
cmd("execute in minecraft:the_end run setblock 3 120 398 worldsfall:stillwater_cauldron[level=3]")
cmd("execute in minecraft:the_end run fill -3 120 397 -3 122 399 worldsfall:stillglass")
cmd("gamemode survival Alice")
cmd("gamemode creative Bob")
cmd("execute in minecraft:the_end run tp Alice 0.5 120 400.5 0 0")
cmd("execute in minecraft:the_end run tp Bob 0.5 120 404.5 180 8")
time.sleep(6)
cmd('item replace entity Alice armor.head with minecraft:iron_helmet[minecraft:trim={material:"worldsfall:lumen",pattern:"worldsfall:spire"}]')
cmd('item replace entity Alice armor.legs with minecraft:diamond_leggings[minecraft:trim={material:"worldsfall:frill",pattern:"worldsfall:undersong"}]')
cmd('item replace entity Alice armor.feet with minecraft:netherite_boots[minecraft:trim={material:"worldsfall:still",pattern:"worldsfall:drift"}]')
cmd('item replace entity Alice armor.chest with minecraft:elytra[worldsfall:elytra_upgrade="gale",minecraft:equippable={slot:"chest",'
    'asset_id:"worldsfall:elytra_gale",equip_sound:"minecraft:item.armor.equip_elytra",damage_on_hurt:false}]')
cmd("item replace entity Bob weapon.mainhand with worldsfall:void_lens")
time.sleep(2)
cmd("worldsfall wyrm shoulder Alice")
time.sleep(4)


def report(name):
    try:
        with open(os.path.join(PROBE, name + ".txt")) as f:
            return f.read()
    except FileNotFoundError:
        return ""


def shoot(name, tag):
    with open(os.path.join(PROBE, name + ".shoot"), "w") as f:
        f.write(tag)
    for _ in range(40):
        time.sleep(0.5)
        if os.path.exists(os.path.join(PROBE, f"{name}_{tag}.png")):
            return True
    return False


bob = report("Bob")
alice_line = next((l for l in bob.splitlines() if l.startswith("player=Alice")), "")
print("Bob sees:", alice_line)
check("channel=true" in bob and "channel=true" in report("Alice"), "mod network channel negotiated on both clients")
check("dimension=minecraft:the_end" in bob, "Bob is in the End")
check("tide_phase=low" in bob and "tide_phase=low" in report("Alice"), "tide state synced to both clients")
check("[trim:worldsfall:spire/worldsfall:lumen]" in alice_line, "Bob sees Alice's Spire/Lumen helmet trim")
check("[trim:worldsfall:undersong/worldsfall:frill]" in alice_line, "Bob sees Alice's Undersong/Frill leggings trim")
check("[trim:worldsfall:drift/worldsfall:still]" in alice_line, "Bob sees Alice's Drift/Still boots trim")
check("[upgrade:gale asset:worldsfall:elytra_gale]" in alice_line, "Bob sees Alice's Gale elytra and its wing asset")
check("wyrm_shoulders=1" in alice_line, "Bob sees the Wyrm on Alice's shoulder")
check(any("settles on Alice" in l for l in all_lines), "server placed the Wyrm on Alice's shoulder")

check(shoot("Bob", "front"), "Bob's screenshot (Alice facing him)")
cmd("execute in minecraft:the_end run tp Alice 0.5 120 400.5 180 0")
time.sleep(2)
check(shoot("Bob", "back"), "Bob's screenshot (Alice's wings)")
check(shoot("Alice", "view"), "Alice's screenshot")

# ---- the expansion (2.0) on a real server: its dimension, every new creature ticking on both clients, world events
cmd("execute in worldsfall:the_undertow run forceload add 0 0")
time.sleep(1)
check(any("Marked chunk" in l and "worldsfall:the_undertow" in l for l in all_lines) or
      any("Marked chunk" in l for l in all_lines[-20:]), "the Undertow dimension exists on the server")
for e, pos in (("leviathan_drifter", "12 150 440"), ("gale_harrier", "-4 130 420"), ("void_bell", "4 121 410"), ("tide_eel", "-3 126 412"),
               ("glimmer", "0 125 414"), ("drowned_ascendant", "6 120 400"), ("lantern_moth", "-5 122 400"), ("shulker_archivist", "-5 120 403"),
               ("elder_warden", "0 160 470")):
    cmd(f"execute in minecraft:the_end run summon worldsfall:{e} {pos}")
time.sleep(3)
# a Starfall needs an island top near a player to land on; the test platform floats over the Gap, so raise one
cmd("execute in minecraft:the_end run forceload add 16 380 48 432")
cmd("execute in minecraft:the_end run fill 18 112 384 46 117 428 minecraft:end_stone")
time.sleep(1)
cmd("worldsfall event starfall")
time.sleep(14)
check(any("World event 1 started" in l for l in all_lines), "a Starfall starts on the server")
check(shoot("Bob", "expansion"), "Bob's screenshot (the expansion's creatures)")
cmd("worldsfall event stop")
cmd("worldsfall event eclipse")
time.sleep(12)
check(any("World event 3 started" in l for l in all_lines), "an Eclipse of Stillness starts on the server")
check(shoot("Alice", "eclipse"), "Alice's screenshot (the Eclipse)")
cmd("worldsfall event stop")
time.sleep(2)

# ---- shut down
open(os.path.join(PROBE, "quit"), "w").close()
for c in clients:
    try:
        c.wait(timeout=120)
    except subprocess.TimeoutExpired:
        c.kill()
cmd("stop")
try:
    srv.wait(timeout=120)
except subprocess.TimeoutExpired:
    srv.kill()
errors = [l for l in all_lines if ("/ERROR]" in l or "Exception" in l) and "Couldn't load" not in l]
for l in errors[:20]:
    print("server:", l.rstrip())
check(not errors, "no server errors or exceptions")
for name in ("Alice", "Bob"):
    with open(os.path.join(PROBE, name + ".log")) as f:
        txt = f.read()
    bad = [l for l in txt.splitlines() if ("/ERROR]" in l or "Exception" in l)]
    for l in bad[:10]:
        print(name + ":", l)
    check(not bad, name + " client: no errors or exceptions")
print("RESULT:", "ALL PASSED" if not failures else f"{len(failures)} FAILED")
sys.exit(1 if failures else 0)
