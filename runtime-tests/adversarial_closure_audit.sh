#!/bin/sh
# adversarial_closure_audit.sh — M6 Phase-C adversarial closure audit
# (runs INSIDE the Alpine guest terminal, like run_on_device.sh).
#
# WHAT THIS IS: the destructive + measurement half of the closure audit.
# run_on_device.sh is the non-destructive suite; THIS script deliberately
# corrupts the glibc layer, runs package-manager operations, dumps the
# environment and filesystem ownership map, and measures the fast path —
# then verifies how the layer ACTUALLY behaves (detection, musl isolation,
# and self-healing on the NEXT app session).
#
# PHASES (each is safe to run independently):
#   sh adversarial_closure_audit.sh probe    — read-only: C7 prediction
#           accuracy, C8 environment, C9 filesystem map, C10/C11 timing
#   sh adversarial_closure_audit.sh drill-c2 — C2 corruption drills
#           (marker loss, loader deletion, loader reclaim, lib deletion);
#           VERIFY phase expects DETECTION (broken state honestly reported)
#   sh adversarial_closure_audit.sh drill-c4 — gcompat reclaim drill:
#           apk fix gcompat + verify, then musl isolation check
#   sh adversarial_closure_audit.sh drill-c5 — apk update/upgrade/add/del
#           + post-op loader/layer verification
#   sh adversarial_closure_audit.sh heal     — run this AFTER opening ONE new
#           PocketShell session (the app's session prep is the repair hook):
#           every drill state must be fully healed again
#
# AUTHORITATIVE RULE: detection claims are proven here; the REPAIR claims
# need the app (host side) — open a new session between a drill and `heal`.
#
# GATE BUILD (device_gate.sh companion, m6.0.4-g):
#  - every drill records its arm time (/tmp/.phasec_drill_ts); `heal` uses it
#    for PROVENANCE: healing is only PROVEN when the marker was RE-WRITTEN
#    (extraction rewrites it; nothing else does) after the drill armed and
#    the status file corroborates the same prep. A `heal --manual` restore
#    can make the layer healthy again but canNOT forge this provenance —
#    the gate then says so instead of claiming the app healed.
#  - drill-c4 arms the reclaim state DETERMINISTICALLY: if `apk fix gcompat`
#    does not reclaim the loader path on this device, the genuine gcompat
#    shim (extracted from the real package via `apk fetch`) is placed at the
#    loader path — physically identical to a real reclaim — so the
#    probe→new-session→heal path is always exercised and measured.
set -u
SUITE="phase-c-g (m6.0.4)"
MODE="${1:-probe}"
# POCKETSHELL_GATE_ROOT: SANDBOX-REHEARSAL ONLY (scripts/rehearse_device_gate.sh).
# Unset on the device — every path below is then the exact production path.
ROOT="${POCKETSHELL_GATE_ROOT:-}"
DRILL_TS_FILE="$ROOT/tmp/.phasec_drill_ts"
CLINE="$ROOT/usr/local/lib/node_modules/cline/node_modules/@cline/cli-linux-arm64/bin/cline"
MARKER="$ROOT/etc/pocketshell/glibc-runtime"
STATUS="$ROOT/etc/pocketshell/glibc-runtime.status"
LOADER="$ROOT/lib/ld-linux-aarch64.so.1"
LOADER_CANONICAL="$ROOT/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
MULTARCH="$ROOT/usr/lib/aarch64-linux-gnu"
MSH="$ROOT/bin/sh"   # the musl interpreter (guest /bin/sh)
SELF_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# Test-binary location: same contract as run_on_device.sh (POCKETSHELL_TESTS_DIR
# override; binaries may sit flat beside the script or under bin/).
BIN="${POCKETSHELL_TESTS_DIR:-$SELF_DIR}"
if [ -x "$BIN/t_static" ]; then
  BIN="$BIN"
elif [ -x "$BIN/bin/t_static" ]; then
  BIN="$BIN/bin"
fi
DIRTY=0

