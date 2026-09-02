#!/bin/bash
# Reproduce the Hermes/uv hardlink failure mechanism on the EXACT proot build
# we ship (termux/proot 7266fb3e, host-built): is a SECOND-ORDER link() —
# hardlinking a file that is itself an l2s-emulated link — emulated or EPERM?
set -uo pipefail
DIST=/home/z/tools/m23-dist/host
export LD_LIBRARY_PATH="$DIST"
export PROOT_TMP_DIR=/home/z/my-project/scratch/m262-rehearsal/proot-tmp
RFS=/home/z/my-project/scratch/m262-rehearsal/rfs-l2s
mkdir -p "$PROOT_TMP_DIR"

"$DIST/libproot.so" -R "$RFS" --link2symlink /bin/sh -c '
  rm -rf /root/l2stest; mkdir -p /root/l2stest; cd /root/l2stest
  echo payload-data > requirements.py
  echo "== 1st-order link (uv: unpack -> cache) =="
  ln requirements.py copy1.py; echo "rc1=$?"
  ls -la
  echo "== 2nd-order link: hardlink the l2s-emulated file (uv: cache -> archive) =="
  ln copy1.py copy2.py; echo "rc2=$?"
  ls -la
  echo "== control: link the REAL original again =="
  ln requirements.py copy3.py; echo "rc3=$?"
  ls -la
  echo "== linkat with AT_SYMLINK_FOLLOW on the l2s symlink =="
  python3 - 2>/dev/null <<PYEOF || echo "(python3 absent — skipping linkat variant)"
import os
try:
    os.link("/root/l2stest/copy1.py", "/root/l2stest/copy4.py", follow_symlinks=True)
    print("linkat-follow rc=0")
except OSError as e:
    print("linkat-follow FAILED:", e)
PYEOF
  ls -la
' 2>&1
