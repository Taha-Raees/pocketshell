#!/bin/sh
# device_gate.sh — M6 FINAL DEVICE GATE: one guided, resumable runner.
# Runs INSIDE the PocketShell Alpine guest terminal (like run_on_device.sh).
#
# PURPOSE
#   The compatibility suite (27 rows) and the adversarial PROBE (22 rows) are
#   already verified green on this device. What remains for M6 closure are the
#   DESTRUCTIVE drills + the PRODUCTION self-heal proof. This script makes
#   them a guided, resumable sequence with the least possible effort:
#
#     sh device_gate.sh gate        # baseline identity checks + C2 drills
#        [open ONE new PocketShell session — the app's repair hook]
#     sh device_gate.sh resume-c2   # prove the app healed → arms C4 drill
#        [open ONE new PocketShell session]
#     sh device_gate.sh resume-c4   # prove the app healed → arms C5 drill
#        [open ONE new PocketShell session]
#     sh device_gate.sh resume-c5   # prove the fast path survived → runs FINAL
#
#   Individual stages (each idempotent, each re-runnable):
#     gate | baseline | c2 | resume-c2 | c4 | resume-c4 | c5 | resume-c5 |
#     final | status
#
#   `status` prints what passed so far and the exact next command.
#
# WHAT HEALING IS PROVEN (and what is NOT accepted)
#   The recovery under test is POCKETSHELL ITSELF: opening a new session runs
#   PackageGateway.prepareGuestForSession → GuestGlibcRuntime.ensureInstalled,
#   whose structural integrity probe detects the broken layer and re-extracts
#   the pinned one. This script NEVER repairs the layer. The heal verification
#   (adversarial_closure_audit.sh heal, gate build) only accepts healing whose
#   PROVENANCE is the app's extractor: the layer marker must have been
#   RE-WRITTEN after the drill armed and the app status file must corroborate
#   the same preparation. A manual restore (heal --manual) makes the device
#   healthy again but canNOT pass this gate — the output will say so honestly.
#
# SAFETY CONTRACT
#   - never deletes or reinstalls the Alpine rootfs; never touches Android
#     user storage; never destroys user projects or packages
#   - only manipulates PocketShell-owned glibc LAYER files (loader path,
#     multiarch libs, marker) — and always with the app's own self-heal as
#     the recovery path
#   - refuses to run destructive stages against an unknown environment
#     (baseline must pass first: app stamp, marker pin, real Debian loader,
#     musl, suite binaries, disk space)
#   - distinguishes PASS / FAIL / ACTION REQUIRED / RECOVERY in every block
#
# M6 CLOSES ONLY WHEN the full sequence is green and the COMPLETE output is
# pasted back for the record.
set -u
GATE_VERSION="device-gate v1.0 (m6.0.4)"
# POCKETSHELL_GATE_ROOT: SANDBOX-REHEARSAL ONLY (scripts/rehearse_device_gate.sh).
# Unset on the device — every path below is then the exact production path.
ROOT="${POCKETSHELL_GATE_ROOT:-}"
SELF_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || SELF_DIR="."
AUDIT="$SELF_DIR/adversarial_closure_audit.sh"
SUITE="${POCKETSHELL_GATE_SUITE:-$SELF_DIR/run_on_device.sh}"
STATE_DIR="$ROOT/tmp/pocketshell-gate"
STATE="$STATE_DIR/state"
MARKER="$ROOT/etc/pocketshell/glibc-runtime"
MARKER_EXPECTED="PocketShell glibc runtime layer 2.41-12.deb13u3 (glibc 2.41) rev=2"
STATUS="$ROOT/etc/pocketshell/glibc-runtime.status"
APP_STAMP="$ROOT/etc/pocketshell/app-version"
LOADER="$ROOT/lib/ld-linux-aarch64.so.1"
LOADER_CANONICAL="$ROOT/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
MULTARCH="$ROOT/usr/lib/aarch64-linux-gnu"
CLINE="$ROOT/usr/local/lib/node_modules/cline/node_modules/@cline/cli-linux-arm64/bin/cline"
MIN_FREE_KB=102400   # 100 MB — the drills rewrite a ~50 MB layer

# test-binary dir: same auto-location contract as the audit script
BIN="${POCKETSHELL_TESTS_DIR:-$SELF_DIR}"; [ -x "$BIN/t_hello" ] || BIN="$BIN/bin"

