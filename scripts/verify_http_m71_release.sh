#!/bin/bash
# verify_http_m71_release.sh — single-call HTTP verification of the OFFICIAL
# M7.1 RELEASE delivery set (v0.11.1-m7.1.0, tip 4e86e50), adapting
# verify_http_m71p3.sh. The sandbox reaps call-spawned listeners at
# tool-call boundaries, so the server must be started, exercised, and
# verified inside ONE tool call. This script:
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
NAMES=("PocketShell-v0.11.1-m7.1.0-debug.apk" \
       "PocketShell-v0.11.1-m7.1.0-source.zip" \
       "PocketShell-v0.11.1-m7.1.0-source.tar.gz" \
       "pocketshell-m7.1.gitbundle" \
       "pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz")
PINS=(2d298c85958f53db0497a2894b33fda70f60de8fe59ec5a14f725b146527eaa0 \
      0b4e8843f071e80d9296c0ce0792d04e5192a6f285d7e40215826d642cb44b35 \
      d00d076f2daaec38260e7fa2a5bc1f503a6801e3d37e7d00e46cb1c7f67f18c4 \
      7f6772220d4944ac47f64e5bcb68c654f155bfe1fda2a984911d20e3dcc34eab \
      ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d)
SIZES=(30448845 50170755 49906882 37532338 6764916)

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
for marker in "v0.11.1-m7.1.0" "4e86e50" "M7.1 RELEASE" "734/734" "2d298c85" "0b4e8843" "d00d076f" "7f677222" "ed82daa8"; do
  if echo "$BODY" | grep -q "$marker"; then echo "marker OK : $marker"; else echo "marker MISS: $marker"; PAGE_OK=0; fi
done
# stale reference check: the superseded m7p3 APK href must be gone
if echo "$BODY" | grep -q 'm7p3-debug.apk'; then echo "stale m7p3 APK href STILL PRESENT"; PAGE_OK=0; else echo "stale m7p3 APK href: absent OK"; fi

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
stale=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 "http://127.0.0.1:3000/PocketShell-v0.11.0-m7.0.0-m7p3-debug.apk")
[[ "$stale" == "404" ]] && echo "stale m7p3 APK: 404 OK" || { echo "stale m7p3 APK: HTTP $stale (want 404)"; fail=1; }

echo "== 4. verdict =="
[[ $PAGE_OK -eq 1 ]] && echo "page markers: 9/9 + stale-href check" || echo "page markers: INCOMPLETE"
if [[ $fail -eq 0 && $PAGE_OK -eq 1 ]]; then
  echo "VERDICT: 5/5 ARTIFACTS BYTE-EXACT ON THE WIRE, PAGE RENDERING CURRENT, STALE SET 404"
  exit 0
fi
echo "VERDICT: FAILURES PRESENT"
exit 2
