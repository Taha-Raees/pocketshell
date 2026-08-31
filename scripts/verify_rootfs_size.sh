#!/usr/bin/env bash
# Task 14 (operational, read-only w.r.t. project source):
# Validate the on-device Diagnostics reading "runtime 9.3 MB" by measuring
# the pinned alpine-minirootfs-3.24.1-aarch64 extracted size in a scratch dir.
# Everything lives in scripts/scratch-verify and is removed on exit.
set -uo pipefail
BASE=/home/z/my-project/scripts
SCRATCH=$BASE/scratch-verify
URL=https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.1-aarch64.tar.gz
EXPECT_SHA=f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259
EXPECT_SIZE=4023732

cleanup() {
  if [ "${KEEP_SCRATCH:-0}" = "1" ]; then echo "scratch KEPT: $SCRATCH"; return; fi
  rm -rf "$SCRATCH"; echo "scratch cleaned: $SCRATCH"
}
trap cleanup EXIT

rm -rf "$SCRATCH"; mkdir -p "$SCRATCH/rootfs"; cd "$SCRATCH" || exit 1
echo "downloading pinned rootfs from live CDN..."
curl -fsSL --retry 3 --retry-delay 2 -o rootfs.tar.gz "$URL" || { echo "DOWNLOAD FAILED"; exit 1; }
size=$(stat -c %s rootfs.tar.gz); sha=$(sha256sum rootfs.tar.gz | awk '{print $1}')
echo "archive_bytes=$size (pin $EXPECT_SIZE)"
echo "archive_sha256=$sha"
[ "$sha" = "$EXPECT_SHA" ] && echo "SHA MATCH: yes" || { echo "SHA MATCH: NO - ABORT"; exit 1; }
[ "$size" = "$EXPECT_SIZE" ] && echo "SIZE MATCH: yes" || echo "SIZE MATCH: NO"

echo "extracting..."
tar -xzf rootfs.tar.gz -C rootfs 2>tar_err.log
echo "tar_stderr_lines=$(wc -l < tar_err.log)"
[ -s tar_err.log ] && sed -n '1,5p' tar_err.log

echo "--- archive entry types (tar listing):"
tar -tvf rootfs.tar.gz | awk '{c[$1]++} END {for (k in c) print "  " k, c[k]}' | sort

echo "--- filesystem metrics (python):"
python3 "$BASE/verify_rootfs_metrics.py" "$SCRATCH/rootfs"

echo "disk_free_after=$(df -h /home/z | awk 'NR==2{print $4}')"
