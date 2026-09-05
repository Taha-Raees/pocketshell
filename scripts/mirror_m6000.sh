#!/bin/bash
# m6.0.0 mirror: new artifact set -> public/ + dist-master/ + download/,
# stale withdrawal (EXPLICIT filenames only — never broad patterns),
# three-way sha verification (includes the runtime-tests suite and the
# glibc layer artifact, both new in M6.0).
set -euo pipefail
P=/home/z/my-project
V=v0.10.0-m6.0.0
NEW_APK=PocketShell-$V-debug.apk
NEW_ZIP=PocketShell-$V-source.zip
NEW_TGZ=PocketShell-$V-source.tar.gz
NEW_BUNDLE=pocketshell-m2.gitbundle
NEW_TESTS=pocketshell-runtime-tests-aarch64.tar.gz
NEW_GLIBC=pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz
STALE="PocketShell-v0.9.1-m5.1.0-debug.apk PocketShell-v0.9.1-m5.1.0-source.zip PocketShell-v0.9.1-m5.1.0-source.tar.gz"

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
for f in $NEW_ZIP $NEW_TGZ $NEW_BUNDLE $NEW_APK $NEW_TESTS $NEW_GLIBC; do
  h1=$(sha256sum "$P/public/$f" | cut -d' ' -f1)
  h2=$(sha256sum "$P/dist-master/$f" | cut -d' ' -f1)
  h3=$(sha256sum "$P/download/$f" | cut -d' ' -f1)
  if [ "$h1" = "$h2" ] && [ "$h2" = "$h3" ]; then
    echo "  OK  $f  ${h1:0:8}…"
  else
    echo "  MISMATCH $f: $h1 / $h2 / $h3"; fail=1
  fi
done
[ "$fail" = 0 ] && echo "MIRROR VERIFIED" || { echo "MIRROR FAILED"; exit 1; }
echo "== final listing =="
ls -1 "$P/download/"
