#!/usr/bin/env python3
"""P1 probe #2: correct params — usage/stats, mcp/list, plugins/list,
session/subscribe + session/read (observe-only), session/subagents."""
import json, os, subprocess, threading, time, collections

NODE = os.path.expanduser("~/.nvm/versions/node/v24.14.0/bin/node")
BUNDLE = os.path.expanduser("~/agent-lab/zcode-cli/zcode.cjs")
CWD = os.path.expanduser("~/agent-lab/experiments/p1-zcode-appserver")
RAW = open(os.path.join(CWD, "raw-traffic2.log"), "w")
MSGS = []
LOCK = threading.Lock()

p = subprocess.Popen([NODE, BUNDLE, "app-server", "--surface", "terminal"],
                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                     text=True, cwd=CWD)

def pump():
    for raw in iter(p.stdout.readline, ""):
        if not raw.strip(): continue
        RAW.write(f"<<< {raw}"); RAW.flush()
        try: MSGS.append(json.loads(raw))
        except Exception: pass

threading.Thread(target=pump, daemon=True).start()

def send(obj):
    line = json.dumps(obj)
    RAW.write(f">>> {line}\n"); RAW.flush()
    p.stdin.write(line + "\n"); p.stdin.flush()

def wait_resp(rid, secs=8):
    end = time.time() + secs
    while time.time() < end:
        for m in MSGS:
            if m.get("id") == rid: return m
        time.sleep(0.15)
    return None

time.sleep(2.0)
send({"id": "r1", "method": "session/list", "params": {}})
lst = wait_resp("r1")
sessions = lst["result"]["sessions"] if lst and "result" in lst else []
ws = sessions[0]["workspace"] if sessions else None
sid = sessions[0]["sessionId"] if sessions else None
print(f"sessions={len(sessions)} workspace_keys={sorted(ws.keys()) if isinstance(ws, dict) else ws}")
ws_short = {k: ws[k] for k in list(ws)[:4]} if isinstance(ws, dict) else ws
print("workspace sample:", json.dumps(ws_short)[:200])

def rpc(rid, method, params):
    send({"id": rid, "method": method, "params": params})
    r = wait_resp(rid)
    out = json.dumps(r.get("result", r.get("error"))) if r else "TIMEOUT"
    print(f"\n### {method} -> {out[:1400]}")
    return r

rpc("r2", "usage/stats", {"range": "all"})
rpc("r3", "mcp/list", {"workspace": ws} if ws else {})
rpc("r4", "plugins/list", {"workspace": ws} if ws else {})
if sid:
    rpc("r5", "session/subagents", {"sessionId": sid})
    rpc("r6", "session/subscribe", {"sessionId": sid})
    time.sleep(2)
    before = len(MSGS)
    time.sleep(4)
    print(f"\n### subscribed; async messages while idle: {collections.Counter(m.get('kind') or m.get('method') or 'resp' for m in MSGS[before:])}")
    send({"id": "r7", "method": "session/read", "params": {"sessionId": sid}})
    r = wait_resp("r7", 6)
    if r and "result" in r:
        res = r["result"]
        print(f"### session/read -> keys={sorted(res.keys()) if isinstance(res, dict) else type(res)}")
        if isinstance(res, dict):
            for k, v in res.items():
                print(f"    {k}: {json.dumps(v)[:260]}")

p.terminate()
try: p.wait(timeout=5)
except Exception: p.kill()
RAW.close()
print("\nDONE")
