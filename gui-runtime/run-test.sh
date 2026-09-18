#!/bin/bash
# run-test.sh — synchronous mc + simple-client verification run.
# usage: run-test.sh [ppm|kiosk] [seconds]
set -u
cd "$(dirname "$0")"
SINK="${1:-ppm}"
SECS="${2:-8}"
export XDG_RUNTIME_DIR=/tmp/mc-xdg
RUN=/tmp/mc-run
rm -rf "$RUN" /tmp/mc-xdg/*
mkdir -p "$RUN"
pkill -f "gui-runtime/build/mc" 2>/dev/null
sleep 0.3

if [ "$SINK" = ppm ]; then
  MC_STATS=1 ./build/mc --sink ppm --size 800x500 --ppm-dir "$RUN" --ppm-every 100 \
    > "$RUN/mc.log" 2>&1 &
else
  echo "kiosk mode needs a display; use --sink x11 manually" >&2
  exit 2
fi
MC_PID=$!
sleep 1.2
timeout "$SECS" ./build/simple-client 2>&1 | tail -2
sleep 0.7
kill $MC_PID 2>/dev/null
wait $MC_PID 2>/dev/null
echo "=== mc.log ==="
cat "$RUN/mc.log"
echo "=== frames: $(ls "$RUN"/*.ppm 2>/dev/null | wc -l) ==="
LAST=$(ls "$RUN"/*.ppm 2>/dev/null | tail -1)
[ -n "$LAST" ] && file "$LAST" | cut -d: -f2
[ -f "$RUN/ppm-timing.csv" ] && awk -F, 'NR>1 {n++; s+=$3} END {printf "presents=%d avg_copyconvert=%.1fus (%.0f MB/s)\n", n, s/n, 800*500*4/(s/n)}' "$RUN/ppm-timing.csv"
