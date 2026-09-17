#!/bin/sh
# PocketShell agent-signal bridge — the Linux-side emitter (M7.2 P10).
#
# WHAT IT IS. One tiny POSIX-sh script that an agent's OWN notification
# mechanism invokes (Claude Code hooks, Codex notify, OpenCode plugins,
# ZCode hooks, ...) to append one structured signal record to THIS
# session's launch-record file:
#
#   {"t":"signal","agent":"<agent>","kind":"<kind>","seq":<n>,
#    "pid":<parent-pid>,"start":<parent-starttime>,"data":"<payload>"}
#
# The record file IS the runtime generation (created fresh per launch by
# the Android side, named by the per-launch token): a signal can only ever
# land in the file of the launch that staged the hook config pointing
# here, so the signal is session-scoped and generation-safe by
# construction — there is no path by which one session's emitter can
# update another session's channel.
#
# CORRELATION. The emitter records its PARENT process's pid and
# /proc starttime (fields 4/22 of the parent's stat — comm-safe parse).
# The Android detector accepts a signal only when that parent is the
# session's anchored agent (pid + birth stamp match, the same
# pid-reuse-proof identity the launch anchor records) or still lives
# inside the session's correlation domain. A signal from a dead or
# foreign process is dropped, never interpreted.
#
# HONESTY. The emitter is a MOVER, not an interpreter: it relays the
# agent's own structured event, bounds it, and never invents state. The
# kind vocabulary is fixed (session_start, working, permission_request,
# attention, turn_complete, session_end); everything else the agent said
# rides inside "data" verbatim (single-line, escaped, bounded) for the
# Android side to read.
#
# FAILURE POSTURE. A bridge must never harm the agent that hosts it: every
# step is guarded, nothing here can exit non-zero into a hook result that
# a paranoid agent might treat as a veto (Claude Code exit 2 blocks tool
# calls), and the whole body is O(1) in the record file (bounded tail).
#
# USAGE: ps-emit.sh <agent-token> <kind> <record-file> [payload-json]
#   The payload arrives either as the 4th argument (Codex notify style:
#   `notify <json>` passes it as argv) or, absent, on stdin (hook style).
#   Both are optional: a bare signal with no payload is still a signal.

AGENT="$1"
KIND="$2"
RECORD="$3"
[ "$#" -gt 3 ] && PAYLOAD="$4" || PAYLOAD=$(cat 2>/dev/null)

# The generation guard: no live record file, no channel, nothing to do.
# (A deleted file means the session closed or the launch was superseded —
# late signals from a dying agent must die with it.)
[ -n "$RECORD" ] || exit 0
[ -f "$RECORD" ] || exit 0
[ -w "$RECORD" ] || exit 0

# Sanitize the payload into ONE safe JSON string value: compact whitespace,
# drop control bytes (keeping UTF-8 multibyte sequences intact), escape
# backslashes and quotes, bound the size, and strip any trailing
# backslashes a truncation could orphan (a lone trailing "\" would escape
# the record line's own closing quote). busybox tr/sed/cut cover every
# step; a huge or binary payload degrades to a bounded prefix — bounded
# loss, never corruption of the record line.
DATA=$(printf '%s' "$PAYLOAD" \
    | tr '\t\n\r' '   ' \
    | tr -d '\001-\010\013\014\016-\037\177' \
    | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' \
    | cut -c 1-800 \
    | sed 's/\\\\*$/_/')

# The parent identity (the agent process that invoked us). The comm-safe
# stat parse strips "pid (comm) " exactly like the launch anchor does;
# after it: field 1 = state, 2 = ppid, 20 = starttime.
PPARENT=""
PSTART=""
if [ -r "/proc/$PPID/stat" ]; then
    PSTAT=$(sed 's/^[0-9]* (.*) //' "/proc/$PPID/stat" 2>/dev/null) || PSTAT=""
    PPARENT=$(printf '%s' "$PSTAT" | cut -d ' ' -f2)
    PSTART=$(printf '%s' "$PSTAT" | cut -d ' ' -f20)
fi
case "$PPARENT" in ''|*[!0-9]*) PPARENT=0 ;; esac
case "$PSTART" in ''|*[!0-9]*) PSTART=0 ;; esac

# Sequence hint: how many signal records this generation already carries
# (bounded read). Strict ordering authority is the file's append order;
# the seq is advisory for humans and logs.
SEQ=$(tail -c 65536 "$RECORD" 2>/dev/null | grep -c '"t":"signal"') || SEQ=0
case "$SEQ" in ''|*[!0-9]*) SEQ=0 ;; esac
SEQ=$((SEQ + 1))

printf '{"t":"signal","agent":"%s","kind":"%s","seq":%s,"pid":%s,"start":%s,"data":"%s"}\n' \
    "$AGENT" "$KIND" "$SEQ" "$PPARENT" "$PSTART" "$DATA" >> "$RECORD" 2>/dev/null || :

exit 0
