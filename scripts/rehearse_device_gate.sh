#!/bin/sh
# rehearse_device_gate.sh — SANDBOX rehearsal of the M6 device gate.
#
# Builds a fake guest root (POCKETSHELL_GATE_ROOT), fake guest binaries and a
# fake app, then drives device_gate.sh through every stage — including the
# negative cases (stale audit build, unknown environment, manual-restore
# provenance trap, stale-marker provenance trap). On the device the gate runs
# with POCKETSHELL_GATE_ROOT UNSET — byte-identical logic, real paths; this
# rehearsal proves the RUNNER logic (state machine, chaining, guards,
# provenance) before a single destructive command runs on real hardware.
#
# The fakes are honest stand-ins:
#  - the fake loader/doctor/test-binaries inspect the SAME filesystem state
#    the real ones do (loader symlink shape, lib presence, libm size), so
#    the drills corrupting the fakeroot produce the same honest rows;
#  - simulate_app_heal() mirrors GuestGlibcRuntime.ensureInstalled exactly
#    where the gate can observe it: pristine files restored, marker written
#    LAST (fresh mtime), status written after (source=extractor).
#  - the compatibility suite is a FAKE (27/27 printout) — the real suite is
#    already device-proven; this rehearsal tests the gate around it.
set -u
REHEARSAL_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/.gate-rehearsal
REPO=$(CDPATH= cd -- "$REHEARSAL_DIR/../.." && pwd)
RT="$REPO/runtime-tests"
GATE="$RT/device_gate.sh"
AUDIT="$RT/adversarial_closure_audit.sh"
MARKER_TXT="PocketShell glibc runtime layer 2.41-12.deb13u3 (glibc 2.41) rev=2"
STAMP_TXT="0.10.0-m6.0.4 (versionCode 44)"

rp=0; rf=0
assert_ok() { # label condition-rc
  if [ "$2" -eq 0 ]; then printf "  REHEARSAL-PASS %-52s\n" "$1"; rp=$((rp+1));
  else printf "  REHEARSAL-FAIL %-52s\n" "$1"; rf=$((rf+1)); fi
}
assert_contains() { # label haystack pattern
  printf '%s' "$2" | grep -q "$3"; assert_ok "$1" $?
}
assert_not_contains() { # label haystack pattern
  printf '%s' "$2" | grep -q "$3"; [ $? -ne 0 ]; assert_ok "$1" $?
}

# ----------------------------------------------------------- fake binaries
write_loader() { # $1 = target path (the canonical loader file)
  cat > "$1" <<'EOF'
#!/bin/sh
# FAKE Debian loader (rehearsal only)
case "$1" in
  --version)
    echo "ld.so (Debian GLIBC 2.41-12+deb13u3) stable release version 2.41."
    exit 0 ;;
esac
exit 0
EOF
  chmod 755 "$1"
}

write_doctor() { # $1 = path
  cat > "$1" <<'EOF'
#!/bin/sh
# FAKE pocketshell-doctor v2 (rehearsal only) — mirrors the real fact
# hierarchy where the gate can observe it: layer health first, then ELF magic.
root="${POCKETSHELL_GATE_ROOT:-}"
loader="$root/lib/ld-linux-aarch64.so.1"
canon="$root/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
if [ "${1:-}" = "--selftest" ]; then echo "SELFTEST PASS 15/15 cases"; exit 0; fi
bin="${1:-}"
if [ ! -f "$bin" ]; then echo "Reason: file not found"; echo "Compatibility: UNSUPPORTED"; exit 2; fi
# ELF magic applies to real unknown files (the probe's non-ELF case); the
# rehearsal's own script-based test binaries are exempt.
case "$bin" in
  "${POCKETSHELL_TESTS_DIR:-__none__}"/*|*/bin/t_*|*/t_*) : ;;
  *)
    if ! head -c4 "$bin" 2>/dev/null | od -An -c | tr -d ' \n' | grep -q '177ELF'; then
      echo "Class: not an ELF"; echo "Compatibility: UNSUPPORTED"; exit 1
    fi ;;
esac
healthy=1
[ -L "$loader" ] || healthy=0
[ "$healthy" = 1 ] && { [ "$(readlink "$loader" 2>/dev/null)" = "$canon" ] || healthy=0; }
[ "$healthy" = 1 ] && [ -s "$canon" ] || healthy=0
for f in libc.so.6 libpthread.so.0 libdl.so.2 libstdc++.so.6; do
  [ "$healthy" = 1 ] && [ -s "$root/usr/lib/aarch64-linux-gnu/$f" ] || healthy=0