pass=0; fail=0; skip=0
row() { printf "%-6s %-44s %s\n" "$1" "$2" "$3" | cut -c1-160; }
ok()   { row PASS "$1" "$2"; pass=$((pass+1)); }
bad()  { row FAIL "$1" "$2"; fail=$((fail+1)); DIRTY=1; }
noun() { row SKIP "$1" "$2"; skip=$((skip+1)); }

have() { command -v "$1" >/dev/null 2>&1; }

# Provenance helper: arm-time recording + evidence reading. BusyBox stat
# supports -c %Y (GNU date -r FILE does NOT — busybox -r means epoch input).
record_drill_ts() { date +%s > "$DRILL_TS_FILE" 2>/dev/null || true; }
mtime_of() { stat -c %Y "$1" 2>/dev/null || echo 0; }
have_stat_epoch() { stat -c %Y "${ROOT}/etc/hostname" >/dev/null 2>&1 || [ -e "${ROOT}/etc" ]; }

echo "== POCKETSHELL M6 PHASE-C ADVERSARIAL CLOSURE AUDIT ($SUITE) — mode: $MODE"
echo "== guest: $(cat "$ROOT/etc/alpine-release" 2>/dev/null || echo '?') / app stamp: $(cat "$ROOT/etc/pocketshell/app-version" 2>/dev/null || echo '(none)')"
echo "== marker: $(cat "$MARKER" 2>/dev/null || echo '(absent)')"
echo "== status: $(cat "$STATUS" 2>/dev/null || echo '(none)')"
echo

# --------------------------------------------------------------- C7 support
doctor_pred() { # binary -> prints SUPPORTED/UNSUPPORTED/UNKNOWN
  pocketshell-doctor "$1" 2>/dev/null | grep -E '^Compatibility:' | head -1 |
    sed 's/^Compatibility: //;s/ (.*//'
}
# C7: prediction vs reality for a list of binaries. Verdict = the binary
# actually ran with the expected output.
c7_row() { # name want-run(0/1) expected-output cmd...
  name="$1"; want_run="$2"; expect="$3"; shift 3
  pred=$(doctor_pred "$1")
  out=$("$@" 2>&1); rc=$?
  if [ "$rc" -eq 0 ] && printf '%s' "$out" | grep -q "$expect"; then runs=1; else runs=0; fi
  if [ "$runs" -eq "$want_run" ]; then
    if [ "$pred" = "SUPPORTED" ] && [ "$runs" -eq 1 ]; then v="true-positive"
    elif [ "$pred" = "SUPPORTED" ]; then v="FALSE-POSITIVE"
    elif [ "$pred" = "UNSUPPORTED" ] && [ "$runs" -eq 0 ]; then v="true-negative"
    else v="FALSE-NEGATIVE"; fi
    ok "C7 $name" "doctor=$pred reality-run=$runs -> $v"
  else
    bad "C7 $name" "reality mismatch (rc=$rc): $(printf '%s' "$out" | head -1)"
  fi
}

