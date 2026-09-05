#!/bin/sh
# run_on_device.sh — PocketShell executable compatibility suite (runs INSIDE
# the Alpine guest terminal). Companion to scripts/runtime/run_sandbox_suite.sh
# (same matrix, no emulation). Expects the test binaries staged in the same
# directory as this script (default /tmp/pocketshell-tests).
#
# Device usage (PocketShell terminal):
#   curl -fsSL <mirror>/pocketshell-runtime-tests-aarch64.tar.gz | tar -xz -C /tmp
#   sh /tmp/pocketshell-tests/run_on_device.sh
#
# v0.10.0-m6.0.1 (device-gate lesson): a missing glibc layer is a SKIP with a
# diagnosis, not a wall of FAILs. The PREFLIGHT section reports exactly why
# the layer is (not) present:
#   - /etc/pocketshell/glibc-runtime          marker (completeness contract)
#   - /etc/pocketshell/glibc-runtime.status   last app-side install outcome
#   - what /lib/ld-linux-aarch64.so.1 really is right now
# Escape hatch (repairs the layer WITHOUT waiting for the app):
#   POCKETSHELL_INSTALL_LAYER=1 sh run_on_device.sh
#     uses $POCKETSHELL_LAYER_URL, or a pocketshell-glibc-*.tar.gz placed in
#     the tests dir (or its parent). Installs + writes the marker the app's
#     fast path recognizes.
#
# Every claim is probed. Nothing is assumed. Output is a paste-ready table.
set -u
DIR="${POCKETSHELL_TESTS_DIR:-/tmp/pocketshell-tests}"
CLINE=/usr/local/lib/node_modules/cline/node_modules/@cline/cli-linux-arm64/bin/cline
MARKER=/etc/pocketshell/glibc-runtime
STATUS=/etc/pocketshell/glibc-runtime.status
LAYER_NAME=pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz

pass=0; fail=0; skip=0; layer_ok=0
row() { printf "%-6s %-38s %s\n" "$1" "$2" "$3" | cut -c1-140; }
ok()   { row PASS "$1" "$2"; pass=$((pass+1)); }
bad()  { row FAIL "$1" "$2"; fail=$((fail+1)); }
noun() { row SKIP "$1" "$2"; skip=$((skip+1)); }

check() { # check <name> <want> <cmd...>
  name="$1"; want="$2"; shift 2
  out=$("$@" 2>&1)
  rc=$?
  if printf '%s' "$out" | grep -q "$want"; then
    ok "$name" "$(printf '%s' "$out" | grep "$want" | head -1)"
  else
    bad "$name" "rc=$rc $(printf '%s' "$out" | head -1)"
  fi
}

layer_present() {
  # Capability, not paperwork: the layer is present when the REAL loader runs
  # ("stable release version"). The marker additionally signals an app-managed
  # install; a working layer without one (e.g. manual repair) is fine — the
  # app re-extracts idempotently on its next session prep.
  [ -f "$MARKER" ] && return 0
  /lib/ld-linux-aarch64.so.1 --version 2>&1 | grep -q 'stable release version'
}
layer_present && layer_ok=1

echo "== POCKETSHELL EXECUTABLE COMPATIBILITY SUITE (device)"
echo "== guest: $(cat /etc/alpine-release 2>/dev/null || echo '?') / $(/bin/busybox | head -1 | grep -o 'BusyBox v[0-9.]*')"
echo

echo "=== PREFLIGHT — glibc layer state (diagnosis, not pass/fail) ==="
if layer_present; then
  echo "  marker  : $(cat "$MARKER" 2>/dev/null || echo '(absent — working layer without app marker; the app re-extracts idempotently, harmless)')"
else
  echo "  marker  : MISSING"
fi
if [ -f "$STATUS" ]; then
  echo "  status  : $(cat "$STATUS")"
else
  echo "  status  : (none — outcome file appears after the first vc40+ session prep)"
