#!/bin/bash
# M2.3 gate rehearsal on the sandbox host (x86_64 glibc build of the SAME proot
# source + SAME argv/env the Android launcher will use), against the SAME
# pinned Alpine version (x86_64 variant).
#
# v0.3.2 device lesson: the two exports below are PART OF THE CONTRACT — the
# launcher spec environment must carry LD_LIBRARY_PATH (bionic does not search
# nativeLibraryDir for proot's DT_NEEDED libtalloc.so) and PROOT_TMP_DIR, and
# argv[0] must be the executable path (bash invocations below do this
# naturally). Keep this script's env list and RuntimeProcessLauncher's spec
# environment in lockstep; unit tests pin both sides.
set -uo pipefail
R=/home/z/tools/m23-rehearsal
D=/home/z/tools/m23-dist/host
TAG="[rehearsal $(date '+%H:%M:%S')]"

log() { printf '%s %s\n' "$TAG" "$*"; }

rm -rf "$R"; mkdir -p "$R/rootfs" "$R/tmp"
log "downloading alpine-minirootfs-3.24.1-x86_64..."
curl -fsSL --retry 3 -o "$R/alpine.tar.gz" \
  "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/x86_64/alpine-minirootfs-3.24.1-x86_64.tar.gz" || { log "DOWNLOAD FAILED"; exit 1; }
log "sha256: $(sha256sum "$R/alpine.tar.gz" | cut -d' ' -f1)  bytes: $(stat -c %s "$R/alpine.tar.gz")"
tar -xzf "$R/alpine.tar.gz" -C "$R/rootfs" 2>/dev/null
log "rootfs extracted: $(find "$R/rootfs" -type f | wc -l) files, $(find "$R/rootfs" -type l | wc -l) symlinks"

export LD_LIBRARY_PATH="$D"
export PROOT_TMP_DIR="$R/tmp"
GATE='echo "--- uname:"; uname -a; echo "--- id:"; id; echo "--- echo:"; echo hello; echo "--- release:"; cat /etc/alpine-release; echo "--- hostname:"; cat /etc/hostname; echo "--- busybox:"; /bin/busybox | head -1'

log "=== mode A: PROOT_LOADER env (the device design path) ==="
printf '%s\n' "$GATE" | PROOT_LOADER="$D/libproot-loader.so" "$D/libproot.so" \
  --kill-on-exit "--rootfs=$R/rootfs" --root-id "--cwd=/root" \
  "--bind=/dev" "--bind=/proc" "--bind=/sys" /bin/sh -l
log "mode A exit=$?"

log "=== mode B: embedded loader fallback (no PROOT_LOADER) ==="
printf '%s\n' "$GATE" | "$D/libproot.so" \
  --kill-on-exit "--rootfs=$R/rootfs" --root-id "--cwd=/root" \
  "--bind=/dev" "--bind=/proc" "--bind=/sys" /bin/sh -l
log "mode B exit=$?"

log "=== mode C: --version sanity ==="
"$D/libproot.so" --version 2>&1 | head -2
log "rehearsal done"
