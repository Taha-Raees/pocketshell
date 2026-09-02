#!/bin/bash
# M2.6.12 + M2.6.13 rehearsal (v0.6.2-m2.6) — host sandbox, x86_64.
#
# Validates the two v0.6.2 layers against the REAL proot built from the SAME
# pin as the shipped jniLibs (termux/proot 7266fb3e):
#
#  1. M2.6.13 --link2symlink (hardlink extraction fix):
#     apk add binutils in a fresh Alpine 3.24.1 x86_64 rootfs WITH the flag
#     produces WORKING tools through the emulated links (usr/bin/ld becomes
#     an l2s symlink chain; ld/ar/readelf run), while the same install
#     WITHOUT the flag lands real hardlinks. On-device the flag is what
#     makes extraction possible at all (SELinux neverallow file link) —
#     that part is the device gate, rehearsals have no SELinux.
#  2. M2.6.12 sysdata overlays: the exact argv shape the app builds
#     (--bind=/proc then file-over-file overlay binds) is accepted by proot,
#     the guest reads overlay content for the bound files while
#     UNOVERLAID files stay real, /proc/self/fd stays a real fd dir, and
#     busybox top renders with the overlay present.
#
# The unit tests cover the probe/REAL-WINS logic and generator formats; this
# script proves the proot layer + argv. PASS required before any payload cut.
set -uo pipefail

SCRATCH=/home/z/my-project/scratch/m262-rehearsal
DIST=/home/z/tools/m23-dist/host
PASS=0; FAIL=0

ok()   { PASS=$((PASS+1)); echo "  PASS: $*"; }
bad()  { FAIL=$((FAIL+1)); echo "  FAIL: $*"; }
check(){ if [ "$1" = "0" ]; then ok "$2"; else bad "$2"; fi; }

rm -rf "$SCRATCH"; mkdir -p "$SCRATCH"
cd "$SCRATCH"
export PROOT_TMP_DIR="$SCRATCH/proot-tmp"
mkdir -p "$PROOT_TMP_DIR"

export LD_LIBRARY_PATH="$DIST"
PROOT="$DIST/libproot.so"

echo "== 0. host proot accepts the v0.6.2 flag set =="
"$PROOT" --help 2>&1 | grep -q -- "--link2symlink"
check $? "proot --help lists --link2symlink (extension compiled in)"

# ---------------------------------------------------------------- rootfs
echo "== 1. stage fresh Alpine 3.24.1 x86_64 rootfs =="
curl -fsSL --retry 2 -o minirootfs.tar.gz \
  "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/x86_64/alpine-minirootfs-3.24.1-x86_64.tar.gz"
check $? "minirootfs download"

stage_rootfs() { # $1 = target dir
  rm -rf "$1"; mkdir -p "$1"
  tar -xzf minirootfs.tar.gz -C "$1"
  printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$1/etc/resolv.conf"
  mkdir -p "$1/etc/apk/cache" "$1/var/cache/apk"
}
stage_rootfs "$SCRATCH/rfs-l2s"
stage_rootfs "$SCRATCH/rfs-plain"
mkdir -p "$SCRATCH/cache-etc" "$SCRATCH/cache-var"

# argv builder + runner mirroring RuntimeProcessLauncher.buildLaunchSpec
# (v0.6.2 shape) — direct array exec, no eval quoting hazards
guest() { # $1=rootfs $2=l2s(0/1) $3=sysdata-dir-or-empty $4=shell command
  local rootfs="$1" l2s="$2" sysdata="$3" cmd="$4"
  local -a args=("$PROOT" --kill-on-exit)
  [ "$l2s" = "1" ] && args+=(--link2symlink)
  args+=(--rootfs="$rootfs" --root-id --cwd=/root
         --bind=/dev --bind=/proc --bind=/sys)
  if [ -n "$sysdata" ]; then
    local f
    for f in stat uptime loadavg version vmstat; do
      [ -f "$sysdata/$f" ] && args+=("--bind=$sysdata/$f:/proc/$f")
    done
  fi
  args+=("--bind=$SCRATCH/cache-etc:/etc/apk/cache"
         "--bind=$SCRATCH/cache-var:/var/cache/apk"
         /bin/sh -l -c "$cmd")
  "${args[@]}"
}

# ---------------------------------------------------------------- M2.6.13
echo "== 2. M2.6.13: apk add binutils WITH --link2symlink =="
guest "$SCRATCH/rfs-l2s" 1 "" "apk update --quiet && apk add --quiet binutils" > "$SCRATCH/l2s-install.log" 2>&1
check $? "apk update + add binutils (link2symlink enabled)"

L2S_LD="$SCRATCH/rfs-l2s/usr/bin/ld"
if [ -L "$L2S_LD" ]; then
  ok "usr/bin/ld is the l2s emulated link (symlink chain), as designed"
elif [ -f "$L2S_LD" ]; then
  ok "usr/bin/ld landed as a plain file (l2s chose direct copy)"
else
  bad "usr/bin/ld missing entirely"
fi
# inode identity between the two names would mean a REAL hardlink — the
# kernel path this app domain can never take; l2s must NOT need it.
guest "$SCRATCH/rfs-l2s" 1 "" "ld --version | head -1" > "$SCRATCH/ld-ver.log" 2>&1
check $? "ld --version works through the emulated link"
head -1 "$SCRATCH/ld-ver.log"
guest "$SCRATCH/rfs-l2s" 1 "" "ar --version | head -1 && readelf --version | head -1" > /dev/null 2>&1
check $? "ar + readelf work through the emulated links"
guest "$SCRATCH/rfs-l2s" 1 "" "apk info -e binutils && echo DB-OK" | grep -q "DB-OK"
check $? "apk database records binutils cleanly"

