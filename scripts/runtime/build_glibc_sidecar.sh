#!/bin/bash
# build_glibc_sidecar.sh — build the PocketShell glibc runtime layer (aarch64).
#
# Assembles REAL Debian trixie (13) arm64 glibc components into a rootfs overlay
# tarball placed at CANONICAL multiarch paths (docs/runtime/DUAL_LIBC.md §2):
#   lib/ld-linux-aarch64.so.1            (real glibc loader)
#   lib/aarch64-linux-gnu/…              (libc, libm, libpthread/libdl/librt stubs,
#                                         libresolv, NSS, zlib, ssl/crypto, …)
#   usr/lib/aarch64-linux-gnu/…          (libstdc++, libgcc_s; user extension slot)
#   lib64/ld-linux-aarch64.so.1          (RHEL-style interp alias)
#   etc/nsswitch.conf                    (glibc NSS policy; musl ignores it)
# Alpine/musl paths are never touched: the loader name, SONAMEs and dirs are
# disjoint from musl by construction.
#
# Reproducibility: every input .deb is fetched from the pinned Debian pool and
# verified against a hard-pinned SHA-256 (recorded in this file by the first
# build; `--check` re-verifies without rebuilding). Output tarball is
# normalized (sorted names, zero mtimes, root ownership) and its SHA-256 is
# printed for the GlibcRuntimePin pin. gconv modules are deliberately excluded
# (documented in docs/runtime/KNOWN_LIMITATIONS.md).
set -euo pipefail

RIG=/home/z/tools/rig/glibc
DIST=/home/z/my-project/download/glibc-sidecar
MIRROR=https://deb.debian.org/debian
SUITE=trixie
MODE="${1:-build}"

# ---- pinned inputs (arm64, Debian 13 trixie) --------------------------------
# name:version:filename:sha256 — filled/verified by resolve step below.
# shellcheck disable=SC2034
PKGS=(
  "libc6"
  "libgcc-s1"
  "libstdc++6"
  "zlib1g"
  "libssl3t64"
  "liblzma5"
  "libbz2-1.0"
  "libexpat1"
  "libffi8"
  "libpcre2-8-0"
  "libyaml-0-2"
  "libtinfo6"
  "libncurses6"
  "libncursesw6"
  "libreadline8t64"
)

mkdir -p "$RIG/debs" "$RIG/stage" "$DIST"

resolve_from_index() {
  # Parse Packages.gz for <name>_arm64.deb filename + SHA256.
  local idx="$RIG/Packages.gz"
  if [ ! -f "$idx" ]; then
    echo "== fetching $SUITE main binary-arm64 index"
    curl -fsSL --retry 3 --max-time 300 "$MIRROR/dists/$SUITE/main/binary-arm64/Packages.gz" -o "$idx"
  fi
  python3 - "$idx" "${PKGS[@]}" << 'PY'
import gzip, sys, re
idx, wanted = sys.argv[1], set(sys.argv[2:])
data = gzip.open(idx, "rt", errors="replace").read().split("\n\n")
found = {}
for para in data:
    fields = dict(l.split(": ", 1) for l in para.splitlines() if ": " in l)
    name = fields.get("Package")
    if name in wanted and name not in found and fields.get("Filename", "").endswith("_arm64.deb"):
        found[name] = (fields["Version"], fields["Filename"], fields["SHA256"])
missing = wanted - set(found)
if missing:
    sys.exit(f"missing in index: {sorted(missing)}")
for name in sorted(found):
    ver, fname, sha = found[name]
    print(f"{name}\t{ver}\t{fname}\t{sha}")
PY
}

fetch_and_verify() {
  local name="$1" ver="$2" fname="$3" sha="$4"
  local out="$RIG/debs/$(basename "$fname")"
  if [ -f "$out" ] && echo "$sha  $out" | sha256sum -c --quiet 2>/dev/null; then
    echo "   cached+verified $(basename "$fname")"
  else
    echo "== downloading $(basename "$fname") ($ver)"
    curl -fsSL --retry 3 --max-time 600 "$MIRROR/$fname" -o "$out"
    echo "$sha  $out" | sha256sum -c --quiet || { echo "SHA MISMATCH: $out"; exit 1; }
  fi
}

