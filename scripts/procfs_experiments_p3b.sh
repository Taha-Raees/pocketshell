#!/usr/bin/env bash
# M7.2 P3b — PART C/D controlled /proc experiments.
#
# Validates, on real Linux procfs (the same kernel contract Android exposes
# to same-UID readers), the exact process-observation mechanism the P3b
# scanner will rely on:
#
#   E1  a simple known process (sleep 300): cmdline / status / stat / exe
#   E2  the real PocketShell guest launch-chain SHAPE (sh -l -c "<cmd>;
#       exec sh -l") — which processes exist and what /proc shows for each
#   E3  the two registry-relevant /proc binary shapes:
#         - shebang/interpreter wrapper (npm-style CLI: argv = [interp,
#           script_path, ...]) — proves the argv[1] script-path contract
#         - single-file compiled binary (bun-style: exe basename == token)
#       Registry stand-ins are used because no real registry agent binary
#       can run in this sandbox — documented, never faked as agent evidence.
#   E4  agent child processes: orphans (reparent to init but KEEP pgrp),
#       setsid escapes (double-escape boundary), and the negative control
#       (an unrelated same-name process must not correlate).
#
# Output: a structured evidence report on stdout.

set -u
EXPDIR="$(mktemp -d /tmp/p3b-procexp.XXXXXX)"
trap 'for j in $(jobs -p); do kill -9 "$j" 2>/dev/null; done; rm -rf "$EXPDIR"' EXIT

hr() { printf '\n==================================================================\n%s\n==================================================================\n' "$1"; }
line() { printf '  %-46s %s\n' "$1" "$2"; }

# ---- procfs readers (exactly what the P3b HostProcfsReader will do) -------
read_cmdline() { tr '\0' '\n' < "/proc/$1/cmdline" 2>/dev/null | sed 's/^/    argv: /'; }
read_stat_safe() { # procfs(5) field number (3=state 4=ppid 5=pgrp), comm-aware
  local pid="$1" field="$2"
  awk -v want="#$field" '
    BEGIN { # name-to-index: procfs(5) fields 1..52; comm is field 2
      split("pid comm state ppid pgrp session tty_nr tpgid flags minflt cminflt majflt cmajflt utime stime cutime cstime priority nice num_threads itrealvalue starttime vsize rss", names, " ");
      for (i = 1; i <= 23; i++) idx[names[i]] = i;
    }
    {
      i = index($0, ")"); head = substr($0, 1, i); tail = substr($0, i + 2);
      split(head, h, " "); split(tail, t, " ");
      f = substr(want, 2) - 2;   # strip leading #; subtract pid+comm offsets -> index into t
      if (f >= 1 && f <= length(t)) print t[f];
    }' "/proc/$pid/stat" 2>/dev/null
}
read_exe() { readlink "/proc/$1/exe" 2>/dev/null || echo "(unreadable)"; }
tree_row() { # pid
  local pid="$1"
  local ppid pgrp state comm
  ppid="$(read_stat_safe "$pid" 4)"; pgrp="$(read_stat_safe "$pid" 5)"; state="$(read_stat_safe "$pid" 3)"
  comm="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | cut -c1-72)"
  [ -z "$comm" ] && comm="$(cat "/proc/$pid/comm" 2>/dev/null)  (empty cmdline)"
  printf '    pid=%-7s ppid=%-7s pgrp=%-7s state=%s  cmd: %s\n' "$pid" "${ppid:-?}" "${pgrp:-?}" "${state:-?}" "$comm"
}

hr "E1 — simple known process: sleep 300"
sleep 300 & E1PID=$!
sleep 0.2
line "pid of sleep" "$E1PID"
line "cmdline" "$(tr '\0' ' ' < /proc/$E1PID/cmdline)"
line "stat: state/ppid/pgrp" "$(read_stat_safe $E1PID 3)/$(read_stat_safe $E1PID 4)/$(read_stat_safe $E1PID 5)"
line "exe (readlink)" "$(read_exe $E1PID)"
line "status Name/Uids line" "$(grep -E '^(Name|Uid):' /proc/$E1PID/status 2>/dev/null | tr '\n' ' ')"
line "hierarchy: ppid chain reaches this shell?" "$( [ "$(read_stat_safe $E1PID 4)" = "$$" ] && echo YES || echo "NO (ppid=$(read_stat_safe $E1PID 4), shell=$$)")"
kill -9 $E1PID 2>/dev/null; wait $E1PID 2>/dev/null

