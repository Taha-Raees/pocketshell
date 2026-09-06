#!/bin/bash
# test_doctor_integration.sh — host-side integration tests for pocketshell-doctor.
# The aarch64 fixtures run through readelf-based fact gathering (readelf is
# arch-agnostic), but the aarch64 loader cannot execute on x86 — so the full
# loader-resolution branch is exercised via a THROWAWAY host-adapted copy
# (x86 loader + x86-64 machine gate) against a real dynamically linked binary
# with a real missing-library failure. The shipped script is never modified.
set -u
D=/home/z/my-project
DOC=$D/scripts/runtime/pocketshell-doctor
FIX=$D/runtime-tests/bin
T=$(mktemp -d)
fails=0
expect() { # expect NAME WANT_ACTUAL ACTUAL
  if printf '%s' "$3" | grep -qE "$2"; then
    echo "PASS: $1"; else fails=$((fails+1)); echo "FAIL: $1 — want /$2/ in:"; printf '%s\n' "$3" | sed 's/^/    /'; fi
}

echo "=== [1] aarch64 glibc fixture (t_cline_shape): full hierarchy, layer missing on host ==="
out=$(dash "$DOC" "$FIX/t_cline_shape" 2>&1); rc=$?
echo "$out"
expect "rc=1 (layer missing on host)" "^rc=1$" "rc=$rc"
expect "Machine AArch64"        "^Machine: AArch64" "$out"
expect "Interpreter glibc"      "^Interpreter: /lib/ld-linux-aarch64" "$out"
expect "Runtime glibc"          "^Runtime: glibc" "$out"
expect "DT_NEEDED enumerated"   "^  libc.so.6$" "$out"
expect "Required GLIBC 2.34"    "^Required GLIBC: GLIBC_2.34$" "$out"
expect "Version gate undecided" "^Version gate: UNDECIDED" "$out"
expect "Loader unavailable"     "^  UNAVAILABLE \(loader missing\)" "$out"
expect "Verdict UNSUPPORTED"    "^Compatibility: UNSUPPORTED$" "$out"
expect "Reason names layer"     "^Reason: glibc runtime layer is not installed" "$out"

echo
echo "=== [2] aarch64 static fixture (t_static): static path ==="
out=$(dash "$DOC" "$FIX/t_static" 2>&1); rc=$?
echo "$out"
expect "static verdict" "^Compatibility: SUPPORTED \(static" "$out"

echo
echo "=== [3] x86-64 binary: architecture gate ==="
out=$(dash "$DOC" /bin/true 2>&1); rc=$?
echo "$out"
expect "UNSUPPORTED arch" "^Reason: not an ARM64 \(AArch64\) binary" "$out"

echo
echo "=== [4] usage / not-a-file exit codes ==="
dash "$DOC" >/dev/null 2>&1; echo "no-args rc=$? (want 2)"
dash "$DOC" /nonexistent-bin >/dev/null 2>&1; echo "not-a-file rc=$? (want 2)"

echo
echo "=== [5] full loader-resolution branch via host-adapted throwaway copy ==="
# Build the probe binary (dynamically linked against a private lib)
W="$T/rig"; mkdir -p "$W"
printf 'int probe_extra(void){return 42;}\n' > "$W/lib.c"
gcc -shared -fPIC -o "$W/libprobe_extra.so.1" "$W/lib.c"
cat > "$W/need.c" <<'EOF'
extern int probe_extra(void);
int main(void){ return probe_extra()==42 ? 0 : 1; }
EOF
gcc -o "$W/need" "$W/need.c" "$W/libprobe_extra.so.1" -Wl,-rpath,"$W"
# Host-adapted throwaway: x86-64 loader + x86-64 machine gate
sed -e 's#^GLIBC_LOADER=/lib/ld-linux-aarch64.so.1#GLIBC_LOADER=/lib64/ld-linux-x86-64.so.2#' \
    -e 's/\*AArch64\*/\*X86-64\*/' "$DOC" > "$T/doc-host"
chmod +x "$T/doc-host"

echo "--- 5a: all libs resolve -> SUPPORTED with per-lib RESOLVED"
out=$(dash "$T/doc-host" "$W/need" 2>&1); rc=$?
echo "$out"
expect "rc=0" "^rc=0$" "rc=$rc"
expect "Version gate pass"  "^Version gate: PASS" "$out"
expect "per-lib RESOLVED"   "RESOLVED" "$out"
expect "loader PASS"        "^  PASS . all required libraries resolve" "$out"
expect "final SUPPORTED"    "^Compatibility: SUPPORTED \(glibc" "$out"
expect "combined reason"    "^Reason: installed glibc [0-9.]+ satisfies required GLIBC_2.34 and all required libraries resolve$" "$out"

echo "--- 5b: lib hidden -> loader FAIL -> UNSUPPORTED (exit code authoritative)"
mv "$W/libprobe_extra.so.1" "$W/hidden.so"
out=$(dash "$T/doc-host" "$W/need" 2>&1); rc=$?
echo "$out"
expect "rc=1" "^rc=1$" "rc=$rc"
expect "FAILED line shown"  "  FAILED: .*cannot open shared object file" "$out"
expect "Verdict UNSUPPORTED" "^Compatibility: UNSUPPORTED$" "$out"
expect "Reason names loader" "^Reason: the real loader could not load this binary \(exit 127\)" "$out"

echo
if [ "$fails" -eq 0 ]; then echo "INTEGRATION: ALL PASS"; else echo "INTEGRATION: $fails FAILURES"; exit 1; fi
rm -rf "$T"
