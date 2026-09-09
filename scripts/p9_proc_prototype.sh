#!/bin/bash
# M7.2 P9 — PART B/D prototype: empirical /proc verification of the facts
# the session-bound observation architecture relies on. REAL processes,
# REAL /proc — the P3b controlled-experiment discipline (E1..E4b precedent)
# repeated for the P9 design questions.
#
#   E1  launcher-chain topology (setsid root -> sh -c chain -> nested
#       sh records+execs agent): ppid-chain + pgrp correlation, cwd / vs
#       subdirectory, exe/cmdline match shapes, anchor pid/starttime.
#   E3  anchor identity across exec: recorded pid/pgrp/start == the exec'd
#       agent's live /proc values; the outer chain's $? IS the agent's
#       real exit status (0 and non-zero arms).
#   E5  the stat field-index contract: after stripping "pid (comm) ",
#       state=1, ppid=2, pgrp=3, starttime=20 of the remainder.
#   E6  orphan semantics: an agent that forks a worker and exits — the
#       worker reparents AWAY and (no job control) keeps the chain pgrp.
set -u
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  FAIL: $1"; }
check(){ if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (got '$2', want '$3')"; fi; }

WORK=$(mktemp -d /tmp/p9proto.XXXXXX)
trap 'kill -9 $(jobs -p) 2>/dev/null; wait 2>/dev/null; rm -rf "$WORK"' EXIT

cat > "$WORK/kilo" <<'EOF'
#!/bin/sh
sleep 2
exit ${KILO_EXIT:-0}
EOF
chmod +x "$WORK/kilo"

# comm-aware stat field extraction (mirrors HostProcfsReader: parse AFTER
# the last ')' so comm entries with spaces/parens cannot shift indices).
stat_rest() { sed 's/^[0-9]* (.*) //' "/proc/$1/stat" 2>/dev/null; }
pgrp_of()   { stat_rest "$1" | cut -d' ' -f3;  }
start_of()  { stat_rest "$1" | cut -d' ' -f20; }
ppid_of()   { stat_rest "$1" | cut -d' ' -f2;  }
alive()     { [ -d "/proc/$1" ] && [ -n "$(stat_rest "$1")" ]; }

echo "== E5: stat field-index contract =="
sleep 5 & GUINEA=$!
check "E5 pgrp is remainder-field 3"  "$(pgrp_of "$GUINEA")"  "$(ps -o pgid= -p "$GUINEA" | tr -d ' ')"
check "E5 ppid is remainder-field 2"  "$(ppid_of "$GUINEA")"  "$$"
check "E5 starttime is remainder-field 20 (positive ticks)" \
  "$([ "$(start_of "$GUINEA")" -gt 0 ] 2>/dev/null && echo yes)" "yes"
kill "$GUINEA" 2>/dev/null; wait "$GUINEA" 2>/dev/null

echo "== E1+E4: launcher-chain topology, cwd / vs subdirectory =="
mkdir -p "$WORK/sub/dir"
for CWD in "/" "$WORK/sub/dir"; do
  rm -f "$WORK/anchor.txt"
  setsid sh -c "
    cd '$CWD' 2>/dev/null
    sh -c '
      PGRP=\$(sed \"s/^[0-9]* (.*) //\" /proc/self/stat | cut -d\" \" -f3)
      START=\$(sed \"s/^[0-9]* (.*) //\" /proc/self/stat | cut -d\" \" -f20)
      printf \"{\\\"pid\\\":%s,\\\"pgrp\\\":%s,\\\"start\\\":%s}\\n\" \"\$\$\" \"\$PGRP\" \"\$START\" >> \"$WORK/anchor.txt\"
      exec \"$WORK/kilo\"
    '
  " >/dev/null 2>&1 &
  ROOT=$!
  for i in $(seq 1 50); do [ -s "$WORK/anchor.txt" ] && break; sleep 0.05; done
  sleep 0.2   # let the exec settle
  LINE=$(cat "$WORK/anchor.txt" 2>/dev/null)
  APID=$(printf '%s' "$LINE" | sed 's/.*"pid":\([0-9]*\).*/\1/')
  if alive "$APID"; then
    ok "E1[$CWD] anchor pid alive during run (pid $APID)"
    check "E1[$CWD] anchor pgrp == root pgrp (non-interactive chain)" \
      "$(pgrp_of "$APID")" "$(pgrp_of "$ROOT")"
    check "E1[$CWD] anchor is a direct child of the chain shell (exec preserved pid)" \
      "$(ppid_of "$APID")" "$ROOT"
    check "E1[$CWD] recorded starttime == live /proc starttime (anti-reuse key) [line=$LINE]" \
      "$(printf '%s' "$LINE" | grep -o '\"start\":[0-9]*' | cut -d: -f2)" "$(start_of "$APID")"
    EXE=$(readlink "/proc/$APID/exe" 2>/dev/null)
    ARGV1=$(tr '\0' '\n' < "/proc/$APID/cmdline" 2>/dev/null | sed -n 2p)
    MATCH=no
    [ "$(basename "$EXE" 2>/dev/null)" = "kilo" ] && MATCH=rule1-exe
    [ "$(basename "$ARGV1" 2>/dev/null)" = "kilo" ] && MATCH=rule2-cmdline
    case "$MATCH" in
      rule1-exe) ok "E4[$CWD] matcher rule 1 fires (exe basename == token)";;
      rule2-cmdline) ok "E4[$CWD] matcher rule 2 fires (shebang contract: argv[1] basename == token, exe=$EXE)";;
      *) bad "E4[$CWD] neither matcher rule fired (exe=$EXE argv1=$ARGV1)";;
    esac
    CWD_OF=$(readlink "/proc/$APID/cwd" 2>/dev/null)
    check "E4[$CWD] agent actually runs at the requested cwd" "$CWD_OF" "$CWD"
  else
    bad "E1[$CWD] anchor pid missing/dead (line: '$LINE')"
  fi
  wait "$ROOT" 2>/dev/null