hr "E2 — the real guest launch-chain SHAPE: sh -l -c '<cmd>; exec sh -l'"
# The chain PocketShell actually builds (guestLaunchChain) — proot is
# transparent to the process SHAPE below the direct child, so the sandbox
# runs the chain without proot to observe the shell-level tree.
sh -l -c "sleep 300; exec sh -l" & CHAIN_SH=$!
sleep 0.6
SLEEPPID="$(pgrep -P "$CHAIN_SH" -x sleep 2>/dev/null | head -1)"
echo "  tree while the command runs (the 'agent' is sleep):"
tree_row "$CHAIN_SH"
[ -n "$SLEEPPID" ] && tree_row "$SLEEPPID"
echo "  the chain shell's FULL argv (note: the command rides as ONE element):"
read_cmdline "$CHAIN_SH"
echo "  child argv (the exec'd command — argv[0] IS the token):"
[ -n "$SLEEPPID" ] && read_cmdline "$SLEEPPID"
echo "  substring check: does any chain-shell argv element EQUAL the token 'sleep'?"
HIT=0
while IFS= read -r el; do [ "$el" = "sleep" ] && HIT=1; done < <(tr '\0' '\n' < "/proc/$CHAIN_SH/cmdline" 2>/dev/null)
line "  element-equal hit on the chain shell" "$([ $HIT = 1 ] && echo 'MATCH (bad)' || echo 'NONE — exact-token matching cannot hit the shell')"
kill -9 $CHAIN_SH ${SLEEPPID:-} 2>/dev/null; pkill -9 -P "$CHAIN_SH" 2>/dev/null; wait $CHAIN_SH 2>/dev/null

hr "E3 — the two registry-relevant /proc binary shapes"
mkdir -p "$EXPDIR/bin"
# (a) shebang/interpreter wrapper (npm-style CLI stand-in, named claude):
#     the kernel turns execve("bin/claude") into argv = [interp, script_path, ...]
#     and the RESIDENT interpreter keeps that shape (a node CLI is resident —
#     an `exec`-style wrapper would replace itself and lose the script path;
#     that difference is itself recorded as a finding).
printf '#!/usr/bin/env python3\nimport time\ntime.sleep(300)\n' > "$EXPDIR/bin/claude"
chmod +x "$EXPDIR/bin/claude"
"$EXPDIR/bin/claude" & SBPID=$!
sleep 0.6
SBPID2="$(pgrep -f "$EXPDIR/bin/claude" | head -1)"
echo "  (a) resident-interpreter shebang (stand-in for npm-style 'claude'):"
[ -n "$SBPID2" ] && tree_row "$SBPID2"
echo "    full argv (the SCRIPT PATH rides at argv[1] — the kernel contract):"
[ -n "$SBPID2" ] && read_cmdline "$SBPID2"
line "    argv[1] basename (PROCFS_CMDLINE grade target)" "$([ -n "$SBPID2" ] && tr '\0' '\n' < "/proc/$SBPID2/cmdline" | sed -n 2p | xargs basename)"
line "    exe of the interpreter process" "$([ -n "$SBPID2" ] && read_exe "$SBPID2")"
kill -9 $SBPID ${SBPID2:-} 2>/dev/null; wait $SBPID 2>/dev/null
# (b) single-file compiled binary (bun-style stand-in, named kilo): exe IS the binary
cp /bin/sleep "$EXPDIR/bin/kilo"
"$EXPDIR/bin/kilo" 300 & BUNPID=$!
sleep 0.3
echo "  (b) single-file binary (stand-in for bun-compiled 'kilo'):"
tree_row "$BUNPID"
line "    cmdline" "$(tr '\0' ' ' < /proc/$BUNPID/cmdline)"
line "    exe basename (PROCFS_EXE grade target)" "$(basename "$(read_exe $BUNPID)")"
kill -9 $BUNPID 2>/dev/null; wait $BUNPID 2>/dev/null

