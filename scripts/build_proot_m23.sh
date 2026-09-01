#!/bin/bash
# M2.3: cross-build the proot stack for PocketShell.
#   - proot: termux/proot pin 7266fb3e8516535682f5a9c8f3a7e70f6506eddb (GPL-2.0)
#   - talloc 2.4.2 (LGPL-3+), dynamic, SONAME normalized to libtalloc.so
#   - libandroid-shmem (BSD, termux) dynamic
# Outputs: /home/z/tools/m23-dist/<abi>/{libproot.so,libproot-loader.so,libtalloc.so,libandroid-shmem.so}
#          /home/z/tools/m23-dist/host/{proot,loader,libtalloc.so}  (x86_64 rehearsal build)
set -euo pipefail

UP=/home/z/tools/upstream
NDK=/home/z/android-sdk/ndk/28.2.13676358
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
API=26
BUILD=/home/z/tools/m23-build
DIST=/home/z/tools/m23-dist
TAG="[m23-build $(date '+%H:%M:%S')]"

log() { printf '%s %s\n' "$TAG" "$*"; }

rm -rf "$BUILD" "$DIST"
mkdir -p "$BUILD" "$DIST"

# Micro-patch (ours, documented): ashmem_memfd.c relies on glibc's transitive
# <string.h>; bionic + clang>=16 makes the implicit decls an error.
if ! rg -q '#include <string.h>' "$UP/proot/src/extension/ashmem_memfd/ashmem_memfd.c"; then
  sed -i '1i #include <string.h>' "$UP/proot/src/extension/ashmem_memfd/ashmem_memfd.c"
fi

# Micro-patch (ours, documented): loader-info.awk uses gawk-only strtonum;
# Debian default awk is mawk. Portable hex decoder below.
cat > "$UP/proot/src/loader/loader-info.awk" << 'AWKEOF'
# Note: This file is included only for targets which have pokedata workaround
# (pocketshell build patch: strtonum is gawk-only; portable hex2dec for mawk)
function hex2dec(s,   d,i,c) {
        d = 0
        s = tolower(s)
        for (i = 1; i <= length(s); i++) {
                c = substr(s, i, 1)
                if (c >= "a" && c <= "f") d = d * 16 + index("0123456789abcdef", c) - 1
                else if (c >= "0" && c <= "9") d = d * 16 + c
        }
        return d
}
/\ypokedata_workaround\y/{pokedata_workaround=hex2dec($2)}
/\y_start\y/{start=hex2dec($2)}
END {
        print "#include <unistd.h>"
        print "const ssize_t offset_to_pokedata_workaround=" (pokedata_workaround-start) ";"
}
AWKEOF

# ---------------------------------------------------------------- talloc
# Canned cross-answers for waf compile-only cross mode (cosmetic checks only).
CROSS_ANSWERS='Checking simple C program: "pocketshell"
Checking uname sysname type: "Linux"
Checking uname machine type: "arm64"
Checking uname release type: "5.15.0"
Checking uname version type: "#1 SMP"
Checking for large file support without additional flags: "OK"
Checking for -D_FILE_OFFSET_BITS=64: "OK"
Checking for -D_LARGE_FILES: "OK"
Checking for working strptime: "OK"
Checking C prototype for gettimeofday: "OK"
Checking C prototype for gettimeofday: "OK"
Checking for C99 vsnprintf: "OK"
Checking for HAVE_SHARED_MMAP: "OK"
Checking for HAVE_MREMAP: "OK"
Checking for HAVE_INCOHERENT_MMAP: "NO"
Checking for HAVE_SECURE_MKSTEMP: "OK"
Checking for HAVE_IFACE_GETIFADDRS: "NO"
Checking for HAVE_IFACE_AIX: "NO"
Checking for HAVE_IFACE_IFCONF: "NO"
Checking for HAVE_IFACE_IFREQ: "NO"
Checking for declaration of getpwent_r: "NO"
Checking for declaration of getpwent_r (as enum): "NO"
Checking for declaration of getgrent_r: "NO"
Checking for declaration of getgrent_r (as enum): "NO"
Checking for declaration of getpwent_r: "NO"
Checking for declaration of getpwent_r (as enum): "NO"
Checking for declaration of getgrent_r: "NO"
Checking for declaration of getgrent_r (as enum): "NO"
Checking for XSI (rather than GNU) prototype for strerror_r: "NO"
rpath library support: "OK"
-Wl,--version-script support: "OK"
Checking getconf LFS_CFLAGS: ""
Checking correct behavior of strtoll: "OK"
Checking getconf large file support flags work: "OK"
'

