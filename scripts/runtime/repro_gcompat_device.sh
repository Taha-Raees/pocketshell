#!/bin/bash
# repro_gcompat_device.sh — simulate the REAL DEVICE condition seen at the
# m6.0.0 device gate (2026-09-06): a rootfs where gcompat was apk-installed
# in an earlier era (Antigravity debugging) and PERSISTED across app updates.
# On that rootfs, without the glibc layer, /lib/ld-linux-aarch64.so.1 is the
# gcompat stub — exactly what the device suite output shows.
#
# Steps:
#   1. clone the rig rootfs (minus the 151 MB Cline tree, re-bound read-only)
#   2. apk add gcompat  → contaminate it like the device
#   3. demonstrate the device failure state (loader stub, t_cpp arc4random)
#   4. run the ESCAPE HATCH path: tar -xzf layer.tar.gz -C / + marker write
#   5. re-run the tier-2 matrix + musl regression on the repaired rootfs
# Host-safe: everything happens in a scratch clone, never the rig rootfs.
set -u
RIG=/home/z/tools/rig
SRC=$RIG/rootfs
SCRATCH=$RIG/rootfs-gcompat-sim
HOSTLD=/home/z/tools/m23-dist/host
PROOT=$HOSTLD/libproot.so
QEMU=$RIG/qu/usr/bin/qemu-aarch64
LAYER=/home/z/my-project/app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz
export LD_LIBRARY_PATH="$HOSTLD${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

pass=0; fail=0; declare -a ROWS=()
g() { "$PROOT" -q "$QEMU" -r "$SCRATCH" --cwd=/ \
      -b /dev -b /proc -b /sys \
      -b "$SRC/usr/local/lib/node_modules/cline:/usr/local/lib/node_modules/cline" \
      -b "$LAYER:/mnt/layer.tar.gz" "$@" 2>&1; }
check() { # check <phase> <name> <want> <cmd...>
  local phase="$1" name="$2" want="$3"; shift 3
  local out; out=$(g "$@")
  if printf '%s' "$out" | grep -q "$want"; then
    ROWS+=("PASS|$phase: $name|$(printf '%s' "$out" | grep "$want" | head -1 | cut -c1-70)")
    pass=$((pass+1))
  else
    ROWS+=("FAIL|$phase: $name|$(printf '%s' "$out" | head -2 | tr '\n' ' ' | cut -c1-90)")
    fail=$((fail+1))
  fi
}

echo "== 1. clone rig rootfs -> $SCRATCH (excluding Cline tree; re-bound)"
rm -rf "$SCRATCH"; mkdir -p "$SCRATCH"
tar -C "$SRC" -cf - --exclude='./usr/local/lib/node_modules/cline' . \
  | tar -C "$SCRATCH" -xf -
echo "   cloned: $(du -sh "$SCRATCH" | cut -f1)"

echo
echo "== 2. contaminate: apk add gcompat (the device's historical state)"
g /bin/sh -c 'apk add gcompat 2>&1 | tail -2'
g /bin/sh -c 'apk info -e gcompat && echo GCOMPAT-INSTALLED'
check "contam" "gcompat installed" "GCOMPAT-INSTALLED" /bin/sh -c 'apk info -e gcompat >/dev/null && echo GCOMPAT-INSTALLED'

echo
echo "== 3. device failure state reproduction (BEFORE layer)"
check "pre" "loader is gcompat stub"  "gcompat ELF interpreter stub" /lib/ld-linux-aarch64.so.1 --version
check "pre" "glibc hello (shim ok)"   "hello-glibc"  /opt/pocketshell-tests/t_hello
check "pre" "glibc C++ (shim gap)"    "arc4random: symbol not found" /opt/pocketshell-tests/t_cpp

echo
echo "== 4. ESCAPE HATCH: tar -xzf layer -C / + marker write"
g /bin/sh -c 'tar -xzf /mnt/layer.tar.gz -C / && mkdir -p /etc/pocketshell && echo "PocketShell glibc runtime layer 2.41-12.deb13u3 (glibc 2.41)" > /etc/pocketshell/glibc-runtime && echo HATCH-OK'
check "hatch" "hatch extraction" "HATCH-OK" /bin/sh -c 'test -f /etc/pocketshell/glibc-runtime && echo HATCH-OK'

echo
echo "== 5. post-hatch: glibc tier-2 matrix"
check "post" "loader real 2.41"       "stable release version 2.41" /lib/ld-linux-aarch64.so.1 --version
check "post" "glibc hello"            "hello-glibc"    /opt/pocketshell-tests/t_hello
check "post" "glibc pthread"          "pthread-ok"     /opt/pocketshell-tests/t_pthread
check "post" "glibc dlopen"           "dlopen-libm-ok" /opt/pocketshell-tests/t_dlopen
check "post" "glibc libm"             "libm-ok"        /opt/pocketshell-tests/t_libm
check "post" "glibc C++ exc"          "cpp-ok"         /opt/pocketshell-tests/t_cpp
check "post" "glibc fork+exec"        "fork-exec-ok"   /opt/pocketshell-tests/t_fork_exec
check "post" "glibc NSS passwd"       "getpwnam-ok"    /opt/pocketshell-tests/t_getpwnam
check "post" "glibc NSS dns"          "getaddrinfo-ok" /opt/pocketshell-tests/t_getaddrinfo
check "post" "glibc Cline-shape"      "pthread-ok"     /opt/pocketshell-tests/t_cline_shape
check "post" "cline 3.0.61 direct"    "3.0.61" /usr/local/lib/node_modules/cline/node_modules/@cline/cli-linux-arm64/bin/cline --version

echo
echo "== 6. post-hatch: musl regression (nothing may have changed)"
check "post" "busybox sh"     "musl-ok"  /bin/sh -c 'echo musl-ok'
check "post" "bash"           "version 5" /bin/bash --version
check "post" "node"           "v24."     /usr/bin/node --version
check "post" "apk tools"      "apk-tools" /sbin/apk --version
check "post" "musl pkg intact" "musl"    /sbin/apk info -e musl
check "post" "musl loader untouched" "OK" /bin/sh -c 'test -f /lib/ld-musl-aarch64.so.1 && echo OK'

echo
printf "%-6s %-36s %s\n" "STATUS" "TEST" "EVIDENCE"
printf "%-6s %-36s %s\n" "------" "-----------------------------------" "----"
for r in "${ROWS[@]}"; do
  IFS='|' read -r st name ev <<< "$r"
  printf "%-6s %-36s %s\n" "$st" "$name" "$ev"
done
echo
echo "RESULT: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
