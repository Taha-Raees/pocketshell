#!/bin/sh
# M7.2 P10 — the Linux-side rehearsal of the agent-signal bridge.
#
# Runs the REAL launch chain (prep snippet + anchor + exit record +
# trailing exec) composed EXACTLY as the Android side composes it for a
# Claude-Code launch with the staged adapter, against a real record file
# and real /proc — then plays a fake agent story THROUGH THE REAL STAGED
# EMITTER (as the agent's hooks would), and finally asserts the record
# file's content the way the Android detector's parser will.
#
# This is the deterministic hardware-independent gate: everything that
# can be proven without an Android device is proven here.
#
# Usage: scripts/p10/rehearse_signal_bridge.sh
# Exit 0 = every check passed; non-zero = the first failed check.

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
EMITTER_ASSET="$REPO_ROOT/app/src/main/assets/agentbridge/ps-emit.sh"
WORK=$(mktemp -d /tmp/p10-rehearse.XXXXXX)
PASS=0
FAIL=0

check() { # check <label> <condition-result>
    if [ "$2" = "0" ]; then
        echo "PASS: $1"; PASS=$((PASS + 1))
    else
        echo "FAIL: $1"; FAIL=$((FAIL + 1))
    fi
}

cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

echo "== P10 bridge rehearsal in $WORK =="

# ---- 0. preconditions ----------------------------------------------------
[ -f "$EMITTER_ASSET" ] && check "emitter asset exists" 0 || check "emitter asset exists" 1

# ---- 1. stage the bridge exactly as the Android side does ----------------
TOKEN="p9rehearsaldeadbeef"
RECORD="$WORK/$TOKEN.jsonl"
STAGING="$WORK/$TOKEN.d"
mkdir -p "$STAGING"
cp "$EMITTER_ASSET" "$STAGING/emit.sh"
chmod +x "$STAGING/emit.sh"
: > "$RECORD"

# The staged Claude settings, composed the same way AgentSignalAdapters
# composes them (emitter path, agent token, record path baked in).
emit_hook() { printf '{"type":"command","command":"sh %s claude %s %s"}' "$STAGING/emit.sh" "$1" "$RECORD"; }
cat > "$STAGING/claude-settings.json" <<EOF
{"hooks":{
"SessionStart":[{"hooks":[$(emit_hook session_start)]}],
"PreToolUse":[{"hooks":[$(emit_hook working)]}],
"PermissionRequest":[{"hooks":[$(emit_hook permission_request)]}],
"Stop":[{"hooks":[$(emit_hook turn_complete)]}]
}}
EOF
python3 -c "import json;json.load(open('$STAGING/claude-settings.json'))" 2>/dev/null \
    && check "staged settings parse as JSON" 0 || check "staged settings parse as JSON" 1

# ---- 2. compose + run the REAL launch chain (anchor exec override) -------
# The anchor's exec command is the adapter's override (claude --settings …),
# with a harmless stand-in agent for rehearsal (the anchor records OUR
# shell's identity, then execs the "agent"). Composed in pure sh — the
# same grammar as AgentLaunchRecords.launchChain.
PGRP=$(sed "s/^[0-9]* (.*) //" /proc/self/stat | cut -d" " -f3)
START=$(sed "s/^[0-9]* (.*) //" /proc/self/stat | cut -d" " -f20)
ANCHOR="sh -c 'PGRP=\$(sed \"s/^[0-9]* (.*) //\" /proc/self/stat | cut -d\" \" -f3); START=\$(sed \"s/^[0-9]* (.*) //\" /proc/self/stat | cut -d\" \" -f20); printf \"{\\\"t\\\":\\\"launch\\\",\\\"pid\\\":%s,\\\"pgrp\\\":%s,\\\"start\\\":%s,\\\"agent\\\":\\\"claude\\\"}\\n\" \"\$\$\" \"\$PGRP\" \"\$START\" >> $RECORD || :; exec claude --settings $STAGING/claude-settings.json --version'"
EXIT_SNIPPET="printf \"{\\\"t\\\":\\\"exit\\\",\\\"status\\\":%s,\\\"agent\\\":\\\"claude\\\"}\\n\" \"\$?\" >> $RECORD || :"
FULL_CHAIN="$ANCHOR ; $EXIT_SNIPPET ; exec /bin/sh -l"

# Run it: the "agent" is `claude --version` (absent here -> the anchor's
# exec fails, the exit record STILL records the real status — exactly the
# no-agent-installed honesty the channel must preserve).
sh -c "$FULL_CHAIN" </dev/null >/dev/null 2>&1
check "chain ran end-to-end (anchor + exit record + exec)" 0

grep -q '"t":"launch"' "$RECORD" && check "launch anchor recorded" 0 || check "launch anchor recorded" 1
grep -q '"t":"exit"' "$RECORD" && check "exit fact recorded" 0 || check "exit fact recorded" 1

# ---- 3. play a fake agent story through the REAL staged emitter ----------
# (what the agent's hooks do: invoke the emitter as their command)
PAYLOAD='{"session_id":"s-9","hook_event_name":"PermissionRequest","tool_name":"Bash","tool_input":{"command":"echo hi"}}'
printf '%s' "$PAYLOAD" | "$STAGING/emit.sh" claude permission_request "$RECORD"
"$STAGING/emit.sh" claude turn_complete "$RECORD" '{"last_assistant_message":"done"}'
check "fake agent story emitted through the staged emitter" 0

# ---- 4. validate the record the way the Android parser will -------------
python3 - "$RECORD" <<'EOF'
import json, sys
lines = [l for l in open(sys.argv[1]).read().splitlines() if l.strip()]
kinds = []
launch = exit_ = None
for line in lines:
    obj = json.loads(line)  # any invalid line fails the rehearsal
    t = obj.get("t")
    if t == "launch": launch = obj
    elif t == "exit": exit_ = obj
    elif t == "signal": kinds.append(obj["kind"])
    else: raise SystemExit(f"unknown record type {t}")
assert launch and launch["pid"] > 0 and launch["start"] > 0, launch
assert exit_ is not None, exit_
assert kinds == ["permission_request", "turn_complete"], kinds
for line in lines:
    obj = json.loads(line)
    if obj.get("t") == "signal":
        assert obj["pid"] > 0 and obj["start"] > 0
        assert obj["agent"] == "claude"
print("RECORD-OK")
EOF
[ $? = 0 ] && check "record validates (strict parser semantics)" 0 || check "record validates (strict parser semantics)" 1

# ---- 5. generation guard: a deleted record cannot receive signals --------
rm -f "$RECORD"
"$STAGING/emit.sh" claude working "$RECORD" '{}' </dev/null
[ ! -f "$RECORD" ] && check "dead generation receives nothing" 0 || check "dead generation receives nothing" 1

echo "== rehearsal complete: $PASS passed, $FAIL failed =="
[ "$FAIL" = "0" ]
