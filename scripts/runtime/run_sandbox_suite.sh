#!/bin/bash
# run_sandbox_suite.sh — the executable compatibility matrix, run inside the
# sandbox emulation rig (proot + qemu-aarch64 + Alpine 3.24.1 + glibc layer).
# Same test logic as runtime-tests/run_on_device.sh (device), host-safe.
set -u
RIG=/home/z/tools/rig
HOSTLD=/home/z/tools/m23-dist/host
PROOT=$HOSTLD/libproot.so
QEMU=$RIG/qu/usr/bin/qemu-aarch64
export LD_LIBRARY_PATH="$HOSTLD${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

pass=0; fail=0; declare -a ROWS=()

g() { # guest exec: g <cmd...>
  "$PROOT" -q "$QEMU" -r "$RIG/rootfs" --cwd=/ -b /dev -b /proc -b /sys "$@" 2>&1
}

check() { # check <name> <expected-marker> <cmd...>
  local name="$1" want="$2"; shift 2
  local out
  out=$(g "$@")
  if printf '%s' "$out" | grep -q "$want"; then
    ROWS+=("PASS|$name|$(printf '%s' "$out" | grep "$want" | head -1 | cut -c1-60)")
    pass=$((pass+1))
  else
    ROWS+=("FAIL|$name|$(printf '%s' "$out" | head -2 | tr '\n' ' ' | cut -c1-90)")
    fail=$((fail+1))
  fi
}

echo "=== TIER 1: musl (Alpine apk binaries) ==="
check "busybox sh"      "shell-test-ok" /bin/sh -c 'echo shell-test-ok'
check "bash"            "version 5"     /bin/bash --version
check "coreutils ls"    "coreutils"     /bin/ls --version
check "git"             "git version"   /usr/bin/git --version
check "curl"            "libcurl"       /usr/bin/curl --version
check "node"            "v24."          /usr/bin/node --version
check "npm"             "11."           /usr/bin/npm --version
check "apk"             "apk-tools"     /sbin/apk --version

echo "=== TIER 1: static ==="
check "static (glibc -static)" "hello-glibc" /opt/pocketshell-tests/t_static

echo "=== TIER 2/3: glibc (real Debian glibc 2.41 loader) ==="
check "glibc hello"        "hello-glibc"  /opt/pocketshell-tests/t_hello
check "glibc pthread"      "pthread-ok"   /opt/pocketshell-tests/t_pthread
check "glibc dlopen"       "dlopen-libm-ok" /opt/pocketshell-tests/t_dlopen
check "glibc libm"         "libm-ok"      /opt/pocketshell-tests/t_libm
check "glibc C++ exc"      "cpp-ok"       /opt/pocketshell-tests/t_cpp
check "glibc fork+exec"    "fork-exec-ok" /opt/pocketshell-tests/t_fork_exec
check "glibc NSS passwd"   "getpwnam-ok"  /opt/pocketshell-tests/t_getpwnam
check "glibc NSS dns"      "getaddrinfo-ok" /opt/pocketshell-tests/t_getaddrinfo
check "glibc Cline-shape DT_NEEDED" "pthread-ok" /opt/pocketshell-tests/t_cline_shape

echo "=== CLINE 3.0.61 (real npm binary, 151,062,848 B) ==="
CLINE=/usr/local/lib/node_modules/cline/node_modules/@cline/cli-linux-arm64/bin/cline
check "cline --version (direct)" "3.0.61" $CLINE --version
check "cline via node spawn (wrapper chain)" "3.0.61" /usr/bin/node -e "console.log(require('child_process').execFileSync('$CLINE',['--version']).toString())"

echo
printf "%-6s %-34s %s\n" "STATUS" "TEST" "EVIDENCE"
printf "%-6s %-34s %s\n" "------" "----------------------------------" "----"
for r in "${ROWS[@]}"; do
  IFS='|' read -r st name ev <<< "$r"
  printf "%-6s %-34s %s\n" "$st" "$name" "$ev"
done
echo
echo "RESULT: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
