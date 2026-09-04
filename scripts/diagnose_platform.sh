#!/bin/sh
# PocketShell guest environment diagnostic (v0.7.0-m3.6).
# Run INSIDE the Alpine guest (Linux Shell, or any command-app session).
# POSIX sh, busybox-safe: reports libc identity, procfs state, virtual-fs
# mounts and the glibc/musl shape of any ELF binary handed to it.
#
# Motivation: docs/PROCFS-CONTRACT.md, docs/ANTIGRAVITY-PLATFORM.md.
# Exit code 0 when the /proc contract holds, 1 when it does not — usable
# as a smoke gate.
set -u

PASS=0
FAIL=0

ok()  { PASS=$((PASS+1)); printf 'PASS  %s\n' "$1"; }
bad() { FAIL=$((FAIL+1)); printf 'FAIL  %s\n' "$1"; }
info(){ printf 'INFO  %s\n' "$1"; }

echo "== identity =="
info "uname -s/-m/-r : $(uname -s) $(uname -m) $(uname -r)"
if [ -f /etc/alpine-release ]; then
    info "alpine-release : $(cat /etc/alpine-release)"
fi
if ldd /bin/busybox 2>/dev/null | grep -q musl; then
    info "libc           : musl ($(ldd /bin/busybox 2>/dev/null | head -1 | awk '{print $1}'))"
elif command -v ldd >/dev/null 2>&1 && ldd --version 2>/dev/null | grep -q GNU; then
    info "libc           : glibc ($(ldd --version | head -1))"
else
    info "libc           : unrecognized"
fi
info "getconf GNU_LIBC_VERSION (glibc-only, must fail on musl): $(getconf GNU_LIBC_VERSION 2>&1 | head -1)"

echo "== procfs contract (docs/PROCFS-CONTRACT.md) =="
if [ -d /proc ]; then ok "/proc exists"; else bad "/proc missing"; fi
if [ -d /proc/self ]; then ok "/proc/self exists"; else bad "/proc/self missing"; fi
if [ -e /proc/version ] && head -c 12 /proc/version >/dev/null 2>&1; then
    ok "/proc/version readable: $(head -c 40 /proc/version)…"
else
    bad "/proc/version missing or unreadable (Bun CLIs will fail realpath)"
fi
if [ -e /proc/self/root ]; then
    ok "/proc/self/root -> $(readlink /proc/self/root 2>/dev/null || echo '?')"
else
    bad "/proc/self/root missing"
fi
if [ -r /proc/cpuinfo ]; then ok "/proc/cpuinfo readable"; else bad "/proc/cpuinfo unreadable"; fi
if [ -r /proc/self/stat ] && head -c 10 /proc/self/stat 2>/dev/null | grep -q "[0-9]"; then
    ok "/proc/self/stat readable (own process entry usable)"
else
    bad "/proc/self/stat missing or unreadable (no usable /proc)"
fi
# Process ROWS are host-policy dependent (hidepid, containers): Android
# shows the app's own tree; some CI sandboxes hide all pid dirs. INFO only.
if command -v ps >/dev/null 2>&1 && ps 2>/dev/null | grep -Eq '^[[:space:]]*[0-9]+'; then
    info "ps lists process rows"
else
    info "ps shows no rows (host pid-hiding policy — not a contract failure)"
fi

echo "== other virtual filesystems =="
[ -e /dev/null ]  && ok "/dev/null present"   || bad "/dev/null missing"
[ -d /dev/pts ]   && ok "/dev/pts present"    || info "/dev/pts not visible (guest pty alloc goes through /dev/ptmx)"
[ -e /dev/ptmx ]  && ok "/dev/ptmx present"   || bad "/dev/ptmx missing"
[ -d /sys/class ] && ok "/sys/class present"  || bad "/sys/class missing"
[ -d /tmp ] && [ -w /tmp ] && ok "/tmp present and writable" || bad "/tmp missing or read-only"
info "mount | grep proc (shows the HOST mount table under proot — binds are ptrace translation, not mounts):"
mount 2>/dev/null | grep proc | head -3 | sed 's/^/      /'

echo "== ELF interpreter probe (pass a binary to check glibc vs musl) =="
if [ "${1:-}" != "" ] && [ -f "$1" ]; then
    if command -v readelf >/dev/null 2>&1; then
        interp=$(readelf -l "$1" 2>/dev/null | awk '/interpreter/ {print $NF}')
        if [ -n "$interp" ]; then
            case "$interp" in
                *musl*)     info "$1 interpreter: $interp (musl build)";;
                *ld-linux*) info "$1 interpreter: $interp (glibc build — needs gcompat on Alpine, may still fail: docs/ANTIGRAVITY-PLATFORM.md)";;
                *)          info "$1 interpreter: $interp";;
            esac
        else
            info "$1: no PT_INTERP (static binary)"
        fi
    else
        info "readelf not installed — apk add binutils to enable the ELF probe"
    fi
fi

echo "== summary =="
printf 'PASS=%d FAIL=%d\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