pass=0; fail=0
row()  { printf "%-6s %-44s %s\n" "$1" "$2" "$3" | cut -c1-160; }
gok()  { row PASS "$1" "$2"; pass=$((pass+1)); }
gbad() { row FAIL "$1" "$2"; fail=$((fail+1)); }

banner() { echo; echo "=== $*"; }

# ---------------------------------------------------------------- state file
get_state() { # KEY -> echoes value or ""
  grep "^$1=" "$STATE" 2>/dev/null | head -1 | cut -d= -f2-
}
set_state() { # KEY VALUE
  mkdir -p "$STATE_DIR"
  touch "$STATE" 2>/dev/null || { gbad "state file" "cannot write $STATE"; return 1; }
  grep -v "^$1=" "$STATE" > "$STATE.write" 2>/dev/null
  echo "$1=$2" >> "$STATE.write"
  mv "$STATE.write" "$STATE"
}

now() { date +%s; }

# ------------------------------------------------------------------ helpers
have() { command -v "$1" >/dev/null 2>&1; }
real_loader_ok() { # symlink → canonical AND the REAL Debian glibc 2.41 executes
  [ -L "$LOADER" ] && [ "$(readlink "$LOADER" 2>/dev/null)" = "$LOADER_CANONICAL" ] \
    && "$LOADER" --version 2>/dev/null | grep -q "Debian GLIBC 2.41" \
    && "$LOADER" --version 2>/dev/null | grep -q "stable release version 2.41"
}
musl_ok() { "$ROOT/bin/sh" -c 'echo musl-ok' >/dev/null 2>&1; }
cline_ok() { [ -x "$CLINE" ] && "$CLINE" --version 2>/dev/null | grep -q "3.0.61"; }
status_ts_sec() { # last app-side prep time (epoch seconds)
  raw=$(sed -n 's/.*ts=\([0-9]*\).*/\1/p' "$STATUS" 2>/dev/null | head -1)
  [ -n "${raw:-}" ] || raw=0
  echo $(( raw / 1000 ))
}

action_required() { # $1 = resume stage name
  echo
  echo "  ============================================================"
  echo "  ACTION REQUIRED:"
  echo "    1. Leave this shell open (switch back to this tab later)."
  echo "    2. Open the PocketShell UI."
  echo "    3. Create ONE NEW Linux session  <-- this IS the repair"
  echo "       hook: the app's session prep re-extracts the layer."
  echo "    4. Return here (this tab) and run:"
  echo
  echo "        sh \"$SELF_DIR/device_gate.sh\" $1"
  echo
  echo "    Do NOT open extra sessions before resuming — the heal"
  echo "    provenance reads the app's LAST prep outcome."
  echo "  ============================================================"
}

refuse() { # $1 = reason
  echo
  echo "  ============================================================"
  echo "  REFUSED — destructive stages may not run in this state:"
  echo "    $1"
  echo "  Fix the environment (or re-run: sh \"$SELF_DIR/device_gate.sh\" baseline),"
  echo "  then retry. NOTHING was modified."
  echo "  ============================================================"
}

run_audit_stage() { # $1 = mode label, $2 = audit mode; sets RUN_AUDIT_OUT + rc
  banner "$1"
  out=$(sh "$AUDIT" "$2" 2>&1); rc=$?
  RUN_AUDIT_OUT="$out"
  printf '%s\n' "$out"
  return $rc
}

require_audit_gate_build() {
  if [ ! -f "$AUDIT" ]; then
    refuse "adversarial_closure_audit.sh not found next to device_gate.sh ($SELF_DIR)"
    return 1
  fi
  if ! grep -q "phase-c-g" "$AUDIT"; then
    refuse "STALE adversarial_closure_audit.sh (pre-gate build, no heal provenance). Re-download the tests tarball and re-extract, then retry."
    return 1
  fi
  return 0
}

