#!/bin/bash
# build-ndk.sh — cross-build mc + simple-client for ARM64 Android.
#
# Builds user-local in ~/p2-lab/android-prefix:
#   libffi 3.4.6, libxkbcommon 1.7.x, libwayland 1.23.1 (server+client)
# then compiles the compositor (with the EGL sink) and the test client.
#
# Prereqs (already on this laptop): NDK r28, host wayland-scanner (~/p2-lab/prefix),
# meson/ninja, libffi+xkbcommon+wayland source tarballs in ~/p2-lab/src.
set -euo pipefail

NDK="$HOME/Android/Sdk/ndk/28.2.13676358"
API=28
TOOL="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
TARGET=aarch64-linux-android
PREFIX="$HOME/p2-lab/android-prefix"
SRC="$HOME/p2-lab/src"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export PATH="$HOME/p2-lab/prefix/bin:$PATH"
export PKG_CONFIG="$HOME/p2-lab/pkg-config-user"
mkdir -p "$PREFIX"

cat > "$SRC/android-cross.ini" <<EOF
[binaries]
c   = '$TOOL/bin/${TARGET}${API}-clang'
ar  = '$TOOL/bin/llvm-ar'
strip = '$TOOL/bin/llvm-strip'
pkg-config = '/usr/bin/pkg-config'

[host_machine]
system = 'linux'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'

[properties]
needs_exe_wrapper = true
c_args = ['--sysroot=$TOOL/sysroot']
c_link_args = []
EOF

echo "== 1. libffi (android) =="
if [ ! -f "$PREFIX/lib/libffi.a" ] && [ ! -f "$PREFIX/lib/libffi.so" ]; then
  cd "$SRC/libffi-3.4.6"
  make distclean >/dev/null 2>&1 || true
  CC="$TOOL/bin/${TARGET}${API}-clang" \
  ./configure --host=${TARGET} --prefix="$PREFIX" --disable-docs --enable-static --disable-shared \
    > "$SRC/ffi-android-configure.log" 2>&1
  make -j4 >> "$SRC/ffi-android-build.log" 2>&1
  make install >> "$SRC/ffi-android-build.log" 2>&1
  echo "   libffi installed"
else
  echo "   already built"
fi

echo "== 2. libxkbcommon (android) =="
XKBCOMMON_DIR=$(ls -d "$SRC"/libxkbcommon-*/ 2>/dev/null | head -1 || true)
if [ -n "$XKBCOMMON_DIR" ] && [ ! -f "$PREFIX/lib/libxkbcommon.a" ]; then
  cd "$XKBCOMMON_DIR"
  rm -rf build-android
  PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig" \
  meson setup build-android --prefix="$PREFIX" --cross-file "$SRC/android-cross.ini" \
    -Denable-x11=false -Denable-docs=false -Denable-tools=false -Denable-xkbregistry=false \
    > "$SRC/xkb-android-setup.log" 2>&1
  ninja -C build-android >> "$SRC/xkb-android-build.log" 2>&1
  meson install -C build-android >> "$SRC/xkb-android-build.log" 2>&1
  echo "   libxkbcommon installed"
else
  echo "   already built (or source missing: download libxkbcommon tarball)"
fi

echo "== 3. libwayland (android) =="
if [ ! -f "$PREFIX/lib/libwayland-server.a" ]; then
  cd "$SRC/wayland-1.23.1"
  rm -rf build-android
  PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig:$PREFIX/lib/x86_64-linux-gnu/pkgconfig" \
  meson setup build-android --prefix="$PREFIX" --cross-file "$SRC/android-cross.ini" \
    --native-file "$SRC/android-native.ini" \
    -Dpkg_config_path="$PREFIX/lib/pkgconfig" \
    -Dbuild.pkg_config_path="$HOME/p2-lab/prefix/lib/pkgconfig:$HOME/p2-lab/prefix/lib/x86_64-linux-gnu/pkgconfig" \
    -Ddocumentation=false -Ddtd_validation=false -Dtests=false \
    -Dlibraries=true -Dscanner=false \
    > "$SRC/wayland-android-setup.log" 2>&1
  ninja -C build-android >> "$SRC/wayland-android-build.log" 2>&1
  meson install -C build-android >> "$SRC/wayland-android-build.log" 2>&1
  echo "   libwayland installed"
else
  echo "   already built"
fi

echo "== 4. mc compositor (ARM64, EGL sink) =="
cd "$SCRIPT_DIR/.."
mkdir -p build-android
HOST_SCANNER="$HOME/p2-lab/prefix/bin/wayland-scanner"
WL_XML="$HOME/p2-lab/prefix/share/wayland/wayland.xml"
XDG_XML="$HOME/p2-lab/src/wayland-protocols-1.36/stable/xdg-shell/xdg-shell.xml"
"$HOST_SCANNER" server-header "$WL_XML" src/protocol-wayland-server.h
"$HOST_SCANNER" private-code  "$WL_XML" src/protocol-wayland-server.c
"$HOST_SCANNER" server-header "$XDG_XML" src/protocol-xdg-shell-server.h
"$HOST_SCANNER" private-code  "$XDG_XML" src/protocol-xdg-shell.c
"$HOST_SCANNER" client-header "$XDG_XML" client/xdg-shell-client-protocol.h
"$HOST_SCANNER" private-code  "$XDG_XML" client/protocol-xdg-shell-client.c

CC="$TOOL/bin/${TARGET}${API}-clang"
MC_FLAGS="-O2 -g -Isrc -I$PREFIX/include --sysroot=$TOOL/sysroot -DMC_ANDROID=1"
$CC $MC_FLAGS \
  src/main.c src/shm.c src/shell.c src/seat.c \
  src/protocol-wayland-server.c src/protocol-xdg-shell.c \
  src/backends/sink_egl.c src/backends/sink_ppm.c \
  -o build-android/mc \
  -L"$PREFIX/lib" -lwayland-server -lxkbcommon \
  -lEGL -lGLESv2 -landroid -llog \
  -Wl,-rpath,"/data/local/tmp/mc-libs"
echo "   build-android/mc done"

echo "== 5. simple-client (ARM64) =="
$CC $MC_FLAGS \
  client/simple-client.c client/protocol-xdg-shell-client.c \
  -o build-android/simple-client \
  -L"$PREFIX/lib" -lwayland-client
echo "   build-android/simple-client done"

echo "== done =="
ls -la build-android/
file build-android/mc build-android/simple-client
