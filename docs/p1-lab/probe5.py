#!/usr/bin/env python3
"""P1 probe #5: answer server->client requests (bidirectional JSON-RPC),
then resume + subscribe + capture the full session snapshot."""
import json, os, subprocess, threading, time, collections

NODE = os.path.expanduser("~/.nvm/versions/node/v24.14.0/bin/node")
BUNDLE = os.path.expanduser("~/agent-lab/zcode-cli/zcode.cjs")
CWD = os.path.expanduser("~/agent-lab/experiments/p1-zcode-appserver")
RAW = open(os.path.join(CWD, "raw-traffic5.log"), "w")
MSGS = []
SERVER_REQS = []

p = subprocess.Popen([NODE, BUNDLE, "app-server", "--surface", "terminal"],
                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                     text=True, cwd=CWD)

def answer_server_requests():
    for raw in iter(p.stdout.readline, ""):
        if not raw.strip(): continue
        RAW.write(f"<<< {raw[:400000]}\n"); RAW.flush()
        try:
            m = json.loads(raw)
        except Exception:
            continue
        MSGS.append(m)
        # server->client REQUEST: has method + id
        if isinstance(m, dict) and "method" in m and "id" in m:
            SERVER_REQS.append(m)
            send({"id": m["id"], "result": {}})
            RAW.write(f">>> (auto-answered {m['method']} with {{}})\n"); RAW.flush()

threading.Thread(target=answer_server_requests, daemon=True).start()

def send(obj):
    line = json.dumps(obj)
    RAW.write(f">>> {line}\n"); RAW.flush()
    p.stdin.write(line + "\n"); p.stdin.flush()

def wait_resp(rid, secs=30):
    end = time.time() + secs
    while time.time() < end:
        for m in MSGS:
            if m.get("id") == rid and "method" not in m: return m
        time.sleep(0.15)
    return None

time.sleep(2.0)
send({"id": "e1", "method": "session/list", "params": {}})
sessions = wait_resp("e1")["result"]["sessions"]
sid = next(s["sessionId"] for s in sessions if "Continuing" in s.get("title", ""))
ws = next(s["workspace"] for s in sessions if "Continuing" in s.get("title", ""))

send({"id": "e2", "method": "session/resume", "params": {"sessionId": sid, "workspace": ws}})
r = wait_resp("e2")
print("resume ->", json.dumps(r.get("result", r.get("error")))[:500] if r else "TIMEOUT")

send({"id": "e3", "method": "session/subscribe",
      "params": {"sessionId": sid, "deliveryKind": "desktop-continuous"}})
r = wait_resp("e3", 20)
print("subscribe ->", json.dumps(r.get("result", r.get("error")))[:300] if r else "TIMEOUT")

time.sleep(6)
kinds = collections.Counter(m.get("kind") or ("req:" + m.get("method") if "method" in m else ("resp:" + str(m.get("id")))) for m in MSGS)
print("\nmessage kinds:", dict(kinds))

snap = next((m for m in MSGS if m.get("kind") == "snapshot"), None)
if snap:
    st = snap.get("state", snap)
    print("\nSNAPSHOT keys:", sorted(st.keys()))
    for key in ("control", "usage", "subagents", "plan", "goal", "queue", "meta", "config", "inputRouting", "availability", "rows", "pendingInteractions", "backgroundWorks"):
        if key in st:
            print(f"\n--- {key} ---")
            print(json.dumps(st[key])[:900])
else:
    print("\nno snapshot yet; last kinds:", [ (m.get('kind') or m.get('method')) for m in MSGS[-6:] ])

p.terminate()
try: p.wait(timeout=5)
except Exception: p.kill()
RAW.close()
