#!/bin/bash
# m6.0.4 mirror: new artifact set -> public/ + dist-master/ + download/,
# stale withdrawal (EXPLICIT filenames only), three-way sha verification,
# APK embedded-asset sha check against the UNCHANGED rev=2 pins (mandatory
# since the m6.0.2 lesson).
set -euo pipefail
P=/home/z/my-project
V=v0.10.0-m6.0.4
NEW_APK=PocketShell-$V-debug.apk
NEW_ZIP=PocketShell-$V-source.zip
NEW_TGZ=PocketShell-$V-source.tar.gz
NEW_BUNDLE=pocketshell-m2.gitbundle
NEW_TESTS=pocketshell-runtime-tests-aarch64.tar.gz
NEW_GLIBC=pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz
REPORT=PocketShell-Runtime-Forensic-Audit.pdf
STALE="PocketShell-v0.10.0-m6.0.3-debug.apk PocketShell-v0.10.0-m6.0.3-source.zip PocketShell-v0.10.0-m6.0.3-source.tar.gz PocketShell-v0.10.0-m6.0.2-debug.apk PocketShell-v0.10.0-m6.0.2-source.zip PocketShell-v0.10.0-m6.0.2-source.tar.gz"

# Pins (mirror GlibcRuntimePin — keep in sync on any layer bump):
ASSET_ENTRY=assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar
ASSET_SHA=898131ff2f5b243ee52eeeb4c3b45a51036f58e29f1a9775d6f8b45048626b1d
ASSET_SIZE=17920000

echo "== withdraw stale (explicit filenames only) =="
for d in "$P/public" "$P/dist-master" "$P/download"; do
  for f in $STALE; do
    if [ -f "$d/$f" ]; then rm -f "$d/$f"; echo "  removed $d/$f"; fi
  done
done

echo "== distribute new set =="
mkdir -p "$P/dist-master"
for d in "$P/dist-master" "$P/download"; do
  cp "$P/public/$NEW_ZIP" "$P/public/$NEW_TGZ" "$P/public/$NEW_BUNDLE" "$P/public/$NEW_APK" \
     "$P/public/$NEW_TESTS" "$P/public/$NEW_GLIBC" "$d/"
done
[ -f "$P/public/$REPORT" ] && cp "$P/public/$REPORT" "$P/download/" && echo "  report copied into download/ (mirror verifies, never required)"
echo "  distributed zip/tgz/bundle/apk/tests/glibc -> dist-master/ + download/"

echo "== APK EMBEDDED ASSET CHECK (m6.0.2 lesson — mandatory on every release) =="
em=$(unzip -l "$P/public/$NEW_APK" "$ASSET_ENTRY" | grep -o "$ASSET_ENTRY" | head -1)
if [ -z "$em" ]; then echo "  FAIL: $ASSET_ENTRY not in APK — this is the vc40/vc41 device-gate defect"; exit 1; fi
got=$(unzip -p "$P/public/$NEW_APK" "$ASSET_ENTRY" | sha256sum | awk '{print $1}')
gsz=$(unzip -p "$P/public/$NEW_APK" "$ASSET_ENTRY" | wc -c)
if [ "$got" = "$ASSET_SHA" ] && [ "$gsz" -eq "$ASSET_SIZE" ]; then
  echo "  OK  $ASSET_ENTRY  ($gsz B, sha ${got:0:16}...)"
else
  echo "  FAIL: asset drift — got $gsz B sha ${got:0:16}... want $ASSET_SIZE B sha ${ASSET_SHA:0:16}..."; exit 1
fi

echo "== three-way sha verification =="
fail=0
for f in "$NEW_APK" "$NEW_ZIP" "$NEW_TGZ" "$NEW_BUNDLE" "$NEW_TESTS" "$NEW_GLIBC" "$REPORT"; do
  [ -f "$P/public/$f" ] || { echo "  SKIP $f (not in public/)"; continue; }
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
[ "$fail" -eq 0 ] && echo "MIRROR VERIFIED (three-way + APK asset)" || { echo "MIRROR FAILED"; exit 1; }