done
[ "$healthy" = 1 ] && [ "$(stat -c %s "$root/usr/lib/aarch64-linux-gnu/libm.so.6" 2>/dev/null || echo 0)" -gt 4096 ] || healthy=0
if [ "$healthy" = 1 ]; then
  echo "Class: dynamically-linked ELF (glibc)"
  echo "Required GLIBC: GLIBC_2.17"
  echo "Compatibility: SUPPORTED (layer provides glibc 2.41)"
  exit 0
fi
echo "Reason: layer is not installed (loader: MISSING or shim at the loader path)"
echo "Compatibility: UNSUPPORTED"
exit 1
EOF
  chmod 755 "$1"
}

# A glibc-shaped test binary: runs only when the loader symlink resolves to
# the real (fake) loader; extra per-binary load requirements honoured.
write_t_bin() { # $1 path  $2 output  $3 extra-lib(check:file:size-min)
  cat > "$1" <<EOF
#!/bin/sh
# FAKE glibc test binary (rehearsal only)
root="\${POCKETSHELL_GATE_ROOT:-}"
loader="\$root/lib/ld-linux-aarch64.so.1"
canon="\$root/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
[ -L "\$loader" ] && [ "\$(readlink "\$loader" 2>/dev/null)" = "\$canon" ] && [ -s "\$canon" ] || exit 127
extra="$3"
if [ -n "\$extra" ]; then
  lib="\$root/usr/lib/aarch64-linux-gnu/\$(printf '%s' "\$extra" | cut -d: -f2)"
  min=\$(printf '%s' "\$extra" | cut -d: -f3)
  [ "\$(stat -c %s "\$lib" 2>/dev/null || echo 0)" -ge "\$min" ] || exit 127
fi
echo "$2"
exit 0
EOF
  chmod 755 "$1"
}

write_apk() { # $1 = path
  cat > "$1" <<'EOF'
#!/bin/sh
# FAKE apk (rehearsal only): gcompat is installed; fetch unavailable (drives
# drill-c4's genuine-shim path into its documented ELF-copy fallback).
case "${1:-}" in
  info)
    [ "${2:-}" = "-e" ] || { echo "apk-tools 3.0.0 (fake)"; exit 0; }
    [ "${3:-}" = "-v" ] && { echo "gcompat-1.1.0-r4 (fake)"; exit 0; }
    exit 0 ;;
  fetch) exit 1 ;;
  *) exit 0 ;;
esac
EOF
  chmod 755 "$1"
}

# ----------------------------------------------------------- fakeroot build
build_fakeroot() { # $1 = root dir
  R="$1"
  rm -rf "$R"; mkdir -p "$R"
  # NOTE: lib/aarch64-linux-gnu is a SYMLINK in the real layer shape — never mkdir it
  mkdir -p "$R/etc/pocketshell" "$R/lib" "$R/usr/local/bin" "$R/bin"
  echo "3.24.1" > "$R/etc/alpine-release"
  echo "rehearsal" > "$R/etc/hostname"
  echo "$STAMP_TXT" > "$R/etc/pocketshell/app-version"
  echo "$MARKER_TXT" > "$R/etc/pocketshell/glibc-runtime"
  echo "state=OK source=fastpath ts=$(( $(date +%s) * 1000 ))" > "$R/etc/pocketshell/glibc-runtime.status"
  M="$R/usr/lib/aarch64-linux-gnu"; mkdir -p "$M"
  write_loader "$M/ld-linux-aarch64.so.1"
  for f in libc.so.6 libpthread.so.0 libdl.so.2 libstdc++.so.6; do
    head -c 2048 /dev/zero | tr '\0' 'x' > "$M/$f"
  done
  head -c 16384 /dev/zero | tr '\0' 'x' > "$M/libm.so.6"
  i=1; while [ "$i" -le 55 ]; do
    head -c 512 /dev/zero | tr '\0' 'x' > "$M/libFiller$i.so"; i=$((i+1))
  done
  ln -s "../usr/lib/aarch64-linux-gnu" "$R/lib/aarch64-linux-gnu"
  ln -s "$M/ld-linux-aarch64.so.1" "$R/lib/ld-linux-aarch64.so.1"
  echo "musl-loader (fake)" > "$R/lib/ld-musl-aarch64.so.1"
  ln -s /bin/sh "$R/bin/sh"
  mkdir -p "$R/usr/local/lib/node_modules/cline/node_modules/@cline/cli-linux-arm64/bin"
  printf '#!/bin/sh\necho 3.0.61\n' > "$R/usr/local/lib/node_modules/cline/node_modules/@cline/cli-linux-arm64/bin/cline"
  chmod 755 "$R/usr/local/lib/node_modules/cline/node_modules/@cline/cli-linux-arm64/bin/cline"
  # pristine snapshot for the app-heal simulation
  PRISTINE="$1.pristine-multarch"; rm -rf "$PRISTINE"; cp -a "$M" "$PRISTINE"
}

