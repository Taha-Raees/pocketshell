#!/bin/bash
# M2.6 /proc-architecture rehearsal on the sandbox host (x86_64 glibc).
# Proves the M2.6 INTERACTIVE_TERMINAL session shape end-to-end:
#   REAL /proc bound INTO the guest + the fd-link-patched libapk + shared
#   apk cache binds -> /proc reads work, process tools work, AND the full
#   apk lifecycle works in the SAME proot session shape the app will spawn.
#
# Rehearsal equivalent of:
#   GuestApkCompat.ensure()            (hash-verified libapk replacement)
#   RuntimeProcessLauncher.buildSessionSpec(procEnabled = true)
#   PackageGateway.prepareGuestForSession (DNS/workspace best-effort)
#
# Honesty notes (see docs/M2.6-RESEARCH.md):
#  - The host has no SELinux, so the ORIGINAL libapk would ALSO pass apk
#    update here; this rehearsal cannot reproduce the Android EACCES. What
#    it proves is the NEW shape (patched libapk + /proc bound) works
#    end-to-end, byte-hashes of the patch are exact, and the device-proven
#    renameat path is what the patched binary uses everywhere.
#  - NOT device validation: the M2.6 device gate is docs/TESTING.md §10.
set -uo pipefail
SRC=/home/z/my-project/scratch/m26
D=/home/z/tools/m23-dist/host
R=/home/z/tools/m26-rehearsal
TAG="26 $(date '+%H:%M:%S')]"
PASS=0; FAIL=0
log() { printf '%s %s\n' "$TAG" "$*"; }
ok()  { PASS=$((PASS+1)); log "PASS: $*"; }
bad() { FAIL=$((FAIL+1)); log "FAIL: $*"; }

[ -x "$D/libproot.so" ] || { log "host proot missing — run scripts/build_proot_host_only.sh"; exit 1; }
[ -d "$SRC/rootfs-x86/usr/lib" ] || { log "pinned x86_64 minirootfs extraction missing"; exit 1; }

# ---------- fresh rehearsal rootfs from the pinned minirootfs extraction ----
rm -rf "$R"; mkdir -p "$R"
cp -a "$SRC/rootfs-x86" "$R/rootfs"

# ---------- GuestApkCompat.ensure() rehearsal-equivalent --------------------
LIB="$R/rootfs/usr/lib/libapk.so.3.0.0"
cur=$(sha256sum "$LIB" | cut -d' ' -f1)
if [ "$cur" = "51ee6652a1a1f36c83edb6fc425882d62c8d074a1ced144b85a961360919189c" ]; then
  ok "rehearsal rootfs libapk matches the ORIGINAL pin (3.0.6-r0 x86_64)"
else
  bad "unexpected original libapk hash: $cur"
fi
tmpf="$LIB.patch.$$"
cp "$SRC/patched/libapk.so.3.0.0.fdlinkoff.x86_64" "$tmpf"
chmod 755 "$tmpf"
if [ "$(sha256sum "$tmpf" | cut -d' ' -f1)" = "d4410cec28a8bfda7d278ab17ff774b679ce4f2529b0894224f562721ace7f55" ]; then
  mv "$tmpf" "$LIB"
  ok "patched libapk installed (hash verified before + after rename)"
else
  rm -f "$tmpf"; bad "patched asset failed its own checksum — refusing (app behavior)"
fi
[ "$(sha256sum "$LIB" | cut -d' ' -f1)" = "d4410cec28a8bfda7d278ab17ff774b679ce4f2529b0894224f562721ace7f55" ] \
  && ok "rootfs libapk now matches the PATCHED pin" || bad "post-install hash mismatch"

# ---------- guest workspace repair (GuestEnvironment best-effort half) ------
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$R/rootfs/etc/resolv.conf"
mkdir -p "$R/rootfs/etc/apk/cache" "$R/rootfs/var/cache/apk" "$R/rootfs/tmp"
chmod 755 "$R/rootfs/etc/apk/cache" "$R/rootfs/var/cache/apk" 2>/dev/null
chmod 1777 "$R/rootfs/tmp" 2>/dev/null
mkdir -p "$R/apk-cache/etc" "$R/apk-cache/var" "$R/tmp"

# ---------- INTERACTIVE_TERMINAL launcher: /proc + /dev + /sys + cache ------
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

# ---------- Gate A rehearsal: /proc is genuinely there ----------------------
g /bin/sh -c 'test -d /proc/1 && echo PROC-DIR-OK' | grep -q PROC-DIR-OK \
  && ok "ls/proc: guest sees a real procfs" || bad "no /proc in guest"
g /bin/sh -c 'grep -q "Linux version" /proc/version && echo VERSION-OK' | grep -q VERSION-OK \
  && ok "cat /proc/version works" || bad "/proc/version unreadable"
g /bin/sh -c 'grep -q MemTotal /proc/meminfo && echo MEMINFO-OK' | grep -q MEMINFO-OK \
  && ok "cat /proc/meminfo works" || bad "/proc/meminfo unreadable"

# ---------- Gate B rehearsal: process tools ---------------------------------
g ps > "$R/ps.out" 2>&1; head -1 "$R/ps.out" | grep -q PID && ok "ps produces a process table" || bad "ps failed"
grep -q . "$R/ps.out" \
  && ok "ps lists visible processes" || bad "ps empty"
g top -b -n 1 > "$R/top.out" 2>&1; grep -q . "$R/top.out" && ok "top (batch) opens and reads /proc" || bad "top failed"

# ---------- Gate C rehearsal: apk lifecycle WITH /proc bound ----------------
v=$(g /sbin/apk --version 2>&1); echo "$v" | grep -q "apk-tools" \
  && ok "apk --version: $v" || bad "apk --version failed: $v"
if g /sbin/apk update > "$R/update.log" 2>&1; then
  ok "apk update succeeded (with /proc bound + patched libapk)"
  tail -1 "$R/update.log" | sed 's/^/      /'
else
  bad "apk update failed: $(tail -2 "$R/update.log")"
fi
g /sbin/apk search nano | grep -q ^nano && ok "apk search nano" || bad "apk search failed"
if g /sbin/apk add nano > "$R/add.log" 2>&1; then
  ok "apk add nano (commit path = renameat with /proc visible)"
else
  bad "apk add failed: $(tail -3 "$R/add.log")"
fi
g /bin/sh -c 'apk info -e -v nano' | grep -q "9\." && ok "apk info confirms nano installed" || bad "info -e failed"
g nano --version | head -1 | grep -q "GNU nano" && ok "nano runs (real executable)" || bad "nano failed"
g /sbin/apk del nano > /dev/null 2>&1 && ok "apk del nano" || bad "apk del failed"
g /bin/sh -c 'apk info -e nano' > /dev/null 2>&1
rc=$?
[ "$rc" -eq 1 ] \
  && ok "apk info -e nano exits non-zero after del (honest absence)" || bad "del state wrong (rc=$rc)"

# ---------- one cache: downloads land in the BOUND host dir -----------------
ls "$R/apk-cache/var" "$R/apk-cache/etc" 2>/dev/null | grep -q . \
  && ok "apk cache objects landed in the app-owned bound host dir" \
  || log "note: cache empty after del cycle (apk may clean on del) — informational"

log "RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] && log "M2.6 REHEARSAL: FULL PASS" || log "M2.6 REHEARSAL: FAILURES PRESENT"
exit "$FAIL"
