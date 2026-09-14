#!/bin/sh
# Targeted Phase-5 probe (Phase 4 audit, Agent Z): a nested proot plays "the
# app's proot" (direct child), with a foreground shell child and a setsid'd
# "server" inside. SIGKILL the nested proot (= TerminalSession.closeSession
# semantics with --kill-on-exit present) and observe who survives.
set -u
NESTED="proot --kill-on-exit --link2symlink --rootfs=/ --root-id"
$NESTED /bin/sh -c 'setsid sleep 300 & sleep 301; echo FG_DONE' &
NP=$!
sleep 1.5
echo "before kill:"
pgrep -af "sleep 30[01]" | sed 's/^/  /'
kill -9 "$NP"
wait "$NP" 2>/dev/null
echo "nested proot reaped"
sleep 1.5
echo "after SIGKILL of nested proot:"
pgrep -af "sleep 30[01]" | sed 's/^/  /' || echo "  (none survived)"
kill -9 $(pgrep -f "sleep 30[01]") 2>/dev/null
echo "cleanup done"