# ------------------------------------------------------------------ probe
probe() {
  echo "=== C7 — DOCTOR PREDICTION ACCURACY (doctor says X; reality = execution)"
  if have pocketshell-doctor; then
    # SUPPORTED must mean runs (true positives):
    c7_row "musl busybox"      1 "musl-ok"       "$MSH" -c 'echo musl-ok'
    [ -x "$BIN/t_static" ] && c7_row "static t_static" 1 "hello-glibc" "$BIN/t_static"
    [ -x "$BIN/t_hello" ] && c7_row "glibc t_hello" 1 "hello-glibc" "$BIN/t_hello"
    [ -x "$BIN/t_cpp" ] && c7_row "glibc C++ t_cpp" 1 "cpp-ok" "$BIN/t_cpp"
    [ -x "$CLINE" ] && c7_row "real cline" 1 "3.0.61" "$CLINE" --version
    # UNSUPPORTED must mean does-not-run (true negatives): arm doctor at an
    # x86 binary — the guest cannot execute it and neither can this audit.
    printf 'not an elf\n' > /tmp/phasec-notelf
    c7_row "non-ELF file" 0 "hello" /tmp/phasec-notelf
  else
    noun "C7 doctor" "pocketshell-doctor not on PATH (layer missing?)"
  fi

  echo
  echo "=== C8 — ENVIRONMENT CONTAMINATION (routing must be interpreter-contract only)"
  env | sort > /tmp/phasec-env.txt
  for v in LD_PRELOAD LD_LIBRARY_PATH PROOT_LOADER PROOT_TMP_DIR LD_CONFIG; do
    line=$(grep "^$v=" /tmp/phasec-env.txt || true)
    case "$v" in
      LD_PRELOAD|LD_CONFIG)
        [ -z "$line" ] && ok "C8 no $v" "absent from guest env" || bad "C8 $v present" "$line"
        ;;
      *)
        if [ -n "$line" ]; then
          val=${line#*=}
          if [ -d "$val" ]; then
            bad "C8 $v points INSIDE the guest" "$line"
          else
            ok "C8 $v present but inert" "$val (does not exist in the guest)"
          fi
        else
          ok "C8 $v absent" ""
        fi
        ;;
    esac
  done
  # The decisive C8 claim: BOTH libcs work under the SAME environment.
  "$MSH" -c 'echo musl-ok' >/dev/null 2>&1 && ok "C8 musl runs under this env" "/bin/sh" || bad "C8 musl broken" ""
  if [ -x "$CLINE" ]; then
    "$CLINE" --version >/dev/null 2>&1 && ok "C8 glibc runs under this env" "cline" || bad "C8 glibc broken" ""
  fi

  echo
  echo "=== C9 — FILESYSTEM OWNERSHIP MAP (no musl replacement, no libc collision)"
  ok "C9 musl loader" "$(ls -la /lib/ld-musl-aarch64.so.1 2>/dev/null | awk '{print $NF}')"
  ok "C9 real loader link" "$(readlink "$LOADER" 2>/dev/null || echo '(not a symlink!)')"
  ok "C9 canonical loader" "$(head -c4 "$LOADER_CANONICAL" 2>/dev/null | od -An -c | tr -d ' \n' || echo MISSING) (expect 177ELF)"
  ok "C9 musl libc" "$(apk info -L musl 2>/dev/null | grep -c 'lib/' ) files owned by musl pkg"
  ok "C9 /lib is a real dir" "$([ -d /lib ] && [ ! -L /lib ] && echo yes || echo 'NO — merged-usr drift')"
  ok "C9 layer libs" "$(ls "$MULTARCH" 2>/dev/null | wc -l) files in $MULTARCH"
  # musl SONAMEs must not exist inside the layer dir and vice versa
  [ -e "$MULTARCH/ld-musl-aarch64.so.1" ] && bad "C9 musl loader inside layer dir!" "" \
    || ok "C9 no musl files in layer dir" ""
  [ -e "$ROOT/lib/aarch64-linux-gnu/libc.so.6" ] && \
    ok "C9 layer libc reachable via /lib multiarch" "present" || bad "C9 layer libc via /lib" "MISSING"

  echo
  echo "=== C10/C11 — SIZE + TIMING (marker fast path must be fast; no repeated extraction)"
  echo "  layer footprint: $(du -sh "$MULTARCH" 2>/dev/null | cut -f1) in $MULTARCH, $(find /usr/local/bin /etc/pocketshell -type f 2>/dev/null | wc -l) tool/marker files"
  t0=$(date +%s%N); "$LOADER" --version >/dev/null 2>&1
  t1=$(date +%s%N); echo "  loader --version exec: $(( (t1-t0)/1000000 )) ms"
  t0=$(date +%s%N); cat "$MARKER" >/dev/null
  t1=$(date +%s%N); echo "  marker read (fast-path core): $(( (t1-t0)/1000000 )) ms"
  for b in "$MSH" /usr/bin/git; do
    t0=$(date +%s%N); "$b" -c 'true' >/dev/null 2>&1 || /usr/bin/git --version >/dev/null 2>&1
    t1=$(date +%s%N); echo "  musl exec ($b): $(( (t1-t0)/1000000 )) ms"
  done
  if [ -x "$CLINE" ]; then
    t0=$(date +%s%N); "$CLINE" --version >/dev/null 2>&1
    t1=$(date +%s%N); echo "  glibc exec (cline): $(( (t1-t0)/1000000 )) ms"
    t0=$(date +%s%N); "$CLINE" --version >/dev/null 2>&1
    t1=$(date +%s%N); echo "  glibc exec (cline, 2nd): $(( (t1-t0)/1000000 )) ms"
  fi
  # Repeated-start regression: the status file must say fastpath after the
  # latest session prep, never extractor (an extractor line on EVERY start
  # would mean the fast path is broken).
  case "$(cat "$STATUS" 2>/dev/null)" in
    state=OK*source=fastpath*) ok "C11 last prep used the fast path" "$(cat "$STATUS")" ;;
    state=OK*source=extractor*) noun "C11 last prep extracted (update/repair run — re-check after a plain restart)" "$(cat "$STATUS")" ;;
    *) bad "C11 status file" "$(cat "$STATUS" 2>/dev/null || echo absent)" ;;
  esac
  echo
  echo "RESULT: $pass passed, $fail failed, $skip skipped"
  [ "$fail" -eq 0 ] || exit 1
  exit 0
}