# $1 = workdir, $2 = CC, $3 = AR, $4 = out dir, $5+ = extra configure args
build_talloc() {
  local src="$1" cc="$2" ar="$3" out="$4"; shift 4
  cp -r "$UP/talloc-2.4.2" "$src"
  # lld (NDK) rejects version-script local assignments for linker-internal
  # symbols it does not see; GNU ld tolerates them. Drop the trio (samba_abi.py).
  sed -i 's/local_abi\.extend(\["!_end", "!__bss_start", "!_edata"\])/local_abi.extend([])/' \
    "$src/buildtools/wafsamba/samba_abi.py"
  rg -q 'local_abi\.extend\(\[\]\)' "$src/buildtools/wafsamba/samba_abi.py" || { echo "vscript patch failed"; exit 1; }
  ( cd "$src" \
    && CC="$cc" AR="$ar" LINKFLAGS="-Wl,-soname,libtalloc.so" \
       ./configure --prefix="$out" --disable-python --abi-check-disable "$@" \
    && make -j2 && make install ) > "$src.build.log" 2>&1 || {
      tail -30 "$src.build.log"; log "TALLOC FAILED for $src"; return 1; }
  # normalize: waf may produce libtalloc.so.X.Y.Z with SONAME libtalloc.so.2
  local real
  real=$(ls "$out/lib/libtalloc.so."* 2>/dev/null | head -1 || true)
  if [ -n "$real" ]; then
    cp "$real" "$out/libtalloc.so.raw"
    python3 - "$out/libtalloc.so.raw" << 'PYEOF'
import sys
p = sys.argv[1]
b = open(p, 'rb').read()
n = b.count(b'libtalloc.so.2')
b = b.replace(b'libtalloc.so.2', b'libtalloc.so\x00\x00')
open(p, 'wb').write(b)
print(f'[soname-patch] {p}: {n} occurrence(s) patched')
PYEOF
    mv "$out/libtalloc.so.raw" "$out/libtalloc.so"
  else
    # waf already produced plain libtalloc.so
    cp "$out/lib/"libtalloc.so* "$out/" 2>/dev/null || true
  fi
  ls -la "$out"/libtalloc* || return 1
}

# ------------------------------------------------------- libandroid-shmem
# DROPPED: bionic API 26 (our minSdk) ships native SysV shm (shmget/shmat),
# so proot's sysvipc extension compiles directly against bionic. Termux needs
# the ashmem shim only for older APIs.

# ---------------------------------------------------------------- proot
# $1 = abi label, $2 = CC, $3 = bin prefix ("" for host tools), $4 = -L dir,
# $5 = include dir, $6 = out dir, $7+ = extra env
build_proot() {
  local abi="$1" cc="$2" prefix="$3" ldir="$4" incdir="$5" out="$6"; shift 6
  mkdir -p "$out"
  ( cd "$UP/proot" \
    && make -C src clean >/dev/null 2>&1 || true \
    && env $@ \
       CPPFLAGS="-DARG_MAX=131072 -I$incdir" \
       CFLAGS="-Wno-error=implicit-function-declaration" \
       LDFLAGS="-L$ldir -Wl,-z,noexecstack" \
       CC="$cc" AR="${prefix}ar" STRIP="${prefix}strip" \
       OBJCOPY="${prefix}objcopy" OBJDUMP="${prefix}objdump" \
       make -C src -j2 proot loader/loader ) > "$BUILD/proot-$abi.build.log" 2>&1 || {
      tail -40 "$BUILD/proot-$abi.build.log"; log "PROOT FAILED ($abi)"; return 1; }
  cp "$UP/proot/src/proot" "$out/libproot.so"
  cp "$UP/proot/src/loader/loader" "$out/libproot-loader.so"
  if [ -f "$UP/proot/src/loader/loader-m32" ]; then
    cp "$UP/proot/src/loader/loader-m32" "$out/libproot-loader32.so" || true
  fi
  ( cd "$UP/proot" && make -C src clean >/dev/null 2>&1 || true )
  log "$abi: proot $(stat -c %s "$out/libproot.so")B loader $(stat -c %s "$out/libproot-loader.so")B"
}

# ============================================================== cross builds
declare -A TRIPLE=(
  [arm64-v8a]=aarch64-linux-android$API
  [armeabi-v7a]=armv7a-linux-androideabi$API
  [x86_64]=x86_64-linux-android$API
  [x86]=i686-linux-android$API
)

for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  tri=${TRIPLE[$abi]}
  cc="$TC/$tri-clang"
  out="$DIST/$abi"
  mkdir -p "$out"
  log "=== $abi ($tri) ==="
  printf '%s' "$CROSS_ANSWERS" > "$BUILD/cross-answers.txt"   # fresh seed per arch
  build_talloc "$BUILD/talloc-$abi" "$cc" "$TC/llvm-ar" "$out" --cross-compile --cross-answers="$BUILD/cross-answers.txt"
  extra=""
  [ "$abi" = "x86_64" ] && extra="HAS_LOADER_32BIT="   # NDK x86_64 cannot -m32 the loader
  build_proot "$abi" "$cc" "$TC/llvm-" "$out" "$out/include" "$out" $extra
done

# ============================================================== host build
log "=== host (x86_64 glibc rehearsal) ==="
HOUT="$DIST/host"; mkdir -p "$HOUT"
build_talloc "$BUILD/talloc-host" "gcc" "ar" "$HOUT"
build_proot host "gcc" "" "$HOUT" "$HOUT/include" "$HOUT"

log "ALL BUILDS DONE"
echo "--- artifacts:"
ls -la "$DIST"/*/ | sed 's/^/  /'
echo "--- DT_NEEDED (libproot.so, arm64):"
"$TC/llvm-readelf" -d "$DIST/arm64-v8a/libproot.so" | rg "NEEDED|SONAME" || readelf -d "$DIST/arm64-v8a/libproot.so" | rg "NEEDED|SONAME"
echo "--- DT_NEEDED (libtalloc.so, arm64):"
"$TC/llvm-readelf" -d "$DIST/arm64-v8a/libtalloc.so" | rg "NEEDED|SONAME" || readelf -d "$DIST/arm64-v8a/libtalloc.so" | rg "NEEDED|SONAME"
file "$DIST"/*/libproot.so "$DIST/host/proot" 2>/dev/null || true
