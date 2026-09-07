#!/bin/bash
# verify_http_m71p22.sh — single-call HTTP verification of the M7.1 P2.2 delivery set.
# The sandbox reaps call-spawned listeners at tool-call boundaries, so the server
# must be started, exercised, and verified inside ONE tool call. This script:
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
NAMES=("PocketShell-v0.11.0-m7.0.0-m7p2.2-debug.apk" \
       "PocketShell-v0.11.0-m7.0.0-m7p2.2-source.zip" \
       "PocketShell-v0.11.0-m7.0.0-m7p2.2-source.tar.gz" \
       "pocketshell-m7.1-p2.2.gitbundle" \
       "pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz")
PINS=(7e0e99a92a3234be11308ce487ee82199a3fef7d9276eee13cab107c2567b6c1 \
      0e2b9d3d5932ebf415b989b8594c9fa9f2b3dc3cbb2e5cb8d3b8750d164e45c4 \
      a45a0f02d4d46a2bd1a3d03783f7dcc8bbdba7498822ceeeebf431050370add4 \
      1e8822fc280edca4395ff93a9e580cc579cf8b898a1cbdb143b12aaa4e9bcf27 \
      ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d)
SIZES=(30664394 50086168 49829695 37479158 6764916)

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
for marker in "m7.1p2.2" "6004805" "696/696" "7e0e99a9" "0e2b9d3d" "a45a0f02" "1e8822fc" "ed82daa8"; do
  if echo "$BODY" | grep -q "$marker"; then echo "marker OK : $marker"; else echo "marker MISS: $marker"; PAGE_OK=0; fi
done
# stale reference check: the superseded m7p2.1 APK href must be gone
if echo "$BODY" | grep -q 'm7p2.1-debug.apk'; then echo "stale m7p2.1 APK href STILL PRESENT"; PAGE_OK=0; else echo "stale m7p2.1 APK href: absent OK"; fi

echo "== 3. artifacts: wire sha256 + byte size vs page pins =="
fail=0
for i in 0 1 2 3 4; do
  f="${NAMES[$i]}"; pin="${PINS[$i]}"; want="${SIZES[$i]}"
  wire=$(curl -s --max-time 180 "http://127.0.0.1:3000/$f" | sha256sum | cut -d' ' -f1)
  size=$(curl -s -o /dev/null -w "%{size_download}" --max-time 180 "http://127.0.0.1:3000/$f")
  if [[ "$wire" == "$pin" && "$size" == "$want" ]]; then
    printf 'PASS  %-46s %12d B  sha %s…\n' "$f" "$size" "${wire:0:8}"
  else
    printf 'FAIL  %-46s size=%s (want %s)  wire=%s\n' "$f" "$size" "$want" "${wire:0:16}"
    fail=1
  fi
done
# stale artifact must 404
stale=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 "http://127.0.0.1:3000/PocketShell-v0.11.0-m7.0.0-m7p2.1-debug.apk")
[[ "$stale" == "404" ]] && echo "stale m7p2.1 APK: 404 OK" || { echo "stale m7p2.1 APK: HTTP $stale (want 404)"; fail=1; }

echo "== 4. verdict =="
[[ $PAGE_OK -eq 1 ]] && echo "page markers: 8/8 + stale-href check" || echo "page markers: INCOMPLETE"
if [[ $fail -eq 0 && $PAGE_OK -eq 1 ]]; then
  echo "VERDICT: 5/5 ARTIFACTS BYTE-EXACT ON THE WIRE, PAGE RENDERING CURRENT, STALE SET 404"
  exit 0
fi
echo "VERDICT: FAILURES PRESENT"
exit 2
