#!/bin/bash
# probe_loader_behavior.sh — empirical evidence for the doctor fix (Phase 1/2).
# Establishes, on THIS host's real glibc loader (x86_64 — same glibc family,
# same message formats as the device's aarch64 2.41 loader):
#   1. `ld.so --list <bin>` exit code + stdout/stderr when ALL DT_NEEDED resolve
#   2. same when a DT_NEEDED lib is MISSING (exact message text + exit code)
#   3. whether "not found" (the string the old doctor grepped) ever appears
#   4. the exact stdout line shape for per-lib RESOLVED reporting
set -u
W=$(mktemp -d)
cd "$W"
LOADER=/lib64/ld-linux-x86-64.so.2

printf 'int probe_extra(void){return 42;}\n' > lib.c
gcc -shared -fPIC -o libprobe_extra.so.1 lib.c

cat > need.c <<'EOF'
extern int probe_extra(void);
int main(void){ return probe_extra()==42 ? 0 : 1; }
EOF
gcc -o need need.c ./libprobe_extra.so.1 -Wl,-rpath,"$W"
echo "== sanity: ./need direct =="
./need; echo "exit=$?"

echo
echo "== CASE A: --list with all libs resolvable =="
"$LOADER" --list ./need > out.a 2> err.a; echo "exit=$?"
echo "--- stdout:"; cat out.a
echo "--- stderr:"; cat err.a

echo
echo "== CASE B: --list with libprobe_extra.so.1 hidden =="
mv libprobe_extra.so.1 hidden.so
"$LOADER" --list ./need > out.b 2> err.b; echo "exit=$?"
echo "--- stdout:"; cat out.b
echo "--- stderr:"; cat err.b
echo "--- grep 'not found' matches:"; grep -c 'not found' out.b err.b || true
echo "--- grep 'cannot open shared object' matches:"; grep -c 'cannot open shared object' out.b err.b || true

echo
echo "== CASE C: --verify on a good binary =="
"$LOADER" --verify ./need; echo "exit=$?"
"$LOADER" --verify ./lib.c; echo "exit=$? (non-ELF input)"

echo
echo "== CASE D: readelf -V shape (version needs) on ./need =="
readelf -V ./need | sed -n '/Version needs/,/^$/p'
echo "== all GLIBC tokens as the old extraction saw them:"
readelf -V ./need 2>/dev/null | grep -o 'GLIBC_[0-9.]*' | sort -uV | tail -1
rm -rf "$W"
