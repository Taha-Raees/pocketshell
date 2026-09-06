#!/bin/sh
# C6 evidence: exercise pocketshell-doctor v2 decision paths in the sandbox
# (dash = busybox-ash-compatible). The real-loader SUPPORTED branch is proven
# on the device (suite rows); the sandbox covers every other decision path.
set -u
D=/home/z/my-project/scripts/runtime/pocketshell-doctor
B=/home/z/my-project/runtime-tests/bin
pass=0; fail=0
row() { printf '%-6s %-46s %s\n' "$1" "$2" "$3"; }
ok()  { row PASS "$1" "$2"; pass=$((pass+1)); }
bad() { row FAIL "$1" "$2"; fail=$((fail+1)); }

echo "== 1. selftest (dash / bash parity) =="
st_dash=$(dash "$D" --selftest 2>&1); rc_dash=$?
st_bash=$(bash "$D" --selftest 2>&1); rc_bash=$?
case "$st_dash" in *'SELFTEST PASS (15/15)'*) ok "dash --selftest 15/15" "rc=$rc_dash";; *) bad "dash --selftest" "$(echo "$st_dash" | tail -1)";; esac
case "$st_bash" in *'SELFTEST PASS (15/15)'*) ok "bash --selftest 15/15" "rc=$rc_bash";; *) bad "bash --selftest" "$(echo "$st_bash" | tail -1)";; esac

echo "== 2. decision-tree matrix (real binaries, no layer in sandbox) =="
# aarch64 static glibc binary -> SUPPORTED (static)
out=$(dash "$D" "$B/t_static" 2>&1); rc=$?
echo "$out" | grep -q '^Compatibility: SUPPORTED (static' && ok "t_static -> SUPPORTED (static)" "rc=$rc" \
  || bad "t_static" "$(echo "$out" | grep '^Compatibility' | head -1)"
# aarch64 glibc dynamic binary, layer ABSENT -> UNSUPPORTED (honest, correct for sandbox)
out=$(dash "$D" "$B/t_hello" 2>&1); rc=$?
echo "$out" | grep -q 'Compatibility: UNSUPPORTED' && echo "$out" | grep -q 'glibc runtime layer is not installed' \
  && ok "t_hello (no layer) -> UNSUPPORTED + reason" "rc=$rc" \
  || bad "t_hello" "$(echo "$out" | grep '^Reason' | head -1)"
# cline-shaped binary: version extraction must print numeric max (GLIBC_2.34)
out=$(dash "$D" "$B/t_cline_shape" 2>&1)
echo "$out" | grep -q '^Required GLIBC: GLIBC_2.34$' && ok "t_cline_shape max extraction" "GLIBC_2.34" \
  || bad "t_cline_shape" "$(echo "$out" | grep 'Required GLIBC' | head -1)"
echo "$out" | grep -q 'version requirements: 2.17 2.34' && ok "t_cline_shape lists all requirements" "2.17 2.34" \
  || bad "t_cline_shape reqs" "$(echo "$out" | grep 'version requirements' | head -1)"
# x86 binary -> UNSUPPORTED (not ARM64)
out=$(dash "$D" /bin/true 2>&1); rc=$?
echo "$out" | grep -q 'not an ARM64' && ok "/bin/true (x86) -> UNSUPPORTED not-ARM64" "rc=$rc" \
  || bad "/bin/true" "$(echo "$out" | grep '^Reason' | head -1)"
# non-ELF text file -> UNKNOWN/UNSUPPORTED path, never a crash
printf 'just text\n' > /tmp/c4evidence/notelf
out=$(dash "$D" /tmp/c4evidence/notelf 2>&1); rc=$?
case "$out" in *'ELF: no'*) ok "text file -> ELF: no" "rc=$rc";; *) bad "text file" "$(echo "$out" | head -1)";; esac
# empty file
: > /tmp/c4evidence/empty
out=$(dash "$D" /tmp/c4evidence/empty 2>&1); rc=$?
case "$out" in *'ELF: no'*) ok "empty file -> ELF: no" "rc=$rc";; *) bad "empty file" "$(echo "$out" | head -1)";; esac
# missing file -> exit 2 usage-class error
out=$(dash "$D" /no/such/binary 2>&1); rc=$?
if [ "$rc" -eq 2 ]; then ok "missing file -> rc=2" ""; else bad "missing file rc" "rc=$rc: $out"; fi

echo "== 3. malformed GLIBC battery (helpers isolated from the script) =="
sed -n '36,115p' "$D" > /tmp/c4evidence/helpers.sh
. /tmp/c4evidence/helpers.sh
t() { # t WANT LABEL args...
  want="$1"; label="$2"; shift 2
  got=$(ver_max "$@" 2>/dev/null)
  [ "$got" = "$want" ] && ok "$label" "= $got" || bad "$label" "= '$got' want '$want'"
}
t 2.34 "max skips malformed token" GLIBC_2.17 GLIBC_banana GLIBC_2.34
t 2.17 "max DROPS malformed trailing-dot token (fail-closed)" GLIBC_2.17 GLIBC_2.41.
t "" "max of all-malformed = empty" GLIBC_x GLIBC_ GLIBC..
t 2.9 "max single odd version" GLIBC_2.9
v() { # v WANT RC LABEL req inst
  want="$1"; wantrc="$2"; label="$3"; shift 3
  ver_le "$1" "$2"; rc=$?
  [ "$rc" = "$wantrc" ] && ok "$label" "rc=$rc" || bad "$label" "rc=$rc want $wantrc"
}
v x 2 "ver_le malformed req -> rc=2" 2.banana 2.41
v x 2 "ver_le malformed installed -> rc=2" 2.17 2..41
v x 2 "ver_le empty -> rc=2" "" 2.41
v x 0 "ver_le 2.41 2.41.0 (padding)" 2.41 2.41.0
v x 0 "ver_le 02.017 2.41 (leading zeros)" 02.017 2.41
v x 1 "ver_le 2.41.1 > 2.41" 2.41.1 2.41

echo
echo "RESULT: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