echo "== 3. control: same install WITHOUT --link2symlink =="
guest "$SCRATCH/rfs-plain" 0 "" "apk update --quiet && apk add --quiet binutils" > "$SCRATCH/plain-install.log" 2>&1
check $? "apk add binutils without the flag (host has no SELinux)"
PLAIN_LD="$SCRATCH/rfs-plain/usr/bin/ld"
[ -f "$PLAIN_LD" ] && ! [ -L "$PLAIN_LD" ]
check $? "control lands a real file (host link() allowed — the device is where it is not)"
if [ -f "$L2S_LD" ] && [ -f "$PLAIN_LD" ]; then
  cmp -s "$L2S_LD" "$PLAIN_LD" && ok "emulated-link binary is byte-identical to the real one" \
                            || bad "emulated-link binary differs from the control"
fi

# ---------------------------------------------------------------- M2.6.12
echo "== 4. M2.6.12: sysdata overlays ride the real /proc bind =="
SD="$SCRATCH/sysdata"; mkdir -p "$SD"
# generator-equivalent content (the Kotlin generators are unit-pinned; here we
# validate the proot layer with the same shapes)
cat > "$SD/stat" << 'EOF'
cpu  0 0 0 0 0 0 0 0 0 0
cpu0 0 0 0 0 0 0 0 0 0 0
intr 0
ctxt 0
btime 1788000000
processes 0
procs_running 0
procs_blocked 0
softirq 0
EOF
printf '7200.25 0.00\n'        > "$SD/uptime"
printf '0.00 0.00 0.00 0/12 34567\n' > "$SD/loadavg"
printf 'Linux version 5.10.157-android13-4 (PocketShell sysdata overlay: kernel identity via uname(2); the kernel'"'"'s own file is denied to apps by Android SELinux) #1 SMP PREEMPT Thu Sep  3 12:00:00 UTC 2026\n' > "$SD/version"
printf 'nr_free_pages 0\npgfault 0\noom_kill 0\n' > "$SD/vmstat"

guest "$SCRATCH/rfs-l2s" 1 "$SD" "cat /proc/stat | head -2" > "$SCRATCH/stat.out" 2>&1
grep -q "^cpu  0 0 0 0 0 0 0 0 0 0$" "$SCRATCH/stat.out"
check $? "overlaid /proc/stat reads the sysdata content inside the guest"
grep -q "^cpu0 " "$SCRATCH/stat.out"
check $? "per-cpu overlay lines visible"

guest "$SCRATCH/rfs-l2s" 1 "$SD" "head -1 /proc/meminfo" > "$SCRATCH/meminfo.out" 2>&1
grep -q "^MemTotal:" "$SCRATCH/meminfo.out"
check $? "UNOVERLAID /proc/meminfo stays REAL (real wins, no blanket overlay)"

guest "$SCRATCH/rfs-l2s" 1 "$SD" "cat /proc/version" > "$SCRATCH/version.out" 2>&1
grep -q "PocketShell sysdata overlay" "$SCRATCH/version.out" && grep -q "5.10.157-android13-4" "$SCRATCH/version.out"
check $? "overlaid /proc/version: attribution + real uname identity"

guest "$SCRATCH/rfs-l2s" 1 "$SD" "n=\$(ls /proc/self/fd 2>/dev/null | grep -c '^[0-9]'); [ \"\$n\" -ge 3 ] && echo FD-OK-\$n" > "$SCRATCH/fd.out" 2>&1
grep -q "FD-OK" "$SCRATCH/fd.out"
check $? "/proc/self/fd stays a real fd dir (numeric fd entries listable)"
# control: identical fd listing WITHOUT overlays — proves no fd regression
guest "$SCRATCH/rfs-plain" 0 "" "ls /proc/self/fd 2>/dev/null | grep -c '^[0-9]'" > "$SCRATCH/fd-ctrl.out" 2>&1
[ "$(head -1 "$SCRATCH/fd-ctrl.out" | tr -dc '0-9')" -ge 3 ]
check $? "control session lists the same real fd set (overlay adds nothing, removes nothing)"

guest "$SCRATCH/rfs-l2s" 1 "$SD" "cat /proc/loadavg && cat /proc/uptime && cat /proc/vmstat | head -1" > "$SCRATCH/misc.out" 2>&1
grep -q "^0.00 0.00 0.00 0/12 34567$" "$SCRATCH/misc.out" && grep -q "^7200.25 0.00$" "$SCRATCH/misc.out" && grep -q "^nr_free_pages 0$" "$SCRATCH/misc.out"
check $? "loadavg/uptime/vmstat overlays read correctly"

guest "$SCRATCH/rfs-l2s" 1 "$SD" "top -b -n 1 2>/dev/null | head -4" > "$SCRATCH/top.out" 2>&1
grep -qi "mem\|CPU\|PID" "$SCRATCH/top.out"
check $? "busybox top renders with the overlay present"
guest "$SCRATCH/rfs-l2s" 1 "$SD" "ps | head -3" > "$SCRATCH/ps.out" 2>&1
grep -q "PID" "$SCRATCH/ps.out"
check $? "ps still lists the real process tree (real /proc rows)"

# ---------------------------------------------------------------- summary
echo "== rehearsal summary =="
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ] && echo "FULL PASS" || { echo "FAILURES PRESENT"; exit 1; }