# The app stand-in: exactly what the gate can observe after a REAL session
# prep — pristine layer files, loader symlink, marker written LAST (fresh
# mtime), status written right after (source=extractor, ts in millis).
simulate_app_heal() { # $1 = root
  R="$1"
  sleep 1   # provenance compares mtimes at 1s granularity — never collide with the drill ts
  rm -rf "$R/usr/lib/aarch64-linux-gnu"
  cp -a "$R.pristine-multarch" "$R/usr/lib/aarch64-linux-gnu"
  rm -f "$R/lib/ld-linux-aarch64.so.1"
  ln -s "$R/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1" "$R/lib/ld-linux-aarch64.so.1"
  echo "$MARKER_TXT" > "$R/etc/pocketshell/glibc-runtime"
  echo "state=OK source=extractor entries=103 ts=$(( $(date +%s) * 1000 ))" > "$R/etc/pocketshell/glibc-runtime.status"
  [ "$(stat -c %s "$R/usr/lib/aarch64-linux-gnu/libm.so.6" 2>/dev/null)" -gt 4096 ] \
    || { echo "REHEARSAL-BUG: simulate_app_heal did not restore the layer"; exit 90; }
}

# The app stand-in for a warm (fast-path) session prep.
simulate_app_fastpath() { # $1 = root
  sleep 2   # resume-c5 requires prep ts STRICTLY after the drill ts (1s granularity)
  echo "state=OK source=fastpath ts=$(( $(date +%s) * 1000 ))" > "$1/etc/pocketshell/glibc-runtime.status"
}

# Manual restore stand-in: makes the layer healthy WITHOUT the app — the
# provenance trap (marker mtime fresh, but no extractor status).
simulate_manual_restore() { # $1 = root
  R="$1"
  rm -rf "$R/usr/lib/aarch64-linux-gnu"
  cp -a "$R.pristine-multarch" "$R/usr/lib/aarch64-linux-gnu"
  rm -f "$R/lib/ld-linux-aarch64.so.1"
  ln -s "$R/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1" "$R/lib/ld-linux-aarch64.so.1"
  echo "$MARKER_TXT" > "$R/etc/pocketshell/glibc-runtime"
}

# --------------------------------------------------------------- fake suite
build_fake_suite() { # $1 = tests dir (flat)
  T="$1"; rm -rf "$T"; mkdir -p "$T"
  write_t_bin "$T/t_static" "hello-glibc" ""
  write_t_bin "$T/t_hello" "hello-glibc" ""
  write_t_bin "$T/t_pthread" "pthread-ok" "check:libpthread.so.0:512"
  write_t_bin "$T/t_libm" "libm-ok" "check:libm.so.6:4097"
  write_t_bin "$T/t_dlopen" "dlopen-libm-ok" "check:libm.so.6:4097"
  write_t_bin "$T/t_cpp" "cpp-ok" ""
  write_t_bin "$T/t_fork_exec" "fork-exec-ok" ""
  write_t_bin "$T/t_getpwnam" "getpwnam-ok" ""
  write_t_bin "$T/t_getaddrinfo" "getaddrinfo-ok" ""
  write_t_bin "$T/t_cline_shape" "pthread-ok" "check:libpthread.so.0:512"
  cat > "$T/run_on_device.sh" <<'EOF'
#!/bin/sh
# FAKE compatibility suite (rehearsal only) — the real run_on_device.sh is
# already device-proven 27/27; this stands in for the gate's FINAL parsing.
echo "== POCKETSHELL EXECUTABLE COMPATIBILITY SUITE (device) [FAKE]"
echo "=== TIER 1 — musl ==="
echo "=== TIER 2 — real glibc layer ==="
echo "=== pocketshell-doctor ==="
echo "=== TIER 2/3 — CLINE 3.0.61 ==="
echo "RESULT: 27 passed, 0 failed, 0 skipped"
echo "VERDICT: universal runtime compatibility — ALL GREEN"
exit 0
EOF
  chmod 755 "$T/run_on_device.sh"
}