fi
loader_line=$(/lib/ld-linux-aarch64.so.1 --version 2>&1 | head -1)
case "$loader_line" in
  *gcompat*) echo "  loader  : gcompat STUB (shim — no real glibc; C++/Cline will fail)" ;;
  *2.41*)    echo "  loader  : $loader_line" ;;
  '')        echo "  loader  : /lib/ld-linux-aarch64.so.1 absent" ;;
  *)         echo "  loader  : $loader_line" ;;
esac
echo "  layer libs: $(ls /usr/lib/aarch64-linux-gnu 2>/dev/null | wc -l) files in /usr/lib/aarch64-linux-gnu"
apk info -e gcompat >/dev/null 2>&1 && echo "  gcompat : installed in this rootfs (historical; harmless once the layer is in)" || true
echo "  disk    : $(df -h / | tail -1 | awk '{print $4" free"}')"

echo
echo "=== ESCAPE HATCH (only with POCKETSHELL_INSTALL_LAYER=1) ==="
if [ -n "${POCKETSHELL_INSTALL_LAYER:-}" ] && ! layer_present; then
  SRC=""
  if [ -n "${POCKETSHELL_LAYER_URL:-}" ]; then
    SRC="$POCKETSHELL_LAYER_URL"; how="url"
  else
    for c in "$DIR/$LAYER_NAME" "$DIR/../$LAYER_NAME" "$DIR/../../$LAYER_NAME"; do
      [ -f "$c" ] && { SRC="$c"; how="local"; break; }
    done
  fi
  if [ -z "$SRC" ]; then
    bad "hatch: locate layer" "no $LAYER_NAME in $DIR[/..] and no POCKETSHELL_LAYER_URL set"
  else
    case "$how" in
      url)   if curl -fsSL "$SRC" | tar -xz -C / 2>/dev/null; then h=0; else h=1; fi ;;
      local) if tar -xzf "$SRC" -C / 2>/dev/null; then h=0; else h=1; fi ;;
    esac
    if [ "$h" -eq 0 ]; then
      mkdir -p /etc/pocketshell
      echo "PocketShell glibc runtime layer 2.41-12.deb13u3 (glibc 2.41)" > "$MARKER"
      echo "state=OK source=manual-hatch ts=$(date +%s)" > "$STATUS"
      layer_present && layer_ok=1
      check "hatch: loader is real 2.41" "stable release version 2.41" /lib/ld-linux-aarch64.so.1 --version
    else
      bad "hatch: extract layer" "tar -xzf failed from: $SRC"
    fi
  fi
elif [ -n "${POCKETSHELL_INSTALL_LAYER:-}" ]; then
  echo "  (layer already present — hatch not needed)"
fi

echo
echo "=== TIER 1 — musl (must be unaffected; runs regardless of layer) ==="
check "busybox sh"        "musl-ok"       /bin/sh -c 'echo musl-ok'
check "bash"              "version 5"     /bin/bash --version
check "git"               "git version"   /usr/bin/git --version
check "curl"              "libcurl"       /usr/bin/curl --version
check "node"              "v"             /usr/bin/node --version
check "npm"               "^[0-9][0-9]*\."  /usr/bin/npm --version
check "apk"               "apk-tools"     /sbin/apk --version
check "apk install probe" "musl"          /sbin/apk info -e musl

echo "=== TIER 1 — static (no loader needed) ==="
[ -x "$DIR/t_static" ] && check "static (glibc -static)" "hello-glibc" "$DIR/t_static" \
                       || bad "static (glibc -static)" "binary missing (download the suite tarball)"

