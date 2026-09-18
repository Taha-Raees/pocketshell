#!/usr/bin/env python3
"""P1 probe: drive `zcode app-server` (ZCode Protocol over stdio) and capture traffic.

Agent L(P), PARALLEL WORK 1. Laptop-side lab evidence for the PocketShell
ZCode Mode audit. No model turns are requested at protocol-discovery stage;
this probe exercises: handshake, runtime/capabilities, session/list,
usage/stats, mcp/list, session/subscribe (observe only).
"""
import json, os, subprocess, sys, threading, time, pathlib

NODE = os.path.expanduser("~/.nvm/versions/node/v24.14.0/bin/node")
BUNDLE = os.path.expanduser("~/agent-lab/zcode-cli/zcode.cjs")
CWD = os.path.expanduser("~/agent-lab/experiments/p1-zcode-appserver")
RAW = open(os.path.join(CWD, "raw-traffic.log"), "w")
SUMMARY = []

def send(obj):
    line = json.dumps(obj)
    RAW.write(f">>> {line}\n"); RAW.flush()
    p.stdin.write(line + "\n"); p.stdin.flush()
    print(f">>> {line[:200]}")

def reader():
    for raw in iter(p.stdout.readline, ""):
        if not raw.strip():
            continue
        RAW.write(f"<<< {raw}"); RAW.flush()
        try:
            msg = json.loads(raw)
        except Exception:
            SUMMARY.append(("UNPARSED", raw[:160])); continue
        kind = msg.get("kind") or msg.get("method") or ("resp:" + str(msg.get("id")))
        SUMMARY.append((kind, raw))
        print(f"<<< {kind}  {raw[:140]}")

p = subprocess.Popen(
    [NODE, BUNDLE, "app-server", "--surface", "terminal"],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    text=True, cwd=CWD, env={**os.environ},
)
t = threading.Thread(target=reader, daemon=True); t.start()
err_sink = threading.Thread(target=lambda: [err_sink_line(l) for l in iter(p.stderr.readline, "")], daemon=True)
def err_sink_line(l):
    RAW.write(f"ERR {l}"); RAW.flush()
err_sink.start()

def wait_for(pred, secs):
    end = time.time() + secs
    while time.time() < end:
        for k, raw in SUMMARY:
            if pred(k, raw):
                return True
        time.sleep(0.2)
    return False

try:
    time.sleep(2.0)
    send({"kind": "clientHello", "protocolVersion": 3, "clientId": "pocketshell-p1-probe",
          "clientKind": "mobileApp", "appVersion": "0.0.0-p1-probe"})
    wait_for(lambda k, r: k == "hello", 6)

    def rpc(i, method, params=None):
        send({"id": f"p1-{i}", "method": method, **({"params": params} if params is not None else {})})
        time.sleep(1.2)

    rpc(1, "runtime/capabilities", {})
    rpc(2, "session/list", {})
    rpc(3, "usage/stats", {})
    rpc(4, "mcp/list", {})
    rpc(5, "plugins/list", {})
    rpc(6, "automation/list", {})
    rpc(7, "offPeak/list", {})
    time.sleep(4)

    # try subscribing to the first existing session for observation
    sess = None
    for k, raw in SUMMARY:
        if k == "resp:p1-2":
            try:
                data = json.loads(raw)
                items = None
                for v in data.get("result", {}).values() if isinstance(data.get("result"), dict) else []:
                    if isinstance(v, list): items = v; break
                if items: sess = items[0]
            except Exception: pass
    if sess is not None:
        sid = sess.get("id") if isinstance(sess, dict) else None
        print(f"--> subscribing to {sid}")
        if sid:
            rpc(8, "session/subscribe", {"sessionId": sid})
            rpc(9, "session/read", {"sessionId": sid})
            time.sleep(5)
    else:
        print("--> no session/list result parsed; dumping nothing further")
finally:
    p.terminate()
    try: p.wait(timeout=5)
    except Exception: p.kill()
    RAW.close()
    print("\n===== MESSAGE KIND SUMMARY =====")
    from collections import Counter
    print(Counter(k for k, _ in SUMMARY))
    pathlib.Path(os.path.join(CWD, "raw-traffic.log")).chmod(0o644)
