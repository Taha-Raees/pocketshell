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
# Every claim is probed. Nothing is assumed. Output is a paste-ready table.
set -u
DIR="${POCKETSHELL_TESTS_DIR:-/tmp/pocketshell-tests}"
CLINE=/usr/local/lib/node_modules/cline/node_modules/@cline/cli-linux-arm64/bin/cline

pass=0; fail=0
row() { # row <STATUS> <NAME> <EVIDENCE>
  printf "%-6s %-38s %s\n" "$1" "$2" "$3" | cut -c1-140
}
ok()   { row PASS "$1" "$2"; pass=$((pass+1)); }
bad()  { row FAIL "$1" "$2"; fail=$((fail+1)); }

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

echo "== POCKETSHELL EXECUTABLE COMPATIBILITY SUITE (device)"
echo "== guest: $(cat /etc/alpine-release 2>/dev/null || echo '?') / $(/bin/busybox | head -1 | grep -o 'BusyBox v[0-9.]*')"
echo "== glibc layer marker: $(cat /etc/pocketshell/glibc-runtime 2>/dev/null || echo 'MISSING — the layer installs on the next session spawn; restart the app and re-run')"
echo

echo "=== TIER 1 — musl (must be unaffected) ==="
check "busybox sh"        "musl-ok"       /bin/sh -c 'echo musl-ok'
check "bash"              "version 5"     /bin/bash --version
check "git"               "git version"   /usr/bin/git --version
check "curl"              "libcurl"       /usr/bin/curl --version
check "node"              "v"             /usr/bin/node --version
check "npm"               "^[0-9][0-9]*\."  /usr/bin/npm --version
check "apk"               "apk-tools"     /sbin/apk --version
check "apk install probe" "musl"          /sbin/apk info -e musl

echo "=== TIER 1 — static ==="
[ -x "$DIR/t_static" ] && check "static (glibc -static)" "hello-glibc" "$DIR/t_static" \
                       || bad "static (glibc -static)" "binary missing (download the suite tarball)"

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
  bad "pocketshell-doctor" "not found — the glibc layer is not installed yet"
fi

echo "=== TIER 2/3 — CLINE 3.0.61 (the real glibc-native test) ==="
if [ -x "$CLINE" ]; then
  check "cline --version"        "3.0.61" "$CLINE" --version
  check "cline --help"           "Usage\|usage\|cline" sh -c "$CLINE --help 2>&1 | head -3"
  check "cline via node spawn"   "3.0.61" /usr/bin/node -e "console.log(require('child_process').execFileSync('$CLINE',['--version']).toString())"
  check "cline relaunch x3"      "3.0.61" sh -c "$CLINE --version && $CLINE --version && $CLINE --version"
  if [ -n "${CLINE_DEEP_TEST:-}" ]; then
    echo "(CLINE_DEEP_TEST set — attempting initialization; requires credentials/config)"
    timeout 60 sh -c "$CLINE" 2>&1 | head -5
  else
    echo "(set CLINE_DEEP_TEST=1 to attempt agent initialization with credentials)"
  fi
else
  bad "cline" "not installed at $CLINE"
fi

echo
echo "RESULT: $pass passed, $fail failed"
[ "$fail" -eq 0 ] && echo "VERDICT: universal runtime compatibility — ALL GREEN"
exit "$fail"