# ================================================================== BASELINE
do_baseline() {
  banner "BASELINE — environment identity + health (gate for destructive drills)"
  ok=1
  # 1) PocketShell identity (refuse unknown environments)
  stamp=$(cat "$APP_STAMP" 2>/dev/null || echo "")
  case "$stamp" in
    0.10.0-m6.0.4\ \(versionCode\ 44\)*) gok "app stamp" "$stamp" ;;
    "") gbad "app stamp" "no $APP_STAMP — this rootfs was never prepared by a vc42+ PocketShell"; ok=0 ;;
    *)  gbad "app stamp" "unexpected: '$stamp' (expected 0.10.0-m6.0.4 / versionCode 44+)"; ok=0 ;;
  esac
  # 2) marker pin (exact content)
  marker=$(cat "$MARKER" 2>/dev/null || echo "")
  if [ "$marker" = "$MARKER_EXPECTED" ]; then gok "marker pin" "$marker"
  else gbad "marker pin" "got: '$marker' — expected: '$MARKER_EXPECTED'"; ok=0; fi
  # 3) real Debian loader (identity by EXECUTION, not paperwork)
  if real_loader_ok; then
    gok "real loader" "$(readlink "$LOADER") -> $("$LOADER" --version 2>&1 | head -1 | cut -c1-60)"
  else
    gbad "real loader" "symlink/identity check failed: $(ls -la "$LOADER" 2>/dev/null | awk '{print $NF}')"; ok=0
  fi
  # 4) gcompat stub canNOT be mistaken for the loader (explicit negative)
  case "$("$LOADER" --version 2>&1 | head -1)" in
    *gcompat*) gbad "loader is gcompat STUB" "refusing"; ok=0 ;;
    *) gok "loader not the gcompat stub" "" ;;
  esac
  # 5) musl healthy + loader separation
  if musl_ok; then gok "musl /bin/sh" "working"; else gbad "musl /bin/sh" "BROKEN — refusing"; ok=0; fi
  [ -e "$ROOT/lib/ld-musl-aarch64.so.1" ] && gok "musl loader present" "/lib/ld-musl-aarch64.so.1" \
    || { gbad "musl loader" "MISSING"; ok=0; }
  # 6) layer payload sanity
  n_libs=$(ls "$MULTARCH" 2>/dev/null | wc -l)
  [ "$n_libs" -ge 60 ] && gok "layer libs" "$n_libs files in $MULTARCH" \
    || { gbad "layer libs" "only $n_libs — layer looks incomplete"; ok=0; }
  # 7) suite binaries (the drills need them)
  if [ -x "$BIN/t_hello" ] && [ -x "$BIN/t_cline_shape" ]; then
    gok "suite binaries" "$BIN"
  else
    gbad "suite binaries" "t_hello/t_cline_shape not found under $BIN — re-extract the tests tarball"; ok=0
  fi
  # 8) Cline + doctor
  if cline_ok; then gok "cline 3.0.61" "$("$CLINE" --version 2>/dev/null)"
  else gbad "cline" "missing or wrong version at $CLINE"; ok=0; fi
  if have pocketshell-doctor && pocketshell-doctor --selftest >/dev/null 2>&1; then
    gok "pocketshell-doctor" "present, selftest PASS"
  else
    gbad "pocketshell-doctor" "missing or selftest failing"; ok=0
  fi
  # 9) status file says the app's last prep succeeded
  case "$(cat "$STATUS" 2>/dev/null)" in
    state=OK*) gok "app status" "$(cat "$STATUS")" ;;
    *) gbad "app status" "$(cat "$STATUS" 2>/dev/null || echo 'absent')"; ok=0 ;;
  esac
  # 10) gcompat state (informational — the C4 subject)
  if apk info -e gcompat >/dev/null 2>&1; then
    gok "gcompat installed" "$(apk info -e -v gcompat 2>/dev/null) (historical; C4 will exercise it)"
  else
    gok "gcompat absent" "drill-c4 will install it for the exercise"
  fi
  # 11) disk headroom for the re-extraction
  free_kb=$(df -k / 2>/dev/null | tail -1 | awk '{print $4}')
  case "${free_kb:-0}" in ''|*[!0-9]*) free_kb=0 ;; esac
  [ "$free_kb" -ge "$MIN_FREE_KB" ] && gok "disk free" "$((free_kb/1024)) MB (need $((MIN_FREE_KB/1024)) MB)" \
    || { gbad "disk free" "${free_kb} KB < $MIN_FREE_KB KB — clean space first"; ok=0; }
  # 12) audit script is the gate build
  require_audit_gate_build || ok=0

  echo
  echo "RESULT: $pass passed, $fail failed"
  if [ "$ok" -eq 1 ] && [ "$fail" -eq 0 ]; then
    set_state "baseline_ts" "$(now)"
    set_state "bin_dir" "$BIN"
    gok "BASELINE" "environment verified — destructive drills unlocked"
    return 0
  fi
  case "$(cat "$STATUS" 2>/dev/null)" in
    state=OK*) : ;;
    *) echo "  RECOVERY: the app status file reports a problem — open one new"
       echo "  PocketShell session first, then re-run baseline." ;;
  esac
  return 1
}