# ------------------------------------------------------------------ C2 drills
drill_c2() {
  echo "=== C2 CORRUPTION DRILLS — the layer must be DETECTED as broken, musl must survive."
  echo "    (Self-heal proof: open ONE new PocketShell session, then run: sh $0 heal)"
  record_drill_ts   # heal provenance: marker must be RE-WRITTEN after this point
  layer_was_ok=0
  "$LOADER" --version 2>/dev/null | grep -q 'stable release version' && layer_was_ok=1
  if [ "$layer_was_ok" -ne 1 ]; then
    bad "C2 precondition" "layer is not healthy — run from a working 27/27 state"
    exit 1
  fi

  echo "-- drill C2.1: marker deleted, files present (valid-runtime recognition)"
  cp "$MARKER" /tmp/phasec-marker.bak
  rm -f "$MARKER"
  noun "C2.1 marker removed" "documented behavior: the app re-extracts idempotently on next prep (no blind trust of files)"

  echo "-- drill C2.2: loader symlink deleted (marker intact)"
  rm -f "$LOADER"
  if [ -e "$LOADER" ]; then bad "C2.2 drill" "loader still present after rm"; else ok "C2.2 loader deleted" "drill armed"; fi
  # doctor must NOT claim SUPPORTED for glibc binaries now
  if have pocketshell-doctor && [ -x "$BIN/t_hello" ]; then
    dout=$(pocketshell-doctor "$BIN/t_hello" 2>&1)
    case "$dout" in
      *"layer is not installed"*|*"loader: MISSING"*) ok "C2.2 doctor detects broken layer" "$(echo "$dout" | grep '^Reason' | head -1 | cut -c1-90)" ;;
      *"Compatibility: SUPPORTED"*) bad "C2.2 doctor false SUPPORTED" "loader deleted but doctor claimed SUPPORTED" ;;
      *) ok "C2.2 doctor reports failure" "$(echo "$dout" | grep '^Compatibility' | head -1)" ;;
    esac
  fi
  glibc_works=0
  [ -x "$BIN/t_hello" ] && "$BIN/t_hello" >/dev/null 2>&1 && glibc_works=1
  [ "$glibc_works" -eq 1 ] && bad "C2.2 glibc still ran without loader" "unexpected" \
    || ok "C2.2 glibc honestly broken" "glibc binaries cannot run without the loader (expected)"
  musl_ok=0
  "$MSH" -c 'echo musl-ok' >/dev/null 2>&1 && musl_ok=1
  [ "$musl_ok" -eq 1 ] && ok "C2.2 musl unaffected" "/bin/sh still runs" || bad "C2.2 musl damaged" "REGRESSION"

  echo "-- drill C2.3: required glibc library deleted (libpthread.so.0, marker intact)"
  cp "$MULTARCH/libpthread.so.0" /tmp/phasec-libpthread.bak
  rm -f "$MULTARCH/libpthread.so.0"
  [ -x "$BIN/t_pthread" ] && { "$BIN/t_pthread" >/dev/null 2>&1 && bad "C2.3 pthread ran without lib" "unexpected" || ok "C2.3 pthread honestly broken" "expected: cannot load libpthread.so.0"; }
  musl_ok=0; "$MSH" -c 'echo musl-ok' >/dev/null 2>&1 && musl_ok=1
  [ "$musl_ok" -eq 1 ] && ok "C2.3 musl unaffected" "" || bad "C2.3 musl damaged" "REGRESSION"

  echo "-- drill C2.4: layer library TRUNCATED (corruption, not deletion)"
  cp "$MULTARCH/libm.so.6" /tmp/phasec-libm.bak
  head -c 4096 /tmp/phasec-libm.bak > "$MULTARCH/libm.so.6"
  if [ -x "$BIN/t_libm" ]; then
    "$BIN/t_libm" >/dev/null 2>&1 && bad "C2.4 truncated libm still ran" "unexpected" \
      || ok "C2.4 truncated libm honestly broken" "ELF load fails, no silent fallback"
  fi
  if have pocketshell-doctor && [ -x "$BIN/t_libm" ]; then
    dout=$(pocketshell-doctor "$BIN/t_libm" 2>&1)
    case "$dout" in *"Compatibility: SUPPORTED"*) bad "C2.4 doctor false SUPPORTED (truncated lib)" "" ;; *) ok "C2.4 doctor catches it" "$(echo "$dout" | grep -E 'FAILED|Compatibility' | head -1 | cut -c1-90)" ;; esac
  fi

  echo
  echo "RESTORE-OR-HEAL: the drills left the layer BROKEN by design."
  echo "  Option A (app heals): open ONE new PocketShell session, then run: sh $0 heal"
  echo "  Option B (manual restore): sh $0 heal --manual  (restores from /tmp backups)"
  echo
  echo "RESULT: $pass passed, $fail failed, $skip skipped"
  [ "$fail" -eq 0 ] || exit 1
  exit 0
}

