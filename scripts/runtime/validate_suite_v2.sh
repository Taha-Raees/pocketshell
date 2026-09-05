#!/bin/bash
# validate_suite_v2.sh — run the NEW run_on_device.sh inside the qemu rig
# under all three device phases:
#   A. fresh gcompat-contaminated rootfs, NO layer  -> musl green, tier2 SKIP,
#      verdict LAYER NOT INSTALLED, exit 0
#   B. same rootfs + POCKETSHELL_INSTALL_LAYER=1 (layer tarball beside tests)
#      -> hatch installs, full matrix green, exit 0
#   C. already-installed rootfs (the m6.0.0 rig)    -> full green, no hatch
set -u
RIG=/home/z/tools/rig
SRC=$RIG/rootfs
SIM=$RIG/rootfs-gcompat-sim2
HOSTLD=/home/z/tools/m23-dist/host
PROOT=$HOSTLD/libproot.so
QEMU=$RIG/qu/usr/bin/qemu-aarch64
LAYER=/home/z/my-project/app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz
SUITE=/home/z/my-project/runtime-tests/run_on_device.sh
export LD_LIBRARY_PATH="$HOSTLD${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

g() { # g <rootfs> <bind-spec...> -- <cmd...>   (bind specs WITHOUT the -b flag)
  local root="$1"; shift
  local binds=()
  while [ "$1" != "--" ]; do binds+=("-b" "$1"); shift; done
  shift
  "$PROOT" -q "$QEMU" -r "$root" --cwd=/ -b /dev -b /proc -b /sys "${binds[@]}" "$@" 2>&1
}

clone() { # clone <dest> <with-cline:yes|no>
  rm -rf "$1"; mkdir -p "$1"
  if [ "$2" = yes ]; then
    tar -C "$SRC" -cf - . | tar -C "$1" -xf -
  else
    tar -C "$SRC" -cf - --exclude='./usr/local/lib/node_modules/cline' . | tar -C "$1" -xf -
  fi
}

stage_tests() { # stage_tests <rootfs>
  g "$1" -- /bin/sh -c '
    mkdir -p /tmp/pocketshell-tests &&
    cp /opt/pocketshell-tests/* /tmp/pocketshell-tests/ 2>/dev/null
    ls /tmp/pocketshell-tests | head -3'
}

echo "########## PHASE A — no layer, no hatch ##########"
clone "$SIM" no
stage_tests "$SIM"
g "$SIM" -b "$SUITE:/tmp/pocketshell-tests/run_on_device.sh" -- \
  /bin/sh -c 'POCKETSHELL_TESTS_DIR=/tmp/pocketshell-tests sh /tmp/pocketshell-tests/run_on_device.sh; echo "EXIT=$?"'
echo

echo "########## PHASE B — no layer, WITH hatch ##########"
# gcompat-contaminate the fresh clone like the device, stage layer beside tests
g "$SIM" -- /bin/sh -c 'apk add gcompat >/dev/null 2>&1 && apk info -e gcompat && echo CONTAMINATED'
g "$SIM" -- /bin/sh -c 'rm -f /etc/pocketshell/glibc-runtime /etc/pocketshell/glibc-runtime.status; echo CLEANED'
cp "$LAYER" /tmp/stage-layer.tar.gz
g "$SIM" /tmp/stage-layer.tar.gz:/mnt/layer.tar.gz -- /bin/sh -c 'cp /mnt/layer.tar.gz /tmp/pocketshell-tests/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz && echo STAGED'
g "$SIM" "$SUITE:/tmp/pocketshell-tests/run_on_device.sh" -- \
  /bin/sh -c 'POCKETSHELL_TESTS_DIR=/tmp/pocketshell-tests POCKETSHELL_INSTALL_LAYER=1 sh /tmp/pocketshell-tests/run_on_device.sh; echo "EXIT=$?"'
echo

echo "########## PHASE C — layer already installed (rig rootfs) ##########"
g "$SRC" "$SUITE:/tmp/pocketshell-tests/run_on_device.sh" -- \
  /bin/sh -c 'mkdir -p /tmp/pocketshell-tests && cp /opt/pocketshell-tests/* /tmp/pocketshell-tests/ 2>/dev/null; POCKETSHELL_TESTS_DIR=/tmp/pocketshell-tests sh /tmp/pocketshell-tests/run_on_device.sh; echo "EXIT=$?"'
