#!/bin/bash
# HOST-ONLY variant of build_proot_m23.sh (sandbox rehearsal acceleration):
# builds ONLY the x86_64 glibc rehearsal proot from the SAME source pins and
# the SAME micro-patches. The shipped APK jniLibs keep using the full-script
# cross builds committed in-repo — this script never touches them.
#   pin: termux/proot 7266fb3e8516535682f5a9c8f3a7e70f6506eddb (GPL-2.0)
#   pin: talloc 2.4.2 (LGPL-3+)
set -euo pipefail
UP=/home/z/tools/upstream
BUILD=/home/z/tools/m23-build
DIST=/home/z/tools/m23-dist
TAG="23-host $(date '+%H:%M:%S')]"
log() { printf '%s %s\n' "$TAG" "$*"; }

mkdir -p "$UP" "$BUILD" "$DIST/host"

# ---- proot source at the exact pin (reuse an existing checkout if valid)
if [ ! -d "$UP/proot" ]; then
  log "cloning termux/proot"
  git clone -q https://github.com/termux/proot.git "$UP/proot"
fi
git -C "$UP/proot" checkout -q 7266fb3e8516535682f5a9c8f3a7e70f6506eddb
git -C "$UP/proot" clean -qfdx src || true

# Micro-patch 1 (same as build_proot_m23.sh): bionic+clang16 implicit decls.
if ! grep -q '#include <string.h>' "$UP/proot/src/extension/ashmem_memfd/ashmem_memfd.c"; then
  sed -i '1i #include <string.h>' "$UP/proot/src/extension/ashmem_memfd/ashmem_memfd.c"
fi

# Micro-patch 2: portable loader-info.awk (mawk has no strtonum).
cat > "$UP/proot/src/loader/loader-info.awk" << 'AWKEOF'
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

# ---- talloc 2.4.2 at the pin
cd "$UP"
if [ ! -d talloc-2.4.2 ]; then
  log "downloading talloc 2.4.2"
  curl -fsSL --retry 3 --max-time 300 "https://www.samba.org/ftp/talloc/talloc-2.4.2.tar.gz" -o talloc.tar.gz
  tar -xzf talloc.tar.gz && rm talloc.tar.gz
fi

HOUT="$DIST/host"
log "=== host talloc ==="
( cd "$UP/talloc-2.4.2" \
  && ./configure --prefix="$HOUT" >/dev/null \
  && make -j2 >/dev/null \
  && cp bin/default/libtalloc.so "$HOUT/libtalloc.so" )
mkdir -p "$HOUT/include"
cp "$UP/talloc-2.4.2/talloc.h" "$HOUT/include/talloc.h"
log "talloc: $(stat -c %s "$HOUT/libtalloc.so")B"

log "=== host proot ==="
( cd "$UP/proot" \
  && make -C src clean >/dev/null 2>&1 || true \
  && CPPFLAGS="-DARG_MAX=131072 -I$HOUT/include" \
     CFLAGS="-Wno-error=implicit-function-declaration" \
     LDFLAGS="-L$HOUT -Wl,-z,noexecstack" \
     CC="gcc" make -C src -j2 proot loader/loader ) > "$BUILD/proot-host.build.log" 2>&1 || {
  tail -40 "$BUILD/proot-host.build.log"; log "PROOT HOST FAILED"; exit 1; }
cp "$UP/proot/src/proot" "$HOUT/libproot.so"
cp "$UP/proot/src/loader/loader" "$HOUT/libproot-loader.so"
( cd "$UP/proot" && make -C src clean >/dev/null 2>&1 || true )
log "proot: $(stat -c %s "$HOUT/libproot.so")B loader: $(stat -c %s "$HOUT/libproot-loader.so")B"

# SONAME handling (rehearsal-only): glibc records the talloc SONAME it saw at
# link time as DT_NEEDED. Normalize via patchelf when available; otherwise a
# matching symlink keeps LD_LIBRARY_PATH resolution working.
SONAME=$(readelf -d "$HOUT/libtalloc.so" | sed -n 's/.*SONAME.*\[\(.*\)\].*/\1/p')
if [ -n "$SONAME" ] && [ "$SONAME" != "libtalloc.so" ]; then
  if command -v patchelf >/dev/null; then
    patchelf --set-soname libtalloc.so "$HOUT/libtalloc.so"
    log "normalized talloc SONAME $SONAME -> libtalloc.so"
  else
    ln -sf libtalloc.so "$HOUT/$SONAME"
    log "patchelf unavailable; SONAME $SONAME stays, added symlink $HOUT/$SONAME"
  fi
fi

log "HOST BUILD DONE"
ls -la "$HOUT"
file "$HOUT/libproot.so" "$HOUT/libproot-loader.so" "$HOUT/libtalloc.so"