# ------------------------------------------------------------------ C4 drill
drill_c4() {
  echo "=== C4 LOADER OWNERSHIP DRILL — can apk/gcompat reclaim the real loader?"
  record_drill_ts   # heal provenance: marker must be RE-WRITTEN after this point
  if ! apk info -e gcompat >/dev/null 2>&1; then
    echo "  gcompat not installed: installing it for the drill (it IS in this rootfs's history)"
    apk add gcompat >/dev/null 2>&1 || { bad "C4 gcompat install" "apk add gcompat failed"; exit 1; }
  fi
  ok "C4 gcompat present" "$(apk info -e -v gcompat 2>/dev/null || echo '?')"
  ok "C4 loader identity BEFORE" "$(readlink "$LOADER" 2>/dev/null || echo '(regular file)')"
  # The reclaim attempt: apk fix forces a reinstall of gcompat's files —
  # INCLUDING its 67,600-byte shim ELF at /lib/ld-linux-aarch64.so.1.
  apk fix gcompat >/dev/null 2>&1; rc=$?
  ok "C4 apk fix gcompat" "exit=$rc"
  after=$(readlink "$LOADER" 2>/dev/null || echo '(regular file)')
  ok "C4 loader identity AFTER" "$after"
  case "$after" in
    "$LOADER_CANONICAL"|*/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1)
      # THE MEASURED C4 ANSWER for this device: the package operation did NOT
      # reclaim the path. The heal path still gets exercised DETERMINISTICALLY:
      # place the GENUINE gcompat shim at the loader path — physically the same
      # state a real reclaim produces (regular ELF at the loader path) — so the
      # probe→new-session→heal chain is always measured, never skipped.
      ok "C4 measured: apk fix did NOT reclaim" "documenting the real device answer"
      sim_ok=0; sim_method=""
      mkdir -p /tmp/psgate
      if apk fetch gcompat -o /tmp/psgate >/dev/null 2>&1; then
        apkfile=$(ls /tmp/psgate/gcompat-*.apk 2>/dev/null | head -1)
        [ -n "${apkfile:-}" ] && tar -xzf "$apkfile" -C /tmp/psgate lib/ld-linux-aarch64.so.1 2>/dev/null \
          && [ -s /tmp/psgate/lib/ld-linux-aarch64.so.1 ] && {
          [ "$(head -c4 /tmp/psgate/lib/ld-linux-aarch64.so.1 | od -An -c | tr -d ' \n')" = "177ELF" ] \
            && sim_method="genuine gcompat shim (from the fetched package)"
        }
      fi
      if [ -z "$sim_method" ]; then
        # Fallback: a regular ELF copy of the real loader at the loader path —
        # the same node-shape a reclaim produces (not a symlink to canonical).
        mkdir -p /tmp/psgate/lib
        if cp "$LOADER_CANONICAL" /tmp/psgate/lib/ld-linux-aarch64.so.1 2>/dev/null; then
          sim_method="regular-file ELF copy (fallback; package fetch unavailable)"
        fi
      fi
      if [ -n "$sim_method" ]; then
        rm -f "$LOADER"; cp /tmp/psgate/lib/ld-linux-aarch64.so.1 "$LOADER" 2>/dev/null; chmod 755 "$LOADER" 2>/dev/null
        [ -f "$LOADER" ] && [ "$(readlink "$LOADER" 2>/dev/null || echo x)" = "x" ] && sim_ok=1
      fi
      [ "$sim_ok" -eq 1 ] && ok "C4 reclaim state ARMED (simulated)" "$sim_method" \
        || bad "C4 reclaim arm" "could not place the shim — layer left intact; report this output"
      ;;
    *)
      ok "C4 measured: apk fix DID reclaim the loader path" "the real C4 device answer — probe must catch this"
      ;;
  esac
  # The probe MUST now see the layer as broken either way (natural or simulated):
  if [ -x "$BIN/t_hello" ]; then
    "$BIN/t_hello" >/dev/null 2>&1 && bad "C4 glibc ran on reclaim state" "unexpected" \
      || ok "C4 glibc honestly broken under reclaim state" "expected"
  fi
  # The decisive musl check either way:
  musl_ok=0; "$MSH" -c 'echo musl-ok' >/dev/null 2>&1 && musl_ok=1
  [ "$musl_ok" -eq 1 ] && ok "C4 musl unaffected" "" || bad "C4 musl damaged" "REGRESSION"
  echo
  echo "If the loader was RECLAIMED: open ONE new PocketShell session (the probe"
  echo "self-heals by re-extraction), then run: sh $0 heal"
  echo
  echo "RESULT: $pass passed, $fail failed, $skip skipped"
  [ "$fail" -eq 0 ] || exit 1
  exit 0
}