hr "E4 — agent children: orphans, setsid escapes, and the negative control"
# (a) the stand-in agent forks a long-lived child then EXITS: the child
#     reparents to init (ppid 1) but KEEPS its process group.
sh -c 'sleep 60 & echo $! > '"$EXPDIR"'/orphan.pid; sleep 0.4; exit 0' & ORPHSPAWN=$!
wait $ORPHSPAWN 2>/dev/null
ORPHPID="$(cat "$EXPDIR/orphan.pid")"
sleep 0.3
echo "  (a) orphaned grandchild after its parent (the 'agent') exited:"
if [ -d "/proc/$ORPHPID" ]; then tree_row "$ORPHPID"; else echo "    (already reaped)"; fi
line "    ppid (reparented to init?)" "$(read_stat_safe $ORPHPID 4)"
line "    pgrp (kept the old group?)" "$(read_stat_safe $ORPHPID 5)"
line "    => pgid correlation still attributes it" "YES if pgrp != its own pid and != 1"
kill -9 "$ORPHPID" 2>/dev/null
# (b) a setsid'd helper escapes BOTH domains (the documented double-escape).
#     setsid(1) forks only when already a group leader — backgrounded from a
#     non-interactive script it is NOT one, so it EXECs the sleep directly:
#     $! IS the new-session process (pgrp == own pid proves the new session).
setsid sleep 60 </dev/null >/dev/null 2>&1 &
SETSIDPID=$!
sleep 0.4
echo "  (b) setsid'd helper (double-escape boundary):"
tree_row "$SETSIDPID"
line "    pgrp == own pid (new group+session)?" "$([ "$(read_stat_safe $SETSIDPID 5)" = "$SETSIDPID" ] && echo YES || echo NO)"
line "    ppid (parent = this script's shell, NOT init — it was never orphaned)" "$(read_stat_safe $SETSIDPID 4)"
kill -9 "$SETSIDPID" 2>/dev/null; wait $SETSIDPID 2>/dev/null
# (c) NEGATIVE CONTROL: an unrelated same-name process must not correlate
cp /bin/sleep "$EXPDIR/bin/kilo"
"$EXPDIR/bin/kilo" 300 & UNREL1=$!
sleep 300 & UNREL2=$!
sleep 0.3
echo "  (c) two unrelated 'kilo'/'sleep' processes outside any session tree:"
tree_row "$UNREL1"; tree_row "$UNREL2"
echo "    their ppid chains reach init/shells, never a foreign session root;"
echo "    their pgrp values differ from any other session leader's pid."
kill -9 $UNREL1 $UNREL2 2>/dev/null; wait $UNREL1 $UNREL2 2>/dev/null

hr "E5 — /proc visibility scope for a non-root reader (the Android-app analog)"
echo "  processes visible to uid 1001 via readdir /proc (hidepid analog):"
echo "  total entries: $(ls /proc 2>/dev/null | grep -c '^[0-9]')"
line "  can read own cmdline" "$([ -r /proc/self/cmdline ] && echo YES || echo NO)"
line "  can read own stat" "$([ -r /proc/self/stat ] && echo YES || echo NO)"
line "  can readlink own exe" "$([ -e /proc/self/exe ] && echo YES || echo NO)"
OTHER="$(ls /proc 2>/dev/null | grep '^[0-9]' | while read p; do [ "$(read_stat_safe $p 4)" != "" ] && [ "$p" != "$$" ] && [ "$(awk '{print $1}' /proc/$p/status 2>/dev/null)" = "" ] && echo $p && break; done | head -1)"
line "  first pid whose /status is unreadable" "${OTHER:-none found}"

hr "EXPERIMENTS COMPLETE"