done

echo "== E3: anchor identity across exec + real exit status =="
run_chain() { # $1 = agent exit code, $2 = output file
  setsid sh -c "
    KILO_EXIT='$1' sh -c '
      PGRP=\$(sed \"s/^[0-9]* (.*) //\" /proc/self/stat | cut -d\" \" -f3)
      START=\$(sed \"s/^[0-9]* (.*) //\" /proc/self/stat | cut -d\" \" -f20)
      printf \"{\\\"t\\\":\\\"launch\\\",\\\"pid\\\":%s,\\\"pgrp\\\":%s,\\\"start\\\":%s}\\n\" \"\$\$\" \"\$PGRP\" \"\$START\" >> \"$2\"
      exec \"$WORK/kilo\"
    '
    RC=\$?
    printf \"{\\\"t\\\":\\\"exit\\\",\\\"status\\\":%s}\\n\" \"\$RC\" >> \"$2\"
  " >/dev/null 2>&1 &
  ROOT=$!
  wait "$ROOT" 2>/dev/null
}
run_chain 0 "$WORK/e3.jsonl"
check "E3 launch record written" \
  "$([ -s "$WORK/e3.jsonl" ] && grep -c '"t":"launch"' "$WORK/e3.jsonl")" "1"
check "E3 exit record written" \
  "$(grep -c '"t":"exit"' "$WORK/e3.jsonl")" "1"
check "E3 exit status == agent's real status (0)" \
  "$(grep '"t":"exit"' "$WORK/e3.jsonl" | sed 's/.*"status":\([0-9-]*\).*/\1/')" "0"
run_chain 7 "$WORK/e3b.jsonl"
check "E3b non-zero agent status rides the exit record" \
  "$(grep '"t":"exit"' "$WORK/e3b.jsonl" | sed 's/.*"status":\([0-9-]*\).*/\1/')" "7"

echo "== E6: orphan worker keeps the chain pgrp (E4a re-verify) =="
rm -f "$WORK/e6.result"
setsid sh -c "
  ( ( sleep 3 ) & exit 0 )
  sleep 0.4
  WPID=\$(pgrep -f '^sleep 3$' | head -1)
  [ -n \"\$WPID\" ] && printf 'wpgrp=%s rpgrp=%s wppid=%s\n' \
    \"\$(sed 's/^[0-9]* (.*) //' /proc/\$WPID/stat | cut -d' ' -f3)\" \
    \"\$(sed 's/^[0-9]* (.*) //' /proc/self/stat | cut -d' ' -f3)\" \
    \"\$(sed 's/^[0-9]* (.*) //' /proc/\$WPID/stat | cut -d' ' -f2)\" >> '$WORK/e6.result'
" >/dev/null 2>&1 &
ROOT6=$!
wait "$ROOT6" 2>/dev/null
pkill -f '^sleep 3$' 2>/dev/null
if [ -s "$WORK/e6.result" ]; then
  read -r WPGRP RPGRP WPPID <<EOF
$(tr ' ' '\n' < "$WORK/e6.result" | sed 's/wpgrp=//;s/rpgrp=//;s/wppid=//' | tr '\n' ' ')
EOF
  check "E6 orphan worker keeps the session-root pgrp" "$WPGRP" "$RPGRP"
  check "E6 orphan worker reparented (ppid $WPPID != chain sh)" \
    "$([ -n "$WPPID" ] && [ "$WPPID" != "$$" ] && echo yes)" "yes"
else
  bad "E6 worker observation missing"
fi

echo
echo "RESULT: $PASS passed, $FAIL failed"
[ "$FAIL" = "0" ]