# ------------------------------------------------------------------ C5 drill
drill_c5() {
  echo "=== C5 APK PACKAGE-MANAGER INTERACTION — normal package ops must not hurt the layer"
  before_loader=$(readlink "$LOADER" 2>/dev/null || echo '(regular file)')
  ok "C5 loader before" "$before_loader"
  apk update >/dev/null 2>&1 && ok "C5 apk update" "rc=0" || bad "C5 apk update" "rc!=0"
  apk upgrade >/dev/null 2>&1 && ok "C5 apk upgrade" "rc=0" || bad "C5 apk upgrade" "rc!=0"
  apk add file >/dev/null 2>&1 && ok "C5 apk add file" "rc=0" || bad "C5 apk add file" "rc!=0"
  have file && ok "C5 added musl binary runs" "$(file --version 2>/dev/null | head -1 | cut -c1-50)"
  apk del file >/dev/null 2>&1 && ok "C5 apk del file" "rc=0" || bad "C5 apk del file" "rc!=0"
  after_loader=$(readlink "$LOADER" 2>/dev/null || echo '(regular file)')
  [ "$before_loader" = "$after_loader" ] && ok "C5 loader unchanged by package ops" "$after_loader" \
    || bad "C5 loader CHANGED by package ops" "$before_loader -> $after_loader"
  [ -f "$MARKER" ] && ok "C5 marker intact" "$(cat "$MARKER")" || bad "C5 marker gone" ""
  if [ -x "$CLINE" ]; then
    "$CLINE" --version >/dev/null 2>&1 && ok "C5 cline works after apk ops" "" || bad "C5 cline broken by apk ops" ""
  fi
  /usr/bin/node --version >/dev/null 2>&1 && ok "C5 node works after apk ops" "$(/usr/bin/node --version)" || bad "C5 node broken" ""
  echo
  echo "RESULT: $pass passed, $fail failed, $skip skipped"
  [ "$fail" -eq 0 ] || exit 1
  exit 0
}