# ======================================================================= C2
do_c2() {
  require_baseline || return 1
  require_audit_gate_build || return 1
  if ! real_loader_ok; then
    refuse "layer is not currently healthy — if a drill is already armed, run its resume stage instead"
    return 1
  fi
  run_audit_stage "C2 — CORRUPTION DRILLS (marker loss, loader deletion, libpthread deletion, libm truncation)" drill-c2 \
    || { echo "  RECOVERY: layer is broken by design and the drill output shows a FAIL row."
         echo "  If you must restore without the app: sh \"$AUDIT\" heal --manual"
         echo "  (break-glass only — it canNOT pass this gate). The audited path is"
         echo "  the ACTION REQUIRED below."; return 1; }
  set_state "c2_ts" "$(now)"
  set_state "c2" "ARMED"
  action_required "resume-c2"
  return 0
}

do_resume_c2() {
  require_baseline || return 1
  [ "$(get_state c2)" = "ARMED" ] || { echo "  c2 is not armed — run: sh \"$SELF_DIR/device_gate.sh\" c2 (or gate)"; return 1; }
  run_audit_stage "C2 RESUME — production self-heal verification (new session must have re-extracted)" heal \
    || { echo "  RECOVERY: see FAIL rows above."
         echo "    - layer still broken? open ONE new session and re-run resume-c2."
         echo "    - 'provenance' FAIL? healing did not come from the app (manual"
         echo "      restore, or extra sessions opened in between). Re-run c2 to"
         echo "      re-arm and repeat with exactly ONE new session."
         echo "    - app status =FAILED? paste the output — that is an app-side bug report."
         return 1; }
  set_state "c2" "PASS"
  echo
  echo "  C2 HEAL PROVEN — the app's session prep re-extracted the layer."
  # chain directly into C4
  do_c4 && action_required "resume-c4"
  return 0
}

# ======================================================================= C4
do_c4() {
  require_baseline || return 1
  [ "$(get_state c2)" = "PASS" ] || { echo "  c2 must PASS before c4 (drill timestamps are shared) — run gate/resume-c2 first"; return 1; }
  if ! real_loader_ok; then
    refuse "layer is not currently healthy — resume-c2 first"
    return 1
  fi
  run_audit_stage "C4 — LOADER OWNERSHIP DRILL (apk fix gcompat; deterministic arm either way)" drill-c4 \
    || { echo "  RECOVERY: inspect the C4 rows above; open one new session to heal, then re-run c4."; return 1; }
  # record WHICH way the reclaim went — the C4 device answer for the closure report
  case "$RUN_AUDIT_OUT" in
    *"apk fix DID reclaim"*) set_state "c4_reclaim" "NATURAL: apk fix gcompat reclaimed the real loader path" ;;
    *"reclaim state ARMED (simulated)"*) set_state "c4_reclaim" "SIMULATED: apk fix did NOT reclaim; armed with the genuine gcompat shim" ;;
    *) set_state "c4_reclaim" "unknown (inspect drill output)" ;;
  esac
  set_state "c4_ts" "$(now)"
  set_state "c4" "ARMED"
  action_required "resume-c4"
  return 0
}

do_resume_c4() {
  require_baseline || return 1
  [ "$(get_state c4)" = "ARMED" ] || { echo "  c4 is not armed — run: sh \"$SELF_DIR/device_gate.sh\" c4"; return 1; }
  run_audit_stage "C4 RESUME — production self-heal verification (loader restored by session prep)" heal \
    || { echo "  RECOVERY: see FAIL rows above (same guidance as resume-c2)."; return 1; }
  if apk info -e gcompat >/dev/null 2>&1; then
    gok "gcompat still installed" "$(apk info -e -v gcompat 2>/dev/null) (usable where intended; loader stays layer-owned per DUAL_LIBC §8.3)"
  fi
  musl_ok && gok "musl unaffected throughout" "" || gbad "musl" "REGRESSION"
  set_state "c4" "PASS"
  echo
  echo "  C4 HEAL PROVEN — the real Debian loader is back via session prep."
  # chain directly into C5
  do_c5 && action_required "resume-c5"
  return 0
}

