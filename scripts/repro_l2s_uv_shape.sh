#!/bin/bash
# Reproduce uv's EXACT failing call shape: hardlink an .l2s anchor symlink
# ACROSS directories (builds-v0 tmp -> archive-v0 cache), plus same-dir
# variant, on the shipped proot build.
set -uo pipefail
DIST=/home/z/tools/m23-dist/host
export LD_LIBRARY_PATH="$DIST"
export PROOT_TMP_DIR=/home/z/my-project/scratch/m262-rehearsal/proot-tmp
RFS=/home/z/my-project/scratch/m262-rehearsal/rfs-l2s

"$DIST/libproot.so" -R "$RFS" --link2symlink /bin/sh -c '
  rm -rf /root/uvtest; mkdir -p /root/uvtest/builds-v0 /root/uvtest/archive-v0
  cd /root/uvtest/builds-v0
  echo payload-data > requirements.py
  ln requirements.py materialized.py   # 1st-order emulated link (wheel unpack)
  echo "== guest sees in builds-v0: =="; ls -la
  echo "== uv call shape 1: link raw .l2s anchor name CROSS-DIR (the failing call) =="
  ln /root/uvtest/builds-v0/.l2s.requirements.py0001 /root/uvtest/archive-v0/.l2s.requirements.py0001 2>&1; echo "rc_cross=$?"
  echo "== uv call shape 2: link a plain-looking entry cross-dir (also emitted by cache sync) =="
  ln /root/uvtest/builds-v0/requirements.py /root/uvtest/archive-v0/requirements.py 2>&1; echo "rc_plain_cross=$?"
  echo "== uv call shape 3: link raw anchor name SAME-dir =="
  ln .l2s.requirements.py0001 .l2s.same.py0001 2>&1; echo "rc_same=$?"
  echo "== archive-v0 contents: =="; ls -la /root/uvtest/archive-v0/
' 2>&1
echo "=== HOST-LEVEL truth of the two trees ==="
ls -la "$RFS/root/uvtest/builds-v0" "$RFS/root/uvtest/archive-v0" 2>/dev/null
