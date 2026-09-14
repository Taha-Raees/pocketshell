#!/bin/sh
# run_bench.sh — Phase 4 terminal/PTY benchmark battery (Agent Z).
#
# Runs INSIDE the Alpine guest on the device. Modes:
#   sh run_bench.sh layer1 <outdir>    battery at the current proot depth
#   sh run_bench.sh nested <outdir>    same battery one proot layer deeper
#                                      (needs Alpine `proot`; apk add proot)
#   sh run_bench.sh appspawn <outdir>  app-shaped new-tab spawn (proot +
#                                      login-shell chain, fork→first-output)
#   sh run_bench.sh sigtest <outdir>   PTY correctness battery (layer 1)
#   sh run_bench.sh sigtest-nested <outdir>  correctness battery under a
#                                      nested proot chain (tracer-death
#                                      semantics of session close)
#
# Every run writes one labeled file into <outdir>. Compile artifacts are
# built fresh into /tmp/ph4-build (never committed).
set -eu
MODE=${1:?usage: run_bench.sh <layer1|nested|appspawn|sigtest|sigtest-nested> <outdir>}
OUT=${2:?usage: run_bench.sh <mode> <outdir>}
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD=/tmp/ph4-build
mkdir -p "$BUILD" "$OUT"

# --- build harness + exec targets (once) ---
if [ ! -x "$BUILD/ph4bench" ] || [ "$HERE/ph4bench.c" -nt "$BUILD/ph4bench" ]; then
  gcc -O2 -Wall -o "$BUILD/ph4bench" "$HERE/ph4bench.c"
fi
cat > "$BUILD/hello.c" <<'EOF'
int main(void) { return 0; }
EOF
gcc -O2 -o "$BUILD/hello_dyn" "$BUILD/hello.c"
gcc -O2 -static -o "$BUILD/hello_static" "$BUILD/hello.c"

# --- nested proot setup ---
NESTED_PROOT=${NESTED_PROOT:-/usr/bin/proot}
# App-fidelity flags: the app's proot runs --kill-on-exit --link2symlink.
NESTED_ARGS="--kill-on-exit --link2symlink --rootfs=/ --root-id"
nested() {
  [ -x "$NESTED_PROOT" ] || { echo "nested mode needs proot (apk add proot)" >&2; exit 2; }
  "$NESTED_PROOT" $NESTED_ARGS --cwd="$PWD" /bin/sh "$@"
}

run_battery() { # $1 = label prefix
  echo "== battery $1 =="
  "$BUILD/ph4bench" exec 200 "$BUILD/hello_static"
  "$BUILD/ph4bench" exec 200 "$BUILD/hello_dyn"
  "$BUILD/ph4bench" exec 200 /bin/true
  "$BUILD/ph4bench" fs 20000 /bin/sh
  "$BUILD/ph4bench" ptylat 200
  "$BUILD/ph4bench" ptytp 32
  "$BUILD/ph4bench" ptytp 32 --rev
  "$BUILD/ph4bench" session
}

case "$MODE" in
  layer1)
    {
      echo "# mode=layer1 (current guest depth)"
      echo "date=$(date -Is 2>/dev/null || date)"
      echo "uname=$(uname -mr)"
      echo "alpine=$(cat /etc/alpine-release 2>/dev/null)"
      echo "loadavg=$(cat /proc/loadavg)"
    } > "$OUT/layer1.env"
    run_battery layer1 | tee "$OUT/layer1.txt"
    ;;
  nested)
    {
      echo "# mode=nested (one extra proot layer: Alpine proot 5.4.0, rootfs=/)"
      echo "date=$(date -Is 2>/dev/null || date)"
      echo "loadavg=$(cat /proc/loadavg)"
    } > "$OUT/nested.env"
    nested -c 'exec "$0"' "$HERE/run_bench.sh" layer1 "$OUT" 2>/dev/null || \
      nested "$HERE/run_bench.sh" layer1 "$OUT"
    mv "$OUT/layer1.txt" "$OUT/nested.txt"
    mv "$OUT/layer1.env" "$OUT/nested.env"
    ;;
  appspawn)
    # App-shaped new-tab spawn, one extra layer deep: proot + the app's
    # login-shell chain. fork→first-output is the user-facing cost.
    "$BUILD/ph4bench" sessionfo \
      "$NESTED_PROOT" $NESTED_ARGS --cwd=/tmp \
      /bin/sh -l -c "exec /bin/sh -l" | tee "$OUT/appspawn.txt"
    ;;
  sigtest)
    "$BUILD/ph4bench" sigtest | tee "$OUT/sigtest.txt"
    ;;
  sigtest-nested)
    "$BUILD/ph4bench" sigtest -- \
      "$NESTED_PROOT" $NESTED_ARGS --cwd=/tmp /bin/sh -li | tee "$OUT/sigtest-nested.txt"
    ;;
  *)
    echo "unknown mode: $MODE" >&2; exit 2 ;;
esac