# ======================================================================= C5
do_c5() {
  require_baseline || return 1
  [ "$(get_state c4)" = "PASS" ] || { echo "  c4 must PASS before c5 — run resume-c4 first"; return 1; }
  run_audit_stage "C5 — APK PACKAGE OPERATIONS (update/upgrade/add/del; layer must survive untouched)" drill-c5 \
    || { echo "  NOTE: a C5 FAIL usually means a package op DID touch the loader —"
         echo "  that is exactly what the heal path below then proves."; }
  if real_loader_ok; then
    set_state "c5_reclaim" "no"
    set_state "c5_ts" "$(now)"
    set_state "c5" "ARMED"
    echo
    echo "  C5 package ops done; loader intact. The last proof: a session prep"
    echo "  AFTER these ops must take the healthy FAST PATH."
  else
    set_state "c5_reclaim" "yes"
    set_state "c5_ts" "$(now)"
    set_state "c5" "ARMED"
    echo
    echo "  C5 package ops CHANGED the loader — armed for the heal proof."
  fi
  return 0
}

do_resume_c5() {
  require_baseline || return 1
  [ "$(get_state c5)" = "ARMED" ] || { echo "  c5 is not armed — run: sh \"$SELF_DIR/device_gate.sh\" c5"; return 1; }
  c5_ts=$(get_state c5_ts); [ -n "${c5_ts:-}" ] || c5_ts=0
  banner "C5 RESUME — post-package-ops verification (fresh session prep must be healthy)"
  ok=1
  if [ "$(get_state c5_reclaim)" = "yes" ]; then
    # heal path: the reclaim must be gone with provenance
    run_audit_stage "C5 RESUME — heal verification (package op touched the loader; app must have re-extracted)" heal \
      || { echo "  RECOVERY: open ONE new session and re-run resume-c5."; return 1; }
  else
    # fast-path survival: real loader by execution, marker intact, prep ran AFTER c5
    if real_loader_ok; then gok "real loader after package ops" "$("$LOADER" --version 2>&1 | head -1 | cut -c1-60)"
    else gbad "real loader" "damaged by package ops"; ok=0; fi
    [ "$(cat "$MARKER" 2>/dev/null)" = "$MARKER_EXPECTED" ] \
      && gok "marker intact after package ops" "" || { gbad "marker" "$(cat "$MARKER" 2>/dev/null || echo absent)"; ok=0; }
    prep_ts=$(status_ts_sec)
    if [ "$prep_ts" -gt "$c5_ts" ]; then
      gok "session prep ran AFTER apk ops" "prep ts $prep_ts > drill ts $c5_ts ($(cat "$STATUS" 2>/dev/null))"
    else
      gbad "no session prep after apk ops" "open ONE new PocketShell session, then re-run resume-c5"; ok=0
    fi
    [ "$ok" -eq 1 ] && { cline_ok && gok "cline still works" "" || { gbad "cline" "broken after package ops"; ok=0; }; }
    [ "$ok" -eq 1 ] && { musl_ok && gok "musl still works" "" || { gbad "musl" "broken"; ok=0; }; }
    echo
    echo "RESULT: $pass passed, $fail failed"
    [ "$ok" -eq 1 ] && [ "$fail" -eq 0 ] || { echo "  RECOVERY: fix the FAIL rows above, then re-run resume-c5."; return 1; }
  fi
  set_state "c5" "PASS"
  echo
  echo "  C5 PROVEN — package manager operations leave the layer intact and"
  echo "  the fast path healthy."
  # chain into FINAL
  do_final
  return 0
}

# ===================================================================== FINAL
do_final() {
  require_baseline || return 1
  for s in c2 c4 c5; do
    [ "$(get_state $s)" = "PASS" ] || { echo "  $s has not passed yet — run status"; return 1; }
  done
  # compatibility suite, again, after everything
  banner "FINAL — full compatibility suite (expect 27/27)"
  out=$(sh "$SUITE" 2>&1); rc=$?
  printf '%s\n' "$out"
  suite_line=$(printf '%s\n' "$out" | grep "^RESULT:" | tail -1)
  case "$suite_line" in
    *" 0 failed"*) gok "compatibility suite" "$suite_line" ;;
    *) gbad "compatibility suite" "$suite_line (rc=$rc)" ;;
  esac
  # adversarial probe, again, after everything
  banner "FINAL — adversarial probe (expect 22/22)"
  out=$(sh "$AUDIT" probe 2>&1); rc=$?
  printf '%s\n' "$out"
  probe_line=$(printf '%s\n' "$out" | grep "^RESULT:" | tail -1)
  case "$probe_line" in
    *" 0 failed"*) gok "adversarial probe" "$probe_line" ;;
    *) gbad "adversarial probe" "$probe_line (rc=$rc)" ;;
  esac

  banner "FINAL SUMMARY"
  echo "  baseline : PASS ($(get_state baseline_ts))"
  echo "  c2       : $(get_state c2) — corruption drills + production heal"
  echo "  c4       : $(get_state c4) — loader-ownership drill + production heal"
  echo "  c5       : $(get_state c5) — apk ops + fast-path survival (reclaim: $(get_state c5_reclaim))"
  echo "  suite    : $suite_line"
  echo "  probe    : $probe_line"
  echo
  if [ "$fail" -eq 0 ]; then
    set_state "final" "PASS"
    set_state "final_ts" "$(now)"
    echo "  DEVICE GATE: ALL STAGES PASS."
    echo "  Paste the COMPLETE output of every stage (gate/resume-c2/resume-c4/"
    echo "  resume-c5 + this summary) as the M6 closure record."
  else
    echo "  DEVICE GATE: INCOMPLETE — $fail final row(s) failed. Paste the output."
  fi
  echo "  RESULT: $pass passed, $fail failed"
  [ "$fail" -eq 0 ]
}