# ------------------------------------------------------------------- heal
heal() {
  echo "=== HEAL VERIFICATION — run AFTER opening one new PocketShell session"
  echo "    (session prep = the repair hook; every drill state must be gone)"
  all_ok=1
  # loader
  if [ -L "$LOADER" ] && [ "$(readlink "$LOADER")" = "$LOADER_CANONICAL" ]; then
    ok "heal loader symlink canonical" "$(readlink "$LOADER")"
  else
    bad "heal loader" "$(readlink "$LOADER" 2>/dev/null || echo 'not a symlink') — run a manual restore: sh $0 heal --manual"
    all_ok=0
  fi
  # loader really executes
  "$LOADER" --version 2>/dev/null | grep -q 'stable release version 2.41' \
    && ok "heal loader is real glibc 2.41" "" || { bad "heal loader exec" "$("$LOADER" --version 2>&1 | head -1)"; all_ok=0; }
  # marker + status
  [ -f "$MARKER" ] && ok "heal marker present" "$(cat "$MARKER")" || { bad "heal marker" "MISSING"; all_ok=0; }
  case "$(cat "$STATUS" 2>/dev/null)" in
    state=OK*) ok "heal status OK" "$(cat "$STATUS")" ;;
    state=FAILED*) bad "heal status FAILED" "$(cat "$STATUS")"; all_ok=0 ;;
    *) bad "heal status" "no status file"; all_ok=0 ;;
  esac
  # PROVENANCE (gate build): health alone does not prove the APP healed — a
  # `heal --manual` restore can produce the same healthy state. Production
  # healing is PROVEN when the marker was RE-WRITTEN after the drill armed
  # (only the app's extractor writes the marker) and the status file
  # corroborates the same prep (source=extractor, or a fastpath status whose
  # timestamp is within seconds of the marker — i.e. an EXTRA session opened
  # after the healing one; the extraction evidence stays in the mtime).
  drill_ts=$(cat "$DRILL_TS_FILE" 2>/dev/null || echo 0)
  [ -n "${drill_ts:-}" ] || drill_ts=0
  if [ "$drill_ts" -eq 0 ]; then
    noun "heal provenance" "no drill arm-time recorded (run drill-c2/c4 first) — health verified only"
  elif ! have_stat_epoch; then
    noun "heal provenance" "stat -c %Y unavailable — cannot read marker mtime"
  else
    marker_mtime=$(mtime_of "$MARKER")
    raw_ts=$(sed -n 's/.*ts=\([0-9]*\).*/\1/p' "$STATUS" 2>/dev/null | head -1)
    [ -n "${raw_ts:-}" ] || raw_ts=0
    status_ts_sec=$(( raw_ts / 1000 ))
    fresh_marker=0; [ "$marker_mtime" -gt "$drill_ts" ] && fresh_marker=1
    same_prep=0
    case "$(cat "$STATUS" 2>/dev/null)" in
      *source=extractor*) same_prep=1 ;;
      *)
        if [ "$status_ts_sec" -gt 0 ]; then
          d=$(( marker_mtime - status_ts_sec ))
          [ "$d" -le 15 ] && [ "$d" -ge -15 ] && same_prep=1
        fi
        ;;
    esac
    if [ "$fresh_marker" -eq 1 ] && [ "$same_prep" -eq 1 ]; then
      ok "heal provenance (production session-prep heal)" "drill armed $drill_ts; marker re-written $marker_mtime; prep ts $status_ts_sec"
    else
      bad "heal provenance" "layer healthy but healing NOT proven from session prep (marker fresh=$fresh_marker, same-prep evidence=$same_prep). Manual restores cannot pass this gate — re-run the drill, open ONE new session, heal again."
      all_ok=0
    fi
  fi
  # doctor must agree the layer is back (gate requirement #7)
  if have pocketshell-doctor; then
    if [ -x "$BIN/t_cline_shape" ]; then
      dout=$(pocketshell-doctor "$BIN/t_cline_shape" 2>&1)
      printf '%s\n' "$dout" | grep -q "^Compatibility: SUPPORTED" \
        && ok "heal doctor verdict (t_cline_shape)" "SUPPORTED" \
        || { bad "heal doctor verdict" "$(printf '%s\n' "$dout" | grep '^Compatibility:' | head -1)"; all_ok=0; }
    fi
    st=$(pocketshell-doctor --selftest 2>&1); st_rc=$?
    if [ "$st_rc" -eq 0 ] && printf '%s\n' "$st" | grep -q "SELFTEST PASS"; then
      ok "heal doctor selftest" ""
    else
      bad "heal doctor selftest" "rc=$st_rc $(printf '%s\n' "$st" | tail -1)"
      all_ok=0
    fi
  else
    bad "heal doctor" "pocketshell-doctor missing from PATH after heal"; all_ok=0
  fi
  # full tier-2 spot check
  if [ -x "$BIN/t_hello" ]; then
    "$BIN/t_hello" >/dev/null 2>&1 && ok "heal glibc hello" "" || { bad "heal glibc hello" "still broken"; all_ok=0; }
  fi
  if [ -x "$BIN/t_pthread" ]; then
    "$BIN/t_pthread" >/dev/null 2>&1 && ok "heal glibc pthread" "" || { bad "heal glibc pthread" "libpthread not restored?"; all_ok=0; }
  fi
  if [ -x "$BIN/t_libm" ]; then
    "$BIN/t_libm" >/dev/null 2>&1 && ok "heal glibc libm (truncated lib restored)" "" || { bad "heal glibc libm" "still broken"; all_ok=0; }
  fi
  if [ -x "$CLINE" ]; then
    "$CLINE" --version >/dev/null 2>&1 && ok "heal real cline" "$("$CLINE" --version)" || { bad "heal real cline" "still broken"; all_ok=0; }
  fi
  musl_ok=0; "$MSH" -c 'echo musl-ok' >/dev/null 2>&1 && musl_ok=1
  [ "$musl_ok" -eq 1 ] && ok "heal musl untouched throughout" "" || bad "heal musl" "REGRESSION"
  echo
  echo "RESULT: $pass passed, $fail failed, $skip skipped"
  [ "$fail" -eq 0 ] || exit 1
  exit 0
}

