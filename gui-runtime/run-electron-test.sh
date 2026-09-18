#!/bin/bash
# run-electron-test.sh — mc (ppm sink) + Electron Ozone-Wayland end-to-end gate.
set -u
cd "$(dirname "$0")"
export XDG_RUNTIME_DIR=/tmp/mc-xdg
RUN=/tmp/mc-el
SECS="${1:-22}"
rm -rf "$RUN"
mkdir -p "$RUN"
pkill -f "gui-runtime/build/mc" 2>/dev/null
pkill -f "electron-dist/electron" 2>/dev/null
sleep 0.3

MC_STATS=1 ./build/mc --sink ppm --size 800x500 --ppm-dir "$RUN" --ppm-every 1 \
  > "$RUN/mc.log" 2>&1 &
MC_PID=$!
sleep 1.2

WAYLAND_DISPLAY=wayland-0 "$HOME/p2-lab/electron-dist/electron" \
  "$HOME/p2-lab/electronapp" \
  --ozone-platform=wayland --no-sandbox --disable-gpu --disable-dev-shm-usage \
  > "$RUN/electron.log" 2>&1 &
EPID=$!

sleep "$SECS"
kill $EPID 2>/dev/null
kill $MC_PID 2>/dev/null
wait 2>/dev/null

echo "=== mc.log ==="
cat "$RUN/mc.log"
echo "=== electron.log head ==="
head -20 "$RUN/electron.log"
echo "=== frames: $(ls "$RUN"/*.ppm 2>/dev/null | wc -l) ==="
ls -S "$RUN"/*.ppm 2>/dev/null | head -2
