#!/bin/bash
# M2.4 package-layer rehearsal on the sandbox host: the SAME proot build, the
# SAME argv/env shape the Android PackageManager will use, against the pinned
# Alpine 3.24.1 (x86_64 rehearsal variant). Proves the real apk flow:
#   apk --version -> update -> search -> add -> info -e -> command -v -> run
#   -> del -> info -e (gone)
# v0.4.1: adds the apk cache binds (etc/apk/cache + var/cache/apk over
# app-owned host dirs) exactly as PackageGateway.buildSpec now passes them,
# plus the guest workspace repair steps.
# Sandbox-only: x86_64 glibc host, NOT device validation (device gate = §9).
set -uo pipefail
D=/home/z/tools/m23-dist/host
R=/home/z/tools/m23-rehearsal
GUEST_RESOLV="$R/rootfs/etc/resolv.conf"
TAG="24 $(date '+%H:%M:%S')]"
log() { printf '%s %s\n' "$TAG" "$*"; }

# --- guest DNS: the app writes the DEVICE's resolvers when available, the
# public pair otherwise. Rehearsal mirrors the fallback (the sandbox has no
# ConnectivityManager); both bodies are just nameserver lines to musl.
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$GUEST_RESOLV"

# --- guest workspace repair (GuestEnvironment.ensureApkWorkspace equivalent)
mkdir -p "$R/rootfs/etc/apk/cache" "$R/rootfs/var/cache/apk" "$R/rootfs/tmp"
chmod 755 "$R/rootfs/etc/apk/cache" "$R/rootfs/var/cache/apk" 2>/dev/null
chmod 1777 "$R/rootfs/tmp" 2>/dev/null

# --- app-owned host cache dirs (PackageGateway.apkCacheDir equivalent)
mkdir -p "$R/apk-cache/etc" "$R/apk-cache/var"

export LD_LIBRARY_PATH="$D"
export PROOT_TMP_DIR="$R/tmp"
export PROOT_LOADER="$D/libproot-loader.so"

# guest entry: identical env contract to RuntimeProcessLauncher.buildLaunchSpec
# (LD_LIBRARY_PATH, PROOT_LOADER, PROOT_TMP_DIR, HOME, PATH, TERM, LANG, TMPDIR)
g() { # g <guest argv...>
  env -i \
    LD_LIBRARY_PATH="$D" \
    PROOT_TMP_DIR="$R/tmp" \
    PROOT_LOADER="$D/libproot-loader.so" \
    HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    TERM=xterm-256color LANG=C.UTF-8 TMPDIR=/tmp \
    "$D/libproot.so" \
    --kill-on-exit --rootfs="$R/rootfs" --root-id --cwd=/root \
    --bind=/dev --bind=/proc --bind=/sys \
    --bind="$R/apk-cache/etc:/etc/apk/cache" \
    --bind="$R/apk-cache/var:/var/cache/apk" "$@"
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
log "== cache landed in the BOUND host dir (proot bind proof)"
ls -la "$R/apk-cache/etc/" | head -5

log "REHEARSAL $([ $fail -eq 0 ] && echo PASSED || echo FAILED)"
exit $fail