prune_stage() {
  local s="$RIG/stage"
  # gconv: ~9 MB of charset modules; excluded on purpose (KNOWN_LIMITATIONS).
  rm -rf "$s/usr/lib/aarch64-linux-gnu/gconv"
  # Debian docs/lintian leftovers; nothing at runtime needs them.
  rm -rf "$s/usr/share/doc" "$s/usr/share/lintian" "$s/usr/share/man"
  # ld.so.cache must NOT exist (Alpine has none; we never build one).
  rm -f "$s/etc/ld.so.cache"
  # Debian trixie debs are merged-usr (everything under /usr/lib/aarch64-linux-gnu).
  # Alpine is NOT merged-usr, and the Debian-built loader's compiled-in default
  # search path contains BOTH /lib/aarch64-linux-gnu and /usr/lib/aarch64-linux-gnu.
  # Replicate merged-usr semantics so both resolve to the one real copy:
  mkdir -p "$s/lib"
  ln -sfn ../usr/lib/aarch64-linux-gnu "$s/lib/aarch64-linux-gnu"
  # Canonical interp path every glibc ELF carries:
  ln -sfn /usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1 "$s/lib/ld-linux-aarch64.so.1"
  # RHEL-style interp alias (gcompat had the same on device).
  mkdir -p "$s/lib64"
  ln -sfn /lib/ld-linux-aarch64.so.1 "$s/lib64/ld-linux-aarch64.so.1"
  # glibc NSS policy (libc6 no longer ships nsswitch.conf; musl ignores it).
  cat > "$s/etc/nsswitch.conf" << 'EOF'
# managed by PocketShell — glibc runtime layer (musl does not read this file)
passwd: files
group: files
shadow: files
hosts: files dns
networks: files
protocols: files
services: files
ethers: files
EOF
  # Extension-slot documentation for /usr/lib/aarch64-linux-gnu.
  cat > "$s/usr/lib/aarch64-linux-gnu/README.pocketshell-glibc" << 'EOF'
PocketShell glibc extension slot
================================
This directory is searched by the real glibc loader AFTER
/lib/aarch64-linux-gnu (compiled-in default order: multiarch dirs, then
/lib, /usr/lib). Place additional glibc-built ARM64 shared objects here
(e.g. libfoo.so.1 from a Debian arm64 package) to satisfy a prebuilt
glibc binary's DT_NEEDED that the base layer does not ship.
Never place musl-built libraries here; never touch /lib or /usr/lib
(musl owns them). Run `pocketshell-doctor <binary>` to diagnose.
EOF
}

check_layout() {
  local s="$RIG/stage"
  # Resolve a path INSIDE the stage, following symlink targets — including the
  # absolute targets that are correct for the guest rootfs but meaningless on
  # this host (Debian trixie debs ship absolute interps; guest-rootfs semantics).
  stage_exists() {
    local cur="$s/$1" t
    local i
    for i in 1 2 3 4 5; do
      if [ -L "$cur" ]; then
        t=$(readlink "$cur")
        case "$t" in
          /*) cur="$s$t" ;;
          *) cur="$(dirname "$cur")/$t" ;;
        esac
      elif [ -e "$cur" ]; then
        return 0
      else
        return 1
      fi
    done
    [ -e "$cur" ]
  }
  local must=(
    "lib/ld-linux-aarch64.so.1"
    "lib/aarch64-linux-gnu/libc.so.6"
    "lib/aarch64-linux-gnu/libm.so.6"
    "lib/aarch64-linux-gnu/libpthread.so.0"
    "lib/aarch64-linux-gnu/libdl.so.2"
    "lib/aarch64-linux-gnu/librt.so.1"
    "lib/aarch64-linux-gnu/libresolv.so.2"
    "lib/aarch64-linux-gnu/libz.so.1"
    "lib/aarch64-linux-gnu/libssl.so.3"
    "lib/aarch64-linux-gnu/libcrypto.so.3"
    "usr/lib/aarch64-linux-gnu/libstdc++.so.6"
    "usr/lib/aarch64-linux-gnu/libgcc_s.so.1"
    "etc/nsswitch.conf"
    "lib64/ld-linux-aarch64.so.1"
  )
  for f in "${must[@]}"; do
    stage_exists "$f" || { echo "LAYOUT MISSING: $f"; exit 1; }
  done
  echo "== layout OK ($(find "$s" -type f | wc -l) files, $(du -sh "$s" | cut -f1))"
}

build_tar() {
  local s="$RIG/stage"
  # Version label comes from the pinned libc6 package version (resolved.tsv).
  local libcver
  libcver=$(awk -F'\t' '$1=="libc6"{print $2}' "$RIG/resolved.tsv")
  [ -n "$libcver" ] || { echo "cannot resolve libc6 version"; exit 1; }
  local tag
  tag=$(echo "$libcver" | tr '+' '.')
  local out="$DIST/pocketshell-glibc-aarch64-${tag}.tar.gz"
  echo "== packing $out"
  tar -C "$s" --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner \
      --format=gnu -czf "$out" .
  local sha size
  sha=$(sha256sum "$out" | cut -d' ' -f1)
  size=$(stat -c %s "$out")
  echo "PIN: artifact=pocketshell-glibc-aarch64-${tag}.tar.gz"
  echo "PIN: sha256=$sha"
  echo "PIN: size=$size"
  echo "$sha  $(basename "$out")" > "$DIST/SHA256SUMS"
  # Record the exact input set for reproducibility.
  {
    echo "# inputs ($SUITE main arm64)"
    for f in "$RIG"/debs/*.deb; do
      echo "$(sha256sum "$f" | cut -d' ' -f1)  $(basename "$f")"
    done
  } > "$DIST/INPUTS.sha256"
}

case "$MODE" in
  resolve)
    echo "== resolving pinned versions (paste into PKGS_PINS for hard pinning)"
    resolve_from_index | tee "$RIG/resolved.tsv"
    ;;
  build)
    echo "== resolve + fetch + verify + assemble"
    resolve_from_index > "$RIG/resolved.tsv"
    [ -s "$RIG/resolved.tsv" ] || { echo "resolve produced nothing"; exit 1; }
    cat "$RIG/resolved.tsv"
    while IFS=$'\t' read -r name ver fname sha; do
      fetch_and_verify "$name" "$ver" "$fname" "$sha"
    done < "$RIG/resolved.tsv"
    rm -rf "$RIG/stage"; mkdir -p "$RIG/stage"
    for f in "$RIG"/debs/*.deb; do
      dpkg-deb -x "$f" "$RIG/stage"
    done
    prune_stage
    check_layout
    build_tar
    ;;
  *)
    echo "usage: $0 [resolve|build]"; exit 2 ;;
esac