build_fakebin() { # $1 = fakebin dir
  F="$1"; rm -rf "$F"; mkdir -p "$F"
  write_doctor "$F/pocketshell-doctor"
  write_apk "$F/apk"
  printf '#!/bin/sh\necho file-5.45 (fake)\n' > "$F/file"; chmod 755 "$F/file"
  printf '#!/bin/sh\necho v22.9.0 (fake)\n' > "$F/node"; chmod 755 "$F/node"
}

# ---------------------------------------------------------------- scenario
echo "== POCKETSHELL M6 DEVICE-GATE REHEARSAL (sandbox, no device involved)"
rm -rf "$REHEARSAL_DIR"; mkdir -p "$REHEARSAL_DIR"
ROOT1="$REHEARSAL_DIR/root"; ROOT2="$REHEARSAL_DIR/root-badmarker"
FAKEBIN="$REHEARSAL_DIR/fakebin"; FAKEBIN2="$REHEARSAL_DIR/fakebin2"
TESTS="$REHEARSAL_DIR/tests"
build_fakeroot "$ROOT1"
build_fake_suite "$TESTS"
build_fakebin "$FAKEBIN"
# fakebin2: WITHOUT the apk fetch-failure (fetch also fails — same fake; kept
# for the stale-audit scenario so only the audit build differs)
build_fakebin "$FAKEBIN2"

gate_run() { # $1 = output label; remaining args = gate stage
  label="$1"; shift
  out=$(POCKETSHELL_GATE_ROOT="$ROOT1" POCKETSHELL_TESTS_DIR="$TESTS" \
        POCKETSHELL_GATE_SUITE="$TESTS/run_on_device.sh" \
        PATH="$FAKEBIN:$PATH" sh "$GATE" "$@" 2>&1); rc=$?
  printf '%s\n' "$out" > "$REHEARSAL_DIR/out-$label.txt"
}

echo
echo "--- T1: status on a fresh environment"
gate_run t1-status status
assert_contains "T1 status shows baseline not run" "$out" "baseline : not run"
assert_contains "T1 status proposes gate" "$out" "device_gate.sh\" gate"

echo
echo "--- T2: stale (pre-gate) audit build is REFUSED"
STALE="$REHEARSAL_DIR/stale"; mkdir -p "$STALE"
cp "$GATE" "$STALE/device_gate.sh"
sed 's/phase-c-g/phase-c/' "$AUDIT" > "$STALE/adversarial_closure_audit.sh"
out=$(POCKETSHELL_GATE_ROOT="$ROOT1" POCKETSHELL_TESTS_DIR="$TESTS" \
      PATH="$FAKEBIN:$PATH" sh "$STALE/device_gate.sh" baseline 2>&1); rc=$?
assert_contains "T2 stale audit refused" "$out" "REFUSED"
assert_contains "T2 refusal names re-download" "$out" "Re-download the tests tarball"

echo
echo "--- T3: unknown environment (wrong marker) is REFUSED"
build_fakeroot "$ROOT2"
echo "some other layer rev=0" > "$ROOT2/etc/pocketshell/glibc-runtime"
out=$(POCKETSHELL_GATE_ROOT="$ROOT2" POCKETSHELL_TESTS_DIR="$TESTS" \
      PATH="$FAKEBIN2:$PATH" sh "$GATE" gate 2>&1); rc=$?
assert_contains "T3 wrong marker refused" "$out" "marker pin"
assert_not_contains "T3 c2 NOT armed" "$(cat "$ROOT2/tmp/pocketshell-gate/state" 2>/dev/null || echo none)" "c2=ARMED"

echo
echo "--- T4: happy path — gate (baseline + c2 arm)"
gate_run t4-gate gate
assert_contains "T4 baseline passes" "$out" "BASELINE.*environment verified"
assert_contains "T4 c2 drills armed" "$out" "C2.2 loader deleted"
assert_contains "T4 action required shown" "$out" "ACTION REQUIRED"
assert_contains "T4 state c2=ARMED" "$(cat "$ROOT1/tmp/pocketshell-gate/state")" "c2=ARMED"
[ ! -e "$ROOT1/lib/ld-linux-aarch64.so.1" ]; assert_ok "T4 loader really deleted by drill" $?
[ "$(stat -c %s "$ROOT1/usr/lib/aarch64-linux-gnu/libm.so.6")" -le 4096 ]; assert_ok "T4 libm really truncated" $?

