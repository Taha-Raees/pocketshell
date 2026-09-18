#!/usr/bin/env python3
"""P1 probe #4: session/resume then session/subscribe — capture the FULL
session-state snapshot (control/usage/subagents/plan/rows) of a persisted session."""
import json, os, subprocess, threading, time, collections

NODE = os.path.expanduser("~/.nvm/versions/node/v24.14.0/bin/node")
BUNDLE = os.path.expanduser("~/agent-lab/zcode-cli/zcode.cjs")
CWD = os.path.expanduser("~/agent-lab/experiments/p1-zcode-appserver")
RAW = open(os.path.join(CWD, "raw-traffic4.log"), "w")
MSGS = []

p = subprocess.Popen([NODE, BUNDLE, "app-server", "--surface", "terminal"],
                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                     text=True, cwd=CWD)

def pump():
    for raw in iter(p.stdout.readline, ""):
        if not raw.strip(): continue
        RAW.write(f"<<< {raw[:400000]}\n"); RAW.flush()
        try: MSGS.append(json.loads(raw))
        except Exception: RAW.write("UNPARSED LINE\n")

threading.Thread(target=pump, daemon=True).start()

def send(obj):
    line = json.dumps(obj)
    RAW.write(f">>> {line}\n"); RAW.flush()
    p.stdin.write(line + "\n"); p.stdin.flush()

def wait_resp(rid, secs=25):
    end = time.time() + secs
    while time.time() < end:
        for m in MSGS:
            if m.get("id") == rid: return m
        time.sleep(0.15)
    return None

time.sleep(2.0)
send({"id": "w1", "method": "session/list", "params": {}})
sessions = wait_resp("w1")["result"]["sessions"]
sid = next(s["sessionId"] for s in sessions if "Continuing" in s.get("title", ""))
ws = next(s["workspace"] for s in sessions if "Continuing" in s.get("title", ""))
print("resume target:", sid)

send({"id": "w2", "method": "session/resume", "params": {"sessionId": sid, "workspace": ws}})
r = wait_resp("w2", 30)
print("resume ->", json.dumps(r.get("result", r.get("error")))[:600] if r else "TIMEOUT")

send({"id": "w3", "method": "session/subscribe",
      "params": {"sessionId": sid, "deliveryKind": "desktop-continuous"}})
r = wait_resp("w3", 20)
print("subscribe ->", json.dumps(r.get("result", r.get("error")))[:400] if r else "TIMEOUT")

time.sleep(6)
kinds = collections.Counter(m.get("kind") or m.get("method") or ("resp:" + str(m.get("id"))) for m in MSGS)
print("\nmessage kinds:", dict(kinds))

snap = None
for m in MSGS:
    if m.get("kind") == "snapshot":
        snap = m
if snap:
    st = snap.get("state", snap)
    print("\nSNAPSHOT keys:", sorted(st.keys()))
    for key in ("control", "usage", "subagents", "plan", "goal", "queue", "meta", "config", "inputRouting", "availability", "rows", "pendingInteractions", "backgroundWorks"):
        if key in st:
            print(f"\n--- {key} ---")
            print(json.dumps(st[key])[:1000])
else:
    print("\nno snapshot kind yet")

p.terminate()
try: p.wait(timeout=5)
except Exception: p.kill()
RAW.close()
