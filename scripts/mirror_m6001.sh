#!/bin/bash
# m6.0.1 mirror: new artifact set -> public/ + dist-master/ + download/,
# stale withdrawal (EXPLICIT filenames only — never broad patterns),
# three-way sha verification (includes the runtime-tests suite and the
# glibc layer artifact).
set -euo pipefail
P=/home/z/my-project
V=v0.10.0-m6.0.1
NEW_APK=PocketShell-$V-debug.apk
NEW_ZIP=PocketShell-$V-source.zip
NEW_TGZ=PocketShell-$V-source.tar.gz
NEW_BUNDLE=pocketshell-m2.gitbundle
NEW_TESTS=pocketshell-runtime-tests-aarch64.tar.gz
NEW_GLIBC=pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz
REPORT=PocketShell-Runtime-Forensic-Audit.pdf
STALE="PocketShell-v0.10.0-m6.0.0-debug.apk PocketShell-v0.10.0-m6.0.0-source.zip PocketShell-v0.10.0-m6.0.0-source.tar.gz"

echo "== withdraw stale (explicit filenames only) =="
for d in "$P/public" "$P/dist-master" "$P/download"; do
  for f in $STALE; do
    if [ -f "$d/$f" ]; then rm -f "$d/$f"; echo "  removed $d/$f"; fi
  done
done

echo "== distribute new set =="
for d in "$P/dist-master" "$P/download"; do
  cp "$P/public/$NEW_ZIP" "$P/public/$NEW_TGZ" "$P/public/$NEW_BUNDLE" "$P/public/$NEW_APK" \
     "$P/public/$NEW_TESTS" "$P/public/$NEW_GLIBC" "$d/"
done
echo "  distributed zip/tgz/bundle/apk/tests/glibc -> dist-master/ + download/"

echo "== three-way sha verification =="
fail=0
for f in "$NEW_APK" "$NEW_ZIP" "$NEW_TGZ" "$NEW_BUNDLE" "$NEW_TESTS" "$NEW_GLIBC" "$REPORT"; do
  a=$(sha256sum "$P/public/$f" | awk '{print $1}')
  c=$(sha256sum "$P/download/$f" | awk '{print $1}')
  if [ -f "$P/dist-master/$f" ]; then
    b=$(sha256sum "$P/dist-master/$f" | awk '{print $1}')
    ok3=$([ "$a" = "$b" ] && [ "$b" = "$c" ] && echo yes || echo no)
  else
    b="(n/a)"
    ok3=$([ "$a" = "$c" ] && echo yes || echo no)
  fi
  if [ "$ok3" = yes ]; then
    echo "  OK  ${a:0:16}  $f"
  else
    echo "  MISMATCH $f: $a / $b / $c"; fail=1
  fi
done
[ "$fail" -eq 0 ] && echo "MIRROR VERIFIED (three-way)" || { echo "MIRROR FAILED"; exit 1; }