echo
echo "--- T5: provenance trap — manual restore cannot pass resume-c2"
simulate_manual_restore "$ROOT1"
gate_run t5-resume-c2 resume-c2
assert_contains "T5 heal health rows pass but provenance fails" "$out" "heal provenance.*NOT proven"
assert_contains "T5 recovery guidance shown" "$out" "RECOVERY"
assert_contains "T5 c2 still armed" "$(cat "$ROOT1/tmp/pocketshell-gate/state")" "c2=ARMED"

echo
echo "--- T6: fresh-marker trap — app heal with a backdated marker fails"
gate_run t6-gate gate   # re-arm from the healthy (manually restored) state
simulate_app_heal "$ROOT1"
drill_ts=$(cat "$ROOT1/tmp/.phasec_drill_ts")
touch -d "@$(( drill_ts - 100 ))" "$ROOT1/etc/pocketshell/glibc-runtime"
gate_run t6-resume-c2 resume-c2
assert_contains "T6 backdated marker caught" "$out" "heal provenance.*NOT proven"
assert_contains "T6 c2 still armed" "$(cat "$ROOT1/tmp/pocketshell-gate/state")" "c2=ARMED"

echo
echo "--- T7: production heal proven — resume-c2 passes and chains c4"
gate_run t7-gate gate   # re-arm once more
simulate_app_heal "$ROOT1"
gate_run t7-resume-c2 resume-c2
assert_contains "T7 heal provenance passes" "$out" "heal provenance .production session-prep heal."
assert_contains "T7 c2 PASS" "$(cat "$ROOT1/tmp/pocketshell-gate/state")" "c2=PASS"
assert_contains "T7 chains into c4 drill" "$out" "C4 — LOADER OWNERSHIP DRILL"
assert_contains "T7 c4 armed" "$(cat "$ROOT1/tmp/pocketshell-gate/state")" "c4=ARMED"
[ ! -L "$ROOT1/lib/ld-linux-aarch64.so.1" ]; assert_ok "T7 loader path now the reclaim shape (regular file)" $?
[ -f "$ROOT1/lib/ld-linux-aarch64.so.1" ]; assert_ok "T7 shim copy really placed" $?

echo
echo "--- T8: resume-c4 proves the loader restoration; chains c5"
simulate_app_heal "$ROOT1"
gate_run t8-resume-c4 resume-c4
assert_contains "T8 heal provenance passes" "$out" "heal provenance .production session-prep heal."
assert_contains "T8 c4 PASS" "$(cat "$ROOT1/tmp/pocketshell-gate/state")" "c4=PASS"
assert_contains "T8 c5 armed" "$(cat "$ROOT1/tmp/pocketshell-gate/state")" "c5=ARMED"
assert_contains "T8 c4 answer recorded" "$(cat "$ROOT1/tmp/pocketshell-gate/state")" "c4_reclaim=SIMULATED"

echo
echo "--- T9: resume-c5 fast-path proof chains the FINAL"
simulate_app_fastpath "$ROOT1"
gate_run t9-resume-c5 resume-c5
assert_contains "T9 prep-after-c5 verified" "$out" "session prep ran AFTER apk ops"
assert_contains "T9 c5 PASS" "$(cat "$ROOT1/tmp/pocketshell-gate/state")" "c5=PASS"
assert_contains "T9 final ran" "$out" "FINAL SUMMARY"
assert_contains "T9 suite row green" "$out" "RESULT: 27 passed, 0 failed, 0 skipped"
assert_contains "T9 device gate all pass" "$out" "DEVICE GATE: ALL STAGES PASS"

echo
echo "--- T10: final status reports completion"
gate_run t10-status status
assert_contains "T10 all stages pass" "$out" "ALL STAGES PASS"

echo
echo "REHEARSAL RESULT: $rp passed, $rf failed"
if [ "$rf" -eq 0 ]; then
  rm -rf "$REHEARSAL_DIR"
  echo "REHEARSAL: ALL GREEN — device_gate.sh runner logic verified end-to-end in the sandbox."
  exit 0
fi
echo "REHEARSAL: FAILURES PRESENT — fakeroot + per-stage outputs kept at $REHEARSAL_DIR (out-*.txt)"
exit 1
