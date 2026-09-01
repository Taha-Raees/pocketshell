#!/bin/bash
# M2.4 package-layer rehearsal on the sandbox host: the SAME proot build, the
# SAME argv/env shape the Android PackageManager will use, against the pinned
# Alpine 3.24.1 (x86_64 rehearsal variant). Proves the real apk flow:
#   apk --version -> update -> search -> add -> info -e -> command -v -> run
#   -> del -> info -e (gone)
# Sandbox-only: x86_64 glibc host, NOT device validation (device gate = §9).
set -uo pipefail
D=/home/z/tools/m23-dist/host
R=/home/z/tools/m23-rehearsal
GUEST_RESOLV="$R/rootfs/etc/resolv.conf"
TAG="[m24 $(date '+%H:%M:%S')]"
log() { printf '%s %s\n' "$TAG" "$*"; }

# --- guest DNS: the app writes exactly this into the rootfs (both fresh
# installs via the installer and pre-op ensure). Rehearsal mirrors it.
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$GUEST_RESOLV"

export LD_LIBRARY_PATH="$D"
export PROOT_TMP_DIR="$R/tmp"
export PROOT_LOADER="$D/libproot-loader.so"

# guest entry: identical env contract to RuntimeProcessLauncher.buildLaunchSpec
# (LD_LIBRARY_PATH, PROOT_LOADER, PROOT_TMP_DIR, HOME, PATH, TERM, LANG, TMPDIR)
# — see g() below; run_guest() above was scrapped during drafting.

g() { # g <guest argv...>
  env -i \
    LD_LIBRARY_PATH="$D" \
    PROOT_TMP_DIR="$R/tmp" \
    PROOT_LOADER="$D/libproot-loader.so" \
    HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    TERM=xterm-256color LANG=C.UTF-8 TMPDIR=/tmp \
    "$D/libproot.so" \
    --kill-on-exit --rootfs="$R/rootfs" --root-id --cwd=/root \
    --bind=/dev --bind=/proc --bind=/sys "$@"
}

fail=0
step() { # step <desc> <cmd...>
  local desc="$1"; shift
  log "== $desc"
  "$@"
  local rc=$?
  log "   exit=$rc"
  [ $rc -eq 0 ] || fail=1
  return 0
}

step "apk --version"        g /sbin/apk --version
step "apk update"           g /sbin/apk update
log "== apk search nano (raw, first 8 lines)"
g /sbin/apk search nano | head -8
log "   exit=${PIPESTATUS[0]}"
step "apk add nano"         g /sbin/apk add nano
step "apk info -e nano"     g /sbin/apk info -e nano
log "== command -v nano"
g /bin/sh -c 'command -v nano'; log "   exit=$?"
log "== nano --version (executable really runs)"
g /usr/bin/nano --version | head -2; log "   exit=${PIPESTATUS[0]}"
step "apk del nano"         g /sbin/apk del nano
log "== apk info -e nano AFTER del (expect NONZERO)"
g /sbin/apk info -e nano; rc=$?; log "   exit=$rc (want non-zero)"; [ $rc -ne 0 ] || fail=1

log "REHEARSAL $([ $fail -eq 0 ] && echo PASSED || echo FAILED)"
exit $fail
