#!/usr/bin/env python3
"""P1 probe #3: session/subscribe with deliveryKind — capture the full
session-state snapshot (usage, pendingInteractions, subagents, plan, rows)."""
import json, os, subprocess, threading, time, collections

NODE = os.path.expanduser("~/.nvm/versions/node/v24.14.0/bin/node")
BUNDLE = os.path.expanduser("~/agent-lab/zcode-cli/zcode.cjs")
CWD = os.path.expanduser("~/agent-lab/experiments/p1-zcode-appserver")
RAW = open(os.path.join(CWD, "raw-traffic3.log"), "w")
MSGS = []

p = subprocess.Popen([NODE, BUNDLE, "app-server", "--surface", "terminal"],
                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                     text=True, cwd=CWD)

def pump():
    for raw in iter(p.stdout.readline, ""):
        if not raw.strip(): continue
        RAW.write(f"<<< {raw[:200000]}\n"); RAW.flush()
        try: MSGS.append(json.loads(raw))
        except Exception: pass

threading.Thread(target=pump, daemon=True).start()

def send(obj):
    line = json.dumps(obj)
    RAW.write(f">>> {line}\n"); RAW.flush()
    p.stdin.write(line + "\n"); p.stdin.flush()

def wait_resp(rid, secs=20):
    end = time.time() + secs
    while time.time() < end:
        for m in MSGS:
            if m.get("id") == rid: return m
        time.sleep(0.15)
    return None

time.sleep(2.0)
send({"id": "q1", "method": "session/list", "params": {}})
lst = wait_resp("q1")
sessions = lst["result"]["sessions"]
# pick the session with subagents (Continuing Work on Existing Repo)
sid = next(s["sessionId"] for s in sessions if "Continuing" in s.get("title", ""))
print(f"target session: {sid}")

send({"id": "q2", "method": "session/subscribe",
      "params": {"sessionId": sid, "deliveryKind": "desktop-continuous"}})
r = wait_resp("q2", 15)
print("subscribe result:", json.dumps(r.get("result", r.get("error")))[:400] if r else "TIMEOUT")

# collect snapshot + deltas for a while
time.sleep(8)
kinds = collections.Counter()
snap = None
for m in MSGS:
    k = m.get("kind") or m.get("method") or ("resp:" + str(m.get("id")))
    kinds[k] += 1
    if m.get("kind") == "snapshot" and isinstance(m.get("state"), dict):
        snap = m["state"]
    elif m.get("kind") == "snapshot":
        snap = m
print("\nmessage kinds:", dict(kinds))

if snap:
    st = snap.get("state", snap)
    print("\nSNAPSHOT top-level keys:", sorted(st.keys()))
    for key in ("control", "usage", "subagents", "plan", "goal", "queue", "meta", "config", "inputRouting", "availability", "rows"):
        if key in st:
            print(f"\n--- {key} ---")
            print(json.dumps(st[key])[:800])
else:
    print("no snapshot captured; dumping last 3 raw kinds")
    for m in MSGS[-5:]:
        print(json.dumps(m)[:300])

p.terminate()
try: p.wait(timeout=5)
except Exception: p.kill()
RAW.close()