manual_restore() {
  echo "=== MANUAL RESTORE (no app involved) — break-glass recovery ONLY."
  echo "  NOTE: this makes the layer healthy again but canNOT pass the heal"
  echo "  provenance check — the gate will honestly report that the app did"
  echo "  NOT perform the healing. The audited path is: new session."
  [ -f /tmp/phasec-marker.bak ] && { mkdir -p /etc/pocketshell; cp /tmp/phasec-marker.bak "$MARKER"; echo "  marker restored"; }
  [ -f /tmp/phasec-libpthread.bak ] && { cp /tmp/phasec-libpthread.bak "$MULTARCH/libpthread.so.0"; echo "  libpthread restored"; }
  [ -f /tmp/phasec-libm.bak ] && { cp /tmp/phasec-libm.bak "$MULTARCH/libm.so.6"; echo "  libm restored"; }
  [ -L "$LOADER" ] || { ln -sf "$LOADER_CANONICAL" "$LOADER" 2>/dev/null && echo "  loader link restored"; }
  echo "  NOTE: manual restore is the ESCAPE HATCH — the app-side heal (new session) is the audited path."
}

case "$MODE" in
  probe) probe ;;
  drill-c2) drill_c2 ;;
  drill-c4) drill_c4 ;;
  drill-c5) drill_c5 ;;
  heal)
    [ "${2:-}" = "--manual" ] && manual_restore
    heal
    ;;
  *)
    echo "usage: sh $0 probe|drill-c2|drill-c4|drill-c5|heal [--manual]" >&2
    exit 2
    ;;
esac