if layer_present; then
  echo
  echo "=== TIER 2 — real glibc layer ==="
  check "glibc loader --version" "stable release version" /lib/ld-linux-aarch64.so.1 --version
  for t in t_hello t_pthread t_dlopen t_libm t_cpp t_fork_exec t_getpwnam t_getaddrinfo t_cline_shape; do
    if [ -x "$DIR/$t" ]; then
      case "$t" in
        t_hello)      check "glibc hello"       "hello-glibc"   "$DIR/$t" ;;
        t_pthread)    check "glibc pthread"     "pthread-ok"    "$DIR/$t" ;;
        t_dlopen)     check "glibc dlopen"      "dlopen-libm-ok" "$DIR/$t" ;;
        t_libm)       check "glibc libm"        "libm-ok"       "$DIR/$t" ;;
        t_cpp)        check "glibc C++ exc"     "cpp-ok"        "$DIR/$t" ;;
        t_fork_exec)  check "glibc fork+exec"   "fork-exec-ok"  "$DIR/$t" ;;
        t_getpwnam)   check "glibc NSS passwd"  "getpwnam-ok"   "$DIR/$t" ;;
        t_getaddrinfo) check "glibc NSS dns"    "getaddrinfo-ok" "$DIR/$t" ;;
        t_cline_shape) check "glibc Cline-shape deps" "pthread-ok" "$DIR/$t" ;;
      esac
    else
      bad "$t" "binary missing (download the suite tarball)"
    fi
  done

  echo "=== pocketshell-doctor ==="
  if command -v pocketshell-doctor >/dev/null 2>&1; then
    if pocketshell-doctor "$DIR/t_cline_shape" 2>/dev/null | grep -q "SUPPORTED"; then
      ok "doctor verdict (t_cline_shape)" "SUPPORTED"
    else
      bad "doctor verdict (t_cline_shape)" "$(pocketshell-doctor "$DIR/t_cline_shape" 2>&1 | tail -1)"
    fi
  else
    bad "pocketshell-doctor" "in the layer (/usr/local/bin) but not on PATH — check rootfs PATH"
  fi

  echo "=== TIER 2/3 — CLINE 3.0.61 (the real glibc-native test) ==="
  if [ -x "$CLINE" ]; then
    check "cline --version"        "3.0.61" "$CLINE" --version
    check "cline --help"           "^Usage" sh -c "$CLINE --help 2>&1 | head -3"
    check "cline via node spawn"   "3.0.61" /usr/bin/node -e "console.log(require('child_process').execFileSync('$CLINE',['--version']).toString())"
    check "cline relaunch x3"      "3.0.61" sh -c "$CLINE --version && $CLINE --version && $CLINE --version"
    if [ -n "${CLINE_DEEP_TEST:-}" ]; then
      echo "(CLINE_DEEP_TEST set — attempting initialization; requires credentials/config)"
      timeout 60 sh -c "$CLINE" 2>&1 | head -5
    else
      echo "(set CLINE_DEEP_TEST=1 to attempt agent initialization with credentials)"
    fi
  else
    noun "cline" "not installed at $CLINE (npm i -g cline to include it)"
  fi
else
  echo
  echo "=== TIER 2/3 — SKIPPED: glibc layer not installed (see PREFLIGHT) ==="
  noun "tier 2 glibc"       "layer missing — loader is the gcompat stub, real glibc absent"
  noun "pocketshell-doctor" "rides the layer (/usr/local/bin) — not installed"
  noun "cline 3.0.61"       "needs the real glibc loader — layer not installed"
fi

echo
echo "RESULT: $pass passed, $fail failed, $skip skipped"
if [ "$fail" -gt 0 ]; then
  echo "VERDICT: FAILURES PRESENT — paste the full output"
elif [ "$layer_ok" = 0 ]; then
  echo "VERDICT: LAYER NOT INSTALLED. Fix path:"
  echo "  1) confirm the app is v0.10.0-m6.0.0 (vc40) or newer — Android Settings > Apps > PocketShell"
  echo "  2) fully close the app, reopen, open ONE fresh session (installs the layer), re-run"
  echo "  3) or repair NOW without the app: put $LAYER_NAME beside this script (or set"
  echo "     POCKETSHELL_LAYER_URL=<mirror>/$LAYER_NAME) and run: POCKETSHELL_INSTALL_LAYER=1 sh $0"
  [ -f "$STATUS" ] && grep -q '^state=FAILED' "$STATUS" 2>/dev/null && {
    echo "  NOTE: the app-side installer recorded a FAILURE — include this line in your report:"
    echo "        $(cat "$STATUS")"
  }
elif [ "$skip" -gt 0 ]; then
  echo "VERDICT: ALL GREEN — $skip optional row(s) skipped (e.g. Cline not installed; optional deep tests)"
else
  echo "VERDICT: universal runtime compatibility — ALL GREEN"
fi
exit "$fail"