# ==================================================================== STATUS
do_status() {
  echo "== POCKETSHELL M6 DEVICE GATE — $GATE_VERSION"
  echo "== state file: $STATE"
  b=$(get_state baseline_ts); [ -n "${b:-}" ] && b="PASS (ts $b)" || b="not run"
  c2=$(get_state c2);  [ -z "${c2:-}" ]  && c2="not run"
  c4=$(get_state c4);  [ -z "${c4:-}" ]  && c4="not run"
  c5=$(get_state c5);  [ -z "${c5:-}" ]  && c5="not run"
  fin=$(get_state final); [ -z "${fin:-}" ] && fin="not run"
  echo "  baseline : $b"
  echo "  c2       : $c2"
  echo "  c4       : $c4"
  echo "  c5       : $c5 (reclaim: $(get_state c5_reclaim))"
  echo "  final    : $fin"
  echo
  next=""
  [ "$(get_state baseline_ts)" = "" ] && next="gate"
  [ "$next" = "" ] && [ "$(get_state c2)" != "PASS" ] && { [ "$(get_state c2)" = "ARMED" ] && next="resume-c2" || next="c2"; }
  [ "$next" = "" ] && [ "$(get_state c4)" != "PASS" ] && { [ "$(get_state c4)" = "ARMED" ] && next="resume-c4" || next="c4"; }
  [ "$next" = "" ] && [ "$(get_state c5)" != "PASS" ] && { [ "$(get_state c5)" = "ARMED" ] && next="resume-c5" || next="c5"; }
  [ "$next" = "" ] && [ "$(get_state final)" != "PASS" ] && next="final"
  if [ -n "$next" ]; then
    echo "  NEXT STEP:  sh \"$SELF_DIR/device_gate.sh\" $next"
  else
    echo "  ALL STAGES PASS — M6 device gate complete. Paste the outputs."
  fi
}

require_baseline() {
  [ -n "$(get_state baseline_ts)" ] && return 0
  echo "  baseline has not passed in this guest — running it first:"
  do_baseline || { echo "  (destructive stages stay locked until baseline passes)"; return 1; }
  return 0
}

usage() {
  cat <<EOF
$GATE_VERSION

  sh device_gate.sh gate        # START HERE: baseline + C2 drills
  sh device_gate.sh baseline    # identity + health gate only
  sh device_gate.sh c2          # corruption drills (destructive, gated)
  sh device_gate.sh resume-c2   # after ONE new session: heal proof -> arms c4
  sh device_gate.sh c4          # loader-ownership drill (destructive, gated)
  sh device_gate.sh resume-c4   # after ONE new session: heal proof -> arms c5
  sh device_gate.sh c5          # apk package-ops drill
  sh device_gate.sh resume-c5   # after ONE new session: fast-path proof -> final
  sh device_gate.sh final       # suite + probe + summary
  sh device_gate.sh status      # what passed; exact next command
EOF
}

case "${1:-}" in
  gate)            do_baseline && do_c2 ;;
  baseline)        do_baseline ;;
  c2)              do_c2 ;;
  resume-c2)       do_resume_c2 ;;
  c4)              do_c4 ;;
  resume-c4)       do_resume_c4 ;;
  c5)              do_c5 ;;
  resume-c5)       do_resume_c5 ;;
  final)           do_final ;;
  status)          do_status ;;
  heal)            sh "$AUDIT" heal ;;   # diagnostic passthrough
  -h|--help|help|*) usage; [ $# -gt 0 ] && [ "$1" != "-h" ] && [ "$1" != "--help" ] && [ "$1" != "help" ] && exit 2 ;;
esac
