#!/bin/bash
# rig_setup.sh — one-shot sandbox emulation rig (x86_64 host, aarch64 guest).
# Recreates /home/z/tools/rig after a sandbox reset. All downloads pinned +
# SHA-verified (same pins as the app: RuntimePin for the minirootfs,
# resolved Debian pool SHAs for the sidecar inputs).
#   rig/rootfs           Alpine 3.24.1 aarch64 minirootfs + glibc layer
#   rig/qu/usr/bin/qemu-aarch64   (static, Debian trixie)
#   rig/xroot            aarch64 glibc cross toolchain (test-binary builds)
set -euo pipefail
RIG=/home/z/tools/rig
HOSTDIST=/home/z/tools/m23-dist/host
MINIROOTFS_URL=https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.1-aarch64.tar.gz
MINIROOTFS_SHA=f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259
SIDECAR=$(ls /home/z/my-project/download/glibc-sidecar/pocketshell-glibc-aarch64-*.tar.gz | head -1)

mkdir -p "$RIG"

# ---- qemu-aarch64 (static) ---------------------------------------------------
if [ ! -x "$RIG/qu/usr/bin/qemu-aarch64" ]; then
  cd "$RIG"
  apt-get download qemu-user >/dev/null
  dpkg -x qemu-user_*.deb qu
fi
"$RIG/qu/usr/bin/qemu-aarch64" --version | head -1

# ---- Alpine minirootfs (pinned, same pin as RuntimePin) ----------------------
if [ ! -f "$RIG/alpine-minirootfs.tar.gz" ]; then
  echo "== fetching pinned minirootfs"
  curl -fsSL --retry 3 --max-time 600 "$MINIROOTFS_URL" -o "$RIG/alpine-minirootfs.tar.gz"
fi
echo "$MINIROOTFS_SHA  $RIG/alpine-minirootfs.tar.gz" | sha256sum -c --quiet

if [ ! -d "$RIG/rootfs/bin" ]; then
  mkdir -p "$RIG/rootfs"
  tar -xzf "$RIG/alpine-minirootfs.tar.gz" -C "$RIG/rootfs"
fi

# ---- glibc layer into the rootfs ---------------------------------------------
if [ ! -e "$RIG/rootfs/usr/lib/aarch64-linux-gnu/libc.so.6" ]; then
  echo "== extracting sidecar: $(basename "$SIDECAR")"
  tar -xzf "$SIDECAR" -C "$RIG/rootfs"
fi
echo "rootfs: $(du -sh "$RIG/rootfs" | cut -f1)"

# ---- guest DNS (same content shape GuestEnvironment writes) ------------------
cat > "$RIG/rootfs/etc/resolv.conf" << 'EOF'
# managed by PocketShell — device resolvers first, public fallback after
nameserver 1.1.1.1
nameserver 8.8.8.8
EOF

# ---- cross toolchain (host-native debs, aarch64 target) ----------------------
if [ ! -x "$RIG/xroot/usr/bin/aarch64-linux-gnu-gcc-14" ]; then
  cd "$RIG"
  apt-get download gcc-14-aarch64-linux-gnu cpp-14-aarch64-linux-gnu \
    binutils-aarch64-linux-gnu libstdc++-14-dev-arm64-cross libc6-dev-arm64-cross libgcc-14-dev-arm64-cross \
    linux-libc-dev-arm64-cross >/dev/null
  mkdir -p xroot
  for f in gcc-14-aarch64-linux-gnu cpp-14-aarch64-linux-gnu g++-14-aarch64-linux-gnu binutils-aarch64-linux-gnu libstdc++-14-dev-arm64-cross \
           libc6-dev-arm64-cross libgcc-14-dev-arm64-cross linux-libc-dev-arm64-cross; do
    dpkg -x "${f}"_*.deb xroot
  done
fi
"$RIG/xroot/usr/bin/aarch64-linux-gnu-gcc-14" --version | head -1
echo "RIG READY"
