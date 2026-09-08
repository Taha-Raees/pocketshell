#!/bin/bash
# verify_http_m72p2.sh — single-call HTTP verification of the M7.2 P2
# (session lifecycle engine & structured exit status) delivery set
# (v0.11.2-m7.1.1-m72p2, record tip 3b144be), adapting verify_http_m72p1.sh.
# The sandbox reaps call-spawned listeners at tool-call boundaries, so the
# server must be started, exercised, and verified inside ONE tool call.
# This script:
#   1. starts `bun run dev` (the platform's own command) if :3000 is not answering,
#   2. waits for the page,
#   3. checks the page's content markers,
#   4. downloads all five pinned artifacts and compares WIRE sha256 + byte size
#      against the pins published on the page (app/page.tsx HASHES),
#   5. prints a PASS/FAIL table and a final verdict.
set -u
cd /home/z/my-project

PAGE_OK=1
declare -a NAMES PINS SIZES
NAMES=("PocketShell-v0.11.2-m7.1.1-m72p2-debug.apk" \
       "PocketShell-v0.11.2-m7.1.1-m72p2-source.zip" \
       "PocketShell-v0.11.2-m7.1.1-m72p2-source.tar.gz" \
       "pocketshell-m7.2-p2.gitbundle" \
       "pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz")
PINS=(4a144d23710e7cd3893d1cdb59b0e3892a7b585ba1d4ed9015a7988360d2dfce \
      996148b0fe4573ef14f44b43880efa8b2d02a6304fa2438e4d23773c7c78714e \
      ea19e8232d9c4294046f730843e763c2f7e05bb077ff2dafee817eda3d326300 \
      d821d94fe21181574a706ceba88368e4defd336f01ec340393af947b91b0c548 \
      ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d)
SIZES=(30802996 50446875 50144080 37687491 6764916)

echo "== 1. ensure the :3000 server =="
if curl -s -m 3 -o /dev/null http://127.0.0.1:3000/; then
  echo "server already answering"
else
  nohup bun run dev >> dev.log 2>&1 &
  up=0
  for i in $(seq 1 30); do
    sleep 2
    if curl -s -m 3 -o /dev/null http://127.0.0.1:3000/; then up=1; break; fi
  done
  [[ $up -eq 1 ]] && echo "server up (waited $((i*2))s)" || { echo "FATAL: server did not come up"; exit 1; }
fi

echo "== 2. page markers =="
BODY=$(curl -s --compressed --max-time 30 http://127.0.0.1:3000/)
BLEN=${#BODY}
echo "page body bytes: $BLEN"
for marker in "v0.11.2-m7.1.1-m72p2" "3b144be" "M7.2 P2" "810/810" "4a144d23" "996148b0" "ea19e823" "d821d94f" "ed82daa8"; do
  if echo "$BODY" | grep -q "$marker"; then echo "marker OK : $marker"; else echo "marker MISS: $marker"; PAGE_OK=0; fi
done
# stale reference check: the superseded M7.2 P1 APK href must be gone
if echo "$BODY" | grep -q 'v0.11.2-m7.1.1-m72p1-debug.apk'; then echo "stale M7.2 P1 APK href STILL PRESENT"; PAGE_OK=0; else echo "stale M7.2 P1 APK href: absent OK"; fi

echo "== 3. artifacts: wire sha256 + byte size vs page pins =="
fail=0
for i in 0 1 2 3 4; do
  f="${NAMES[$i]}"; pin="${PINS[$i]}"; want="${SIZES[$i]}"
  wire=$(curl -s --max-time 180 "http://127.0.0.1:3000/$f" | sha256sum | cut -d' ' -f1)
  size=$(curl -s -o /dev/null -w "%{size_download}" --max-time 180 "http://127.0.0.1:3000/$f")
  if [[ "$wire" == "$pin" && "$size" == "$want" ]]; then
    printf 'PASS  %-52s %12d B  sha %s…\n' "$f" "$size" "${wire:0:8}"
  else
    printf 'FAIL  %-52s size=%s (want %s)  wire=%s\n' "$f" "$size" "$want" "${wire:0:16}"
    fail=1
  fi
done
# stale artifact must 404
stale=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 "http://127.0.0.1:3000/PocketShell-v0.11.2-m7.1.1-m72p1-debug.apk")
[[ "$stale" == "404" ]] && echo "stale M7.2 P1 APK: 404 OK" || { echo "stale M7.2 P1 APK: HTTP $stale (want 404)"; fail=1; }

echo "== 4. verdict =="
[[ $PAGE_OK -eq 1 ]] && echo "page markers: 9/9 + stale-href check" || echo "page markers: INCOMPLETE"
if [[ $fail -eq 0 && $PAGE_OK -eq 1 ]]; then
  echo "VERDICT: 5/5 ARTIFACTS BYTE-EXACT ON THE WIRE, PAGE RENDERING CURRENT, STALE SET 404"
  exit 0
fi
echo "VERDICT: FAILURES PRESENT"
exit 2
