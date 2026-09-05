#!/bin/bash
# build_test_binaries.sh — cross-compile the glibc compatibility test binaries
# (aarch64, Debian cross gcc 14 from the rig) into runtime-tests/bin/, then
# stage them into the rig rootfs at /opt/pocketshell-tests/.
set -euo pipefail
RIG=/home/z/tools/rig
XROOT=$RIG/xroot
SRC=/home/z/my-project/runtime-tests/src
BIN=/home/z/my-project/runtime-tests/bin
GCC=$XROOT/usr/bin/aarch64-linux-gnu-gcc-14
GXX=$XROOT/usr/bin/aarch64-linux-gnu-g++-14
SYSROOT=$XROOT/usr/aarch64-linux-gnu
GBIND=$XROOT/usr/lib/gcc-cross/aarch64-linux-gnu/14

# Cross ld scripts reference ABSOLUTE /usr/aarch64-linux-gnu/lib/... paths;
# with --sysroot, ld joins sysroot + that path. The standard fix: make
# $SYSROOT/usr/aarch64-linux-gnu resolve back to $SYSROOT itself.
mkdir -p "$SYSROOT/usr"
ln -sfn .. "$SYSROOT/usr/aarch64-linux-gnu"

CFLAGS="-O2 --sysroot=$SYSROOT -B$GBIND/ -B$XROOT/usr/bin/"
LDFLAGS_COMMON="--sysroot=$SYSROOT -B$GBIND/ -B$XROOT/usr/bin/"
# Cross-binutils helper libs (libbfd/libopcodes) live in the x86_64 dir.
export LD_LIBRARY_PATH="$XROOT/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

mkdir -p "$BIN"
build() { echo "== $1"; }

build t_hello
$GCC $CFLAGS "$SRC/t_hello.c" $LDFLAGS_COMMON -o "$BIN/t_hello"

build t_pthread   # DT_NEEDED libpthread.so.0 forced (Cline's dependency shape)
$GCC $CFLAGS "$SRC/t_pthread.c" $LDFLAGS_COMMON -pthread -Wl,--no-as-needed,-lpthread -o "$BIN/t_pthread"

build t_dlopen
$GCC $CFLAGS "$SRC/t_dlopen.c" $LDFLAGS_COMMON -ldl -o "$BIN/t_dlopen"

build t_libm      # DT_NEEDED libm.so.6 forced (Cline's dependency shape)
$GCC $CFLAGS "$SRC/t_libm.c" $LDFLAGS_COMMON -Wl,--no-as-needed,-lm -o "$BIN/t_libm"

build t_cpp
$GCC $CFLAGS -xc++ "$SRC/t_cpp.cpp" $LDFLAGS_COMMON -lstdc++ -lm -o "$BIN/t_cpp"

build t_fork_exec
$GCC $CFLAGS "$SRC/t_fork_exec.c" $LDFLAGS_COMMON -o "$BIN/t_fork_exec"

build t_getaddrinfo
$GCC $CFLAGS "$SRC/t_getaddrinfo.c" $LDFLAGS_COMMON -o "$BIN/t_getaddrinfo"

build t_getpwnam
$GCC $CFLAGS "$SRC/t_getpwnam.c" $LDFLAGS_COMMON -o "$BIN/t_getpwnam"

build t_static
$GCC $CFLAGS "$SRC/t_hello.c" $LDFLAGS_COMMON -static -o "$BIN/t_static"

echo "== staged shapes (host readelf):"
for b in t_hello t_pthread t_libm t_static; do
  interp=$(readelf -l "$BIN/$b" 2>/dev/null | sed -n 's/.*Requesting program interpreter: \(.*\)]/\1/p' || true)
  echo "  $b interp=${interp:-<none,static-or-symlinked>} $(stat -c %s "$BIN/$b")B"
done
for b in t_pthread t_libm; do
  echo "  $b DT_NEEDED: $(readelf -d "$BIN/$b" | sed -n 's/.*NEEDED Shared library: \[\(.*\)\]/\1/p' | tr '\n' ' ')"
done

# Stage into the rig rootfs
DEST=$RIG/rootfs/opt/pocketshell-tests
mkdir -p "$DEST"
cp "$BIN"/t_* "$DEST/"
chmod 755 "$DEST"/t_*
echo "== staged $(ls "$DEST" | wc -l) binaries into rig rootfs $DEST"

# t_cline_shape — replicates Cline 3.0.61's exact DT_NEEDED class:
# libc.so.6 + libpthread.so.0 + libdl.so.2 + libm.so.6 (forced, no-as-needed).
$GCC $CFLAGS "$SRC/t_pthread.c" $LDFLAGS_COMMON -pthread \
  -Wl,--no-as-needed \
  "$SYSROOT/lib/libpthread.so.0" "$SYSROOT/lib/libdl.so.2" "$SYSROOT/lib/libm.so.6" \
  -o "$BIN/t_cline_shape"
cp "$BIN"/t_cline_shape "$DEST/"
chmod 755 "$DEST/t_cline_shape"
echo "== t_cline_shape DT_NEEDED:"
readelf -d "$BIN/t_cline_shape" | sed -n 's/.*(NEEDED).*\[\(.*\)\]/  \1/p'
echo "== staged $(ls "$DEST" | wc -l) binaries total"
